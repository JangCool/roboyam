-- DB 배치 `board-activity-daily` — 게시판 활동 일별 집계(board_activity_daily · site_activity_daily, V133). docs/features/site-analytics-contract.md 「주기 작업」.
-- 번호: shared R__batch_37 다음.
-- 규칙: 10분마다 오늘·어제(한국 날짜) 두 날만 원천에서 다시 세어 그 날의 줄을 바꾼다(지우고 다시 넣기 — 더하기가 아니라서 몇 번 돌아도 같은 값). 그 전 날은 건드리지 않는다
--       (자정 직후의 실행이 어제를 마지막으로 바로잡고, 그 뒤 지워진 글은 지난 날 숫자에 반영되지 않는다).
--   「글」 = 공개 게시판(boards.kind <> 'GROUP' — 소모임 게시판 제외)의 게시 글(status = 'PUBLISHED' — 임시저장 DRAFT 는 세지 않는다) 중 지금 지워지지 않은 글(deleted_at IS NULL).
--           숨김 글은 센다(쓰인 활동이다). 날짜는 쓴 시각 = 글 ID(Snowflake)의 시각 — 임시저장했다 나중에 게시하는 흐름은 지금 서버에 없다(게시 시각 칸이 생기면 그 칸 기준으로 바꾼다).
--   「댓글」 = 같은 게시판들의 지워지지 않은 댓글, 쓴 시각 = 댓글 ID.
--   「가입」 = users.created_at 이 그 날인 회원 수(관리자 대시보드 「회원 현황」 오늘 가입과 같은 기준 — 그 뒤 탈퇴해도 센다).
-- 인덱스: 글은 PK(id) 범위 — posts 는 id 월 범위 파티션이라 그 달 조각만 본다(board_snowflake_floor, R__batch_17). 댓글은 ix_comments_id(id)(V46) 범위.
--         가입은 ix_users_created(created_at DESC, id DESC)(V9) 범위. 하루치 행만 읽는다.
-- 설정 「게시판 활동 집계」(stats_collection_settings.board_activity)가 꺼져 있으면 세지 않고 SKIP(문구 off).
-- 기본 실행: 10분마다(*/10). 결과: 처리 건수 = 쓴 줄 수(게시판 줄 + 날 줄), 0 이면 SKIP. 날마다 COMMIT. 실패하면 그때까지의 건수와 사유를 남기고 FAIL.
-- 호출: CALL batch_board_activity_daily();  (「지금 실행」은 CALL batch_board_activity_daily('MANUAL', 운영자ID, NULL))

-- 한 날(한국 날짜)을 다시 센다 — 돌려주는 값 = 쓴 줄 수
CREATE OR REPLACE FUNCTION board_activity_recount(p_day date) RETURNS bigint
LANGUAGE plpgsql AS $fn$
DECLARE
    v_from timestamptz := p_day::timestamp AT TIME ZONE 'Asia/Seoul';
    v_to   timestamptz := (p_day + 1)::timestamp AT TIME ZONE 'Asia/Seoul';
    v_lo   bigint := board_snowflake_floor(v_from);
    v_hi   bigint := board_snowflake_floor(v_to);
    v_n    bigint;
BEGIN
    DELETE FROM board_activity_daily WHERE day = p_day;
    INSERT INTO board_activity_daily (day, board_id, posts, comments)
    SELECT p_day, x.board_id, sum(x.posts), sum(x.comments)
      FROM (SELECT p.board_id, count(*) AS posts, 0::bigint AS comments
              FROM posts p
             WHERE p.id >= v_lo AND p.id < v_hi AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL
             GROUP BY p.board_id
            UNION ALL
            SELECT c.board_id, 0::bigint, count(*)
              FROM comments c
             WHERE c.id >= v_lo AND c.id < v_hi AND c.deleted_at IS NULL
             GROUP BY c.board_id) x
      JOIN boards b ON b.id = x.board_id AND b.kind <> 'GROUP'
     GROUP BY x.board_id;
    GET DIAGNOSTICS v_n = ROW_COUNT;

    INSERT INTO site_activity_daily (day, posts, comments, signups)
    SELECT p_day, coalesce((SELECT sum(posts) FROM board_activity_daily WHERE day = p_day), 0),
           coalesce((SELECT sum(comments) FROM board_activity_daily WHERE day = p_day), 0),
           (SELECT count(*) FROM users WHERE created_at >= v_from AND created_at < v_to)
    ON CONFLICT (day) DO UPDATE SET posts = EXCLUDED.posts, comments = EXCLUDED.comments, signups = EXCLUDED.signups;
    RETURN v_n + 1;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_board_activity_daily(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job   CONSTANT text := 'board-activity-daily';
    v_on    boolean;
    v_today date;
    v_day   date;
    v_total bigint := 0;
    v_err   text;
    v_state text;
    v_msg   text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_on := coalesce((SELECT board_activity FROM stats_collection_settings WHERE id = 1), false);
    IF NOT v_on THEN
        PERFORM batch_run_close(p_run_id, c_job, 0, NULL, 'batch.result.boardActivityDaily', NULL, 'batch.result.boardActivityDaily.off', NULL);
        COMMIT;
        RETURN;
    END IF;

    v_today := (now() AT TIME ZONE 'Asia/Seoul')::date;
    FOR i IN REVERSE 1..0 LOOP -- 어제 → 오늘
        v_day := v_today - i;
        BEGIN
            v_total := v_total + board_activity_recount(v_day);
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
        EXIT WHEN v_err IS NOT NULL;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        'batch.result.boardActivityDaily', batch_args(v_total::text),
        'batch.result.boardActivityDaily.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_define_every_minutes('board-activity-daily', 'batch_board_activity_daily', 10);
