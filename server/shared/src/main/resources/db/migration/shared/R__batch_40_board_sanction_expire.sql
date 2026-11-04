-- DB 배치 `board-sanction-expire` — 끝난 게시판 쓰기 제한의 상태를 정리하고 지난 하루 사용량을 지운다(docs/features/board-sanction-contract.md 「규칙 > 상태」).
-- 규칙 1(만료): 진행 중(ACTIVE)이고 끝(ends_at)이 지난 쓰기 제한 → ENDED(ended_at = ends_at). 상태 정리일 뿐이다 — 쓰기 막힘·권장 단계·목록은 읽을 때 ends_at > 지금 으로
--   판정하므로 크론이 늦어도 회원에게는 정확하다. 회원 상태(users.status)는 건드리지 않는다(게시판 제재는 계정을 막지 않는다).
-- 규칙 2(사용량 정리): 매니저·스텝 하루 사용량(board_staff_usage) 중 한국 날짜로 retention_days(기본 7)일이 지난 날을 지운다(한도는 오늘 줄만 읽는다).
-- 동시성: 고른 뒤 다른 곳(해제·무효·부여 전 지연 만료)이 바꾼 행은 조건부 UPDATE 가 건너뛴다. 회원 행은 잠그지 않는다(회원 상태를 바꾸지 않는다).
-- ⚠ Java 에도 같은 판정이 있다(이중 구현) — 부여·해제 전 (회원, 게시판) 지연 만료 shared BoardSanctionStore#endDue. 한쪽을 고치면 다른 쪽도 고친다.
-- 기본 실행: 1분마다(batch_define_every_minutes — 운영자가 배치 화면에서 cron 을 바꿀 수 있다). 업무 배치(BatchCatalog.BOARD_SANCTION_EXPIRE — 분류 MEMBER):
--   pg_cron 이 없으면 정기 정리가 없다 → 관리자 서버 기동 WARN(판정은 읽을 때라 동작은 그대로다). Java 폴백 배치는 없다.
-- 설정값(batch_params): chunk_size(한 덩어리, 기본 1000) · max_per_run(한 번 실행 상한 — 두 규칙 합계, 기본 50000) · retention_days(사용량 보관, 기본 7).
-- 결과: 처리 건수 = 끝낸 쓰기 제한 수 + 지운 사용량 줄 수, 0 이면 SKIP.
-- 호출: CALL batch_board_sanction_expire();  (「지금 실행」은 CALL batch_board_sanction_expire('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_board_sanction_expire(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'board-sanction-expire';
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 1000, 10, 100000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 50000, 100, 10000000);
    v_days    bigint := batch_param(c_job, 'retention_days', 7, 2, 365);
    v_now     timestamptz;
    v_cutoff  date;
    v_n       bigint;
    v_ended   bigint := 0;
    v_purged  bigint := 0;
    v_err     text;
    v_state   text;
    v_msg     text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    -- 규칙 1 — 끝난 쓰기 제한(ix_board_sanctions_expire)
    LOOP
        BEGIN
            v_now := clock_timestamp();
            UPDATE board_sanctions SET status = 'ENDED', ended_at = ends_at, updated_at = v_now
             WHERE id IN (SELECT s.id FROM board_sanctions s
                           WHERE s.status = 'ACTIVE' AND s.ends_at IS NOT NULL AND s.ends_at <= v_now
                           ORDER BY s.ends_at, s.id LIMIT greatest(least(v_chunk, v_max - v_ended - v_purged), 0))
               AND status = 'ACTIVE' AND ends_at IS NOT NULL AND ends_at <= v_now;
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_ended := v_ended + v_n;
        COMMIT;
        EXIT WHEN v_n = 0 OR v_ended + v_purged >= v_max;
    END LOOP;

    -- 규칙 2 — 지난 하루 사용량(ix_board_staff_usage_date)
    IF v_err IS NULL THEN
        v_cutoff := (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date - v_days::int;
        LOOP
            EXIT WHEN v_ended + v_purged >= v_max;
            BEGIN
                DELETE FROM board_staff_usage
                 WHERE (board_id, user_id, usage_date) IN (SELECT u.board_id, u.user_id, u.usage_date FROM board_staff_usage u
                                                            WHERE u.usage_date < v_cutoff
                                                            ORDER BY u.usage_date LIMIT greatest(least(v_chunk, v_max - v_ended - v_purged), 0));
                GET DIAGNOSTICS v_n = ROW_COUNT;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                EXIT;
            END;
            v_purged := v_purged + v_n;
            COMMIT;
            EXIT WHEN v_n = 0;
        END LOOP;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_ended + v_purged, v_err,
        'batch.result.boardSanctionExpire', batch_args(v_ended::text, v_purged::text),
        'batch.result.boardSanctionExpire.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('board-sanction-expire', 'chunk_size', 1000);
SELECT batch_param_seed('board-sanction-expire', 'max_per_run', 50000);
SELECT batch_param_seed('board-sanction-expire', 'retention_days', 7);
SELECT batch_define_every_minutes('board-sanction-expire', 'batch_board_sanction_expire', 1);
