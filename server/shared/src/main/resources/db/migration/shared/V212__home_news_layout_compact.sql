-- 아이로빗 홈 첫 줄 배치 바로잡기 — V211 이 넣은 자리는 모바일 한 열 순서를 맞추려고 사각형이 서로 겹쳤다(home-news-contract 「세션 정리 결정」 2026-11-15).
-- 관리자 저장(GadgetLayoutWriter)은 자리가 겹치면 400 으로 막고 저장 때 위로 당겨 정리하므로, 운영자가 아이로빗 홈을 저장할 수 없었다(데모 광고 가젯 배치도 실패).
-- 겹치지 않는 자리로 다시 놓는다 — 모바일 전용 핫 아이로 픽·최신글을 PC 두 가젯 바로 아래 같은 행에 나란히(PC 에서는 숨어 자리가 없다):
--   새소식 x0 y0 w18 h12 · 인기글(오른쪽) → 핫 아이로 픽(PC) x0 y12 w9 h8 · 최신글(PC) x9 y12 w9 h8 · 핫 아이로 픽(모바일) x0 y20 w9 h8 · 최신글(모바일) x9 y20 w9
--   → 첫 줄 아래 띠 배너 → 나머지 차례 그대로. 모바일 한 열(옆 열 끼워 넣기) = 새소식 → 인기글 → 핫 아이로 픽 → 최신글 → 광고 → 인기 소모임 → 띠 배너 → ….
--   코드 기본 배치 HomeLayoutDefaults.irobit() 와 같은 자리.
-- 이미 설치된 DB 에서, 아이로빗 홈 배치에 겹치는 자리가 있고 V211 의 모양(뉴스 새소식 · PC 전용 하이라이트·최신글 · 모바일 전용 하이라이트)일 때만 — 운영자가 저장한 배치는 겹치지 않으므로 손대지 않는다.
-- 자리는 차례 키를 정한 뒤 관리자 저장과 같은 「위로 당겨 정리」(읽는 차례 (y, x, ID) — 열이 겹치는 앞 가젯 아래 끝으로)로 정한다.

DO $fix$
DECLARE
    v_items   jsonb;
    v_new     jsonb := '[]'::jsonb;
    v_out     jsonb := '[]'::jsonb;
    v_it      jsonb;
    v_gid     text;
    v_y       int;
    v_news    text;
    v_pick_pc text;
    v_post_pc text;
    v_pick_m  text;
    v_post_m  text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM installation i WHERE i.id = 1 AND i.status = 'COMPLETED' AND i.board_preset_applied_at IS NOT NULL) THEN
        RETURN;
    END IF;
    SELECT l.items INTO v_items FROM home_layouts l WHERE l.theme = 'irobit';
    IF NOT FOUND OR jsonb_typeof(v_items) <> 'array' THEN
        RETURN;
    END IF;

    -- 겹치는 자리가 없으면 그대로(운영자가 저장한 배치)
    IF NOT EXISTS (
        SELECT 1
          FROM jsonb_array_elements(v_items) WITH ORDINALITY a(v, i)
          JOIN jsonb_array_elements(v_items) WITH ORDINALITY b(v, j) ON a.i < b.j
         WHERE (a.v ->> 'x')::int < (b.v ->> 'x')::int + (b.v ->> 'w')::int AND (b.v ->> 'x')::int < (a.v ->> 'x')::int + (a.v ->> 'w')::int
           AND (a.v ->> 'y')::int < (b.v ->> 'y')::int + (b.v ->> 'h')::int AND (b.v ->> 'y')::int < (a.v ->> 'y')::int + (a.v ->> 'h')::int
    ) THEN
        RETURN;
    END IF;

    -- V211 의 가젯들(배치에 놓인 것 중)
    SELECT g.id::text INTO v_news FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'POST_LIST' AND g.config -> 'boardIds' = '["news"]'::jsonb LIMIT 1;
    SELECT g.id::text INTO v_pick_pc FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'HIGHLIGHT' AND g.desktop AND NOT g.mobile LIMIT 1;
    SELECT g.id::text INTO v_pick_m FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'HIGHLIGHT' AND NOT g.desktop AND g.mobile LIMIT 1;
    SELECT g.id::text INTO v_post_pc FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'POST_LIST' AND g.desktop AND NOT g.mobile AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST' LIMIT 1;
    SELECT g.id::text INTO v_post_m FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'POST_LIST' AND NOT g.desktop AND g.mobile AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST'
       AND coalesce(g.config -> 'boardIds', '[]'::jsonb) <> '["news"]'::jsonb LIMIT 1;
    IF v_news IS NULL OR v_pick_pc IS NULL OR v_pick_m IS NULL OR v_post_pc IS NULL THEN
        RETURN;
    END IF;

    -- 차례 키
    FOR v_it IN SELECT value FROM jsonb_array_elements(v_items)
    LOOP
        v_gid := v_it ->> 'gadgetId';
        IF v_gid = v_pick_pc THEN
            v_it := jsonb_build_object('gadgetId', v_gid, 'x', 0, 'y', 12, 'w', 9, 'h', 8);
        ELSIF v_gid = v_post_pc THEN
            v_it := jsonb_build_object('gadgetId', v_gid, 'x', 9, 'y', 12, 'w', 9, 'h', 8);
        ELSIF v_gid = v_pick_m THEN
            v_it := jsonb_build_object('gadgetId', v_gid, 'x', 0, 'y', 13, 'w', 9, 'h', 8);
        ELSIF v_post_m IS NOT NULL AND v_gid = v_post_m THEN
            v_it := jsonb_build_object('gadgetId', v_gid, 'x', 9, 'y', 13, 'w', 9, 'h', greatest(coalesce((v_it ->> 'h')::int, 9), 8));
        ELSIF (v_it ->> 'x')::int = 0 AND (v_it ->> 'w')::int = 18 AND (v_it ->> 'y')::int = 12
              AND EXISTS (SELECT 1 FROM home_gadgets g WHERE g.id::text = v_gid AND g.type = 'AD') THEN
            -- 첫 줄 아래 띠 배너 — 모바일 전용 둘 뒤로
            v_it := jsonb_set(v_it, '{y}', to_jsonb(14));
        END IF;
        v_new := v_new || jsonb_build_array(v_it);
    END LOOP;

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
$fix$;
