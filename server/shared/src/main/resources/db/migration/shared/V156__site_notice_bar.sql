-- 공지 띠(공개 사이트 모든 화면의 메뉴 줄 아래 한 줄 공지) — 관리자 설정 > 일반 「공지 띠」(2026-11-06, 화면 확정).
--
-- 사이트 설정 행(site_settings, 한 행)에 칸을 더한다.
--   notice_bar_enabled      : 띠를 보일지(기본 켬 — 공지 게시판 글이 없으면 띠가 나오지 않으므로 켜 두어도 빈 사이트는 달라지지 않는다)
--   notice_bar_source       : BOARD(slug notice 게시판의 최근 글) · CUSTOM(운영자가 쓴 문구 한 줄)
--   notice_bar_board_count  : BOARD 일 때 차례로 보일 글 수 — 5 또는 10
--   notice_bar_custom_text  : CUSTOM 문구 — 앞뒤 공백 없이 1~100자, '<'·'>'·제어 문자 없음(tagline V5 와 같은 틀). 빈 글자는 저장하지 않는다(앱이 NULL 로)
--   notice_bar_custom_link  : CUSTOM 문구를 누르면 갈 곳(선택) — '/'(단 '//' 아님) 또는 http(s):// 로 시작, 공백 없음, 500자 이하
-- 출처가 아닌 쪽의 칸도 그대로 둔다(출처를 바꿔 볼 때 지운 듯 보이지 않게) — CUSTOM 인데 문구가 없는 경우는 앱이 막는다(이 표의 제약은 칸 하나씩만 본다).
-- 순수 expand — 칸 추가뿐이라 이전 버전 앱은 이 칸을 모르고 그대로 돈다.
ALTER TABLE site_settings
    ADD COLUMN notice_bar_enabled     boolean      NOT NULL DEFAULT true,
    ADD COLUMN notice_bar_source      varchar(10)  NOT NULL DEFAULT 'BOARD',
    ADD COLUMN notice_bar_board_count integer      NOT NULL DEFAULT 5,
    ADD COLUMN notice_bar_custom_text varchar(100),
    ADD COLUMN notice_bar_custom_link varchar(500),
    ADD CONSTRAINT ck_site_settings_notice_bar_source CHECK (notice_bar_source IN ('BOARD', 'CUSTOM')),
    ADD CONSTRAINT ck_site_settings_notice_bar_board_count CHECK (notice_bar_board_count IN (5, 10)),
    ADD CONSTRAINT ck_site_settings_notice_bar_custom_text CHECK (
        notice_bar_custom_text IS NULL OR (
            char_length(notice_bar_custom_text) BETWEEN 1 AND 100
            AND notice_bar_custom_text = btrim(notice_bar_custom_text)
            AND notice_bar_custom_text !~ '[<>\u0001-\u001f\u007f-\u009f]'
        )
    ),
    ADD CONSTRAINT ck_site_settings_notice_bar_custom_link CHECK (
        notice_bar_custom_link IS NULL OR (
            char_length(notice_bar_custom_link) BETWEEN 1 AND 500
            AND notice_bar_custom_link !~ '\s'
            AND (notice_bar_custom_link ~ '^/([^/\\]|$)' OR notice_bar_custom_link ~* '^https?://')
        )
    );
