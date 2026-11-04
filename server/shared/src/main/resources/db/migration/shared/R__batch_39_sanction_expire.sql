-- DB 배치 `sanction-expire` — 끝난 기간 정지를 끝내고 회원을 정상으로 되돌린다(docs/features/member-sanction-contract.md 정책 7).
-- 규칙 1(만료): 진행 중(ACTIVE)이고 끝(ends_at)이 지난 기간 정지 → ENDED(ended_at = ends_at). 그 회원에게 다른 진행 중 정지·영구 정지가 없고 상태가 SUSPENDED 면 ACTIVE.
-- 규칙 2(어긋난 상태 바로잡기): 상태가 SUSPENDED·BANNED 인데 제재 기록이 있고 진행 중 정지·영구 정지가 하나도 없는 회원 → ACTIVE.
--   (탈퇴 진행 중(WITHDRAWING)에 정지가 끝나거나 풀린 뒤 탈퇴를 취소하면 예전 상태(SUSPENDED·BANNED)로 돌아가므로 — 제재 기록이 없는 회원은 건드리지 않는다.)
-- 동시성: 회원마다 회원 행을 먼저 잠그고(FOR UPDATE — 제재 부여·해제·인용과 같은 잠금, 차례: 회원 행 → 제재 행) 조건을 다시 본다. 고른 뒤 다른 곳이 바꾼 행은 조건부 UPDATE 가 건너뛴다.
-- ⚠ Java 에도 같은 판정이 있다(이중 구현) — 회원 쪽 읽기 전 마감 shared MemberSanctionStore#endDue·#repairStatus(MemberSanctionService#settle).
--   한쪽을 고치면 다른 쪽도 고친다.
-- 기본 실행: 1분마다(batch_define_every_minutes, 운영자가 배치 화면에서 cron 을 바꿀 수 있다). 업무 배치(BatchCatalog.SANCTION_EXPIRE — 분류 MEMBER):
--   pg_cron 이 없으면 정기 만료가 없다 → 관리자 서버 기동 WARN. 그래도 회원이 로그인 정보·내 제재를 읽을 때 지난 정지를 먼저 끝낸다(앱). Java 폴백 배치는 없다.
-- 설정값(batch_params): chunk_size(한 덩어리, 기본 500) · max_per_run(한 번 실행 상한, 기본 20000).
-- 결과: 처리 건수 = 끝낸 정지 수 + 바로잡은 회원 수, 0 이면 SKIP.
-- 호출: CALL batch_sanction_expire();  (「지금 실행」은 CALL batch_sanction_expire('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_sanction_expire(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'sanction-expire';
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 500, 10, 10000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 20000, 100, 1000000);
    v_now     timestamptz;
    r         record;
    v_n       bigint;
    v_round   bigint;
    v_ended   bigint := 0;
    v_fixed   bigint := 0;
    v_err     text;
    v_state   text;
    v_msg     text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    -- 규칙 1 — 끝난 기간 정지(ix_member_sanctions_expire)
    LOOP
        BEGIN
            v_round := 0;
            v_now := clock_timestamp();
            FOR r IN SELECT s.id, s.user_id FROM member_sanctions s
                      WHERE s.status = 'ACTIVE' AND s.ends_at IS NOT NULL AND s.ends_at <= v_now
                      ORDER BY s.ends_at, s.id LIMIT greatest(least(v_chunk, v_max - v_ended - v_fixed), 0) LOOP
                PERFORM 1 FROM users u WHERE u.id = r.user_id FOR UPDATE;
                UPDATE member_sanctions SET status = 'ENDED', ended_at = ends_at, updated_at = v_now
                 WHERE id = r.id AND status = 'ACTIVE' AND ends_at IS NOT NULL AND ends_at <= v_now;
                GET DIAGNOSTICS v_n = ROW_COUNT;
                IF v_n > 0 THEN
                    v_round := v_round + 1;
                    UPDATE users SET status = 'ACTIVE', updated_at = v_now
                     WHERE id = r.user_id AND status = 'SUSPENDED'
                       AND NOT EXISTS (SELECT 1 FROM member_sanctions x WHERE x.user_id = r.user_id AND x.status = 'ACTIVE' AND x.kind <> 'WARNING');
                END IF;
            END LOOP;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_ended := v_ended + v_round;
        COMMIT;
        EXIT WHEN v_round = 0 OR v_ended + v_fixed >= v_max;
    END LOOP;

    -- 규칙 2 — 어긋난 상태(ix_users_suspended 범위, 회원 수가 적다)
    IF v_err IS NULL AND v_ended + v_fixed < v_max THEN
        BEGIN
            v_now := clock_timestamp();
            FOR r IN SELECT u.id FROM users u
                      WHERE u.status IN ('SUSPENDED', 'BANNED')
                        AND EXISTS (SELECT 1 FROM member_sanctions s WHERE s.user_id = u.id)
                        AND NOT EXISTS (SELECT 1 FROM member_sanctions s WHERE s.user_id = u.id AND s.status = 'ACTIVE' AND s.kind <> 'WARNING')
                      ORDER BY u.id LIMIT greatest(least(v_chunk, v_max - v_ended - v_fixed), 0) LOOP
                PERFORM 1 FROM users u WHERE u.id = r.id FOR UPDATE;
                UPDATE users SET status = 'ACTIVE', updated_at = v_now
                 WHERE id = r.id AND status IN ('SUSPENDED', 'BANNED')
                   AND NOT EXISTS (SELECT 1 FROM member_sanctions x WHERE x.user_id = r.id AND x.status = 'ACTIVE' AND x.kind <> 'WARNING');
                GET DIAGNOSTICS v_n = ROW_COUNT;
                v_fixed := v_fixed + v_n;
            END LOOP;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            v_fixed := 0; -- 블록이 되돌려졌다
        END;
        COMMIT;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_ended + v_fixed, v_err,
        'batch.result.sanctionExpire', batch_args(v_ended::text, v_fixed::text),
        'batch.result.sanctionExpire.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('sanction-expire', 'chunk_size', 500);
SELECT batch_param_seed('sanction-expire', 'max_per_run', 20000);
SELECT batch_define_every_minutes('sanction-expire', 'batch_sanction_expire', 1);
