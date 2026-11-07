# 메뉴 바로가기(다른 묶음에도 보이기) 계약 (화면 확정 2026-11-08)

한 게시판을 공개 메뉴의 여러 묶음(폴더)에 보이게 한다. 게시판의 **진짜 자리**는 하나, 다른 묶음에는 **바로가기**로 둔다.
첫 쓰임(사용자 결정): AI·로봇·테크·산업·주식·게임·스포츠·연애·결혼·직장·취업·시사 일곱 게시판의 진짜 자리 = 커뮤니티, 핫 로토픽에는 바로가기.

## 화면 (확정)
- 관리자 공개 메뉴(`/menus/public`): 진짜 자리 게시판 줄 [다른 묶음에도 보이기] → 묶음 고르기(원래 자리·이미 있음·단계 한도는 비활성 + 이유) → 그 묶음 끝에 바로가기. 바로가기 줄 「바로가기」 딱지 + 「원래 자리: …」, 진짜 자리 줄 「다른 묶음에도 있음 (…)」.
  바로가기 수정 = 이름·영문명·보이기·보이는 대상·구분선·설명(연결 게시판 고정, 모바일 탭 없음). 바로가기 지우기 = 그것만, 진짜 자리(또는 그 폴더) 지우기 = 바로가기도 함께(확인).
- 공개 메뉴: 바로가기 줄은 일반 게시판 줄과 같은 모양(색 점·이름·링크). 「지금 있는 곳」 묶음은 진짜 자리(사이드 고르기·막대 밑줄·하단 탭). 사용자가 바로가기 묶음을 열어 두면 그 안 줄도 활성.

## 규칙 (서버가 최종 판정 — 화면 `UtilMenu.validate` 와 같게)
- 바로가기는 BOARD 항목만, 폴더 안에만(최상위 불가), `mobileTab` 은 늘 false.
- 같은 메뉴 그룹 안에 같은 게시판의 진짜 자리가 있어야 한다(`SHORTCUT_ORIGIN_MISSING`). 진짜 자리와 같은 폴더·같은 폴더에 같은 게시판 바로가기 두 개는 불가(`SHORTCUT_SAME_FOLDER`).
- 「한 그룹에 게시판 한 번」은 진짜 자리끼리만 센다.
- 진짜 자리 기준: 글쓰기 기본 게시판, 「지금 있는 곳」 묶음, 게시판 모음(`/boards` — 바로가기는 안 보임), 폴더 자동 넣기.
- **가젯 페이지 「묶음 범위(FOLDER)」 게시판에는 그 묶음의 바로가기 게시판도 포함**(핫 로토픽 인기글·최신글이 비지 않게 — 사용자 결정).
- 진짜 자리가 보는 사람에게 안 보이면 바로가기가 「지금 있는 곳」을 맡는다.

## 저장·API
- `menu_nodes.shortcut boolean NOT NULL DEFAULT false`(BOARD 항목만 true), 유일 색인 `ux_menu_nodes_board` 를 `shortcut = false` 인 항목에만(부분 색인). shared 마이그레이션 번호는 합칠 때 develop 최신 다음(운영 게시판 세션이 V166 을 쓰는 중).
- 관리자 `GET·PUT /api/admin/menu-sets/{key}/tree` 항목에 `shortcut`(빠지면 false), 오류 `fields["nodes[i]…target"]`.
- 공개 `GET /api/menu` 항목에 `shortcut: true`(나머지 모양은 일반 게시판 항목과 같음, `id` 는 노드마다 다름).
- 데이터: 로컬 DB 는 관리자 화면(트리 저장)으로 옮긴다 — 운영 DB 에는 그 게시판들이 없어 마이그레이션으로 옮기지 않는다.

## 서버 구현 결과 (2026-11-08)
- 마이그레이션 shared `V167__menu_shortcut.sql`(V166 은 운영 게시판 세션 몫 — 합칠 때 develop 최신 다음으로 다시 확인): `shortcut` 칸 + `ck_menu_nodes_shortcut`(ITEM·BOARD·모바일 탭 아님·폴더 안), `ux_menu_nodes_board` 를 `NOT shortcut` 부분 색인으로, 같은 폴더 같은 게시판 바로가기 두 번을 막는 `ux_menu_nodes_board_shortcut(parent_id, target_id) WHERE shortcut`. 데이터는 옮기지 않는다.
- 검사 `MenuTreeValidator`(shared): 오류는 `fields["nodes[i]….targetId"]` — `menu.shortcut.originMissing`(=`SHORTCUT_ORIGIN_MISSING`) · `menu.shortcut.topLevel`(최상위) · `menu.shortcut.sameFolder`(=`SHORTCUT_SAME_FOLDER`, 같은 폴더 두 번째부터). 게시판 아닌 바로가기 `….targetType`(`menu.shortcut.board`), 모바일 탭 `….mobileTab`(`menu.shortcut.mobileTab`). 폴더의 `shortcut` 은 버린다. 진짜 자리 = 트리 차례 첫 진짜 항목(하위 폴더 안도).
- 응답: 관리자 트리 항목·공개 메뉴 항목 모두 `shortcut` 을 늘 싣는다(일반 항목은 false). 감사 기록 트리 요약에 `[shortcut]`.
- 서버에서 게시판→폴더를 menu_nodes 로 정하는 곳은 가젯 페이지 기준 폴더(`GadgetPageFolders` — 공개 페이지 FOLDER 범위·글쓰기 게시판 `writeBoard`, 관리자 `/api/admin/gadget-pages/folders`)뿐이고 결정대로 바로가기 게시판을 담는다. 글쓰기 기본 게시판·지금 있는 곳·게시판 모음은 화면이 공개 메뉴로 계산한다(서버 바꿀 곳 없음). 폴더 자동 넣기(`MenuBoardLinker`)는 새 게시판을 진짜 자리로만 넣는다.
