-- 배치 보강(커머스 0-2) — docs/domain/commerce.md 15-2~15-4, docs/features/batch-contract.md 「0-2 보강」, docs/features/commerce-foundation-contract.md(결정 6·7·16)
--
-- Spring Batch 를 쓰지 않는 대신 자체 배치에 이력·재실행을 더한다.
--   batch_runs 칸 추가   실행 방식(보통·실패 지점부터·실패 건만), 실행 파라미터, 읽음·실패·건너뜀 건수, 이어 받은 실행, 체크포인트, 만든 요청
--   batch_run_steps      단계별 진행(상태·건수·체크포인트)
--   batch_run_failures   건별 실패(실패한 건만 — 성공 건은 건수만 센다). 비밀값·개인정보는 앱이 가려 넣는다
--   batch_run_requests   「지금 실행」·재실행 요청 표 — 배치를 켠 서버(roboyam.batch.enabled)가 FOR UPDATE SKIP LOCKED 로 집어 실행한다
--   worker_heartbeats    짧은 주기 워커의 마지막 성공·오류(실행마다 로그를 쌓지 않는다). 멈춤 알람은 4단계(결정 16)
--   batch_db_jobs.cadence  DB 배치의 기본 주기 종류(매일·매시) — 매시 등록 도우미(R__batch_00_helpers.sql 의 batch_define_hourly)
-- DB 배치(저장 프로시저)가 넣는 batch_runs 줄은 새 칸을 비워 둔다(기본값만) — 프로시저는 그대로다.

-- ── 실행 로그: 실행 방식·파라미터·건수·이어 받기 ──────────────────────────────
-- run_mode: NORMAL 예약·지금 실행 · RESUME 실패 지점부터 다시 · RETRY_FAILED 실패 건만 다시.
-- params: 실행 파라미터(JSON 객체, 값은 모두 글자). 없으면 null.
-- read/failed/skipped_count: 읽음·건너뜀 = 주 단계의 건수, 실패 = 단계 합(확정 규칙 1). 처리 건수는 기존 affected_count 다(목록·알림이 쓴다).
-- resumed_from_run_id: 이 실행이 다시 돌린 원래 실행(RESUME·RETRY_FAILED). 원래 실행이 보관 기간으로 지워지면 비운다.
-- checkpoint: 마지막으로 끝낸 덩어리의 정렬 키(글) — 실패한 단계(없으면 주 단계)의 것.
-- request_id: 이 실행을 만든 요청(예약 실행은 null).
ALTER TABLE batch_runs
    ADD COLUMN run_mode            varchar(12) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN params              text,
    ADD COLUMN read_count          bigint,
    ADD COLUMN failed_count        bigint,
    ADD COLUMN skipped_count       bigint,
    ADD COLUMN resumed_from_run_id bigint REFERENCES batch_runs (id) ON DELETE SET NULL,
    ADD COLUMN checkpoint          varchar(200),
    ADD COLUMN request_id          bigint;
ALTER TABLE batch_runs ADD CONSTRAINT ck_batch_runs_mode CHECK (run_mode IN ('NORMAL', 'RESUME', 'RETRY_FAILED'));
ALTER TABLE batch_runs ADD CONSTRAINT ck_batch_runs_counts
    CHECK ((read_count IS NULL OR read_count >= 0) AND (failed_count IS NULL OR failed_count >= 0) AND (skipped_count IS NULL OR skipped_count >= 0));
-- 확정 규칙 3: 한 실행은 한 번만 이어 받는다(「실패 지점부터 다시」). 「실패 건만 다시」는 여러 번 할 수 있다
CREATE UNIQUE INDEX ux_batch_runs_resumed ON batch_runs (resumed_from_run_id) WHERE run_mode = 'RESUME';
-- 이어 준 실행 찾기(상세의 resumedByRunIds)와 원래 실행을 지울 때의 SET NULL
CREATE INDEX ix_batch_runs_resumed_from ON batch_runs (resumed_from_run_id) WHERE resumed_from_run_id IS NOT NULL;

-- ── 단계 ────────────────────────────────────────────────────────────────────
-- 작업이 정의한 단계마다 한 줄(한 단계짜리 작업도 1줄). 실행을 시작할 때 모두 PENDING(또는 고르지 않았거나 이어 받은 실행에서 이미 끝난 단계는 SKIP)으로 넣는다.
-- 건수·체크포인트는 덩어리를 저장 확정하는 트랜잭션에서 덩어리마다 한 번 고친다(성공 건을 건별로 기록하지 않는다 — 15-3 성능 규칙).
-- 실행 줄을 지우면(보관 기간) 같이 지워진다.
CREATE TABLE batch_run_steps (
    run_id          bigint       NOT NULL REFERENCES batch_runs (id) ON DELETE CASCADE,
    step_name       varchar(40)  NOT NULL,
    step_order      smallint     NOT NULL,
    status          varchar(8)   NOT NULL,
    read_count      bigint       NOT NULL DEFAULT 0,
    processed_count bigint       NOT NULL DEFAULT 0,
    failed_count    bigint       NOT NULL DEFAULT 0,
    skipped_count   bigint       NOT NULL DEFAULT 0,
    started_at      timestamptz,
    finished_at     timestamptz,
    checkpoint      varchar(200),
    PRIMARY KEY (run_id, step_name),
    CONSTRAINT ck_batch_run_steps_name CHECK (step_name ~ '^[a-z][a-z0-9-]{0,39}$'),
    CONSTRAINT ck_batch_run_steps_status CHECK (status IN ('PENDING', 'RUNNING', 'OK', 'FAIL', 'SKIP')),
    CONSTRAINT ck_batch_run_steps_counts CHECK (read_count >= 0 AND processed_count >= 0 AND failed_count >= 0 AND skipped_count >= 0),
    CONSTRAINT uq_batch_run_steps_order UNIQUE (run_id, step_order)
);

-- ── 건별 실패 ────────────────────────────────────────────────────────────────
-- 실패한 건만 남긴다(덩어리가 실패하면 그 덩어리를 한 건씩 다시 돌려 실패한 건을 찾는다). 실패 한도(작업별 failure_limit, 기본 100)를 넘으면 실행을 멈추므로 무한히 쌓이지 않는다.
-- message: 앱이 비밀값·개인정보(이메일·토큰·「(칸)=(값)」)를 가린 한 줄, 500자까지. target_id 는 대상의 ID(글자) — 개인정보를 넣지 않는다.
-- attempts: 「실패 건만 다시」에서 또 실패하면 원래 줄은 미해결인 채 +1, 새 실행에 새 줄(원래 + 1)이 생긴다(확정 규칙 4). 성공하면 원래 줄을 해결 표시한다.
CREATE TABLE batch_run_failures (
    id                 bigint       PRIMARY KEY,
    run_id             bigint       NOT NULL REFERENCES batch_runs (id) ON DELETE CASCADE,
    step_name          varchar(40)  NOT NULL,
    target_type        varchar(40)  NOT NULL,
    target_id          varchar(100) NOT NULL,
    error_code         varchar(80)  NOT NULL,
    message            varchar(500) NOT NULL,
    attempts           integer      NOT NULL DEFAULT 1,
    resolved           boolean      NOT NULL DEFAULT false,
    resolved_by_run_id bigint       REFERENCES batch_runs (id) ON DELETE SET NULL,
    resolved_at        timestamptz,
    occurred_at        timestamptz  NOT NULL,
    CONSTRAINT ck_batch_run_failures_attempts CHECK (attempts >= 1),
    CONSTRAINT ck_batch_run_failures_resolved CHECK (resolved = (resolved_at IS NOT NULL))
);
-- 「실패 건만 다시」가 집는 미해결 건(단계별)과 상세의 미해결 수
CREATE INDEX ix_batch_run_failures_unresolved ON batch_run_failures (run_id, step_name, id) WHERE NOT resolved;
-- 상세의 실패 건 목록(일어난 시각 최근순)
CREATE INDEX ix_batch_run_failures_run ON batch_run_failures (run_id, occurred_at DESC, id DESC);
-- 해결한 실행이 지워질 때의 SET NULL
CREATE INDEX ix_batch_run_failures_resolved_by ON batch_run_failures (resolved_by_run_id) WHERE resolved_by_run_id IS NOT NULL;

-- ── 실행 요청 ────────────────────────────────────────────────────────────────
-- 화면의 「지금 실행」(파라미터 지정 실행 포함)·「실패 지점부터 다시」·「실패 건만 다시」는 이 표에 줄만 넣고 바로 돌아온다(202). 배치를 켠 서버가 집어 실행한다 —
-- 같은 서버든 다른 서버든 같은 방식이라 배치 서버를 따로 떼어도 새 장치가 필요 없다(15-2).
-- kind: NOW · RESUME · RETRY_FAILED. status: PENDING(대기) → RUNNING(집음 — node_id, 실행이 시작되면 run_id) → DONE(run_id) 또는 REJECTED(message_key — 사유).
-- params: JSON 객체(값은 글자). steps: 고른 단계 키의 JSON 배열(모두 돌리면 null). source_run_id: RESUME·RETRY_FAILED 의 원래 실행.
CREATE TABLE batch_run_requests (
    id            bigint      PRIMARY KEY,
    job_key       varchar(40) NOT NULL,
    kind          varchar(12) NOT NULL,
    status        varchar(8)  NOT NULL,
    params        text,
    steps         text,
    source_run_id bigint      REFERENCES batch_runs (id) ON DELETE SET NULL,
    run_id        bigint      REFERENCES batch_runs (id) ON DELETE SET NULL,
    operator_id   bigint      REFERENCES operators (id) ON DELETE SET NULL,
    requested_at  timestamptz NOT NULL,
    started_at    timestamptz,
    finished_at   timestamptz,
    message_key   varchar(80),
    message_args  text,
    node_id       integer,
    CONSTRAINT ck_batch_run_requests_key CHECK (job_key ~ '^[a-z][a-z0-9-]{0,39}$'),
    CONSTRAINT ck_batch_run_requests_kind CHECK (kind IN ('NOW', 'RESUME', 'RETRY_FAILED')),
    CONSTRAINT ck_batch_run_requests_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'REJECTED')),
    CONSTRAINT ck_batch_run_requests_started CHECK ((status = 'PENDING') = (started_at IS NULL)),
    CONSTRAINT ck_batch_run_requests_finished CHECK ((status IN ('DONE', 'REJECTED')) = (finished_at IS NOT NULL))
);
-- 같은 배치에 끝나지 않은(PENDING·RUNNING) 요청은 하나만 — 두 번째 「지금 실행」은 유일 위반(앱이 409 BATCH_RUNNING 으로 답한다)
CREATE UNIQUE INDEX ux_batch_run_requests_open ON batch_run_requests (job_key) WHERE status IN ('PENDING', 'RUNNING');
-- 집기(오래된 대기 요청부터)
CREATE INDEX ix_batch_run_requests_pending ON batch_run_requests (requested_at, id) WHERE status = 'PENDING';
-- 남은 RUNNING 요청 정리
CREATE INDEX ix_batch_run_requests_running ON batch_run_requests (started_at) WHERE status = 'RUNNING';
-- 보관 기간 정리(batch-log-cleanup)
CREATE INDEX ix_batch_run_requests_finished ON batch_run_requests (finished_at) WHERE finished_at IS NOT NULL;
-- 실행 줄을 지울 때의 SET NULL, 상세의 「이 실행에 대한 요청」
CREATE INDEX ix_batch_run_requests_run ON batch_run_requests (run_id) WHERE run_id IS NOT NULL;
CREATE INDEX ix_batch_run_requests_source ON batch_run_requests (source_run_id) WHERE source_run_id IS NOT NULL;

ALTER TABLE batch_runs ADD CONSTRAINT fk_batch_runs_request FOREIGN KEY (request_id) REFERENCES batch_run_requests (id) ON DELETE SET NULL;
CREATE INDEX ix_batch_runs_request ON batch_runs (request_id) WHERE request_id IS NOT NULL;

-- ── 워커 기록 ────────────────────────────────────────────────────────────────
-- 짧은 주기 워커(요청 집기·알림 감시·이후의 선점 만료·메일 발송 …)는 실행마다 로그를 남기지 않고 워커마다 한 줄만 고친다(15-4).
-- 일이 없을 때는 일정 간격(roboyam.batch.heartbeat-idle-seconds, 기본 30초)에 한 번만 고친다. last_error 는 가린 한 줄(500자).
-- 서버가 여러 대면 마지막으로 쓴 서버가 남는다(node_id). 멈춤 알람(기대 주기의 3배 동안 성공 없음)은 4단계에서 이 표를 읽어 만든다.
CREATE TABLE worker_heartbeats (
    worker_key      varchar(60)  PRIMARY KEY,
    node_id         integer,
    last_success_at timestamptz,
    last_count      bigint,
    last_error      varchar(500),
    last_error_at   timestamptz,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT ck_worker_heartbeats_key CHECK (worker_key ~ '^[a-z][a-z0-9-]{0,59}$')
);

-- ── DB 배치 정의: 기본 주기 종류 ─────────────────────────────────────────────
-- DAILY 매일 kst_hour:kst_minute(한국시간) · HOURLY 매시 kst_minute 분(kst_hour 는 쓰지 않는다 — 0). 기본 cron 환산(batch_default_cron)이 이 값을 본다.
ALTER TABLE batch_db_jobs ADD COLUMN cadence varchar(8) NOT NULL DEFAULT 'DAILY';
ALTER TABLE batch_db_jobs ADD CONSTRAINT ck_batch_db_jobs_cadence CHECK (cadence IN ('DAILY', 'HOURLY'));
