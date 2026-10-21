-- 사이트 일반 설정 3단계 — 파일 표(files)와 사이트 이미지·표시 기본값 칸 (docs/features/site-settings-contract.md, roles-contract.md 「백엔드 구현 순서」 3단계)
--
-- files: 운영자가 올린 파일의 장부. 지금은 사이트 로고 그림(용도 SITE_LOGO)만 쓴다. 파일 자체는 저장소(로컬 디스크, roboyam.storage.local-path)에 있고
-- 이 표는 키·크기·상태만 가진다. 올리기(POST /api/admin/files)는 PENDING 으로 만들고, 로고로 지정(PUT /api/admin/site/logo)하면 IN_USE 가 된다.
-- 올리기만 하고 지정하지 않은 PENDING 파일은 「올리다 만 파일 정리」 배치(file-orphan-cleanup)가 지운다.
CREATE TABLE files (
    id           bigint       PRIMARY KEY,
    purpose      varchar(20)  NOT NULL,                          -- SITE_LOGO
    storage_key  varchar(200) NOT NULL,                          -- 저장소 키 (site-logo/{버전}/logo.png …)
    content_type varchar(40)  NOT NULL,
    size_bytes   bigint       NOT NULL,
    width        integer      NOT NULL,
    height       integer      NOT NULL,
    status       varchar(10)  NOT NULL,                          -- PENDING(올리기만 함) · IN_USE(쓰는 중)
    uploaded_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    used_at      timestamptz,
    CONSTRAINT uq_files_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO')),
    CONSTRAINT ck_files_status CHECK (status IN ('PENDING', 'IN_USE')),
    CONSTRAINT ck_files_size CHECK (size_bytes >= 0)
);
-- 올리다 만 파일 정리 배치가 오래된 PENDING 을 찾는다
CREATE INDEX ix_files_pending ON files (created_at) WHERE status = 'PENDING';

-- site_settings: 로고(그림 파일 하나 또는 SVG 소스 하나), 사이트 아이콘·링크 미리보기 이미지의 버전 토큰, 표시 기본값
--   logo_file_id : 로고로 지정한 그림 파일. logo_svg : 로고로 지정한 SVG 소스(서버가 검사해 xmlns 를 맞춘 것, 100,000자 이하). 둘 다 NULL 이면 로고 없음(이름 글자만). 둘이 함께 있을 수 없다.
--   icon_token   : 사이트 아이콘 버전(저장소 키 site-icon/{버전}/…). NULL 이면 기본 아이콘.
--   og_token     : 링크 미리보기 이미지 버전(저장소 키 site-og/{버전}/…, 1200×630 PNG 한 장). NULL 이면 기본 이미지.
--   display_*    : 표시 기본값(언어·시간대·시간 형식·날짜 형식). 개인 설정의 「기본 설정」이 되는 값이다. 처음 값은 한국어 / Asia/Seoul / 24시간 / YYYY-MM-DD.
-- 모두 칸 추가뿐이다(기존 행은 로고·아이콘·이미지 없음, 표시 기본값은 처음 값).
ALTER TABLE site_settings ADD COLUMN logo_file_id bigint REFERENCES files (id);
ALTER TABLE site_settings ADD COLUMN logo_svg text;
ALTER TABLE site_settings ADD COLUMN icon_token varchar(32);
ALTER TABLE site_settings ADD COLUMN og_token varchar(32);
ALTER TABLE site_settings ADD COLUMN display_language varchar(2) NOT NULL DEFAULT 'ko';
ALTER TABLE site_settings ADD COLUMN display_zone_id varchar(64) NOT NULL DEFAULT 'Asia/Seoul';
ALTER TABLE site_settings ADD COLUMN display_time_format varchar(3) NOT NULL DEFAULT 'H24';
ALTER TABLE site_settings ADD COLUMN display_date_format varchar(10) NOT NULL DEFAULT 'YMD_DASH';

ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_one CHECK (logo_file_id IS NULL OR logo_svg IS NULL);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_logo_svg_len CHECK (logo_svg IS NULL OR char_length(logo_svg) BETWEEN 1 AND 100100);
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_icon_token CHECK (icon_token IS NULL OR icon_token ~ '^[0-9a-f]{8,32}$');
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_og_token CHECK (og_token IS NULL OR og_token ~ '^[0-9a-f]{8,32}$');
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_display_language CHECK (display_language IN ('ko', 'en'));
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_display_time_format CHECK (display_time_format IN ('H24', 'H12'));
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_display_date_format CHECK (display_date_format IN ('YMD_DASH', 'YMD_DOT', 'DMY_SLASH', 'MDY_SLASH'));
