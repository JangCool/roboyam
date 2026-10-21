-- DB 배치 `audit-log-retention` — 감사 로그(audit_logs) 보관 기간 정리
-- 규칙: 발생(occurred_at)한 지 retention_days 일(기본 365일 — docs/domain/operator-permissions.md 의 「감사 보관 1년」 가정)이 지난 감사 기록을 지운다.
--       보관 기간을 바꾸려면 batch_params 의 값을 고친다(예: UPDATE batch_params SET param_value = 730 WHERE job_key = 'audit-log-retention' AND param_key = 'retention_days').
-- 기본 실행: 매일 03:30 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 5칸 식(분 시 일 월 요일).
-- 설정값(batch_params): retention_days(기본 365) · chunk_size(기본 5000) · max_per_run(기본 100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_audit_log_retention();  (「지금 실행」은 CALL batch_audit_log_retention('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_audit_log_retention(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'audit-log-retention';
    v_days   bigint := batch_param(c_job, 'retention_days', 365, 1, 3650);
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
    LOOP
        BEGIN
            DELETE FROM audit_logs
             WHERE id IN (SELECT id FROM audit_logs
                           WHERE occurred_at < v_cutoff
                           ORDER BY occurred_at LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.auditLogRetention' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.auditLogRetention.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('audit-log-retention', 'retention_days', 365);
SELECT batch_param_seed('audit-log-retention', 'chunk_size', 5000);
SELECT batch_param_seed('audit-log-retention', 'max_per_run', 100000);
SELECT batch_define('audit-log-retention', 'batch_audit_log_retention', 3, 30);
