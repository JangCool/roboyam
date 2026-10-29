# 커머스 2a 계약 — 결제 모듈 단독

**의도**: 공구(2b)·충전(3)이 함께 쓸 현금 결제 모듈을 도메인 없이 둔다 — 결제 주문표 하나·확정 코드 하나·취소 대기열 하나, 포트원 V2 연동, 웹훅, READY 정리, 대사. 가짜 결제 게이트웨이(local·시험)와 포트원 테스트 채널로 검증한다.
정책 원천은 `docs/domain/commerce.md` 1-2(D10)·2-3·3·8·12-4·13-2·13-4·14·15·16·18·19·20·22절, 0단계 장치(포트원 설정 표·멱등·메일 대기열·배치 core·워커 기록·시험 DB)는 `commerce-foundation-contract.md`. 이 계약과 commerce.md 가 다르면 이 계약이 정본(결정 1 등 「commerce.md ○○ 를 고쳤다」).
**테스트**: 이 문서와 commerce.md 의 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절). 잠금·동시성·제약은 `CommerceTestDatabase`(실제 PostgreSQL), 포트원은 대역 HTTP·가짜 PG.

## 작업 단위
| 단위 | 내용 | 상태 |
|---|---|---|
| 2a-1 화면 | 관리자 결제 다섯 화면·`/settings/portone` 카드 셋, user-web `/dev/payment` | 확정 |
| 2a-2 결제 핵심 서버 | V97, 가짜·포트원 게이트웨이, 주문 만들기·확정·완료 API·시험 처리기, 웹훅, 취소 대기열·워커, READY 정리 | 완료 |
| 2a-3 대사 + 관리자 API + 연결 | V98·`R__batch_31`, 대사 배치, 관리자 조회·처리 API, 포트원 결제 칸, 알림·감사, 화면 연결 | 완료 |
| 2a-4 테스트 채널 실제 확인 | `@portone/browser-sdk` 0.1.13, 회원 API 연결, 사람 확인 | 로컬 완료 · **dev 서버 확인 남음**(「미확정·남은 일」) |
| 2a 보강 | V100 — 포트원 취소 호출을 잠금 밖으로 | 완료 |

## 마이그레이션 계획 (shared 스트림)
| 번호 | 내용 |
|---|---|
| `V97__payment_module` | 아래 「표」 7개(FK 없음) |
| `V98__payment_reconcile_admin` | 이력 종류 `RECHECKED`, 대사한 날 상태 RUNNING·DONE·MISMATCH·FAILED(V97 의 OK·FAIL 대신), 색인 셋 |
| `V100__payment_cancel_one_sending` | 부분 유일 `(payment_order_id) WHERE status = 'SENDING'` — 한 결제에 보내는 중인 취소는 하나 |
| `R__batch_31_payment_webhook_log_cleanup` | pg_cron 매일 04:45 KST, 90일 지난 웹훅 기록 삭제(`batch_params` `retention_days` 90 · 30~3650) |
| (공구 V102) | `payment_orders.order_type` 에 `GROUP_BUY_RETURN_FEE`, `payment_cancels.manual_method`(CONSOLE_CANCEL·BANK_TRANSFER) — `commerce-groupbuy-contract.md` 결정 68·74 |
- Java 배치(카탈로그만): `payment-ready-cleanup`(10분, MEMBER, `0 */10 * * * *`) · `payment-reconciliation`(`BatchCatalog.PAYMENT_RECONCILIATION`, SYSTEM, 매일 04:50 KST `0 50 4 * * *`, 날짜 파라미터 `day`·이어 받기). DB 배치 `PAYMENT_WEBHOOK_LOG_CLEANUP`. 워커 `payment-cancel`(1분, `worker_heartbeats`).
- `portone_settings` 는 키 CHECK 가 없어 표를 바꾸지 않는다(코드 `PortOneSettingKey` 만).

## 표 (FK 없음 — 회원은 `user_id`, 대상은 `order_type + order_ref_id`. 금액 bigint, 시각 timestamptz, ID Snowflake)
- **`payment_orders`**(결제 시도 한 건 = 한 줄): id, `payment_id`(서버 생성 `pay_` + 무작위 32자, 유일), user_id, order_type(`POINT_CHARGE`·`GROUP_BUY`·`GROUP_BUY_RETURN_FEE`·`PAYMENT_TEST`), order_ref_id, order_name, amount(> 0, 서버 계산), currency(`KRW` CHECK), status(`READY`·`PAID`·`PARTIAL_CANCELED`·`CANCELED`·`EXPIRED`), paid_amount, canceled_amount(CHECK 0 ≤ canceled ≤ paid), anomaly(null·`AMOUNT_MISMATCH`·`DOMAIN_REJECTED`·`LATE_PAYMENT`), pay_method, pg_provider, pg_tx_id, receipt_url, paid_at, expires_at, confirmed_via(`COMPLETE_API`·`WEBHOOK`·`READY_CLEANUP`·`ADMIN_RECHECK`), created_at, updated_at. CHECK: PAID 계열이면 paid_amount·paid_at 필수.
  - 색인: 유일 `payment_id` · **유일 `(order_type, order_ref_id) WHERE status IN ('PAID','PARTIAL_CANCELED') AND anomaly IS NULL`**(결정 3) · `(expires_at) WHERE status='READY'` · `(user_id, created_at)` · `(order_type, order_ref_id)` · `(paid_at)` · `WHERE anomaly IS NOT NULL`.
- **`payment_cancels`**(모든 PG 취소의 대기열): id, payment_order_id, amount(> 0), reason(내부), kind(`USER_CANCEL`·`REFUND`·`AUTO_REJECT`·`ADMIN`·`CHARGE_REFUND`), `request_key`(유일 — 예 `AUTO_REJECT:{결제 주문 ID}`), status(`PENDING`·`SENDING`·`SUCCEEDED`·`GAVE_UP`·`MANUAL` — 결정 2), pg_status(`REQUESTED`·`SUCCEEDED`·`FAILED`), pg_cancel_id(있으면 유일), attempts, next_attempt_at, locked_by, locked_at, last_error(가림), requested_by, resolved_by, resolve_note, manual_method, created_at, completed_at. 색인 `(next_attempt_at) WHERE status='PENDING'` · `(payment_order_id)` · `WHERE status='GAVE_UP'` · V100 의 SENDING 부분 유일.
- **`payment_events`**(결제 한 건의 이력 — 5년 거래 기록): id, payment_order_id, kind(`CREATED`·`PAID`·`ANOMALY`·`EXPIRED`·`CANCEL_REQUESTED`·`CANCEL_SUCCEEDED`·`CANCEL_GAVE_UP`·`CANCEL_MANUAL`·`EXTERNAL_CANCEL`·`RECHECKED`·`WEBHOOK` 등), `provider_event_id`(웹훅 `webhook-id`, 부분 유일), detail(jsonb, 개인정보 없음), created_at. 색인 `(payment_order_id, created_at)`.
- **`payment_webhook_logs`**(받은 웹훅 전부 — 90일): id, webhook_id, event_type, payment_id, result(`PROCESSED`·`IGNORED`·`REJECTED`·`RETRY`), http_status, error_code, received_at. **본문은 저장하지 않는다.** 색인 `(received_at)`·`(payment_id)`·`(result, received_at)`.
- **`reconciliation_issues`**: id, type(`PG_PAID_LOCAL_UNPAID`·`LOCAL_PAID_PG_MISSING`·`AMOUNT_MISMATCH`·`CANCEL_MISMATCH`·`PG_ONLY`), payment_id, payment_order_id(null 가능), detected_day, detail, status(`OPEN`·`RESOLVED`), resolve_note, resolved_by, resolved_at, created_at. **유일 `(type, payment_id) WHERE status='OPEN'`**. 콘솔 직접 취소 웹훅(P18)도 `CANCEL_MISMATCH` 로 바로 남긴다.
- **`payment_reconcile_days`**: day(PK, KST 날짜), status(RUNNING·DONE·MISMATCH·FAILED — 줄이 없으면 화면 PENDING), checked_count, issue_count, run_id, finished_at.
- **`fake_pg_payments`**(가짜 PG 의 결제 상태 — local·시험 전용, dev·prod 에서는 비어 있다, 결정 8): payment_id(PK), status, amount, currency, paid_at, cancelled_amount, cancellations(jsonb), updated_at.

## 패키지 (`payment` 는 다른 도메인을 모른다)
- shared `com.community.payment`:
  - `dto` — `PaymentOrderType`·`PaymentStatus`·`PaymentAnomaly`·`ConfirmedVia`·`CancelKind`·`CancelStatus`·`PgCancelStatus`·`PaymentDecision`(ACCEPT | REJECT(사유))·`PaidPayment`·`CanceledPayment`·`PaymentOrderRequest`·`PaymentCheckout`·`ManualCancelMethod`·`ManualCancelResolution`, 응답 `PaymentResult`·`PaymentCheckoutView`(도메인 결제 시작 API 가 같이 주는 공용 `checkout`)·관리자 화면 모양
  - `handler` — **`PaymentOrderHandler`**(`orderType()`·`onPaid(PaidPayment) → PaymentDecision`·`onCanceled(CanceledPayment)`·`onCancelResolvedManually`(기본 빈 메서드)) · `PaymentOrderHandlers`(종류마다 정확히 하나 — 둘 이상이면 기동 실패, 없으면 그 종류 결제를 만들 수 없다)
  - `gateway` — `PaymentGateway`(단건 조회·취소·다건 조회·웹훅 서명 검증·결제창 정보·준비 여부 `ready()`·에스크로 `registerEscrowLogistics`·`completeEscrow`) · `gateway.portone`(`PortOnePaymentGateway`·`StandardWebhookVerifier`·`PortOnePaymentParser`) · `gateway.fake`(`FakePaymentGateway`·`FakePgStore`)
  - `config` — `PaymentConfig`(가짜면 dev·prod 기동 막기)·`PaymentProperties`
  - `repository.master` — JDBC 저장소(조건부 UPDATE·`SKIP LOCKED`): `PaymentOrderStore`·`PaymentCancelStore`·`PaymentEventStore`·`PaymentWebhookLogStore`·`ReconciliationIssueStore`·`PaymentReconcileDayStore`, 관리자 읽기 `PaymentAdminQueries`·`PaymentReconcileQueries`
  - `service` — `PaymentOrderService.create`(MANDATORY — 도메인 트랜잭션 안, 같은 주문의 이전 READY 는 EXPIRED)·`expireOpen`·`openCheckout` · **`PaymentConfirmService.confirm`**(확정 코드 하나 — 완료 API·웹훅·READY 정리·관리자 다시 확인이 함께 쓴다) · `PaymentCompletionService` · `PaymentWebhookService` · `PaymentCancelService`(넣기, MANDATORY) · `PaymentCancelExecutor`·`PaymentCancelWorker`·`PaymentCancelImmediateAttempt` · `PaymentAnomalyService` · `ReconciliationService` · `PaymentAdminReadService`·`PaymentAdminActionService`·`PaymentCancelManualService`·`PaymentDetailText`
  - `batch` — `PaymentReadyCleanupJob`·`PaymentReconciliationJob` · `testorder` — `PaymentTestOrderHandler`·`PaymentTestOrderService`(`PAYMENT_TEST`, `roboyam.payment.test-orders` 를 켠 환경만)
- api `payment.controller`: `PaymentController`(완료 API)·`PaymentWebhookController`·`PaymentTestController`(`/api/dev/payments`). admin `payment.controller.PaymentAdminController`·`PortOneSettingsController`, `batch.service.PaymentCancelScheduler`(1분, `roboyam.batch.enabled` 서버만).

## 확정 흐름 (8-2 를 구체화)
1. 포트원 단건 조회는 **트랜잭션 밖**. 조회 결과의 `id`·`storeId` 가 우리 것이어야 한다.
2. 한 트랜잭션: `READY|EXPIRED → PAID` 조건부 UPDATE(이긴 쪽만) → 늦은 결제 판정(결정 4) → 금액·통화(비교 금액 = 포트원 `amount.total`) → `onPaid` → REJECT 면 anomaly + `AUTO_REJECT` 취소 넣기. 늦은 결제·금액 불일치는 `onPaid` 를 부르지 않는다.
3. 처리기 예외 = 전체 되돌림(READY 그대로, 취소 없음) → 다음 웹훅·READY 정리가 다시 시도.
4. 커밋 뒤 이벤트로 그 자리에서 취소를 한 번 시도(새 트랜잭션으로 집는다), 실패하면 1분 워커가 이어 받는다(D10 「즉시 시도」). 워커와 즉시 시도는 `PENDING → SENDING`(+`locked_by`) 조건부 UPDATE 로 한쪽만 집는다.
5. 취소 실행(고침 — 「2a 보강 — 취소 호출을 잠금 밖으로」): 집기 커밋 → 재시도 전 조회(이미 취소됐으면 SUCCEEDED 로 맞춤) → **잠금 없이** 잔액(`paid − canceled`)을 읽어 포트원 `POST /payments/{id}/cancel`(`amount`·`currentCancellableAmount`·`reason`) → 새 트랜잭션에서 결제 주문 행을 잠그고 `canceled_amount` 조건부 증가(`WHERE canceled_amount + :amt <= paid_amount`)·상태 갱신·`onCanceled`, 또는 재시도·GAVE_UP. 즉시 1 + 재시도 5, 간격 1·5·30·120·720분, 다 실패하면 `GAVE_UP` + 결제 이상 목록 + 운영자 알림.

## 세션 정리 결정 (1~18)
1. **PG 설정 위치**: DB(`portone_settings`) — commerce.md 8-5 「환경 변수」를 고쳤다. 포트원/가짜 선택만 배포 설정 `roboyam.payment.gateway`(공통 portone, local fake).
2. **취소 대기열 상태**: 우리 상태 `PENDING`·`SENDING`·`SUCCEEDED`·`GAVE_UP`·`MANUAL`, 포트원 응답은 `pg_status`. 재시도 전에 조회로 확인.
3. **받아들인 PAID 하나**: 부분 유일 색인에 `AND anomaly IS NULL` — 이상 결제가 정상 결제와 부딪쳐 확정이 되돌아가지 않게.
4. **늦은 결제 판정**: 우리 상태가 EXPIRED 면 무조건 늦음. READY 면 포트원 `paidAt > expires_at` 일 때만(1µs 도 늦음, 같으면 아님 — 확인 요청이 늦게 온 것은 회원 탓이 아니다). 숨은 여유 시간 없음.
5. **웹훅을 받는 서버**: api. `POST /api/payments/webhooks/portone`, `SecurityConfig` permitAll + CSRF 제외(`PaymentWebhookPaths`), 본문 64KB 제한, 원본 바이트로 서명 검증.
6. **완료 API**: 공용 `POST /api/payments/{paymentId}/complete` + `{orderType, orderRefId}` — 그 회원·그 주문의 결제가 아니면 거절(P24, 포트원 조회 없음). 속도 제한·멱등 키 없음(12-4). 결제 시작 API 는 도메인마다.
7. **시험 주문**: `PAYMENT_TEST` + 시험 처리기는 `roboyam.payment.test-orders` 로 local·dev 만, prod 에서 켜면 기동을 막는다. 처리기 판정·현금은 그 서버 메모리(다시 띄우면 잊고 READY 정리는 기본 ACCEPT).
8. **가짜 PG 상태**: DB 표 `fake_pg_payments`(api·admin 이 같은 가짜 결제를 본다).
9. **브라우저 SDK**: `@portone/browser-sdk` 0.1.13(정확한 버전 고정), `@portone/browser-sdk/v2` 를 클라이언트에서만 동적 import.
10. **운영자 알림**: `COMMERCE_PAYMENT_ANOMALY`(CRITICAL — 최종 취소 실패·금액 불일치, 이메일 기본 켬, 같은 결제 하루 한 건 — 묶음 키 결제 ID, 고정 문구, 링크 `/payments/anomalies`) · `COMMERCE_RECONCILE_MISMATCH`(WARNING, 이메일 없음, 링크 `/payments/reconciliation`, 묶음 키 `reconcile:<KST 날짜>` + 24시간 — 한 실행이 새 이슈를 열었을 때만). 받는 사람 `commerce.reconcile.manage` 보유자, 영역 GROUPBUY. `COMMERCE_ORDER_ANOMALY` 는 공구 몫.
11. **회원 알림·메일**(자동 취소 안내 등): 도메인이 `onCanceled` 에서 보낸다. 결제 모듈에는 회원 알림이 없다.
12. **결제 이상·대사 처리 권한**: 「다시 시도」·「수동 처리 완료」·「포트원에서 다시 확인」·대사 이슈 처리 = `commerce.reconcile.manage` + 사유 5~200자(`400 fields.reason`) + 감사(돈). 조회 = `commerce.payment.read`. 두 권한 `featureReady=true`.
13. **메뉴**: 보통 모드 묶음 「결제」(`menu.payment`, 포인트 상점 다음 — 공구 묶음 뒤, 아이콘 `pi pi-credit-card`) — 결제 주문 · 웹훅 수신 내역 · 결제 이상 · 날짜별 대사 · 대사 불일치. PG 설정은 `/settings/portone`.
14. **포트원 설정**: `PortOneSettingKey` 묶음 공통(storeId·apiSecret)·본인인증(identityChannelKey)·결제(`paymentChannelKey`·`webhookSecret`) — 키 문자열 불변. API `PUT /api/admin/portone-settings/{common|identity|payment}` · `DELETE …/{group}/{key}`(권한 `site.read`/`site.secret`). `paymentReady` = 결제 4칸(상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키) + 암호화 키. 저장·지우기는 카드 단위이고 다른 카드 칸은 받지 않는다(400), 감사 대상 ID = 카드 이름·칸 이름만(값 없음, 돈 관련). 웹훅 비밀키 = `whsec_` + 비어 있지 않은 base64(아니면 `400 fields.webhookSecret`, 그 카드는 저장 안 함). 0-4 계약 API 표는 고쳤다.
15. **결제 채널**: KG이니시스 V2 하나(사용자 결정 2026-10-27). 카카오페이·네이버페이는 이니시스 결제창 안 간편결제(포트원이 `PaymentMethodEasyPay` 로 돌려줌 — 화면 「간편결제」). 직접 채널(카카오페이 CID)은 필요할 때 칸을 더한다. 포트원 상점은 로보얌 전용이라 대사에서 상점을 거를 필요가 없다.
16. **대사**: 포트원 다건 조회 기준 `STATUS_CHANGED_AT`, 하루 = KST 날짜, 매일 04:50 KST, 날짜 지정 다시 실행, 자동 보정 없음.
17. **결제 화면 개인정보**: 회원 번호·닉네임만(이메일 없음, 탈퇴 회원 번호 없음). 상세를 열면 `PAYMENT_ORDER_VIEW`(개인정보 열람, 2년).
18. **숫자 위치**: 워커 구조는 배포 설정 `roboyam.payment.*` — `cancel-batch-size` 20 · `cancel-gap` 200ms · `cancel-max-retries` 5 · `cancel-backoff` 1m,5m,30m,120m,720m · `cancel-stale` 10m · (admin) `cancel-poll-millis` 60000 · `test-orders`(공통 false, local true). 배치 값은 `batch_params`(`payment-ready-cleanup.grace_minutes` 기본 10 · 0~1440, 웹훅 기록 `retention_days` 90). 웹훅 시각 오차 5분은 코드 상수.
- **감사 행위**: `RECONCILE_ISSUE_RESOLVE`·`PAYMENT_CANCEL_RETRY`·`PAYMENT_CANCEL_MANUAL`·`PAYMENT_RECHECK` = 돈 관련, `PAYMENT_ORDER_VIEW` = 개인정보 열람, `PAYMENT_RECONCILE_RERUN` = 일반. 대상 `PAYMENT_ORDER`·`PAYMENT_CANCEL`·`RECONCILIATION_ISSUE`·`PAYMENT_RECONCILE_DAY`.
- **오류 코드**: `PAYMENT_ORDER_NOT_FOUND`(404) · `PAYMENT_NOT_PAID`(409) · `PAYMENT_ORDER_MISMATCH`(409) · `PAYMENT_CANCEL_EXCEEDS`(409) · `PAYMENT_GATEWAY_UNAVAILABLE`(503) · `PAYMENT_NOT_CONFIGURED`(503) · `PAYMENT_GATEWAY_NOT_FAKE`(409) · `FAKE_PG_ALREADY_DONE`(409) · `PAYMENT_CANCEL_NOT_FOUND`(404) · `PAYMENT_CANCEL_NOT_GAVE_UP`(409) · `RECONCILE_ISSUE_NOT_FOUND`(404) · `RECONCILE_ISSUE_NOT_OPEN`(409) · `RECONCILE_ALREADY_RUNNING`(409).

## 웹훅 처리 표 (8-4 를 구체화)
| 경우 | 응답 | 기록 |
|---|---|---|
| 비밀키 없음 | 머리글을 보기 전에 503(설정 뒤 재전송을 받는다) | RETRY |
| 머리글 없음·서명 틀림·시각 오차 5분 초과 | 400, 아무것도 바꾸지 않고 포트원 조회 없음. 검증 안 된 머리글 값은 남기지 않는다(`error_code` 만) | REJECTED |
| 같은 `webhook-id` 를 이미 처리 | 200 | PROCESSED(다시) — 이벤트 줄은 하나 |
| 우리 결제가 아님(storeId 다름·payment_id 없음)·모르는 이벤트·읽을 수 없는 본문 | 200 | IGNORED |
| `Transaction.Paid` | 조회 후 확정 코드. 포트원이 아직 PAID 아님·장애 → 503(이벤트 줄을 남기지 않아 재전송이 다시 처리) | PROCESSED / RETRY |
| `Transaction.Cancelled`·`PartialCancelled` | 조회해 포트원 취소 목록과 `payment_cancels` 를 `pg_cancel_id`·우리 표시로 맞춤. 우리 것이 아닌 취소(콘솔 직접 취소) → 상태 불변 + 취소 아이디마다 `EXTERNAL_CANCEL` 이력 한 줄 + 열린 `CANCEL_MISMATCH` 하나(P18) | PROCESSED |
| 처리기 예외 등 뜻밖의 오류 | 500(재전송) | — |
- 서명: Standard Webhooks — 대상 `{webhook-id}.{webhook-timestamp}.{본문}`, 비밀키는 `whsec_` 뒤 base64(`whsec_` 없이 주면 그대로 base64), HMAC-SHA256, `webhook-signature` 의 `v1,<서명>` 여러 개 중 하나라도 맞으면 통과, 상수 시간 비교. 처리한 웹훅만 `payment_events` 의 `WEBHOOK` 줄로 남는다.

## 2a-1 화면 목업 (확정 화면과 API 모양)
문구는 i18n `payment.*`(두 앱)·`portone.*`·`menu.payment*`. 관리자 다섯 화면 모두 조회 `commerce.payment.read`, 처리 단추는 `commerce.reconcile.manage`(없으면 잠그고 이유 표시), 처리는 사유 5~200자 확인창(`PaymentReasonDialog`).
| 메뉴 키 | 주소 | 화면 |
|---|---|---|
| `paymentOrders` | `/payments/orders` | 결제 주문 목록 — 기간(기본 1주일)·상태·이상(전체·있음·종류)·주문 종류·번호(결제 번호 일부·회원 번호). 줄: 만든 시각·결제 번호·회원(닉네임·번호)·주문(이름·종류)·금액(주문 / 다르면 결제액·취소액)·상태(+ 이상·취소 진행 중)·결제 시각(READY 면 유효 시각). 줄 또는 `?payment=ID` 로 상세 서랍 |
| `paymentWebhooks` | `/payments/webhooks` | 웹훅 수신 내역 — 기간·결과(뜻 풀이)·번호(결제 번호·webhook-id). 줄: 받은 시각·이벤트·결제 번호(우리 주문이면 서랍)·결과·HTTP·사유 코드·webhook-id |
| `paymentAnomalies` | `/payments/anomalies` | 결제 이상 — 이상 결제와 GAVE_UP 취소를 한 목록, 기본 처리 안 된 것만(「처리된 것도 보기」), 「처리 안 됨 N건」. GAVE_UP 줄만 [다시 시도]·[수동 처리 완료](방법 고르기 — 공구 계약 결정 74) |
| `paymentReconcileDays` | `/payments/reconcile-days` | 날짜별 대사 — 요약(마지막 대사한 날·다음 자동 대사·열린 이슈 수) + 최근 14일 표(상태 PENDING·RUNNING·DONE·MISMATCH·FAILED·확인한 건수·불일치 열림/전체·끝난 시각·[이슈 보기]·[이 날 다시 대사](확인창, 4초 뒤 한 번 다시 읽음)) |
| `paymentReconciliation` | `/payments/reconciliation` | 대사 불일치 — 이슈 5종, 거르기 종류·상태(기본 열림)·날짜(`?day=`). 줄: 발견한 날·종류(+ 풀이)·결제 번호(우리 주문 없으면 「우리 주문 없음」)·내용·상태/처리 기록·[처리] |
- **결제 상세 서랍**(`PaymentOrderDrawer` + `usePaymentOrderDrawer`): 머리(주문 이름·상태·이상·결제 번호 + 「열면 열람 기록이 남아요」), 이상 띠, 열린 대사 이슈 띠 / ① 금액 네 칸(주문·결제·취소·남은) + 회원·주문 종류·대상 번호·결제 수단·PG·PG 거래 번호·영수증·확정한 길·결제창 연 시각·유효 시각·결제 시각 / ② 이력 타임라인 / ③ 취소 대기열 / ④ 이 결제의 웹훅 / ⑤ [포트원에서 다시 확인](사유).
- **`/settings/portone`**: ① 지금 쓰는 구현(본인인증·결제 각각 포트원/가짜, 읽기 전용) ② 공통 ③ 본인인증(알약 `identityReady`) ④ 결제(결제 채널 키·웹훅 비밀키, 알약 `paymentReady`, 콘솔 웹훅 주소 `…/api/payments/webhooks/portone` 안내). 카드마다 저장, 한 카드 저장 중엔 다른 카드 잠금.
- **회원 `/dev/payment`**(개발 모드만 — 운영 빌드 404, 로그인 필요): ① 시험 주문(금액 100~1,000,000원·유효 1~60분) ② 결제창 — 가짜면 `PaymentFakeCheckoutDialog`(정상 결제·결제 실패·금액 바꿔 결제·늦게 결제), 포트원이면 `PortOne.requestPayment`(`payMethod: CARD`, `currency: 'KRW'`). 성공이면 완료 API, `code` 가 있으면 완료 요청 없음. 모바일 리디렉션은 시험 주문을 sessionStorage 에 두었다가 돌아온 `paymentId` 가 같을 때만 복원 ③ 결과(상태·받아들였나·금액·자동 취소 진행·[결과 다시 받기]). 포트원 실패는 `code`·`pgCode` 를 보이고 「같은 결제 번호로 다시 열 수 있어요」(`PG_FAILED`). 가짜 결제창 부품은 충전·공구 화면이 그대로 쓴다.
- **창 닫기 판정** `UtilPortone.isUserCancel(code, message)` = `Cancelled` 또는 (`FAILURE_TYPE_PG` ∧ 문구 「사용자 … 취소」) — 이니시스 V2 는 창 닫기에 카드 거절과 같은 코드를 준다. 결제창·본인인증이 같이 쓴다. 다른 PG 를 붙이면 그 PG 의 문구를 다시 확인.

**확정한 목업 가정(서버 규칙)**
1. 「다시 시도」 = GAVE_UP → PENDING, 시도 0, 다음 시도 지금, `completed_at` 비움(1분 워커가 집는다 — 즉시 시도 없음), 이력 `CANCEL_REQUESTED`(`detail.by = ADMIN_RETRY`). 「수동 처리 완료」 = GAVE_UP → MANUAL, 이력 `CANCEL_MANUAL` — 방법이 「표시만」·계좌 송금이면 **취소 누계 불변**, 콘솔 취소면 그 취소 ID 를 붙이고 누계까지(공구 계약 결정 74, 고침). 둘 다 처리자·사유를 취소 줄(`resolved_by`·`resolve_note`)에, 감사에 `paymentId`·`amount`·`note`.
2. 「포트원에서 다시 확인」 결과 셋: `CONFIRMED`(이번에 확정 — `confirmedVia: ADMIN_RECHECK`, EXPIRED 였으면 늦은 결제 + 자동 취소) · `UNCHANGED`(이미 확정 — 포트원 취소 목록을 대기열과 맞춤: 멈춘 취소를 포트원이 실제로 했으면 SUCCEEDED·누계 반영, 남의 취소는 `CANCEL_MISMATCH`) · `PG_NOT_PAID`. 이력 `RECHECKED`(`detail.outcome`), 포트원 장애는 503·감사 없음.
3. 사유가 필요한 처리: 다시 시도 · 수동 처리 완료 · 다시 확인 · 이슈 처리. **「이 날 다시 대사」는 사유 없이 확인창만**(돈을 움직이지 않는다 — 감사는 일반으로 남긴다).
4. 결제 이상 목록의 한 줄 = 이상 결제 주문 하나(`ORDER:{id}` — 발견 = 첫 `ANOMALY` 이력, 취소 = `AUTO_REJECT:<주문 ID>` 줄) 또는 이상이 아닌 결제의 **한 번이라도 GAVE_UP 이 됐던** 취소(`CANCEL:{id}` — 발견 = 첫 `CANCEL_GAVE_UP`). 처리됨 = 그 취소가 SUCCEEDED·MANUAL. 정렬 = 처리 안 된 것 → 발견 시각 내림차순.
5. 회원 화면 결과의 「완료」 판단은 `accepted`(PAID 계열 ∧ 이상 없음) 하나.
6. 이력 `detail`·대사 이슈 `detail`·취소 `last_error` 는 서버가 요청 언어로 푼 한 줄(「이름: 값 · …」, 금액 「12,000원」, 시각 KST, 코드 값은 번들 `payment.value.*` — 개인정보·비밀값 없음). 화면에 `CANCEL_RETRY`·`WEBHOOK` 이력 종류도 내린다.

**API**(공통 래퍼 `data`, `no-store`, 쓰기는 CSRF — 웹훅 제외. 쪽 번호 0부터·크기 1~100(기본 20), 기간은 ISO 순간 [from, to), 못 읽는 질의 `400 fields.<이름>`, 못 읽는 경로 ID 404)
| 주소 | 권한 | 요청 → 응답 · 오류 |
|---|---|---|
| `POST /api/payments/{paymentId}/complete` | 회원 | `{orderType, orderRefId}` → `PaymentResult{paymentId, orderType, orderRefId, status, anomaly, accepted, amount, paidAmount, canceledAmount, paidAt, cancel: {amount, status} \| null}`(확정 뒤 다시 읽어 즉시 취소 결과까지) · 404 `PAYMENT_ORDER_NOT_FOUND`(없음·남의 결제) · 409 `PAYMENT_ORDER_MISMATCH`·`PAYMENT_NOT_PAID` · 503 `PAYMENT_GATEWAY_UNAVAILABLE`·`PAYMENT_NOT_CONFIGURED` |
| `POST /api/payments/webhooks/portone` | 공개(서명) | 원본 본문 ≤ 64KB + 머리글 셋 → 본문 없는 200·400·503·500 |
| `POST /api/dev/payments` | 회원 · `test-orders` 서버만 | `{amount(100~1,000,000), validMinutes(1~60), decision?: ACCEPT\|REJECT\|ERROR}` → 201 `{orderType: PAYMENT_TEST, orderRefId, expiresAt(= 만든 시각 + N분), checkout: {gateway: FAKE\|PORTONE, paymentId, orderName, amount, currency, storeId, channelKey}}` · 400 `fields.amount`·`fields.validMinutes`(둘 다면 둘 다) · 503 `PAYMENT_NOT_CONFIGURED` |
| `POST /api/dev/payments/{paymentId}/fake-checkout` | 회원 | `{outcome: PAID\|FAILED, amount?, late?}` → 204(가짜 PG 에 `INSERT … ON CONFLICT DO NOTHING` 으로 처음 한 번만). `late=true` = 결제 시각 = 유효 시각 + 1분. **본인의 결제 주문이면 종류와 관계없이**(공구 결제 포함) · 404(남의 결제를 먼저 봄) · 409 `PAYMENT_GATEWAY_NOT_FAKE`·`FAKE_PG_ALREADY_DONE` · 400 `fields.outcome`·`fields.amount`(1원 미만) |
| `GET /api/dev/payments/{paymentId}` | 회원 | `PaymentResult`(펼침) + `handlerCash`·`cancels[{id, kind, amount, status, pgStatus, attempts}]` |
| `GET /api/admin/payments` | `commerce.payment.read` | `status·anomaly(종류\|ANY)·orderType·from·to·q(결제 번호 일부\|회원 번호)` → `{items, total}`(만든 시각 내림차순) |
| `GET /api/admin/payments/{id}` | 같음 | 상세(+ events·cancels·webhooks 최근 50·openIssueCount) · 감사 `PAYMENT_ORDER_VIEW` · 404 |
| `POST /api/admin/payments/{id}/recheck` | `commerce.reconcile.manage` | `{reason}` → `{outcome, detail}` · 503(감사 없음) |
| `GET /api/admin/payment-webhooks` | `commerce.payment.read` | `result·from·to·q` → `{items, total}` |
| `GET /api/admin/payment-anomalies` | 같음 | `kind(이상 종류\|CANCEL_GAVE_UP)·includeResolved·from·to` → `{items, total, openCount}` |
| `POST /api/admin/payment-cancels/{id}/retry`·`/manual` | `commerce.reconcile.manage` | `{reason}`(manual 은 + 방법·포트원 취소 번호 — 콘솔 취소면 필수, 모르는 방법 400) → 취소 한 줄 · 409 `PAYMENT_CANCEL_NOT_GAVE_UP` · 404 `PAYMENT_CANCEL_NOT_FOUND` |
| `GET /api/admin/payment-reconciliation` | `commerce.payment.read` | `{lastCheckedDay, nextRunAt, days[14](어제부터), openIssueCount}` |
| `GET …/issues` | 같음 | `type·status·day` → `{items, total}`(열린 것 먼저) |
| `POST …/issues/{id}/resolve` | `commerce.reconcile.manage` | `{reason}` → 이슈 한 줄 · 409 `RECONCILE_ISSUE_NOT_OPEN` · 404 `RECONCILE_ISSUE_NOT_FOUND` |
| `POST …/days/{day}/rerun` | 같음 | 본문 없음 → 그 날 줄(RUNNING) · 409 `RECONCILE_ALREADY_RUNNING` · 400 `fields.day`(오늘·미래·못 읽음) |
| 포트원 설정 | `site.read`/`site.secret` | 결정 14, `commerce-foundation-contract.md` 「0-4」 API 표 |

## 구현 때 정한 것
**2a-2**
1. **비교 금액 = 포트원 `amount.total`**(카드사 할인으로 `amount.paid` 가 작을 수 있다). `paid_amount` 에도 `amount.total`(취소 금액의 기준).
2. **우리 취소 표시**: 포트원에 보내는 `reason` = `roboyam <종류> rc:<취소 ID>`(내부 사유는 보내지 않는다). 취소 아이디를 못 받아도 재시도 전 조회·취소 웹훅이 이 표시로 우리 취소를 알아본다.
3. **재시도 횟수**: 즉시 시도 1 + 재시도 5 = 최대 6번, 간격 1·5·30·120·720분. REQUESTED(접수만)는 다시 보내지 않고 조회로 확인.
4. **잠그는 차례**: 취소 결과 기록·웹훅 맞춤·운영자 처리 모두 결제 주문 행 → 취소 줄. 포트원 취소 호출 자체는 잠금 밖(고침 — 「2a 보강」).
5. **커밋 뒤 즉시 시도는 새 트랜잭션으로 집는다**(`AFTER_COMMIT` 단계의 연결은 끝난 트랜잭션에 묶여 있다).
6. **남은 취소 가능 금액** = 결제 − 취소됨 − 끝나지 않은 취소(PENDING·SENDING·GAVE_UP) — 넘치면 409 `PAYMENT_CANCEL_EXCEEDS`. 같은 요청 키는 처음 줄을 돌려준다.
7. **웹훅**: 「웹훅 처리 표」.
8. **콘솔 직접 취소(P18)**: 포트원 취소 아이디마다 `EXTERNAL_CANCEL` 한 줄 + 열린 `CANCEL_MISMATCH` 하나(다시 와도 늘지 않는다).
9. **처리기 없는 종류**: 결정 7·패키지 `PaymentOrderHandlers`.
10. **운영자 알림** `COMMERCE_PAYMENT_ANOMALY`: 결정 10.
11. **감사 행위**: 결정 12·17·18 아래 목록.
12. `PaymentReadiness` 는 두지 않는다 — `PaymentGateway.ready()`(포트원: `paymentReady`).

**2a-3**
1. **대사 비교**(16-1): 포트원 「결제 계열」 = PAID·PARTIAL_CANCELLED·CANCELLED. 우리 주문 없음 + 결제 계열 → `PG_ONLY` · 우리 READY/EXPIRED + 결제 계열 → `PG_PAID_LOCAL_UNPAID` · 우리 결제 계열 + 포트원 없음/결제 아님 → `LOCAL_PAID_PG_MISSING` · `paid_amount` ↔ `amount.total` 다름 → `AMOUNT_MISMATCH` · 취소 누계 다름 → `CANCEL_MISMATCH`(둘 다 날 수 있다). 하루 = KST 날짜의 다건 조회(`STATUS_CHANGED_AT`, 100건씩, 60,000건 한도) + 그 날 결제되거나 취소가 끝난 우리 결제 중 목록에 없던 것은 단건 조회. 이슈 `detail` = 포트원 값 ↔ 우리 값(개인정보 없음).
2. **「아직 안 한 날 전부」** = 처음 결제 주문이 생긴 날(KST)부터 어제까지 중 DONE·MISMATCH 가 아닌 날. 날 하나 = 배치 한 건(체크포인트 = 날짜), 포트원 장애는 그 날만 FAILED + 실패 건 `PAYMENT_RECONCILE_DAY`. 날짜 상태·이슈는 각각 새 트랜잭션. `issue_count` = 그 날 마지막 대사의 불일치 수, 화면 「열림」 = 그 날 발견해 아직 열린 수.
3. **날짜 상태 저장**: V98 이 상태를 화면 이름으로. 「이 날 다시 대사」는 줄을 바꾸지 않고 배치 요청 표(`batch_run_requests` NOW + `{day}`)에 넣는다 — 화면 RUNNING 은 끝나지 않은 요청의 날짜. 끝나지 않은 요청이 있으면 409.
4. **운영자 알림** `COMMERCE_RECONCILE_MISMATCH`: 결정 10.
5. **다시 확인**: 확정한 목업 가정 2.
6. **다시 시도·수동 처리**: 확정한 목업 가정 1. 잠금은 결제 주문 행 → 취소 줄.
7. **결제 이상 목록**: 확정한 목업 가정 4.
8. **이력 종류·detail**: 확정한 목업 가정 6.
9. **감사** 「이 날 다시 대사」 = `PAYMENT_RECONCILE_RERUN`(대상 `PAYMENT_RECONCILE_DAY` + 날짜, 일반).
10. **포트원 설정**: 결정 14.
- **세션 정리 결정**: 관리자 조회는 JPA + QueryDSL 대신 **JDBC 읽기 저장소**(`PaymentAdminQueries`) — 결제 저장소가 모두 JDBC 이고 결제 이상 목록이 두 출처의 `UNION ALL`·`jsonb` 라서(정렬·쪽 나누기는 DB).

**2a-4**
- 회원 API 는 2a-1 화면 계약에 맞췄다(화면 계약이 정본): 시험 주문 `POST /api/dev/payments`(`validMinutes` 로 유효 시각 — 고정 10분 상수 없음), 가짜 결제창 `…/fake-checkout`, 결과 `PaymentResult`(완료 API·`GET` 공용). `checkout` 은 공용 `PaymentCheckoutView`(내부 `paymentOrderId` 는 내리지 않음).
- **`cancel` 고르는 규칙**: 그 결제의 `AUTO_REJECT` 취소 줄(여럿이면 ID 가 가장 큰 것), 없으면 null. 회원 취소·환불 등 다른 종류는 `cancel` 에 싣지 않고 `canceledAmount` 로만.
- 로컬 포트원 모드: `.claude/launch.json` 의 `api-portone`·`admin-portone`(`ROBOYAM_PAYMENT_GATEWAY=portone`·`ROBOYAM_IDENTITY_PROVIDER=portone`).
- 본인인증(`useIdentity`): 포트원이면 `PortOne.requestIdentityVerification`(이름 `PortOne` 팝업) → 서버 confirm. 창을 닫으면 오류 안내 없이 끝나고 시도 기록은 PENDING(30분 뒤 완료 요청이 오면 410 `IDENTITY_VERIFICATION_EXPIRED`). 모바일 리디렉션은 아직 없다(데스크톱 팝업만).

## 2a-4 로컬 실제 확인 (이니시스 V2 테스트 채널)
| 확인 | 결과 |
|---|---|
| 정상 결제(1,000원) | 결제창 → 완료 API → 포트원 조회 → PAID(`COMPLETE_API`), 관리자 목록·상세 같은 값. **`paymentId` 36자를 포트원이 받는다** |
| 늦은 결제(유효 1분 뒤 결제) | LATE_PAYMENT → `AUTO_REJECT` 1회 만에 SUCCEEDED → CANCELED·누계 1,000. 포트원 단건 조회로 같은 취소 ID·사유 `roboyam AUTO_REJECT rc:<취소 ID>` 확인 |
| 결제창 안 간편결제(네이버페이) | 포트원 `PaymentMethodEasyPay` → 화면 「간편결제」 |
| 결제창 닫기 | SDK `code: FAILURE_TYPE_PG`, `pgCode` 없음, 문구 「사용자가 결제를 취소하였습니다」 → `isUserCancel` 규칙 |
| 본인인증 창 닫기 | 단추 원래대로, 시도 PENDING(실명 인증 자체는 하지 않음) |
| 공구 부분 취소 연속 2회 등 | `commerce-groupbuy-contract.md` 「2b-5 로컬 실제 확인」 |

## 2a 보강 — 취소 호출을 잠금 밖으로
- **왜**: 결제 주문 행을 잠근 채 포트원 취소를 부르면 응답을 기다리는 동안 같은 결제의 확정·웹훅·관리자 처리가 막히고, 공구 불발처럼 취소가 몰리면 DB 연결과 잠금이 PG 응답 시간에 묶인다(사용자 지시).
- **흐름**(「확정 흐름」 5): ① 집기 = `PENDING → SENDING` 커밋 ② 재시도 전 조회 ③ 잠금 없이 잔액(`paid − canceled`)을 읽어 포트원 취소 ④ 결과는 새 트랜잭션에서 결제 주문 행을 잠그고 끝내기·재시도·GAVE_UP. 응답 뒤 기록 전에 서버가 죽으면 SENDING 으로 남고 10분 뒤 되돌리기 → 재시도 전 조회가 맞춘다.
- **줄 세우기는 DB 제약으로**(V100): 한 결제에 보내는 중인 취소는 하나. 집기 쿼리는 보내는 중인 취소가 있는 결제를 건너뛰고 한 결제에서는 차례가 가장 이른 한 줄만 집는다. 서버끼리 부딪치면 색인이 둘째를 거절한다(즉시 시도는 「못 집음」, 워커는 그 바퀴를 건너뜀 — 줄은 PENDING 그대로). 같은 결제의 부분 취소 둘이 같은 잔액으로 겹쳐 나가 거절되는 일이 없다.

## 포트원 V2 확인 사항 (OpenAPI·웹훅 문서·Standard Webhooks 규격)
- 웹훅 버전 `2024-04-25`, 본문 `{type, timestamp, data:{storeId, paymentId, transactionId, cancellationId?}}`, 이벤트 `Transaction.Paid`·`Transaction.Cancelled`·`Transaction.PartialCancelled`(그 밖 Ready·VirtualAccountIssued·Failed·PayPending·CancelPending·DisputeCreated/Resolved 는 무시), **`cancellationId` 는 Cancelled·PartialCancelled·CancelPending 에만**. 실패한 웹훅은 최대 5번(0·1·4·16·64·256분 + 흔들림) 다시 보낸다.
- 단건 조회 `GET /payments/{paymentId}?storeId=`(404 `PAYMENT_NOT_FOUND`), 상태 `READY·PENDING·PAY_PENDING·VIRTUAL_ACCOUNT_ISSUED·PAID·FAILED·PARTIAL_CANCELLED·CANCELLED`, 금액 `amount.total`·`amount.cancelled`, `paidAt`·`pgTxId`·`receiptUrl`·`method.type`·`channel.pgProvider`·`cancellations[{status, id, totalAmount, reason, cancelledAt}]`, 에스크로 여부 `escrow`.
- 취소 `POST /payments/{paymentId}/cancel` `{storeId, amount, currentCancellableAmount, reason(필수)}` → `{cancellation:{status: REQUESTED|SUCCEEDED|FAILED, id, …}}`, 거절 409 `CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN`·`PAYMENT_ALREADY_CANCELLED`·`CANCEL_AMOUNT_EXCEEDS_CANCELLABLE_AMOUNT` 등, PG 오류 502.
- 다건 조회는 페이지 기반 `GET /payments` — 본문을 쿼리 `requestBody` 에 JSON(`{page:{number,size}, filter:{storeId, timestampType: CREATED_AT|STATUS_CHANGED_AT, from, until}}`, `(number+1)×size ≤ 60,000`), 대용량은 커서 기반 `GET /payments-by-cursor`(생성일시 기준, 「불안정」).
- 에스크로 `POST·PATCH /payments/{id}/escrow/logistics`·`POST …/escrow/complete`(4xx 중 `ALREADY…` 는 성공 — 실제 응답은 dev 확인).
- 주문명은 PG 마다 40바이트(나이스·이니시스) 등 제한.

## 규칙 → 테스트 (모두 실제 PostgreSQL, 포트원은 가짜 PG·HTTP 흉내. shared `com.community.payment` 가 기본)
| 규칙 | 테스트 |
|---|---|
| CHECK(취소 ≤ 결제·결제 계열 칸·통화·상태·이상·확정 길), payment_id 형식·유일, 받아들인 결제 하나(이상·끝난 결제·READY 는 셈 밖), request_key·pg_cancel_id 유일, webhook-id 유일, 열린 이슈 유일(처리하면 다시 열 수 있음), FK 없음 | `PaymentSchemaTest`(10) |
| `pay_`+32자·서버 금액·유효 시각, MANDATORY, 도메인 롤백이면 없음, 재결제 새 번호·이전 READY → EXPIRED, 처리기 없는 종류, 설정 없음 503 | `PaymentOrderServiceTest`(7) |
| 8-2 표 전부(PAID 아님·ACCEPT·REJECT → DOMAIN_REJECTED + 전액 자동 취소·금액/통화 불일치 → onPaid 없이 전액 취소 + 알림·장애 503), 처리기 예외 → 전체 되돌림, 이미 확정이면 조회 없이 같은 결과 | `PaymentConfirmServiceTest`(8) |
| 동시 확정(완료 API·웹훅·READY 정리, 9갈래 × 5회) → onPaid 한 번·PAID 이력 한 줄·자동 취소 한 번 | `PaymentConfirmConcurrencyTest`(2) |
| 결정 4 늦은 결제(P3b·P15 일반형) — EXPIRED 무조건·READY 는 `paidAt > expires_at` 만·재결제 뒤 옛 시도 자동 취소 | `LatePaymentTest`(4) |
| P24 남의 결제 404·다른 주문 409(조회 0회)·결제 전 409 뒤 성공·장애 503·웹훅이 먼저면 같은 결과·`accepted` · 완료 응답의 `cancel`·`canceledAmount` | `PaymentCompletionTest`(7) |
| 넣기 MANDATORY·남은 금액 넘으면 409·같은 키 한 번 / `currentCancellableAmount`·부분 → 전액·onCanceled 한 번·조건부 누계 / **P17** 즉시 1 + 재시도 5 → GAVE_UP·알림·도메인 확정 없음·간격 1·5·30·120·720 / 재시도 전 조회·접수만·오래된 SENDING 10분 되살림·20건·200ms·즉시 시도와 워커는 한쪽만 | `PaymentCancelWorkerTest`(12) |
| 워커 넷이 동시에(`SKIP LOCKED`) → 취소마다 포트원 요청 한 번 | `PaymentCancelConcurrencyTest`(1) |
| P23(서명 틀림·본문 바뀜·머리글 없음·시각 ±5분 → 400 REJECTED·조회 0) · 비밀키 없음 503 · 중복 한 번 · 503 RETRY 뒤 재전송 · IGNORED · **P18** 콘솔 취소 → 상태 불변 + 이슈 하나 · 우리 취소 웹훅 → SUCCEEDED 한 번 | `PaymentWebhookTest`(9) |
| 서명 규칙(규격 시험 값·여러 서명·v1 만·`whsec_` 유무·시각 경계) | `StandardWebhookVerifierTest`(6) |
| READY 정리(카탈로그·여유 10분 경계·EXPIRED·제때 결제 확정·늦은 결제 자동 취소·두 번 같음·`grace_minutes`·건별 실패·사흘 밀려도 전부) | `PaymentReadyCleanupJobTest`(5) |
| 불변식: 취소 누계 = SUCCEEDED 합, 받아들인 결제 − 취소 = 처리기 현금, 이상 결제 현금 0·전액 취소, 포트원 취소액 = 우리 누계 | `PaymentInvariantTest`(1) |
| 가짜는 dev·prod 기동 실패, 시험 주문 prod 기동 실패, 숫자 기본값·잘못된 재시도 설정 기동 실패 | `PaymentConfigTest`(6) |
| 포트원 요청 모양·응답 해석·오류·다건 조회 `requestBody`·`STATUS_CHANGED_AT` / 로그에 비밀값·구매자 이메일·서명·본문 없음·내부 사유 안 보냄 | `PortOnePaymentGatewayTest`(8) · `PaymentLogSecrecyTest`(3) |
| 결정 14 키 문자열 불변·묶음 셋·`paymentReady` = 4칸 + 암호화 키·값 감춤·`whsec_` + base64·카드 밖 칸 거절 | shared `portone.service.PortOnePaymentSettingsTest`(5) · admin `commerce.PortOneSettingsHttpTest`(5) |
| 완료 API CSRF·로그인·끝까지·**속도 제한·멱등 키 없음**(15번 200) · 시험 주문 새 경로·400 두 칸·늦은 결제·`FAKE_PG_ALREADY_DONE` · 웹훅은 로그인·CSRF 없이·64KB 넘으면 400 | api `PaymentCompletionHttpTest` |
| 2a-4 시험 주문·가짜 결제창(범위·경계·`late`·이미 끝냄 409·가짜 아님 409·남의 결제 404·다시 읽기·문구 두 언어) | `PaymentTestOrderServiceTest`(9) |
| 워커 「언제 돌릴지」 — 배치를 끈 서버는 안 돎·워커 기록·가린 실패 | admin `batch.service.PaymentCancelSchedulerTest`(3) |
| 대사 5종·자동 보정 없음·날짜 줄·알림 하나·중복 없음·놓친 날 메꿈·날짜 지정·장애 FAILED → 다음 실행 | `PaymentReconciliationJobTest`(6) |
| `R__batch_31` 정의·90일 지난 웹훅 기록만 삭제·`retention_days` 하한 | `PaymentCleanupBatchTest`(3) |
| 관리자 결제 API: 권한 403·목록·상세(닉네임·번호만·열람 감사)·웹훅·결제 이상·다시 시도·수동 처리(누계 불변)·다시 확인(세 결과·콘솔 취소 이슈·멈춘 취소 SUCCEEDED) | admin `payment.PaymentAdminHttpTest`(11) |
| 대사 관리자: 권한·요약 14일·이슈 목록·처리(사유·감사 MONEY·409·404)·다시 대사(배치 요청·감사 GENERAL·409) | admin `payment.ReconciliationAdminTest`(5) |
| 결제 API 선언 = 계약·두 권한 준비됨·처리는 SENSITIVE / 메뉴 다섯 줄·감사 분류·대상 넷·알림 둘·배치 두 줄·문구 두 언어 | admin `permission.config.Commerce2aPermissionDeclarationTest`(2) · `commerce.Commerce2aCatalogTest`(6) · `CommercePermissionsAndMenuTest` |
| 2a 보강 — 포트원 호출 중 다른 연결이 결제 행을 `FOR UPDATE NOWAIT` 로 잠금(즉시 시도·워커·거절) · 한 결제 한 줄만 집힘 · 다른 결제는 줄 서지 않음 · 서버 둘 × 부분 취소 30건 → 요청 30번·전부 첫 시도 성공 | `PaymentCancelOutsideLockTest` |
| 2a-3 이 바꾼 기존 시험 — 포트원 핸들러 다섯 선언·카드 단위 저장·지우기·생성자 | admin `Commerce04PermissionDeclarationTest` · api `PortOneIdentitySettingsTest` · shared `PortOneSettingsServiceTest`·`IdentityConfigTest`·`PaymentConfigTest` |
| 결정 74 수동 처리 방법(공구 계약) | admin `GroupBuyAdminHttpTest.결정74_…`(공구 계약) |
- 테스트로 막지 못하는 것: 실제 포트원 호출·웹훅 전송·재전송, 상수 시간 비교의 실제 시간, pg_cron 등록, 워커·배치의 실제 예약 주기, 운영자 알림의 실제 발행(무엇을 냈는가까지), 브라우저 결제창(타입 검사만).

## 미확정·남은 일
- **dev 서버 확인**(공개 주소 필요): 웹훅 실제 수신·재전송, 콘솔 직접 취소(P18 `CANCEL_MISMATCH`), dev 의 `ROBOYAM_PAYMENT_TEST_ORDERS` 를 compose 에 넣기, 다건 조회 실제 응답·60,000건 한도. 공구 쪽 확인 목록(에스크로·네이버페이·카카오페이 부분 취소·가맹점 취소 한도)은 공구 계약 「남은 일」.
- **포트원**: 웹훅 비밀키의 실제 형식, 채널별 승인/매입 분리.
- 본인인증 실제 완료(실명 인증)·모바일 리디렉션 복원.

## 옛 절 이름 → 새 위치 (코드 주석·commerce.md 가 옛 이름을 가리킨다)
| 옛 절 | 새 위치 |
|---|---|
| 「2a-2 구현 결과」(포트원 문서로 확인한 것·구현 때 정한 것·API) | 「포트원 V2 확인 사항」·「구현 때 정한 것」 2a-2·「2a-1 화면 목업」 API |
| 「2a-3 구현 결과」(구현 때 정한 것·API·세션 정리 결정) | 「구현 때 정한 것」 2a-3·「2a-1 화면 목업」 API |
| 「2a-2·2a-3 규칙 → 테스트 결과」 | 「규칙 → 테스트」 |
| 「2a-4 준비」·「2a-4 진행」 | 「구현 때 정한 것」 2a-4·「2a-4 로컬 실제 확인」 |
| 「develop 병합」·「미확정」 | 「마이그레이션 계획」·「미확정·남은 일」 |
