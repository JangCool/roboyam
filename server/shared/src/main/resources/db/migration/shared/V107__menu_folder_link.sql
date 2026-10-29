-- 공개 메뉴 폴더(부모 메뉴)의 연결 대상 — docs/features/menu-contract.md 「폴더 연결」(사용자 확정 2026-10-29).
-- 번호: shared 마지막 V106(공구 문의·리뷰) 다음. 다른 worktree 에 V107 이 없음을 2026-10-29 확인(합칠 때 다시 본다).
--
-- 무엇이 바뀌나
--   · 폴더도 항목과 같은 칸(target_type·target_id·target_key·settings)으로 연결 대상을 가질 수 있다(선택). 이름 필수·모바일 탭 금지는 그대로.
--     폴더 연결의 유형은 앱이 아는 유형(BOARD·BUILTIN·PATH·URL)만 저장된다 — 값 검사는 앱(MenuTreeValidator)이 한다.
--   · link_click: 연결이 있는 폴더의 공개 사이트 상단 첫 줄 탭을 누르면 NAVIGATE_OPEN(이동하고 하위 메뉴도 열기) · NAVIGATE(이동만).
--     연결이 없는 폴더와 항목은 NULL.
--   · 「한 그룹 안에서 같은 게시판은 한 번」 부분 유일 색인은 항목에만 건다 — 폴더 연결은 같은 그룹의 항목과 같은 게시판을 가리킬 수 있다.
-- 게시판을 지우면 그 게시판을 가리키는 항목은 지우고 폴더는 연결만 비운다(MenuBoardLinker — 폴더를 지우면 하위가 CASCADE 로 사라진다).

ALTER TABLE menu_nodes DROP CONSTRAINT ck_menu_nodes_folder;

ALTER TABLE menu_nodes
    ADD COLUMN link_click varchar(16),
    ADD CONSTRAINT ck_menu_nodes_folder CHECK (kind <> 'FOLDER' OR (label IS NOT NULL AND NOT mobile_tab)),
    ADD CONSTRAINT ck_menu_nodes_link_click CHECK (
        link_click IS NULL OR (kind = 'FOLDER' AND target_type IS NOT NULL AND link_click IN ('NAVIGATE_OPEN', 'NAVIGATE')));

DROP INDEX ux_menu_nodes_board;
CREATE UNIQUE INDEX ux_menu_nodes_board ON menu_nodes (set_key, target_id) WHERE target_type = 'BOARD' AND kind = 'ITEM';
