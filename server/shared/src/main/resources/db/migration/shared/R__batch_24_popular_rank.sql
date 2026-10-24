-- DB 배치 `popular-rank` — 인기글 기간 탭(실시간 · 어제 · 이번 주) 순위 계산. docs/features/member-activity-contract.md 「결정 2차 — 인기글」
-- (community docs/api-popular.md 1~4절을 로보얌에 맞춘 것 — 관리자 인기글 설정·뺀 글은 없다. 규칙 값은 아래 「서버 상수」이고 설정 화면이 없다.)
--
-- 기간(popular_period_range — 한국시간(Asia/Seoul) 달력, [from, to), 주는 월요일 시작). 공개 API(GET /api/home/popular 의 from·to)도 이 함수로 범위를 계산한다
-- (server/api PopularRankQueries#range) — 둘이 어긋나지 않게 경계 계산은 이 함수 하나에만 둔다.
--   REALTIME 오늘 0시 ~ 내일 0시 · YESTERDAY 어제 하루 · WEEK 이번 주 월요일 0시부터 7일 · LAST_WEEK 지난주(채우기에만 쓴다)
--
-- 서버 상수(사용자 결정 2026-10-24 — 바꾸려면 이 파일을 고친다):
--   점수 = 추천 × 3 + 댓글 × 2 + 조회 × 0.1 (popular_rank_candidates). 실시간만 시간 감쇠 ÷ (경과 시간(시) + 2)^1.5 — 경과 시간의 기준은 배치 시각.
--   어제·이번 주는 감쇠 없음. 후보 조건: 추천 3 이상 · 댓글 0 이상 · 조회 0 이상 · 점수 0 초과.
--   한 글쓴이는 한 탭에 최대 2개(채운 글 포함 탭 전체로 센다) · 탭마다 상위 50개 보관 · 채우기 기준 5개:
--   실시간 자기 글이 5개 미만이면 어제 글을 감쇠 없는 점수 순으로 모자란 만큼(fill = YESTERDAY), 이번 주가 5개 미만이면 지난주 글(fill = LAST_WEEK), 어제 탭은 채우지 않는다.
--   점수가 같으면 ID 가 큰(나중에 쓴) 글이 먼저.
-- 제외: 19금 글 · 임시저장 · 삭제 · 예약 삭제 시각이 지난 글 · 지금 숨김 중인 글(임시조치가 끝난 글은 다시 후보) · 꺼진 게시판 · 「가젯 노출 금지」에 인기글(POPULAR)을 고른 게시판
--   (boards.gadget_exclusions, V69 — 핫딜·직접홍보 게시판은 이 값으로 시작한다) · 운영진 전용 게시판
--   (유효 글쓰기 자격 = 게시판 값 ?? 기본값 이 STAFF 인 게시판 — 공개 게시판의 「운영진만 글쓰기」, BoardFields.STAFF). 공개 API 는 읽을 때 지금 상태로 다시 거른다.
-- 글쓴이 구분(popular_author_key): 회원은 회원 ID, 비회원은 작성 IP(PostRepositoryQDSL 의 작성 속도 판정과 같은 기준) — 보유기간이 지나 IP 를 지운 글은 입력한 이름.
-- 기간 판정: 글 표는 id(Snowflake = 시각) 월 범위 파티션이라 id 범위(board_snowflake_floor, R__batch_17)로 조각을 가지치기하고 created_at 으로 정확히 자른다.
--
-- 쓰기: 기간마다 한 트랜잭션에서 그 기간의 스냅샷 줄을 지우고 다시 넣는다(BEGIN … EXCEPTION 블록 하나 = 기간 하나, 블록을 나온 뒤 COMMIT) — 읽는 쪽은 늘 온전한 스냅샷을 본다.
--   한 기간이 실패하면 그 기간은 예전 스냅샷이 남고 실행은 FAIL(그때까지 넣은 줄 수와 사유). 세 기간의 기준 시각은 실행 시작 때 한 번 잡는다(clock_timestamp).
-- 기본 실행: 5분마다(*/5 — 가정, 운영자가 배치 화면에서 cron 을 바꿀 수 있다). batch_define_every_minutes(R__batch_00).
-- 업무 배치(BatchCatalog.POPULAR_RANK — 분류 MEMBER): pg_cron 이 없으면 순위가 계산되지 않는다 → 관리자 서버 기동 WARN(DbBatchGateway#prepare)과 배치 목록의 「사용 불가」.
--   DBA 는 CALL batch_popular_rank(); 로 직접 돌릴 수 있다. Java 폴백은 없다.
-- 결과: 처리 건수 = 세 기간에 넣은 순위 줄 수(최대 150), 0 이면 SKIP.
-- 호출: CALL batch_popular_rank();  (「지금 실행」은 CALL batch_popular_rank('MANUAL', 운영자ID, NULL))

-- 기간의 범위 [range_from, range_to) — 한국시간 달력의 경계를 timestamptz 로. 모르는 기간이면 둘 다 NULL
CREATE OR REPLACE FUNCTION popular_period_range(p_period text, p_at timestamptz, OUT range_from timestamptz, OUT range_to timestamptz)
LANGUAGE sql STABLE AS $fn$
    SELECT (r.first_day::timestamp AT TIME ZONE 'Asia/Seoul'), ((r.first_day + r.days)::timestamp AT TIME ZONE 'Asia/Seoul')
      FROM (SELECT CASE p_period
                       WHEN 'REALTIME'  THEN d.today
                       WHEN 'YESTERDAY' THEN d.today - 1
                       WHEN 'WEEK'      THEN d.today - (extract(isodow FROM d.today)::int - 1)
                       WHEN 'LAST_WEEK' THEN d.today - (extract(isodow FROM d.today)::int - 1) - 7
                   END AS first_day,
                   CASE WHEN p_period IN ('WEEK', 'LAST_WEEK') THEN 7 ELSE 1 END AS days
              FROM (SELECT (p_at AT TIME ZONE 'Asia/Seoul')::date AS today) d) r
$fn$;

-- 글쓴이 구분 열쇠 — 회원 m:<회원 ID>, 비회원 g:<작성 IP>(IP 를 지웠으면 n:<이름>)
CREATE OR REPLACE FUNCTION popular_author_key(p_author_id bigint, p_author_ip text, p_guest_name text) RETURNS text
LANGUAGE sql IMMUTABLE AS $fn$
    SELECT CASE WHEN p_author_id IS NOT NULL THEN 'm:' || p_author_id
                WHEN p_author_ip IS NOT NULL THEN 'g:' || p_author_ip
                ELSE 'n:' || coalesce(p_guest_name, '') END
$fn$;

-- 기간 [p_from, p_to) 에 올라온 후보 글과 점수(서버 상수 — 머리말). p_decay = 실시간 탭의 시간 감쇠(기준 시각 p_now)
CREATE OR REPLACE FUNCTION popular_rank_candidates(p_from timestamptz, p_to timestamptz, p_now timestamptz, p_decay boolean)
RETURNS TABLE (post_id bigint, author_key text, score double precision)
LANGUAGE sql STABLE AS $fn$
    SELECT p.id,
           popular_author_key(p.author_id, p.author_ip, p.guest_name),
           (p.like_count * 3 + p.comment_count * 2 + p.view_count * 0.1)::double precision
               / CASE WHEN p_decay THEN power(greatest(extract(epoch FROM (p_now - p.created_at))::double precision / 3600.0, 0) + 2, 1.5)
                      ELSE 1 END
      FROM posts p
      JOIN boards b ON b.id = p.board_id
      JOIN board_defaults d ON d.id = 1
     WHERE p.id >= board_snowflake_floor(p_from) AND p.id < board_snowflake_floor(p_to)
       AND p.created_at >= p_from AND p.created_at < p_to
       AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL
       AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > p_now)
       AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= p_now))
       AND NOT p.adult
       AND b.active
       AND NOT ('POPULAR' = ANY (b.gadget_exclusions))
       AND coalesce(b.write_min, d.write_min) <> 'STAFF'
       AND p.like_count >= 3 AND p.comment_count >= 0 AND p.view_count >= 0
       AND (p.like_count * 3 + p.comment_count * 2 + p.view_count * 0.1) > 0
$fn$;

CREATE OR REPLACE PROCEDURE batch_popular_rank(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job            CONSTANT text := 'popular-rank';
    c_max_per_author CONSTANT int  := 2;   -- 한 글쓴이는 한 탭에 최대 2개(채운 글 포함)
    c_keep           CONSTANT int  := 50;  -- 탭마다 보관하는 순위 수
    c_fill_min       CONSTANT int  := 5;   -- 자기 기간 글이 이보다 적으면 앞 기간 글로 채운다
    v_now            timestamptz;
    v_period         text;
    v_fill           text;
    v_from           timestamptz;
    v_to             timestamptz;
    v_own            bigint;
    v_filled         bigint;
    v_realtime       bigint := 0;
    v_yesterday      bigint := 0;
    v_week           bigint := 0;
    v_total          bigint := 0;
    v_err            text;
    v_state          text;
    v_msg            text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_now := clock_timestamp(); -- 세 기간과 채우기가 같은 순간을 본다(실시간 감쇠의 기준 시각)
    FOREACH v_period IN ARRAY ARRAY['REALTIME', 'YESTERDAY', 'WEEK'] LOOP
        v_own := 0;
        v_filled := 0;
        v_fill := CASE v_period WHEN 'REALTIME' THEN 'YESTERDAY' WHEN 'WEEK' THEN 'LAST_WEEK' END; -- 어제 탭은 채우지 않는다
        BEGIN
            DELETE FROM post_rank_snapshots WHERE period = v_period;

            -- 자기 기간 글: 글쓴이마다 점수 순 2개까지 → 점수 순 50개
            SELECT r.range_from, r.range_to INTO v_from, v_to FROM popular_period_range(v_period, v_now) r;
            INSERT INTO post_rank_snapshots (period, rank, post_id, score, fill, computed_at)
            SELECT v_period, row_number() OVER (ORDER BY k.score DESC, k.post_id DESC), k.post_id, k.score, NULL, v_now
              FROM (SELECT c.post_id, c.score
                      FROM (SELECT x.post_id, x.score,
                                   row_number() OVER (PARTITION BY x.author_key ORDER BY x.score DESC, x.post_id DESC) AS author_rn
                              FROM popular_rank_candidates(v_from, v_to, v_now, v_period = 'REALTIME') x) c
                     WHERE c.author_rn <= c_max_per_author
                     ORDER BY c.score DESC, c.post_id DESC
                     LIMIT c_keep) k;
            GET DIAGNOSTICS v_own = ROW_COUNT;

            -- 채우기: 앞 기간 글을 감쇠 없는 점수 순으로 모자란 만큼. 글쓴이 상한은 탭 전체(이미 넣은 자기 글 + 채운 글)로 센다 —
            -- 점수 순으로 보며 「그 글쓴이의 자기 글 수 + 앞 기간 안 순번 ≤ 2」 인 글만 올린다(탐욕 선택과 같은 결과)
            IF v_fill IS NOT NULL AND v_own < c_fill_min THEN
                SELECT r.range_from, r.range_to INTO v_from, v_to FROM popular_period_range(v_fill, v_now) r;
                INSERT INTO post_rank_snapshots (period, rank, post_id, score, fill, computed_at)
                SELECT v_period, v_own + row_number() OVER (ORDER BY k.score DESC, k.post_id DESC), k.post_id, k.score, v_fill, v_now
                  FROM (SELECT c.post_id, c.score
                          FROM (SELECT x.post_id, x.author_key, x.score,
                                       row_number() OVER (PARTITION BY x.author_key ORDER BY x.score DESC, x.post_id DESC) AS author_rn
                                  FROM popular_rank_candidates(v_from, v_to, v_now, false) x) c
                          LEFT JOIN (SELECT popular_author_key(p.author_id, p.author_ip, p.guest_name) AS author_key, count(*) AS n
                                       FROM post_rank_snapshots s
                                       JOIN posts p ON p.id = s.post_id
                                      WHERE s.period = v_period
                                      GROUP BY 1) own ON own.author_key = c.author_key
                         WHERE coalesce(own.n, 0) + c.author_rn <= c_max_per_author
                         ORDER BY c.score DESC, c.post_id DESC
                         LIMIT c_fill_min - v_own) k;
                GET DIAGNOSTICS v_filled = ROW_COUNT;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        CASE v_period
            WHEN 'REALTIME' THEN v_realtime := v_own + v_filled;
            WHEN 'YESTERDAY' THEN v_yesterday := v_own + v_filled;
            ELSE v_week := v_own + v_filled;
        END CASE;
        v_total := v_total + v_own + v_filled;
        COMMIT; -- 이 기간의 스냅샷을 한 번에 바꾼다
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        'batch.result.popularRank', batch_args(v_realtime::text, v_yesterday::text, v_week::text),
        'batch.result.popularRank.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_define_every_minutes('popular-rank', 'batch_popular_rank', 5);
