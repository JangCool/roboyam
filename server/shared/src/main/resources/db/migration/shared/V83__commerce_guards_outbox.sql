-- 돈 요청 보호·메일 대기열·감사 보관 분류(커머스 0-5) — docs/domain/commerce.md 3절(멱등)·12-4(속도 제한)·13-4(감사 보관)·14절(메일)·15-4·15-5,
-- docs/features/commerce-foundation-contract.md 「0-5」(결정 10·11·13·17).
-- 번호: develop 이 V82 까지 쓴 뒤라 V83(2026-10-26 세션 정리 — 0-4 는 V84 부터). 반복 마이그레이션은 R__batch_25·26·27.
--
--   idempotency_requests    회원 돈 요청의 Idempotency-Key — 같은 키·같은 본문이면 처음 응답을 다시 준다(24시간)
--   request_rate_limits     요청 종류별 1분 한도(관리자 화면 「커머스 설정 › 요청 제한」, 0 = 제한 없음)
--   request_rate_counters   회원 × 요청 종류 × 1분 창의 시도 수(UNLOGGED — 비정상 종료 때 비워져도 되는 1분짜리 값)
--   mail_outbox             커머스 메일 대기열 — 업무 트랜잭션에서 넣고 관리자 서버의 워커가 보내며 재시도한다. 주소 원문은 두지 않는다
--   audit_logs.retention_class  감사 줄의 보관 분류(일반·개인정보 열람·돈 관련) — 정리 배치가 분류별 기간으로 지운다

-- ── 멱등 키 ──────────────────────────────────────────────────────────────────
-- 처리 중(IN_PROGRESS) 행은 업무와 따로 먼저 커밋하고, 결과(COMPLETED·응답)는 업무 트랜잭션 안에서 쓴다. 업무가 실패하면 행을 지워 같은 키로 다시 할 수 있다.
-- request_hash: 요청 종류 + 정렬한 본문의 SHA-256(16진 64자) — 같은 키에 다른 본문이면 409.
-- response_body: 다시 줄 응답(JSON 글, 16KB 까지). 24시간이 지난 행은 없는 것으로 보고 정리 배치(idempotency-request-cleanup)가 지운다.
CREATE TABLE idempotency_requests (
    user_id       bigint       NOT NULL,
    idem_key      varchar(64)  NOT NULL,
    action        varchar(40)  NOT NULL,
    request_hash  char(64)     NOT NULL,
    status        varchar(12)  NOT NULL,
    response_code int,
    response_body text,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    completed_at  timestamptz,
    PRIMARY KEY (user_id, idem_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_key CHECK (idem_key ~ '^[A-Za-z0-9_-]{8,64}$'),
    CONSTRAINT ck_idempotency_completed CHECK (status = 'IN_PROGRESS' OR (response_code IS NOT NULL AND completed_at IS NOT NULL)),
    CONSTRAINT ck_idempotency_body_size CHECK (response_body IS NULL OR octet_length(response_body) <= 16384)
);
CREATE INDEX ix_idempotency_created ON idempotency_requests (created_at);

-- ── 요청 속도 제한 ───────────────────────────────────────────────────────────
-- 요청 종류(action)별 회원 1명의 1분 한도. 0 = 제한 없음(세지도 않는다). 관리자 화면이 바꾸고(권한 commerce.settings, 감사 전→후), 서버들은 15초 사본으로 따라온다.
CREATE TABLE request_rate_limits (
    action           varchar(40) PRIMARY KEY,
    limit_per_minute int         NOT NULL,
    updated_at       timestamptz,
    updated_by       bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_request_rate_limit CHECK (limit_per_minute BETWEEN 0 AND 100)
);
INSERT INTO request_rate_limits (action, limit_per_minute) VALUES
    ('GROUPBUY_JOIN', 5),   -- 공구 참여(수량 선점)
    ('POINT_CHARGE', 3),    -- 포인트 충전 요청
    ('SHOP_PURCHASE', 10),  -- 상점 구매
    ('REFUND_REQUEST', 3);  -- 환불 신청(아이템·충전·반품)

-- 시도 수 — 요청마다 INSERT … ON CONFLICT … DO UPDATE SET count = count + 1 RETURNING count 한 문장, 업무와 따로 커밋한다(업무가 실패해도 시도는 남는다).
-- window_start 는 분 단위로 자른 시각. 지난 창은 매시 정리 배치(request-rate-counter-cleanup)가 지운다.
CREATE UNLOGGED TABLE request_rate_counters (
    user_id      bigint      NOT NULL,
    action       varchar(40) NOT NULL,
    window_start timestamptz NOT NULL,
    count        int         NOT NULL,
    PRIMARY KEY (user_id, action, window_start)
);
CREATE INDEX ix_request_rate_counters_window ON request_rate_counters (window_start);

-- ── 커머스 메일 대기열 ───────────────────────────────────────────────────────
-- 업무 트랜잭션 안에서 넣고(dedupe_key 유일 — 같은 일로 두 번 넣지 않는다), 배치를 켠 관리자 서버의 워커가 30초마다 FOR UPDATE SKIP LOCKED 로 집어 보낸다.
-- 받는 주소는 두지 않는다 — 보낼 때 users.email 을 읽고, 보낸 뒤 가린 주소(to_masked)만 남긴다.
-- mail_kind: 필수 안내 메일 코드(RequiredMail — 알림 문구 엔진이 문구·템플릿을 고른다). variables: 문구 변수(JSON 객체) — 끝난 줄(SENT·DEAD·CANCELED)에는 남기지 않는다(CHECK).
-- status: PENDING 보낼 차례(next_attempt_at 이후) · SENDING 워커가 집음 · SENT 보냄 · DEAD 재시도 한도 · CANCELED 받을 수 없는 회원(주소 없음·미인증·탈퇴)·종류 없음.
CREATE TABLE mail_outbox (
    id              bigint       PRIMARY KEY,
    dedupe_key      varchar(200) NOT NULL,
    user_id         bigint       NOT NULL,
    mail_kind       varchar(60)  NOT NULL,
    variables       jsonb,
    status          varchar(10)  NOT NULL DEFAULT 'PENDING',
    attempts        int          NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    locked_at       timestamptz,
    locked_by       int,
    last_error      varchar(500),
    to_masked       varchar(320),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    finished_at     timestamptz,
    CONSTRAINT ux_mail_outbox_dedupe UNIQUE (dedupe_key),
    CONSTRAINT ck_mail_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'DEAD', 'CANCELED')),
    CONSTRAINT ck_mail_outbox_no_body_after CHECK (status IN ('PENDING', 'SENDING') OR variables IS NULL),
    CONSTRAINT ck_mail_outbox_finished CHECK ((status IN ('PENDING', 'SENDING')) = (finished_at IS NULL)),
    CONSTRAINT ck_mail_outbox_sent CHECK (status <> 'SENT' OR sent_at IS NOT NULL),
    CONSTRAINT ck_mail_outbox_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_mail_outbox_due ON mail_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_mail_outbox_sending ON mail_outbox (locked_at) WHERE status = 'SENDING';
CREATE INDEX ix_mail_outbox_finished ON mail_outbox (finished_at) WHERE finished_at IS NOT NULL;

-- ── 감사 보관 분류 ───────────────────────────────────────────────────────────
-- 감사 줄을 남길 때 행위마다 분류를 붙인다(Java AuditRetention). SQL 이 직접 남기는 줄(V30·admin_mfa_reset)은 기본값 GENERAL.
-- 정리 배치(audit-log-retention)가 분류별 기간(batch_params — 하한보다 짧은 값은 무시하고 기본값)으로 지운다.
ALTER TABLE audit_logs ADD COLUMN retention_class varchar(16) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_retention_class CHECK (retention_class IN ('GENERAL', 'PERSONAL_DATA', 'MONEY'));
CREATE INDEX ix_audit_logs_retention ON audit_logs (retention_class, occurred_at);
