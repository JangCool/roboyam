-- 아이로빗 홈 첫 줄 · 뉴스 게시판(docs/features/home-news-contract.md) — 이미 설치된 DB 에 새 설치(설치 프리셋 irobit.structure.json)와 같은 변경을 넣는다.
--
-- 모두 이미 설치된 DB 에서만(installation.status = COMPLETED 이고 board_preset_applied_at 이 있는 곳) — 빈 DB 는 설치 프리셋이 만든다
-- (빈 DB 에 게시판을 넣으면 설치 때 「게시판이 있음」으로 보고 기본 게시판을 건너뛴다 — V168 과 같은 이유). 다시 돌아도 그대로다(조건마다 「없을 때만」).
--
-- ① 뉴스 게시판 news — 아이로빗 프리셋으로 설치한 곳(board_preset = IROBIT)에서, 주소 news 나 이름 「뉴스」(대소문자 무시 — ux_boards_name)인 게시판이 없을 때만.
--    · 일반 게시판(GENERAL · 목록형), 영문 이름 News, 글쓴이 대표 썸네일 고르기 켬(author_thumbnail_enabled — 사용자 2026-11-15). 그 밖의 칸은 NULL = 게시판 기본 설정 따름
--      (권한·포인트·편집기 등 — 다른 일반 게시판과 같다). AI 회원 글은 막지 않는다(AiMemberRules — 일반 게시판이라 열림). 게시판 차례는 맨 뒤.
--    · 말머리: 공통 4개(질문·후기·잡담·공지[운영진 전용] — BoardCreator 와 같은 차례·이름, 모두 끔) + 고유 9개(차례 10부터, 모두 3자 이내 — 목록 말머리 칸 3자 고정):
--      AI(BLUE) · 로봇(TEAL) · IT(INDIGO) · 자동차(ORANGE) · 산업(GREEN) · 정책(SLATE) · 연구(PURPLE) · 투자(EMERALD) · 게임(PINK).
--    · 공개 메뉴 main 의 최상위 「최신글」(BUILTIN LATEST) 바로 다음에 게시판 항목(뒤 형제 차례를 1 씩 민다 · 최신글이 없으면 최상위 끝). 이 게시판을 만들었을 때만 넣고,
--      main 의 트리 버전을 1 올린다(관리자 저장의 MENU_VERSION_CONFLICT — 열려 있던 편집 화면이 옛 트리로 덮어쓰지 않게, V99 와 같다).
-- ② 섞여 있던 뉴스 말머리 끄기(active = false) — ai 게시판 「AI 뉴스·이슈」「로봇 뉴스·이슈」 · game 게시판 「게임 뉴스」(고유 말머리, 켜져 있는 것만). 이미 쓴 글은 옮기지 않는다.
-- ③ 아이로빗 홈(home_layouts theme irobit + 그 테마의 홈 가젯) — 하이라이트 가젯이 처음 자리(x0 · y0 · 폭 18) 그대로이고 뉴스 게시판이 있을 때만(운영자가 고쳤으면 손대지 않음):
--    · 새 가젯: 새소식(뉴스 게시판 글 목록 · LINE · 첫 글 작은 사진 THUMB · 두 열 · 17개 · 모바일 3쪽×5) · 최신글(PC 전용 · COMPACT · 추천 LEAD · 12개)
--      · 핫 아이로 픽(모바일 전용 하이라이트 — 기존 하이라이트와 같은 설정, 좁은 칸 DEFAULT).
--    · 기존 하이라이트 → 「핫 아이로 픽」 PC 전용 · 5개 · 좁은 칸 LIST · 카드 아래 여백 TIGHT, 자리 x0 y1 폭 9.
--    · 기존 최신글(정렬 최신 · 배치의 첫 것) → 모바일 전용 · 줄 정보에 추천(LIKES) · 추천 ICON · 줄 여백 TIGHT · 10개.
--    · 한 줄 광고(AD_TEXT_LINE) 가젯은 아이로빗 홈에서 지운다(배치·가젯 행). 영상·짤(GALLERY) 바로 위에 붙은 띠 배너(AD)가 있으면 둘의 위아래를 바꾼다.
--    · 자리 y 는 차례 값이다(공개 화면은 보이는 가젯만 위로 당겨 그린다) — 코드 기본 배치 HomeLayoutDefaults.irobit() 와 같은 자리: 새소식 x0 y0 w18 h12 ·
--      핫 아이로 픽(PC) x0 y1 w9 h12 · 최신글(PC) x9 y1 w9 h12 · 핫 아이로 픽(모바일) x0 y13 w18 h1. 나머지 가젯은 제자리.
--    · 꾸미기는 아이로빗 다른 가젯과 같은 저장 모양(아이콘 + 막대 없는 머리 + 카드)이다.
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — 마이그레이션용 노드, V43·V99·V168 과 같은 방식) + 순번(같은 표에 이미 있으면 건너뛴다).
-- 서버는 이 마이그레이션 뒤에 뜨므로 배치·메뉴·게시판 캐시는 비어 있다(따로 비울 것 없음).

DO $news$
DECLARE
    v_root     bigint;
    v_preset   varchar(32);
    v_base     bigint := ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12);
    v_seq      int := 0;
    v_now      timestamptz := now();
    v_board    bigint;
    v_order    int;
    v_id       bigint;
    v_prefix   record;
    v_latest   record;
    v_items    jsonb;
    v_new      jsonb := '[]'::jsonb;
    v_it       jsonb;
    v_gid      text;
    v_hl       record;
    v_post     record;
    v_gal      text;
    v_gal_h    int;
    v_strip    text;
    v_strip_y  int;
    v_news_id  bigint;
    v_pc_id    bigint;
    v_mob_id   bigint;
    v_style    jsonb := '{"icon": null, "empty": {"keep": true, "text": "", "message": true}, "cardLook": "DEFAULT", "subtitle": "", "cardColor": null, "titleSize": "M", "mobileFold": "NONE", "titleColor": null, "headerStyle": "PLAIN"}'::jsonb;
    v_meta0    jsonb := '["BOARD", "PREFIX", "COMMENTS", "TIME"]'::jsonb;
BEGIN
    SELECT o.id, i.board_preset INTO v_root, v_preset
      FROM installation i LEFT JOIN operators o ON o.id = i.root_operator_id
     WHERE i.id = 1 AND i.status = 'COMPLETED' AND i.board_preset_applied_at IS NOT NULL;
    IF NOT FOUND THEN
        RETURN; -- 설치 전·설치 프리셋을 아직 적용하지 않은 DB — 새 설치는 프리셋 적용이 만든다
    END IF;

    -- ── ① 뉴스 게시판 · 말머리 · 메뉴 ──────────────────────────────────────────
    IF v_preset = 'IROBIT' AND NOT EXISTS (SELECT 1 FROM boards b WHERE b.slug = 'news' OR lower(b.name) = lower('뉴스')) THEN
        SELECT coalesce(max(b.sort_order), 0) + 1 INTO v_order FROM boards b;
        LOOP
            v_seq := v_seq + 1;
            v_board := v_base | v_seq;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM boards WHERE id = v_board);
        END LOOP;
        INSERT INTO boards (id, slug, name, name_en, description, kind, layout, color, allow_adult, investment_notice, active, sort_order,
                            gadget_exclusions, ads_hidden, secret_mode, status_enabled, author_mode, author_thumbnail_enabled,
                            created_at, created_by, updated_at, updated_by)
        VALUES (v_board, 'news', '뉴스', 'News', 'AI·로봇·IT·산업 소식을 모아 보는 게시판', 'GENERAL', 'LIST', NULL, false, false, true, v_order,
                '{}'::varchar[], false, 'OFF', false, 'NICKNAME', true,
                v_now, v_root, v_now, v_root);

        FOR v_prefix IN
            SELECT * FROM (VALUES (1, 'QUESTION', '질문', NULL), (2, 'REVIEW', '후기', NULL), (3, 'CHAT', '잡담', NULL), (4, 'NOTICE', '공지', NULL),
                                  (10, NULL, 'AI', 'BLUE'), (11, NULL, '로봇', 'TEAL'), (12, NULL, 'IT', 'INDIGO'), (13, NULL, '자동차', 'ORANGE'),
                                  (14, NULL, '산업', 'GREEN'), (15, NULL, '정책', 'SLATE'), (16, NULL, '연구', 'PURPLE'), (17, NULL, '투자', 'EMERALD'),
                                  (18, NULL, '게임', 'PINK')) AS p (ord, common_key, name, color)
            ORDER BY ord
        LOOP
            LOOP
                v_seq := v_seq + 1;
                v_id := v_base | v_seq;
                EXIT WHEN NOT EXISTS (SELECT 1 FROM board_prefixes WHERE id = v_id);
            END LOOP;
            INSERT INTO board_prefixes (id, board_id, common_key, name, staff_only, color, active, sort_order, created_at, updated_at)
            VALUES (v_id, v_board, v_prefix.common_key, v_prefix.name, coalesce(v_prefix.common_key = 'NOTICE', false), v_prefix.color,
                    v_prefix.common_key IS NULL, v_prefix.ord, v_now, v_now);
        END LOOP;

        IF EXISTS (SELECT 1 FROM menu_sets WHERE set_key = 'main') THEN
            SELECT n.id, n.sort_order INTO v_latest
              FROM menu_nodes n
             WHERE n.set_key = 'main' AND n.parent_id IS NULL AND n.kind = 'ITEM' AND n.target_type = 'BUILTIN' AND n.target_key = 'LATEST'
             ORDER BY n.sort_order, n.id
             LIMIT 1;
            IF FOUND THEN
                UPDATE menu_nodes SET sort_order = sort_order + 1
                 WHERE set_key = 'main' AND parent_id IS NULL AND sort_order > v_latest.sort_order;
                v_order := v_latest.sort_order + 1;
            ELSE
                SELECT coalesce(max(n.sort_order) + 1, 0) INTO v_order FROM menu_nodes n WHERE n.set_key = 'main' AND n.parent_id IS NULL;
            END IF;
            LOOP
                v_seq := v_seq + 1;
                v_id := v_base | v_seq;
                EXIT WHEN NOT EXISTS (SELECT 1 FROM menu_nodes WHERE id = v_id);
            END LOOP;
            INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, visible, audience, target_type, target_id, mobile_tab, divider, shortcut, bold)
            VALUES (v_id, 'main', NULL, 'ITEM', v_order, true, 'ALL', 'BOARD', v_board::text, false, 'NONE', false, false);
            UPDATE menu_sets SET version = version + 1, updated_at = v_now WHERE set_key = 'main';
        END IF;
    END IF;

    -- ── ② 섞여 있던 뉴스 말머리 끄기 ────────────────────────────────────────────
    UPDATE board_prefixes p
       SET active = false, updated_at = v_now
      FROM boards b
     WHERE b.id = p.board_id
       AND p.active
       AND p.common_key IS NULL
       AND ((b.slug = 'ai' AND p.name IN ('AI 뉴스·이슈', '로봇 뉴스·이슈')) OR (b.slug = 'game' AND p.name = '게임 뉴스'));

    -- ── ③ 아이로빗 홈 ───────────────────────────────────────────────────────────
    IF NOT EXISTS (SELECT 1 FROM boards WHERE slug = 'news') THEN
        RETURN;
    END IF;
    SELECT l.items INTO v_items FROM home_layouts l WHERE l.theme = 'irobit';
    IF NOT FOUND OR jsonb_typeof(v_items) <> 'array' THEN
        RETURN;
    END IF;

    -- 하이라이트가 처음 자리(x0 · y0 · 폭 18)일 때만
    SELECT g.id, g.style, g.config, g.more INTO v_hl
      FROM jsonb_array_elements(v_items) it
      JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'HIGHLIGHT'
       AND (it ->> 'x')::int = 0 AND (it ->> 'y')::int = 0 AND (it ->> 'w')::int = 18
     LIMIT 1;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    -- 기존 최신글 — 배치에 놓인 글 목록 중 정렬이 최신인 첫 것(없으면 PC 최신글은 전체 게시판 · /latest)
    SELECT g.id, g.style, g.config, g.more INTO v_post
      FROM jsonb_array_elements(v_items) it
      JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'POST_LIST'
       AND coalesce(g.config ->> 'sort', 'LATEST') = 'LATEST'
       AND coalesce(g.config -> 'boardIds', '[]'::jsonb) <> '["news"]'::jsonb
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int
     LIMIT 1;

    -- 영상·짤 바로 위에 붙은 띠 배너(같은 x · 폭) — 위아래를 바꾼다
    SELECT gi.value ->> 'gadgetId', (gi.value ->> 'h')::int, si.value ->> 'gadgetId', (si.value ->> 'y')::int
      INTO v_gal, v_gal_h, v_strip, v_strip_y
      FROM jsonb_array_elements(v_items) gi
      JOIN home_gadgets gg ON gg.id::text = gi.value ->> 'gadgetId' AND gg.theme = 'irobit' AND gg.type = 'GALLERY'
      JOIN jsonb_array_elements(v_items) si ON (si.value ->> 'y')::int + (si.value ->> 'h')::int = (gi.value ->> 'y')::int
                                            AND (si.value ->> 'x')::int = (gi.value ->> 'x')::int AND (si.value ->> 'w')::int = (gi.value ->> 'w')::int
      JOIN home_gadgets sg ON sg.id::text = si.value ->> 'gadgetId' AND sg.theme = 'irobit' AND sg.type = 'AD'
     ORDER BY (gi.value ->> 'y')::int DESC
     LIMIT 1;

    -- 새 가젯 ID 셋
    LOOP
        v_seq := v_seq + 1;
        v_news_id := v_base | v_seq;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM home_gadgets WHERE id = v_news_id);
    END LOOP;
    LOOP
        v_seq := v_seq + 1;
        v_pc_id := v_base | v_seq;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM home_gadgets WHERE id = v_pc_id);
    END LOOP;
    LOOP
        v_seq := v_seq + 1;
        v_mob_id := v_base | v_seq;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM home_gadgets WHERE id = v_mob_id);
    END LOOP;

    -- 새소식
    INSERT INTO home_gadgets (id, theme, type, title, desktop, mobile, style, config, more, created_at)
    VALUES (v_news_id, 'irobit', 'POST_LIST', '새소식', true, true, v_style || '{"icon": "pi-megaphone"}'::jsonb,
            '{"boardScope": "PICK", "boardIds": ["news"], "sort": "LATEST", "rowStyle": "LINE", "lead": "THUMB", "thumbnail": "NONE",
              "meta": ["PREFIX", "COMMENTS", "TIME"], "columns": 2, "titleLines": 1, "count": 17, "mobilePages": 3, "mobilePageSize": 5, "dividers": false}'::jsonb,
            '{"to": "/boards/news", "label": null}'::jsonb, v_now);

    -- 최신글(PC 전용) — 대상 게시판은 기존 최신글과 같게
    INSERT INTO home_gadgets (id, theme, type, title, desktop, mobile, style, config, more, created_at)
    VALUES (v_pc_id, 'irobit', 'POST_LIST', '최신글', true, false,
            coalesce(v_post.style, v_style || '{"icon": "pi-clock"}'::jsonb) || '{"bottomSpace": "TIGHT"}'::jsonb,
            '{"sort": "LATEST", "rowStyle": "COMPACT", "meta": ["BOARD", "COMMENTS", "LIKES"], "likes": "LEAD", "columns": 1, "count": 12, "dividers": false}'::jsonb
                || CASE WHEN v_post.config ? 'boardScope' THEN jsonb_build_object('boardScope', v_post.config -> 'boardScope') ELSE '{}'::jsonb END
                || CASE WHEN v_post.config ? 'boardIds' THEN jsonb_build_object('boardIds', v_post.config -> 'boardIds') ELSE '{}'::jsonb END,
            coalesce(v_post.more, '{"to": "/latest", "label": null}'::jsonb), v_now);

    -- 핫 아이로 픽(모바일 전용) — 기존 하이라이트와 같은 설정(바꾸기 전 값)
    INSERT INTO home_gadgets (id, theme, type, title, desktop, mobile, style, config, more, created_at)
    VALUES (v_mob_id, 'irobit', 'HIGHLIGHT', '핫 아이로 픽', false, true, v_hl.style, coalesce(v_hl.config, '{}'::jsonb) || '{"narrow": "DEFAULT"}'::jsonb,
            v_hl.more, v_now);

    -- 기존 하이라이트 → 핫 아이로 픽(PC 전용)
    UPDATE home_gadgets
       SET title = '핫 아이로 픽', desktop = true, mobile = false,
           config = config || '{"count": 5, "narrow": "LIST"}'::jsonb,
           style = style || '{"bottomSpace": "TIGHT"}'::jsonb
     WHERE id = v_hl.id;

    -- 기존 최신글 → 모바일 전용 · 추천 아이콘 · 줄 여백 좁게 · 10개
    IF v_post.id IS NOT NULL THEN
        UPDATE home_gadgets
           SET desktop = false, mobile = true,
               config = jsonb_set(config, '{meta}',
                                  CASE WHEN coalesce(config -> 'meta', v_meta0) ? 'LIKES' THEN coalesce(config -> 'meta', v_meta0)
                                       ELSE coalesce(config -> 'meta', v_meta0) || '["LIKES"]'::jsonb END)
                        || '{"likes": "ICON", "rowSpacing": "TIGHT", "count": 10}'::jsonb
         WHERE id = v_post.id;
    END IF;

    -- 배치 — 한 줄 광고 빼기 · 하이라이트 자리 · 영상·짤과 띠 배너 위아래 · 새 가젯 셋
    FOR v_it IN SELECT value FROM jsonb_array_elements(v_items)
    LOOP
        v_gid := v_it ->> 'gadgetId';
        CONTINUE WHEN EXISTS (SELECT 1 FROM home_gadgets g WHERE g.id::text = v_gid AND g.type = 'AD_TEXT_LINE');
        IF v_gid = v_hl.id::text THEN
            v_it := jsonb_build_object('gadgetId', v_gid, 'x', 0, 'y', 1, 'w', 9, 'h', 12);
        ELSIF v_gal IS NOT NULL AND v_gid = v_gal THEN
            v_it := jsonb_set(v_it, '{y}', to_jsonb(v_strip_y));
        ELSIF v_strip IS NOT NULL AND v_gid = v_strip THEN
            v_it := jsonb_set(v_it, '{y}', to_jsonb(v_strip_y + v_gal_h));
        END IF;
        v_new := v_new || jsonb_build_array(v_it);
    END LOOP;
    v_new := v_new
        || jsonb_build_array(jsonb_build_object('gadgetId', v_news_id::text, 'x', 0, 'y', 0, 'w', 18, 'h', 12))
        || jsonb_build_array(jsonb_build_object('gadgetId', v_pc_id::text, 'x', 9, 'y', 1, 'w', 9, 'h', 12))
        || jsonb_build_array(jsonb_build_object('gadgetId', v_mob_id::text, 'x', 0, 'y', 13, 'w', 18, 'h', 1));
    UPDATE home_layouts SET items = v_new, updated_at = v_now WHERE theme = 'irobit';

    DELETE FROM home_gadgets g
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'AD_TEXT_LINE';
END
$news$;
