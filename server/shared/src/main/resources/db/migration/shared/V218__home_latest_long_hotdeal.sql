-- 아이로빗 PC 홈 첫 줄 아래 다시 놓기(docs/features/home-news-contract.md 「PC 첫 줄 아래 — 핫딜」, 사용자 2026-11-15). 코드 기본 배치 HomeLayoutDefaults.irobit() 와 같다.
--   · 최신글(PC 전용)을 아래로 길게(h8 → h18 — 왼쪽 핫딜 끝까지, 화면은 격자 「같은 줄 맞추기」가 맞춘다) + 모양을 모바일 최신글과 같은 두 줄(HEADLINE · 사진 없음 · 한 열 · 15개 · 줄 간격 좁게 · 줄 정보 게시판·댓글·시간·추천)
--   · 상단 띠 배너를 반 폭(w18 → w9, 같은 자리 — 핫 아이로 픽 바로 아래, 높이 h2 — 반 폭 띠 배너 그림 높이에 맞게)
--   · 새 가젯 「핫딜」(HOT_DEALS · PC 전용 · 최신순 · 5개 · 사진 보임)을 원래 팁과강좌 자리(x0 · w9 · h8)에
--   · 영상·짤 바로 아래 띠 배너(본문 전체 폭)를 핫딜·최신글 아래 · 팁과강좌·사용기 위로
--   · 팁과강좌·사용기는 나란한 모양 그대로 그 아래로, 그 밖의 아래 가젯도 차례 그대로 따라 내려간다
--   · 모바일 전용 핫 아이로 픽·최신글은 PC 에서 숨어 자리가 없으니 배치표 맨 아래로(늘린 최신글과 자리가 겹치지 않게 — 겹치면 관리자 저장이 400). 모바일 화면 차례는 mobile_order 그대로.
-- 대상: 설치가 끝난 DB 의 아이로빗 홈 배치가 지금 기본 모양일 때만 — 핫 아이로 픽(PC 전용) x0 y12 w9 h8 · 최신글(PC 전용, 정렬 최신, 뉴스 아님, 줄 모양 COMPACT) x9 y12 w9 h8 ·
--   팁과강좌(BOARD_CARDS ["tip"]) x0 w9 와 사용기(["review"]) x9 w9 가 같은 행 · 배치에 핫딜 가젯이 아직 없음. 운영자가 바꾼 배치는 손대지 않는다.
-- 자리는 관리자 저장과 같은 「위로 당겨 정리」(읽는 차례 (y, x, ID) — 열이 겹치는 앞 가젯 아래 끝으로)로 정한다. 서버는 이 마이그레이션 뒤에 뜨므로 배치·가젯 캐시는 비어 있다.

DO $v218$
DECLARE
    v_items   jsonb;
    v_new     jsonb := '[]'::jsonb;
    v_out     jsonb := '[]'::jsonb;
    v_it      jsonb;
    v_gid     text;
    v_y       int;
    v_pick_pc text;
    v_post_pc text;
    v_pick_m  text;
    v_post_m  text;
    v_tips    text;
    v_reviews text;
    v_strip   text;
    v_tips_y  int;
    v_deal_id bigint;
    v_bottom  text;
    v_seq     bigint := 0;
    v_base    bigint := ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12);
    v_style   jsonb := '{"icon": "pi-ticket", "empty": {"keep": true, "text": "", "message": true}, "cardLook": "DEFAULT", "subtitle": "", "cardColor": null, "titleSize": "M", "mobileFold": "NONE", "titleColor": null, "headerStyle": "PLAIN"}'::jsonb;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM installation i WHERE i.id = 1 AND i.status = 'COMPLETED' AND i.board_preset_applied_at IS NOT NULL) THEN
        RETURN;
    END IF;
    SELECT l.items INTO v_items FROM home_layouts l WHERE l.theme = 'irobit';
    IF NOT FOUND OR jsonb_typeof(v_items) <> 'array' THEN
        RETURN;
    END IF;

    -- 이미 핫딜 가젯이 놓였으면 그대로
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId' WHERE g.type = 'HOT_DEALS') THEN
        RETURN;
    END IF;

    SELECT g.id::text INTO v_pick_pc FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'HIGHLIGHT' AND g.desktop AND NOT g.mobile
       AND (it ->> 'x')::int = 0 AND (it ->> 'y')::int = 12 AND (it ->> 'w')::int = 9 AND (it ->> 'h')::int = 8
     LIMIT 1;
    SELECT g.id::text INTO v_post_pc FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'POST_LIST' AND g.desktop AND NOT g.mobile
       AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST' AND coalesce(g.config -> 'boardIds', '[]'::jsonb) <> '["news"]'::jsonb
       AND g.config ->> 'rowStyle' = 'COMPACT'
       AND (it ->> 'x')::int = 9 AND (it ->> 'y')::int = 12 AND (it ->> 'w')::int = 9 AND (it ->> 'h')::int = 8
     LIMIT 1;
    SELECT g.id::text, (it ->> 'y')::int INTO v_tips, v_tips_y FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'BOARD_CARDS' AND g.config -> 'boardIds' = '["tip"]'::jsonb AND (it ->> 'x')::int = 0 AND (it ->> 'w')::int = 9
     LIMIT 1;
    SELECT g.id::text INTO v_reviews FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'BOARD_CARDS' AND g.config -> 'boardIds' = '["review"]'::jsonb AND (it ->> 'x')::int = 9 AND (it ->> 'w')::int = 9
       AND (it ->> 'y')::int = v_tips_y
     LIMIT 1;
    IF v_pick_pc IS NULL OR v_post_pc IS NULL OR v_tips IS NULL OR v_reviews IS NULL THEN
        RETURN;
    END IF;
    -- 팁과강좌 위의 첫 띠 배너(본문 전체 폭)
    SELECT it ->> 'gadgetId' INTO v_strip FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.type = 'AD' AND g.config ->> 'adType' = 'STRIP_BANNER' AND (it ->> 'x')::int = 0 AND (it ->> 'w')::int = 18 AND (it ->> 'y')::int < v_tips_y
     ORDER BY (it ->> 'y')::int DESC
     LIMIT 1;
    -- 영상·짤 바로 아래 띠 배너(같은 x · 폭)
    SELECT si.value ->> 'gadgetId' INTO v_bottom
      FROM jsonb_array_elements(v_items) gi
      JOIN home_gadgets gg ON gg.id::text = gi.value ->> 'gadgetId' AND gg.theme = 'irobit' AND gg.type = 'GALLERY'
      JOIN jsonb_array_elements(v_items) si ON (si.value ->> 'y')::int = (gi.value ->> 'y')::int + (gi.value ->> 'h')::int
                                            AND (si.value ->> 'x')::int = (gi.value ->> 'x')::int AND (si.value ->> 'w')::int = (gi.value ->> 'w')::int
      JOIN home_gadgets sg ON sg.id::text = si.value ->> 'gadgetId' AND sg.type = 'AD' AND sg.config ->> 'adType' = 'STRIP_BANNER'
     LIMIT 1;
    SELECT g.id::text INTO v_pick_m FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'HIGHLIGHT' AND NOT g.desktop AND g.mobile LIMIT 1;
    SELECT g.id::text INTO v_post_m FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'POST_LIST' AND NOT g.desktop AND g.mobile AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST'
       AND coalesce(g.config -> 'boardIds', '[]'::jsonb) <> '["news"]'::jsonb LIMIT 1;

    -- 최신글(PC) 모양 — 모바일 최신글과 같은 두 줄(대상 게시판·뺄 게시판·꾸미기는 그대로)
    UPDATE home_gadgets
       SET config = config || '{"rowStyle": "HEADLINE", "thumbnail": "NONE", "titleLines": 1, "columns": 1, "count": 15, "dividers": false, "likes": "ICON", "rowSpacing": "TIGHT", "meta": ["BOARD", "COMMENTS", "TIME", "LIKES"]}'::jsonb
     WHERE id::text = v_post_pc;

    -- 핫딜 가젯
    LOOP
        v_seq := v_seq + 1;
        v_deal_id := v_base | v_seq;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM home_gadgets WHERE id = v_deal_id);
    END LOOP;
    INSERT INTO home_gadgets (id, theme, type, title, desktop, mobile, style, config, more, created_at)
    VALUES (v_deal_id, 'irobit', 'HOT_DEALS', '핫딜', true, false, v_style,
            '{"boards": [], "sort": "LATEST", "count": 5, "thumbnails": true}'::jsonb, NULL, now());

    -- 차례 키
    FOR v_it IN SELECT value FROM jsonb_array_elements(v_items)
    LOOP
        v_gid := v_it ->> 'gadgetId';
        IF v_gid = v_post_pc THEN
            v_it := jsonb_build_object('gadgetId', v_gid, 'x', 9, 'y', 12, 'w', 9, 'h', 18);
        ELSIF v_strip IS NOT NULL AND v_gid = v_strip THEN
            v_it := jsonb_build_object('gadgetId', v_gid, 'x', 0, 'y', 20, 'w', 9, 'h', 2);
        ELSIF v_bottom IS NOT NULL AND v_gid = v_bottom THEN
            -- 팁과강좌·사용기 바로 위로(정리 차례가 그 둘보다 앞)
            v_it := jsonb_set(v_it, '{y}', to_jsonb(v_tips_y + 99));
        ELSIF v_gid = v_tips THEN
            v_it := jsonb_set(v_it, '{y}', to_jsonb(v_tips_y + 100));
        ELSIF v_gid = v_reviews THEN
            v_it := jsonb_set(v_it, '{y}', to_jsonb(v_tips_y + 100));
        ELSIF (v_pick_m IS NOT NULL AND v_gid = v_pick_m) OR (v_post_m IS NOT NULL AND v_gid = v_post_m) THEN
            v_it := jsonb_set(v_it, '{y}', to_jsonb(10000));
        ELSIF v_gid <> v_pick_pc AND (v_it ->> 'y')::int >= v_tips_y THEN
            -- 팁과강좌 아래 가젯은 차례 그대로 그 뒤로
            v_it := jsonb_set(v_it, '{y}', to_jsonb((v_it ->> 'y')::int + 100));
        END IF;
        v_new := v_new || jsonb_build_array(v_it);
    END LOOP;
    v_new := v_new || jsonb_build_array(jsonb_build_object('gadgetId', v_deal_id::text, 'x', 0, 'y', 22, 'w', 9, 'h', 8));

    -- 위로 당겨 정리
    FOR v_it IN SELECT value FROM jsonb_array_elements(v_new)
                 ORDER BY (value ->> 'y')::int, (value ->> 'x')::int, value ->> 'gadgetId'
    LOOP
        SELECT coalesce(max((p ->> 'y')::int + (p ->> 'h')::int), 0) INTO v_y
          FROM jsonb_array_elements(v_out) p
         WHERE (p ->> 'x')::int < (v_it ->> 'x')::int + (v_it ->> 'w')::int
           AND (v_it ->> 'x')::int < (p ->> 'x')::int + (p ->> 'w')::int;
        v_out := v_out || jsonb_build_array(jsonb_set(v_it, '{y}', to_jsonb(v_y)));
    END LOOP;
    UPDATE home_layouts SET items = v_out, updated_at = now() WHERE theme = 'irobit';
END
$v218$;
