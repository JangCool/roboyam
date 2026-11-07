-- 팝업(공개 사이트에 들어오면 화면 가운데 뜨는 이벤트·프로모션 창) — docs/features/site-popup-contract.md(화면 확정 2026-11-07).
-- 운영자가 관리자 「팝업 관리」(/content/popups, 보기 site.read · 바꾸기 site.manage)에서 여러 개를 만들고 순서·노출·기간·대상을 정한다. 공개 사이트는 GET /api/site/popups 로 읽는다.
--
--   name          : 관리용 이름(방문자에게 안 보인다) — 앞뒤 공백 없이 1~40자
--   kind          : IMAGE(그림 + 링크) · TEXT(제목·내용·단추)
--   image_file_id : IMAGE 의 그림(files, purpose SITE_POPUP_IMAGE — 관리자 POST /api/admin/files 로 올려 PENDING, 지정하면 IN_USE). 파일 행이 지워지면 NULL.
--                   한 파일은 한 팝업만 쓴다(고유 색인 — 파일 쪽 삭제의 외래 키 확인도 맡는다)
--   image_alt     : 그림 대체 글(선택, 100자) · heading 제목(선택, 40자) · body 내용(1000자, 줄바꿈 유지) · button_label 단추 글자(선택, 20자)
--   link          : 누르면 갈 곳(선택) — 공지 띠 링크(V156 notice_bar_custom_link)와 같은 틀: '/'(단 '//'·'/\' 아님) 또는 http(s):// 로 시작, 공백 없음, 500자 이하
--   target        : HOME(주소 /) · ALL(기본 레이아웃의 모든 화면) — show_pc·show_mobile 기기(하나 이상) — 화면이 가린다
--   hide_option   : 닫기 줄 왼쪽 선택지 TODAY(그날 자정) · WEEK(7일) · NEVER(다시 보지 않기) · NONE(「닫기」만) — 기록은 브라우저에만 둔다
--   visible·show_from·show_until : 「노출 중」 = visible 이고 show_from ≤ 지금 < show_until(비어 있으면 상시)
--   sort_order    : 공개 사이트에서 넘기는 차례(1부터, 같으면 먼저 만든 것)
-- 글자 칸은 앞뒤 공백을 떼고 비면 NULL(앱). 지금 종류가 아닌 칸도 그대로 둔다(종류를 되돌리면 다시 쓰인다) — IMAGE 의 그림 필수·TEXT 의 내용 필수는 앱이 막는다
-- (이 표의 제약은 칸 하나씩만 본다 — 그림 파일이 지워져 NULL 이 되어도 행은 남는다. 공개 목록은 그림 없는 IMAGE 를 건너뛴다).
-- 순수 expand — 새 표와 파일 용도 추가뿐이라 이전 버전 앱은 이 표를 모르고 그대로 돈다.
CREATE TABLE site_popups (
    id            bigint        PRIMARY KEY,
    name          varchar(40)   NOT NULL,
    kind          varchar(10)   NOT NULL,
    image_file_id bigint        REFERENCES files (id) ON DELETE SET NULL,
    image_alt     varchar(100),
    link          varchar(500),
    heading       varchar(40),
    body          varchar(1000),
    button_label  varchar(20),
    target        varchar(10)   NOT NULL DEFAULT 'HOME',
    show_pc       boolean       NOT NULL DEFAULT true,
    show_mobile   boolean       NOT NULL DEFAULT true,
    hide_option   varchar(10)   NOT NULL DEFAULT 'TODAY',
    visible       boolean       NOT NULL DEFAULT true,
    show_from     timestamptz,
    show_until    timestamptz,
    sort_order    integer       NOT NULL,
    created_by    bigint        REFERENCES operators (id) ON DELETE SET NULL,
    created_at    timestamptz   NOT NULL,
    updated_by    bigint        REFERENCES operators (id) ON DELETE SET NULL,
    updated_at    timestamptz   NOT NULL,
    CONSTRAINT ck_site_popups_name CHECK (char_length(name) BETWEEN 1 AND 40 AND name = btrim(name)),
    CONSTRAINT ck_site_popups_kind CHECK (kind IN ('IMAGE', 'TEXT')),
    CONSTRAINT ck_site_popups_image_alt CHECK (image_alt IS NULL OR (char_length(image_alt) BETWEEN 1 AND 100 AND image_alt = btrim(image_alt))),
    CONSTRAINT ck_site_popups_heading CHECK (heading IS NULL OR (char_length(heading) BETWEEN 1 AND 40 AND heading = btrim(heading))),
    CONSTRAINT ck_site_popups_body CHECK (body IS NULL OR char_length(body) BETWEEN 1 AND 1000),
    CONSTRAINT ck_site_popups_button_label CHECK (button_label IS NULL OR (char_length(button_label) BETWEEN 1 AND 20 AND button_label = btrim(button_label))),
    CONSTRAINT ck_site_popups_link CHECK (
        link IS NULL OR (
            char_length(link) BETWEEN 1 AND 500
            AND link !~ '\s'
            AND (link ~ '^/([^/\\]|$)' OR link ~* '^https?://')
        )
    ),
    CONSTRAINT ck_site_popups_target CHECK (target IN ('HOME', 'ALL')),
    CONSTRAINT ck_site_popups_devices CHECK (show_pc OR show_mobile),
    CONSTRAINT ck_site_popups_hide_option CHECK (hide_option IN ('TODAY', 'WEEK', 'NEVER', 'NONE')),
    CONSTRAINT ck_site_popups_period CHECK (show_from IS NULL OR show_until IS NULL OR show_until > show_from)
);
CREATE INDEX ix_site_popups_order ON site_popups (sort_order, id);
CREATE UNIQUE INDEX ux_site_popups_image_file ON site_popups (image_file_id) WHERE image_file_id IS NOT NULL;

-- 팝업 그림 용도(FilePurpose.SITE_POPUP_IMAGE) — 관리자 POST /api/admin/files(purpose SITE_POPUP_IMAGE, 권한 site.manage)로 올리고 팝업 만들기·고치기의 imageFileId 로 지정한다.
-- 하이라이트 대표 이미지와 같은 검사·저장(PNG·JPG·GIF·WEBP 2MB, 한 변 4000px, GIF 는 첫 장면), 저장소 키 site-popup/{버전}/image.{확장자}. 지정하지 않은 파일은 「올리다 만 파일 정리」가 지운다.
ALTER TABLE files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE files ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO', 'POST_IMAGE', 'HIGHLIGHT_IMAGE', 'BIZ_AD_IMAGE', 'GROUP_IMAGE', 'SITE_POPUP_IMAGE'));
