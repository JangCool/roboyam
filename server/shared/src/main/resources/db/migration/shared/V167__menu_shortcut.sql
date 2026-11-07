-- 메뉴 바로가기(다른 묶음에도 보이기) — docs/features/menu-shortcut-contract.md
-- · menu_nodes.shortcut: 다른 묶음(폴더)에 진짜 자리가 있는 게시판을 이 묶음에도 보이게 둔 항목. BOARD 항목만, 폴더 안에만, 모바일 탭 없음.
--   진짜 자리가 같은 그룹에 있어야 한다·진짜 자리와 같은 폴더 불가는 앱(MenuTreeValidator)이 트리 저장 한 번 단위로 검사한다.
-- · 「한 그룹 안에서 같은 게시판은 한 번」(ux_menu_nodes_board)은 진짜 자리(shortcut = false)끼리만 — 바로가기는 여러 묶음에 둘 수 있다.
--   같은 폴더에 같은 게시판 바로가기 두 개는 마지막 방어로 부분 유일 색인(ux_menu_nodes_board_shortcut)을 둔다.
-- · 데이터는 옮기지 않는다(기존 행은 모두 진짜 자리 — 기본값 false).

ALTER TABLE menu_nodes
    ADD COLUMN shortcut boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_menu_nodes_shortcut CHECK (
        NOT shortcut OR (kind = 'ITEM' AND target_type = 'BOARD' AND NOT mobile_tab AND parent_id IS NOT NULL));

DROP INDEX ux_menu_nodes_board;
CREATE UNIQUE INDEX ux_menu_nodes_board ON menu_nodes (set_key, target_id) WHERE target_type = 'BOARD' AND kind = 'ITEM' AND NOT shortcut;
CREATE UNIQUE INDEX ux_menu_nodes_board_shortcut ON menu_nodes (parent_id, target_id) WHERE shortcut;
