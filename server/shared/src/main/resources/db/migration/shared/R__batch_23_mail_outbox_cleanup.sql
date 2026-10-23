-- DB 배치 `mail-outbox-cleanup` — 커머스 메일 대기열(mail_outbox) 보관 정리. docs/domain/commerce.md 14절·15-5, commerce-foundation-contract.md 결정 10·「0-5」
-- 규칙: 끝난 줄(SENT 보냄 · DEAD 재시도 한도 · CANCELED 받을 수 없음 — finished_at 이 있다)을 끝난 지 retention_days 일(기본 365 — 0-5 에서 정함, 30~3650)이 지나면 지운다.
--       끝난 줄에는 이미 본문(variables)이 없다(표의 CHECK) — 남는 것은 언제·종류·가린 주소·마지막 오류뿐이다. 보낼 차례(PENDING)·보내는 중(SENDING) 줄은 지우지 않는다.
-- 기본 실행: 매일 04:40 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_days(기본 365) · chunk_size(5000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_mail_outbox_cleanup();  (「지금 실행」은 CALL batch_mail_outbox_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_mail_outbox_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'mail-outbox-cleanup';
    v_keep   bigint := batch_param(c_job, 'retention_days', 365, 30, 3650);
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
            DELETE FROM mail_outbox
             WHERE id IN (SELECT id FROM mail_outbox
                         WHERE finished_at < v_cutoff
                         ORDER BY finished_at LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.mailOutboxCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_keep::text) END,
        'batch.result.mailOutboxCleanup.none', batch_args(v_keep::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('mail-outbox-cleanup', 'retention_days', 365);
SELECT batch_param_seed('mail-outbox-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('mail-outbox-cleanup', 'max_per_run', 100000);
SELECT batch_define('mail-outbox-cleanup', 'batch_mail_outbox_cleanup', 4, 40);
