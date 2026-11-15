-- 아이로빗 홈 최신글(PC 전용)의 추천을 제목 앞 고정 칸(likes LEAD)에서 줄 오른쪽 끝(likes ICON — 게시판 이름 뒤 엄지 + 숫자)으로
-- (docs/features/home-news-contract.md — 사용자 2026-11-15 「추천 수 위치 우측 끝으로」). 코드 기본 배치 HomeLayoutDefaults.irobit() 와 같다.
-- 대상: 아이로빗 홈 배치(home_layouts theme irobit)에 놓인 홈 가젯 중 POST_LIST · PC 전용(desktop AND NOT mobile) · 촘촘(COMPACT) · 정렬 최신 · likes 가 LEAD 인 것.
-- 운영자가 likes 를 다른 값으로 바꿨으면 손대지 않는다. 서버는 이 마이그레이션 뒤에 뜨므로 배치·가젯 캐시는 비어 있다.

UPDATE home_gadgets g
   SET config = g.config || '{"likes": "ICON"}'::jsonb
 WHERE g.theme = 'irobit'
   AND g.page_id IS NULL
   AND NOT g.board_layout
   AND g.type = 'POST_LIST'
   AND g.desktop
   AND NOT g.mobile
   AND g.config ->> 'rowStyle' = 'COMPACT'
   AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST'
   AND g.config ->> 'likes' = 'LEAD'
   AND EXISTS (SELECT 1
                 FROM home_layouts l
                CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(l.items) = 'array' THEN l.items ELSE '[]'::jsonb END) it
                WHERE l.theme = 'irobit'
                  AND it ->> 'gadgetId' = g.id::text);
