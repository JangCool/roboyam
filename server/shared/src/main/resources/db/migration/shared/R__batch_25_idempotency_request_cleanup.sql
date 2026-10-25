-- DB 배치 `idempotency-request-cleanup` — 멱등 키(idempotency_requests) 보관 정리. docs/domain/commerce.md 3절·15-5, commerce-foundation-contract.md 「0-5」
-- 규칙: 만든 지(created_at) retention_hours 시간(기본 24 — 멱등 키는 24시간만 유효하다, Java IdempotencyService.TTL)이 지난 줄을 지운다(처리 중이든 끝났든).
--       앱은 24시간이 지난 줄을 이미 없는 것으로 보므로 이 배치가 늦게 돌아도 규칙은 그대로다.
-- 기본 실행: 매일 04:20 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_hours(기본 24, 24~168) · chunk_size(5000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_idempotency_request_cleanup();  (「지금 실행」은 CALL batch_idempotency_request_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_idempotency_request_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'idempotency-request-cleanup';
    v_keep   bigint := batch_param(c_job, 'retention_hours', 24, 24, 168);
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

    v_cutoff := now() - make_interval(hours => v_keep::int);
    LOOP
        BEGIN
            DELETE FROM idempotency_requests
             WHERE (user_id, idem_key) IN (SELECT user_id, idem_key FROM idempotency_requests
                         WHERE created_at < v_cutoff
                         ORDER BY created_at LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.idempotencyRequestCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_keep::text) END,
        'batch.result.idempotencyRequestCleanup.none', batch_args(v_keep::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('idempotency-request-cleanup', 'retention_hours', 24);
SELECT batch_param_seed('idempotency-request-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('idempotency-request-cleanup', 'max_per_run', 100000);
SELECT batch_define('idempotency-request-cleanup', 'batch_idempotency_request_cleanup', 4, 20);
