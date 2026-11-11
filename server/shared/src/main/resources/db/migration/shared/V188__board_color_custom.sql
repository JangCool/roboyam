-- 게시판 표시색(이름 옆 점) 직접 고르기(사용자 2026-11-11). 메뉴 글자 색(V186 menu_nodes.text_color)과 같은 규칙:
-- color : NULL = 자동. 13색 키(BoardFields.COLORS) 또는 직접 고른 #RRGGBB(서버 BoardFields.normalizeColor 가 대문자로 정리해 저장).
-- 칸 길이 varchar(8)(V42)은 '#RRGGBB'(7자)를 담는다 — 넓히지 않는다.
ALTER TABLE boards DROP CONSTRAINT ck_boards_color;
ALTER TABLE boards ADD CONSTRAINT ck_boards_color CHECK (color IS NULL
    OR color IN ('BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN', 'EMERALD')
    OR color ~ '^#[0-9A-F]{6}$');

-- 말머리 색은 13색 키만(직접 고르기 없음). V186 이 13번째 EMERALD 를 게시판 표시색에만 더해, 관리자 화면이 고르게 하는 EMERALD 말머리 저장이
-- DB CHECK 에 걸렸다 — 여기서 함께 더한다.
ALTER TABLE board_prefixes DROP CONSTRAINT ck_board_prefixes_color;
ALTER TABLE board_prefixes ADD CONSTRAINT ck_board_prefixes_color CHECK (color IS NULL
    OR color IN ('BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN', 'EMERALD'));
