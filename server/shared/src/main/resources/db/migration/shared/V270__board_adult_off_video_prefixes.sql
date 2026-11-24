-- 영상·짤 · 전방/후방 19금 해제, 영상·짤 말머리(영화·드라마 · 예능·방송 · 애니·만화), 커뮤니티의 전방/후방 가젯을 일반 갤러리로 (사용자 2026-11-24
-- 「영상짤은 19금 없어 · 전방 후방도 19금은 아니야 · 영상짤 말머리에 영화·드라마 같은 것 · 커뮤니티에 전방/후방 없네」).
-- 새 설치는 설치 프리셋(irobit.structure.json — 같은 날 다시 내보냄)이 같은 값을 넣는다. 이 파일은 이미 설치된 DB 를 맞춘다.
-- 이미 설치된 DB 에서만(installation.status = COMPLETED 이고 board_preset_applied_at 이 있는 곳). 다시 돌아도 그대로다(조건마다 「아직 아니면」).
-- ① boards video: allow_adult → false(19금 글 허용 끔). boards frontback: adult_only → false(성인 전용 — 핀·성인 확인 없이 모두에게), allow_adult → false, 설명이 설치 기본값이면 새 설명.
-- ② video 말머리: 같은 이름이 없을 때만 「영화·드라마」 · 「예능·방송」 · 「애니·만화」를 「기타」 앞(차례 19·20·21)에 넣고 「기타」를 22 로 민다.
-- ③ 커뮤니티 가젯 페이지의 「성인 글」 가젯(ADULT_POSTS, boards = [frontback]) → 갤러리(GALLERY, boardIds [frontback], 8개 — 영상·짤 칸과 같은 모양). 자리·제목·스타일·더보기는 그대로, 아이콘만 그림 모양으로.
-- 바꾼 것이 있으면 공개 메뉴 main 트리 버전을 1 올린다(게시판 노출 조건이 바뀜 — V259 와 같은 이유). ID 는 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V259 와 같은 방식) + 순번.
-- 서버는 이 마이그레이션 뒤에 뜨므로 게시판·메뉴·가젯 캐시는 비어 있다(따로 비울 것 없음).
DO $adult_off$
DECLARE
    v_base    bigint := ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12);
    v_seq     int := 0;
    v_id      bigint;
    v_video   bigint;
    v_changed boolean := false;
    v_n       int;
    v_name    text;
    v_order   int;
BEGIN
    PERFORM 1 FROM installation i WHERE i.id = 1 AND i.status = 'COMPLETED' AND i.board_preset_applied_at IS NOT NULL;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    -- ── ① 19금 해제 ───────────────────────────────────────────────────────────
    UPDATE boards SET allow_adult = false, updated_at = now() WHERE slug = 'video' AND allow_adult;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_changed := v_changed OR v_n > 0;
    UPDATE boards SET adult_only = false, allow_adult = false, updated_at = now() WHERE slug = 'frontback' AND (adult_only OR allow_adult);
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_changed := v_changed OR v_n > 0;
    -- 설명이 설치 기본값(「본인인증한 성인만 …」)이면 새 기본값으로 — 운영자가 고친 설명은 그대로
    UPDATE boards SET description = '주변을 살펴야 하는 짤·영상·썰을 나누는 게시판이에요. 공공장소에서는 주의해서 열어 주세요.', updated_at = now()
     WHERE slug = 'frontback' AND description = '본인인증한 성인만 볼 수 있는 게시판이에요.';

    -- ── ② 영상·짤 말머리 ──────────────────────────────────────────────────────
    SELECT id INTO v_video FROM boards WHERE slug = 'video';
    IF v_video IS NOT NULL THEN
        v_order := 19;
        FOREACH v_name IN ARRAY ARRAY['영화·드라마', '예능·방송', '애니·만화'] LOOP
            IF NOT EXISTS (SELECT 1 FROM board_prefixes WHERE board_id = v_video AND lower(name) = lower(v_name)) THEN
                LOOP
                    v_seq := v_seq + 1;
                    v_id := v_base | v_seq;
                    EXIT WHEN NOT EXISTS (SELECT 1 FROM board_prefixes WHERE id = v_id);
                END LOOP;
                INSERT INTO board_prefixes (id, board_id, common_key, name, staff_only, color, active, sort_order, created_at, updated_at, adult_only)
                VALUES (v_id, v_video, NULL, v_name, false, NULL, true, v_order, now(), now(), false);
                v_changed := true;
            END IF;
            v_order := v_order + 1;
        END LOOP;
        UPDATE board_prefixes SET sort_order = 22, updated_at = now()
         WHERE board_id = v_video AND name = '기타' AND common_key IS NULL AND sort_order < 22;
    END IF;

    -- ── ③ 커뮤니티의 전방/후방 가젯 ───────────────────────────────────────────
    UPDATE home_gadgets g
       SET type = 'GALLERY',
           config = '{"count": 8, "boardIds": ["frontback"], "boardScope": "PICK"}'::jsonb,
           style = jsonb_set(coalesce(g.style, '{}'::jsonb), '{icon}', '"pi-images"'::jsonb)
     WHERE g.type = 'ADULT_POSTS'
       AND g.page_id = (SELECT id FROM gadget_pages WHERE page_key = 'community')
       AND g.config -> 'boards' = '["frontback"]'::jsonb;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_changed := v_changed OR v_n > 0;

    IF v_changed THEN
        UPDATE menu_sets SET version = version + 1 WHERE set_key = 'main';
    END IF;
END
$adult_off$;
