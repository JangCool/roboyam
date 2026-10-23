-- DB 배치 `request-rate-counter-cleanup` — 요청 속도 카운터(request_rate_counters, UNLOGGED) 지난 창 정리. docs/domain/commerce.md 12-4·15-5, commerce-foundation-contract.md 「0-5」
-- 규칙: 1분 창 시작(window_start)이 retention_minutes 분(기본 5) 전보다 이른 줄을 지운다 — 앱은 지금 창(분 단위로 자른 지금)만 읽으므로 지난 창은 쓸모가 없다.
-- 기본 실행: **매시** 05분 (한국시간 — batch_define_hourly). 설정값(batch_params): retention_minutes(기본 5, 2~1440) · chunk_size(5000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_request_rate_counter_cleanup();  (「지금 실행」은 CALL batch_request_rate_counter_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_request_rate_counter_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'request-rate-counter-cleanup';
    v_keep   bigint := batch_param(c_job, 'retention_minutes', 5, 2, 1440);
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

    v_cutoff := date_trunc('minute', now()) - make_interval(mins => v_keep::int);
    LOOP
        BEGIN
            DELETE FROM request_rate_counters
             WHERE (user_id, action, window_start) IN (SELECT user_id, action, window_start FROM request_rate_counters
                         WHERE window_start < v_cutoff
                         ORDER BY window_start LIMIT least(v_chunk, v_max - v_total));
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_n = 0 OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.requestRateCounterCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_keep::text) END,
        'batch.result.requestRateCounterCleanup.none', batch_args(v_keep::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('request-rate-counter-cleanup', 'retention_minutes', 5);
SELECT batch_param_seed('request-rate-counter-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('request-rate-counter-cleanup', 'max_per_run', 100000);
SELECT batch_define_hourly('request-rate-counter-cleanup', 'batch_request_rate_counter_cleanup', 5);
