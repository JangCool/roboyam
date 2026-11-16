-- 사이트 로고 4칸(언어 × 바탕) + 「로고에 사이트 이름이 들어 있음」 (docs/features/site-logo-variants-contract.md, 저장 방식 (a))
--
-- 칸: ① 한글·밝은 바탕용(koLight) · ② 한글·어두운 바탕용(koDark) · ③ 영문·밝은 바탕용(enLight) · ④ 영문·어두운 바탕용(enDark).
-- ① 은 V12 의 단일 로고 칸(logo_file_id · logo_svg)을 그대로 쓴다 — 지금 로고가 곧 ① 이므로 값을 옮기지 않아도 기존 사이트가 그대로 보인다.
-- 옛 단일 로고 API(PUT/DELETE /api/admin/site/logo, PUT …/logo/svg)와 관리자 me·메일·index.html 머리의 로고도 같은 ① 칸을 읽는다.
-- 나머지 세 칸은 칸마다 그림 파일 하나(files.id, 용도 SITE_LOGO) 또는 SVG 소스 하나 — 둘이 함께 있을 수 없고 둘 다 NULL 이면 빈 칸이다.
-- 빈 칸을 어느 칸이 대신할지는 화면 규칙(UtilSiteLogo.resolve)이다. 서버는 원본 4칸만 준다.
--
-- logo_name_included: 로고 그림에 사이트 이름이 들어 있는가 — 켜져 있으면 공개 헤더가 로고 옆 이름 글자를 붙이지 않는다.
--   기존 사이트는 이름 글자가 계속 보이도록 false 로 시작한다.
ALTER TABLE site_settings ADD COLUMN logo_ko_dark_file_id bigint REFERENCES files (id);
ALTER TABLE site_settings ADD COLUMN logo_ko_dark_svg text;
ALTER TABLE site_settings ADD COLUMN logo_en_light_file_id bigint REFERENCES files (id);
ALTER TABLE site_settings ADD COLUMN logo_en_light_svg text;
ALTER TABLE site_settings ADD COLUMN logo_en_dark_file_id bigint REFERENCES files (id);
ALTER TABLE site_settings ADD COLUMN logo_en_dark_svg text;
ALTER TABLE site_settings ADD COLUMN logo_name_included boolean NOT NULL DEFAULT false;

ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_ko_dark_one CHECK (logo_ko_dark_file_id IS NULL OR logo_ko_dark_svg IS NULL);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_en_light_one CHECK (logo_en_light_file_id IS NULL OR logo_en_light_svg IS NULL);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_en_dark_one CHECK (logo_en_dark_file_id IS NULL OR logo_en_dark_svg IS NULL);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_ko_dark_svg_len CHECK (logo_ko_dark_svg IS NULL OR char_length(logo_ko_dark_svg) BETWEEN 1 AND 100100);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_en_light_svg_len CHECK (logo_en_light_svg IS NULL OR char_length(logo_en_light_svg) BETWEEN 1 AND 100100);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_en_dark_svg_len CHECK (logo_en_dark_svg IS NULL OR char_length(logo_en_dark_svg) BETWEEN 1 AND 100100);
