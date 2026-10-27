# 커머스 2a 계약 — 결제 모듈 단독 (ERD·패키지 제안 승인 2026-10-26 — 구현 전)

**의도**: 공구(2b)·충전(3)이 함께 쓸 현금 결제 모듈을 도메인 없이 먼저 다진다 — 결제 주문표 하나·확정 코드 하나·취소 대기열 하나, 포트원 V2 연동, 웹훅, READY 정리, 대사. 가짜 결제 게이트웨이(local·시험)와 포트원 테스트 채널(dev)로 검증한다.
정책 원천은 `docs/domain/commerce.md` 1-2(D10)·2-3·3·8·12-4·13-2·13-4·14·15·16·18·19·20·22절이고, 0단계 장치(포트원 설정 표·멱등·메일 대기열·배치 core·워커 기록·시험 DB)는 `commerce-foundation-contract.md` 에 있다. 이 문서는 2a 에서 정한 구조와 결정만 적는다.
**테스트**: 이 문서와 commerce.md 에 적힌 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절). 잠금·동시성·제약은 `CommerceTestDatabase`(실제 PostgreSQL), 포트원은 대역 HTTP.

## 작업 단위와 순서
| 단위 | 내용 | 담당 | 상태 |
|---|---|---|---|
| 2a-1 화면 목업 | admin 결제 주문(목록·상세 서랍: 이력·취소·웹훅) · 웹훅 수신 내역 · 결제 이상 · 날짜별 대사 · 대사 불일치 · `/settings/portone` 카드 셋, user-web `/dev/payment`(시험 주문 + 가짜 결제창) | frontend | **화면 확정 2026-10-26**(아래 「2a-1 화면 목업」, 브랜치 `claude/commerce-2a-1-payment-screens-4ae2d1`) |
| 2a-2 결제 핵심 서버 | V97(가번호 V90), Fake·포트원 게이트웨이, 주문 만들기·확정·완료 API·시험 핸들러, 웹훅, 취소 대기열·워커, READY 정리, 규칙 테스트 | backend | **구현·규칙 테스트 통과**(2026-10-26, 아래 「2a-2 구현 결과」) — **develop 병합 2026-10-27**(아래 「develop 병합」) |
| 2a-3 대사 + 관리자 API + 연결 | 대사 배치, `R__batch_31`, 관리자 조회·처리 API, 포트원 결제 칸·API 정리, 알림·감사, 화면 연결 | backend → frontend | **구현·규칙 테스트 통과·화면 연결**(2026-10-27, 아래 「2a-3 구현 결과」, 브랜치 `claude/commerce-2a-3-reconcile-admin-0ef5e7`) — **develop 병합 2026-10-27**(아래 「develop 병합」) |
| 2a-4 테스트 채널 실제 확인 | dev 에 포트원 테스트 채널 키·웹훅 주소 등록, 사람이 결제창으로 결제·취소·콘솔 직접 취소 확인. 브라우저 SDK `@portone/browser-sdk` 추가(**승인 2026-10-26**, 버전은 설치 때 확인) | 사용자 + 세션 | 마지막 |
순서: 2a-1 ∥ 2a-2 → 화면 확정 → 2a-3 → 2a-4. 2a-2 는 화면과 무관한 서버 핵심이라 화면 확정 전에 병행한다(사용자 승인 2026-10-26). 세션마다 자기 worktree.

## 마이그레이션 계획 (가번호 — develop 에 합칠 때 다시 확인)
| 가번호 | 스트림 | 내용 | 단위 |
|---|---|---|---|
| V97 `payment_module`(가번호 V90 — develop 병합 때 확정) | shared | 아래 표 7개 | 2a-2 (대사 두 표도 여기 — 2a-3 이 쓴다) |
| `R__batch_31_payment_webhook_log_cleanup` | shared | pg_cron 매일 04:45 KST, 90일 지난 웹훅 기록 삭제(`batch_params` `retention_days`) | 2a-3 (**번호 확정** — develop 81f286b 의 마지막 반복 shared `R__batch_29`·admin `R__batch_30`, 다른 브랜치에도 31 없음) |
| `V98__payment_reconcile_admin` | shared | 이력 종류 `RECHECKED`, 대사한 날 상태 RUNNING·DONE·MISMATCH·FAILED(V97 의 OK·FAIL 대신), 색인 셋 | 2a-3 (**번호 확정** — develop 병합 때 확인, V97 다음) |
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

## 2a-2 구현 결과 (2026-10-26)
- **포트원 문서로 확인한 것**(2026-10-26 — 포트원 V2 OpenAPI `developers.portone.io` 저장소의 `v2.openapi.json`, 웹훅 안내 문서, Standard Webhooks 규격): 웹훅 버전 `2024-04-25`, 본문 `{type, timestamp, data:{storeId, paymentId, transactionId, cancellationId?}}`, 이벤트 `Transaction.Paid`·`Transaction.Cancelled`·`Transaction.PartialCancelled`(그 밖에 Ready·VirtualAccountIssued·Failed·PayPending·CancelPending·DisputeCreated/Resolved — 모르는 이벤트는 무시하라고 안내), **`cancellationId` 는 Cancelled·PartialCancelled·CancelPending 에만 있다**. 포트원은 실패한 웹훅을 최대 5번(0·1·4·16·64·256분 + 흔들림) 다시 보낸다. 결제 단건 조회 `GET /payments/{paymentId}?storeId=`(404 `PAYMENT_NOT_FOUND`), 상태 `READY·PENDING·PAY_PENDING·VIRTUAL_ACCOUNT_ISSUED·PAID·FAILED·PARTIAL_CANCELLED·CANCELLED`, 금액 `amount.total`·`amount.cancelled`, `paidAt`·`pgTxId`·`receiptUrl`·`method.type`·`channel.pgProvider`·`cancellations[{status, id, totalAmount, reason, cancelledAt}]`. 취소 `POST /payments/{paymentId}/cancel` 본문 `{storeId, amount, currentCancellableAmount, reason(필수)}` → `{cancellation:{status: REQUESTED|SUCCEEDED|FAILED, id, …}}`, 거절 409 `CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN`·`PAYMENT_ALREADY_CANCELLED`·`CANCEL_AMOUNT_EXCEEDS_CANCELLABLE_AMOUNT` 등, PG 오류 502. **다건 조회는 페이지 기반 `GET /payments` — 본문을 쿼리 `requestBody` 에 JSON 으로 싣는다**(`{page:{number,size}, filter:{storeId, timestampType: CREATED_AT|STATUS_CHANGED_AT(기본), from, until}}`, `(number+1)×size ≤ 60,000`), 대용량은 커서 기반 `GET /payments-by-cursor`(생성일시 기준, 「불안정」 표시). **`paymentId` 길이 제한은 문서에 없다**(「이미 결제 완료된 paymentId 는 재사용 불가」만) — 2a-4 실제 확인으로 넘긴다. 주문명은 PG 마다 40바이트(나이스·이니시스) 등 제한이 있다.
- **코드**: shared `com.community.payment` — `dto`(`PaymentOrderType`·`PaymentStatus`·`PaymentAnomaly`·`ConfirmedVia`·`CancelKind`·`CancelStatus`·`PgCancelStatus`·`PaymentDecision`·`PaidPayment`·`CanceledPayment`·`PaymentOrderRequest`·`PaymentCheckout`·`PaymentCancelOrder`·`PaymentCancelRequested`, 요청 `PaymentCompleteRequest`, 응답 `PaymentCompletionView`) · `handler`(`PaymentOrderHandler`·`PaymentOrderHandlers`) · `gateway`(`PaymentGateway`·`PgPayment`·`PgCancellation`·`PgCancelResult`·`PgPaymentPage`·`PaymentCheckoutInfo`·`WebhookVerification`, `portone.PortOnePaymentGateway`·`StandardWebhookVerifier`·`PortOnePaymentParser`, `fake.FakePaymentGateway`·`FakePgStore`) · `config`(`PaymentConfig`·`PaymentProperties`) · `repository.master`(JDBC `PaymentOrderStore`·`PaymentCancelStore`·`PaymentEventStore`·`PaymentWebhookLogStore`·`ReconciliationIssueStore`) · `service`(`PaymentOrderService`·`PaymentConfirmService`·`PaymentCompletionService`·`PaymentWebhookService`·`PaymentCancelService`·`PaymentCancelExecutor`·`PaymentCancelWorker`·`PaymentCancelImmediateAttempt`·`PaymentAnomalyService`) · `batch.PaymentReadyCleanupJob` · `testorder`(`PaymentTestOrderHandler`·`PaymentTestOrderService`·`PaymentTestViews`) · `exception`(오류 6개). `portone`: `PortOneSettingKey` 묶음 COMMON·IDENTITY·PAYMENT(키 문자열 불변, `paymentChannelKey`·`webhookSecret` 더함), `PortOneSettingsService.paymentReady()`·`paymentCredentials()`, `PortOnePaymentCredentials`. `zconfiguration.security`: `SecurityConfig` 에 웹훅 permitAll + CSRF 제외(`PaymentWebhookPaths`). api `payment.controller`(`PaymentController`·`PaymentWebhookController`·`PaymentTestController`). admin `batch.service.PaymentCancelScheduler`.
- **마이그레이션** shared `V90__payment_module.sql`(가번호 — 표 7개, FK 없음) → **develop 병합 때 `V97__payment_module.sql` 로 확정**(소모임 V90~V96 이 먼저 들어갔다 — 아래 「develop 병합」). 반복 마이그레이션은 없다(`R__batch_31` 은 2a-3).
- **설정**: `roboyam.payment.gateway`(공통 `${ROBOYAM_PAYMENT_GATEWAY:portone}`, local fake) · `test-orders`(공통 `${ROBOYAM_PAYMENT_TEST_ORDERS:false}`, local true — dev 는 환경 변수로 켠다) · `cancel-batch-size` 20 · `cancel-gap` 200ms · `cancel-max-retries` 5 · `cancel-backoff` 1m,5m,30m,120m,720m · `cancel-stale` 10m · (admin) `cancel-poll-millis` 기본 60000. `batch_params` `payment-ready-cleanup.grace_minutes`(기본 10, 0~1440). 배치 카탈로그 `payment-ready-cleanup`(Java, MEMBER, `0 */10 * * * *`, 켬). 워커 기록 키 `payment-cancel`.
- **API**(공통 래퍼, `no-store`, 쓰기는 CSRF — 웹훅 제외)
  | | 선언 | 요청 → 응답 |
  |---|---|---|
  | `POST /api/payments/{paymentId}/complete` | 회원 로그인 | `{orderType, orderRefId}` → `{paymentId, orderType, orderRefId, status, accepted, anomaly, amount, paidAmount, paidAt}` · 404 `PAYMENT_ORDER_NOT_FOUND`(없음·남의 결제) · 409 `PAYMENT_ORDER_MISMATCH` · 409 `PAYMENT_NOT_PAID` · 503 `PAYMENT_GATEWAY_UNAVAILABLE`·`PAYMENT_NOT_CONFIGURED` · 400 `VALIDATION` |
  | `POST /api/payments/webhooks/portone` | 공개(서명) | 원본 본문 ≤ 64KB + 머리글 셋 → 본문 없는 200·400·503(·500 뜻밖의 오류) |
  | `POST /api/dev/payments/test-orders` | 회원 · `test-orders` 서버만 | `{amount(100~1,000,000), decision: ACCEPT\|REJECT\|ERROR}` → 201 `{orderRefId, paymentId, orderName, amount, currency, expiresAt, provider: FAKE\|PORTONE, storeId?, channelKey?}` · 400 `fields.amount` |
  | `POST /api/dev/payments/{paymentId}/fake-pay` | 같음 · 가짜 게이트웨이만(아니면 404) | `{result: PAID\|FAILED, amount?}` → 204(가짜 PG 에 씀 — 이어서 완료 API) |
  | `GET /api/dev/payments/{paymentId}` | 같음 | `{paymentId, orderRefId, status, anomaly, amount, paidAmount, canceledAmount, handlerCash, cancels[{id, kind, amount, status, pgStatus, attempts}]}` |
- **구현 때 정한 것**(계약에 없던 세부 — 화면·2a-3 이 따른다)
  1. **비교 금액 = 포트원 `amount.total`**(카드사 할인 등으로 `amount.paid` 가 작을 수 있어 청구 총액으로 맞춘다). `paid_amount` 에도 `amount.total` 을 둔다(취소 금액의 기준).
  2. **우리 취소 표시**: 포트원에 보내는 `reason` = `roboyam <종류> rc:<취소 ID>`(내부 사유는 보내지 않는다). 시간 초과로 취소 내역 아이디를 못 받아도 재시도 전 조회·취소 웹훅이 이 표시로 우리 취소를 알아본다(그래서 우리 취소가 콘솔 취소로 잘못 이슈가 되지 않는다).
  3. **재시도 횟수**: 즉시 시도 1 + 재시도 5 = 최대 6번, 재시도 간격 1·5·30·120·720분(D10 「즉시 시도 + 재시도 최대 5회」 — 다섯 간격을 모두 쓴다). REQUESTED(접수만)는 다시 보내지 않고 조회로 확인한다.
  4. **잠그는 차례**: 취소 실행·웹훅 맞춤 모두 결제 주문 행 → 취소 줄 차례로 잠근다(교착 방지). 취소 실행은 계약대로 결제 주문 행을 잠근 채 포트원을 부른다(읽기 제한 10초).
  5. **커밋 뒤 즉시 시도는 새 트랜잭션으로 집는다**(스프링 `AFTER_COMMIT` 단계에서는 끝난 트랜잭션의 연결이 묶여 있어, 새 트랜잭션이 아니면 집은 표시가 저장되지 않는다 — 시험이 잡았다).
  6. **남은 취소 가능 금액** = 결제 − 취소됨 − 끝나지 않은 취소(PENDING·SENDING·GAVE_UP) — 넘치면 409 `PAYMENT_CANCEL_EXCEEDS`(새 오류 코드). 같은 요청 키는 처음 줄을 돌려준다.
  7. **웹훅**: 비밀키가 없으면 머리글을 보기 전에 503 RETRY(설정한 뒤 재전송을 받는다). 서명 실패는 검증 안 된 머리글 값을 기록에 남기지 않는다(`webhook_id`·`payment_id` 없음, `error_code` 만). 처리한 웹훅만 `payment_events` 의 `WEBHOOK` 줄(webhook-id)로 남고, 503 이면 남기지 않아 재전송이 다시 처리된다. 처리기 예외 등 뜻밖의 오류는 500(재전송).
  8. **콘솔 직접 취소(P18)**: 포트원 취소 내역 아이디마다 `EXTERNAL_CANCEL` 이력 한 줄 + 열린 `CANCEL_MISMATCH` 이슈 하나(다음 웹훅에 같은 취소가 실려 와도 늘지 않는다).
  9. **처리기 없는 종류**: 「종류마다 하나」는 둘 이상이면 기동 실패로 막고, 없으면(충전·공구 — 아직 도메인 없음) 결제를 만들 수 없다(설정 오류). 시험 주문 처리기의 판정·현금은 그 서버 메모리다(표를 더하지 않았다 — 다시 띄우면 잊고, 관리자 앱의 READY 정리가 확정하면 기본 ACCEPT).
  10. **운영자 알림** `COMMERCE_PAYMENT_ANOMALY`: CRITICAL·이메일 기본 켬·같은 결제 하루 한 건(묶음 키 = 결제 ID)·고정 문구(인자 없음)·링크 `/payments/anomalies`(가정 — 2a-3 화면 연결 때 맞춘다). 받는 권한 `commerce.reconcile.manage` 는 아직 `featureReady=false`(최고 관리자는 받는다 — 2a-3 이 준비됨으로 바꾼다).
  11. **감사 행위**(쓰는 API 는 2a-3): `RECONCILE_ISSUE_RESOLVE`·`PAYMENT_CANCEL_RETRY`·`PAYMENT_CANCEL_MANUAL`·`PAYMENT_RECHECK` = 돈 관련, `PAYMENT_ORDER_VIEW` = 개인정보 열람 — `AuditActions`·`AuditRetention`·번들 두 언어. 감사 대상 종류(`AuditLabels.TARGET_TYPES`)는 2a-3.
  12. `PaymentReadiness` 는 따로 두지 않았다 — `PaymentGateway.ready()`(포트원: `paymentCredentials` 가 있는가 = `paymentReady`)가 맡는다.

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
| 관리자 결제 API: 목록·상세(회원 번호·닉네임만, 열람 감사)·웹훅·결제 이상, 다시 시도·수동 처리(누계 불변)·다시 확인(결과 셋, 이미 확정이면 취소 맞춤) — 권한·사유·감사 | `PaymentAdminHttpTest` | 2a-3 |
- 테스트로 막지 못할 것: 실제 포트원 호출·웹훅 전송(2a-4 에서 사람이), 상수 시간 비교의 실제 시간, pg_cron 등록, 브라우저 결제창, 워커의 실제 예약 주기.

## 2a-1 화면 목업 (2026-10-26 — **사용자 확정**(「화면 이대로 확정해」). 서버 없음)
화면은 메모리 목업(`// MOCK:`·`// REAL:` 나란히, 목업은 각 `api/mock/`, `?mock=` 스위치는 목업 파일 머리)으로 만들었다. 아래 모양대로 2a-2(회원 쪽)·2a-3(관리자 쪽)이 서버를 만들고, 2a-3 이 MOCK 줄을 지운다. 문구는 i18n `payment.*`(두 앱)·`portone.*`·`menu.payment*`.

**관리자(admin-web)** — 보통 모드 묶음 「결제」(`menu.payment`, 포인트 상점 다음, 아이콘 `pi pi-credit-card`). 다섯 화면 모두 조회 `commerce.payment.read`, 처리 단추는 `commerce.reconcile.manage`(없으면 잠그고 이유 표시). 처리는 모두 사유 5~200자 확인창(`PaymentReasonDialog`, 감사 돈 관련 안내).
| 메뉴 키 | 주소 | 화면 |
|---|---|---|
| `paymentOrders` | `/payments/orders` | 결제 주문 목록 — 거르기 기간(기본 1주일)·상태·이상(전체·이상 있음·종류)·주문 종류·번호(결제 번호 일부 또는 회원 번호). 한 줄: 만든 시각 · 결제 번호 · 회원(닉네임·번호) · 주문(이름·종류) · 금액(주문 / 다르면 결제액 · 취소액) · 상태(+ 이상 · 취소 진행 중) · 결제 시각(READY 면 유효 시각). 줄 누르면 상세 서랍, `?payment=ID` 로도 연다 |
| `paymentWebhooks` | `/payments/webhooks` | 웹훅 수신 내역 — 거르기 기간·결과(PROCESSED·IGNORED·REJECTED·RETRY, 각 뜻을 위에 풀이)·번호(결제 번호·webhook-id). 한 줄: 받은 시각 · 이벤트 · 결제 번호(우리 주문이면 서랍) · 결과 · HTTP · 사유 코드 · webhook-id |
| `paymentAnomalies` | `/payments/anomalies` | 결제 이상 — 이상 결제(금액 불일치·주문 거절·늦은 결제, 자동 취소 줄 포함)와 GAVE_UP 취소를 한 목록. 기본 처리 안 된 것만(「처리된 것도 보기」), 제목 옆 「처리 안 됨 N건」. 한 줄: 발견 시각 · 종류 · 결제 · 금액 · 취소(금액·종류·상태·시도 횟수·마지막 오류·처리 기록). GAVE_UP 줄만 [다시 시도]·[수동 처리 완료] |
| `paymentReconcileDays` | `/payments/reconcile-days` | 날짜별 대사 — 요약(마지막 대사한 날 · 다음 자동 대사 · 열린 이슈 수 → 대사 불일치) + 최근 14일 표(날짜 · 상태 PENDING·RUNNING·DONE·MISMATCH·FAILED · 확인한 건수 · 불일치 열림/전체 · 끝난 시각 · [이슈 보기] · [이 날 다시 대사](확인창, 비동기 — 4초 뒤 한 번 다시 읽음)). **사용자 요청으로 대사 불일치에서 따로 뺐다** |
| `paymentReconciliation` | `/payments/reconciliation` | 대사 불일치 — 이슈 5종 목록. 거르기 종류·상태(기본 열림)·날짜(`?day=YYYY-MM-DD`, 칩으로 뗌). 한 줄: 발견한 날 · 종류(+ 풀이) · 결제 번호(우리 주문 있으면 서랍, 없으면 「우리 주문 없음」) · 내용 · 상태/처리 기록 · [처리] |
- **결제 상세 서랍**(`PaymentOrderDrawer` + `usePaymentOrderDrawer`, 다섯 화면 중 넷이 같이 연다): 머리(주문 이름·상태·이상·결제 번호 + 「열면 열람 기록이 남아요」), 이상 띠, 열린 대사 이슈 띠(→ 대사 불일치) / ① 금액 네 칸(주문 금액 · 결제액 · 취소액 · 남은 금액) + 회원 · 주문 종류·대상 번호 · 결제 수단·PG · PG 거래 번호·영수증 · 확정한 길 · 결제창 연 시각 · 유효 시각 · 결제 시각 / ② 이력 타임라인(오래된 것 위, 웹훅에서 온 줄은 webhook-id) / ③ 취소 대기열(금액·종류·상태·사유·진행·마지막 오류·처리 기록, GAVE_UP 이면 결제 이상으로 가는 안내) / ④ 이 결제의 웹훅 / ⑤ [포트원에서 다시 확인](사유).
- **`/settings/portone`**: ① 지금 쓰는 구현 — 본인인증·결제 각각 포트원/가짜(배포 설정, 읽기 전용), 가짜면 local 전용 안내, 포트원인데 준비 안 됐으면 「본인인증(결제)을 쓸 수 없어요」 ② 「공통」(상점 ID·API 비밀키) ③ 「본인인증」(본인인증 채널 키, 알약 「본인인증 준비됨」 = `identityReady`) ④ 「결제」(결제 채널 키·웹훅 비밀키 `whsec_`, 알약 「결제 준비됨」 = `paymentReady`, 콘솔 웹훅 주소 `…/api/payments/webhooks/portone` 안내). 카드마다 저장, 한 카드 저장 중엔 다른 카드 잠금. **공통·본인인증은 실제 서버(지금 `PUT/DELETE …/identity`)를 그대로 부르고 결제 칸·`paymentGateway`·`paymentReady` 만 목업**(`portone/api/mock/`).

**회원(user-web)** — `/dev/payment`(개발 모드만 — 운영 빌드 404, 로그인 필요): ① 시험 주문 만들기(금액 100~1,000,000원 · 유효 1~60분) ② 결제창 — 가짜면 `PaymentFakeCheckoutDialog`(정상 결제 · 결제 실패 · 금액 바꿔 결제(금액 칸, 기본 절반) · 늦게 결제(결제 시각 = 유효 시각 + 1분)), 포트원이면 「2a-4 에서 연결」 안내로 멈춤. 결제했으면 완료 API, 실패·창 닫기는 완료 요청을 보내지 않는다 ③ 결과 — 상태 · 받아들였나/이상 풀이 · 금액·결제액·결제 시각 · 자동 취소 진행 · [결과 다시 받기]. 가짜 결제창 부품은 충전·공구 화면이 가짜 게이트웨이에서 그대로 쓴다.

**확정한 목업 가정(서버 규칙)**
1. 「다시 시도」 = GAVE_UP → PENDING, 시도 횟수 0, 다음 시도 지금(그 뒤 5회 실패하면 다시 GAVE_UP). 「수동 처리 완료」 = GAVE_UP → MANUAL 표시만 — **결제 주문의 취소 누계는 바꾸지 않는다**(실제 취소 여부는 「포트원에서 다시 확인」·대사가 맞춘다). 둘 다 처리자·사유를 취소 줄(`resolved_by`·`resolve_note`)과 이력(`CANCEL_REQUESTED`·`CANCEL_MANUAL`)에 남긴다.
2. 「포트원에서 다시 확인」 결과 셋: `CONFIRMED`(이번에 확정 — `confirmedVia: ADMIN_RECHECK`, EXPIRED 였으면 늦은 결제 + 자동 취소) · `UNCHANGED` · `PG_NOT_PAID`. 이력 종류에 `RECHECKED` 를 더한다.
3. 사유가 필요한 처리: 다시 시도 · 수동 처리 완료 · 다시 확인 · 이슈 처리. **「이 날 다시 대사」는 사유 없이 확인창만**(돈을 움직이지 않는다 — 감사는 남긴다).
4. 결제 이상 목록의 한 줄 = 이상 결제 주문 하나(`ORDER:{id}`) 또는 이상이 아닌 결제의 GAVE_UP 취소 하나(`CANCEL:{id}`). 처리됨 = 그 취소가 SUCCEEDED·MANUAL.
5. 회원 화면 결과의 「완료」 판단은 `accepted`(PAID 계열 ∧ 이상 없음) 하나로 한다.
6. 이력 `detail`·대사 이슈 `detail`·취소 `last_error` 는 서버가 요청 언어로 푼 한 줄(개인정보·비밀값 없음)이다.

**API (2a-1 이 정한 모양 — 응답은 공통 래퍼의 `data`)**
- 회원(api, 2a-2): `POST /api/payments/{paymentId}/complete` `{ orderType, orderRefId }` → `PaymentResult { paymentId, status, anomaly, accepted, amount, paidAmount, canceledAmount, paidAt, cancel: { amount, status } | null }` · `409 PAYMENT_NOT_PAID`·`PAYMENT_ORDER_MISMATCH` · `404 PAYMENT_ORDER_NOT_FOUND` · `503 PAYMENT_GATEWAY_UNAVAILABLE`.
- 시험 주문(api, `test-orders` 일 때만, 2a-2): `POST /api/dev/payments` `{ amount, validMinutes }` → `{ orderType: 'PAYMENT_TEST', orderRefId, expiresAt, checkout: { gateway: FAKE|PORTONE, paymentId, orderName, amount, currency, storeId, channelKey } }`(`checkout` 은 도메인 결제 시작 API 가 같이 줄 공용 모양) · `400 fields.amount|validMinutes` · `503 PAYMENT_NOT_CONFIGURED`. `POST /api/dev/payments/{paymentId}/fake-checkout` `{ outcome: PAID|FAILED, amount, late }` → 204(가짜 PG 표에 남김, 가짜가 아니면 `409 PAYMENT_GATEWAY_NOT_FAKE`, 이미 끝냈으면 `409 FAKE_PG_ALREADY_DONE`). `GET /api/dev/payments/{paymentId}` → `PaymentResult`.
- 관리자(admin, 2a-3) — 모양 원문은 `apps/admin-web/app/composables/payment/api/usePaymentAdminApi.ts`·`.types.ts`:
  - `GET /api/admin/payments?status=&anomaly=(종류|ANY)&orderType=&from=&to=&q=&page=&size=` → `{ items: PaymentOrderRow[], total }` · `GET /api/admin/payments/{id}` → 상세(+ `events`·`cancels`·`webhooks`·`openIssueCount`, 열람 감사) · `POST …/{id}/recheck` `{ reason }` → `{ outcome, detail }`
  - `GET /api/admin/payment-webhooks?result=&from=&to=&q=&page=&size=`
  - `GET /api/admin/payment-anomalies?kind=&includeResolved=&from=&to=&page=&size=` → `{ items, total, openCount }` · `POST /api/admin/payment-cancels/{id}/retry|manual` `{ reason }` → 취소 줄 · `409 PAYMENT_CANCEL_NOT_GAVE_UP`
  - `GET /api/admin/payment-reconciliation` → `{ lastCheckedDay, nextRunAt, days[14], openIssueCount }` · `GET …/issues?type=&status=&day=&page=&size=`(열린 것 먼저) · `POST …/issues/{id}/resolve` `{ reason }` · `409 RECONCILE_ISSUE_NOT_OPEN` · `POST …/days/{day}/rerun` → 그 날 줄(RUNNING) · `409 RECONCILE_ALREADY_RUNNING` · `400 fields.day`(오늘·미래)
  - 포트원 설정 GET 에 `paymentGateway`·`paymentReady`·결제 두 칸, 결정 14 의 `PUT …/{common|identity|payment}`·`DELETE …/{group}/{key}`, 웹훅 비밀키 `whsec_` 형식 검증(`400 fields.webhookSecret`).
- 새 감사 행위·오류 코드 후보(2a-3 이 확정): 위 결정 목록 + `PAYMENT_CANCEL_NOT_FOUND`·`RECONCILE_ISSUE_NOT_FOUND`·`RECONCILE_ALREADY_RUNNING`·`PAYMENT_GATEWAY_NOT_FAKE`·`FAKE_PG_ALREADY_DONE`. 「다시 대사」 감사 행위 이름은 2a-3 이 정한다.

**2a-3 이 맞출 것**: 서버 `AdminScreenRegistry` 에 결제 다섯 줄(키·주소·권한·아이콘·차례 위 표대로), 두 권한 `featureReady=true`(화면 `util-permission-catalog.ts` 도), 포트원 칸을 공통·본인인증·결제로 나눔(그때 `usePortoneSettingsApi` 의 공통 카드를 `…/common` 으로).

### 2a-2 규칙 → 테스트 결과 (2026-10-26 — 모두 실제 PostgreSQL `CommerceTestDatabase`, 포트원은 가짜 PG·HTTP 흉내)
| 테스트(위치) | 수 | 막은 규칙(위 표의 2a-2 줄 + 구현 때 정한 것) |
|---|---|---|
| shared `payment.PaymentSchemaTest` | 10 | 취소 ≤ 결제·결제 계열 칸·통화·상태·이상·확정 길 CHECK, payment_id 형식·유일, 받아들인 결제 하나(이상·끝난 결제·READY 는 셈 밖), 취소 대기열 request_key·pg_cancel_id 유일·상태 칸 맞물림, webhook-id 유일, 열린 이슈 유일(처리하면 다시 열 수 있음), FK 없음 |
| `PaymentOrderServiceTest` | 7 | `pay_`+32자·겹치지 않음·서버 금액·유효 시각, MANDATORY, 도메인 롤백이면 없음, 재결제 새 번호·이전 READY → EXPIRED(다른 주문은 그대로), 범위 밖 입력, 처리기 없는 종류, 설정 없음 503 |
| `PaymentConfirmServiceTest` | 8 | 8-2 표 전부(PAID 아님 그대로·ACCEPT 확정·REJECT → DOMAIN_REJECTED + 전액 자동 취소 + 커밋 뒤 즉시 취소·금액/통화 불일치 → onPaid 없이 전액 취소 + 운영자 알림·포트원 장애 503 무변경), 처리기 예외 → 전체 되돌림 뒤 다시 시도, 이미 확정이면 조회 없이 같은 결과 |
| `PaymentConfirmConcurrencyTest` | 2 | 완료 API·웹훅·READY 정리가 동시에(연결 여럿, 9갈래 × 5회) → onPaid 한 번·PAID 이력 한 줄, 거절 결제의 자동 취소도 한 번 |
| `LatePaymentTest` | 4 | 결정 4 — EXPIRED 면 무조건 늦음, READY 면 `paidAt > expires_at` 만(1µs 도 늦음·같으면 늦지 않음), 확인이 늦게 와도 받아들임, 재결제 뒤 옛 시도의 늦은 결제가 정상 결제와 부딪치지 않고 자동 취소 |
| `PaymentCompletionTest` | 5 | P24 남의 결제 404·다른 주문 409(포트원 조회 0회), 결제 전 409 `PAYMENT_NOT_PAID` 뒤 다시 성공, 장애 503, 웹훅이 먼저면 같은 결과·이상 결제는 `accepted=false` |
| `PaymentCancelWorkerTest` | 12 | 넣기 MANDATORY·남은 금액(진행 중 포함) 넘으면 409·같은 키 한 번·업무 롤백이면 없음·즉시 시도도 없음 / `currentCancellableAmount`·부분 → 전액 상태·onCanceled 한 번·조건부 누계(넘침 거절), **P17** 즉시 1 + 재시도 5 → GAVE_UP·운영자 알림·도메인 확정 없음·간격 정확히 1·5·30·120·720분·차례 전엔 안 집음·멈춘 줄은 다시 안 집음, 재시도 전 조회(응답 끊김 뒤 이중 취소 없음), 접수만(REQUESTED), 오래된 SENDING 10분 뒤 되살림, 한 바퀴 20건·건 사이 200ms, 즉시 시도와 워커는 한쪽만 |
| `PaymentCancelConcurrencyTest` | 1 | 서버 번호가 다른 워커 넷이 동시에(`SKIP LOCKED`) → 취소 40건마다 포트원 요청 한 번·누계 한 번 |
| `PaymentWebhookTest` | 9 | P23(서명 틀림·본문 바뀜·머리글 없음·시각 ±5분 초과 → 400 REJECTED·조회 0·상태 불변, 5분 안은 받음), 비밀키 없음 503, 조회 후 확정·본문 저장 안 함, 중복 webhook-id 한 번, 아직 결제 전·장애 503 RETRY 뒤 재전송 처리, 모르는 상점·결제·이벤트·읽을 수 없는 본문 200 IGNORED, **P18** 콘솔 취소 → 상태 불변 + 이슈 하나(다시 와도 하나), 우리 취소 웹훅 → 그 줄 SUCCEEDED(한 번만 반영) |
| `StandardWebhookVerifierTest` | 6 | 규격 시험 값, 여러 서명 중 하나·v1 만, 본문·ID·시각·키가 다르면 틀림, `whsec_` 있든 없든, 시각 오차 경계, 머리글 없음·키 없음 |
| `PaymentReadyCleanupJobTest` | 5 | 카탈로그(Java·10분), 여유 10분 경계, 결제 안 됨 EXPIRED·제때 결제 확정(READY_CLEANUP)·늦은 결제 자동 취소, 두 번 돌려도 같음, `grace_minutes` 설정·범위 밖은 기본, 장애는 건별 실패(`PAYMENT_ORDER`) 뒤 다음 실행이 처리, 덩어리 여럿·사흘 밀려도 전부 |
| `PaymentInvariantTest` | 1 | 30건을 받아들임·거절·금액 불일치·늦은 결제·부분 환불·장애·접수만·응답 끊김으로 섞어 → 결제마다 취소 누계 = SUCCEEDED 합, 받아들인 결제는 결제 − 취소 = 처리기 현금, 이상 결제는 현금 0·전액 취소, 포트원 쪽 취소액 = 우리 누계 |
| `PaymentConfigTest` | 6 | dev·prod 가짜 기동 실패, prod 시험 주문 기동 실패(dev·local 됨), 포트원은 어디서나·설정 없으면 준비 안 됨, 숫자 기본값·잘못된 재시도 설정 기동 실패, 시험 주문 처리기는 켠 서버에만, 공통/local 설정 값 |
| `PortOnePaymentGatewayTest` | 8 | 조회 요청 모양(storeId·`PortOne` 머리글)·쓰는 칸만·취소 내역, 404 없음·그 밖 오류/연결 실패/모양 틀림/남의 결제 503, 취소 본문 칸·SUCCEEDED/REQUESTED/FAILED, 409 거절 type·5xx·연결 실패 503, 다건 조회 `requestBody` 쿼리·`STATUS_CHANGED_AT`, 설정 없음 503·요청 없음, 결제창 값·웹훅 검증 |
| `PaymentLogSecrecyTest` | 3 | 포트원 오류 본문·API 비밀키·웹훅 비밀키·구매자 이메일이 로그에 없음, 거절 웹훅의 서명·본문이 로그·기록에 없음, 취소 실패 문구 가림·포트원에 내부 사유를 보내지 않음 |
| shared `portone.service.PortOnePaymentSettingsTest` | 3 | 키 문자열 불변·묶음 셋, `paymentReady` = 4칸 + 암호화 키, 결제 값 풀기·`toString` 감춤, 본인인증 화면·판정은 예전 세 칸 그대로 |
| api `payment.PaymentCompletionHttpTest` | 6 | 완료 API CSRF·로그인 필요, 시험 주문 → 가짜 결제 → 완료가 공통 래퍼·`no-store` 로 끝까지, 404·409·400, **속도 제한·멱등 키 없음**(15번 연속 200), 웹훅은 로그인·CSRF 없이(보안 필터를 거쳐) 틀린 서명 400·맞으면 200, 64KB 넘으면 400 |
| admin `batch.service.PaymentCancelSchedulerTest` | 3 | 배치를 끈 서버는 안 돎·기록 없음, 켠 서버는 한 바퀴·`payment-cancel` 워커 기록(건수), 실패는 가린 한 줄 |
- **실행**(2026-10-26, 이 worktree): `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew --continue :server:shared:test --rerun --tests 'com.community.payment.*' --tests 'com.community.portone.*' --tests 'com.community.identity.*' --tests 'com.community.commerce.*' --tests 'com.community.audit.*' --tests 'com.community.batch.core.*' --tests 'com.community.testsupport.*' --tests 'com.community.mail.outbox.*' --tests 'com.community.point.PointBatchCatalogTest' --tests 'com.community.pointshop.PointShopCatalogTest' :server:api:test --rerun --tests 'com.community.payment.*' --tests 'com.community.identity.*' --tests 'com.community.commerce.*' :server:admin:test --rerun --tests 'com.community.commerce.*' --tests 'com.community.batch.*' --tests 'com.community.permission.*' --tests 'com.community.menu.*' --tests 'com.community.mfa.MfaCatalogTest' --tests 'com.community.audit.*'` → **shared 239 · api 47 · admin 73 = 359 통과, 실패 0**. 그중 2a-2 새 시험 99개(shared 90 · api 6 · admin 3), 나머지 260개는 바꾼 곳 둘레의 기존 시험(포트원 설정·본인인증·감사 분류 `AuditRetentionTest`·커머스 카탈로그·배치 core/화면·권한·메뉴·메일 대기열 — 관리자·공개 사이트 앱 문맥이 결제 빈을 싣고 뜨는 것도 여기서 확인). 전체 테스트는 돌리지 않았다.
- **테스트로 막지 못한 것**: 실제 포트원 호출(응답 모양은 OpenAPI 기준 흉내 — 2a-4), 실제 웹훅 전송·재전송, `paymentId` 길이 제한(문서에 없음), 상수 시간 비교의 실제 시간, 워커·READY 정리의 실제 예약 주기(`@Scheduled`·cron 자체), 운영자 알림의 실제 발행(발행기 대역으로 「무엇을 냈는가」만 — 문구·받는 사람은 알림 모듈 시험의 몫), 시험 주문 처리기 메모리가 서버마다 따로인 것(시연용으로 받아들임), dev 의 `ROBOYAM_PAYMENT_TEST_ORDERS` 를 compose 에 넣는 일(배포 파일은 이 단계에서 고치지 않았다).

## 2a-3 구현 결과 (2026-10-27 — develop 81f286b 에서 시작)
- **코드**
  - shared `com.community.payment`: `batch.PaymentReconciliationJob`(Java 배치 `payment-reconciliation`) · `service.ReconciliationService`(하루 대사·5종 비교·알림) · `service.PaymentAdminReadService`(목록·상세·결제 이상·대사 요약, 상세 열람 감사) · `service.PaymentAdminActionService`(다시 시도·수동 처리 완료·다시 확인·이슈 처리·다시 대사) · `service.PaymentDetailText`(이력·이슈 `detail` → 요청 언어 한 줄, 결제 수단 이름) · `repository.master`(`PaymentAdminQueries` 읽기 · `PaymentReconcileQueries` · `PaymentReconcileDayStore`, 취소 대기열 `reopenGaveUp`·`markManual`·`lockById`, 이슈 `resolve`) · `dto.response`(관리자 화면 모양 13개) · `exception`(오류 5개). `PaymentWebhookService.syncCancellations` 를 공개(다시 확인이 같이 쓴다).
  - shared 그 밖: `BatchCatalog` 두 줄(`PAYMENT_RECONCILIATION` Java SYSTEM `0 50 4 * * *` · `PAYMENT_WEBHOOK_LOG_CLEANUP` DB SYSTEM), `Permission` 두 권한 `featureReady=true`, `AdminNotificationEvent.COMMERCE_RECONCILE_MISMATCH`, `AuditActions.PAYMENT_RECONCILE_RERUN`·`AuditRetention`(일반), `PortOneSettingsService`(카드 단위 `save(group, …)`·`deleteField(group, key)`·`violation`·`webhookSecretShape`, 화면에 `paymentGateway`·`paymentReady`·다섯 칸), `PortOneSettingsView` 두 칸 더함, 메시지 두 언어.
  - admin: `payment.controller.PaymentAdminController`(11개 — 경로·응답은 2a-1 의 `usePaymentAdminApi.ts` 그대로) · `payment.service.PaymentAdminService`(질의 읽기) · `payment.dto.request.PaymentReasonRequest`, `PortOneSettingsController`(`PUT …/{common|identity|payment}` · `DELETE …/{group}/{key}`) + 요청 셋, `AdminScreenRegistry` 결제 묶음 다섯 줄(포인트 상점 다음), `AuditLabels.TARGET_TYPES` 넷(`PAYMENT_ORDER`·`PAYMENT_CANCEL`·`RECONCILIATION_ISSUE`·`PAYMENT_RECONCILE_DAY`).
  - admin-web: `usePaymentAdminApi`·`usePortoneSettingsApi` 의 `// MOCK:` 을 지우고 실제 API(목업 파일 둘 삭제), 이력 종류 `CANCEL_RETRY`·`WEBHOOK` 타입·문구, `util-permission-catalog.ts` 두 권한 준비됨, `util-notification.ts` 결제 알림 아이콘 둘, 화면 머리 주석의 「목업 단계」 정리(화면 동작·모양은 그대로).
- **마이그레이션**: shared `V98__payment_reconcile_admin.sql`, `R__batch_31_payment_webhook_log_cleanup.sql`(04:45, `retention_days` 90 · 30~3650).
- **API**(공통 래퍼, `no-store`, 쓰기는 CSRF). 쪽 번호 0부터·크기 1~100(기본 20), 기간은 ISO 순간 [from, to), 빈 거르기는 생략. 처리 사유 5~200자(`400 fields.reason`), 못 읽는 질의는 `400 fields.<이름>`, 못 읽는 경로 ID 는 404.
  | | 권한 | 요청 → 응답 · 오류 |
  |---|---|---|
  | `GET /api/admin/payments` | `commerce.payment.read` | `status·anomaly(종류\|ANY)·orderType·from·to·q(결제 번호 일부\|회원 번호)` → `{items, total}`(만든 시각 내림차순) |
  | `GET /api/admin/payments/{id}` | 같음 | 상세(+ events·cancels·webhooks 최근 50·openIssueCount) · 감사 `PAYMENT_ORDER_VIEW` · 404 `PAYMENT_ORDER_NOT_FOUND` |
  | `POST /api/admin/payments/{id}/recheck` | `commerce.reconcile.manage` | `{reason}` → `{outcome: CONFIRMED\|UNCHANGED\|PG_NOT_PAID, detail}` · 503 `PAYMENT_GATEWAY_UNAVAILABLE`(감사 없음) |
  | `GET /api/admin/payment-webhooks` | `commerce.payment.read` | `result·from·to·q(결제 번호·webhook-id 일부)` → `{items, total}` |
  | `GET /api/admin/payment-anomalies` | 같음 | `kind(이상 종류\|CANCEL_GAVE_UP)·includeResolved·from·to`(발견 시각) → `{items, total, openCount}`(처리 안 된 것 먼저) |
  | `POST /api/admin/payment-cancels/{id}/retry`·`/manual` | `commerce.reconcile.manage` | `{reason}` → 취소 한 줄 · 409 `PAYMENT_CANCEL_NOT_GAVE_UP` · 404 `PAYMENT_CANCEL_NOT_FOUND` |
  | `GET /api/admin/payment-reconciliation` | `commerce.payment.read` | `{lastCheckedDay, nextRunAt, days[14](어제부터), openIssueCount}` |
  | `GET /api/admin/payment-reconciliation/issues` | 같음 | `type·status·day` → `{items, total}`(열린 것 먼저) |
  | `POST …/issues/{id}/resolve` | `commerce.reconcile.manage` | `{reason}` → 이슈 한 줄 · 409 `RECONCILE_ISSUE_NOT_OPEN` · 404 `RECONCILE_ISSUE_NOT_FOUND` |
  | `POST …/days/{day}/rerun` | 같음 | 본문 없음 → 그 날 줄(RUNNING) · 409 `RECONCILE_ALREADY_RUNNING` · 400 `fields.day`(오늘·미래·못 읽음) |
  | 포트원 설정 | `site.read`/`site.secret` | `commerce-foundation-contract.md` 「0-4」 API 표를 고쳤다(카드 셋·`paymentGateway`·`paymentReady`·`whsec_` 검증) |
- **구현 때 정한 것**(계약에 없던 세부)
  1. **대사 비교**(16-1): 포트원 「결제 계열」= PAID·PARTIAL_CANCELLED·CANCELLED. 우리 주문 없음 + 포트원 결제 계열 → `PG_ONLY` · 우리 READY/EXPIRED + 포트원 결제 계열 → `PG_PAID_LOCAL_UNPAID` · 우리 결제 계열 + 포트원 없음/결제 아님 → `LOCAL_PAID_PG_MISSING` · 결제액(`paid_amount` ↔ `amount.total`) 다름 → `AMOUNT_MISMATCH` · 취소 누계 다름 → `CANCEL_MISMATCH`(한 결제에 둘 다 날 수 있다). 하루 = KST 날짜의 포트원 다건 조회(`STATUS_CHANGED_AT`, 100건씩, 60,000건 한도) + **그 날 결제되거나 성공한 취소가 끝난 우리 결제 중 목록에 없던 것은 단건 조회**(포트원 쪽 상태 변경일이 다른 날로 넘어간 결제를 놓치지 않게). 이슈 `detail` 은 포트원 값 ↔ 우리 값(개인정보 없음).
  2. **「아직 안 한 날 전부」** = 처음 결제 주문이 생긴 날(KST)부터 어제까지 중 DONE·MISMATCH 가 아닌 날(줄 없음·RUNNING·FAILED). 날 하나 = 배치 한 건(체크포인트 = 날짜 → 「실패 지점부터 다시」), 포트원 장애는 그 날만 FAILED + 실패 건 `PAYMENT_RECONCILE_DAY`. 날짜 상태·이슈는 각각 새 트랜잭션으로 확정(덩어리 실패 뒤 한 건씩 다시 돌아도 남는다). `issue_count` = 그 날 마지막 대사의 불일치 수(이미 열려 있던 것 포함), 화면의 「열림」은 그 날 발견해 아직 열린 이슈 수.
  3. **날짜 상태 저장**: V98 이 `payment_reconcile_days.status` 를 화면 이름(RUNNING·DONE·MISMATCH·FAILED)으로 바꿨다. PENDING 은 줄이 없는 날. 「이 날 다시 대사」는 줄을 바꾸지 않고 배치 요청 표(`batch_run_requests` NOW + `{day}`)에 넣는다 — 화면의 RUNNING 은 끝나지 않은 요청의 날짜로 보인다(배치를 켠 서버가 없어 요청이 남아도 줄이 RUNNING 으로 굳지 않는다). 대사 배치에 끝나지 않은 요청이 있으면 409.
  4. **운영자 알림** `COMMERCE_RECONCILE_MISMATCH`: WARNING · 이메일 없음 · 링크 `/payments/reconciliation` · 묶음 키 `reconcile:<KST 날짜>` + 묶기 24시간 — 배치 한 실행이 새 이슈를 하나라도 열었을 때만(다시 돌려 새 이슈가 없으면 알리지 않는다).
  5. **다시 확인**: 확정 코드(`ADMIN_RECHECK`) — READY·EXPIRED 면 결과대로 CONFIRMED(EXPIRED 는 늦은 결제 + 자동 취소)·PG_NOT_PAID. **이미 확정된 결제면 UNCHANGED 이고 포트원 취소 목록을 취소 대기열과 맞춘다**(취소 웹훅과 같은 코드 — 멈춘 취소를 포트원이 실제로 취소했으면 그 줄 SUCCEEDED·누계 반영, 우리 것이 아닌 취소는 상태 불변 + `CANCEL_MISMATCH` 이슈). 이력 `RECHECKED`(`detail.outcome`), 감사 `PAYMENT_RECHECK`. 포트원 장애는 503·감사 없음.
  6. **다시 시도**: GAVE_UP → PENDING(시도 0·다음 시도 지금·`completed_at` 비움, 1분 워커가 집는다 — 즉시 시도는 하지 않는다), 이력 `CANCEL_REQUESTED`(`detail.by = ADMIN_RETRY`). **수동 처리 완료**: GAVE_UP → MANUAL(누계 불변), 이력 `CANCEL_MANUAL`. 둘 다 처리자·사유를 취소 줄에, 감사(돈 관련)에 `paymentId`·`amount`·`note`. 잠금은 결제 주문 행 → 취소 줄.
  7. **결제 이상 목록**: 이상 결제(발견 = 첫 `ANOMALY` 이력 시각, 취소 = `AUTO_REJECT:<주문 ID>` 줄) + 이상이 아닌 결제의 **한 번이라도 GAVE_UP 이 됐던** 취소(발견 = 첫 `CANCEL_GAVE_UP` 이력 — 다시 시도해 PENDING 이면 처리 안 됨, SUCCEEDED·MANUAL 이면 처리됨). 정렬 = 처리 안 된 것 → 발견 시각 내림차순.
  8. **이력 종류**: 화면에 `CANCEL_RETRY`(재시도 예약)·`WEBHOOK`(webhook-id)도 그대로 내린다(admin-web 타입·문구 추가). `detail` 은 「이름: 값 · …」(금액 「12,000원」, 시각 KST, 코드 값은 번들 `payment.value.*`) — 번들에 없는 칸·값은 그대로.
  9. **감사 행위 이름** 「이 날 다시 대사」 = `PAYMENT_RECONCILE_RERUN`(대상 `PAYMENT_RECONCILE_DAY` + 날짜, **일반** — 확정한 목업 가정 3). 대상 종류 넷을 `AuditLabels` 에 더했다.
  10. **포트원 설정**: 저장·지우기는 카드 단위이고 다른 카드의 칸은 받지 않는다(`PUT …/identity` 에 `storeId` 를 보내면 「보낸 칸 없음」 400). 감사 대상 ID = 카드 이름(common·identity·payment). 웹훅 비밀키 = `whsec_` + 비어 있지 않은 base64(아니면 `400 fields.webhookSecret`) — 한 칸이라도 틀리면 그 카드는 저장하지 않는다.
- **세션 정리 결정**(2a-3): 계약 「패키지」의 「관리자 조회는 읽기 전용 JPA + QueryDSL」 대신 **JDBC 읽기 저장소(`PaymentAdminQueries`)** 로 했다 — 결제 모듈 저장소가 모두 JDBC 이고, 결제 이상 목록이 두 출처의 `UNION ALL`·이력/이슈가 `jsonb` 라 엔티티를 새로 두는 것보다 단순하다(포인트 상점 관리자 목록 `PointShopPurchaseStore` 와 같은 방식, 정렬·쪽 나누기는 DB).

### 2a-3 규칙 → 테스트 결과 (2026-10-27 — 모두 실제 PostgreSQL `CommerceTestDatabase`, 포트원은 가짜 PG)
| 테스트(위치) | 수 | 막은 규칙 |
|---|---|---|
| shared `payment.PaymentReconciliationJobTest` | 6 | 카탈로그(Java·04:50·`day` DATE·이어 받기), **5종**(PG_PAID_LOCAL_UNPAID·LOCAL_PAID_PG_MISSING·AMOUNT_MISMATCH·CANCEL_MISMATCH·PG_ONLY, 맞는 결제는 없음)·**자동 보정 없음**(결제 주문·취소 대기열·포트원 취소 호출 그대로)·날짜 줄(MISMATCH·건수·실행 번호)·알림 하나, **중복 없음**(다시 돌려도 하나·새 이슈 없으면 알림 없음·처리한 이슈는 어긋남이 남으면 다시 열림), **놓친 날 메꿈**(줄 없음·FAILED 날까지, 끝낸 날은 그대로, 두 번째 실행 SKIP), **날짜 지정**(그 날만·오늘/미래 거절), 포트원 장애 → 그 날 FAILED + 실패 건 → 다음 실행이 처리 |
| shared `payment.PaymentCleanupBatchTest` | 3 | `R__batch_31` 정의(04:45·DAILY·90일), 90일 지난 웹훅 기록만 삭제·결제 이력(WEBHOOK) 보존, `retention_days` 바꿈·하한 30 미만은 기본값 |
| shared `portone.service.PortOnePaymentSettingsTest`(보강) | 5(+2) | 화면 다섯 칸·`paymentGateway`·`paymentReady`(암호화 키 없으면 거짓)·값 감춤, `whsec_` + base64 형식·빈 값·500자, 카드 밖 칸 거절·카드 이름 |
| admin `payment.PaymentAdminHttpTest` | 11 | 권한(조회 `commerce.payment.read`·처리 `commerce.reconcile.manage` 403), 목록 거르기·쪽·회원 번호·닉네임만(탈퇴 번호 없음·이메일 없음)·결제 수단 이름, 상세(이력 요청 언어·웹훅·열린 이슈·`PAYMENT_ORDER_VIEW` 개인정보 열람 감사·404), 웹훅 목록(결과·번호 거르기·우리 주문 번호), 결제 이상(두 출처·처리 안 된 것만·`includeResolved`·`openCount`·종류), 다시 시도(사유 5~200·PENDING·시도 0·이력·감사 MONEY·두 번째 409·404), 수동 처리(누계 불변·감사), 다시 확인(사유 먼저·CONFIRMED·PG_NOT_PAID·늦은 결제 자동 취소·UNCHANGED + 콘솔 취소 이슈·멈춘 취소를 포트원 취소로 SUCCEEDED) |
| admin `payment.ReconciliationAdminTest` | 5 | 권한 403, 요약(14일·PENDING·다음 04:50·마지막 대사한 날), 이슈 목록(열린 것 먼저·거르기·요청 언어·우리 주문 없음), 처리(사유·RESOLVED·처리자·감사 MONEY·결제 불변·409·404), 다시 대사(어제까지·못 읽음 400·배치 요청 `{day}`·RUNNING·감사 GENERAL·409) |
| admin `commerce.PortOneSettingsHttpTest`(다시 씀) | 5 | 카드 셋 권한 `site.read`/`site.secret`, 다섯 칸·두 구현·`paymentReady`, 값이 응답·감사·암호문에 없음·감사 대상 = 카드·칸 이름만·MONEY, 빈 값·500자·`whsec_` 형식 400(아무것도 저장 안 함), 카드별 지우기·다른 카드 칸·모르는 카드 404 |
| admin `permission.config.Commerce2aPermissionDeclarationTest` | 2 | 결제 API 11개 선언 = 계약, 두 권한 준비됨·처리는 SENSITIVE |
| admin `commerce.Commerce2aCatalogTest` | 6 | 메뉴 다섯 줄(포인트 상점 다음·키·주소·아이콘·권한)·admin-web 사본 같음, 감사 분류(돈 넷·개인정보 하나·다시 대사 일반)·대상 넷, 알림 둘(링크·수준·받는 권한·하루 한 번), 배치 두 줄, 새 문구 두 언어 |
| 바꾼 기존 시험 | — | `CommercePermissionsAndMenuTest`(두 권한 준비됨 — admin-web 사본까지), `Commerce04PermissionDeclarationTest`(포트원 핸들러 다섯), api `PortOneIdentitySettingsTest`(카드 단위 저장·지우기), shared `PortOneSettingsServiceTest`·`IdentityConfigTest`·`PaymentConfigTest`(생성자) |
- **실행**(2026-10-27, 이 worktree): `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew --continue :server:shared:test --rerun --tests 'com.community.payment.*' --tests 'com.community.portone.*' --tests 'com.community.identity.*' --tests 'com.community.batch.core.*' --tests 'com.community.commerce.*' --tests 'com.community.audit.*' --tests 'com.community.notification.*'` → **212 통과**, `:server:shared:test --rerun --tests 'com.community.testsupport.*' --tests 'com.community.mail.outbox.*' --tests 'com.community.point.PointBatchCatalogTest' --tests 'com.community.point.PointInvariantCheckBatchTest' --tests 'com.community.point.PointMigrationConversionTest' --tests 'com.community.pointshop.PointShopCatalogTest' --tests 'com.community.pointshop.PointShopMigrationTest' --tests 'com.community.datakey.*' --tests 'com.community.batch.*'` → **88 통과**, `:server:admin:test --rerun --tests 'com.community.payment.*' --tests 'com.community.commerce.*' --tests 'com.community.permission.*' --tests 'com.community.menu.*' --tests 'com.community.audit.*' --tests 'com.community.batch.*' --tests 'com.community.mfa.MfaCatalogTest' --tests 'com.community.notification.*'` → **97 통과**, `:server:api:test --rerun --tests 'com.community.payment.*' --tests 'com.community.identity.*' --tests 'com.community.commerce.*'` → **47 통과**. 모두 실패 0(shared 두 묶음은 겹치는 시험이 있어 합이 아니다). 그중 2a-3 새 시험 35개(shared 9 + 포트원 보강 2 · admin 24), 다시 쓴 시험 `PortOneSettingsHttpTest` 5개. admin-web·user-web `pnpm typecheck` 통과. 전체 테스트는 돌리지 않았다.
- **테스트로 막지 못한 것**: 실제 포트원 다건 조회(요청 모양은 2a-2 `PortOnePaymentGatewayTest` 의 흉내까지 — 페이지·`STATUS_CHANGED_AT` 필터의 실제 응답·60,000건 한도는 2a-4), 대사 배치의 실제 예약(04:50 cron)·배치를 켠 서버가 「다시 대사」 요청을 집어 도는 길(요청 표에 들어가는 것까지 — 실행기는 배치 core 시험의 몫), pg_cron 등록(시험 DB 에 없음 — 정의·`CALL` 만), 운영자 알림의 실제 발행(발행기 대역 — 무엇을 냈는가까지), 「다음 자동 대사」를 운영자가 cron 을 바꾼 경우(기본 cron 만 시험), 화면(타입 검사만 — 브라우저 확인은 사용자).

## 미확정
- **포트원**(위 「2a-2 구현 결과」에서 확인한 것 빼고): `paymentId` 길이 제한(문서에 없다 — 우리 36자, 2a-4 실제 결제로 확인), 웹훅 비밀키의 실제 형식(Standard Webhooks 는 `whsec_` + base64 — `whsec_` 없이 주면 그대로 base64 로 푼다), 채널별 승인/매입 분리.
- ~~운영자 알림 링크는 2a-1 화면이 정한 `/payments/anomalies` 로 2a-3 연결 때 맞춘다.~~ → 2a-3 에서 확정(결제 이상 `/payments/anomalies`, 대사 불일치 `/payments/reconciliation`).
- (2a-1 계약 때의 미확정 — 웹훅 버전·이벤트 이름·`cancellationId` 유무·다건 조회 방식은 위 「2a-2 구현 결과」에서 포트원 문서로 확인했다.)

## develop 병합 (2026-10-27)
- 2a 제안(`3a2060c`)·2a-1(`claude/commerce-2a-1-payment-screens-4ae2d1`)·2a-2(`claude/commerce-2a-2-payment-core-b7f905`)를 develop(`b294806` — 소모임 V90~V96 이 먼저 들어가 있음)에 합쳤다.
- **결제 마이그레이션 번호 확정: `V90__payment_module` → `V97__payment_module`**(shared, 소모임 V90~V96 뒤). 반복 마이그레이션은 아직 없다(`R__batch_31` 은 2a-3 — 그때 develop 의 마지막 반복 번호를 다시 본다).
- 충돌 6곳은 모두 양쪽 추가분을 함께 남겼다: `SecurityConfig`(소모임 공개 GET + 결제 웹훅 POST), `roboyam-shared.yml`(`groups` + `payment`), `AuditActions`(소모임 행위 + 결제 행위), 메시지 두 언어(소모임 문구 + 결제 문구), 이 계약 문서.
- **2a-3**(`0cd9279`)을 develop(`6831e68` — 그사이 AWS 배포 문서·스크립트만 들어옴)에 합쳤다(`4017a3d`). 충돌·겹치는 파일 없음. 번호 그대로 확정: shared `V98__payment_reconcile_admin`(V97 다음), `R__batch_31`(shared 마지막 반복 29·admin 30 다음, 중복 없음).
- 합친 뒤 커머스 테스트를 골라 돌림(2026-10-27): shared 457 · api 139 · admin 307, 실패·건너뜀 0.
