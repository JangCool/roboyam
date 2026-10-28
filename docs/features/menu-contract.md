# 메뉴 관리 계약 (**관리자 화면 확정 2026-10-22** — 이번 범위는 백엔드 구현, 화면 연결은 후속)

**의도**: 공개 사이트 메뉴와 관리자 사이트 메뉴를 관리자 앱에서 구성한다. 공개 메뉴는 **메뉴 그룹**(좌측 사이드·마이페이지·공동구매·사용자 정의) 마다 트리를 따로 갖고, 트리는 폴더와 항목(게시판·앱 화면·페이지·외부 링크 연결)으로 이루어진다. 관리자 메뉴는 일반/설정 두 모드의 사이드 메뉴 순서·묶음·이름·아이콘·표시를 바꾼다(권한은 화면이 정한다).
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
- **상태**(관리자 `resolved.status`): `OK`·`BOARD_INACTIVE`·`SALES_OFF`·`TARGET_MISSING`·`UNKNOWN_TYPE`. 공동구매 판매 스위치는 roboyam 에 아직 없다 → `salesEnabled = true` 고정(스위치가 생기면 거기서 읽는다).
- **게시판과의 연결**: 게시판을 만들면 `main` 그룹의 `autoAdd` 폴더 끝에 같은 트랜잭션으로 넣는다(폴더 없거나 한도 초과면 넣지 않음). 설치 프리셋으로 만든 게시판도 같다. 게시판을 지우면 그 게시판을 가리키는 항목을 모든 그룹에서 지운다. 게시판 목록 순서(`boards`)와 메뉴 순서는 별개다.
- **공개 API** `GET /api/menu?set=main`(기본 `main`) — 보는 사람 기준 걸러진 트리(visible → audience → 리졸버 → 빈 폴더 제거). 꺼진 그룹은 `nodes: []`, 없는 그룹 404 `MENU_SET_NOT_FOUND`. 폴더는 `kind: 'GROUP'`. `mobileTabs` 는 SIDE 그룹에서만.

## 관리자 사이트 메뉴
- 모드 둘(`normal`·`settings`) 고정, 두 층: `SECTION`(최상위만) → `SCREEN` 또는 `FOLDER` → 하위 `SCREEN`(아이콘 없음). 유형 `ADMIN_SCREEN`(`targetKey` = 관리자 화면 키) · `URL`(권한 하나 선택 가능).
- 화면 레지스트리(키 → 주소·권한)는 서버에도 둔다(`UtilAdminMenu.ITEMS` 잎과 같다). 한 화면은 두 모드 통틀어 한 번. 화면 권한은 요청으로 바꿀 수 없다.
- 행이 없으면 레지스트리 기본 구성(지금 `UtilAdminMenu.ITEMS`)을 돌려준다. **관리자 셸은 이번에 바꾸지 않는다**(저장만).

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

## 미확정 사항
1. 저장한 관리자 메뉴를 실제 관리자 셸이 언제부터 읽을지.
2. 공동구매 판매 스위치(공동구매 기능 생길 때).
3. `PAGE`(고정 페이지) 유형은 CMS 기능이 생길 때.

## 화면 실제 연결 결과 (2026-10-22)
- **admin-web** `composables/menu/api/useMenuApi.ts` 의 모든 호출이 서버(`MenuSetController`·`MenuTargetController`·`AdminMenuController`)를 부른다. 목업 `composables/menu/api/mock/menu-mock.ts` 와 그 시드 전용 `UtilAdminMenuConfig.seed()` 는 지웠다. 포인트 목업이 쓰는 `common/mock/mock-util.ts` 는 남겼다.
- 타입: `MenuItemDto.resolved?: { status }` 를 더했다 — 화면은 같은 규칙(`UtilMenu.itemStatus`)으로 상태를 스스로 계산하고 저장 요청에 싣지 않는다. `version` 은 뜻을 읽지 않고 그대로 돌려보낸다.
- 409 `MENU_VERSION_CONFLICT`: 저장 줄에 서버 문구를 그대로 보이고 [다시 불러오기]로 최신 트리를 받는다(공개·관리자 메뉴 같음). 서버 `fields` 경로(`.settings.path`·`.settings.href`·`.targetKey`·`.kind`)는 기존 `UtilMenuTree.splitFieldErrors` 가 줄에 붙이고, `fields.nodes` 는 저장 줄 문구로 모은다.
- **user-web** `composables/menu/api/useMenuApi.ts` 가 `GET /api/menu`(기본 `main`)를 부른다. 목업 폴더는 지웠다. 못 받으면 기존대로 `useMenu` 가 기본 메뉴(폴백)로 그린다.
- 게시판 순서: 공개 내비게이션 순서의 주인은 메뉴(`main` 그룹). `/boards` 표 아래 안내를 「게시판 목록·글쓰기 게시판 고르기 순서」로 고치고 `layout.read` 가 있으면 `/menus/public` 링크를 둔다.
- 일회용 환경 확인(develop jar + 새 DB, 설치 API 로 설치): 공개 메뉴 트리 조회·폴더 추가 저장·새로고침 뒤 유지, 다른 탭에서 게시판을 만든 뒤 열어 둔 트리 저장 → 409 문구 + 다시 불러오기로 새 게시판 포함 트리, 공개 사이트 내비에 새 게시판 표시, `GET /api/menu` `Cache-Control: no-store`, 관리자 메뉴 화면 조회. 관리자 메뉴 **저장**은 화면에서 눌러 보지 않았다.

## 세션 정리 결정 (2026-10-22)
- **공개 내비게이션 순서의 주인은 메뉴(main 그룹)**다. 게시판 `sort_order` 는 게시판 목록 API·글쓰기 게시판 고르기·폴백 메뉴·새 게시판의 처음 위치에만 쓰인다(`board-contract.md` 같은 절).
- **관리자 메뉴의 원천은 아직 코드**다 — 사이드바는 `util-admin-menu.ts` 의 ITEMS 를 그리고, 저장한 관리자 메뉴 구성(admin V2)은 셸이 아직 읽지 않는다. 새 화면은 ITEMS 한 줄 + `menu.ts` ko/en + 서버 `AdminScreenRegistry` 한 줄을 **같게** 더한다(두 곳이 어긋나면 관리자 메뉴 관리 화면에 그 화면이 빠진다). 셸이 저장 구성을 읽게 되면 역할 화면의 메뉴 미리보기도 같은 원천으로 바꾼다.
- **없는 화면을 가리키는 메뉴 시드는 숨긴다**(mypage·group-buy 그룹 중 user-web 에 화면이 없는 주소) — 화면이 생길 때 보이게 바꾼다.
- 포인트 관리(`/points`)는 목업 화면이지만 메뉴는 `point.read` 보유 여부로만 감춘다(권한의 「준비 중」은 역할 권한 고르개 표시에만 쓰인다). 화면 확정 전 검토용으로 최고 관리자에게는 보인다 — 포인트 백엔드가 생길 때 서버 `Permission` 의 `point.*` 를 준비됨으로 바꾼다.
