-- 배치 인프라 — docs/features/roles-contract.md 「배치 인프라 결과」, docs/features/batch-contract.md
--
-- 배치의 **정의(키·이름·설명·분류·기본 cron·기본 사용 여부)는 코드(카탈로그)가 원천**이다. 이 파일은 운영자가 바꾸는 값(사용 여부·cron)과
-- 마지막 실행 요약(batch_jobs), 실행 로그(batch_runs)만 만든다. batch_jobs 의 행은 서버가 뜰 때 카탈로그와 맞춘다(없는 배치만 기본값으로 넣고,
-- 카탈로그에서 사라진 배치의 행은 지운다 — 운영자가 바꾼 값은 덮어쓰지 않는다).
--
-- 예약 실행은 관리자 서버(server/admin)가 한다. 서버가 여러 대여도 한 번만 돌도록 배치마다 DB 어드바이저리 잠금을 잡고(실행 중 배타),
-- 예약 시각(슬롯)마다 batch_runs 에 한 줄만 들어가게 유일 색인으로 막는다(잠금과 별개의 이중 방어).

-- ── 배치 설정 + 마지막 실행 요약 ────────────────────────────────────────────
-- job_key: 카탈로그의 키(소문자·숫자·하이픈). cron: Spring 6칸 식(초 분 시 일 월 요일), 시간대는 앱이 Asia/Seoul 로 해석한다.
-- last_*: 마지막 실행의 요약(목록 화면용 사본). 문구는 키 + 인자로 들고 있다가 응답 때 요청 언어로 푼다.
CREATE TABLE batch_jobs (
    job_key             varchar(40)  PRIMARY KEY,
    enabled             boolean      NOT NULL,
    cron                varchar(100) NOT NULL,
    last_run_at         timestamptz,
    last_status         varchar(8),
    last_affected_count bigint,
    last_message_key    varchar(80),
    last_message_args   text,
    last_error          varchar(500),
    updated_by          bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_batch_jobs_key CHECK (job_key ~ '^[a-z][a-z0-9-]{0,39}$'),
    CONSTRAINT ck_batch_jobs_last_status CHECK (last_status IS NULL OR last_status IN ('RUNNING', 'OK', 'FAIL', 'SKIP'))
);

-- ── 실행 로그 ───────────────────────────────────────────────────────────────
-- status: RUNNING 실행 중 · OK 성공 · FAIL 실패 · SKIP 건너뜀(돌았지만 처리할 것이 없었다 — 처리 건수 0).
-- trigger_type: SCHEDULED 예약(scheduled_at = 예약 시각, 서버가 여러 대여도 같은 값) · MANUAL 수동(operator_id = 실행한 운영자).
-- message_key/message_args: 결과 요약(문구 키 + 인자 JSON 배열). error_message: 실패 사유(예외 이름 + 첫 줄, 민감값을 가려 500자까지).
-- job_key 에 외래키가 없다 — 카탈로그에서 사라진 배치의 기록도 보관 기간까지 남긴다. 보관 기간이 지난 로그는 「배치 로그 정리」 배치가 지운다.
CREATE TABLE batch_runs (
    id             bigint       PRIMARY KEY,
    job_key        varchar(40)  NOT NULL,
    status         varchar(8)   NOT NULL,
    trigger_type   varchar(9)   NOT NULL,
    scheduled_at   timestamptz,
    started_at     timestamptz  NOT NULL,
    finished_at    timestamptz,
    affected_count bigint,
    message_key    varchar(80),
    message_args   text,
    error_message  varchar(500),
    operator_id    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    node_id        integer,
    CONSTRAINT ck_batch_runs_status CHECK (status IN ('RUNNING', 'OK', 'FAIL', 'SKIP')),
    CONSTRAINT ck_batch_runs_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT ck_batch_runs_slot CHECK ((trigger_type = 'SCHEDULED') = (scheduled_at IS NOT NULL)),
    CONSTRAINT ck_batch_runs_finished CHECK ((status = 'RUNNING') = (finished_at IS NULL)),
    CONSTRAINT ck_batch_runs_count CHECK (affected_count IS NULL OR affected_count >= 0)
);
-- 같은 배치의 같은 예약 시각은 한 줄만 — 서버가 여러 대여도 한 번만 돈다(두 번째는 유일 위반으로 건너뛴다)
CREATE UNIQUE INDEX ux_batch_runs_slot ON batch_runs (job_key, scheduled_at) WHERE trigger_type = 'SCHEDULED';
-- 한 배치는 동시에 하나만 실행 중이다
CREATE UNIQUE INDEX ux_batch_runs_running ON batch_runs (job_key) WHERE status = 'RUNNING';
-- 로그 목록(최근순)과 배치별 최근 기록
CREATE INDEX ix_batch_runs_started ON batch_runs (started_at DESC, id DESC);
CREATE INDEX ix_batch_runs_job ON batch_runs (job_key, started_at DESC);
-- 보관 기간이 지난 로그 정리
CREATE INDEX ix_batch_runs_finished ON batch_runs (finished_at) WHERE finished_at IS NOT NULL;
