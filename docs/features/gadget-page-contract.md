# 가젯 페이지 계약 (**화면 확정 2026-10-29**)

**의도**: 홈처럼 가젯 격자로 꾸미는 공개 페이지를 여러 개 만든다(사용자 결정 2026-10-29 — 「커뮤니티」 전용 화면이 아니라 일반화). 첫 페이지 「커뮤니티」, 다음 「주제」.
페이지 정보(이름·설명·영문 주소·기준 폴더·글쓰기 단추)는 하나, 배치는 홈처럼 **테마마다 따로**다. 메뉴 폴더의 「누르면 가는 곳」이 페이지를 가리킬 수 있다.
배치·가젯 모양·편집 방식(실제 화면 iframe 편집)은 `site-theme-layout-contract.md` 의 홈 구성과 같다 — 여기에는 다른 점만 적는다.

## 화면 (기준 코드)
- 관리자 `설정 › 테마 › 레이아웃` 의 페이지 종류 「페이지」 = `/settings/theme?kind=page&page=<id>`(`pages/settings/theme/[[tab]].vue`).
  - 고르기 줄 `components/theme/GadgetPagePicker.vue`(목록 · 공개 주소 새 탭 · [페이지 정보] · [새 페이지], 없으면 빈 안내) · 만들기/고치기 창 `GadgetPageDialog.vue`(처음 배치 미리, [페이지 지우기]).
  - 편집은 홈과 같은 `HomeLayoutEditor`(prop `page`) + `ThemeLivePreview`(prop `path` `/p/{key}` · `page` 머리). 「테마 기본 배치로」 대신 「처음 배치로」.
  - 가젯 ⚙ 설정 창 `GadgetSettingsDialog.vue` — 칸·범위·처음 값의 원천은 admin `utils/util-gadget-config.ts`(아래 「가젯 설정」). 홈에서도 같은 창이다.
- 공개 `/p/{key}`(user-web `pages/p/[key].vue`) — 고정 머리 `components/page/GadgetPageHeader.vue`(이름 h1 · 설명 · 글쓰기 단추) + 지금 테마의 홈 템플릿(`HomeGadgetGrid`). 없는 페이지는 「찾을 수 없어요」.
- 메뉴 관리: 「누르면 가는 곳 › 사이트 화면 › 목록에서 고르기」 맨 위 「가젯 페이지」 묶음(연결 유형 `GADGET_PAGE`, 아래 「세션 정리 결정」).

## 필드·검증
- `key` 영문 주소: `^[a-z][a-z0-9-]{1,29}$`, 겹치지 않음(`400 fields.key`). 공개 주소 `/p/{key}`. 바꿔도 메뉴 연결은 페이지 ID 라 따라간다(옛 주소는 열리지 않는다).
- `name` 1~40자(필수) · `nameEn` 0~40자(빈 글자 = null) · `description` 0~200자 · 줄바꿈·제어 문자 금지(이름).
- `folderId` 기준 폴더 = 공개 메뉴 `main` 의 폴더 ID(깊이 무관) 또는 null. 없는 폴더·폴더가 아닌 노드는 `400 fields.folderId`. 폴더가 나중에 지워지면 기준 폴더 없음으로 본다(FK 없음 — 메뉴 저장은 트리를 지우고 다시 넣는다).
  - 폴더 게시판 = 그 폴더 아래(하위 폴더까지) `BOARD` 항목의 게시판, 메뉴 차례, 겹치면 한 번. 꺼진 게시판은 공개 데이터에서 빠진다.
- `writeButton` 참/거짓. 글쓰기 단추는 `/write?board=<기준 폴더의 첫 켜진 게시판>`(없으면 `/write`) — 판정은 글쓰기 화면·서버.
- 페이지 수 한도 50.
- 처음 배치(만들 때 서버가 기준 폴더의 **켜진** 게시판으로 만든다 — 서버 `GadgetPageInitial`, admin `UtilGadgetPage.initialLayout` 은 만들기 창의 미리 보기, 24칸 · 줄마다 꽉 차게, 가젯 ID `seed-<n>`):
  「{폴더} 인기글」(POST_LIST POPULAR·기간 탭·대상 FOLDER, 24칸) / 「{게시판}」(GALLERY PICK 8개, 24칸 — 사진·영상 게시판: 목록 모양 `GALLERY` 인 첫 폴더 게시판, 없으면 갤러리 보기 전환(`viewToggle`)이 켜진 첫 게시판, 없으면 이 줄 없음) /
  게시판 카드(BOARD_CARDS, 갤러리로 쓴 게시판을 뺀 폴더 게시판마다 하나 PICK, 12+12 두 열, 홀수면 마지막 24칸) / 「{폴더} 최신글」(POST_LIST LATEST 16개 두 열·대상 FOLDER, 24칸). 기준 폴더가 없으면 이름 자리는 「전체」.
  커뮤니티 예(사용자 결정 2026-10-29): 커뮤니티 인기글 / 영상·짤(전체 폭) / 자유게시판 · 유머게시판(두 열) / 커뮤니티 최신글.
  높이(h = 최소 높이, 행): 인기글 15 · 갤러리 11 · 게시판 카드 줄 13 · 최신글 15 — 보통 내용에 맞췄고(내용이 길면 격자가 늘린다), 같은 행·같은 h 로 나란한 가젯은 공개 격자가 가장 긴 가젯 높이로 함께 늘려 아래 끝을 맞춘다(user-web `UtilHomeGrid.place` 「같은 줄 맞추기」).
  저장한 적 없는 테마는 이 처음 배치를 보인다(꾸미기는 그 테마의 기본 꾸미기 + 아이콘 — 로보얌 테마는 홈 로보얌 시드처럼 흰 카드 + 아이콘 머리). 기준 폴더를 바꿔도 처음 배치는 다시 만들지 않는다.

## 가젯 설정(⚙ — 홈과 가젯 페이지 공통, 2026-10-29 넓힘)
| 종류 | 칸(서버 JSON `config`) |
|---|---|
| `GROUP_BUY` | `count` 1~10(기본 5) · `sort` DEADLINE·NEWEST·POPULAR (기존) |
| `POST_LIST` | `boardScope` · `boardIds` · `count` 1~30(기본 10) · `sort` LATEST·POPULAR·VIEWS·LIKES·COMMENTS · `periodTabs`(POPULAR 일 때만 참) |
| `GALLERY` | `boardScope` · `boardIds` · `count` 1~24(기본 6) |
| `BOARD_CARDS` | `boardScope` · `boardIds`(카드로 보일 게시판) · `perBoard` 1~10(기본 4) |
- `boardScope`: `ALL` 전체(글 목록·갤러리는 최신글과 같은 범위, 게시판 카드는 켜진 일반 게시판 앞 6개) · `FOLDER` 이 페이지의 기준 폴더 게시판 전체(가젯 페이지에서만 — 홈에서 보내면 400) · `PICK` `boardIds`(게시판 slug 1~20개, 고른 차례).
  저장된 설정에 `boardScope` 가 없으면(옛 시드) `boardIds` 가 있으면 PICK, 없으면 ALL 로 읽는다. FOLDER 인데 기준 폴더가 없거나 게시판이 없으면 ALL 과 같다.
- 저장: 보낸 칸만 저장 설정에 덮는다(꾸미기용 다른 칸 — `rowStyle` 등 — 은 그대로). 오류 `400 fields.gadgets[i].config.<칸>`.
- 관리자 응답의 `config` 는 이 표의 칸만(저장 값, 없으면 처음 값). 다른 종류는 null.

## 권한·정책
- 조회 `site.read`, 만들기·고치기·지우기·배치 저장 `site.manage`. 공개 조회는 로그인 없이(보는 사람 몫 — 차단·메모 — 이 섞여 `no-store`).
- 지우기: 모든 테마 배치·가젯을 함께 지우고, 이 페이지를 가리키는 메뉴 **항목은 지우고 폴더는 연결만 비운다**(게시판 삭제와 같은 규칙, 바뀐 그룹 버전을 올린다).
- 감사: `GADGET_PAGE_CREATE`·`GADGET_PAGE_UPDATE`(바뀐 칸 전후)·`GADGET_PAGE_DELETE`·`GADGET_PAGE_LAYOUT_UPDATE`(테마 · 칸 수·가젯 수 전후 · 넣은·뺀 수). 관리자 알림은 사이트 설정 변경(변형 `gadgetPage`).

## API (서버 구현 2026-10-29 — 마이그레이션 shared V108)
| 주소 | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /api/admin/gadget-pages/folders` | `site.read` | → `{ folders[{ id, name, nameEn, label, boards[{slug,name,nameEn,layout,viewToggle,active}] }] }` — 기준 폴더로 고를 main 의 폴더(트리 차례, 하위 폴더 게시판까지) |
| `GET /api/admin/gadget-pages` | `site.read` | → `{ pages: GadgetPage[] }` — `{ id, key, name, nameEn, description, folderId, folderName, folderBoards[{slug,name,nameEn,layout,viewToggle,active}], writeButton, menuLinks, updatedAt, updatedBy }` |
| `POST /api/admin/gadget-pages` (CSRF) | `site.manage` | `{ key, name, nameEn, description, folderId, writeButton }` → `GadgetPage` · `400 fields.key|name|nameEn|description|folderId`·`fields.pages`(한도) |
| `PUT /api/admin/gadget-pages/{id}` (CSRF) | `site.manage` | 같은 본문(칸 전부) → `GadgetPage` · `404` · 같은 400 |
| `DELETE /api/admin/gadget-pages/{id}` (CSRF) | `site.manage` | → 200 `null` · `404` |
| `GET /api/admin/gadget-pages/{id}/layouts/{theme}` | `site.read` | → `{ pageId, theme, layout, gadgets[{id,type,title,desktop,mobile,config}], updatedAt, updatedBy }` — 저장한 적 없으면 처음 배치(`updatedAt: null`) · 모르는 테마·페이지 `404` |
| `GET /api/admin/gadget-pages/{id}/initial` | `site.read` | → `{ layout, gadgets }` 처음 배치(저장 안 함) |
| `PUT /api/admin/gadget-pages/{id}/layouts/{theme}` (CSRF) | `site.manage` | `{ layout, gadgets[{id,type,title,config?}] }` → 위 모양 — 홈 저장과 같은 검사·같은 가젯 규칙(시드 `seed-<n>` = 처음 배치의 가젯) |
| `GET /api/pages/{key}` (공개, `no-store`) | — | → `{ page{key,name,nameEn,description,writeButton,writeBoard}, theme, layout, gadgets[...] }`(가젯은 홈 가젯 응답과 같은 모양) · 없는 페이지 `404 GADGET_PAGE_NOT_FOUND` |
| `GET /api/admin/menu/target-options` | (기존) | 응답에 `gadgetPages[{id,key,name,nameEn}]` 추가 |

## 미확정 사항
- 페이지마다 SEO 설명·대표 이미지, 페이지 머리 꾸미기(배경·아이콘)는 없다.
- 「주제」 페이지는 같은 방식으로 운영자가 만든다(기준 폴더 = 주제).
- 관리자 미리보기에서 새로 넣은 가젯은 공개 응답의 같은 종류 데이터를 빌려 보인다(저장하면 서버가 설정대로 고른다 — 홈과 같다).

## 세션 정리 결정 (2026-10-29)
- 메뉴 연결 유형 `GADGET_PAGE` 의 정본은 `menu-contract.md` 「가젯 페이지 연결」. 이미 「준비 중」으로 있던 `PAGE`(고정 페이지 — CMS)와 다른 유형이다.
- 가젯 ⚙ 설정 칸(위 표)의 정본은 이 문서다 — `site-theme-layout-contract.md` 의 「공구 가젯만」 서술은 이 표로 넓어졌다.
