-- DB 배치 `admin-mfa-cleanup` — 관리자 2차 인증 기록 정리 (admin 스트림 — 세션 정리 결정 5, 표는 admin V3)
-- 규칙: ① 기간(expires_at)이 끝난 신뢰 기기(operator_trusted_devices)를 지운다 — 로그인은 이미 기간으로 거르므로 쌓이지만 않게 한다.
--       ② 등록 중(확정 전) 비밀키가 pending_minutes(기본 10분) 넘게 남은 operator_mfa 는 등록 중 칸을 비운다 — 앱도 10분이 지난 등록 확정을 받지 않는다.
--          그 결과 등록된 비밀키도 등록 중 비밀키도 없는 행은 지운다(등록 시작 때만 생긴 빈 행).
-- 기본 실행: 매일 04:10 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron).
-- 설정값(batch_params): pending_minutes(기본 10) · chunk_size(기본 5000) · max_per_run(한 번 실행에서 처리하는 상한 — 두 일 합계, 기본 100000).
-- 결과: 처리 건수 = 지운 신뢰 기기 + 비운 등록 중 비밀키 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_admin_mfa_cleanup();  (앱의 「지금 실행」은 CALL batch_admin_mfa_cleanup('MANUAL', 운영자ID, NULL))
-- 공통 함수(batch_param·batch_run_begin …)는 shared 스트림의 R__batch_00_helpers.sql 에 있다 — 앱은 shared 를 먼저 이관한다.

CREATE OR REPLACE PROCEDURE batch_admin_mfa_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'admin-mfa-cleanup';
    v_minutes bigint := batch_param(c_job, 'pending_minutes', 10, 10, 1440);
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_n       bigint;
    v_round   bigint;
    v_total   bigint := 0;
    v_err     text;
    v_state   text;
    v_msg     text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    LOOP
        BEGIN
            v_round := 0;

            DELETE FROM operator_trusted_devices
             WHERE id IN (SELECT id FROM operator_trusted_devices
                           WHERE expires_at < now()
                           ORDER BY expires_at LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            UPDATE operator_mfa
               SET pending_secret_enc = NULL, pending_started_at = NULL, pending_reenroll = false, updated_at = now()
             WHERE operator_id IN (SELECT operator_id FROM operator_mfa
                                    WHERE pending_started_at < now() - make_interval(mins => v_minutes::int)
                                    ORDER BY pending_started_at LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            -- 등록된 비밀키도 등록 중 비밀키도 없는 빈 행 — 건수에 세지 않는다(위에서 비운 행을 정리하는 뒷일)
            DELETE FROM operator_mfa WHERE secret_enc IS NULL AND pending_secret_enc IS NULL;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_round;
        COMMIT;
        EXIT WHEN v_round = 0 OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.adminMfaCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text) END,
        'batch.result.adminMfaCleanup.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('admin-mfa-cleanup', 'pending_minutes', 10);
SELECT batch_param_seed('admin-mfa-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('admin-mfa-cleanup', 'max_per_run', 100000);
SELECT batch_define('admin-mfa-cleanup', 'batch_admin_mfa_cleanup', 4, 10);
