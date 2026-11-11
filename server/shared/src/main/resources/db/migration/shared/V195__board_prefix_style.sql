-- 글 목록 말머리 모양(docs/features/mobile-nav-prefix-style-contract.md 1절) — TEXT(글자, 사이트 기본) · MUTED(회색 칩) · COLOR(색상 칩, 지금 모양).
-- 칩 위치(V169)와 같은 틀이다:
-- · board_defaults.list_prefix_style: 사이트 기본(관리자 게시판 > 기본 설정 > 일반). 게시판이 따로 고르지 않으면 이 값이고,
--   섞인 목록(최신글·인기글·검색·회원 작성글·소모임 글·팁 등)도 이 값을 쓴다(공개 /api/site/info 의 boardDisplay.listPrefixStyle).
-- · boards.prefix_style: 게시판별 값. NULL = 사이트 기본 따름(유효값 = 게시판 값 ?? 기본값 — 앱 BoardCatalog 가 계산한다).
-- 값 목록의 원천은 앱 BoardFields.PREFIX_STYLES 다(여기 CHECK 와 같다).
-- 상수 기본값이 있는 칸 더하기는 표를 다시 쓰지 않는다. 두 표 모두 작아(행 하나 · 게시판 수) CHECK 는 바로 건다.

ALTER TABLE board_defaults ADD COLUMN list_prefix_style varchar(8) NOT NULL DEFAULT 'TEXT';
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_list_prefix_style CHECK (list_prefix_style IN ('TEXT', 'MUTED', 'COLOR'));

ALTER TABLE boards ADD COLUMN prefix_style varchar(8);
ALTER TABLE boards ADD CONSTRAINT ck_boards_prefix_style CHECK (prefix_style IS NULL OR prefix_style IN ('TEXT', 'MUTED', 'COLOR'));

-- 말머리 색 직접 고르기(같은 계약 2절): NULL = 자동, 13색 키(BoardFields.COLORS) 또는 직접 고른 #rrggbb
-- (서버 BoardFields.normalizePrefixColor 가 소문자로 정리해 저장 — 화면 UtilColor.normalize 와 같은 모양. 게시판 표시색 V188 은 대문자로 다르다).
-- 칸 길이 varchar(8)(V42)은 '#rrggbb'(7자)를 담는다 — 넓히지 않는다.
ALTER TABLE board_prefixes DROP CONSTRAINT ck_board_prefixes_color;
ALTER TABLE board_prefixes ADD CONSTRAINT ck_board_prefixes_color CHECK (color IS NULL
    OR color IN ('BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN', 'EMERALD')
    OR color ~ '^#[0-9a-f]{6}$');
