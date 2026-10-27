# 커머스 2a 계약 — 결제 모듈 단독 (ERD·패키지 제안 승인 2026-10-26 — 구현 전)

**의도**: 공구(2b)·충전(3)이 함께 쓸 현금 결제 모듈을 도메인 없이 먼저 다진다 — 결제 주문표 하나·확정 코드 하나·취소 대기열 하나, 포트원 V2 연동, 웹훅, READY 정리, 대사. 가짜 결제 게이트웨이(local·시험)와 포트원 테스트 채널(dev)로 검증한다.
정책 원천은 `docs/domain/commerce.md` 1-2(D10)·2-3·3·8·12-4·13-2·13-4·14·15·16·18·19·20·22절이고, 0단계 장치(포트원 설정 표·멱등·메일 대기열·배치 core·워커 기록·시험 DB)는 `commerce-foundation-contract.md` 에 있다. 이 문서는 2a 에서 정한 구조와 결정만 적는다.
**테스트**: 이 문서와 commerce.md 에 적힌 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절). 잠금·동시성·제약은 `CommerceTestDatabase`(실제 PostgreSQL), 포트원은 대역 HTTP.

## 작업 단위와 순서
| 단위 | 내용 | 담당 | 상태 |
|---|---|---|---|
| 2a-1 화면 목업 | admin 결제 주문(목록·상세 서랍: 이력·취소·웹훅) · 웹훅 수신 내역 · 결제 이상 · 날짜별 대사 · 대사 불일치 · `/settings/portone` 카드 셋, user-web `/dev/payment`(시험 주문 + 가짜 결제창) | frontend | **화면 확정 2026-10-26**(아래 「2a-1 화면 목업」, 브랜치 `claude/commerce-2a-1-payment-screens-4ae2d1`) |
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

## 미확정 (포트원 문서로 구현 때 확인 — commerce.md 22절)
- 웹훅 버전과 이벤트 이름(`Transaction.Paid`·`Cancelled`·`PartialCancelled`), 취소 웹훅의 `cancellationId` 유무, 다건 조회 요청 방식(페이지·커서), `paymentId` 길이 제한(우리 36자).
