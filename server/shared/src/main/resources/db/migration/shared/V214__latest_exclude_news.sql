-- 최신글에서 뉴스 게시판 빼기(docs/features/home-news-contract.md 「POST_LIST config」 excludeBoardIds — 사용자 2026-11-15).
-- 글 목록 가젯 config 새 키 excludeBoardIds(게시판 slug 목록, 최대 20)는 대상 게시판이 ALL 일 때만 쓰여 전체 게시판 글에서 그 게시판들의 글을 뺀다.
-- 새소식 가젯이 뉴스 게시판을 따로 보여 주므로, 이미 설치된 DB 의 아이로빗 홈 최신글(PC 전용 · 모바일 전용)에 ["news"] 를 넣는다 — 코드 기본 배치
-- HomeLayoutDefaults.irobit() 와 같다.
-- 대상: 뉴스 게시판(slug news)이 있을 때, 아이로빗 홈 배치(home_layouts theme irobit)에 놓인 홈 가젯 중
--   POST_LIST · 정렬 최신(없으면 최신) · 대상 게시판 ALL(boardScope ALL, 또는 boardScope 가 없거나 모르는 값이고 boardIds 가 비었을 때 — GadgetConfigs.scope 와 같은 읽기)
--   · boardIds 가 ["news"] 아님 · PC 전용이나 모바일 전용(desktop <> mobile) · excludeBoardIds 칸이 아직 없는 것.
-- 운영자가 excludeBoardIds 를 이미 정했으면(빈 목록 포함) 손대지 않는다. 서버는 이 마이그레이션 뒤에 뜨므로 배치·가젯 캐시는 비어 있다.

UPDATE home_gadgets g
   SET config = coalesce(g.config, '{}'::jsonb) || '{"excludeBoardIds": ["news"]}'::jsonb
 WHERE EXISTS (SELECT 1 FROM boards b WHERE b.slug = 'news')
   AND g.theme = 'irobit'
   AND g.page_id IS NULL
   AND NOT g.board_layout
   AND g.type = 'POST_LIST'
   AND g.desktop <> g.mobile
   AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST'
   AND (g.config ->> 'boardScope' = 'ALL'
        OR (coalesce(g.config ->> 'boardScope', '') NOT IN ('ALL', 'FOLDER', 'PICK', 'BOARD')
            AND (jsonb_typeof(g.config -> 'boardIds') IS DISTINCT FROM 'array' OR g.config -> 'boardIds' = '[]'::jsonb)))
   AND coalesce(g.config -> 'boardIds', '[]'::jsonb) <> '["news"]'::jsonb
   AND NOT coalesce(g.config, '{}'::jsonb) ? 'excludeBoardIds'
   AND EXISTS (SELECT 1
                 FROM home_layouts l
                CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(l.items) = 'array' THEN l.items ELSE '[]'::jsonb END) it
                WHERE l.theme = 'irobit'
                  AND it ->> 'gadgetId' = g.id::text);
