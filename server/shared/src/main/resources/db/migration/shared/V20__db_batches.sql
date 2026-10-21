-- DB 배치(저장 프로시저 + pg_cron) 기반 — docs/features/batch-contract.md 「DB 배치」
--
-- Java 배치(V10)와 DB 배치가 같은 화면·같은 로그 표(batch_runs)를 쓴다. 이 파일은 표만 손본다. 프로시저·공통 함수·cron 등록은 반복 마이그레이션(R__batch_*.sql)이다 —
-- 내용이 바뀌면 Flyway 가 다시 적용하므로 프로시저를 고치려고 새 번호를 만들지 않는다.
-- pg_cron 확장 자체는 여기서 만들지 않는다: 슈퍼유저 권한과 서버 설정(shared_preload_libraries)이 필요해서 앱 계정이 못 만드는 곳이 많고, 없어도 서버는 떠야 한다.
-- 확장이 있으면 R__batch_00_helpers.sql 의 batch_enable_pg_cron() 이 만든다(앱 계정이 슈퍼유저일 때만 성공 — 아니면 건너뛰고 DB 배치는 「사용 불가」로 보인다).

-- ── 실행 로그: 어디서 돈 배치인가 ────────────────────────────────────────────
-- JAVA 는 애플리케이션 스케줄러, POSTGRESQL 은 pg_cron 이 부른 저장 프로시저(또는 앱이 「지금 실행」으로 CALL 한 것). 기존 줄은 모두 JAVA 다.
ALTER TABLE batch_runs ADD COLUMN source varchar(10) NOT NULL DEFAULT 'JAVA';
ALTER TABLE batch_runs ADD CONSTRAINT ck_batch_runs_source CHECK (source IN ('JAVA', 'POSTGRESQL'));

-- DB 프로시저가 만드는 로그 줄의 ID. Java 쪽 줄은 앱이 Snowflake 로 발급하므로(매우 큰 수) 이 순번과 겹치지 않는다. node_id 는 DB 줄에는 없다(null).
CREATE SEQUENCE batch_run_db_id_seq;

-- ── DB 배치 정의 ─────────────────────────────────────────────────────────────
-- R__ 파일이 배치마다 한 줄씩 넣는다(매번 갱신 — 정의값이다). 이름·설명·분류는 코드(BatchCatalog)와 메시지 번들이 원천이고, 여기에는 DB 쪽 사실만 둔다:
-- 부를 프로시저, 「기본 실행 시각」(한국시간 매일 H시 M분)과 그것을 pg_cron 의 시간대(cron.timezone, 없으면 GMT)로 환산한 「기본 cron」.
-- 기본 cron 은 환산 결과라 pg_cron 설정이 바뀌거나 나중에 도입되면 다시 계산한다(batch_register_missing). 운영자가 바꾼 현재 cron·사용 여부는 cron.job 이 원천이라 여기에 두지 않는다.
CREATE TABLE batch_db_jobs (
    job_key        varchar(40)  PRIMARY KEY,
    procedure_name varchar(63)  NOT NULL,
    kst_hour       smallint     NOT NULL,
    kst_minute     smallint     NOT NULL,
    default_cron   varchar(100) NOT NULL,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_batch_db_jobs_key CHECK (job_key ~ '^[a-z][a-z0-9-]{0,39}$'),
    CONSTRAINT ck_batch_db_jobs_procedure CHECK (procedure_name ~ '^[a-z][a-z0-9_]{0,62}$'),
    CONSTRAINT ck_batch_db_jobs_time CHECK (kst_hour BETWEEN 0 AND 23 AND kst_minute BETWEEN 0 AND 59)
);

-- ── DB 배치 설정값 ───────────────────────────────────────────────────────────
-- 보관 일수·한 번에 지우는 덩어리 크기 같은 수치. 기본값은 R__ 파일이 처음에만 넣는다(이미 있으면 건드리지 않음 — 운영자가 바꾼 값을 지키려고).
-- 프로시저는 batch_param() 으로 읽고, 범위를 벗어난 값(0 일 보관 같은 사고)은 무시하고 기본값을 쓴다. 바꾸는 법: UPDATE batch_params SET param_value = … (화면은 후속).
CREATE TABLE batch_params (
    job_key     varchar(40) NOT NULL,
    param_key   varchar(40) NOT NULL,
    param_value bigint      NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (job_key, param_key),
    CONSTRAINT ck_batch_params_key CHECK (job_key ~ '^[a-z][a-z0-9-]{0,39}$' AND param_key ~ '^[a-z][a-z0-9_]{0,39}$')
);

-- ── 정리 배치가 훑는 칸의 색인 ───────────────────────────────────────────────
-- 만료/오래된 행을 덩어리로 지우려면 그 시각 칸으로 찾을 수 있어야 한다(없으면 덩어리마다 전체를 읽는다).
CREATE INDEX ix_auth_tokens_expires ON auth_tokens (expires_at);
CREATE INDEX ix_login_attempts_updated ON login_attempts (updated_at);
