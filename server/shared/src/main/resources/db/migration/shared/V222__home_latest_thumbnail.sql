-- 아이로빗 홈 최신글(PC 전용 · 모바일 전용 — 두 줄 모양 HEADLINE)에 줄 사진 켜기(사용자 2026-11-15 — 「이미지 올리면 썸네일 나오게, row 높이만큼 늘어나지 않게」).
-- docs/features/home-news-contract.md 「PC 첫 줄 아래 — 핫딜」. 코드 기본 배치 HomeLayoutDefaults.irobit() 와 같다.
--   · config.thumbnail NONE → AUTO(두 줄 모양이면 오른쪽 — 사진이 있는 글만). 화면은 제목 한 줄 줄에서 사진을 글 두 줄 높이에 꼭 맞춘다(GadgetPostRow thumbFit) — 줄 높이가 그대로다.
-- 대상: 아이로빗 홈 배치에 놓인 POST_LIST · 정렬 최신 · 줄 모양 HEADLINE · 제목 한 줄 · 뉴스 게시판 뺌(excludeBoardIds ["news"]) · 사진 NONE 인 것(V218 이 만든 모양 그대로). 운영자가 바꿨으면 손대지 않는다.
-- 서버는 이 마이그레이션 뒤에 뜨므로 가젯 캐시는 비어 있다.

UPDATE home_gadgets g
   SET config = g.config || '{"thumbnail": "AUTO"}'::jsonb
 WHERE g.theme = 'irobit'
   AND g.page_id IS NULL
   AND NOT g.board_layout
   AND g.type = 'POST_LIST'
   AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST'
   AND g.config ->> 'rowStyle' = 'HEADLINE'
   AND g.config ->> 'thumbnail' = 'NONE'
   AND (g.config ->> 'titleLines') = '1'
   AND g.config -> 'excludeBoardIds' = '["news"]'::jsonb
   AND EXISTS (SELECT 1
                 FROM home_layouts l
                CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(l.items) = 'array' THEN l.items ELSE '[]'::jsonb END) it
                WHERE l.theme = 'irobit'
                  AND it ->> 'gadgetId' = g.id::text);
