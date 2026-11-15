-- 글 태그(docs/features/post-tags-contract.md — 시안 확정 2026-11-15).
--
-- 1) post_tags: 글에 단 태그 — 글당 최대 5개(앱 PostTags 가 정리·검사). 글 표는 파티션 표라 칸을 더하지 않고 글 ID 를 키로 하는 별도 표에 둔다
--    (post_thumbnails·post_debates 와 같은 이유로 외래 키 없음 — 지운·가린 글의 행은 남아도 목록·추천 수 질의가 글 표의 조건으로 뺀다).
--    tag = 처음 쓴 모양(앞뒤 공백·앞의 # 떼고 안쪽 공백 하나로, 1~20자) · tag_key = 비교용(tag 의 영문 소문자 — 같은 글 안 겹침·태그 거르기·추천 묶기).
--    sort_order = 글쓴이가 단 차례(0부터 — 글 보기는 이 차례로 보인다). 고치기는 빠진 태그 행을 지우고 새 태그를 넣는다(남은 태그는 모양·차례만 고친다).
--    (tag_key, post_id) 색인 = 게시판 목록 ?tag= · 가젯 태그 탭 · 매거진 ?tag= 의 EXISTS 판정.
CREATE TABLE post_tags (
    post_id    bigint      NOT NULL,
    tag_key    varchar(40) NOT NULL,
    tag        varchar(20) NOT NULL,
    sort_order smallint    NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_post_tags PRIMARY KEY (post_id, tag_key)
);
CREATE INDEX ix_post_tags_key_post ON post_tags (tag_key, post_id);

-- 2) 게시판 정책 칸 「태그 쓰기」(V206 author_thumbnail_enabled 와 같은 구조 — boards 는 NULL = 기본값 따름, board_defaults 는 전체 기본값, 기본 끔).
--    켠 게시판에서만 글쓰기·고치기의 tags 칸을 받는다(꺼져 있으면 무시 — 오류 아님). 나중에 꺼도 이미 단 태그는 그대로(목록 ?tag= 거르기도 그대로).
ALTER TABLE boards ADD COLUMN tags_enabled boolean;
ALTER TABLE board_defaults ADD COLUMN tags_enabled boolean NOT NULL DEFAULT false;

-- 3) 이미 설치된 DB — 뉴스(news) · 팁과강좌(tip) · 사용기(review) 게시판은 태그 쓰기 켬(계약 「관리자」). 빈 DB 는 게시판이 없어 아무 일도 하지 않는다
--    (새 설치는 설치 프리셋이 정한다). 방금 만든 칸이라 모두 NULL 이다.
UPDATE boards SET tags_enabled = true WHERE slug IN ('news', 'tip', 'review') AND tags_enabled IS NULL;

-- 4) 아이로빗 홈 새소식 가젯(글 목록 · 대상 게시판 ["news"])의 태그 탭 — 코드 기본 배치 HomeLayoutDefaults.irobit() 와 같은 다섯 개.
--    아이로빗 홈 배치(home_layouts theme irobit)에 놓인 홈 가젯 중 POST_LIST · boardIds ["news"] 이고 tagTabs 칸이 아직 없는 것만(운영자가 이미 정했으면 — 빈 목록 포함 — 손대지 않음).
--    뉴스 게시판이 있을 때만. 서버는 이 마이그레이션 뒤에 뜨므로 배치·가젯 캐시는 비어 있다.
UPDATE home_gadgets g
   SET config = coalesce(g.config, '{}'::jsonb) || '{"tagTabs": ["AI", "반도체", "테슬라", "휴머노이드", "엔비디아"]}'::jsonb
 WHERE EXISTS (SELECT 1 FROM boards b WHERE b.slug = 'news')
   AND g.theme = 'irobit'
   AND g.page_id IS NULL
   AND NOT g.board_layout
   AND g.type = 'POST_LIST'
   AND g.config -> 'boardIds' = '["news"]'::jsonb
   AND NOT coalesce(g.config, '{}'::jsonb) ? 'tagTabs'
   AND EXISTS (SELECT 1
                 FROM home_layouts l
                CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(l.items) = 'array' THEN l.items ELSE '[]'::jsonb END) it
                WHERE l.theme = 'irobit'
                  AND it ->> 'gadgetId' = g.id::text);
