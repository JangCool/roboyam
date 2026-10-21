-- DB 배치 공통 도구 — 반복 마이그레이션(내용이 바뀌면 다시 적용된다. 모두 CREATE OR REPLACE 라 여러 번 돌아도 안전하다)
-- docs/features/batch-contract.md 「DB 배치」, .claude/guides/backend/service.md 「DB 배치」
--
-- 이 파일이 DB 배치의 약속(규약)을 한 곳에 모은다. 모든 DB 배치 프로시저는 아래 도구로 로그를 남기고 설정을 읽고 자기를 등록한다.
--   batch_enable_pg_cron()   pg_cron 확장을 만들 수 있으면 만든다 / 지금 상태를 돌려준다
--   batch_define(...)        배치 정의(batch_db_jobs) 갱신 + 아직 pg_cron 에 없으면 기본 cron 으로 등록(있으면 건드리지 않는다)
--   batch_param(...)         설정값(batch_params) 읽기, 범위 밖이면 기본값
--   batch_run_begin/close    batch_runs 에 시작(RUNNING) / 끝(OK·SKIP·FAIL) 기록 + 동시 실행 방지(어드바이저리 잠금)

-- ── pg_cron 확장 ─────────────────────────────────────────────────────────────
-- pg_cron 의 지금 상태(읽기만 한다). 앱이 「사용 불가」 이유를 보이는 데에도 쓴다.
--   READY           쓸 수 있다(확장이 있고 이 계정이 cron 스키마를 쓸 수 있다)
--   NO_PERMISSION   확장은 있는데 이 계정에 cron 스키마 사용 권한이 없다(GRANT USAGE ON SCHEMA cron TO <앱 계정> 필요)
--   NOT_CREATED     패키지·서버 설정은 맞는데 이 DB 에 CREATE EXTENSION 이 안 되어 있다(슈퍼유저가 해야 한다)
--   MISSING_PACKAGE 서버에 pg_cron 패키지가 없다
--   NOT_PRELOADED   shared_preload_libraries 에 pg_cron 이 없다(서버 설정 + 재기동 필요)
--   WRONG_DATABASE  cron.database_name 이 이 DB 가 아니다(pg_cron 은 그 DB 하나에서만 돈다)
CREATE OR REPLACE FUNCTION batch_pg_cron_status() RETURNS text
LANGUAGE plpgsql STABLE AS $fn$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        IF to_regnamespace('cron') IS NOT NULL AND has_schema_privilege('cron', 'USAGE') THEN
            RETURN 'READY';
        END IF;
        RETURN 'NO_PERMISSION';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_cron') THEN
        RETURN 'MISSING_PACKAGE';
    END IF;
    IF coalesce(current_setting('shared_preload_libraries', true), '') NOT LIKE '%pg_cron%' THEN
        RETURN 'NOT_PRELOADED';
    END IF;
    IF current_setting('cron.database_name', true) IS DISTINCT FROM current_database() THEN
        RETURN 'WRONG_DATABASE';
    END IF;
    RETURN 'NOT_CREATED';
END
$fn$;

CREATE OR REPLACE FUNCTION batch_pg_cron_ready() RETURNS boolean
LANGUAGE sql STABLE AS $fn$
    SELECT batch_pg_cron_status() = 'READY'
$fn$;

-- 만들 수 있으면 pg_cron 확장을 만들고 지금 상태(batch_pg_cron_status)를 돌려준다. CREATE EXTENSION 은 슈퍼유저 권한이 필요하다(앱 계정이 슈퍼유저인 도커 구성에서는
-- 성공하고, RDS 등에서는 DBA 가 미리 만들어 두거나 이 호출이 건너뛴다). 실패해도 예외를 던지지 않는다 — pg_cron 이 없어도 서버와 Java 배치는 정상이어야 한다.
-- 앱이 뜰 때마다 부르므로 이미 만들어져 있으면 아무것도 하지 않는다.
CREATE OR REPLACE FUNCTION batch_enable_pg_cron() RETURNS text
LANGUAGE plpgsql AS $fn$
BEGIN
    IF batch_pg_cron_status() = 'NOT_CREATED' THEN
        BEGIN
            CREATE EXTENSION pg_cron;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'pg_cron 확장을 만들지 못했다(%) — 슈퍼유저가 이 DB 에서 CREATE EXTENSION pg_cron 을 해야 한다', SQLERRM;
        END;
    END IF;
    RETURN batch_pg_cron_status();
END
$fn$;

-- ── 시각 ─────────────────────────────────────────────────────────────────────
-- 「매일 한국시간 H시 M분」을 pg_cron 이 해석하는 시간대(cron.timezone, 설정이 없으면 GMT)의 5칸 식으로 바꾼다.
-- 기본 cron 을 한국시간으로 적어도 pg_cron 이 GMT 로 도는 서버(RDS 등)에서 시각이 어긋나지 않게 한다.
CREATE OR REPLACE FUNCTION batch_daily_cron(p_hour int, p_minute int) RETURNS text
LANGUAGE plpgsql STABLE AS $fn$
DECLARE
    v_zone  text := coalesce(nullif(current_setting('cron.timezone', true), ''), 'GMT');
    v_local timestamp;
BEGIN
    v_local := ((timestamp '2001-01-01 00:00:00' + make_interval(hours => p_hour, mins => p_minute)) AT TIME ZONE 'Asia/Seoul') AT TIME ZONE v_zone;
    RETURN extract(minute FROM v_local)::int || ' ' || extract(hour FROM v_local)::int || ' * * *';
END
$fn$;

-- ── 설정값 ───────────────────────────────────────────────────────────────────
-- 값이 없거나 [p_min, p_max] 밖이면 기본값. 보관 일수가 0 이 되어 전부 지워지는 사고를 막는다.
CREATE OR REPLACE FUNCTION batch_param(p_job text, p_key text, p_default bigint, p_min bigint, p_max bigint) RETURNS bigint
LANGUAGE sql STABLE AS $fn$
    SELECT coalesce((SELECT param_value FROM batch_params
                      WHERE job_key = p_job AND param_key = p_key AND param_value BETWEEN p_min AND p_max), p_default)
$fn$;

-- 기본값을 처음에만 넣는다(이미 있으면 그대로 — 운영자가 바꾼 값을 지킨다)
CREATE OR REPLACE FUNCTION batch_param_seed(p_job text, p_key text, p_value bigint) RETURNS void
LANGUAGE sql AS $fn$
    INSERT INTO batch_params (job_key, param_key, param_value) VALUES (p_job, p_key, p_value) ON CONFLICT (job_key, param_key) DO NOTHING
$fn$;

-- ── 등록 ─────────────────────────────────────────────────────────────────────
-- 정의된 배치 하나를 pg_cron 에 등록한다. 돌려주는 값: REGISTERED(새로 등록) · KEPT(이미 있어 그대로 — 운영자가 바꾼 cron·사용 여부를 덮어쓰지 않는다) ·
-- NO_PG_CRON(pg_cron 을 쓸 수 없다) · NO_DEFINITION(정의 없음) · FAILED(등록 권한 없음 등).
-- cron.schedule() 은 같은 이름이 있으면 식을 덮어쓰므로 반드시 먼저 있는지 확인한다.
CREATE OR REPLACE FUNCTION batch_register(p_job text) RETURNS text
LANGUAGE plpgsql AS $fn$
DECLARE
    v_def batch_db_jobs%ROWTYPE;
    v_exists boolean;
BEGIN
    SELECT * INTO v_def FROM batch_db_jobs WHERE job_key = p_job;
    IF NOT FOUND THEN
        RETURN 'NO_DEFINITION';
    END IF;
    IF NOT batch_pg_cron_ready() THEN
        RETURN 'NO_PG_CRON';
    END IF;
    -- 기본 cron 은 pg_cron 의 시간대에 달려 있다 — 등록하기 직전의 설정으로 다시 환산한다(pg_cron 이 없던 때 GMT 로 계산해 둔 값을 쓰지 않게)
    v_def.default_cron := batch_daily_cron(v_def.kst_hour, v_def.kst_minute);
    UPDATE batch_db_jobs SET default_cron = v_def.default_cron WHERE job_key = p_job AND default_cron IS DISTINCT FROM v_def.default_cron;
    BEGIN
        EXECUTE 'SELECT EXISTS (SELECT 1 FROM cron.job WHERE jobname = $1)' INTO v_exists USING p_job;
        IF v_exists THEN
            RETURN 'KEPT';
        END IF;
        PERFORM cron.schedule(p_job, v_def.default_cron, format('CALL %I()', v_def.procedure_name));
        RETURN 'REGISTERED';
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'pg_cron 등록에 실패했다 job=% (%)', p_job, SQLERRM;
        RETURN 'FAILED';
    END;
END
$fn$;

-- 정의되어 있는데 pg_cron 에 없는 배치를 모두 등록한다. 앱이 뜰 때 부른다 — pg_cron 을 나중에 도입한 설치도 이 한 번으로 따라온다.
-- 돌려주는 값: 새로 등록한 개수
CREATE OR REPLACE FUNCTION batch_register_missing() RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_job text;
    v_count int := 0;
BEGIN
    FOR v_job IN SELECT job_key FROM batch_db_jobs ORDER BY job_key LOOP
        IF batch_register(v_job) = 'REGISTERED' THEN
            v_count := v_count + 1;
        END IF;
    END LOOP;
    RETURN v_count;
END
$fn$;

-- 배치 하나를 정의한다(R__ 파일이 부른다). 정의 표는 매번 갱신하고(코드와 함께 바뀌는 값), pg_cron 에는 없을 때만 올린다.
-- p_hour/p_minute 는 한국시간의 매일 실행 시각이다.
CREATE OR REPLACE FUNCTION batch_define(p_job text, p_procedure text, p_hour int, p_minute int) RETURNS text
LANGUAGE plpgsql AS $fn$
BEGIN
    PERFORM batch_enable_pg_cron();
    INSERT INTO batch_db_jobs (job_key, procedure_name, kst_hour, kst_minute, default_cron, updated_at)
    VALUES (p_job, p_procedure, p_hour, p_minute, batch_daily_cron(p_hour, p_minute), now())
    ON CONFLICT (job_key) DO UPDATE
        SET procedure_name = EXCLUDED.procedure_name, kst_hour = EXCLUDED.kst_hour, kst_minute = EXCLUDED.kst_minute,
            default_cron = EXCLUDED.default_cron, updated_at = EXCLUDED.updated_at;
    RETURN batch_register(p_job);
END
$fn$;

-- ── 실행 로그 ────────────────────────────────────────────────────────────────
-- 요약 문구의 인자(JSON 배열, 문자열만) — Java 쪽 BatchMessageCodec 과 같은 모양이다.
CREATE OR REPLACE FUNCTION batch_args(VARIADIC p_args text[]) RETURNS text
LANGUAGE sql IMMUTABLE AS $fn$
    SELECT CASE WHEN cardinality(p_args) = 0 THEN NULL ELSE to_json(p_args)::text END
$fn$;

-- 실패 사유를 로그에 남길 한 줄로 만든다 — SQLSTATE + 메시지 첫 줄, 이메일·비밀번호/토큰 값·「(칸)=(값)」의 값을 가려 500자까지.
-- 메시지의 DETAIL(행의 값이 실리는 곳)은 받지 않는다. 전체 내용은 pg_cron 의 cron.job_run_details / 서버 로그에 있다.
CREATE OR REPLACE FUNCTION batch_error_text(p_sqlstate text, p_message text) RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $fn$
DECLARE
    v_text text := 'SQLSTATE ' || coalesce(p_sqlstate, '?') || ': ' || btrim(split_part(coalesce(p_message, ''), E'\n', 1));
BEGIN
    v_text := regexp_replace(v_text, '[A-Za-z0-9_.+-]+@[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+', '(hidden)', 'g');
    v_text := regexp_replace(v_text, '(password|passwd|secret|token|apikey|api_key|authorization)\s*[=:]\s*\S+', '\1=(hidden)', 'gi');
    v_text := regexp_replace(v_text, '\(([^)]*)\)=\(([^)]*)\)', '(\1)=(hidden)', 'g');
    IF char_length(v_text) > 500 THEN
        v_text := left(v_text, 499) || '…';
    END IF;
    RETURN v_text;
END
$fn$;

-- 시작을 기록한다. 돌려주는 값 = 로그 줄 ID, 이미 같은 배치가 돌고 있으면 NULL(아무것도 기록하지 않는다 → 호출한 쪽이 그냥 돌아간다).
--  · 동시 실행 방지: Java 배치와 같은 어드바이저리 잠금(pg_try_advisory_lock(7340002, hashtext(키)), 세션 잠금)을 쥔다 — 끝낼 때(batch_run_close) 푼다.
--    세션 잠금이라 프로시저가 중간에 COMMIT 해도 유지된다. pg_cron 은 일이 끝나면 세션이 닫혀 DB 가 풀어 주고, 앱의 「지금 실행」은 부른 뒤 같은 연결에서 푼다.
--  · 잠금을 쥐었다는 것은 지금 그 배치를 돌리는 곳이 없다는 뜻이라, 남아 있는 RUNNING 줄은 DB 가 죽어 끝맺지 못한 것이다 — 실패로 닫는다(Java 와 같은 규칙).
--  · 이중 방어: batch_runs 의 유일 색인(RUNNING 하나, 예약 시각마다 한 줄)에 걸리면 같은 일이라 NULL 로 돌아간다.
CREATE OR REPLACE FUNCTION batch_run_begin(p_job text, p_trigger text, p_operator bigint) RETURNS bigint
LANGUAGE plpgsql AS $fn$
DECLARE
    v_id      bigint;
    v_started timestamptz := clock_timestamp();
BEGIN
    IF NOT pg_try_advisory_lock(7340002, hashtext(p_job)) THEN
        RAISE NOTICE '% — 이미 실행 중이라 건너뛴다', p_job;
        RETURN NULL;
    END IF;
    BEGIN
        UPDATE batch_runs SET status = 'FAIL', finished_at = v_started, message_key = 'batch.error.interrupted'
         WHERE job_key = p_job AND status = 'RUNNING';
        v_id := nextval('batch_run_db_id_seq');
        INSERT INTO batch_runs (id, job_key, status, trigger_type, scheduled_at, started_at, operator_id, source)
        VALUES (v_id, p_job, 'RUNNING', p_trigger, CASE WHEN p_trigger = 'SCHEDULED' THEN date_trunc('second', v_started) END,
                v_started, p_operator, 'POSTGRESQL');
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE '% — 같은 예약 시각의 실행이 이미 있어 건너뛴다', p_job;
        PERFORM pg_advisory_unlock(7340002, hashtext(p_job));
        RETURN NULL;
    END;
    RETURN v_id;
END
$fn$;

-- 끝을 기록하고 잠금을 푼다. 결과는 처리 건수와 오류로 정해진다:
--   p_error 가 있으면 FAIL(사유 = p_error, 그때까지 처리한 건수는 남긴다) · 건수 0 이면 SKIP(none 문구) · 그 밖은 OK.
-- 문구는 키 + 인자(batch_args)로 넘긴다 — 서버가 요청 언어로 푼다(Java 배치와 같다).
CREATE OR REPLACE FUNCTION batch_run_close(p_run bigint, p_job text, p_affected bigint, p_error text,
                                           p_ok_key text, p_ok_args text, p_none_key text, p_none_args text) RETURNS text
LANGUAGE plpgsql AS $fn$
DECLARE
    v_status text;
BEGIN
    IF p_error IS NOT NULL THEN
        v_status := 'FAIL';
    ELSIF coalesce(p_affected, 0) = 0 THEN
        v_status := 'SKIP';
    ELSE
        v_status := 'OK';
    END IF;
    UPDATE batch_runs
       SET status = v_status,
           finished_at = clock_timestamp(),
           affected_count = p_affected,
           message_key  = CASE v_status WHEN 'OK' THEN p_ok_key WHEN 'SKIP' THEN p_none_key END,
           message_args = CASE v_status WHEN 'OK' THEN p_ok_args WHEN 'SKIP' THEN p_none_args END,
           error_message = CASE v_status WHEN 'FAIL' THEN left(p_error, 500) END
     WHERE id = p_run AND status = 'RUNNING';
    PERFORM pg_advisory_unlock(7340002, hashtext(p_job));
    RETURN v_status;
END
$fn$;
