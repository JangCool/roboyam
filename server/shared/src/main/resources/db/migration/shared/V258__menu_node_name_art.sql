-- 메뉴 항목 이름 그림(사용자 2026-11-19). docs/features/achu-page-contract.md 「메뉴 이름 그림」
--
-- name_style : TEXT(보통 글자, 기본) · LOGO(로고 글꼴 그림 — 관리자 화면이 개구체 Gaegu 700 으로 이름을 SVG 패스로 굳혀 보낸다).
-- name_art   : LOGO 일 때의 그림 {d, w, h, em, sw} — d 채움 패스(그리기 명령·숫자만, 60000자 이하), w·h viewBox 크기(0 초과 10000 이하),
--              em 그릴 높이(글자 크기 배수 0.3~2), sw 같은 색 테두리 두께(0~10, 없으면 테두리 없음). 모양 검사는 서버(MenuRules.nameArtProblems)가 한다 —
--              jsonb 는 길이 한도가 없어 60000자 패스도 그대로 받는다.
-- 항목(ITEM)만 그림을 가진다 — 폴더는 늘 TEXT·NULL. TEXT 이면 그림을 두지 않고, LOGO 이면 그림이 꼭 있다.
-- 관리자 메뉴(admin_menu_nodes)는 건드리지 않는다(사이트 메뉴 세트만).
ALTER TABLE menu_nodes
    ADD COLUMN name_style varchar(8) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN name_art jsonb NULL;

ALTER TABLE menu_nodes ADD CONSTRAINT ck_menu_nodes_name_style CHECK (name_style IN ('TEXT', 'LOGO'));
ALTER TABLE menu_nodes ADD CONSTRAINT ck_menu_nodes_name_art CHECK (
    (name_style = 'TEXT' AND name_art IS NULL)
    OR (name_style = 'LOGO' AND name_art IS NOT NULL AND kind = 'ITEM' AND jsonb_typeof(name_art) = 'object'));
