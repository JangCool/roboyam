# 메뉴 관리 계약 (**관리자 화면 확정 2026-10-22** — 이번 범위는 백엔드 구현, 화면 연결은 후속)

**의도**: 공개 사이트 메뉴와 관리자 사이트 메뉴를 관리자 앱에서 구성한다. 공개 메뉴는 **메뉴 그룹**(좌측 사이드·마이페이지·공동구매·사용자 정의) 마다 트리를 따로 갖고, 트리는 폴더(선택: 누르면 가는 곳)와 항목(게시판·사이트 화면·외부 링크 연결)으로 이루어진다. 관리자 메뉴는 일반/설정 두 모드의 사이드 메뉴 순서·묶음·이름·아이콘·표시를 바꾼다(권한은 화면이 정한다).
**참고(읽기 전용)**: community `D:\project\community\workspace\community` — `docs/api-menu.md`(트리·연결 유형·검증·리졸버 구조의 원본), `server/shared/.../menu/**`. roboyam 은 이를 **메뉴 그룹 여러 개**로 넓혔다.

## 화면 (기준 코드 — 칸 이름·범위·기본값의 원천)
- `/menus/public` · `/menus/admin` (`/menus` → `/menus/public`).
- 기준 코드: `apps/admin-web/app/composables/menu/api/useMenuApi.ts`(머리말 = API 후보·오류 코드 전체) · `useMenuApi.types.ts`(요청·응답 모양 = JSON 키) · `utils/util-menu.ts`(`LIMITS`·`TARGET_TYPES`·`BUILTINS` 앱 화면 레지스트리·주소 검사 `pathProblem`/`hrefProblem`·상태·미연결·미리보기 거르기) · `utils/util-menu-tree.ts`(서버 `fields` 경로 꼴) · `utils/util-admin-menu-config.ts`(관리자 메뉴 레지스트리·구조 규칙·검증). 목업은 서버 연결 뒤 지워졌고 서버 규칙의 원천은 서버 코드다.
- 공개 사이트가 받는 모양: `apps/user-web/app/composables/menu/api/useMenuApi.types.ts`(`GET /api/menu` 응답 — 바꾸지 않는다).

## 권한
조회 `layout.read`(공개·관리자 메뉴 모두) · 공개 메뉴 그룹·트리 저장 `layout.settings` · 관리자 메뉴 저장 `admin-menu.manage`(새 코드). 서버 `Permission` 의 `LAYOUT_READ`·`LAYOUT_SETTINGS` 는 준비됨으로 켜고 `ADMIN_MENU_MANAGE` 를 더한다. 응답의 `editable` = 그 저장 권한 보유.

## 공개 사이트 메뉴
- **메뉴 그룹** `menu_sets`: `key`(PK, `^[a-z][a-z0-9-]*$` 2~30자, 변경 불가) · 이름 1~20자 · 영문명 ≤40자 · `placement` `SIDE`·`MYPAGE`·`GROUP_BUY`·`CUSTOM` · 사용 여부 · 설명 ≤100자 · `system`(기본 그룹 — 삭제·위치 변경 불가). 시드: `main`(SIDE) · `mypage`(MYPAGE) · `group-buy`(GROUP_BUY), 모두 system.
- **노드** `menu_nodes`(그룹별 트리, `parent_id`): `FOLDER`(이름 필수, `collapsible`·`collapsed`, `autoAdd`) · `ITEM`(`targetType`·`targetId`·`targetKey`·`settings` jsonb·`mobileTab`). 공통: `label`(항목은 null = 대상 이름) · `nameEn` · `visible` · `audience` `ALL`·`GUEST`·`MEMBER`·`GRADE_MIN`·`STAFF` · `minGrade`(등급 **키**, GRADE_MIN 일 때만).
- 한도(서버 상수 한 곳 = 화면 `UtilMenu.LIMITS`): 폴더 깊이 ≤3 · 항목 깊이 ≤4 · 그룹당 노드 ≤80 · 모바일 탭 ≤10 · 이름 1~20자(`<`·`>`·줄바꿈·제어 문자 금지). 부모는 폴더만.
- **SIDE 그룹 전용**: `mobileTab` · `autoAdd`(그룹 안에서 한 폴더만 — 새로 켠 폴더가 있으면 기존 것을 끈다, 새로 켠 것이 둘 이상이면 400) · 미연결 게시판 경고. 다른 그룹에서 켜서 보내면 400.
- **연결 유형**(코드 레지스트리 — 유형별 리졸버, `target_type` 에 CHECK 없음):
  - `BOARD` — `targetId` = `boards.id`. 기본 이름 = 게시판 이름, 주소 `/boards/{slug}`, matchPrefix. 꺼진 게시판은 공개에서 빠지고 관리자 상태 `BOARD_INACTIVE`. **한 그룹 안에서 한 번**(다른 그룹엔 또 가능).
  - `BUILTIN` — `targetKey` = 앱 화면 키(`UtilMenu.BUILTINS` 와 같은 표: key·주소·matchPrefix·로그인 필요·판매 스위치 필요). 한 그룹 안에서 한 번. 로그인 필요 키는 `audience` 가 `MEMBER`·`GRADE_MIN`·`STAFF` 가 아니면 400.
  - `PATH` — `settings {path, newTab}`, 이름 필수, 주소 규칙 = `UtilMenu.pathProblem`. 중복 허용.
  - `URL` — `settings {href, newTab}`, 이름 필수, 주소 규칙 = `UtilMenu.hrefProblem`. 중복 허용. 공개 응답에서 `https://` 면 `extra.external = true`.
  - 준비 중(서버 유형 없음, 화면 표시만): `PAGE`(고정 페이지).
  - 모르는 유형 행은 공개에서 빠지고 관리자 상태 `UNKNOWN_TYPE`, 저장 때는 받은 그대로 보존.
- **상태**(관리자 `resolved.status`): `OK`·`BOARD_INACTIVE`·`SALES_OFF`·`TARGET_MISSING`·`UNKNOWN_TYPE`. 공동구매 판매 스위치 = `commerce_sales_settings.groupbuy_open`(DB 의 지금 값, `MenuSales`) — 꺼지면 `GROUP_BUYS`·`GROUP_BUY_ORDERS` 가 `SALES_OFF` 로 공개 메뉴에서 빠진다(2026-11-07). 운영 주체가 개인 단계면 판매를 켤 수 없어 늘 빠진다.
- **게시판과의 연결**: 게시판을 만들면 `main` 그룹의 `autoAdd` 폴더 끝에 같은 트랜잭션으로 넣는다(폴더 없거나 한도 초과면 넣지 않음). 설치 프리셋으로 만든 게시판도 같다. 게시판을 지우면 그 게시판을 가리키는 항목을 모든 그룹에서 지운다. 게시판 목록 순서(`boards`)와 메뉴 순서는 별개다.
- **공개 API** `GET /api/menu?set=main`(기본 `main`) — 보는 사람 기준 걸러진 트리(visible → audience → 리졸버 → 빈 폴더 제거). 꺼진 그룹은 `nodes: []`, 없는 그룹 404 `MENU_SET_NOT_FOUND`. 폴더는 `kind: 'GROUP'`. `mobileTabs` 는 SIDE 그룹에서만.

## 관리자 사이트 메뉴
- 모드 둘(`normal`·`settings`) 고정, 두 층: `SECTION`(최상위만) → `SCREEN` 또는 `FOLDER` → 하위 `SCREEN`(아이콘 없음). 유형 `ADMIN_SCREEN`(`targetKey` = 관리자 화면 키) · `URL`(권한 하나 선택 가능).
- 화면 레지스트리(키 → 주소·권한)는 서버에도 둔다(`UtilAdminMenu.ITEMS` 잎과 같다). 한 화면은 두 모드 통틀어 한 번. 화면 권한은 요청으로 바꿀 수 없다.
- 행이 없으면 레지스트리 기본 구성(지금 `UtilAdminMenu.ITEMS`)을 돌려준다.
- **관리자 셸은 저장한 구성을 그린다**(2026-11-07 사용자 지시 「DB 메뉴 표에서 읽어 보여 줘야지」): `GET /api/admin/admin-menu/shell`(로그인한 운영자 누구나, 조회와 같은 모양) → `UtilAdminMenu.fromConfig` → `useAdminMenuTree`(셸이 뜰 때 한 번, 저장하면 바로 바꿔 끼움). 숨김 노드·레지스트리에 없는 화면은 빠지고, 권한 거르기는 화면 레지스트리 권한(외부 링크는 고른 권한)으로 화면이 한다. 받기 전에는 빈 메뉴, 못 받으면 메뉴 정의(`ITEMS`)로 그린다.
  메뉴에 없는 화면(편집 화면의 「메뉴에 없는 화면」)은 셸에도 없다 — 저장한 적이 있으면 **새로 만든 화면은 [넣기] 해야 보인다**. 주소로 들어오면 권한(`canOpen`, 레지스트리)대로 열린다. 사이드바·상단 메뉴·검색·역할 미리보기가 같은 노드를 쓰고, 대시보드 바로가기 후보는 레지스트리 그대로다.

## API
`useMenuApi.ts` 머리말 그대로: `/api/admin/menu-sets…`(목록·만들기·수정·삭제·트리 조회·트리 전체 교체 `version`·미리보기 후보) · `GET /api/admin/menu/target-options?set=` · `GET·PUT /api/admin/admin-menu` · 공개 `GET /api/menu`. 오류: 400 `VALIDATION` + `fields["nodes[i].children[j].<칸>"]` · 409 `MENU_VERSION_CONFLICT`·`MENU_SET_KEY_DUPLICATE`·`MENU_SET_SYSTEM` · 404 `MENU_SET_NOT_FOUND`. 감사: `MENU_SET_CREATE`·`MENU_SET_UPDATE`·`MENU_SET_DELETE`·`MENU_TREE_UPDATE`·`ADMIN_MENU_UPDATE`.

## 서버 구현 결과 (2026-10-22) — 화면 연결은 후속
**DB** shared `V43__menus.sql`(`menu_sets` · `menu_nodes`, 그룹별 게시판 부분 유일 색인, 시드 3그룹 — main 은 HOME + 자동 추가 폴더 「게시판」에 기존 게시판) · admin `V2__admin_menu.sql`(`admin_menu_state` 1행 + `admin_menu_nodes`, admin 서버만 쓰므로 admin 스트림·JDBC).
**API** `useMenuApi.ts` 머리말 그대로 + `POST …/{key}/preview`·`GET /api/admin/menu/target-types` 구현(화면은 아직 안 부름). 공개 `GET /api/menu` 는 로그인 없이 열림, `Cache-Control: no-store`, 캐시 없음. 목업과 다른 점:
- `version` 은 숫자 문자열. 트리 응답 항목에 `resolved: { status }` 추가.
- fields 경로: BUILTIN 은 `.targetKey`, PATH `.settings.path`, URL `.settings.href`, 깊이 초과는 그 줄의 `.kind`, 트리 전체 문제는 `fields.nodes` 에 첫 문제 하나.
- 더 거절(400): SIDE 아닌 그룹의 `mobileTab`·`autoAdd`, 없는 게시판·모르는 BUILTIN 키(`menu.target.missing`), 등급 목록에 없는 `minGrade`. 관리자 메뉴: 레지스트리에 없는 `labelKey`, 카탈로그에 없는 URL `permission`, 두 모드 합계 노드 200 초과.
- 게시판 만들기·삭제도 해당 그룹 `version` 을 올린다(메뉴 화면을 열어 둔 채 저장하면 409). 바뀐 것 없는 저장은 version·감사 기록 그대로.
- 관리자 메뉴를 저장한 적이 없으면 기본 구성을 `id: null` 로 준다. 부모 항목 안의 화면은 아이콘을 지운다.
- 동시 저장: `menu_sets` 행·`admin_menu_state` 행 잠금 후 version 비교. 트리 저장은 그룹 노드 일괄 삭제 후 재삽입(같은 그룹의 ID 는 재사용).
**보는 사람 판정**: STAFF = 게시판 매니저·스텝(`board_staff`)을 하나라도 맡은 회원(**2026-10-22 사용자 확정** — 운영자는 공개 사이트에서 일하지 않으므로 공개 메뉴의 STAFF 는 이 뜻뿐이다). 등급은 `profiles.grade_key` → `grades.level`. 회원이 없거나 탈퇴한 세션은 GUEST.
**group-buy 시드**: 목업의 PATH `/boards/notice` 는 게시판 존재 보장이 없어 뺐다.

## 설치 프리셋의 메뉴 (2026-10-27 확정, 구현 전)
설치 때 `main` 트리를 프리셋 JSON 의 `menu` 로 바꾸고(ROBOYAM: 홈·인기글·최신글·「주제」·「커뮤니티」(autoAdd)·「소모임」·공지), `mypage` 의 V49 숨김을 화면이 있는 항목만 풀고 `MY_SHOP` 을 넣는다(새 마이그레이션). 정본은 `install-contract.md` 「프리셋 구조」.

## 구분선·설명 (화면 확정 2026-10-28 · 서버 구현 2026-10-29 — 화면은 아직 관리자 목업 `composables/menu/api/mock/menu-extra-mock.ts`)
- 공개 메뉴 노드(폴더·항목)에 `divider`: `NONE`(기본) · `BEFORE` · `AFTER` — 공개 사이트 상단 메뉴 첫 줄에서 그 칸 앞/뒤에 세로 구분선(예: 나중에 「공동구매 · 핫딜」 묶음 앞). 관리자 항목·폴더 대화상자 `MenuDividerField`.
- 항목에 `description`(80자까지, 최상위 항목에서만 뜻) — 공개 사이트 상단 메뉴 둘째 줄의 메뉴 설명(운영자 설정 「메뉴 설명 줄」이 켜졌을 때, `useMenuDescription`). 비우면 기본 설명(인기글·최신글 기본 문구, 게시판 소개).
- 서버(shared V105 `menu_nodes.divider`·`description`): 트리 `GET/PUT /api/admin/menu-sets/{key}/tree`·미리보기의 노드 — 폴더 `divider`, 항목 `divider`·`description`. 공개 `GET /api/menu` — 그룹 `divider`, 항목 `divider`·`description`(없으면 null).
  요청에서 `divider` 가 빠지면 `NONE`, 목록 밖은 `400 fields.<경로>.divider`. `description` 은 앞뒤 공백을 지우고 비면 null, 80자 초과·`<`·`>`·줄바꿈은 `fields.<경로>.description`. 폴더의 `description` 은 버린다. 어느 항목에나 저장되고 「최상위 항목에서만 뜻」은 화면 규칙이다.
  메뉴 설정(배치·모양)은 `site-theme-layout-contract.md`.

## 폴더 연결 · 사이트 화면 통합 (화면 확정 2026-10-29)
- **폴더(부모 메뉴)의 「누르면 가는 곳」**(선택): 게시판 · 사이트 화면 · 외부 링크 — 항목과 같은 칸(`targetType`·`targetId`·`targetKey`·`settings`)·같은 검사. 연결이 없으면 넷 다 null(지금처럼 펼치기·접기만).
  - 폴더 연결은 「한 그룹 안 한 번」(게시판·앱 화면)에 세지 않는다 — 「소모임」 폴더 → 전체 소모임과 그 안의 「전체 소모임」 항목이 함께 있어도 된다. 로그인 필요 화면의 보이는 대상 규칙도 폴더에는 걸지 않는다.
  - `linkClick`(연결 있을 때만): `NAVIGATE_OPEN` 이동하고 하위 메뉴도 열기(기본) · `NAVIGATE` 이동만. 공개 사이트 **상단 첫 줄의 그룹 탭**(다음식 GNB·사이드 그룹 탭·모바일 첫 줄)에만 영향 — 이동만이어도 간 곳이 그 폴더 안이면 「지금 있는 곳이 든 그룹」 규칙으로 하위 메뉴가 보인다.
  - 공개 `GET /api/menu` 그룹: 연결 대상이 이 사람에게 항목이었다면 보였을 때만 `path`·`newTab`·`extra`·`linkClick`. 못 보이면(꺼진 게시판·판매 꺼짐·비로그인에 로그인 필요 화면) 폴더 이름만. 연결이 빈 폴더를 살리지 않는다.
  - 공개 화면: 세로 메뉴·햄버거·상단 막대·드롭다운·모바일 둘째 줄의 그룹 제목은 이름 = 링크, 펼치기·접기는 옆 화살표(`AppMenuGroupLabel`). 첫 줄 그룹 탭은 링크 + (`NAVIGATE_OPEN` 이면) 이동 뒤 그 그룹을 연다(`useAfterMenuNavigate`).
  - 게시판을 지우면 그 게시판을 가리키는 폴더는 지우지 않고 연결만 지운다.
  - 개발 모드 미리보기 `?groupLink=소모임:/groups,주제:/boards!`(끝 `!` = 이동만) — 서버 `path` 가 있으면 서버 값이 이긴다.
- **「앱 화면」 + 「페이지」 → 「사이트 화면」**(화면에서만 합침): 목록에서 고르기(`BUILTIN`) · 주소 직접 입력(`PATH`). 서버 유형·기존 데이터는 그대로. 직접 입력으로 바꾸면 고른 화면의 주소·이름을 채우고, 목록으로 돌아오면 같은 주소의 화면을 고른다.
- 이미 만든 항목도 [종류 바꾸기]로 연결 종류를 바꿀 수 있다 — 대상 칸만 비우고 이름·보이는 대상·탭·구분선·설명·ID 는 그대로(`UtilMenu.retarget`).

## 가젯 페이지 연결 (화면 확정·서버 구현 2026-10-29 — gadget-page-contract.md)
- 새 연결 유형 **`GADGET_PAGE`** — `targetId` = 가젯 페이지 ID(영문 주소가 바뀌어도 따라간다). `targetKey`·`settings` 는 null. 항목과 폴더 연결 모두 된다.
- 기본 이름 = 페이지 이름(+영문 이름), 공개 주소 `/p/{key}`(그 아래 주소도 밝다). 한 그룹 안에서 항목은 한 번(`GADGET_PAGE:<id>`). 로그인·판매 조건 없음.
- 없는 페이지를 보내면 `400 …targetId`(`menu.target.missing`), 저장된 연결의 페이지가 없어지면 상태 `TARGET_MISSING`(공개 메뉴에서 빠지고 폴더는 이름만).
- 관리자 대상 고르기: 화면 종류는 「사이트 화면」 — 목록 맨 위 「가젯 페이지」 묶음(`target-options.gadgetPages`). 「주소 직접 입력」으로 바꾸면 `/p/{key}` 가 `PATH` 로 옮겨진다.
- 페이지를 지우면 그 페이지를 가리키는 항목은 모든 그룹에서 지우고 폴더는 연결만 비운다(게시판 삭제와 같은 규칙, 바뀐 그룹 버전을 올린다).

## 미확정 사항
1. ~~저장한 관리자 메뉴를 실제 관리자 셸이 언제부터 읽을지~~ → 2026-11-07 셸이 읽는다(위 「관리자 사이트 메뉴」).
2. ~~공동구매 판매 스위치~~ → 2026-11-07 `MenuSales` 가 실제 스위치를 읽는다(위 「상태」).
3. `PAGE`(고정 페이지) 유형은 CMS 기능이 생길 때.

## 화면 실제 연결 결과 (2026-10-22)
- **admin-web** `composables/menu/api/useMenuApi.ts` 의 모든 호출이 서버(`MenuSetController`·`MenuTargetController`·`AdminMenuController`)를 부른다. 목업 `composables/menu/api/mock/menu-mock.ts` 와 그 시드 전용 `UtilAdminMenuConfig.seed()` 는 지웠다. 포인트 목업이 쓰는 `common/mock/mock-util.ts` 는 남겼다.
- 타입: `MenuItemDto.resolved?: { status }` 를 더했다 — 화면은 같은 규칙(`UtilMenu.itemStatus`)으로 상태를 스스로 계산하고 저장 요청에 싣지 않는다. `version` 은 뜻을 읽지 않고 그대로 돌려보낸다.
- 409 `MENU_VERSION_CONFLICT`: 저장 줄에 서버 문구를 그대로 보이고 [다시 불러오기]로 최신 트리를 받는다(공개·관리자 메뉴 같음). 서버 `fields` 경로(`.settings.path`·`.settings.href`·`.targetKey`·`.kind`)는 기존 `UtilMenuTree.splitFieldErrors` 가 줄에 붙이고, `fields.nodes` 는 저장 줄 문구로 모은다.
- **user-web** `composables/menu/api/useMenuApi.ts` 가 `GET /api/menu`(기본 `main`)를 부른다. 목업 폴더는 지웠다. 못 받으면 메뉴 줄 대신 「메뉴를 불러오지 못했어요」 + 다시 시도만 둔다 — **화면 코드가 따로 만드는 기본 메뉴(폴백)는 2026-11-07 지웠다**(사용자 결정: 보이는 규칙이 서버와 두 곳으로 갈려 판매 꺼짐 같은 규칙을 놓쳤다).
- 게시판 순서: 공개 내비게이션 순서의 주인은 메뉴(`main` 그룹). `/boards` 표 아래 안내를 「게시판 목록·글쓰기 게시판 고르기 순서」로 고치고 `layout.read` 가 있으면 `/menus/public` 링크를 둔다.
- 일회용 환경 확인(develop jar + 새 DB, 설치 API 로 설치): 공개 메뉴 트리 조회·폴더 추가 저장·새로고침 뒤 유지, 다른 탭에서 게시판을 만든 뒤 열어 둔 트리 저장 → 409 문구 + 다시 불러오기로 새 게시판 포함 트리, 공개 사이트 내비에 새 게시판 표시, `GET /api/menu` `Cache-Control: no-store`, 관리자 메뉴 화면 조회. 관리자 메뉴 **저장**은 화면에서 눌러 보지 않았다.

## 세션 정리 결정 (2026-10-22)
- **공개 내비게이션 순서의 주인은 메뉴(main 그룹)**다. 게시판 `sort_order` 는 게시판 목록 API·글쓰기 게시판 고르기·폴백 메뉴·새 게시판의 처음 위치에만 쓰인다(`board-contract.md` 같은 절).
- **관리자 메뉴의 원천은 아직 코드**다 — 사이드바는 `util-admin-menu.ts` 의 ITEMS 를 그리고, 저장한 관리자 메뉴 구성(admin V2)은 셸이 아직 읽지 않는다. 새 화면은 ITEMS 한 줄 + `menu.ts` ko/en + 서버 `AdminScreenRegistry` 한 줄을 **같게** 더한다(두 곳이 어긋나면 관리자 메뉴 관리 화면에 그 화면이 빠진다). 셸이 저장 구성을 읽게 되면 역할 화면의 메뉴 미리보기도 같은 원천으로 바꾼다.
- **없는 화면을 가리키는 메뉴 시드는 숨긴다**(mypage·group-buy 그룹 중 user-web 에 화면이 없는 주소) — 화면이 생길 때 보이게 바꾼다.
- **(2026-10-29) 연결 유형의 화면 이름**: 정본은 서버 유형 넷(`BOARD`·`BUILTIN`·`PATH`·`URL`)이고, 운영자 화면은 `BUILTIN`·`PATH` 를 「사이트 화면」 한 종류로 보인다(`UtilMenu.TARGET_KINDS`). 새 유형도 서버 표에 먼저 더하고 화면 종류는 그 뒤에 정한다.
- **(2026-10-29) 가젯 페이지 유형**: 서버 유형은 다섯(+`GADGET_PAGE`)이 됐고 화면은 `BUILTIN`·`GADGET_PAGE`·`PATH` 를 「사이트 화면」으로 보인다. 「준비 중」 `PAGE`(고정 페이지 — CMS)와는 다른 유형이다(정본: 위 「가젯 페이지 연결」).
- **(2026-10-29) 폴더 연결은 바로가기다** — 메뉴 항목이 아니므로 한 번 규칙·모바일 탭·미연결 게시판 계산·지금 있는 곳 판정에 넣지 않는다.
- 포인트 관리(`/points`)는 목업 화면이지만 메뉴는 `point.read` 보유 여부로만 감춘다(권한의 「준비 중」은 역할 권한 고르개 표시에만 쓰인다). 화면 확정 전 검토용으로 최고 관리자에게는 보인다 — 포인트 백엔드가 생길 때 서버 `Permission` 의 `point.*` 를 준비됨으로 바꾼다.

## 메뉴 항목 글자 색·강조 (2026-11-11 사용자 확정)
- 의도: 커뮤니티 오른쪽 게시판 목록 같은 공개 메뉴에서 특정 항목을 눈에 띄게. 이름 옆 점(●)은 지금처럼 게시판 표시색(`boards.color`, 12색)이고 이것과 따로다.
- 관리자: 메뉴 › 항목 고치기 창에 「글자 색」(기본 / 12색 `BoardFields.COLORS` 키 / 직접 고르기 `#RRGGBB`) · 「강조」(굵게 체크, 배지 글자 1~4자 — 배지 색 = 글자 색, 글자 색이 기본이면 주 색).
- 데이터(shared V186): `menu_nodes.text_color varchar(16) NULL`(NULL = 기본, 12색 키 또는 `#RRGGBB` 대문자로 정리) · `menu_nodes.bold boolean NOT NULL DEFAULT false` · `menu_nodes.badge varchar(8) NULL`(앞뒤 공백 정리, 빈 값 NULL, 4자까지 — 코드포인트 기준).
- API: 관리자 메뉴 노드 응답·저장 요청에 `textColor`·`bold`·`badge`, 공개 메뉴(`GET /api/menu`) 줄에 `textColor`·`bold`·`badge`. 잘못된 색·5자 이상 배지는 400 `fields.textColor`/`fields.badge`.
- 공개 표시: 메뉴 노드 이름을 그리는 모든 곳이 같은 부품(`AppMenuItemLabel` — 규칙 `UtilMenu.textStyle`·`labelClass`·`badgeStyle`) — 상단 메뉴·드롭다운·커뮤니티 오른쪽 목록(AppMenuList)·모바일 메뉴·다음·네이버 테마 막대·모바일 상단 탭·로고 옆 링크·푸터 링크·게시판 모음(/boards). 좁은 칸은 작은 배지(`compact`), 이미 더 굵은 칸(다음 테마 700 등)은 「굵게」로 얇아지지 않게 그 굵기 유지. 하단 탭(고정 6칸)은 메뉴 줄이 아니라 제외. 12색은 기존 라이트/다크 짝(`UtilColor.cssPair`), 직접 고른 색은 다크 모드에서 읽히게 밝게 섞은 짝을 자동으로 쓴다.
- 설치 프리셋은 `export-install-preset.mjs` 가 칸을 DB 에서 읽으므로 자동으로 따라온다.
- **(2026-11-11) 폴더(그룹)도 같다** — 관리자 폴더 고치기 창에 같은 「글자 색」·「강조」 칸(점 안내만 뺀다), 트리 줄·미리보기의 폴더 제목에도 같은 색·굵게·배지. 공개 메뉴 그룹 줄(`kind: "GROUP"`)의 `textColor`·`bold`·`badge` 를 상단 막대 그룹 제목·드롭다운 머리와 소제목·세로 메뉴 그룹 제목과 소제목·모바일 첫 줄 그룹 단추와 둘째 줄 그룹 제목에 항목과 같은 규칙(`AppMenuItemLabel`)으로 그린다. 기본(null)이면 제목의 흐린 색 그대로, 고른 색은 펼침·고른 상태에서도 유지된다.
- **(2026-11-11) 테마 메뉴 설정 「메뉴 글자 기본 굵게」** — `site_theme_menus.menu_bold`(shared V187, 켬·끔 · 다음·로보얌 테마(같은 막대 — 예전부터 굵게)만 기본 켬, 행이 없는 테마의 처음 값도 `SiteThemeMenus.initialOf` 가 같은 규칙). 관리자 `GET /api/admin/site/theme`·`PUT /api/admin/site/themes/{id}/menu` 의 `menuBold`(PUT 에서 빠지면 지금 값 유지), 공개 `GET /api/site/theme` 의 `menuBold`. 켜면 막대 칸이 굵게(700)·이름은 `keep-weight`, 끄면 얇게·「굵게」 항목만 600 — 다음식(로보얌 포함)·네이버·기본 상단 막대.
