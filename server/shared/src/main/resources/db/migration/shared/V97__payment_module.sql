-- 커머스 2a 결제 모듈(포트원 V2) — docs/features/commerce-payment-contract.md 「표」·「확정 흐름」·「세션 정리 결정」, docs/domain/commerce.md 8절·16-1.
-- 번호: 가번호 V90 → develop 병합(2026-10-27) 때 V97 로 확정 — 소모임 V90~V96 뒤(계약 「develop 병합」).
--
-- 무엇이 생기나
--   · 결제 주문(payment_orders): 결제 시도 한 건 = 한 줄. 서버가 만든 payment_id(pay_ + 32자)·금액·유효 시각. 상태 전이는 조건부 UPDATE(앱).
--     「받아들인 결제는 주문마다 하나」 — 부분 유일 색인(PAID·PARTIAL_CANCELED 이고 이상 없음, 결정 3). 이상 결제(늦은 결제·도메인 거절·금액 불일치)는 세지 않는다.
--   · 취소 대기열(payment_cancels): 모든 PG 취소. 우리 상태(PENDING·SENDING·SUCCEEDED·GAVE_UP·MANUAL)와 포트원 응답(pg_status)을 따로 둔다(결정 2). request_key 유일 = 같은 취소를 두 번 넣지 않는다.
--   · 결제 이력(payment_events): 결제 한 건의 일(만듦·결제·이상·만료·취소·웹훅). provider_event_id(웹훅 webhook-id) 부분 유일 = 같은 웹훅은 한 번만. detail 에 개인정보를 넣지 않는다.
--   · 웹훅 수신 기록(payment_webhook_logs): 받은 웹훅 전부(본문은 저장하지 않는다 — 90일 정리는 2a-3 의 R__batch_31).
--   · 대사(reconciliation_issues·payment_reconcile_days): 2a-3 의 대사 배치가 쓴다. 콘솔 직접 취소 웹훅(P18)은 2a-2 가 CANCEL_MISMATCH 이슈로 바로 남긴다. 열린 이슈는 (종류, 결제) 하나.
--   · 가짜 PG(fake_pg_payments): local·시험 전용 가짜 게이트웨이의 결제 상태(api·admin 두 서버가 같은 가짜 결제를 본다, 결정 8). dev·prod 에서는 비어 있다(가짜 구현이면 기동을 막는다).
-- 새 표는 다른 모듈 표에 외래키를 걸지 않는다(commerce.md 3절 — 회원은 user_id, 대상은 order_type + order_ref_id). 금액 bigint(원), 시각 timestamptz, ID Snowflake.

-- ── 결제 주문 ────────────────────────────────────────────────────────────────
-- status: READY(결제 대기) → PAID → PARTIAL_CANCELED → CANCELED / READY → EXPIRED(유효 시각이 지남·재결제로 닫힘) → (늦게 결제되면) PAID + anomaly LATE_PAYMENT.
-- paid_amount: 포트원이 알려 준 결제 금액(amount.total — 우리가 청구한 금액과 비교한다). canceled_amount: 성공한 취소의 누계(0 ≤ canceled ≤ paid).
-- confirmed_via: 결제를 확정한 길(완료 API·웹훅·READY 정리·관리자 다시 확인).
CREATE TABLE payment_orders (
    id              bigint        PRIMARY KEY,
    payment_id      varchar(40)   NOT NULL,
    user_id         bigint        NOT NULL,
    order_type      varchar(20)   NOT NULL,
    order_ref_id    bigint        NOT NULL,
    order_name      varchar(100)  NOT NULL,
    amount          bigint        NOT NULL,
    currency        varchar(3)    NOT NULL DEFAULT 'KRW',
    status          varchar(20)   NOT NULL,
    paid_amount     bigint,
    canceled_amount bigint        NOT NULL DEFAULT 0,
    anomaly         varchar(20),
    pay_method      varchar(40),
    pg_provider     varchar(40),
    pg_tx_id        varchar(200),
    receipt_url     varchar(1000),
    paid_at         timestamptz,
    expires_at      timestamptz   NOT NULL,
    confirmed_via   varchar(20),
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    CONSTRAINT ck_payment_orders_payment_id CHECK (payment_id ~ '^pay_[A-Za-z0-9]{32}$'),
    CONSTRAINT ck_payment_orders_type CHECK (order_type IN ('POINT_CHARGE', 'GROUP_BUY', 'PAYMENT_TEST')),
    CONSTRAINT ck_payment_orders_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_orders_currency CHECK (currency = 'KRW'),
    CONSTRAINT ck_payment_orders_status CHECK (status IN ('READY', 'PAID', 'PARTIAL_CANCELED', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_payment_orders_anomaly CHECK (anomaly IS NULL OR anomaly IN ('AMOUNT_MISMATCH', 'DOMAIN_REJECTED', 'LATE_PAYMENT')),
    CONSTRAINT ck_payment_orders_confirmed_via CHECK (confirmed_via IS NULL OR confirmed_via IN ('COMPLETE_API', 'WEBHOOK', 'READY_CLEANUP', 'ADMIN_RECHECK')),
    CONSTRAINT ck_payment_orders_paid_amount CHECK (paid_amount IS NULL OR paid_amount >= 0),
    CONSTRAINT ck_payment_orders_canceled CHECK (canceled_amount >= 0 AND canceled_amount <= coalesce(paid_amount, 0)),
    -- 결제된 계열(PAID·PARTIAL_CANCELED·CANCELED)이면 결제 금액·결제 시각이 반드시 있다
    CONSTRAINT ck_payment_orders_paid_fields CHECK (status NOT IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED') OR (paid_amount IS NOT NULL AND paid_at IS NOT NULL AND confirmed_via IS NOT NULL)),
    -- 이상 표시는 결제된 계열에만
    CONSTRAINT ck_payment_orders_anomaly_paid CHECK (anomaly IS NULL OR status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED'))
);
CREATE UNIQUE INDEX ux_payment_orders_payment_id ON payment_orders (payment_id);
-- 받아들인 결제는 주문마다 하나(결정 3 — 이상 결제는 세지 않는다)
CREATE UNIQUE INDEX ux_payment_orders_accepted ON payment_orders (order_type, order_ref_id)
    WHERE status IN ('PAID', 'PARTIAL_CANCELED') AND anomaly IS NULL;
-- READY 정리 배치(유효 시각 + 여유가 지난 READY 전부)
CREATE INDEX ix_payment_orders_ready_expiry ON payment_orders (expires_at, id) WHERE status = 'READY';
CREATE INDEX ix_payment_orders_user ON payment_orders (user_id, created_at);
CREATE INDEX ix_payment_orders_order ON payment_orders (order_type, order_ref_id);
CREATE INDEX ix_payment_orders_paid_at ON payment_orders (paid_at);
CREATE INDEX ix_payment_orders_anomaly ON payment_orders (created_at) WHERE anomaly IS NOT NULL;

-- ── 취소 대기열 ──────────────────────────────────────────────────────────────
-- status(우리 상태): PENDING(보낼 차례를 기다림) → SENDING(워커·즉시 시도가 집음, locked_by·locked_at) → SUCCEEDED / PENDING(재시도, next_attempt_at) / GAVE_UP(재시도를 다 씀) / MANUAL(운영자 처리).
-- pg_status: 포트원 취소 응답(REQUESTED 접수만 · SUCCEEDED · FAILED). pg_cancel_id: 포트원 취소 내역 아이디(cancellation.id — 웹훅 cancellationId 와 맞춘다).
-- request_key: 같은 취소를 두 번 넣지 않는 자연 키(예 AUTO_REJECT:<결제 주문 ID>). requested_by: 운영자(시스템이면 NULL). last_error 는 가린 한 줄.
CREATE TABLE payment_cancels (
    id               bigint        PRIMARY KEY,
    payment_order_id bigint        NOT NULL,
    amount           bigint        NOT NULL,
    reason           varchar(200)  NOT NULL,
    kind             varchar(20)   NOT NULL,
    request_key      varchar(120)  NOT NULL,
    status           varchar(10)   NOT NULL,
    pg_status        varchar(10),
    pg_cancel_id     varchar(200),
    attempts         integer       NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz   NOT NULL,
    locked_by        integer,
    locked_at        timestamptz,
    last_error       varchar(500),
    requested_by     bigint,
    resolved_by      bigint,
    resolve_note     varchar(200),
    created_at       timestamptz   NOT NULL,
    completed_at     timestamptz,
    CONSTRAINT ck_payment_cancels_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_cancels_kind CHECK (kind IN ('USER_CANCEL', 'REFUND', 'AUTO_REJECT', 'ADMIN', 'CHARGE_REFUND')),
    CONSTRAINT ck_payment_cancels_status CHECK (status IN ('PENDING', 'SENDING', 'SUCCEEDED', 'GAVE_UP', 'MANUAL')),
    CONSTRAINT ck_payment_cancels_pg_status CHECK (pg_status IS NULL OR pg_status IN ('REQUESTED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_payment_cancels_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_payment_cancels_request_key CHECK (length(request_key) BETWEEN 1 AND 120),
    CONSTRAINT ck_payment_cancels_locked CHECK ((status = 'SENDING') = (locked_by IS NOT NULL AND locked_at IS NOT NULL)),
    CONSTRAINT ck_payment_cancels_done CHECK ((status IN ('SUCCEEDED', 'GAVE_UP', 'MANUAL')) = (completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_payment_cancels_request_key ON payment_cancels (request_key);
CREATE UNIQUE INDEX ux_payment_cancels_pg_cancel_id ON payment_cancels (pg_cancel_id) WHERE pg_cancel_id IS NOT NULL;
CREATE INDEX ix_payment_cancels_due ON payment_cancels (next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX ix_payment_cancels_order ON payment_cancels (payment_order_id);
CREATE INDEX ix_payment_cancels_gave_up ON payment_cancels (created_at) WHERE status = 'GAVE_UP';
-- 오래 머문 SENDING 되살리기
CREATE INDEX ix_payment_cancels_sending ON payment_cancels (locked_at) WHERE status = 'SENDING';

-- ── 결제 이력(거래 기록 — 5년) ───────────────────────────────────────────────
CREATE TABLE payment_events (
    id                bigint        PRIMARY KEY,
    payment_order_id  bigint        NOT NULL,
    kind              varchar(30)   NOT NULL,
    provider_event_id varchar(200),
    detail            jsonb,
    created_at        timestamptz   NOT NULL,
    CONSTRAINT ck_payment_events_kind CHECK (kind IN ('CREATED', 'PAID', 'ANOMALY', 'EXPIRED', 'CANCEL_REQUESTED', 'CANCEL_SUCCEEDED', 'CANCEL_RETRY',
        'CANCEL_GAVE_UP', 'CANCEL_MANUAL', 'EXTERNAL_CANCEL', 'WEBHOOK'))
);
-- 같은 웹훅(webhook-id)은 한 번만
CREATE UNIQUE INDEX ux_payment_events_provider_event ON payment_events (provider_event_id) WHERE provider_event_id IS NOT NULL;
CREATE INDEX ix_payment_events_order ON payment_events (payment_order_id, created_at);

-- ── 웹훅 수신 기록(90일) ─────────────────────────────────────────────────────
CREATE TABLE payment_webhook_logs (
    id          bigint        PRIMARY KEY,
    webhook_id  varchar(200),
    event_type  varchar(60),
    payment_id  varchar(100),
    result      varchar(10)   NOT NULL,
    http_status integer       NOT NULL,
    error_code  varchar(60),
    received_at timestamptz   NOT NULL,
    CONSTRAINT ck_payment_webhook_logs_result CHECK (result IN ('PROCESSED', 'IGNORED', 'REJECTED', 'RETRY'))
);
CREATE INDEX ix_payment_webhook_logs_received ON payment_webhook_logs (received_at);
CREATE INDEX ix_payment_webhook_logs_payment ON payment_webhook_logs (payment_id);
CREATE INDEX ix_payment_webhook_logs_result ON payment_webhook_logs (result, received_at);

-- ── 대사 이슈 ────────────────────────────────────────────────────────────────
-- type: PG_PAID_LOCAL_UNPAID(포트원 결제 ↔ 우리 READY·EXPIRED) · LOCAL_PAID_PG_MISSING · AMOUNT_MISMATCH · CANCEL_MISMATCH(콘솔 직접 취소 포함) · PG_ONLY(우리에게 없는 결제).
-- 자동 보정하지 않고 운영자가 처리한다(commerce.md 16-1). 다시 돌려도 중복이 없게 열린 이슈는 (종류, 결제) 하나.
CREATE TABLE reconciliation_issues (
    id               bigint        PRIMARY KEY,
    type             varchar(30)   NOT NULL,
    payment_id       varchar(100)  NOT NULL,
    payment_order_id bigint,
    detected_day     date          NOT NULL,
    detail           jsonb,
    status           varchar(10)   NOT NULL DEFAULT 'OPEN',
    resolve_note     varchar(200),
    resolved_by      bigint,
    resolved_at      timestamptz,
    created_at       timestamptz   NOT NULL,
    CONSTRAINT ck_reconciliation_issues_type CHECK (type IN ('PG_PAID_LOCAL_UNPAID', 'LOCAL_PAID_PG_MISSING', 'AMOUNT_MISMATCH', 'CANCEL_MISMATCH', 'PG_ONLY')),
    CONSTRAINT ck_reconciliation_issues_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_reconciliation_issues_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_reconciliation_issues_open ON reconciliation_issues (type, payment_id) WHERE status = 'OPEN';
CREATE INDEX ix_reconciliation_issues_status ON reconciliation_issues (status, created_at);

-- ── 대사한 날(KST 날짜) — 「마지막으로 대사한 날 다음부터 어제까지」 ─────────
CREATE TABLE payment_reconcile_days (
    day           date         PRIMARY KEY,
    status        varchar(10)  NOT NULL,
    checked_count integer      NOT NULL DEFAULT 0,
    issue_count   integer      NOT NULL DEFAULT 0,
    run_id        bigint,
    finished_at   timestamptz,
    CONSTRAINT ck_payment_reconcile_days_status CHECK (status IN ('OK', 'FAIL')),
    CONSTRAINT ck_payment_reconcile_days_counts CHECK (checked_count >= 0 AND issue_count >= 0)
);

-- ── 가짜 PG(local·시험 전용) ─────────────────────────────────────────────────
-- 포트원 결제 상태 이름을 그대로 쓴다. cancellations: [{id, amount, reason, cancelledAt}] — 가짜 게이트웨이만 읽고 쓴다.
CREATE TABLE fake_pg_payments (
    payment_id       varchar(100) PRIMARY KEY,
    status           varchar(20)  NOT NULL,
    amount           bigint       NOT NULL,
    currency         varchar(3)   NOT NULL,
    paid_at          timestamptz,
    cancelled_amount bigint       NOT NULL DEFAULT 0,
    cancellations    jsonb        NOT NULL DEFAULT '[]'::jsonb,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT ck_fake_pg_payments_status CHECK (status IN ('READY', 'PAID', 'FAILED', 'PARTIAL_CANCELLED', 'CANCELLED')),
    CONSTRAINT ck_fake_pg_payments_amount CHECK (amount >= 0 AND cancelled_amount >= 0 AND cancelled_amount <= amount)
);
