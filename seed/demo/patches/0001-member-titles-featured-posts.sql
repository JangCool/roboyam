-- 데모 팩 패치 0001 — 우수 글(post_features, V238) · 인기글 상위 날짜 목록(point_popular_rewards, V238) · 회원 칭호(member_titles·member_title_prefs, V240)
-- (docs/features/point-board-tier-contract.md · member-title-contract.md, 2026-11-17)
--
-- 변수: :'demo_now' = 팩 기준 시각, :demo_id_base = 그 시각의 Snowflake ID(새 행 ID = 여기에 1, 2, … — 이 패치는 1~, 0002 는 1001~).
-- 원고(seed/demo/)에는 칭호·우수 글을 적을 자리가 없다 — 처음부터 다시 만들 때(seed-demo) 이 패치 내용은 빠진다(docs/seed-demo.md 「패치」).
--
-- 넣는 것
--  1) 인기글 상위 목록: 기준 시각 전 30일(한국 날짜)마다 하루 인기글 상위 N개(포인트 정책 popular_reward_top_n, 0 이면 10) — 인기글 보상·칭호 배치와 같은 점수·글쓴이 한도.
--     칭호 「인기글」(POPULAR_DAYS)이 이 표를 센다. DB 함수 popular_rank_candidates 를 그대로 부르지 않는 이유: 함수가 글 ID 범위(board_snowflake_floor)로 먼저 자르는데,
--     데모 글은 ID 가 「만든 때」라 작성 시각(지난날)과 어긋나 하나도 걸리지 않는다(docs/seed-demo.md 「글 id 와 작성 시각이 어긋난다」). 그래서 같은 조건을 작성 시각으로만 건다.
--  2) 우수 글 선정 6개(팁·강좌 tip · 사용기 review) — 지난달 선정 2개(지난달 이달의 작가와 이어짐), 이번 달 4개(관리자 「이달의 작가 뽑기」 후보). 포인트(보류 지급·원장)는 넣지 않는다.
--  3) 이달의 작가(운영자 수여): 3개월 전 1명 · 2개월 전 2명 · 지난달 3명(우수 글이 뽑힌 두 회원 + 석 달 연속 받은 회원 → 그 회원은 명예 작가, 자동·세 번째 수여와 같은 때).
--  4) 인기글 칭호(자동·단계): 1)의 목록으로 동 문턱(10일)을 넘은 회원(AI 회원·운영팀 계정 제외) — 배치(title-auto-grant)가 줄 것과 같다.
--     2026-11-17 팩: 사이트 상위 개수 5 · 데모 글이 있는 날 21일이라 가장 많은 회원이 7일 — 받는 회원이 없다(내 칭호의 진행 막대로 보인다). 지어내지 않는다.
--  5) 대표 칭호: 명예 작가 1 · 이달의 작가 2(드론날리기는 0002 에서 리뷰어로). 리뷰어 칭호와 그 대표 칭호는 0002(체험단·구매 인증 뒤).
-- ⚠ 이달의 작가 기간(period 'YYYY-MM')은 글자라 설치 때 옮겨지지 않는다(DemoDataInstaller 는 시각·ID 만 옮김) — 늦게 설치하면 「n월의 작가」가 그 시점보다 옛 달로 보인다.

SELECT id AS op FROM operators ORDER BY id LIMIT 1 \gset
CREATE TEMP SEQUENCE demo_seq_0001;
CREATE TEMP TABLE demo_m AS SELECT nickname, user_id FROM profiles;

-- ── 1) 인기글 상위 날짜 목록 ──────────────────────────────────────────────
CREATE TEMP TABLE demo_pop AS
SELECT p.id AS post_id, p.author_id, (p.created_at AT TIME ZONE 'Asia/Seoul')::date AS day,
       popular_author_key(p.author_id, p.author_ip, p.guest_name) AS author_key,
       (p.like_count * s.like_weight + p.comment_count * s.comment_weight + p.view_count * s.view_weight)::double precision AS score
  FROM posts p
  JOIN boards b ON b.id = p.board_id
  JOIN board_defaults d ON d.id = 1
  JOIN popular_settings s ON s.id = 1
 WHERE p.created_at >= (((timestamptz :'demo_now' AT TIME ZONE 'Asia/Seoul')::date - 30)::timestamp AT TIME ZONE 'Asia/Seoul')
   AND p.created_at < (((timestamptz :'demo_now' AT TIME ZONE 'Asia/Seoul')::date)::timestamp AT TIME ZONE 'Asia/Seoul')
   AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND p.hidden_at IS NULL AND NOT p.adult AND NOT p.secret
   AND (p.group_id IS NULL OR (p.is_public AND p.group_left_at IS NULL AND NOT EXISTS (SELECT 1 FROM groups g WHERE g.id = p.group_id AND g.status = 'INACTIVE')))
   AND b.active AND NOT ('POPULAR' = ANY (b.gadget_exclusions))
   AND (NOT s.exclude_staff_boards OR coalesce(b.write_min, d.write_min) <> 'STAFF')
   AND p.like_count >= s.min_likes AND p.comment_count >= s.min_comments AND p.view_count >= s.min_views
   AND (p.like_count * s.like_weight + p.comment_count * s.comment_weight + p.view_count * s.view_weight) > 0
   AND NOT EXISTS (SELECT 1 FROM popular_exclusions e WHERE e.post_id = p.id)
   AND NOT EXISTS (SELECT 1 FROM ai_member_profiles am WHERE am.user_id = p.author_id)
   AND NOT EXISTS (SELECT 1 FROM post_deals pd WHERE pd.post_id = p.id AND (pd.source = 'BUSINESS' OR pd.status = 'ENDED'));

INSERT INTO point_popular_rewards (day, rank, post_id, author_id, score, created_at)
SELECT r.day, r.rn, r.post_id, r.author_id, r.score, ((r.day + 1)::timestamp + interval '10 minutes') AT TIME ZONE 'Asia/Seoul'
  FROM (SELECT c.*, row_number() OVER (PARTITION BY c.day ORDER BY c.score DESC, c.post_id DESC) AS rn
          FROM (SELECT x.*, row_number() OVER (PARTITION BY x.day, x.author_key ORDER BY x.score DESC, x.post_id DESC) AS author_rn FROM demo_pop x) c
         WHERE c.author_rn <= (SELECT coalesce(max(s.max_per_author), 2) FROM popular_settings s WHERE s.id = 1)) r
 WHERE r.rn <= (SELECT CASE WHEN popular_reward_top_n > 0 THEN popular_reward_top_n ELSE 10 END FROM point_settings WHERE id = 1)
ON CONFLICT DO NOTHING;

-- ── 2) 우수 글 선정 ──────────────────────────────────────────────────────
-- 선정 시각 = 글 작성 20시간 뒤(기준 시각 1시간 전을 넘지 않게). 지난달 두 글은 작성·선정이 모두 지난달(한국 시간) 안이다.
CREATE TEMP TABLE demo_feat (board_slug text, title text);
INSERT INTO demo_feat VALUES
    ('tip',    'AI 챗봇한테 원하는 답 받는 질문 쓰는 법 정리'),
    ('review', '교육용 휴머노이드 키트 조립 2주 사용기'),
    ('review', '한 달 써 본 폴더블폰, 바 형태로 다시 못 돌아갈 듯'),
    ('tip',    'ROS 2 처음 시작하는 법 정리 (설치부터 첫 노드까지)'),
    ('review', '한 달 같이 산 가정용 로봇 펫 솔직 후기'),
    ('tip',    '중고 그래픽카드 살 때 체크리스트 정리');

CREATE TEMP TABLE demo_feat_posts AS
SELECT p.id AS post_id, p.author_id, least(p.created_at + interval '20 hours', timestamptz :'demo_now' - interval '1 hour') AS featured_at
  FROM demo_feat f
  JOIN boards b ON b.slug = f.board_slug
  JOIN posts p ON p.board_id = b.id AND p.title = f.title AND p.deleted_at IS NULL;

DO $$ BEGIN
    IF (SELECT count(*) FROM demo_feat_posts) <> 6 THEN
        RAISE EXCEPTION '우수 글로 고를 데모 글 6개 중 % 개만 찾았다 — 글 제목이 원고와 다르다', (SELECT count(*) FROM demo_feat_posts);
    END IF;
END $$;

INSERT INTO post_features (id, post_id, author_id, featured_by, featured_at)
SELECT :demo_id_base + nextval('demo_seq_0001'), post_id, author_id, :op, featured_at
  FROM demo_feat_posts ORDER BY featured_at;

-- ── 3) 이달의 작가 · 명예 작가 ─────────────────────────────────────────────
-- m0 = 기준 시각이 든 달의 1일 0시(한국 시간). n 달 전 몫의 수여 = (m0 − (n−1)달) + 1일 10시 — 그 달이 끝난 다음 달 2일 오전.
CREATE TEMP TABLE demo_award (nickname text, months_ago int, reason text);
INSERT INTO demo_award VALUES
    ('새벽코딩',   3, '꾸준한 개발 일지와 질문 답변'),
    ('새벽코딩',   2, '꾸준한 개발 일지와 질문 답변'),
    ('웹툰정주행', 2, '웹툰 정주행 후기 연재'),
    ('새벽코딩',   1, '석 달 연속 — 개발 일지 연재'),
    ('직관러',     1, '우수 글 「AI 챗봇한테 원하는 답 받는 질문 쓰는 법 정리」'),
    ('드론날리기', 1, '우수 글 「교육용 휴머노이드 키트 조립 2주 사용기」');

CREATE TEMP TABLE demo_award_rows AS
SELECT m.user_id, a.reason,
       to_char(date_trunc('month', timestamptz :'demo_now' AT TIME ZONE 'Asia/Seoul') - make_interval(months => a.months_ago), 'YYYY-MM') AS period,
       (date_trunc('month', timestamptz :'demo_now' AT TIME ZONE 'Asia/Seoul') - make_interval(months => a.months_ago - 1) + interval '1 day 10 hours') AT TIME ZONE 'Asia/Seoul' AS granted_at
  FROM demo_award a JOIN demo_m m ON m.nickname = a.nickname;

DO $$ BEGIN
    IF (SELECT count(*) FROM demo_award_rows) <> 6 THEN
        RAISE EXCEPTION '이달의 작가로 줄 데모 회원을 다 찾지 못했다';
    END IF;
END $$;

INSERT INTO member_titles (id, user_id, title_id, tier, period, source, granted_at, granted_by, grant_reason, seen_at)
SELECT :demo_id_base + nextval('demo_seq_0001'), r.user_id, t.id, NULL, r.period, 'AWARD', r.granted_at, :op, r.reason, r.granted_at + interval '1 day'
  FROM demo_award_rows r CROSS JOIN titles t
 WHERE t.title_key = 'monthly-writer'
 ORDER BY r.granted_at, r.user_id;

-- 명예 작가: 이달의 작가 살아 있는 줄이 설정 횟수(title_settings.honorary_count, 기본 3) 이상 — 그 횟수째 수여 때 함께(TitleGrantService#grantHonoraryIfDue 와 같은 판정)
INSERT INTO member_titles (id, user_id, title_id, tier, period, source, granted_at, seen_at)
SELECT :demo_id_base + nextval('demo_seq_0001'), x.user_id, h.id, NULL, '', 'AUTO', x.granted_at, x.granted_at + interval '1 day'
  FROM (SELECT mt.user_id, max(mt.granted_at) AS granted_at, count(*) AS n
          FROM member_titles mt JOIN titles t ON t.id = mt.title_id AND t.title_key = 'monthly-writer'
         WHERE mt.revoked_at IS NULL GROUP BY mt.user_id) x
  CROSS JOIN titles h
 WHERE h.title_key = 'honorary-writer' AND h.enabled
   AND x.n >= (SELECT honorary_count FROM title_settings WHERE id = 1)
   AND NOT EXISTS (SELECT 1 FROM member_titles e WHERE e.user_id = x.user_id AND e.title_id = h.id AND e.revoked_at IS NULL);

-- ── 4) 인기글 칭호(자동·단계) ─────────────────────────────────────────────
-- 받은 때 = 동 문턱을 채운 날 다음 날 00:10(한국 시간, 자동 판정 배치 시각) · 단계는 지금 센 날 수로.
INSERT INTO member_titles (id, user_id, title_id, tier, period, source, granted_at, seen_at)
SELECT :demo_id_base + nextval('demo_seq_0001'), c.author_id, t.id,
       CASE WHEN c.days >= t.tier_gold THEN 'GOLD' WHEN c.days >= t.tier_silver THEN 'SILVER' ELSE 'BRONZE' END,
       '', 'AUTO', c.reached_at, c.reached_at + interval '6 hours'
  FROM titles t
  CROSS JOIN LATERAL (
        SELECT d.author_id, count(*) AS days,
               ((array_agg(d.day ORDER BY d.day))[t.tier_bronze] + 1)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '10 minutes' AS reached_at
          FROM (SELECT DISTINCT author_id, day FROM point_popular_rewards WHERE author_id IS NOT NULL) d
         WHERE NOT EXISTS (SELECT 1 FROM ai_member_profiles am WHERE am.user_id = d.author_id)
           AND NOT EXISTS (SELECT 1 FROM operator_member_accounts o WHERE o.user_id = d.author_id)
           AND EXISTS (SELECT 1 FROM users u WHERE u.id = d.author_id AND u.status = 'ACTIVE')
         GROUP BY d.author_id
        HAVING count(*) >= t.tier_bronze) c
 WHERE t.title_key = 'popular' AND t.enabled AND t.tier_bronze IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM member_titles e WHERE e.user_id = c.author_id AND e.title_id = t.id AND e.period = '' AND e.revoked_at IS NULL)
 ORDER BY c.author_id;

-- ── 5) 대표 칭호 ─────────────────────────────────────────────────────────
INSERT INTO member_title_prefs (user_id, representative_title_id, hidden, updated_at)
SELECT m.user_id, t.id, false, timestamptz :'demo_now' - make_interval(hours => v.hours_ago)
  FROM (VALUES ('새벽코딩', 'honorary-writer', 300), ('직관러', 'monthly-writer', 200), ('웹툰정주행', 'monthly-writer', 900)) AS v (nickname, title_key, hours_ago)
  JOIN demo_m m ON m.nickname = v.nickname
  JOIN titles t ON t.title_key = v.title_key
 WHERE EXISTS (SELECT 1 FROM member_titles mt WHERE mt.user_id = m.user_id AND mt.title_id = t.id AND mt.revoked_at IS NULL)
ON CONFLICT (user_id) DO UPDATE SET representative_title_id = EXCLUDED.representative_title_id, hidden = false, updated_at = EXCLUDED.updated_at;

-- 결과 한 줄(로그)
SELECT format('0001 — 인기글 목록 %s줄(%s일) · 우수 글 %s · 칭호 %s줄(회원 %s명) · 대표 칭호 %s',
              (SELECT count(*) FROM point_popular_rewards), (SELECT count(DISTINCT day) FROM point_popular_rewards), (SELECT count(*) FROM post_features),
              (SELECT count(*) FROM member_titles), (SELECT count(DISTINCT user_id) FROM member_titles), (SELECT count(*) FROM member_title_prefs));
