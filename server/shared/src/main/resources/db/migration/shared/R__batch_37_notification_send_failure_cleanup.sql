-- DB 배치 `notification-send-failure-cleanup` — 알림 발송 실패 기록(notification_send_failures, V133) 정리. docs/features/site-analytics-contract.md 「주기 작업」.
-- 번호: shared R__batch_36 다음.
-- 규칙: 실패 시각이 retention_days(기본 30, 7~365)일 지난 줄을 지운다. 일별 합(notification_send_daily)은 지우지 않는다.
--       설정 「알림 발송 기록」(stats_collection_settings.notification_sends)이 꺼져 있어도 돈다 — 끈 뒤에도 쌓여 있던 줄은 보관 기간대로 지워야 하고, 빈 표면 바로 SKIP 이다.
-- 기본 실행: 매일 04:20 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_days(30) · chunk_size(5000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_notification_send_failure_cleanup();  (「지금 실행」은 CALL batch_notification_send_failure_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_notification_send_failure_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL,
                                                                    INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'notification-send-failure-cleanup';
    v_days   bigint := batch_param(c_job, 'retention_days', 30, 7, 365);
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
            -- ix_notification_send_failures_at(failed_at DESC) 범위로 고른 뒤 PK 로 지운다
            DELETE FROM notification_send_failures
             WHERE id IN (SELECT id FROM notification_send_failures
                           WHERE failed_at < v_cutoff
                           LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.notificationSendFailureCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.notificationSendFailureCleanup.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('notification-send-failure-cleanup', 'retention_days', 30);
SELECT batch_param_seed('notification-send-failure-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('notification-send-failure-cleanup', 'max_per_run', 100000);
SELECT batch_define('notification-send-failure-cleanup', 'batch_notification_send_failure_cleanup', 4, 20);
