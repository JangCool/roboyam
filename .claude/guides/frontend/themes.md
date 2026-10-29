# Frontend Themes Guide (`themes.md`)

## 1. 역할 및 기본 원칙
* **테마는 사이트 설정 값 하나**다. 운영자가 고르고, 사용자 개인이 테마를 고르지 않는다.
* **데스크톱 메뉴 배치**(사용자 결정 2026-10-28, 규칙은 `UtilMenuPosition` 한 곳·`useMenuPosition`). 레이아웃이 계산해 셸에 `menuPosition`·`topItems`·`menuWidth` 로 넘기고, 모든 셸이 모든 배치를 그린다. 모바일은 무관하다.
    * **메뉴 위치**(`TOP`·`LEFT`·`RIGHT`·`TOP_LEFT`·`TOP_RIGHT`) — **회원 보기 설정(`AUTO` 가 아니면) > 운영자 설정(`THEME` 이 아니면) > 테마 기본값**(`THEMES[*].menuPosition` — default `LEFT`, daum·naver `TOP`, roboyam `TOP_RIGHT`). 섞인 배치(`TOP_*`)는 운영자가 고른 그룹(`barGroupIds`, 비면 첫 최상위 그룹)만 상단, 나머지는 사이드다.
    * **최상위 항목 위치**(그룹 밖 항목 — 홈·인기글 …, `LOGO` 로고 옆 링크 줄 · `MENU` 메뉴 안) — 운영자 설정 > 테마 기본값(`THEMES[*].topItems` — naver `LOGO`, default·daum·roboyam `MENU`). 회원은 고르지 않는다.
    * **상단 메뉴 폭**(`CONTENT` 본문 폭 · `FULL` 화면 좌우 끝까지) — 운영자 설정(`THEME` 이 아니면) > 테마 기본값(`THEMES[*].menuWidth` — 지금은 모두 `CONTENT`). 회원은 고르지 않는다. `FULL` 은 상단 메뉴 상자(바탕·테두리)만 창 폭으로 늘리고 **메뉴 항목의 시작은 본문 열 왼쪽 끝 그대로**(로고·본문과 정렬 유지)다. 상단 메뉴가 없는 배치(`LEFT`·`RIGHT`)에서는 셸이 쓰지 않는다.
      다음식 메뉴 카드(`DaumMenuBar` `full`)는 창 폭 흰 띠(반경 0 — `.shell-card[data-menu-width='FULL']`)가 되고, 바탕이 이미 창 폭인 기본 테마 `AppTopMenu`·네이버식 탭 바는 두 값의 모양이 같다.
    * **메뉴가 넘칠 때**(`SCROLLBAR` 줄 아래 얇은 스크롤 · `ARROWS` 줄 양끝 화살표 단추, 사용자 결정 2026-10-28) — 운영자 설정(`THEME` 이 아니면) > 테마 기본값(`THEMES[*].menuOverflow` — 지금은 모두 `SCROLLBAR`). 회원은 고르지 않는다. 레이아웃이 셸에 `menuOverflow` 로 넘기고 셸은 자기 가로 메뉴 줄에 그대로 전한다.
      **가로 메뉴 줄은 공통 `AppScrollRow` 하나로 그린다**(`AppTopMenu`·`DaumMenuBar` GNB·LNB·`NaverMenuBar`·`AppLogoLinks`, 넘침 계산은 `useScrollOverflow`) — 줄마다 `overflow-x-auto`·넘침 계산을 따로 쓰지 않는다. 줄 내용은 슬롯, 줄 클래스는 `scroller-class`, 활성 칸 가운데로 끌어오기는 `useScrollRowRef()` 의 `scroller` 를 `useCenterActive` 에 넘긴다.
      브라우저 스크롤바는 늘 숨기고(OS 설정마다 높이·겹침이 달라 줄 높이를 지킬 수 없다) 표시를 직접 그린다. **넘쳐도 줄 높이는 그대로다**: 얇은 스크롤은 줄 바닥 예약 자리(`--scroll-reserve` 7px)에 그리는데, 칸이 줄 가운데에 놓여 아래 여백이 있는 줄(다음식 GNB 70·LNB 55, 로고 옆 링크 줄 40)은 그 여백 안에 겹치고,
      활성 밑줄이 줄 바닥에 붙는 막대(기본 테마 `AppTopMenu` 2.5rem·네이버식 탭 바 50)는 `reserve` 로 `SCROLLBAR` 일 때 처음부터 7px 높다(붙는 사이드 메뉴는 `AppSideMenu` `barOverflow` + `--shell-bar-reserve`). 화살표 단추는 글자 위에 겹치는 그라데이션이라 높이가 안 바뀐다. 모바일(md 미만)은 표시 없이 손가락 밀기만이다.
      색·크기는 역할 변수(`--scroll-reserve` `--scroll-bar-h` `--scroll-arrow-w` `--scroll-track` `--scroll-thumb(-active)` `--scroll-arrow-fg(-hover)` `--scroll-fade-bg`)다. 그라데이션 바탕 `--scroll-fade-bg` 는 **줄이 놓인 면이 채운다**(기본 페이지 바탕, `shell-card` = 카드, `.app-nav` = 주 색상 바 — 손잡이·화살표도 `--nav-fg` 기준, 그 밖의 면은 그 면 요소에 `[--scroll-fade-bg:…]`).
    * **메뉴 모양**(사용자 결정 2026-10-28 — 예전에는 테마 선언에 고정, 이제 운영자 설정) — 칸마다 **운영자 설정(`THEME` 이 아니면) > 테마 기본값**(`THEMES[*].menuStyle`, 적지 않은 칸은 `UtilMenuPosition.MENU_STYLE_FALLBACK`). 회원은 고르지 않는다.
      규칙은 `UtilMenuPosition.resolveStyle` 한 곳(값 목록 `MENU_STYLE_VALUES`), 해석 값은 `useMenuStyle()`. **셸·부품은 테마 선언을 직접 읽지 않고 해석 값을 쓴다**(테마 선언 조회 `UtilTheme.menuStyleDefault` 는 「테마 기본값」 용도로만).
      CSS 가 맡는 값은 플러그인(`01.site-theme.client.ts`)이 루트에 해석 값으로 붙이고(`data-mobile-header`·`data-sticky-menu`), CSS 는 **테마 이름이 아니라 그 속성**에 묶는다.

      | 칸 | 값(테마 선언 없을 때 = 첫 값) | 로보얌 기본 | 쓰는 곳 |
      |---|---|---|---|
      | `menuGroupStyle` 상단 메뉴 그룹 표시 | `TABS` 그룹 이름 탭 → 둘째 줄 · `INLINE` 둘째 줄에 「그룹 이름 + 칸」 | `INLINE` | `DaumMenuBar`(다음식 셸) |
      | `sideGroupStyle` 섞인 배치의 사이드 그룹 | `COLUMN` 늘 모두 · `PICK` 첫 줄 탭으로 골라 누른 그룹만 사이드 | `PICK` | `DaumShell`·`DaumMenuBar`·`AppSideMenu`(`pickedOnly`)·`useSideGroupPick` |
      | `menuSize` 상단 메뉴 크기 | `NORMAL` · `COMPACT` | `COMPACT` | `DaumMenuBar` |
      | `mobileMenu` 모바일 상단 메뉴 | `PICKED` 고른 모바일 탭 한 줄 · `MENU` 메뉴 구조 두 줄(`AppMobileMenuRows`) | `MENU` | `AppTopTabs` |
      | `menuDescription` 메뉴 설명 줄 | `OFF` · `ON` 최상위 항목에서 둘째 줄에 설명(`useMenuDescription`) | `ON` | `DaumMenuBar`(`INLINE` 은 주제 줄 대신, `TABS` 는 그룹을 고르기 전까지)·`AppMobileMenuRows` |
      | `stickyMenu` 메뉴 붙이기 | `OFF` · `ON` 로고 줄은 사라지고 메뉴 줄이 창 위에 | `ON` | `DaumShell` 데스크톱(사이드 붙는 높이 `--shell-header-h` 를 본문 상자에 103/126px) · 모바일 `html[data-sticky-menu='on']` |
      | `mobileHeader` 모바일 상단 바 모양 | `BRAND` 주 색상 · `PLAIN` 흰 바 + 흰 메뉴 띠, 굵은 탭·검은 밑줄 | `PLAIN` | `html[data-mobile-header='plain']`(`.app-nav` 는 md 미만만 — 네이버식 탭 바도 `.app-nav`) |

      공개 응답 `ThemeResponse` 의 선택 칸 7개(없으면 `THEME`), 관리자 설정 › 테마 › 메뉴 설정 › 「메뉴 모양」(목업 — 테마를 고르고 그 테마의 레이아웃·메뉴 설정을 편집, 메뉴 설정은 테마마다 따로, 사용자 결정 2026-10-28). 개발 미리보기 `?groups=`·`?sidegroups=`·`?menusize=`·`?mobilemenu=`·`?menudesc=`·`?sticky=`·`?mheader=`(값 또는 `theme`).
    * **메뉴 나누기는 순수 함수 하나**(`UtilMenu.split` — 트리 + 배치 → `logo`·`bar`·`side`). 메뉴 부품(`AppMenuList`·`AppTopMenu`·`DaumMenuBar`·`NaverMenuBar`·`AppLogoLinks`)은 `useMenuItems(scope)` 로 자기 몫만 그린다(`all` 은 햄버거·모바일). 활성 판정은 전체 트리 기준, 로딩·오류 안내는 한 자리(사이드가 있으면 사이드, 없으면 상단)만.
    * 테마는 기본값과 **각 배치에서의 모양**만 정한다(사이드는 공통 `AppSideMenu` 의 `side`·`withBar`, 로고 옆은 공통 `AppLogoLinks`).
* **게시판 유형(`layout`: `LIST`·`CARD`·`GALLERY`)은 테마가 아니다.** 게시판이 정하는 값이고 유형별 컴포넌트는 하나씩만 둔다. 테마는 그 컴포넌트의 외형을 바꾼다.
* **가장 낮은 단계로 해결되면 거기서 끝낸다.** 컴포넌트를 통째로 바꾸는 것은 예외다.
* **속성 이름을 나눈다.** `<html data-theme>` 는 사이트 테마 전용이고, 다크 모드 판정은 `<html data-color-scheme='dark'>` 다(`main.css` `@custom-variant dark` · PrimeVue `darkModeSelector`). 둘을 섞지 않는다.
* 이 앱은 `ssr:false` SPA 라 서버가 테마를 미리 입혀 주지 못한다. 앱이 뜨기 전에 테마 값을 받아 루트에 `data-theme` 를 붙이고 셸을 고른다(깜빡임 방지).

---

## 2. 주요 업무 및 규약

### 2-1. 테마가 바꾸는 3단계
| 단계 | 언제 | 방법 |
|---|---|---|
| 1. 값이 다르다 | 색·모서리·간격·글자 굵기·썸네일 비율 | 테마별 CSS 변수 + PrimeVue 프리셋 |
| 2. 모양이 살짝 다르다 | 구분선 유무·행 밀도·배지 위치 | `data-theme` 기준 CSS(Tailwind `@custom-variant`) |
| 3. 구조가 다르다 | 각 메뉴 위치의 모양·헤더 구성 | 슬롯 교체(§2-3) |

* 컴포넌트는 값을 직접 쓰지 않고 **역할 이름의 변수**를 읽는다: `--post-row-py` `--card-radius` `--thumb-ratio`. 테마가 그 변수를 채운다. 변수 이름에 테마 이름을 넣지 않는다(`--naver-green` ❌).
* 셸 역할 변수는 `main.css` 에 있다: `--page-bg`(레이아웃 바탕) `--card-bg` `--card-radius` `--card-shadow`(카드 면 = `shell-card`) `--hairline` `--hairline-strong` `--tab-fg(-active)` `--subtab-fg(-active)` `--nav-tab-fg` `--content-max`. 기본값이 기본 테마 모양이고, Tailwind 로는 `bg-page` `bg-card` `border-hairline` `text-tab` … 로 쓴다. 다크는 `html[data-theme='<id>'][data-color-scheme='dark']` 로 채운다.
* **화면의 면은 두 가지 카드로만 그린다**(사용자 요청 2026-10-29 — 테마를 바꾸면 모든 공개 화면의 카드가 같이 바뀌게). 둘 다 `main.css` 의 유틸리티이고 값은 테마가 채운다.
    * `content-card` — 화면 본문 한 장(머리·탭·목록·쪽 번호를 한 카드에). 게시판 목록·글 상세·인기글·최신글·검색·게시판 모음·소모임 목록·소모임 본문 열·프로필·글쓰기 계열. 기본 테마는 보이지 않고(바탕 투명·여백 0), 카드 테마(다음식·로보얌·네이버식)는 흰 카드 + 안 여백, 모바일은 화면 끝까지(좌우 바깥 여백은 카드 테마의 모바일에서만 걸려 `mx-auto` 와 함께 써도 된다).
    * `panel-card` — 화면 안의 작은 상자(마이페이지·설정 묶음, 주문·공동구매 구획, 소모임 옆 칸 …). 안 여백은 쓰는 곳이 `p-4` 등으로 준다. 기본 테마 = 흰 면 + 회색 테두리 + 반경 12, 카드 테마 = 테두리 없는 흰 카드(가젯 카드와 같은 반경·그림자). 흰 카드(`content-card`·`gadget-card`·`panel-card`) 안의 패널은 머리카락 선 테두리로 바뀐다.
    * `rounded-xl border border-surface bg-surface-0 dark:bg-surface-900` 같은 고정 조합, 맨 페이지 바탕에 목록을 바로 놓기, `bg-surface-50` 같은 고정 페이지 바탕(→ `bg-page`)은 쓰지 않는다. 알림·경고 상자(주 색상·노랑 바탕)는 의미 색이라 그대로 둔다.
* 이산적 차이는 `@custom-variant naver (&:where([data-theme='naver'], [data-theme='naver'] *))` 처럼 선언해 `naver:py-2` 로 쓴다. 한 컴포넌트에 테마 분기가 3개를 넘으면 변수화나 슬롯 승격을 검토한다.
* 색은 PrimeVue 프리셋과 `surface-*` 토큰으로 간다. 색을 하드코딩하지 않는다.

### 2-2. 테마 적용 항목 (카탈로그)
테마가 건드릴 수 있는 항목의 목록이다. **여기에 없는 항목은 1단계(변수)만 따라가고 컴포넌트는 교체하지 않는다.**

| 영역 | 항목 | 하위 축 | 슬롯 등록 |
|---|---|---|---|
| 셸(데스크톱) | 헤더 · 메뉴(위치·상단 메뉴 폭 포함) · 본문 폭 · 푸터 | - | **등록됨**(`shell`, `components/themes/default/DefaultShell.vue`, `daum/DaumShell.vue`(daum·roboyam), `naver/NaverShell.vue`). 모바일 상단 바(`AppHeader`)·탭·드로어는 테마 공통, 계정 부분은 공통 `AppHeaderActions` |
| 홈 | 템플릿(겉모양) · 가젯 | - (가젯 종류는 슬롯이 아니다) | **등록됨**(`home`, 계약 `components/themes/HomeTemplate.types.ts` — props `layout`·`gadgets`). **템플릿 = 테마**(사용자 결정 2026-10-28): 기본 테마 = `DefaultHome`(기본형) · `daum/DaumHome`(다음식) · `roboyam/RoboyamHome`(로보얌형) · `naver/NaverHome`(네이버형). **템플릿은 겉모양만** 정한다 — 가젯 모양(`provideHomeGadgetLook(look, { heroSplit, galleryColumns })` — `classic`·`card` + 선택 세부 모양, 주지 않으면 기본값)과 `data-template` 을 두고 공통 격자 `components/home/HomeGadgetGrid` 를 그린다. 칸 이름(`MAIN`·`SIDE` …)·열·모바일 순서는 없다. **배치는 운영자 몫**(사용자 결정 2026-10-28 — 관리자 `/settings/theme`, 테마마다 칸 수 12·24 + 가젯별 x·y·w·h, 모양·계산은 공유 레이어 `@roboyam/home-layout` `UtilHomeLayout`): 데스크톱(lg) + 홈 폭 52rem 이상은 CSS grid 그대로(라이브러리 없음 — 행 15px·간격 16px 고정, `h` 는 최소 높이: 내용이 길면 칸이 늘고 같은 열 아래만 밀린다), 태블릿·사이드 메뉴로 좁을 때는 칸 수 반(반 폭 이하 둘은 나란히), md 미만·36rem 미만은 (y, x) 순서 한 열(`UtilHomeGrid`). 칸 안의 가젯은 테마 공통 부품(`components/home/HomeGadget*`, 종류 → 컴포넌트 `UtilHomeGadgetRegistry`) — 모양은 역할 변수 `--gadget-card-*`·`--gadget-gap`(`main.css` — 한 열일 때 간격)·공통 부품의 고리 클래스(`gadget-icon`·`gadget-more` — 기본 규칙 없음)로 바꾼다 |
| 게시판 | 목록 | 유형(`LIST`·`CARD`·`GALLERY`) | **등록됨**(`postList:<유형>`, 계약 `components/board/BoardPostList.types.ts`). 기본 테마 = `BoardPostList`·`BoardPostCards`·`BoardPostGallery`, `daum`·`roboyam`(같은 `DaumPostList`)·`naver` 는 `postList:LIST` 만 선언 |
| 게시판 | 글 상세 · 댓글 · 글쓰기 | - | 구조가 달라질 때 승격 |
| 회원 | 프로필 · 내 정보 · 쪽지 | - | 구조가 달라질 때 승격 |
| 공통 | 알림 · 시트/확인창/토스트 · 등급·스태프 배지 | - | 구조가 달라질 때 승격 |
| 인증 | 로그인 · 가입 | - | 구조가 달라질 때 승격 |

* **홈 한 열(모바일) 모양**(사용자 요청 2026-10-28): 격자가 한 열(`stack` — md 미만 또는 홈 폭 36rem 미만)일 때만 바뀌고 데스크톱·태블릿 격자(`full`·`half`)는 그대로다. 템플릿이 `provideHomeGadgetLook(look, { mobile: { … } })` 로 고르고(주지 않은 값은 지금 모양 그대로), 격자가 한 열인지 `provideHomeGadgetStacked` 로 알려 가젯은 **둘 다일 때만** 한 열 모양을 그린다 — 가젯 부품을 테마별로 복사하지 않는다.
    * 값(`HomeGadgetMobileLook`): `order`(`reading` (y, x) · `columns` 본문 열 먼저 — `UtilHomeGrid.byColumns`: 어떤 가젯도 가로지르지 않는 칸 경계로 열을 나눠 왼쪽 열부터, 경계가 없으면 `reading`) · `highlightScroll`·`heroBleed` · `galleryScroll` · `groupBuyScroll` · `groupsScroll` · `boardCardsScroll`.
      운영자가 모바일 순서를 바꾸고 싶으면 모바일 전용 가젯(`mobile` 노출)을 본문 열 원하는 자리에 둔다(로보얌 목업의 모바일 공동구매).
    * 가로로 미는 줄은 공통 클래스 `gadget-scroll-row`(`main.css` — 스크롤바 숨김 · 칸 시작 스냅 · `--gadget-bleed` 만큼 카드 안쪽 여백/본문 여백 끝까지 · 칸 폭 `--row-item`)이고 줄에 이름(`aria-label` = `home.scrollRowLabel`)을 단다. 칸은 링크라 Tab 으로 넘어간다. 줄마다 `overflow-x-auto` 를 따로 쓰지 않는다.
    * 모든 테마 공통(한 열): 글 목록 두 열 → 한 열 · 기간 탭 큰 알약(36px) · 머리 「더보기 ›」 44px 터치 영역(`.gadget-head .gadget-more`). 카드 테마(다음식·로보얌·네이버식)의 모바일 카드는 화면 폭(반경 0 · 그림자 없음).
    * 테마별: 로보얌 = `columns` + 전부(공구·소모임·영상·짤·하이라이트 가로 줄, 큰 글 사진 끝까지, 안 여백 좌우 16) · 다음식·기본 = 갤러리 가로 줄 · 네이버식 = 게시판 카드 묶음·갤러리 가로 줄.
* 슬롯은 **필요할 때 하나씩** 등록한다. 표 전체를 처음부터 슬롯으로 만들지 않는다. 카탈로그에 항목을 더하려면 이 표와 계약(props)을 함께 정한다.
* 항목은 하위 축을 가질 수 있다(게시판 목록의 유형, 가젯의 종류). 슬롯 키는 `항목:하위축`(예: `postList:GALLERY`)이다.

### 2-3. 슬롯 교체와 폴백
* 테마는 **바꾸는 슬롯만** 선언한다. 선언하지 않은 슬롯은 기본 테마 것을 쓴다.
* 찾는 순서: ① 현재 테마의 `항목:하위축` → ② 현재 테마의 `항목` → ③ 기본 테마의 `항목:하위축` → ④ 기본 테마의 `항목`. 마지막이 항상 있어 화면이 깨지지 않는다. 서버가 모르는 유형·종류를 먼저 보내도 기본으로 그린다.
* **같은 슬롯의 모든 컴포넌트는 같은 props 계약**을 지킨다. 계약이 같아야 `<component :is>` 로 갈아끼워도 페이지가 그대로다. 유형에만 필요한 필드는 선택 prop 으로 둔다.
* 로직(조회·페이징·권한)은 페이지와 컴포저블에 하나만 둔다. 슬롯 컴포넌트는 그리기만 한다 → `components.md`.
* 테마 컴포넌트는 `defineAsyncComponent` 로 불러 쓰지 않는 테마의 코드가 번들에 들어가지 않게 한다.

### 2-4. 파일 배치와 이름
```text
components/shell/              # 공통 셸 부품(AppMenuList, AppMenuLink, AppToaster …)
components/themes/<테마>/      # 그 테마만의 슬롯 컴포넌트: NaverShell, NaverPostList …
```
* `pathPrefix:false` 라 파일 이름은 프로젝트 전체에서 유일해야 한다. 테마 컴포넌트는 **테마 접두어**(`Naver…` `Daum…`)를 붙인다 → `components.md`.
* `layouts/default.vue` 는 테마와 무관한 일(메뉴·알림 로딩, 배너, 시트·확인창)만 하고 셸 슬롯을 끼운다. 테마별 분기를 `default.vue` 에 `v-if` 로 쌓지 않는다.
* 메뉴의 알맹이(`AppMenuList` `AppMenuLink`)는 공통으로 재사용한다. 테마 셸은 그것을 어디에 놓느냐만 정한다.
* 모바일은 하단 탭 중심이라 구조(상단 바 `AppHeader`·상단 탭 `AppTopTabs`·하단 탭·드로어)는 테마와 무관하게 공통으로 둔다(데스크톱만 테마별). 겉모양은 역할 변수(`.app-nav` 의 `--nav-bg`·`--nav-fg`)와 고리 클래스(`app-top-tabs`)로 바꾼다 — 상단 바 모양·메뉴 줄·붙이기는 운영자 설정 「메뉴 모양」(`mobileHeader`·`mobileMenu`·`stickyMenu`, 로보얌 기본 = 흰 상단 바 + 흰 메뉴 두 줄 + 붙이기)이다.

### 2-5. 새 테마를 추가할 때
* 지금 테마: `default` · `daum`(다음식) · `naver`(네이버식) · `roboyam`(로보얌 테마) — `utils/util-theme.ts` `THEMES`. 서버 `SiteThemes.IDS` 에도 있어야 운영자가 고를 수 있다(지금 서버에는 `default` 하나 — 관리자 화면은 `roboyam` 을 목업으로 덧붙인다). 개발 모드에서만 주소 `?theme=<id>`·`?menu=top|left|right|top-left|top-right|theme`·`?top=logo|menu|theme`·`?bar=<그룹ID,…>|first`·`?width=content|full|theme`·`?overflow=scrollbar|arrows|theme` · 메뉴 모양 `?groups=tabs|inline|theme`·`?sidegroups=column|pick|theme`·`?menusize=normal|compact|theme`·`?mobilemenu=picked|menu|theme`·`?menudesc=on|off|theme`·`?sticky=on|off|theme`·`?mheader=brand|plain|theme`(운영자 값 자리) 로 미리 본다(`useTheme`, 탭에 기억).
* **로보얌 테마(`roboyam`, 사용자 결정 2026-10-28)** = 다음식 겉모양 + 로보얌 기본 배치. 기본값 `menuPosition: TOP_RIGHT`(흰 메뉴 카드 GNB·LNB = 첫 최상위 그룹 · 나머지 그룹은 GNB 탭(`sideGroupStyle: PICK`) — 누른 그룹만 오른쪽 사이드) · `topItems: MENU`(홈·인기글 … 이 GNB 맨 앞) · `menuWidth: FULL`(메뉴 카드가 창 좌우 끝까지) ·
  `menuStyle`(위 「메뉴 모양」 표의 로보얌 기본 — `menuSize: COMPACT` · `menuGroupStyle: INLINE`(다음식은 `TABS`) · 메뉴 설명 줄 · 붙이기 · 모바일 두 줄·흰 바). 모두 운영자가 칸마다 바꿀 수 있다.
  **다음식 부품을 재사용한다(복사 금지)**: `THEMES.roboyam.slots` 가 daum 과 같은 슬롯 로더(`daumSlots` — `DaumShell`·`DaumPostList`)를 가리키고 홈만 `{ ...daumSlots, home: RoboyamHome }` 로 덮으며, `main.css` 의 다음식 값은 `html:is([data-theme='daum'], [data-theme='roboyam'])` 로 함께 쓴다. 로보얌만 바꿀 값은 `main.css` 끝의 `html[data-theme='roboyam']` 자리에 역할 변수로 덮는다(홈 가젯: 카드 안 여백 · 머리 아이콘 주 색상 칩 · 작은 「더보기」). 메뉴 모양(모바일 상단 바·붙이기)은 테마 이름이 아니라 설정 속성(`data-mobile-header`·`data-sticky-menu`)에 묶는다.
  **로보얌 홈**(사용자 결정 2026-10-28 — 「로봇과 AI, 쉽게 풀어 함께 이야기해요」, 뉴스 포털처럼 빽빽하지 않게): 목업 기본 배치(24칸) = 본문 넓은 열 17칸(하이라이트 큰 글 1 + 작은 글 3 · 지금 뜨는 글 순위 6 두 열 · 영상·짤 한 줄 4칸 · 방금 올라온 글 두 열 8줄) + 오른쪽 열 7칸(공동구매 · 인기 소모임). 머리는 막대 대신 아이콘 + 제목(색은 주 색상 하나), 게시판 이름 회색, 구분선·NEW 없음. 목업 배치는 `home-gadget-mock.ts` `roboyam()`(관리자 편집기 목업 `home-layout-mock.ts` 와 같은 자리).
* **관리자 미리보기 모드**(사용자 결정 2026-10-28 — 관리자 미리보기는 실제 공개 화면과 똑같아야 한다, 관리자에서 따로 흉내 내 그리지 않는다): 관리자 테마 화면(`/settings/theme`)의 「실제 화면」이 이 앱을 iframe 으로 `/?adminPreview=1` 로 띄운다.
  `plugins/03.admin-preview.client.ts` 가 `runtimeConfig.public.adminOrigins`(`NUXT_PUBLIC_ADMIN_ORIGINS`) 주소의 메시지만 받아 `useTheme` 상태(테마·메뉴 칸)를 덮고 홈 구성은 `useAdminPreview().home` 으로 홈에 덮는다(`UtilAdminPreview.applyHome`). 미리보기 안에서는 회원 보기 설정의 메뉴 위치를 쓰지 않고, 링크로 화면을 옮기지 않으며, 윗줄·메뉴·사이드 메뉴를 누르면 관리자에게 메뉴 설정을 열라고 알린다.
  **편집도 실제 화면에서 한다**: 관리자가 「편집」이면 `useAdminPreview().editing` 이 켜져 `HomeGadgetGrid` 가 실제 가젯 위에 손잡이 층(끌기 = 옮기기 · 오른쪽 아래 = 크기 · × = 빼기)을 얹고, 놓은 칸·크기·빼기만 관리자에게 알린다(`roboyam:admin-preview-layout`).
  배치 계산(최소 크기·밀기·위로 당기기)과 저장은 관리자 `HomeLayoutEditor`(배치의 주인)가 하고 바뀐 배치를 다시 보낸다. 관리자 쪽에서 가젯을 흉내 내 그리는 미리보기는 두지 않는다.
  셸·홈 부품은 이 모드를 몰라도 된다(상태만 바뀐다) — 새 테마 설정 칸을 만들면 관리자 메시지 읽기(`UtilAdminPreview.read`·플러그인)에도 한 줄 더한다.
* 겉모양이 같은 테마는 이렇게 **슬롯 로더와 CSS 선택자를 묶어** 만들고, 기본값(`menuPosition`·`topItems`·`menuWidth`·`menuOverflow`·`menuStyle`)만 다르게 선언한다.
1. 테마 id 를 정하고 토큰(변수·프리셋)을 채운다.
2. 달라지는 모양은 2단계 CSS 로 처리한다.
3. 구조가 달라지는 항목만 슬롯 컴포넌트를 만들고 테마 선언에 한 줄 추가한다. 나머지는 기본 테마가 채운다.

---

## 3. 작성 예시
```ts
// 테마 선언 — 바꾸는 슬롯만 적는다 (형태만 보인 예)
export const themes = {
  default: { slots: {} },
  naver: { slots: { shell: () => import('~/components/themes/naver/NaverShell.vue') } },
}
// resolve('postList', 'GALLERY') → 테마 → 기본 테마 순으로 찾는다
```
```css
/* 컴포넌트가 읽는 변수는 역할 이름, 테마가 값을 채운다 */
[data-theme='naver'] { --card-radius: 4px; --thumb-ratio: 4 / 3; }
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ 사용자 개인 테마 추가(보기 설정은 메뉴 위치처럼 결정된 칸만), 메뉴 위치 판정을 셸·페이지마다 따로 계산
2. ❌ 변수·클래스 이름에 테마 이름 사용(`--naver-green`)
3. ❌ 카탈로그에 없는 항목의 컴포넌트를 테마별로 복제
4. ❌ `default.vue` 에 테마별 `v-if` 분기 누적
5. ❌ 슬롯 컴포넌트에서 서버 호출·로직 처리, 같은 슬롯의 props 계약을 테마마다 다르게 정의, 홈 템플릿에 칸 이름·열 배치를 다시 두기(배치는 운영자 격자 — `HomeGadgetGrid`)
6. ❌ 색·모서리·간격 하드코딩, 카드를 `rounded-xl border bg-surface-0` 조합으로 직접 그리기(→ `content-card`·`panel-card`)
7. ❌ 테마 컴포넌트를 동기 임포트(번들 부풀림)
8. ❌ 셸·부품이 테마 선언(`UtilTheme.menuStyleDefault` 등)을 직접 읽어 메뉴 모양을 정하기, 메뉴 모양 CSS 를 `data-theme` 테마 이름에 묶기 — 해석 값(`useMenuStyle`·루트 설정 속성)을 쓴다
9. ❌ 데스크톱 가로 메뉴 줄을 `AppScrollRow` 없이 직접 `overflow-x-auto` 로 만들기·넘침 계산 복사, 브라우저 기본 가로 스크롤바 드러내기, 넘칠 때 줄 높이가 바뀌는 표시
