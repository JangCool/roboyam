-- 사이트 이름 바꾸기 「로보얌 / ROBOYAM」→「아이로빗 / IROBIT」(사용자 결정 2026-11-12) — 옛 마이그레이션·설치가 넣은 보이는 기본값만 새 이름으로.
--   · 운영자가 고친 값은 건드리지 않는다 — 옛 기본값과 글자가 똑같을 때만 바꾼다(다시 돌려도 바뀌는 것이 없다).
--   · 코드 이름(테마 id roboyam · 설치 프리셋 id ROBOYAM · 경로 /emoticons/robotikon-* 등)은 그대로다.
--   · 기록 값(로보티콘 구매 줄 emoticon_purchases.pack_name · 보상 이벤트 줄 reward_event_lines.pack_name · 지난 판)은 그때의 이름이라 그대로 둔다.
--   · 사이트 설정·로보티콘 상점은 서버 메모리 캐시(Caffeine)라 이 파일을 적용하는 재기동에서 새 값으로 읽힌다.

-- ── 1. 운영 로보티콘 팩(V142 시드) — 이름 · 설명 · 태그 ─────────────────────────
-- 낸 판은 고칠 수 없다(트리거 tr_emoticon_pack_revisions_sealed). 관리자 화면의 운영 팩 저장(EmoticonPackAdminService.updateOfficial)과 같은 길로
-- 새 판(판 번호 + 1)을 넣는다: 임시 판 → 차례(지금 판의 로보티콘 ID · 그림 · 이름표 그대로) → 낸다 → 상점 판을 새 판으로.
-- 대상 = 상점 판 이름이 옛 기본값 그대로인 운영(OFFICIAL) 팩. 설명·태그는 상점 판에서 옛 기본값 그대로일 때만 새 값, 아니면 그대로 옮긴다.
-- 그 팩에 임시 판이 이미 있으면(운영 팩은 저장 때 바로 내므로 보통 없다) 건너뛴다 — 팩마다 임시 판은 하나.
-- ID 는 앱 Snowflake 와 같은 모양(2026-01-01 기준 ms << 22 | 서버 번호 1023(마이그레이션 몫) << 12 | 순번).
DO $rename$
DECLARE
    v_ms   bigint := greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000);
    v_now  timestamptz := now();
    v_seq  integer := 0;
    v_new  bigint;
    v_ver  integer;
    r      record;
BEGIN
    FOR r IN
        SELECT p.id AS pack_id, l.id AS live_id, l.name, l.description, l.tags, l.cover_image_id, l.sale_wish, l.price_request_memo, m.new_name, m.old_desc,
               m.new_desc, m.old_tags, m.new_tags
          FROM emoticon_packs p
          JOIN emoticon_pack_revisions l ON l.id = p.live_revision_id
          JOIN (VALUES
                ('로보얌콘', '아이로빗콘',
                 '로보얌의 파란 로봇이 기쁨, 슬픔부터 로딩, 에러, 방전까지 감정과 로봇다운 순간을 표현하는 움직이는 로보티콘입니다.',
                 '아이로빗의 파란 로봇이 기쁨, 슬픔부터 로딩, 에러, 방전까지 감정과 로봇다운 순간을 표현하는 움직이는 로보티콘입니다.',
                 ARRAY['로봇', '로보얌', '기계', '리액션', '귀여움']::text[], ARRAY['로봇', '아이로빗', '기계', '리액션', '귀여움']::text[]),
                ('로보얌콘핑크', '아이로빗콘핑크',
                 '로보얌의 분홍 리본 로봇이 기쁨, 슬픔부터 로딩, 에러, 방전까지 감정과 로봇다운 순간을 표현하는 움직이는 로보티콘입니다.',
                 '아이로빗의 분홍 리본 로봇이 기쁨, 슬픔부터 로딩, 에러, 방전까지 감정과 로봇다운 순간을 표현하는 움직이는 로보티콘입니다.',
                 ARRAY['로봇', '로보얌', '핑크', '리액션', '귀여움']::text[], ARRAY['로봇', '아이로빗', '핑크', '리액션', '귀여움']::text[])
               ) AS m (old_name, new_name, old_desc, new_desc, old_tags, new_tags) ON m.old_name = l.name
         WHERE p.source = 'OFFICIAL'
           AND NOT EXISTS (SELECT 1 FROM emoticon_pack_revisions d WHERE d.pack_id = p.id AND d.version IS NULL)
         ORDER BY p.id
    LOOP
        v_new := (v_ms << 22) | (1023::bigint << 12) | v_seq;
        v_seq := v_seq + 1;
        SELECT coalesce(max(version), 0) + 1 INTO v_ver FROM emoticon_pack_revisions WHERE pack_id = r.pack_id;

        INSERT INTO emoticon_pack_revisions (id, pack_id, name, description, tags, cover_image_id, sale_wish, price_request_memo, created_at)
        VALUES (v_new, r.pack_id, r.new_name,
                CASE WHEN r.description = r.old_desc THEN r.new_desc ELSE r.description END,
                CASE WHEN r.tags = r.old_tags THEN r.new_tags ELSE r.tags END,
                r.cover_image_id, r.sale_wish, r.price_request_memo, v_now);
        INSERT INTO emoticon_revision_items (revision_id, position, pack_id, emoticon_id, image_id, label)
        SELECT v_new, i.position, i.pack_id, i.emoticon_id, i.image_id, i.label
          FROM emoticon_revision_items i
         WHERE i.revision_id = r.live_id;
        UPDATE emoticon_pack_revisions SET version = v_ver, submitted_at = v_now WHERE id = v_new;
        UPDATE emoticon_packs SET live_revision_id = v_new, updated_at = v_now WHERE id = r.pack_id;
    END LOOP;
END
$rename$;

-- ── 2. 사이트 설정 — 「로보얌 구성 그대로」 설치가 넣은 이름 · 한 줄 소개 ─────────────
-- 설치 화면 임시 이름(SAMPLE)으로 그 구성을 고르면 install-presets/roboyam.json 의 siteName · siteTagline 이 들어갔다. 글자가 똑같을 때만 바꾼다.
UPDATE site_settings SET site_name = 'IROBIT' WHERE site_name = 'ROBOYAM';
UPDATE site_settings SET tagline = 'AI와 로봇, 그리고 우리(OUR)가 함께 만들어가는 작은 이야기' WHERE tagline = '로봇과 AI, 우리가 함께 만드는 이야기';
