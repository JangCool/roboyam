-- DB 배치 `batch-log-cleanup` — 배치 실행 로그(batch_runs)와 끝난 실행 요청(batch_run_requests) 정리. Java 배치·DB 배치의 로그가 같은 표에 있으므로 둘 다 지운다.
-- (예전에는 Java 배치였다. 정리도 DB 프로시저가 하도록 옮겼다 — 같은 키를 이어 쓰므로 이미 쌓인 로그와 화면의 이름이 그대로다.)
-- 규칙: 끝난 지(finished_at) retention_days 일(기본 90일)이 지난 로그를 지운다. 실행 중(RUNNING) 줄과 이 배치 자신의 줄은 지우지 않는다.
--       실행 줄의 단계(batch_run_steps)·건별 실패(batch_run_failures)는 외래키 ON DELETE CASCADE 로 같이 지워진다(V57).
--       끝난(DONE·REJECTED) 요청도 끝난 지 retention_days 일이 지나면 지운다 — 대기·실행 중(PENDING·RUNNING) 요청은 지우지 않는다.
-- 기본 실행: 매일 03:40 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 5칸 식(분 시 일 월 요일).
-- 설정값(batch_params): retention_days(기본 90) · chunk_size(기본 5000) · max_per_run(기본 100000 — 로그·요청을 합한 한 번 실행 상한).
-- 결과: 처리 건수 = 지운 로그 줄 + 요청 줄 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_batch_log_cleanup();  (「지금 실행」은 CALL batch_batch_log_cleanup('MANUAL', 운영자ID, NULL))
-- 이름 규칙: 프로시저 이름 = 'batch_' + 배치 키의 하이픈을 밑줄로 바꾼 것(그래서 batch_ 가 두 번 나온다).

CREATE OR REPLACE PROCEDURE batch_batch_log_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'batch-log-cleanup';
    v_days   bigint := batch_param(c_job, 'retention_days', 90, 1, 3650);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_cutoff timestamptz;
    v_n      bigint;
    v_total  bigint := 0;
    v_err    text;
    v_state  text;
    v_msg    text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_cutoff := now() - make_interval(days => v_days::int);

    -- ① 끝난 실행 요청(대기·실행 중 요청은 남긴다)
    LOOP
        EXIT WHEN v_total >= v_max;
        BEGIN
            DELETE FROM batch_run_requests
             WHERE id IN (SELECT id FROM batch_run_requests
                           WHERE status IN ('DONE', 'REJECTED') AND finished_at < v_cutoff
                           ORDER BY finished_at LIMIT least(v_chunk, v_max - v_total));
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_n = 0;
    END LOOP;

    -- ② 실행 로그(단계·건별 실패는 CASCADE 로 같이)
    IF v_err IS NULL THEN
        LOOP
            EXIT WHEN v_total >= v_max;
            BEGIN
                DELETE FROM batch_runs
                 WHERE id IN (SELECT id FROM batch_runs
                               WHERE status <> 'RUNNING' AND finished_at < v_cutoff AND id <> p_run_id
                               ORDER BY finished_at LIMIT least(v_chunk, v_max - v_total));
                GET DIAGNOSTICS v_n = ROW_COUNT;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                EXIT;
            END;
            v_total := v_total + v_n;
            COMMIT;
            EXIT WHEN v_n = 0;
        END LOOP;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.logCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.logCleanup.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('batch-log-cleanup', 'retention_days', 90);
SELECT batch_param_seed('batch-log-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('batch-log-cleanup', 'max_per_run', 100000);
SELECT batch_define('batch-log-cleanup', 'batch_batch_log_cleanup', 3, 40);
