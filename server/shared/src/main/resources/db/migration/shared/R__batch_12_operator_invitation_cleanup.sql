-- DB 배치 `operator-invitation-cleanup` — 운영자 초대(operator_invitations) 정리. 초대 역할(operator_invitation_roles)은 외래키 연쇄 삭제로 같이 지워진다.
-- 규칙: 수락하지 않고 만료된 지 retention_days 일이 지난 초대, 또는 수락한 지 그만큼 지난 초대를 지운다.
--       만료 직후에는 「초대 대기」 목록에 만료로 보이므로 바로 지우지 않는다. 초대 취소는 앱이 이미 행을 바로 지운다.
-- 기본 실행: 매일 03:20 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 5칸 식(분 시 일 월 요일).
-- 설정값(batch_params): retention_days(기본 30) · chunk_size(기본 5000) · max_per_run(기본 100000).
-- 결과: 처리 건수 = 지운 초대 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_operator_invitation_cleanup();  (「지금 실행」은 CALL batch_operator_invitation_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_operator_invitation_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'operator-invitation-cleanup';
    v_days   bigint := batch_param(c_job, 'retention_days', 30, 1, 3650);
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
            DELETE FROM operator_invitations
             WHERE id IN (SELECT id FROM operator_invitations
                           WHERE (consumed_at IS NULL AND expires_at < v_cutoff) OR (consumed_at IS NOT NULL AND consumed_at < v_cutoff)
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.operatorInvitationCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.operatorInvitationCleanup.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('operator-invitation-cleanup', 'retention_days', 30);
SELECT batch_param_seed('operator-invitation-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('operator-invitation-cleanup', 'max_per_run', 100000);
SELECT batch_define('operator-invitation-cleanup', 'batch_operator_invitation_cleanup', 3, 20);
