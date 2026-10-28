# 테마 · 레이아웃 · 메뉴 설정 계약 (**화면 확정 2026-10-28 · 서버 구현 2026-10-29** — 화면 연결은 다음 단계)

**의도**: 운영자가 공개 사이트 테마(기본 · 다음식 · 네이버식 · 로보얌)를 고르고, **그 테마의** 레이아웃(지금은 홈 구성 — 게시판·페이지 레이아웃은 자리만)과 메뉴 설정(배치·모양)을 정한다. 레이아웃·메뉴 설정은 테마마다 따로 저장된다.
보기와 편집은 **실제 공개 화면**에서 한다 — 관리자가 공개 사이트를 iframe 으로 띄워 저장 전 값을 보내고, 공개 사이트가 그대로 그린다(관리자에서 흉내 내 그리지 않는다).

## 화면 (기준 코드 — 칸 이름·범위·기본값의 원천)
- 관리자 `설정 › 테마 › 레이아웃` = `/settings/theme`(`pages/settings/theme/[[tab]].vue`, `/settings/theme/menu` 는 메뉴 설정 drawer 를 연 채 시작). 옛 주소 `/settings/home-layout`·`/settings/menu-layout` 은 없앴다. 「일반」에서 테마 구획을 뺐다.
  - 왼쪽 위: 테마 카드(세로 목록) + [테마 적용](사이트 테마 저장) · [되돌리기]. 카드를 고르면 아래가 그 테마의 것으로 바뀐다(`?theme=`). 저장하지 않은 변경이 있으면 묻는다.
  - 가운데: 페이지 종류(홈 · 게시판「준비 중」 · 페이지「준비 중」) · 「보기 | 편집」 · [메뉴 설정] → 실제 화면(`ThemeLivePreview` — 공개 사이트 1280×800 을 칸 폭에 맞춰 줄임, 안에서 스크롤).
  - 편집: 실제 홈 격자의 가젯 위 손잡이 — 끌기 = 옮기기 · 오른쪽 아래 = 크기 · × = 빼기(좌표 입력 칸 없음). 도구 줄: 격자 칸 수 12/24 · 테마 기본 배치로 · 가젯 넣기(8종). 저장 줄은 늘 있다. 배치 계산·저장의 주인은 `components/theme/HomeLayoutEditor.vue`.
  - 메뉴 설정: 오른쪽 drawer(바탕을 막지 않음, `components/theme/ThemeMenuSettings.vue`). 실제 화면의 윗줄·메뉴·사이드를 눌러도 열린다. 입력하면 실제 화면이 바로 따르고, 저장하지 않고 닫으면 버린다. 구획 둘, 저장은 구획마다.
- 공개 사이트 미리보기 모드: `/?adminPreview=1` + iframe 안일 때만(`plugins/03.admin-preview.client.ts`, `composables/preview/useAdminPreview.ts`, `utils/util-admin-preview.ts`, 편집 손잡이 `components/home/HomeGadgetGrid.vue`). 규칙은 `.claude/guides/frontend/themes.md` 2-5.

## 필드·검증
- 테마 id: 서버 `SiteThemes.IDS` = `default` · `daum` · `naver` · `roboyam`(보일 순서 — user-web `THEMES` 와 같은 id. user-web 객체의 키 차례는 default·daum·roboyam·naver 지만 화면은 id 로만 찾는다).
- 메뉴 설정(테마마다, 칸마다 `THEME` = 테마 기본 따름 — 테마 기본값 표는 user-web `util-theme.ts` `THEMES[*]`):
  - 배치: `menuPosition` TOP·LEFT·RIGHT·TOP_LEFT·TOP_RIGHT · `topItems` LOGO·MENU · `barGroupIds`(공개 메뉴 main 의 최상위 폴더 ID — 섞인 배치에서만, 비면 첫 그룹) · `menuWidth` CONTENT·FULL · `menuOverflow` SCROLLBAR·ARROWS
  - 모양: `menuGroupStyle` TABS·INLINE · `sideGroupStyle` COLUMN·PICK · `menuSize` NORMAL·COMPACT · `mobileMenu` PICKED·MENU · `menuDescription` ON·OFF · `stickyMenu` ON·OFF · `mobileHeader` BRAND·PLAIN
  - 공개 사이트 메뉴 위치 = 회원 보기 설정(`AUTO` 아니면) > 이 값 > 테마 기본. 나머지 칸은 회원이 고르지 않는다.
- 홈 구성(테마마다): `{ columns: 12|24, items: [{ gadgetId, x, y, w, h }] }` + 가젯 `{ id, type, title, desktop, mobile }` — 행 높이 15px · 간격 16px · `h` 는 최소 높이(공유 레이어 `@roboyam/home-layout`). 최소 크기·기본 크기는 종류별(admin `UtilHomeLayoutEditor.KINDS`). 새 가젯 id 는 `new-<n>`.

## 권한·정책
- 조회 `site.read`(메뉴), 테마 적용·메뉴 설정·홈 구성 저장 `site.manage`. 감추는 것은 편의, 막는 것은 서버.
- 미리보기 모드는 그 iframe 탭의 화면 상태만 바꾼다(서버에 아무것도 쓰지 않는다). 공개 사이트는 `NUXT_PUBLIC_ADMIN_ORIGINS` 주소의 메시지만 받고, 관리자는 `NUXT_PUBLIC_USER_WEB_ORIGIN` 으로만 보낸다. 미리보기 안에서는 회원 보기 설정의 메뉴 위치를 쓰지 않고 링크 이동을 막는다.

## API (서버 구현 2026-10-29 — 마이그레이션 shared V105)
| 주소 | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /api/admin/site/theme?forTheme=` | `site.read` | → `SiteThemeResponse` `{ theme, themes, menuTheme, menuPosition, topItems, barGroupIds, menuWidth, menuOverflow, menuStyle{7칸}, themeMenuDefaults, themeTopItemsDefaults, themeMenuWidthDefaults, themeMenuOverflowDefaults, themeMenuStyleDefaults }` — 메뉴 칸은 `forTheme`(없으면 지금 테마) 것, `menuTheme` 가 그 테마. 목록 밖 `forTheme` 은 `400 fields.forTheme` |
| `PUT /api/admin/site/theme` (CSRF) | `site.manage` | `{ theme }` → 같은 모양(새 테마의 메뉴 칸) · `400 fields.theme`·`fields.site` — 사이트 테마만 바꾼다 |
| `PUT /api/admin/site/themes/{id}/menu` (CSRF) | `site.manage` | `{ menuPosition, topItems, barGroupIds, menuWidth, menuOverflow, menuStyle{7칸} }`(칸 전부) → 같은 모양(그 테마의 메뉴 칸) · 모르는 테마 `404` · `400 fields.menuPosition`…`fields.menuStyle.<칸>`(빠짐·목록 밖 「목록에서 골라 주세요」), `fields.barGroupIds`(숫자 ID 글자 아님 · 20개 넘음) |
| `GET /api/site/theme` (공개) | — | → `{ theme, menuPosition, topItems, barGroupIds, menuWidth, menuOverflow, menuGroupStyle, sideGroupStyle, menuSize, mobileMenu, menuDescription, stickyMenu, mobileHeader }` — **지금 테마**의 운영자 값(칸마다 `THEME` 또는 값). 사본·캐시·전파는 `/api/site/info` 와 같다(20초 + 60초) |
| `GET /api/admin/home-layouts/{theme}` | `site.read` | → `HomeLayoutView` `{ theme, layout{columns, items[{gadgetId,x,y,w,h}]}, gadgets[{id,type,title,desktop,mobile}], updatedAt, updatedBy }` — 저장한 적 없으면 테마 기본 배치 + `updatedAt/updatedBy: null` |
| `GET /api/admin/home-layouts/{theme}/defaults` | `site.read` | → `{ layout, gadgets }` 테마 기본 배치(저장 안 함) |
| `PUT /api/admin/home-layouts/{theme}` (CSRF) | `site.manage` | `{ layout{columns, items}, gadgets[{id, type, title}] }` → `HomeLayoutView` · 모르는 테마 `404` · `400` 칸: `layout.columns` · `gadgets`(30개 한도) · `gadgets[i].id`(비었음·겹침) · `gadgets[i].type`·`.title`(새 가젯만, 1~40자) · `layout.items[i]`(없는 가젯·두 번째 자리·격자 밖·종류별 최소 크기 미만) · `layout.items`(자리 없는 가젯·겹침) |
| `GET /api/home/gadgets` (공개, `no-store`) | — | → `{ theme, layout, gadgets[{ id, type, title, desktop, mobile, empty, locked, style, config, more, data }] }` — 지금 테마의 저장 배치, 없으면 테마 기본 배치 |
| `GET/PUT /api/me/settings` (api, 로그인) | 본인 | 응답에 `menuPosition`(`AUTO`·`TOP`·`LEFT`·`RIGHT`·`TOP_LEFT`·`TOP_RIGHT`, 기본 `AUTO`). `PUT` 에서 빠지면 저장된 값 그대로, 목록 밖은 `400 fields.menuPosition` |

**규칙(서버)**
- 테마별 기본값의 원천은 서버 `SiteThemeMenus`(메뉴 설정 — user-web `THEMES[*]` 와 같은 값)·`HomeLayoutDefaults`(홈 기본 배치 — 확정 때 목업과 같은 가젯·자리·꾸미기·설정). 저장 표: `site_theme_menus`(테마마다 한 행, 없으면 칸마다 `THEME`)·`home_layouts`(테마마다 한 행)·`home_gadgets`(테마에 딸림). 테마 id 칸에 CHECK 없음.
- 홈 저장 = 넣고 뺀 결과 전체: 빠진 저장 가젯은 지우고, 저장 가젯 ID 는 그대로(제목·노출·꾸미기·설정은 서버 값), 기본 배치 ID(`seed-<n>`)는 그 시드 설정 그대로, 그 밖(`new-<n>`)은 종류별 처음 값(테마 기본 꾸미기 · 빈 설정 · PC·모바일 보임) + 보낸 제목으로 새로 만든다. 통과하면 공유 레이어 `compact` 와 같이 위로 당겨 저장한다. 최소 크기는 admin `KINDS` 와 같은 값(24칸이면 폭 ×2).
- 공개 홈 데이터(가젯 설정 `config` 로 고른다, `boardIds` 는 게시판 slug): `POST_LIST` `sort` LATEST(최신글 범위)·POPULAR(인기글 스냅샷, `periodTabs` 면 세 기간)·VIEWS·LIKES·COMMENTS(이번 주 인기글 후보 50개를 그 수로 다시 정렬 — 전체 글 집계 아님), `count` 기본 10 · `HIGHLIGHT` 노출 중인 하이라이트 · `GALLERY` 사진 있는 최신 글 · `BOARD_CARDS` 게시판마다 최신 `perBoard`(기본 4) + 대표 글(정하지 않으면 켜진 일반 게시판 앞 6개) · `GROUPS` 지금 뜨는 소모임 · `QUICK_LINKS`·`NOTICE_TEXT` 는 설정의 `items`·`text`·`format`. **`GROUP_BUY` 는 공개 공구 목록 API 가 없어 늘 빈 목록**(`empty: true`), `GALLERY.kind` 는 늘 null, 노출 기간·보는 사람 설정이 없어 `locked` 는 늘 false.
- 감사: 메뉴 설정 `SITE_THEME_MENU_UPDATE`(바뀐 때만, 바뀐 칸 전후 + `theme`) · 홈 저장 `HOME_LAYOUT_UPDATE`(저장마다, 칸 수·가젯 수 전후 + 넣은·뺀 수). 관리자 알림은 사이트 설정 변경(변형 `siteThemeMenu`·`homeLayout`). 같은 테마 저장은 사이트 설정 행 잠금으로 줄 세운다.
- `AdminScreenRegistry` 에 테마 묶음(`settingsTheme` 자리 — `menu.theme` › `themeLayout` `/settings/theme`, `site.read`)을 admin-web 과 같은 차례로 더했다.

## 미확정 사항
- 운영 배포: 관리자가 띄울 공개 사이트 주소(서버의 공개 주소 설정과 맞출지), 공개 사이트 웹 서버의 `frame-ancestors` 에 관리자 주소 허용.
- 실제 화면은 이 브라우저의 공개 사이트 로그인 상태로 보인다(비로그인 화면으로 볼지).
- 새로 넣은 가젯은 서버가 종류별 처음 값(빈 설정 — 게시판 전체·기본 개수)으로 데이터를 준다. 가젯 내용 설정(게시판·개수·꾸미기) 편집 화면은 범위 밖.
- 공개 공동구매 목록 API 가 생기면 `GROUP_BUY` 가젯 데이터를 채운다. 기본 배치의 가젯 제목·운영 원칙 문구는 한국어 시드다(운영자가 고칠 처음 데이터).
- 서버를 실제로 띄워 부르는 확인(마이그레이션 적용·저장·공개 응답)은 아직 하지 않았다(컴파일만).
- 키보드로 가젯 옮기기는 없다(마우스 끌기만).
- 게시판·페이지 레이아웃의 내용.

## 세션 정리 결정 (2026-10-28)
- 테마 선택의 자리 = 이 화면(`site-settings-contract.md` 의 「일반 > 공개 사이트 테마」 구획은 옮겼다 — 테마 API 계약은 그쪽 「테마」 절이 정본, 메뉴 설정 칸은 이 문서가 정본).
- 메뉴 노드의 첫 줄 구분선(`divider`)·항목 설명(`description`) 칸은 `menu-contract.md` 「구분선·설명」 절이 정본.
