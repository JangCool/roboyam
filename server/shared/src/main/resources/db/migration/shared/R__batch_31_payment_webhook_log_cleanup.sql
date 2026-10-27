-- DB 배치 `payment-webhook-log-cleanup` — 결제 웹훅 수신 기록(payment_webhook_logs) 보관 정리. docs/domain/commerce.md 8-1(90일)·15-5, commerce-payment-contract.md 「마이그레이션 계획」·결정 18
-- 번호: develop(81f286b) 의 반복 마이그레이션 shared R__batch_29 · admin R__batch_30 다음.
-- 규칙: 받은 지 retention_days 일(기본 90, 30~3650)이 지난 줄을 지운다. 본문은 원래 저장하지 않는다 — 남는 것은 언제·이벤트·결제 번호·결과·HTTP·사유 코드뿐이다.
--       결제 이력(payment_events — 거래 기록 5년)의 WEBHOOK 줄은 지우지 않는다(같은 webhook-id 를 두 번 처리하지 않는 장치).
-- 기본 실행: 매일 04:45 (한국시간 — batch_daily_cron 이 환산, 메일 대기열 정리 04:40 과 결제 대사 04:50 사이). 설정값(batch_params): retention_days(90) · chunk_size(5000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_payment_webhook_log_cleanup();  (「지금 실행」은 CALL batch_payment_webhook_log_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_payment_webhook_log_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'payment-webhook-log-cleanup';
    v_keep   bigint := batch_param(c_job, 'retention_days', 90, 30, 3650);
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

    v_cutoff := now() - make_interval(days => v_keep::int);
    LOOP
        BEGIN
            DELETE FROM payment_webhook_logs
             WHERE id IN (SELECT id FROM payment_webhook_logs
                         WHERE received_at < v_cutoff
                         ORDER BY received_at LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.paymentWebhookLogCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_keep::text) END,
        'batch.result.paymentWebhookLogCleanup.none', batch_args(v_keep::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('payment-webhook-log-cleanup', 'retention_days', 90);
SELECT batch_param_seed('payment-webhook-log-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('payment-webhook-log-cleanup', 'max_per_run', 100000);
SELECT batch_define('payment-webhook-log-cleanup', 'batch_payment_webhook_log_cleanup', 4, 45);
