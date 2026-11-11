-- 메뉴 항목 글자 색·강조(사용자 2026-11-11). docs/features/menu-contract.md 「메뉴 항목 글자 색·강조」
--
-- text_color : NULL = 기본. 게시판 표시색 12색 키(BoardFields.COLORS — BLUE·TEAL…) 또는 직접 고른 #RRGGBB(대문자로 정리해 저장).
--              검사는 서버(MenuRules.textColorProblem)가 한다 — 12색이 늘어도 마이그레이션 없이 따라가도록 DB 검사는 길이만 둔다.
-- bold       : 굵게.
-- badge      : 이름 옆 작은 배지 글자(앞뒤 공백 정리, 빈 값 NULL, 코드포인트 4개까지). 배지 색 = 글자 색(기본이면 주 색)은 화면이 정한다.
-- 폴더·항목 모두 칸을 가진다(사이트 메뉴 세트만 — 관리자 메뉴는 admin_menu_nodes 로 따로다).
ALTER TABLE menu_nodes
    ADD COLUMN text_color varchar(16) NULL,
    ADD COLUMN bold boolean NOT NULL DEFAULT false,
    ADD COLUMN badge varchar(8) NULL;

-- 13번째 표시색 EMERALD(에메랄드 — 사용자 2026-11-11: 익명게시판 점·메뉴 글자). 게시판 표시색 CHECK(V42 ck_boards_color)에 더한다.
ALTER TABLE boards DROP CONSTRAINT ck_boards_color;
ALTER TABLE boards ADD CONSTRAINT ck_boards_color CHECK (color IS NULL OR color IN
    ('BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN', 'EMERALD'));
