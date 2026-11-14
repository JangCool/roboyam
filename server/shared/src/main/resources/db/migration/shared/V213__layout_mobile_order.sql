-- 배치의 모바일 순서(docs/features/home-news-contract.md 「모바일 순서」 2026-11-15) — 모바일 한 열 차례를 PC 배치와 따로 정한다.
--
-- ① 칸: 홈 배치(home_layouts) · 가젯 페이지 배치(gadget_page_layouts) · 게시판 레이아웃(board_layouts)에 mobile_order jsonb(NULL 가능) — 가젯 ID 글자 목록.
--    NULL = 지금처럼 PC 배치에서 계산(템플릿 mobile.order). 목록에 없는 가젯은 화면이 계산된 차례대로 그 뒤에 붙인다. 정리(배치에 없는 ID·겹친 ID 버리기)는 관리자 저장이 한다.
-- ② 이미 설치된 DB(installation COMPLETED · board_preset_applied_at 있음)의 아이로빗 홈 — 배치가 V212 모양일 때만 모바일 순서를 채운다:
--    새소식(POST_LIST boardIds ["news"]) · 모바일 전용 하이라이트 · 모바일 전용 최신글(POST_LIST 정렬 최신, 뉴스 아님)이 배치에 있고 mobile_order 가 NULL 일 때.
--    순서(코드 기본 배치 HomeLayoutDefaults.irobit() 와 같다): 새소식 → 상단 띠 배너 → 인기글 → 핫 아이로 픽(모바일 전용) → 최신글(모바일 전용) → 광고
--      → 팁과강좌·사용기 → 인기 소모임 → 영상·짤 → 띠 배너.
--    가젯은 배치를 보고 고른다(모두 모바일에 보이는 홈 가젯 중):
--      · 인기글 = 오른쪽 열(x ≥ 18)의 정렬 인기 POST_LIST 중 맨 위 · 광고 = 오른쪽 열의 AD 중 맨 위 · 인기 소모임 = GROUPS 중 맨 위
--      · 영상·짤 = 본문 열(x < 18)의 GALLERY 중 맨 위 · 띠 배너 = 영상·짤 바로 아래(같은 x·폭, 위 끝 = 영상·짤 아래 끝)의 AD
--      · 상단 띠 배너 = 본문 열 x0 · 폭 18 의 AD 중, 첫 줄(새소식 · 모바일 전용 둘)의 아래 끝 이후 · 영상·짤보다 위 · 띠 배너가 아닌 것 중 맨 위
--      · 팁과강좌·사용기 = 본문 열의 BOARD_CARDS 전부(위 → 왼쪽 차례)
--    못 찾는 가젯은 뺀다(화면이 계산 차례로 뒤에 붙인다). 운영자가 모바일 순서를 저장했으면(NULL 아님) 손대지 않는다.
-- 서버는 이 마이그레이션 뒤에 뜨므로 배치 캐시는 비어 있다(따로 비울 것 없음).

ALTER TABLE home_layouts ADD COLUMN mobile_order jsonb;
ALTER TABLE gadget_page_layouts ADD COLUMN mobile_order jsonb;
ALTER TABLE board_layouts ADD COLUMN mobile_order jsonb;

COMMENT ON COLUMN home_layouts.mobile_order IS '모바일 한 열 순서 — 가젯 ID 글자 목록, NULL = PC 배치에서 계산(home-news-contract 「모바일 순서」)';
COMMENT ON COLUMN gadget_page_layouts.mobile_order IS '모바일 한 열 순서 — 가젯 ID 글자 목록, NULL = PC 배치에서 계산(home-news-contract 「모바일 순서」)';
COMMENT ON COLUMN board_layouts.mobile_order IS '모바일 한 열 순서 — 가젯 ID 글자 목록, NULL = PC 배치에서 계산(home-news-contract 「모바일 순서」)';

DO $mobile$
DECLARE
    v_items     jsonb;
    v_order     jsonb;
    v_news      text;
    v_pick_m    text;
    v_post_m    text;
    v_popular   text;
    v_ad        text;
    v_groups    text;
    v_gallery   text;
    v_gal_x     int;
    v_gal_y     int;
    v_gal_w     int;
    v_gal_h     int;
    v_strip     text;
    v_top       text;
    v_top_from  int;
    v_cards     text[];
    v_ids       text[];
    v_out       text[] := ARRAY[]::text[];
    v_id        text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM installation i WHERE i.id = 1 AND i.status = 'COMPLETED' AND i.board_preset_applied_at IS NOT NULL) THEN
        RETURN;
    END IF;
    SELECT l.items, l.mobile_order INTO v_items, v_order FROM home_layouts l WHERE l.theme = 'irobit';
    IF NOT FOUND OR v_order IS NOT NULL OR jsonb_typeof(v_items) <> 'array' THEN
        RETURN;
    END IF;

    -- V212 모양 — 새소식 · 모바일 전용 하이라이트 · 모바일 전용 최신글
    SELECT g.id::text INTO v_news
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'POST_LIST' AND g.mobile
       AND g.config -> 'boardIds' = '["news"]'::jsonb
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int LIMIT 1;
    SELECT g.id::text INTO v_pick_m
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'HIGHLIGHT' AND NOT g.desktop AND g.mobile
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int LIMIT 1;
    SELECT g.id::text INTO v_post_m
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'POST_LIST' AND NOT g.desktop AND g.mobile
       AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST' AND coalesce(g.config -> 'boardIds', '[]'::jsonb) <> '["news"]'::jsonb
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int LIMIT 1;
    IF v_news IS NULL OR v_pick_m IS NULL OR v_post_m IS NULL THEN
        RETURN;
    END IF;

    -- 오른쪽 열 — 인기글 · 광고
    SELECT g.id::text INTO v_popular
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'POST_LIST' AND g.mobile
       AND g.config ->> 'sort' = 'POPULAR' AND (it ->> 'x')::int >= 18
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int LIMIT 1;
    SELECT g.id::text INTO v_ad
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'AD' AND g.mobile AND (it ->> 'x')::int >= 18
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int LIMIT 1;
    SELECT g.id::text INTO v_groups
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'GROUPS' AND g.mobile
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int LIMIT 1;

    -- 본문 열 — 영상·짤과 그 바로 아래 띠 배너
    SELECT g.id::text, (it ->> 'x')::int, (it ->> 'y')::int, (it ->> 'w')::int, (it ->> 'h')::int INTO v_gallery, v_gal_x, v_gal_y, v_gal_w, v_gal_h
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'GALLERY' AND g.mobile AND (it ->> 'x')::int < 18
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int LIMIT 1;
    IF v_gallery IS NOT NULL THEN
        SELECT g.id::text INTO v_strip
          FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
         WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'AD' AND g.mobile
           AND (it ->> 'x')::int = v_gal_x AND (it ->> 'w')::int = v_gal_w AND (it ->> 'y')::int = v_gal_y + v_gal_h
         LIMIT 1;
    END IF;

    -- 상단 띠 배너 — 첫 줄(새소식 · 모바일 전용 둘) 아래 끝 이후의 x0 · 폭 18 AD 중 맨 위(영상·짤보다 위 · 띠 배너가 아닌 것)
    SELECT max((it ->> 'y')::int + (it ->> 'h')::int) INTO v_top_from
      FROM jsonb_array_elements(v_items) it
     WHERE it ->> 'gadgetId' IN (v_news, v_pick_m, v_post_m);
    SELECT g.id::text INTO v_top
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'AD' AND g.mobile
       AND (it ->> 'x')::int = 0 AND (it ->> 'w')::int = 18 AND (it ->> 'y')::int >= v_top_from
       AND (v_gallery IS NULL OR (it ->> 'y')::int < v_gal_y)
       AND (v_strip IS NULL OR g.id::text <> v_strip)
     ORDER BY (it ->> 'y')::int LIMIT 1;

    -- 팁과강좌 · 사용기 — 본문 열 게시판 카드(위 → 왼쪽)
    SELECT coalesce(array_agg(g.id::text ORDER BY (it ->> 'y')::int, (it ->> 'x')::int), ARRAY[]::text[]) INTO v_cards
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'BOARD_CARDS' AND g.mobile AND (it ->> 'x')::int < 18;

    -- 차례대로 — 못 찾은 것(NULL)·겹친 것은 뺀다
    v_ids := ARRAY[v_news, v_top, v_popular, v_pick_m, v_post_m, v_ad] || v_cards || ARRAY[v_groups, v_gallery, v_strip];
    FOREACH v_id IN ARRAY v_ids
    LOOP
        IF v_id IS NOT NULL AND NOT (v_id = ANY (v_out)) THEN
            v_out := v_out || v_id;
        END IF;
    END LOOP;

    UPDATE home_layouts SET mobile_order = to_jsonb(v_out), updated_at = now() WHERE theme = 'irobit';
END
$mobile$;
