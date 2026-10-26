-- DB 배치 `member-privacy-retention` — 탈퇴 대비·본인인증 개인정보 정리(docs/features/commerce-foundation-contract.md 「0-4」, docs/domain/commerce.md 17-3)
-- 규칙(위에서 아래로):
--   1) 재가입·재인증 제한 해시(identity_withdrawals): 기간이 끝난 것(expires_at ≤ 지금). 기간은 탈퇴 때 박은 값이다(기본 30일 — roboyam.withdrawal.rejoin-block-days).
--   2) 탈퇴 회원 연락용 이메일(withdrawn_member_contacts): 파기 예정일이 지난 것(purge_after ≤ 지금 — 마지막 거래일 + 5년).
--   3) 본인인증 시도(identity_verification_attempts): 만든 지 attempt_retention_days 일(기본 30일)이 지난 것(상태 무관 — 확정된 결과는 identity_verifications 에 있다).
-- 기본 실행: 매일 04:25 (한국시간으로 적고 batch_daily_cron 이 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다).
-- 설정값(batch_params): attempt_retention_days(기본 30) · chunk_size(기본 5000) · max_per_run(한 번 실행에서 지우는 상한 — 세 표 합계, 기본 100000).
-- 결과: 처리 건수 = 세 표에서 지운 행 수의 합, 0 이면 SKIP. 덩어리마다 COMMIT. 상한에 닿으면 나머지는 다음 실행이 이어서 지운다. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_member_privacy_retention();  (「지금 실행」은 CALL batch_member_privacy_retention('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_member_privacy_retention(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'member-privacy-retention';
    v_days    bigint := batch_param(c_job, 'attempt_retention_days', 30, 1, 3650);
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_now     timestamptz;
    v_cutoff  timestamptz;
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

    v_now := now();
    v_cutoff := v_now - make_interval(days => v_days::int);
    LOOP
        BEGIN
            v_round := 0;

            DELETE FROM identity_withdrawals
             WHERE id IN (SELECT id FROM identity_withdrawals
                           WHERE expires_at <= v_now
                           ORDER BY expires_at LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            DELETE FROM withdrawn_member_contacts
             WHERE user_id IN (SELECT user_id FROM withdrawn_member_contacts
                                WHERE purge_after <= v_now
                                ORDER BY purge_after LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            DELETE FROM identity_verification_attempts
             WHERE id IN (SELECT id FROM identity_verification_attempts
                           WHERE created_at < v_cutoff
                           ORDER BY created_at LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.memberPrivacyRetention' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.memberPrivacyRetention.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('member-privacy-retention', 'attempt_retention_days', 30);
SELECT batch_param_seed('member-privacy-retention', 'chunk_size', 5000);
SELECT batch_param_seed('member-privacy-retention', 'max_per_run', 100000);
SELECT batch_define('member-privacy-retention', 'batch_member_privacy_retention', 4, 25);
