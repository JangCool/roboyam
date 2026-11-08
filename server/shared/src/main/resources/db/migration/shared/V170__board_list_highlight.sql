-- 글 목록 윗줄의 추천·조회 숫자 강조(docs/features/post-chip-position-contract.md) — 사이트 기본만(게시판별 덮어쓰기 없음), 관리자 게시판 > 기본 설정 > 일반.
--
-- · list_like_highlight: 추천 수가 이 값 이상이면 강조. 0 = 강조 안 함(0~100000).
-- · list_view_highlight: 조회 수가 이 값 이상이면 강조. 0 = 강조 안 함(0~10000000).
-- · list_highlight_color: 강조 색 #rrggbb(앱이 소문자로 맞춰 저장한다, 기본 빨강 #e5484d).
-- 공개 /api/site/info 의 boardDisplay.likeHighlight · viewHighlight · highlightColor 로 나간다. 행 하나짜리 표라 CHECK 는 바로 건다.

ALTER TABLE board_defaults
    ADD COLUMN list_like_highlight  integer    NOT NULL DEFAULT 10,
    ADD COLUMN list_view_highlight  integer    NOT NULL DEFAULT 1000,
    ADD COLUMN list_highlight_color varchar(7) NOT NULL DEFAULT '#e5484d';

ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_list_like_highlight CHECK (list_like_highlight BETWEEN 0 AND 100000);
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_list_view_highlight CHECK (list_view_highlight BETWEEN 0 AND 10000000);
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_list_highlight_color CHECK (list_highlight_color ~ '^#[0-9A-Fa-f]{6}$');
