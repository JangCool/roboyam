-- 하이라이트를 놓일 곳(place)별로 — docs/features/tips-contract.md 「결정」(페이지별 하이라이트 + 자동 채움)·「서버 구현 결과」.
-- ⚠ 가번호 V161 — develop 최신 V160. 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- place = 하이라이트가 놓일 곳의 키: 'home'(홈 — 지금까지의 모든 하이라이트) · 'tips'(팁·강좌·사용기 「이번 주 추천」) · 가젯 페이지 키(gadget_pages.page_key).
--         모양은 가젯 페이지 키 규칙과 같다(^[a-z][a-z0-9-]{1,29}$). 가젯 페이지가 지워져도 행은 남는다(외래 키 없음 — 공개 읽기는 없는 곳을 'home' 으로 읽는다).
-- 같은 글을 여러 곳에 넣을 수 있다 — 유일 제약은 (place, post_id). 순서(sort_order)는 곳마다 따로 센다.
ALTER TABLE highlights
    ADD COLUMN place varchar(30) NOT NULL DEFAULT 'home',
    ADD CONSTRAINT ck_highlights_place CHECK (place ~ '^[a-z][a-z0-9-]{1,29}$');

ALTER TABLE highlights DROP CONSTRAINT ux_highlights_post;
ALTER TABLE highlights ADD CONSTRAINT ux_highlights_place_post UNIQUE (place, post_id);

DROP INDEX ix_highlights_order;
CREATE INDEX ix_highlights_place_order ON highlights (place, sort_order, id);
-- 글 상세의 「하이라이트 글인가」·관리자 토론 줄의 하이라이트 ID 는 글 번호로 찾는다(예전 유일 제약 색인 대신)
CREATE INDEX ix_highlights_post ON highlights (post_id);
