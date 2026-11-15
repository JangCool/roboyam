-- DB 배치 `post-tag-daily` — 글 태그 일별 집계(post_tag_daily, V216). docs/features/post-tags-contract.md 「집계」.
-- 번호: shared R__batch_43 다음.
-- 왜: 태그 「많이 달린 순」(관리자 추천 GET /api/admin/tags/popular · 매거진 GET /api/tips 태그 칩)을 요청마다 post_tags × posts 로 세지 않고(매거진은 방문자마다였다)
--     미리 세어 둔 표를 읽는다(사용자 결정 2026-11-15). 읽는 쪽은 shared PostTagQueries#popular.
--
-- 규칙: 한 날(한국 날짜 Asia/Seoul — 다른 DB 배치와 같은 시간대)을 원천에서 다시 세어 그 날의 줄을 바꾼다(한 트랜잭션에서 지우고 다시 넣기 — 몇 번 돌아도 같은 값).
--   10분마다(*/10) 한 배치가 다시 셀 날 = ① 어제·오늘 + ② 「지난 계산 뒤로 바뀐 글」이 쓰인 날 + ③ 바뀐 날 표시(post_tag_dirty_days — 앱 PostTagStore#replace 가 태그를
--   실제로 바꿀 때 그 글의 작성일·게시판을 넣는다)의 날. ②③ 은 최근 window_days 일(기본 31, 오늘 포함) 안에서만 — 그보다 앞선 날의 표시는 세지 않고 지운다.
--   새벽에 31일을 통째로 다시 세는 크론은 두지 않는다(부하 — 사용자 결정 2026-11-15 변경).
--   지난 계산 시각 = 이 배치의 마지막 성공 실행(batch_runs — status OK·SKIP)의 시작 시각에서 5분을 뺀 값(배치 이력 관례 — 따로 상태 표를 두지 않는다. 앞선 트랜잭션이 시각을
--     찍은 뒤 늦게 COMMIT 한 변경을 놓치지 않게 겹쳐 본다). 실패(FAIL)한 실행은 기준이 되지 않으므로 다음 실행이 같은 변경을 다시 찾는다. 성공 기록이 없으면
--     (처음 · 실행 기록 정리 뒤 오래 꺼져 있던 경우) 최근 window_days 일을 모두 다시 센다.
--   「바뀐 글」 = 지금 태그가 달린 최근 window_days 일의 글 중 아래 하나. 반드시 post_tags 에서 출발한다 — Snowflake id 바닥으로 post_tags(PK post_id 앞부분)를 좁히고,
--     그 post_id 로 posts 를 기본 키로 붙여 시각 칸을 지난 계산 시각과 비교한다(posts 는 분할 표이고 updated_at 색인이 없어 posts 를 시각 칸으로 훑지 않는다 — 색인도 만들지 않는다):
--     · posts.updated_at > 기준 — 지우기(delete·staffDelete·예약 삭제 확정 R__batch_19)·되살리기·신고/운영/판매자 숨김·숨김 풀기·숨김 확정·고치기(태그만 고쳐도 PostWriteService#update 가
--       PostEntity#edit 를 불러 updated_at 이 바뀐다 — 비밀글 바꾸기 changeSecret 은 updated_at 을 고치지 않지만 늘 edit 와 함께 불린다)·게시판 이동(moveTo)·
--       소모임 공개 범위(grantPublic·revokePublic·markMembersOnly·releaseMembersOnly·GroupPostQueries 일괄)·소모임 나가기 가리기(group_left_at 일괄)가 모두 updated_at 을 함께 쓴다.
--     · posts.hidden_until 이 (기준, 계산 시각] — 기간 숨김(임시조치)이 저절로 끝남(칸을 고치는 쓰기가 없다).
--     · posts.delete_scheduled_at 이 (기준, 계산 시각] — 예약 삭제 시각이 지남(R__batch_19 가 deleted_at 으로 옮기기 전에도 보이지 않는 글이다).
--     · 소모임 비활성·다시 열기(groups.deactivated_at·reactivated_at > 기준) — 글 표를 건드리지 않는 변경이라 그 소모임의 글을 본다.
--     · post_tags.created_at > 기준 — 새로 단 태그(글 updated_at 과 겹치지만 이중으로 본다). 태그 일부를 빼고 남은 태그가 있으면 글의 updated_at 으로 잡힌다.
--     태그를 모두 뺀 글은 post_tags 행이 없어 여기서 찾을 수 없다 — ③ 바뀐 날 표시가 그 날을 잡는다.
--   바뀐 날 표시는 그 날을 다시 세는 트랜잭션에서 <세기 전에> 지운다 — 지우는 문장이 본 표시(먼저 COMMIT 된 태그 변경)는 뒤따르는 세기 문장이 반드시 본다.
--     센 다음에 지우면 세기와 지우기 사이에 COMMIT 된 변경의 표시가 세지 않은 채 지워질 수 있다. 지우기 뒤에 COMMIT 된 표시는 남아 다음 실행이 다시 센다.
--   게시판 지우기(BoardContentQueries#deleteContent)는 글 행을 아예 지워 위 방법으로 찾을 수 없다 — 대신 없는 게시판의 집계 줄을 실행마다 지운다(그 게시판의 글은 이제 없다).
--   두 실행(정기·지금 실행)이 같은 날을 함께 세면 날마다 어드바이저리 트랜잭션 잠금으로 차례를 지킨다(지우고 넣기가 엇갈려 PK 충돌이 나지 않게).
-- 셀 글(= 예전 요청마다 세던 PostTagQueries#popular 의 조건 — Java 판정을 SQL 로 옮긴 것, 한쪽을 고치면 같이 고친다):
--   PostRepositoryQDSL#alive   — 지우지 않음(deleted_at IS NULL) · 게시(status = 'PUBLISHED') · 예약 삭제 시각이 없거나 아직 안 옴(delete_scheduled_at > 계산 시각)
--   PostRepositoryQDSL#notHidden — 숨김이 아니거나(hidden_at IS NULL) 기간 숨김이 끝남(hidden_until ≤ 계산 시각)
--   PostRepositoryQDSL#feedVisible — 일반 글이거나 비멤버에게 보이는 소모임 글(is_public · 작성자가 나가 가리지 않음 · 소모임이 INACTIVE 아님) · 비밀글 아님
--   시각에 따라 바뀌는 판정(예약 삭제·기간 숨김)은 계산 시각(실행 시작 때 한 번 잡는 clock_timestamp) 기준 — 그래서 집계는 최대 10분 늦다.
--   꺼진 게시판도 센다(예전 질의와 같다 — 범위는 읽는 쪽이 게시판 ID 로 고른다).
-- 날짜: 글 작성 시각(posts.created_at)의 한국 날짜. 범위 좁히기는 Snowflake id 바닥(board_snowflake_floor — R__batch_17, Java SnowflakeIdGenerator#floorIdAt 과 같은 식)으로
--   post_tags(PK post_id 앞부분)·posts(id 월 범위 파티션) 의 아래쪽만 자르고(1분 넓혀) created_at 으로 정확히 자른다 — 위쪽은 자르지 않는다(id 가 작성 시각보다 뒤인 글 — 데모 글 등).
--   바뀐 글 찾기는 최근 window_days 일의 post_tags 행(PK 범위)만 읽고 글은 기본 키로 하나씩 붙인다.
-- 모양(tag): 그날 그 게시판에서 그 열쇠로 가장 많이 쓴 모양, 같으면 글자 차례(COLLATE "C" — Java String#compareTo 와 같은 코드 차례).
-- 설정값(batch_params): window_days(31, 2~365) — 바뀐 글을 찾는 범위(이보다 앞선 날은 마지막으로 센 값 그대로).
-- 결과: 처리 건수 = 쓴 줄 수 + 지운(없는 게시판) 줄 수, 0 이면 SKIP. 날마다 COMMIT. 실패하면 그때까지의 건수와 사유를 남기고 FAIL(그 날은 예전 줄이 남는다).
-- 분류: 정리(SYSTEM) — 멈춰도 추천·칩이 마지막 값으로 남을 뿐이다. pg_cron 이 없으면 정기 계산이 없다(배치 목록 「사용 불가」) — 이 파일이 적용될 때 최근 31일을 한 번 센다(아래 끝).
-- 호출: CALL batch_post_tag_daily();  (「지금 실행」은 CALL batch_post_tag_daily('MANUAL', 운영자ID, NULL))

-- 한 날(한국 날짜)을 다시 센다 — 돌려주는 값 = 쓴 줄 수. p_now = 시각 판정(예약 삭제·기간 숨김)의 기준
CREATE OR REPLACE FUNCTION post_tag_daily_recount(p_day date, p_now timestamptz) RETURNS bigint
LANGUAGE plpgsql AS $fn$
DECLARE
    v_from timestamptz := p_day::timestamp AT TIME ZONE 'Asia/Seoul';
    v_to   timestamptz := (p_day + 1)::timestamp AT TIME ZONE 'Asia/Seoul';
    -- 아래쪽만 자른다 — 글 id 는 작성 시각보다 앞설 수 없지만 뒤일 수는 있다(데모 글처럼 지금 만들고 작성 시각만 옮긴 글). 위쪽을 자르면 그런 글이 빠진다.
    -- 태그 달린 글만 세므로 위쪽 범위 없이도 가볍다(최근 글 쪽 post_tags PK 범위)
    v_lo   bigint := board_snowflake_floor(v_from - interval '1 minute');
    v_n    bigint;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('post_tag_daily'), p_day - date '2000-01-01');
    DELETE FROM post_tag_dirty_days WHERE day = p_day; -- 바뀐 날 표시 — 세기 전에(머리말)
    DELETE FROM post_tag_daily WHERE day = p_day;
    WITH s AS (
        SELECT p.board_id, t.tag_key, t.tag, count(*) AS n
          FROM post_tags t
          JOIN posts p ON p.id = t.post_id
         WHERE t.post_id >= v_lo
           AND p.id >= v_lo
           AND p.created_at >= v_from AND p.created_at < v_to
           -- alive
           AND p.deleted_at IS NULL AND p.status = 'PUBLISHED'
           AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > p_now)
           -- notHidden
           AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= p_now))
           -- feedVisible
           AND (p.group_id IS NULL
                OR (p.is_public AND p.group_left_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM groups g WHERE g.id = p.group_id AND g.status = 'INACTIVE')))
           AND NOT p.secret
         GROUP BY p.board_id, t.tag_key, t.tag
    )
    INSERT INTO post_tag_daily (day, board_id, tag_key, tag, post_count)
    SELECT p_day, s.board_id, s.tag_key, (array_agg(s.tag ORDER BY s.n DESC, s.tag COLLATE "C"))[1], sum(s.n)::int
      FROM s
     GROUP BY s.board_id, s.tag_key;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    RETURN v_n;
END
$fn$;

-- 지난 계산(p_since) 뒤로 바뀐 태그 글이 쓰인 날(한국 날짜, p_oldest 부터) — 머리말 「바뀐 글」. post_tags 에서 출발해 posts 는 기본 키로 붙인다
CREATE OR REPLACE FUNCTION post_tag_daily_changed_days(p_since timestamptz, p_now timestamptz, p_oldest date) RETURNS SETOF date
LANGUAGE plpgsql STABLE AS $fn$
DECLARE
    v_from timestamptz := p_oldest::timestamp AT TIME ZONE 'Asia/Seoul';
    v_lo   bigint := board_snowflake_floor(v_from - interval '1 minute');
BEGIN
    RETURN QUERY
    SELECT DISTINCT (p.created_at AT TIME ZONE 'Asia/Seoul')::date
      FROM post_tags t
      JOIN posts p ON p.id = t.post_id
     WHERE t.post_id >= v_lo
       AND p.id >= v_lo
       AND p.created_at >= v_from
       AND (t.created_at > p_since
            OR p.updated_at > p_since
            OR (p.hidden_until > p_since AND p.hidden_until <= p_now)
            OR (p.delete_scheduled_at > p_since AND p.delete_scheduled_at <= p_now)
            OR (p.group_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM groups g
                             WHERE g.id = p.group_id AND (g.deactivated_at > p_since OR g.reactivated_at > p_since))));
END
$fn$;

-- 오늘(한국 날짜)부터 거슬러 p_days 일을 한 트랜잭션에서 다시 센다 — 돌려주는 값 = 쓴 줄 수. 이 파일 끝의 처음 채우기에만 쓴다(배치는 날마다 COMMIT 한다)
CREATE OR REPLACE FUNCTION post_tag_daily_recount_recent(p_days int) RETURNS bigint
LANGUAGE plpgsql AS $fn$
DECLARE
    v_now   timestamptz := now();
    v_today date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_total bigint := 0;
BEGIN
    FOR i IN 0..greatest(p_days, 1) - 1 LOOP
        v_total := v_total + post_tag_daily_recount(v_today - i, v_now);
    END LOOP;
    RETURN v_total;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_post_tag_daily(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'post-tag-daily';
    v_window bigint := batch_param(c_job, 'window_days', 31, 2, 365);
    v_now    timestamptz;
    v_today  date;
    v_oldest date;
    v_since  timestamptz;
    v_days   date[];
    v_day    date;
    v_n      bigint;
    v_count  bigint := 0;
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

    v_now := clock_timestamp(); -- 모든 날이 같은 순간을 본다
    v_today := (v_now AT TIME ZONE 'Asia/Seoul')::date;
    v_oldest := v_today - (v_window - 1)::int;
    BEGIN
        -- 없는 게시판(지운 게시판)의 집계 줄 — 글 행이 지워져 바뀐 글로 찾을 수 없다
        DELETE FROM post_tag_daily d WHERE NOT EXISTS (SELECT 1 FROM boards b WHERE b.id = d.board_id);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        v_total := v_total + v_n;
        -- 범위보다 앞선 날의 바뀐 날 표시 — 세지 않는다(그 날은 마지막으로 센 값 그대로)
        DELETE FROM post_tag_dirty_days WHERE day < v_oldest;

        SELECT max(r.started_at) - interval '5 minutes' INTO v_since
          FROM batch_runs r
         WHERE r.job_key = c_job AND r.status IN ('OK', 'SKIP') AND r.id <> p_run_id;
        IF v_since IS NULL THEN
            SELECT array_agg(g::date ORDER BY g) INTO v_days FROM generate_series(v_oldest::timestamp, v_today::timestamp, interval '1 day') g;
        ELSE
            SELECT array_agg(DISTINCT d ORDER BY d) INTO v_days
              FROM (SELECT v_today - 1 AS d
                    UNION SELECT v_today
                    UNION SELECT c.d FROM post_tag_daily_changed_days(v_since, v_now, v_oldest) c(d) WHERE c.d <= v_today
                    UNION SELECT dd.day FROM post_tag_dirty_days dd WHERE dd.day BETWEEN v_oldest AND v_today) u;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
        v_err := batch_error_text(v_state, v_msg);
    END;
    COMMIT;

    IF v_err IS NULL THEN
        FOREACH v_day IN ARRAY v_days LOOP -- 옛날 → 오늘
            BEGIN
                v_total := v_total + post_tag_daily_recount(v_day, v_now);
                v_count := v_count + 1;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
            END;
            COMMIT; -- 날마다 바꾼다
            EXIT WHEN v_err IS NOT NULL;
        END LOOP;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        'batch.result.postTagDaily', batch_args(v_count::text, v_total::text),
        'batch.result.postTagDaily.none', batch_args(v_count::text));
    COMMIT;
END
$proc$;

-- 새벽 보정(사용자 2026-11-15) — 최근 recent_days 일(기본 7)을 통째로 다시 센다. 10분 배치가 신호(수정 시각·바뀐 날 표시)로 못 잡는 예외적인 틀어짐
--   (DB 를 직접 고친 경우 · 수정 시각을 안 바꾸는 새 경로 등)을 하루 안에 바로잡는 안전망. 보정은 대부분 글이 올라온 뒤 며칠 안에 일어나므로 30일 전체는 세지 않는다.
--   태그 달린 글만 읽으므로 가볍다. 날마다 COMMIT.
CREATE OR REPLACE PROCEDURE batch_post_tag_daily_recent(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job   CONSTANT text := 'post-tag-daily-recent';
    v_days  bigint := batch_param(c_job, 'recent_days', 7, 1, 31);
    v_now   timestamptz;
    v_today date;
    v_count bigint := 0;
    v_total bigint := 0;
    v_err   text;
    v_state text;
    v_msg   text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN;
    END IF;
    COMMIT;
    v_now := clock_timestamp();
    v_today := (v_now AT TIME ZONE 'Asia/Seoul')::date;
    FOR i IN REVERSE (v_days - 1)::int..0 LOOP -- 옛날 → 오늘
        BEGIN
            v_total := v_total + post_tag_daily_recount(v_today - i, v_now);
            v_count := v_count + 1;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
        EXIT WHEN v_err IS NOT NULL;
    END LOOP;
    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        'batch.result.postTagDaily', batch_args(v_count::text, v_total::text),
        'batch.result.postTagDaily.none', batch_args(v_count::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('post-tag-daily', 'window_days', 31);
SELECT batch_define_every_minutes('post-tag-daily', 'batch_post_tag_daily', 10);
SELECT batch_param_seed('post-tag-daily-recent', 'recent_days', 7);
SELECT batch_define('post-tag-daily-recent', 'batch_post_tag_daily_recent', 4, 50); -- 04:50 KST(새벽 4시대 다른 배치와 겹치지 않는 분)

-- 처음 채우기 — 설치 직후·크론이 아직 안 돈 때(또는 pg_cron 이 없는 설치)에도 추천·칩이 비지 않게 최근 31일을 한 번 센다. 빈 DB 면 바로 끝난다.
-- 이 파일이 바뀌어 다시 적용될 때도 돈다(지우고 다시 넣기라 같은 값이 된다).
SELECT post_tag_daily_recount_recent(31);
