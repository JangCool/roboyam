-- DB 배치 `auth-token-cleanup` — 이메일 인증·비밀번호 재설정 토큰(auth_tokens) 정리
-- 규칙: 만료된 지 retention_days 일이 지났고, 사용(consumed_at)된 것이면 사용한 지도 그만큼 지난 토큰을 지운다.
--       재발송 쿨다운(60초)은 가장 최근 토큰의 발급 시각(created_at)만 읽으므로 며칠 지난 행은 필요 없다.
-- 기본 실행: 매일 03:00 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 5칸 식(분 시 일 월 요일).
-- 설정값(batch_params): retention_days(기본 7) · chunk_size(한 번에 지우는 덩어리, 기본 5000) · max_per_run(한 번 실행에서 지우는 상한, 기본 100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT 해서 긴 잠금과 큰 트랜잭션을 피한다. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_auth_token_cleanup();  (pg_cron 이 이 명령으로 부른다. 앱의 「지금 실행」은 CALL batch_auth_token_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_auth_token_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'auth-token-cleanup';
    v_days   bigint := batch_param(c_job, 'retention_days', 7, 1, 3650);
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
            DELETE FROM auth_tokens
             WHERE id IN (SELECT id FROM auth_tokens
                           WHERE expires_at < v_cutoff AND (consumed_at IS NULL OR consumed_at < v_cutoff)
                           ORDER BY expires_at LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.authTokenCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.authTokenCleanup.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('auth-token-cleanup', 'retention_days', 7);
SELECT batch_param_seed('auth-token-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('auth-token-cleanup', 'max_per_run', 100000);
SELECT batch_define('auth-token-cleanup', 'batch_auth_token_cleanup', 3, 0);
