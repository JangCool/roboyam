-- DB 배치 `board-post-upkeep` — 공개 게시판 글 정리. docs/features/board-public-contract.md 「서버 구현 결과」
-- 규칙(두 가지를 차례로 한다):
--   ① 예약 삭제 확정: 댓글 많은 글 보호로 예약 삭제된 글 중 시각(delete_scheduled_at)이 지난 글의 deleted_at 을 그 시각으로 채운다.
--      앱은 시각이 지난 글을 이미 지운 글로 보고 거른다(PostVisibility) — 이 배치는 표시를 확정할 뿐이라 늦게 돌아도 화면은 같다.
--   ② 조회 기록 정리: 조회수 중복 방지 시간(board_security_settings.view_count_dedup_hours)보다 오래된 post_views 줄을 지운다(판정에 더는 쓰이지 않는다).
--   ③ 비회원 비밀번호 시도 기록 정리: 기간(10분)이 시작된 지 하루가 지난 guest_password_attempts 줄을 지운다(막힘은 이미 풀렸다).
-- 기본 실행: 매일 00:40 KST. 설정값(batch_params): chunk_size(기본 5000) · max_per_run(기본 100000, 두 일 합계).
-- 결과: 처리 건수 = 확정한 글 수 + 지운 조회 기록 수 + 지운 비밀번호 시도 기록 수, 0 이면 SKIP. 덩어리마다 COMMIT.
-- 호출: CALL batch_board_post_upkeep();  (「지금 실행」은 CALL batch_board_post_upkeep('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_board_post_upkeep(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job      CONSTANT text := 'board-post-upkeep';
    v_chunk    bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max      bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_hours    int;
    v_n        bigint;
    v_deleted  bigint := 0;
    v_views    bigint := 0;
    v_attempts bigint := 0;
    v_total    bigint := 0;
    v_err      text;
    v_state    text;
    v_msg      text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    -- ① 예약 삭제 확정
    LOOP
        BEGIN
            UPDATE posts SET deleted_at = delete_scheduled_at, updated_at = now()
             WHERE id IN (SELECT id FROM posts
                           WHERE delete_scheduled_at IS NOT NULL AND deleted_at IS NULL AND delete_scheduled_at <= now()
                           ORDER BY delete_scheduled_at LIMIT least(v_chunk, v_max - v_total));
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_deleted := v_deleted + v_n;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_n = 0 OR v_total >= v_max;
    END LOOP;

    -- ② 조회 기록 정리
    IF v_err IS NULL AND v_total < v_max THEN
        SELECT coalesce((SELECT view_count_dedup_hours FROM board_security_settings WHERE id = 1), 24) INTO v_hours;
        LOOP
            BEGIN
                DELETE FROM post_views
                 WHERE (post_id, viewer_key) IN (SELECT post_id, viewer_key FROM post_views
                                                  WHERE viewed_at < now() - make_interval(hours => v_hours)
                                                  LIMIT least(v_chunk, v_max - v_total));
                GET DIAGNOSTICS v_n = ROW_COUNT;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                EXIT;
            END;
            v_views := v_views + v_n;
            v_total := v_total + v_n;
            COMMIT;
            EXIT WHEN v_n = 0 OR v_total >= v_max;
        END LOOP;
    END IF;

    -- ③ 비회원 비밀번호 시도 기록 정리
    IF v_err IS NULL AND v_total < v_max THEN
        LOOP
            BEGIN
                DELETE FROM guest_password_attempts
                 WHERE attempt_key IN (SELECT attempt_key FROM guest_password_attempts
                                        WHERE window_started_at < now() - interval '1 day'
                                        ORDER BY window_started_at LIMIT least(v_chunk, v_max - v_total));
                GET DIAGNOSTICS v_n = ROW_COUNT;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                EXIT;
            END;
            v_attempts := v_attempts + v_n;
            v_total := v_total + v_n;
            COMMIT;
            EXIT WHEN v_n = 0 OR v_total >= v_max;
        END LOOP;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.boardPostUpkeep' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_deleted::text, v_views::text, v_attempts::text) END,
        'batch.result.boardPostUpkeep.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('board-post-upkeep', 'chunk_size', 5000);
SELECT batch_param_seed('board-post-upkeep', 'max_per_run', 100000);
SELECT batch_define('board-post-upkeep', 'batch_board_post_upkeep', 0, 40);
