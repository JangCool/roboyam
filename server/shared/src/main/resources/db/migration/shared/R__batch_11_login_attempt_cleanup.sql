-- DB 배치 `login-attempt-cleanup` — 로그인 실패 기록(login_attempts) 정리. 공개 사이트와 관리자 앱(키 sha256("admin:"+이메일))의 행이 같은 표에 있다.
-- 규칙: 마지막 갱신(updated_at)이 retention_days 일 지났고 잠금이 이미 풀린 행을 지운다. 잠금(10분)은 곧 풀리고 연속 실패 횟수는 며칠이면 의미가 없다 —
--       오래 쉰 계정의 누적 실패는 0 으로 돌아간다(정책 가정: 로그인 시도 제한은 「연속」 실패를 세지 기간을 세지 않아 지금은 영영 남는다).
-- 기본 실행: 매일 03:10 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 5칸 식(분 시 일 월 요일).
-- 설정값(batch_params): retention_days(기본 30) · chunk_size(기본 5000) · max_per_run(기본 100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_login_attempt_cleanup();  (「지금 실행」은 CALL batch_login_attempt_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_login_attempt_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'login-attempt-cleanup';
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
            DELETE FROM login_attempts
             WHERE email_key IN (SELECT email_key FROM login_attempts
                                  WHERE updated_at < v_cutoff AND (locked_until IS NULL OR locked_until < now())
                                  ORDER BY updated_at LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.loginAttemptCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.loginAttemptCleanup.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('login-attempt-cleanup', 'retention_days', 30);
SELECT batch_param_seed('login-attempt-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('login-attempt-cleanup', 'max_per_run', 100000);
SELECT batch_define('login-attempt-cleanup', 'batch_login_attempt_cleanup', 3, 10);
