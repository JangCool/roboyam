-- 아이로빗 홈 「상단 띠 배너」(반 폭 x0 w9 — V218)를 반 폭 배너(HALF_BANNER 600×100 · 6:1 — V219)로(사용자 2026-11-15 — 띠 배너 12:1 은 반 폭 약 430px 에서 높이 36px 라 너무 낮다).
-- docs/features/home-news-contract.md 「PC 첫 줄 아래 — 핫딜」 · ad-contract 「반 폭 배너」. 코드 기본 배치 HomeLayoutDefaults.irobit() 와 같다.
--   · 그 광고 가젯 config.adType STRIP_BANNER → HALF_BANNER, 배치 높이 h2 → h3(반 폭에서 약 72px)
--   · 최신글(PC 전용 · 두 줄 모양 — V218) h18 → h19(왼쪽 열이 한 행 길어진 만큼 — 화면은 격자가 늘려 맞춘다)
-- 대상: 아이로빗 홈 배치에서 x0 · w9 · h2 자리의 STRIP_BANNER 광고 가젯이 있을 때만(V218 이 만든 모양 그대로) — 운영자가 바꿨으면 손대지 않는다.
-- 자리는 관리자 저장과 같은 「위로 당겨 정리」. 서버는 이 마이그레이션 뒤에 뜨므로 배치·가젯 캐시는 비어 있다.

DO $v220$
DECLARE
    v_items  jsonb;
    v_new    jsonb := '[]'::jsonb;
    v_out    jsonb := '[]'::jsonb;
    v_it     jsonb;
    v_y      int;
    v_strip  text;
    v_latest text;
BEGIN
    SELECT l.items INTO v_items FROM home_layouts l WHERE l.theme = 'irobit';
    IF NOT FOUND OR jsonb_typeof(v_items) <> 'array' THEN
        RETURN;
    END IF;
    SELECT it ->> 'gadgetId' INTO v_strip FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'AD' AND g.config ->> 'adType' = 'STRIP_BANNER'
       AND (it ->> 'x')::int = 0 AND (it ->> 'w')::int = 9 AND (it ->> 'h')::int = 2
     LIMIT 1;
    IF v_strip IS NULL THEN
        RETURN;
    END IF;
    SELECT it ->> 'gadgetId' INTO v_latest FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.type = 'POST_LIST' AND g.desktop AND NOT g.mobile AND g.config ->> 'rowStyle' = 'HEADLINE'
       AND (it ->> 'x')::int = 9 AND (it ->> 'w')::int = 9 AND (it ->> 'h')::int = 18
     LIMIT 1;

    UPDATE home_gadgets SET config = config || '{"adType": "HALF_BANNER"}'::jsonb WHERE id::text = v_strip;

    FOR v_it IN SELECT value FROM jsonb_array_elements(v_items)
    LOOP
        IF v_it ->> 'gadgetId' = v_strip THEN
            v_it := jsonb_set(v_it, '{h}', to_jsonb(3));
        ELSIF v_latest IS NOT NULL AND v_it ->> 'gadgetId' = v_latest THEN
            v_it := jsonb_set(v_it, '{h}', to_jsonb(19));
        END IF;
        v_new := v_new || jsonb_build_array(v_it);
    END LOOP;

    -- 위로 당겨 정리(높이가 늘어 겹치는 아래 가젯은 밀린다)
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
$v220$;
