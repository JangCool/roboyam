-- 포인트 게임 홈 가젯을 이미 설치된 사이트에도 넣는다(사용자 2026-11-16 — V235 는 게임 표·설정만 만들고, 가젯은 설치 구조 파일에만 있어 배포만 한 사이트 홈에는 없었다).
-- 아이로빗 테마 홈(page_id NULL)에 포인트 게임 가젯이 아직 없을 때만 만든다 — 운영자가 이미 만들었거나 지운 뒤 다시 만든 가젯은 건드리지 않는다.
-- 홈 배치: 지금 배치가 옛 기본 배치(V235 앞 설치 구조 파일) 그대로면 새 기본 배치(포인트 게임 오른쪽 열 · 인기 소모임 한 칸 아래)로 바꾸고,
--          운영자가 바꾼 배치면 맨 아래 왼쪽에 덧붙이기만 한다. 가젯을 이 마이그레이션이 만들었을 때만 배치를 손댄다. 값은 설치 구조 파일에서 그대로 옮겼다.
DO $$
DECLARE
    v_gadget  bigint := 115737624580190209;
    v_items   jsonb;
    v_bottom  int;
BEGIN
    IF EXISTS (SELECT 1 FROM home_gadgets WHERE theme = 'irobit' AND type = 'POINT_GAMES' AND page_id IS NULL)
       OR EXISTS (SELECT 1 FROM home_gadgets WHERE id = v_gadget) THEN
        RETURN;
    END IF;
    -- 아이로빗 테마 홈 배치가 없는 사이트(테마를 쓰지 않음)는 가젯도 만들지 않는다
    SELECT items INTO v_items FROM home_layouts WHERE theme = 'irobit';
    IF NOT FOUND THEN
        RETURN;
    END IF;

    INSERT INTO home_gadgets (id, theme, type, title, desktop, mobile, style, config, more, created_at, page_id, board_layout)
    VALUES (v_gadget, 'irobit', 'POINT_GAMES', '포인트 게임', true, true, '{"icon":"pi-gift","empty":{"keep":true,"text":"","message":true},"cardLook":"DEFAULT","subtitle":"","cardColor":null,"titleSize":"M","mobileFold":"NONE","titleColor":null,"headerStyle":"PLAIN"}'::jsonb, '{}'::jsonb, NULL, now(), NULL, false);

    IF v_items = '[{"h":12,"w":18,"x":0,"y":0,"gadgetId":"115134224360534032"},{"h":12,"w":6,"x":18,"y":0,"gadgetId":"109141180830191617"},{"h":8,"w":9,"x":0,"y":12,"gadgetId":"110040215887810560"},{"h":19,"w":9,"x":9,"y":12,"gadgetId":"115134224360534033"},{"h":5,"w":6,"x":18,"y":12,"gadgetId":"112052581106192384"},{"h":7,"w":6,"x":18,"y":17,"gadgetId":"109141180838580225"},{"h":3,"w":9,"x":0,"y":20,"gadgetId":"112054351140229120"},{"h":8,"w":9,"x":0,"y":23,"gadgetId":"115288976000020481"},{"h":3,"w":18,"x":0,"y":31,"gadgetId":"112052581106192386"},{"h":8,"w":9,"x":0,"y":34,"gadgetId":"111996527412514816"},{"h":8,"w":9,"x":9,"y":34,"gadgetId":"111996527416709120"},{"h":8,"w":18,"x":0,"y":42,"gadgetId":"109141180830191619"},{"h":8,"w":9,"x":0,"y":50,"gadgetId":"115134224360534034"},{"h":13,"w":7,"x":17,"y":50,"gadgetId":"109141180838580224"},{"h":9,"w":9,"x":9,"y":63,"gadgetId":"109141180830191620"}]'::jsonb THEN
        UPDATE home_layouts SET items = '[{"h":12,"w":18,"x":0,"y":0,"gadgetId":"115134224360534032"},{"h":12,"w":6,"x":18,"y":0,"gadgetId":"109141180830191617"},{"h":8,"w":9,"x":0,"y":12,"gadgetId":"110040215887810560"},{"h":19,"w":9,"x":9,"y":12,"gadgetId":"115134224360534033"},{"h":5,"w":6,"x":18,"y":12,"gadgetId":"112052581106192384"},{"h":6,"w":6,"x":18,"y":17,"gadgetId":"115737624580190209"},{"h":3,"w":9,"x":0,"y":20,"gadgetId":"112054351140229120"},{"h":8,"w":9,"x":0,"y":23,"gadgetId":"115288976000020481"},{"h":7,"w":6,"x":18,"y":23,"gadgetId":"109141180838580225"},{"h":3,"w":18,"x":0,"y":31,"gadgetId":"112052581106192386"},{"h":8,"w":9,"x":0,"y":34,"gadgetId":"111996527412514816"},{"h":8,"w":9,"x":9,"y":34,"gadgetId":"111996527416709120"},{"h":8,"w":18,"x":0,"y":42,"gadgetId":"109141180830191619"},{"h":8,"w":9,"x":0,"y":50,"gadgetId":"115134224360534034"},{"h":13,"w":7,"x":17,"y":50,"gadgetId":"109141180838580224"},{"h":9,"w":9,"x":9,"y":63,"gadgetId":"109141180830191620"}]'::jsonb, updated_at = now() WHERE theme = 'irobit';
    ELSE
        SELECT COALESCE(MAX((x->>'y')::int + (x->>'h')::int), 0) INTO v_bottom FROM jsonb_array_elements(v_items) x;
        UPDATE home_layouts
           SET items = v_items || jsonb_build_array(jsonb_build_object('h', 6, 'w', 6, 'x', 0, 'y', v_bottom, 'gadgetId', v_gadget::text)),
               updated_at = now()
         WHERE theme = 'irobit';
    END IF;
END $$;
