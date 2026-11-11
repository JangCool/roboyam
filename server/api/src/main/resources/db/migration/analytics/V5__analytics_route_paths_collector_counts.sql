-- 방문 통계 3단계 — docs/features/site-analytics-contract.md 「3단계」 결정 ②(집계 주소 = 정해진 주소만, 번호 조각은 {id}) · 「뺄 계정」.
--
-- 1) 기존 집계 줄의 주소를 user-web 라우트 기준으로 묶는다(한 번). 이제부터는 공개 사이트 서버(api VisitRoutes)가 신호를 받을 때 path·prevPath 를 라우트 표 기준으로 바꿔
--    원본·페이지별·이동 흐름을 쌓는다 — 게시판 [slug]·가젯 페이지 [key] 만 값을 남기고 나머지 동적 자리([id]·[eventId]·[orderNo] …)는 문자 그대로 {id}.
--    옛 줄은 slug·key 를 확인하지 않고(지운 게시판 기록도 남긴다) 동적 자리만 {id} 로 바꾸며, 같은 열쇠가 된 줄은 더해서 한 줄로 합친다.
--    라우트 표(2026-11-11, apps/user-web/app/pages)의 동적 자리 — 같은 자리의 정적 주소가 이긴다(vue-router 와 같다):
--      /posts/[id](/edit) · /group-buys/[id](/order) · /groups/[id]/…(me·new 는 정적) · /groups/[id]/events/[eventId](/edit)(new·past 는 정적) · /users/[id] ·
--      /point-shop/[id] · /robotikon/[id](contract·guide·studio 는 정적) · /robotikon/studio/[id](payout 은 정적) · /messages/[id](new 는 정적) · /me/orders/[orderNo] ·
--      /business/applications/[id]
--    기존 원본(analytics_page_views)은 고치지 않는다(보관 2개월 안에 지워진다 — 실시간은 최근 30분만 읽어 옛 줄이 섞이지 않는다).
--    롤링 배포 중 아직 옛 서버가 쓴 몇 분치 줄은 옛 주소로 남을 수 있다(통계라 허용 — 옛 서버가 모두 내려가면 생기지 않는다).
CREATE OR REPLACE FUNCTION analytics_v5_route_path(p text) RETURNS text
LANGUAGE sql IMMUTABLE AS $fn$
    SELECT CASE
        WHEN p ~ '^/posts/[^/]+(/edit)?/?$' THEN regexp_replace(p, '^/posts/[^/]+', '/posts/{id}')
        WHEN p ~ '^/group-buys/[^/]+(/order)?/?$' THEN regexp_replace(p, '^/group-buys/[^/]+', '/group-buys/{id}')
        WHEN p ~ '^/groups/[^/]+/events/[^/]+(/edit)?/?$' AND split_part(p, '/', 3) NOT IN ('me', 'new') AND split_part(p, '/', 5) NOT IN ('new', 'past')
            THEN regexp_replace(p, '^/groups/[^/]+/events/[^/]+', '/groups/{id}/events/{id}')
        WHEN p ~ '^/groups/[^/]+(/.*)?$' AND split_part(p, '/', 3) NOT IN ('me', 'new', '') THEN regexp_replace(p, '^/groups/[^/]+', '/groups/{id}')
        WHEN p ~ '^/users/[^/]+/?$' THEN '/users/{id}'
        WHEN p ~ '^/point-shop/[^/]+/?$' THEN '/point-shop/{id}'
        WHEN p ~ '^/robotikon/studio/[^/]+/?$' AND split_part(p, '/', 4) NOT IN ('payout', '') THEN '/robotikon/studio/{id}'
        WHEN p ~ '^/robotikon/[^/]+/?$' AND split_part(p, '/', 3) NOT IN ('contract', 'guide', 'studio', '') THEN '/robotikon/{id}'
        WHEN p ~ '^/messages/[^/]+/?$' AND split_part(p, '/', 3) NOT IN ('new', '') THEN '/messages/{id}'
        WHEN p ~ '^/me/orders/[^/]+/?$' THEN '/me/orders/{id}'
        WHEN p ~ '^/business/applications/[^/]+/?$' THEN '/business/applications/{id}'
        ELSE p
    END
$fn$;

-- 페이지별 일별(열쇠 day · path · kind) — 바뀌는 줄만 빼서 묶어 다시 넣고, 같은 열쇠의 줄이 있으면 더한다
WITH moved AS (
    DELETE FROM analytics_page_daily WHERE analytics_v5_route_path(path) <> path RETURNING day, path, kind, views
)
INSERT INTO analytics_page_daily (day, path, kind, views)
SELECT day, analytics_v5_route_path(path), kind, sum(views) FROM moved GROUP BY 1, 2, 3 ORDER BY 1, 2, 3
ON CONFLICT (day, path, kind) DO UPDATE SET views = analytics_page_daily.views + EXCLUDED.views;

-- 이동 흐름 일별(열쇠 day · from_path · to_path — 「바로 들어옴」 from_path = '' 는 그대로)
WITH moved AS (
    DELETE FROM analytics_flow_daily
     WHERE analytics_v5_route_path(from_path) <> from_path OR analytics_v5_route_path(to_path) <> to_path
    RETURNING day, from_path, to_path, moves
)
INSERT INTO analytics_flow_daily (day, from_path, to_path, moves)
SELECT day, analytics_v5_route_path(from_path), analytics_v5_route_path(to_path), sum(moves) FROM moved GROUP BY 1, 2, 3 ORDER BY 1, 2, 3
ON CONFLICT (day, from_path, to_path) DO UPDATE SET moves = analytics_flow_daily.moves + EXCLUDED.moves;

-- 한 번만 쓰는 함수 — 앞으로의 규칙은 서버의 라우트 표가 정본이다(두 곳에 남겨 어긋나지 않게)
DROP FUNCTION analytics_v5_route_path(text);

-- 2) 수집 진단 서버별 시간 칸에 두 숫자(늘 0 이상, 옛 서버의 넣기 문장은 칸을 적지 않아 0):
--    ignored_excluded = 뺄 계정(운영자 회원 계정·AI 회원)의 로그인 세션으로 온 방문 신호라 버린 수
--    unknown_path     = 라우트 표에 없거나 없는 게시판 slug·가젯 페이지 key 라 「알 수 없는 주소」(/{unknown})로 묶은 신호 수(버리지 않고 세었다)
ALTER TABLE analytics_collector_hourly ADD COLUMN IF NOT EXISTS ignored_excluded bigint NOT NULL DEFAULT 0;
ALTER TABLE analytics_collector_hourly ADD COLUMN IF NOT EXISTS unknown_path     bigint NOT NULL DEFAULT 0;
