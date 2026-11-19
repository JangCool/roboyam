-- 아이로빗 홈 — 하이라이트를 「아추!」로 · 최신글(PC) 13개 · 모바일 순서 인기글 → 최신글 → 아추!(docs/features/mobile-home-list-contract.md · achu-page-contract.md, 사용자 2026-11-19)
-- 이미 설치된 DB 에 새 설치(설치 프리셋 irobit.structure.json · 코드 기본 배치 HomeLayoutDefaults.irobit())와 같은 값을 넣는다. 운영자가 고친 값은 건드리지 않는다(옛 기본값 그대로일 때만).
--
-- ① 아이로빗 홈(home_gadgets theme irobit · 가젯 페이지 아님)의 하이라이트(HIGHLIGHT) 중 제목이 「함께 보는 글」인 것 → 제목 「아추!」, 부제가 비어 있으면 「아이로빗 추천」.
-- ② 최신글(PC 전용 — POST_LIST · 정렬 LATEST · PC 보임 · 모바일 숨김)의 글 수가 15(옛 기본값)이면 13.
-- ③ 모바일 순서(home_layouts.mobile_order)에서 모바일 전용 하이라이트 바로 뒤에 모바일 전용 최신글이 있으면 둘의 자리를 바꾼다(인기글 → 최신글 → 아추!).
-- 서버는 이 마이그레이션 뒤에 뜨므로 홈 배치 캐시는 비어 있다(따로 비울 것 없음). 다시 돌아도 그대로다.
DO $home$
DECLARE
    v_pick    text;
    v_latest  text;
    v_order   jsonb;
    v_i       int;
    v_next    jsonb := '[]'::jsonb;
BEGIN
    -- ── ① 하이라이트 → 아추! ─────────────────────────────────────────────────────
    UPDATE home_gadgets
       SET title = '아추!',
           style = CASE WHEN coalesce(style ->> 'subtitle', '') = '' THEN jsonb_set(coalesce(style, '{}'::jsonb), '{subtitle}', '"아이로빗 추천"') ELSE style END
     WHERE theme = 'irobit' AND page_id IS NULL AND type = 'HIGHLIGHT' AND title = '함께 보는 글';

    -- ── ② 최신글(PC) 15 → 13 ────────────────────────────────────────────────────
    UPDATE home_gadgets
       SET config = jsonb_set(config, '{count}', '13')
     WHERE theme = 'irobit' AND page_id IS NULL AND type = 'POST_LIST' AND desktop AND NOT mobile
       AND config ->> 'sort' = 'LATEST' AND config -> 'count' = '15'::jsonb;

    -- ── ③ 모바일 순서 — 아추!(모바일 전용) ↔ 최신글(모바일 전용) ─────────────────
    SELECT id::text INTO v_pick FROM home_gadgets
     WHERE theme = 'irobit' AND page_id IS NULL AND type = 'HIGHLIGHT' AND NOT desktop AND mobile ORDER BY id LIMIT 1;
    SELECT id::text INTO v_latest FROM home_gadgets
     WHERE theme = 'irobit' AND page_id IS NULL AND type = 'POST_LIST' AND NOT desktop AND mobile AND config ->> 'sort' = 'LATEST' ORDER BY id LIMIT 1;
    SELECT mobile_order INTO v_order FROM home_layouts WHERE theme = 'irobit';
    IF v_pick IS NULL OR v_latest IS NULL OR v_order IS NULL OR jsonb_typeof(v_order) <> 'array' THEN
        RETURN;
    END IF;
    FOR v_i IN 0 .. jsonb_array_length(v_order) - 2 LOOP
        IF v_order ->> v_i = v_pick AND v_order ->> (v_i + 1) = v_latest THEN
            FOR v_i2 IN 0 .. jsonb_array_length(v_order) - 1 LOOP
                v_next := v_next || CASE WHEN v_i2 = v_i THEN to_jsonb(v_latest) WHEN v_i2 = v_i + 1 THEN to_jsonb(v_pick) ELSE v_order -> v_i2 END;
            END LOOP;
            UPDATE home_layouts SET mobile_order = v_next, updated_at = now() WHERE theme = 'irobit';
            RETURN;
        END IF;
    END LOOP;
END
$home$;
