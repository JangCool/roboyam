# 테마 · 레이아웃 · 메뉴 설정 계약 (**화면 확정 2026-10-28 · 서버 구현·화면 연결 2026-10-29**)

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
- 미리보기 모드는 그 iframe 탭의 화면 상태만 바꾼다(서버에 아무것도 쓰지 않는다). 공개 사이트는 관리자 주소(개발 `NUXT_PUBLIC_ADMIN_ORIGINS`, 운영 `ADMIN_URL`)의 메시지만 받고, 관리자는 공개 사이트 주소(개발 `NUXT_PUBLIC_USER_WEB_ORIGIN`, 운영 사이트 공개 주소)로만 보낸다(아래 「운영 배포」). 미리보기 안에서는 회원 보기 설정의 메뉴 위치를 쓰지 않고 링크 이동을 막는다.

## API (서버 구현 2026-10-29 — 마이그레이션 shared V105)
| 주소 | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /api/admin/site/theme?forTheme=` | `site.read` | → `SiteThemeResponse` `{ theme, themes, menuTheme, menuPosition, topItems, barGroupIds, menuWidth, menuOverflow, menuStyle{7칸}, themeMenuDefaults, themeTopItemsDefaults, themeMenuWidthDefaults, themeMenuOverflowDefaults, themeMenuStyleDefaults, publicUrl }`(`publicUrl` = 사이트 공개 주소, 없으면 null — 「운영 배포」) — 메뉴 칸은 `forTheme`(없으면 지금 테마) 것, `menuTheme` 가 그 테마. 목록 밖 `forTheme` 은 `400 fields.forTheme` |
| `PUT /api/admin/site/theme` (CSRF) | `site.manage` | `{ theme }` → 같은 모양(새 테마의 메뉴 칸) · `400 fields.theme`·`fields.site` — 사이트 테마만 바꾼다 |
| `PUT /api/admin/site/themes/{id}/menu` (CSRF) | `site.manage` | `{ menuPosition, topItems, barGroupIds, menuWidth, menuOverflow, menuStyle{7칸} }`(칸 전부) → 같은 모양(그 테마의 메뉴 칸) · 모르는 테마 `404` · `400 fields.menuPosition`…`fields.menuStyle.<칸>`(빠짐·목록 밖 「목록에서 골라 주세요」), `fields.barGroupIds`(숫자 ID 글자 아님 · 20개 넘음) |
| `GET /api/site/theme` (공개) | — | → `{ theme, menuPosition, topItems, barGroupIds, menuWidth, menuOverflow, menuGroupStyle, sideGroupStyle, menuSize, mobileMenu, menuDescription, stickyMenu, mobileHeader }` — **지금 테마**의 운영자 값(칸마다 `THEME` 또는 값). 사본·캐시·전파는 `/api/site/info` 와 같다(서버 사본 20초, 브라우저는 `no-cache` + ETag 로 매번 확인) |
| `GET /api/admin/home-layouts/{theme}` | `site.read` | → `HomeLayoutView` `{ theme, layout{columns, items[{gadgetId,x,y,w,h}]}, gadgets[{id,type,title,desktop,mobile,config}], updatedAt, updatedBy }`(`config` 는 공구 가젯만 `{count, sort}` — 저장 값, 없으면 5 · DEADLINE, 다른 종류 null) — 저장한 적 없으면 테마 기본 배치 + `updatedAt/updatedBy: null` |
| `GET /api/admin/home-layouts/{theme}/defaults` | `site.read` | → `{ layout, gadgets }` 테마 기본 배치(저장 안 함) |
| `PUT /api/admin/home-layouts/{theme}` (CSRF) | `site.manage` | `{ layout{columns, items}, gadgets[{id, type, title, config?}] }` → `HomeLayoutView`(공구 가젯 `config{count, sort}` 는 보내면 그 값으로 바꾸고 설정의 다른 칸은 그대로, 없으면 그대로, 다른 종류는 무시) · 모르는 테마 `404` · `400` 칸: `layout.columns` · `gadgets`(30개 한도) · `gadgets[i].id`(비었음·겹침) · `gadgets[i].type`·`.title`(새 가젯만, 1~40자) · `gadgets[i].config.count`(1~10)·`.config.sort`(3종, 공구 가젯만) · `layout.items[i]`(없는 가젯·두 번째 자리·격자 밖·종류별 최소 크기 미만) · `layout.items`(자리 없는 가젯·겹침) |
| `GET /api/home/gadgets` (공개, `no-store`) · `?theme=`(선택 — 관리자 테마 화면 미리보기만 싣는다, 모르는 값은 무시) | — | → `{ theme, layout, gadgets[{ id, type, title, desktop, mobile, empty, locked, style, config, more, data }] }` — 지금 테마(`theme` 을 실으면 그 테마)의 저장 배치, 없으면 테마 기본 배치 |
| `GET/PUT /api/me/settings` (api, 로그인) | 본인 | 응답에 `menuPosition`(`AUTO`·`TOP`·`LEFT`·`RIGHT`·`TOP_LEFT`·`TOP_RIGHT`, 기본 `AUTO`). `PUT` 에서 빠지면 저장된 값 그대로, 목록 밖은 `400 fields.menuPosition` |

**규칙(서버)**
- 테마별 기본값의 원천은 서버 `SiteThemeMenus`(메뉴 설정 — user-web `THEMES[*]` 와 같은 값)·`HomeLayoutDefaults`(홈 기본 배치 — 확정 때 목업과 같은 가젯·자리·꾸미기·설정). 저장 표: `site_theme_menus`(테마마다 한 행, 없으면 칸마다 `THEME`)·`home_layouts`(테마마다 한 행)·`home_gadgets`(테마에 딸림). 테마 id 칸에 CHECK 없음.
  - **세션 정리 결정(2026-10-29, 사용자 요청 「로보얌 가젯 기준으로 다른 테마 구성 맞추기」)**: 네 테마의 홈 기본 배치는 로보얌 구성이 정본이다 — 본문 넓은 열(하이라이트 · 인기글 두 열 · [모바일 전용 공구] · 영상·짤 · 방금 올라온 글 두 열) + 오른쪽 열(공구 데스크톱 전용 · 인기 소모임), 24칸 17+7 · 다음식 12칸 8+4. 테마마다 다른 것은 꾸미기(머리 모양·제목 색·부제)·하이라이트 모양·영상·짤 줄 수이고, 네이버식만 언론사 홈 모양으로 본문 「게시판별 새 글」(게시판 카드 격자, 인기글 · [모바일 공구] 다음)과 오른쪽 「많이 본 글」(공구 다음, PC 전용)을 더한다(사용자 요청 「네이버식 본문 내용이 부족해」). 네 템플릿 모두 한 열(모바일) 순서는 본문 열 먼저(`mobile.order: 'columns'`). 이미 저장한 테마(로컬: `naver`)는 저장 배치가 이기므로 관리자 「테마 기본 배치로」 + 저장으로 다시 받아야 한다.
- 홈 저장 = 넣고 뺀 결과 전체: 빠진 저장 가젯은 지우고, 저장 가젯 ID 는 그대로(제목·노출·꾸미기·설정은 서버 값), 기본 배치 ID(`seed-<n>`)는 그 시드 설정 그대로, 그 밖(`new-<n>`)은 종류별 처음 값(테마 기본 꾸미기 · 빈 설정 · PC·모바일 보임) + 보낸 제목으로 새로 만든다. 통과하면 공유 레이어 `compact` 와 같이 위로 당겨 저장한다. 최소 크기는 admin `KINDS` 와 같은 값(24칸이면 폭 ×2).
- 공개 홈 데이터(가젯 설정 `config` 로 고른다, `boardIds` 는 게시판 slug): `POST_LIST` `sort` LATEST(최신글 범위)·POPULAR(인기글 스냅샷, `periodTabs` 면 세 기간)·VIEWS·LIKES·COMMENTS(이번 주 인기글 후보 50개를 그 수로 다시 정렬 — 전체 글 집계 아님), `count` 기본 10 · `HIGHLIGHT` 노출 중인 하이라이트 · `GALLERY` 사진 있는 최신 글 · `BOARD_CARDS` 게시판마다 최신 `perBoard`(기본 4) + 대표 글(정하지 않으면 켜진 일반 게시판 앞 6개) · `GROUPS` 지금 뜨는 소모임 · `QUICK_LINKS`·`NOTICE_TEXT` 는 설정의 `items`·`text`·`format`. `GROUP_BUY` 모집 중인 공개 공구(회원 공구 목록과 같은 거름·줄 모양, `sort` DEADLINE 마감 이른 순(기본)·NEWEST 새로 시작한 순·POPULAR 참여 많은 순(같으면 마감 이른 순), `count` 기본 5 · 1~10) + `salesOpen`(판매 스위치 — 꺼져도 공구는 보이고 화면이 안내 한 줄), `GALLERY.kind` 는 늘 null, 노출 기간·보는 사람 설정이 없어 `locked` 는 늘 false.
- 감사: 메뉴 설정 `SITE_THEME_MENU_UPDATE`(바뀐 때만, 바뀐 칸 전후 + `theme`) · 홈 저장 `HOME_LAYOUT_UPDATE`(저장마다, 칸 수·가젯 수 전후 + 넣은·뺀 수). 관리자 알림은 사이트 설정 변경(변형 `siteThemeMenu`·`homeLayout`). 같은 테마 저장은 사이트 설정 행 잠금으로 줄 세운다.
- `AdminScreenRegistry` 에 테마 묶음(`settingsTheme` 자리 — `menu.theme` › `themeLayout` `/settings/theme`, `site.read`)을 admin-web 과 같은 차례로 더했다.

## 운영 배포 (결정 2026-10-29)
화면은 `nuxt generate` 정적 파일이라 `NUXT_PUBLIC_*` 가 빌드 때 박히지만, 이미지는 로컬·dev·prod·설치형이 한 벌이므로 **운영 주소를 화면 빌드에 넣지 않고 서버 설정을 실행 때 쓴다**. 빌드한 화면(`$production`)은 두 값을 비워 둔다.
- 공개 사이트 HTML 은 nginx 가 아니라 api 가 내린다(`SiteHeadFilter` — Caddy·ALB 는 전달만). 프레임 헤더는 공통 `SecurityConfig` 의 `FrameHeaderWriter` 가 붙인다: 늘 `X-Frame-Options: DENY`, 앱의 `FrameAncestorsPolicy` 가 허락한 응답만 그 대신 `Content-Security-Policy: frame-ancestors …`.
- 허락하는 응답(api `AdminPreviewFrame`): 화면 주소의 GET·HEAD 이면서 `adminPreview=1` 인 것만 `frame-ancestors 'self' <관리자 origin>`. 관리자 origin = 환경 값 `ADMIN_URL`(api 의 `roboyam.admin-preview.origin`)의 스킴·호스트·포트 — 비었거나 http(s) 가 아니면 허락하지 않는다. 그 밖의 화면·API·정적 파일·관리자 앱은 그대로 DENY(관리자 앱은 정책 빈이 없다).
- 공개 화면이 받을 관리자 주소: 같은 응답 머리의 `<meta name="roboyam-admin-origin">`(서버가 넣는다) + `runtimeConfig.public.adminOrigins`(개발 서버 기본 `http://localhost:3200,3201`).
- 관리자가 띄울 공개 사이트 주소: 사이트 설정 공개 주소(설치 마법사 값 — `GET /api/admin/site/theme` 응답 `publicUrl`)의 origin. 개발 서버는 `runtimeConfig.public.userWebOrigin`(기본 `http://localhost:3100`)이 먼저다(`useUserWebOrigin`). 둘 다 없으면 「실제 화면」 자리에 안내 문구(`site.livePreviewNoOrigin`).
- 하위 도메인 여부와 무관하다(주소를 origin 으로 정확히 맞춘다). 다만 공개·관리자 도메인의 등록 도메인이 다르면 iframe 안 공개 사이트 쿠키가 서드파티라 브라우저가 막을 수 있다 — 그때 실제 화면은 비로그인 화면으로 보인다(아래 미확정 「로그인 상태」와 같은 결과).
- 배포 값: dev 는 `init-env.sh` 가 `ADMIN_URL=https://<관리자 도메인>` 을 쓴다. prod 는 `.env` 의 `ADMIN_URL`(앱 서버·관리자 앱 같은 값). 설치형(`installer/`)은 관리자 앱이 없어 해당 없음. 절차·확인 명령은 `docker/dev/README.md` 9.

## 미확정 사항
- 실제 화면은 이 브라우저의 공개 사이트 로그인 상태로 보인다(비로그인 화면으로 볼지).
- 새로 넣은 가젯은 서버가 종류별 처음 값(빈 설정 — 게시판 전체·기본 개수)으로 데이터를 준다. 가젯 내용 설정(게시판·개수·꾸미기) 편집 화면은 범위 밖.
- ~~공개 공동구매 목록 API 가 생기면 `GROUP_BUY` 가젯 데이터를 채운다~~ → **채움(2026-10-29)** — 위 「공개 홈 데이터」 줄, 상세는 commerce-groupbuy-contract 「홈 `GROUP_BUY` 가젯 연결」. ~~가정: 정렬은 마감 이른 순 고정, 관리자 편집기에 `count` 칸 없음~~ → **처리(2026-10-29 사용자 결정)**: 정렬 3가지(DEADLINE·NEWEST·POPULAR) + `count` 1~10(기본 5)을 관리자 실제 화면 편집의 공구 가젯 ⚙ 설정 창에서 고친다(공구 가젯만 — 다른 종류의 내용 설정은 범위 밖). 저장 전 미리보기는 받은 줄 안에서만 차례·개수를 바꿔 보이고, 저장하면 서버가 그 설정으로 다시 고른다. 기본 배치의 가젯 제목·운영 원칙 문구는 한국어 시드다(운영자가 고칠 처음 데이터).
- 키보드로 가젯 옮기기는 없다(마우스 끌기만).
- 게시판·페이지 레이아웃의 내용.

## 서버 실행 확인 · 화면 연결 결과 (2026-10-29)
- 실행 확인(develop 4bd01a14 기준 bootJar, 로컬 DB): api 가 shared V101~V105 를 차례로 적용, admin 은 검증만. 다른 세션 서버와 겹치지 않게 api 8911 · admin 8921(배치 끔)로 띄웠다.
  - 공개: `GET /api/site/theme`(평평한 13칸, `public, max-age=60` + ETag) · `GET /api/home/gadgets`(기본 테마 기본 배치 8개, `seed-<n>`, `no-store`) · `GET /api/menu`(그룹·항목 `divider`, 항목 `description`).
  - 회원(시드 시험 계정): `/api/me/settings` `menuPosition` 기본 `AUTO` · 저장 · 세 칸만 보낸 PUT 에서 값 유지 · 목록 밖 400 `fields.menuPosition`.
  - 관리자(운영자 세션): 테마 조회(`forTheme` 400 포함) · 같은 테마 PUT · 목록 밖 400 · `daum` 메뉴 설정 저장(상단 띠 그룹 중복 제거)·재조회·칸 오류 400·모르는 테마 404 · 홈 구성(`naver`) 조회·기본값·저장(시드 → 새 ID, 새 가젯 위로 당김, 운영자 이름)·같은 ID 재저장·칸 수·종류·제목·겹침·범위·자리 없음 400·모르는 테마 404 · 메뉴 트리 구분선·설명 저장(설명 공백 정리, 폴더 설명 버림)·80자 초과·모르는 구분선 400. 바꾼 값은 모두 되돌렸다(`naver` 홈은 기본 배치로 다시 저장해 `home_layouts` 행이 남는다).
  - 확인 중 고친 것: 메뉴 설정 PUT 본문이 비면 500(NPE) → 칸마다 400 · 인기글 기간 탭 가젯은 세 기간이 모두 빌 때만 `empty`.
- 화면 연결: admin `useSiteSettingsApi`(theme·saveTheme·saveThemeMenu)·`useHomeLayoutApi`·`useMenuApi`(트리), user-web `useHomeGadgetApi`·`useViewSettingsApi` 가 실제 API 를 부른다. 목업 `site-theme-mock.ts`·`home-layout-mock.ts`·`menu-extra-mock.ts`·`home-gadget-mock.ts` 와 user-web 홈의 `?mock=home-*` 스위치는 없앴다. `SiteThemeMenuFields` 는 `useSiteSettingsApi.types.ts` 로 옮겼고, admin 메뉴 노드 타입의 `divider`·`description` 은 필수 칸이 됐다. 개발 미리보기 `?divider=`·`?theme=` 등은 남겼다(서버 값이 없는 칸에만 입힌다).
- 화면을 브라우저로 눌러 보는 확인은 하지 않았다(사용자 확인).

## 세션 정리 결정 (2026-10-28)
- 테마 선택의 자리 = 이 화면(`site-settings-contract.md` 의 「일반 > 공개 사이트 테마」 구획은 옮겼다 — 테마 API 계약은 그쪽 「테마」 절이 정본, 메뉴 설정 칸은 이 문서가 정본).
- 메뉴 노드의 첫 줄 구분선(`divider`)·항목 설명(`description`) 칸은 `menu-contract.md` 「구분선·설명」 절이 정본.
