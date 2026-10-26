# 커머스 2a 계약 — 결제 모듈 단독 (ERD·패키지 제안 승인 2026-10-26 — 구현 전)

**의도**: 공구(2b)·충전(3)이 함께 쓸 현금 결제 모듈을 도메인 없이 먼저 다진다 — 결제 주문표 하나·확정 코드 하나·취소 대기열 하나, 포트원 V2 연동, 웹훅, READY 정리, 대사. 가짜 결제 게이트웨이(local·시험)와 포트원 테스트 채널(dev)로 검증한다.
정책 원천은 `docs/domain/commerce.md` 1-2(D10)·2-3·3·8·12-4·13-2·13-4·14·15·16·18·19·20·22절이고, 0단계 장치(포트원 설정 표·멱등·메일 대기열·배치 core·워커 기록·시험 DB)는 `commerce-foundation-contract.md` 에 있다. 이 문서는 2a 에서 정한 구조와 결정만 적는다.
**테스트**: 이 문서와 commerce.md 에 적힌 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절). 잠금·동시성·제약은 `CommerceTestDatabase`(실제 PostgreSQL), 포트원은 대역 HTTP.

## 작업 단위와 순서
| 단위 | 내용 | 담당 | 상태 |
|---|---|---|---|
| 2a-1 화면 목업 | admin 결제 주문(목록·상세 서랍: 이력·취소·웹훅) · 웹훅 수신 내역 · 결제 이상 · 대사 불일치 · `/settings/portone` 「결제」 카드, user-web `/dev/payment`(시험 주문 + 가짜 결제창) | frontend | 시작 |
| 2a-2 결제 핵심 서버 | V90, Fake·포트원 게이트웨이, 주문 만들기·확정·완료 API·시험 핸들러, 웹훅, 취소 대기열·워커, READY 정리, 규칙 테스트 | backend | 시작 |
| 2a-3 대사 + 관리자 API + 연결 | 대사 배치, `R__batch_31`, 관리자 조회·처리 API, 포트원 결제 칸·API 정리, 알림·감사, 화면 연결 | backend → frontend | 2a-1 확정·2a-2 병합 뒤 |
| 2a-4 테스트 채널 실제 확인 | dev 에 포트원 테스트 채널 키·웹훅 주소 등록, 사람이 결제창으로 결제·취소·콘솔 직접 취소 확인. 브라우저 SDK `@portone/browser-sdk` 추가(**승인 2026-10-26**, 버전은 설치 때 확인) | 사용자 + 세션 | 마지막 |
순서: 2a-1 ∥ 2a-2 → 화면 확정 → 2a-3 → 2a-4. 2a-2 는 화면과 무관한 서버 핵심이라 화면 확정 전에 병행한다(사용자 승인 2026-10-26). 세션마다 자기 worktree.

## 마이그레이션 계획 (가번호 — develop 에 합칠 때 다시 확인)
| 가번호 | 스트림 | 내용 | 단위 |
|---|---|---|---|
| V90 `payment_module` | shared | 아래 표 7개 | 2a-2 (대사 두 표도 여기 — 2a-3 이 쓴다) |
| `R__batch_31_payment_webhook_log_cleanup` | shared | pg_cron 매일, 90일 지난 웹훅 기록 삭제(`batch_params` `retention_days`) | 2a-3 |
- develop 64123b5 기준 shared 마지막 V89 · 반복 `R__batch_29`(admin 스트림이 `R__batch_30`).
- Java 배치(카탈로그만): `payment-ready-cleanup`(10분, 2a-2) · `payment-reconciliation`(매일 04:50 KST, 날짜 파라미터·이어 받기, 2a-3). 워커 `payment-cancel`(1분, `worker_heartbeats`, 2a-2).
- `portone_settings` 는 키 CHECK 가 없어 표를 바꾸지 않는다(코드 `PortOneSettingKey` 만).

## 표 (FK 없음 — 회원은 `user_id`, 대상은 `order_type + order_ref_id`. 금액 bigint, 시각 timestamptz, ID Snowflake)
- **`payment_orders`**(결제 시도 한 건 = 한 줄): id, `payment_id`(서버 생성 `pay_` + 무작위 32자, 유일), user_id, order_type(`POINT_CHARGE`·`GROUP_BUY`·`PAYMENT_TEST`), order_ref_id, order_name, amount(> 0, 서버 계산), currency(`KRW` CHECK), status(`READY`·`PAID`·`PARTIAL_CANCELED`·`CANCELED`·`EXPIRED`), paid_amount, canceled_amount(CHECK 0 ≤ canceled ≤ paid), anomaly(null·`AMOUNT_MISMATCH`·`DOMAIN_REJECTED`·`LATE_PAYMENT`), pay_method, pg_provider, pg_tx_id, receipt_url, paid_at, expires_at, confirmed_via(`COMPLETE_API`·`WEBHOOK`·`READY_CLEANUP`·`ADMIN_RECHECK`), created_at, updated_at. CHECK: PAID 계열이면 paid_amount·paid_at 필수.
  - 색인: 유일 `payment_id` · **유일 `(order_type, order_ref_id) WHERE status IN ('PAID','PARTIAL_CANCELED') AND anomaly IS NULL`**(결정 3) · `(expires_at) WHERE status='READY'` · `(user_id, created_at)` · `(order_type, order_ref_id)` · `(paid_at)` · `WHERE anomaly IS NOT NULL`.
- **`payment_cancels`**(모든 PG 취소의 대기열): id, payment_order_id, amount(> 0), reason(내부), kind(`USER_CANCEL`·`REFUND`·`AUTO_REJECT`·`ADMIN`·`CHARGE_REFUND`), `request_key`(유일 — 예 `AUTO_REJECT:{주문 ID}`), status(`PENDING`·`SENDING`·`SUCCEEDED`·`GAVE_UP`·`MANUAL` — 결정 2), pg_status(`REQUESTED`·`SUCCEEDED`·`FAILED`), pg_cancel_id(있으면 유일), attempts, next_attempt_at, locked_by, locked_at, last_error(가림), requested_by, resolved_by, resolve_note, created_at, completed_at. 색인 `(next_attempt_at) WHERE status='PENDING'` · `(payment_order_id)` · `WHERE status='GAVE_UP'`.
- **`payment_events`**(결제 한 건의 이력 — 5년 거래 기록): id, payment_order_id, kind(`CREATED`·`PAID`·`ANOMALY`·`EXPIRED`·`CANCEL_REQUESTED`·`CANCEL_SUCCEEDED`·`CANCEL_GAVE_UP`·`CANCEL_MANUAL`·`EXTERNAL_CANCEL` 등), `provider_event_id`(웹훅 `webhook-id`, **부분 유일**), detail(jsonb, 개인정보 없음), created_at. 색인 `(payment_order_id, created_at)`.
- **`payment_webhook_logs`**(받은 웹훅 전부 — 90일): id, webhook_id, event_type, payment_id, result(`PROCESSED`·`IGNORED`·`REJECTED`·`RETRY`), http_status, error_code, received_at. **본문은 저장하지 않는다.** 색인 `(received_at)` · `(payment_id)` · `(result, received_at)`.
- **`reconciliation_issues`**: id, type(`PG_PAID_LOCAL_UNPAID`·`LOCAL_PAID_PG_MISSING`·`AMOUNT_MISMATCH`·`CANCEL_MISMATCH`·`PG_ONLY`), payment_id, payment_order_id(null 가능), detected_day, detail, status(`OPEN`·`RESOLVED`), resolve_note, resolved_by, resolved_at, created_at. **유일 `(type, payment_id) WHERE status='OPEN'`**(다시 돌려도 중복 없음). 콘솔 직접 취소 웹훅(P18)도 `CANCEL_MISMATCH` 로 바로 남긴다.
- **`payment_reconcile_days`**: day(PK, KST 날짜), status, checked_count, issue_count, run_id, finished_at — 「마지막으로 대사한 날 다음부터 어제까지」.
- **`fake_pg_payments`**(가짜 PG 의 결제 상태 — local·시험 전용, dev·prod 에서는 비어 있다, 결정 8): payment_id(PK), status, amount, currency, paid_at, cancelled_amount, cancellations(jsonb), updated_at.

## 패키지 (`payment` 는 다른 도메인을 모른다)
- shared `com.community.payment`:
  - `dto` — `PaymentOrderType`·`PaymentStatus`·`PaymentAnomaly`·`CancelKind`·`CancelStatus`·`PaymentDecision`(ACCEPT | REJECT(사유))·`PaidPayment`·`CanceledPayment`
  - `handler` — **`PaymentOrderHandler`**(`orderType()`·`onPaid(PaidPayment) → PaymentDecision`·`onCanceled(CanceledPayment)`) · `PaymentOrderHandlers`(종류마다 정확히 하나 — 기동 검사)
  - `gateway` — `PaymentGateway`(결제 단건 조회·취소·다건 조회·웹훅 서명 검증·결제창 정보·준비 여부) · `gateway.portone`(`PortOnePaymentGateway`·`StandardWebhookVerifier`·`PortOnePaymentParser`) · `gateway.fake`(`FakePaymentGateway`·`FakePgStore`)
  - `config` — `PaymentConfig`(Fake 면 dev·prod 기동 막기 — `IdentityConfig.guard` 와 같은 모양) · `PaymentProperties`
  - `repository` — 상태 전이·대기열 집기는 JDBC 저장소(조건부 UPDATE·`SKIP LOCKED`), 관리자 조회는 읽기 전용 JPA + QueryDSL
  - `service` — `PaymentOrderService.create`(MANDATORY — 도메인 트랜잭션 안. 같은 주문의 이전 READY 는 EXPIRED 로 닫음) · **`PaymentConfirmService.confirm`**(확정 코드 하나 — 완료 API·웹훅·READY 정리·관리자 다시 확인이 함께 쓴다) · `PaymentCompletionService` · `PaymentWebhookService` · `PaymentCancelService`(넣기, MANDATORY) · `PaymentCancelExecutor`(한 건) · `PaymentCancelWorker.runOnce` · `PaymentAnomalyService` · `ReconciliationService` · `PaymentReadiness`
  - `batch` — `PaymentReadyCleanupJob` · `PaymentReconciliationJob`
  - `testorder` — `PaymentTestOrderHandler`(`PAYMENT_TEST`, `roboyam.payment.test-orders` 를 켠 환경만)
- api `payment.controller`: 완료 API, 웹훅, `PaymentTestController`(`/api/dev/payments`, `test-orders` 일 때만).
- admin `payment.controller`: 결제 주문·웹훅 내역·결제 이상·대사(2a-3), `batch.service.PaymentCancelScheduler`(1분, `roboyam.batch.enabled` 인 서버만 — `MailOutboxScheduler` 모양). 나중에 `server/batch` 로 뺄 때는 스케줄러 등록만 옮긴다(15-2).

## 확정 흐름 (8-2 를 구체화)
1. 포트원 단건 조회는 **트랜잭션 밖**(DB 잠금을 쥔 채 HTTP 를 기다리지 않는다). 조회 결과의 `id`·`storeId` 가 우리 것이어야 한다.
2. 한 트랜잭션: `READY|EXPIRED → PAID` 조건부 UPDATE(이긴 쪽만) → 늦은 결제 판정(결정 4) → 금액·통화 → `onPaid` → REJECT 면 anomaly + `AUTO_REJECT` 취소 넣기. 늦은 결제·금액 불일치는 `onPaid` 를 부르지 않는다.
3. 핸들러 예외 = 전체 되돌림(READY 그대로, 취소 없음) → 다음 웹훅·READY 정리가 다시 시도.
4. 커밋 뒤 이벤트로 그 자리에서 취소를 한 번 시도, 실패하면 1분 워커가 이어 받는다(D10 「즉시 시도」). 워커와 즉시 시도는 `PENDING → SENDING`(+`locked_by`) 조건부 UPDATE 로 한쪽만 집는다.
5. 취소 실행: 결제 주문 행 잠금 → 포트원 `POST /payments/{id}/cancel`(`amount`·`currentCancellableAmount`·`reason`) → 성공이면 `canceled_amount` 조건부 증가(`WHERE canceled_amount + :amt <= paid_amount`)·상태 갱신·`onCanceled`. **재시도 전에는 먼저 결제를 조회해 이미 취소됐으면 SUCCEEDED 로 맞춘다**(시간 초과·「접수만」 대비). 간격 1·5·30·120·720분, 5회 실패 → `GAVE_UP` + 결제 이상 목록 + 운영자 알림.

## 세션 정리 결정 (제안 ⑥ — 모두 추천안으로 승인 2026-10-26)
1. **PG 설정 위치**: DB(`portone_settings`). commerce.md 8-5 의 「환경 변수」를 고쳤다. 포트원/가짜 선택만 배포 설정 `roboyam.payment.gateway`(공통 portone, local fake).
2. **취소 대기열 상태**: 우리 상태 `PENDING`·`SENDING`·`SUCCEEDED`·`GAVE_UP`·`MANUAL`, 포트원 응답은 `pg_status` 칸. 재시도 전에 조회로 확인.
3. **받아들인 PAID 하나**: 부분 유일 색인에 `AND anomaly IS NULL` — 이상 결제(늦은 결제·도메인 거절·금액 불일치)가 정상 결제와 부딪쳐 확정이 되돌아가지 않게.
4. **늦은 결제 판정**: 우리 상태가 EXPIRED 면 무조건 늦음. READY 면 포트원 `paidAt > expires_at` 일 때만 늦음(확인 요청이 늦게 도착한 것은 회원 탓이 아니다). 숨은 여유 시간 없음.
5. **웹훅을 받는 서버**: api(공개 쪽). `POST /api/payments/webhooks/portone`, `SecurityConfig` permitAll + CSRF 제외, 본문 64KB 제한, 원본 바이트로 서명 검증.
6. **완료 API**: 공용 `POST /api/payments/{paymentId}/complete` + `{orderType, orderRefId}` — 그 회원·그 주문의 결제가 아니면 거절(P24, 포트원 조회 없음). 속도 제한·멱등 키 없음(12-4). 결제 시작 API 는 도메인마다.
7. **시험 주문**: `PAYMENT_TEST` + 시험 핸들러는 `roboyam.payment.test-orders` 로 local·dev 만, **prod 에서 켜면 기동을 막는다**.
8. **가짜 PG 상태**: DB 표 `fake_pg_payments`(api·admin 두 서버가 같은 가짜 결제를 본다).
9. **브라우저 SDK**: `@portone/browser-sdk` 를 2a-4 에서 넣는다(승인). 2a-1 의 `/dev/payment` 는 가짜 결제창만.
10. **운영자 알림**: 새 `COMMERCE_PAYMENT_ANOMALY`(CRITICAL — 최종 취소 실패·금액 불일치) · `COMMERCE_RECONCILE_MISMATCH`(WARNING, 하루 한 번), 받는 사람 `commerce.reconcile.manage` 보유자, 영역 GROUPBUY. 예약된 `COMMERCE_ORDER_ANOMALY` 는 2b 용으로 둔다.
11. **회원 알림·메일**(자동 취소 안내 등): 도메인이 `onCanceled` 에서 보낸다. 2a 에는 회원 알림이 없다.
12. **결제 이상·대사 처리 권한**: 「다시 시도」·「수동 처리 완료」·「포트원에서 다시 확인」·대사 처리 = `commerce.reconcile.manage` + 사유(5~200자) + 감사(돈 관련). 조회 = `commerce.payment.read`. 두 권한 `featureReady=true`.
13. **메뉴**: 보통 모드 새 묶음 「결제」(`menu.payment`, 포인트 상점 다음) — 결제 주문 · 웹훅 수신 내역 · 결제 이상 · 대사 불일치. PG 설정은 `/settings/portone` 그대로.
14. **포트원 설정**: `PortOneSettingKey` 묶음을 공통(storeId·apiSecret)·본인인증(identityChannelKey)·결제(`paymentChannelKey`·`webhookSecret`)로 나눈다(키 문자열 불변). 화면 카드 셋, API `PUT /api/admin/portone-settings/{common|identity|payment}` · `DELETE …/{key}`. `paymentReady` = 결제에 필요한 4칸(상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키) + 암호화 키. 0-4 계약의 API 표는 2a-3 구현 때 고친다.
15. **결제 채널 키**: 2a 는 하나(PG 통합 결제창 안의 간편결제 포함).
16. **대사**: 포트원 다건 조회 기준 `STATUS_CHANGED_AT`, 하루 = KST 날짜, 매일 04:50 KST, 날짜 지정 다시 실행, 자동 보정 없음.
17. **결제 화면 개인정보**: 회원 번호·닉네임만(이메일 없음). 상세를 열면 `PAYMENT_ORDER_VIEW`(개인정보 열람, 2년).
18. **숫자 위치**: 워커 구조(한 번에 20건·건 사이 간격·재시도 간격·최대 5회)는 배포 설정 `roboyam.payment.*`, 배치 값(READY 여유 기본 10분·웹훅 기록 90일)은 `batch_params`, 웹훅 시각 오차 5분은 코드 상수.
- 감사 행위와 분류: `RECONCILE_ISSUE_RESOLVE`·`PAYMENT_CANCEL_RETRY`·`PAYMENT_CANCEL_MANUAL`·`PAYMENT_RECHECK` = 돈 관련, `PAYMENT_ORDER_VIEW` = 개인정보 열람.
- 오류 코드 후보: `PAYMENT_NOT_PAID`(409) · `PAYMENT_ORDER_NOT_FOUND`(404) · `PAYMENT_ORDER_MISMATCH`(409) · `PAYMENT_GATEWAY_UNAVAILABLE`(503) · `PAYMENT_NOT_CONFIGURED`(503).

## 웹훅 처리 표 (8-4 를 구체화)
| 경우 | 응답 | 기록 |
|---|---|---|
| 머리글 없음·서명 틀림·시각 오차 5분 초과 | 400, 아무것도 바꾸지 않고 포트원 조회도 없음 | REJECTED |
| 같은 `webhook-id` 를 이미 처리 | 200 | PROCESSED(다시) — 이벤트 줄은 하나 |
| 우리 결제가 아님(storeId 다름·payment_id 없음)·모르는 이벤트 | 200 | IGNORED |
| `Transaction.Paid` | 조회 후 확정 코드. 포트원이 아직 PAID 아님·포트원 장애 → 503 | PROCESSED / RETRY |
| `Transaction.Cancelled`·`PartialCancelled` | 조회해 포트원 취소 목록과 `payment_cancels` 를 `pg_cancel_id` 로 맞춤. 우리 것이 아닌 취소(콘솔 직접 취소) → 상태 불변 + `CANCEL_MISMATCH` 이슈(P18) | PROCESSED |
- 서명: Standard Webhooks — 서명 대상 `{webhook-id}.{webhook-timestamp}.{본문}`, 비밀키는 `whsec_` 뒤 base64, HMAC-SHA256, `webhook-signature` 의 `v1,<서명>` 여러 개 중 하나라도 맞으면 통과, 상수 시간 비교.

## 규칙 → 테스트 (구현 세션이 채운다 — 모두 실제 PostgreSQL)
| 규칙 | 테스트 | 단위 |
|---|---|---|
| CHECK(취소 ≤ 결제·PAID 칸·통화·이상 값), payment_id 유일, 받아들인 PAID 하나(이상 제외), webhook-id 유일, request_key 유일, 열린 이슈 유일 | `PaymentSchemaTest` | 2a-2 |
| `pay_`+32자·서버 금액·유효 시각, MANDATORY, 재결제 새 번호·이전 READY → EXPIRED | `PaymentOrderServiceTest` | 2a-2 |
| 8-2 표 전부(PAID 아님 409·ACCEPT·REJECT → 자동 취소·금액/통화 불일치 → onPaid 없이 전액 취소·포트원 장애 503), 핸들러 예외 → 전체 되돌림 | `PaymentConfirmServiceTest` | 2a-2 |
| 동시 확정(완료 API·웹훅·READY 정리 동시에, 연결 여럿) → onPaid 정확히 한 번 | `PaymentConfirmConcurrencyTest` | 2a-2 |
| 중복 웹훅 처리 한 번 | `PaymentWebhookTest` | 2a-2 |
| 늦은 결제(P3b·P15 일반형, 결정 4) | `LatePaymentTest` | 2a-2 |
| P17 취소 5회 실패 → GAVE_UP·이상 목록·운영자 알림·도메인 확정 없음, 간격 1·5·30·120·720 | `PaymentCancelWorkerTest` | 2a-2 |
| `currentCancellableAmount`, 취소 누계 조건부 증가, 부분/전액 상태, 넘치는 취소 거절, onCanceled 한 번, 재시도 전 조회, 오래된 SENDING 되살림, 20건·간격, 커밋 뒤 즉시 시도 | `PaymentCancelWorkerTest` | 2a-2 |
| 워커 여럿이어도 같은 취소 한 번(`SKIP LOCKED`) | `PaymentCancelConcurrencyTest` | 2a-2 |
| P18 콘솔 직접 취소 → 상태 불변 + 이슈, 우리 취소 웹훅 → 그 줄 SUCCEEDED | `PaymentWebhookTest` | 2a-2 |
| P23 서명 틀림·시각 초과·머리글 없음 → 400·포트원 조회 없음·상태 불변·REJECTED, 서명 규칙 | `StandardWebhookVerifierTest`, `PaymentWebhookTest` | 2a-2 |
| 모르는 결제·이벤트 200 IGNORED, 장애·아직 PAID 아님 503 RETRY, 본문을 믿지 않음 | `PaymentWebhookTest` | 2a-2 |
| P24 다른 주문·다른 회원의 paymentId 거절(포트원 조회 없음), 완료 API 속도 제한·멱등 키 없음 | `PaymentCompletionTest`, `PaymentCompletionHttpTest` | 2a-2 |
| READY 정리: 여유 시간·PAID 면 확정 길·아니면 EXPIRED·장애는 건별 실패·「아직 처리 안 한 것 전부」 | `PaymentReadyCleanupJobTest` | 2a-2 |
| 불변식: 취소 누계 = SUCCEEDED 합, 결제 − 취소 = 시험 핸들러의 현금 | `PaymentInvariantTest` | 2a-2 |
| Fake 는 dev·prod 기동 실패, 시험 주문은 prod 기동 실패, 기본값 | `PaymentConfigTest` | 2a-2 |
| 포트원 요청 모양·응답 해석·오류·로그에 비밀값 없음 | `PortOnePaymentGatewayTest`, `PaymentLogSecrecyTest` | 2a-2 |
| 대사 5종·자동 보정 없음·중복 없음·놓친 날 메꿈·날짜 지정·처리 권한/사유/감사 | `PaymentReconciliationJobTest`, `ReconciliationAdminTest` | 2a-3 |
| 포트원 결제 칸: 값 숨김·`paymentReady`·감사 칸 이름만·`site.secret` | `PortOneSettingsHttpTest`(보강) | 2a-3 |
| 권한 준비됨·선언·403, 메뉴, 감사 분류, 문구 두 언어, 웹훅 기록 90일 정리 | `Commerce2aPermissionDeclarationTest`·`Commerce2aCatalogTest`·`PaymentCleanupBatchTest` | 2a-3 |
- 테스트로 막지 못할 것: 실제 포트원 호출·웹훅 전송(2a-4 에서 사람이), 상수 시간 비교의 실제 시간, pg_cron 등록, 브라우저 결제창, 워커의 실제 예약 주기.

## 미확정 (포트원 문서로 구현 때 확인 — commerce.md 22절)
- 웹훅 버전과 이벤트 이름(`Transaction.Paid`·`Cancelled`·`PartialCancelled`), 취소 웹훅의 `cancellationId` 유무, 다건 조회 요청 방식(페이지·커서), `paymentId` 길이 제한(우리 36자).
