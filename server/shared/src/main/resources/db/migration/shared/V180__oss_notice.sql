-- 오픈소스 라이선스 고지(docs/features/oss-licenses-contract.md, 사용자 확정 2026-11-10) — 공개 화면 /licenses 와 관리자 「오픈소스 라이선스」(/settings/oss-licenses,
-- 보기 site.read · 바꾸기 site.manage). 자동 수집 목록(빌드 때 모은 의존성)은 DB 가 아니라 두 앱의 정적 파일 public/oss-licenses.json 이다 — 여기는 운영자가 정하는 것만.
--
-- oss_notice_settings: 한 줄(id = 1) — intro 는 공개 화면 제목 아래 설명(여러 줄, 1000자 이하). NULL = 화면 기본 문구. {사이트} 는 화면이 사이트 이름으로 바꾼다.
-- oss_notice_entries : 직접 추가 항목(자동 수집에 안 잡히는 글꼴·이미지·복사해 넣은 코드 등) — 이름순으로 보인다(차례 칸 없음).
--   name 이름(1~100자) · version 버전(선택, 50자) · license 라이선스(SPDX 또는 자유 글, 1~100자) · copyright 저작권 고지(선택, 2000자) ·
--   license_text 라이선스 전문(선택, 100000자 — 줄바꿈 그대로) · source_url 원본 링크(선택, http(s)://, 500자) ·
--   category 묶음 USER(공개 화면) · ADMIN(관리자 화면) · SERVER(서버) · FONT_ICON(글꼴·아이콘) · OTHER(기타) · hidden 숨김(공개 화면에서 뺀다)
-- 글자 칸은 앱이 앞뒤 공백을 떼고 비면 NULL 로 넣는다. 순수 expand — 새 표 둘뿐이라 이전 버전 앱은 이 표를 모르고 그대로 돈다.
CREATE TABLE oss_notice_settings (
    id         integer      PRIMARY KEY,
    intro      text,
    updated_by bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT ck_oss_notice_settings_single CHECK (id = 1),
    CONSTRAINT ck_oss_notice_settings_intro CHECK (intro IS NULL OR char_length(intro) BETWEEN 1 AND 1000)
);
INSERT INTO oss_notice_settings (id, intro, updated_at) VALUES (1, NULL, now());

CREATE TABLE oss_notice_entries (
    id           bigint        PRIMARY KEY,
    name         varchar(100)  NOT NULL,
    version      varchar(50),
    license      varchar(100)  NOT NULL,
    copyright    varchar(2000),
    license_text text,
    source_url   varchar(500),
    category     varchar(20)   NOT NULL,
    hidden       boolean       NOT NULL DEFAULT false,
    created_by   bigint        REFERENCES operators (id) ON DELETE SET NULL,
    created_at   timestamptz   NOT NULL,
    updated_by   bigint        REFERENCES operators (id) ON DELETE SET NULL,
    updated_at   timestamptz   NOT NULL,
    CONSTRAINT ck_oss_notice_entries_name CHECK (char_length(name) BETWEEN 1 AND 100 AND name = btrim(name)),
    CONSTRAINT ck_oss_notice_entries_version CHECK (version IS NULL OR (char_length(version) BETWEEN 1 AND 50 AND version = btrim(version))),
    CONSTRAINT ck_oss_notice_entries_license CHECK (char_length(license) BETWEEN 1 AND 100 AND license = btrim(license)),
    CONSTRAINT ck_oss_notice_entries_copyright CHECK (copyright IS NULL OR char_length(copyright) BETWEEN 1 AND 2000),
    CONSTRAINT ck_oss_notice_entries_license_text CHECK (license_text IS NULL OR char_length(license_text) BETWEEN 1 AND 100000),
    CONSTRAINT ck_oss_notice_entries_source_url CHECK (source_url IS NULL OR (char_length(source_url) BETWEEN 1 AND 500 AND source_url !~ '\s' AND source_url ~* '^https?://')),
    CONSTRAINT ck_oss_notice_entries_category CHECK (category IN ('USER', 'ADMIN', 'SERVER', 'FONT_ICON', 'OTHER'))
);
CREATE INDEX ix_oss_notice_entries_name ON oss_notice_entries (lower(name), id);
