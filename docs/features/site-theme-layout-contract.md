# 테마 · 레이아웃 · 메뉴 설정 계약 (**화면 확정 2026-10-28** — 서버는 다음 단계)

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
- 테마 id: 서버 `SiteThemes.IDS` = user-web `THEMES` 와 같은 id·순서 — `default` · `daum` · `naver` · `roboyam`(지금 서버는 `default` 하나, 나머지는 관리자 목업이 덧붙인다).
- 메뉴 설정(테마마다, 칸마다 `THEME` = 테마 기본 따름 — 테마 기본값 표는 user-web `util-theme.ts` `THEMES[*]`):
  - 배치: `menuPosition` TOP·LEFT·RIGHT·TOP_LEFT·TOP_RIGHT · `topItems` LOGO·MENU · `barGroupIds`(공개 메뉴 main 의 최상위 폴더 ID — 섞인 배치에서만, 비면 첫 그룹) · `menuWidth` CONTENT·FULL · `menuOverflow` SCROLLBAR·ARROWS
  - 모양: `menuGroupStyle` TABS·INLINE · `sideGroupStyle` COLUMN·PICK · `menuSize` NORMAL·COMPACT · `mobileMenu` PICKED·MENU · `menuDescription` ON·OFF · `stickyMenu` ON·OFF · `mobileHeader` BRAND·PLAIN
  - 공개 사이트 메뉴 위치 = 회원 보기 설정(`AUTO` 아니면) > 이 값 > 테마 기본. 나머지 칸은 회원이 고르지 않는다.
- 홈 구성(테마마다): `{ columns: 12|24, items: [{ gadgetId, x, y, w, h }] }` + 가젯 `{ id, type, title, desktop, mobile }` — 행 높이 15px · 간격 16px · `h` 는 최소 높이(공유 레이어 `@roboyam/home-layout`). 최소 크기·기본 크기는 종류별(admin `UtilHomeLayoutEditor.KINDS`). 새 가젯 id 는 `new-<n>`.

## 권한·정책
- 조회 `site.read`(메뉴), 테마 적용·메뉴 설정·홈 구성 저장 `site.manage`. 감추는 것은 편의, 막는 것은 서버.
- 미리보기 모드는 그 iframe 탭의 화면 상태만 바꾼다(서버에 아무것도 쓰지 않는다). 공개 사이트는 `NUXT_PUBLIC_ADMIN_ORIGINS` 주소의 메시지만 받고, 관리자는 `NUXT_PUBLIC_USER_WEB_ORIGIN` 으로만 보낸다. 미리보기 안에서는 회원 보기 설정의 메뉴 위치를 쓰지 않고 링크 이동을 막는다.

## API 후보 (서버 단계에서 확정)
| 주소 | 권한 | 모양 |
|---|---|---|
| `GET /api/admin/site/theme?forTheme=` | `site.read` | `{ theme, themes, …메뉴 설정(forTheme 없으면 지금 테마) , theme*Defaults }` |
| `PUT /api/admin/site/theme` | `site.manage` | `{ theme }` — 사이트 테마만 |
| `PUT /api/admin/site/themes/{id}/menu` | `site.manage` | 메뉴 설정 칸 전부 → 같은 모양 |
| `GET /api/site/theme` (공개) | — | `{ theme, …지금 테마의 메뉴 설정 평평한 칸 }`(user-web `useThemeApi.types.ts`) |
| `GET/PUT /api/admin/home-layouts/{theme}`, `GET …/{theme}/defaults` | `site.read`/`site.manage` | admin `useHomeLayoutApi.ts` 머리말 |
| `GET /api/home/gadgets` (공개) | — | `{ theme, layout, gadgets }`(user-web `useHomeGadgetApi.types.ts`) |
- 서버가 줄 테마별 기본값(`themeMenuDefaults` 등)은 지금 관리자 목업(`site-theme-mock.ts`)이 user-web `THEMES` 값을 복사해 둔다 — 서버가 주면 원천은 서버 한 곳.
- `AdminScreenRegistry` 에 테마 화면 한 줄(`/settings/theme`, `site.read`). 회원 보기 설정 `menuPosition` 칸(`/api/me/settings`)도 서버에 없다.

## 미확정 사항
- 운영 배포: 관리자가 띄울 공개 사이트 주소(서버의 공개 주소 설정과 맞출지), 공개 사이트 웹 서버의 `frame-ancestors` 에 관리자 주소 허용.
- 실제 화면은 이 브라우저의 공개 사이트 로그인 상태로 보인다(비로그인 화면으로 볼지).
- 새로 넣은 가젯은 그 테마 공개 홈에 같은 종류가 있어야 실제 화면에 보인다(서버가 새 가젯의 기본 데이터를 줄 때까지). 가젯 내용 설정(게시판·개수·꾸미기) 편집 화면은 범위 밖.
- 키보드로 가젯 옮기기는 없다(마우스 끌기만).
- 게시판·페이지 레이아웃의 내용.

## 세션 정리 결정 (2026-10-28)
- 테마 선택의 자리 = 이 화면(`site-settings-contract.md` 의 「일반 > 공개 사이트 테마」 구획은 옮겼다 — 테마 API 계약은 그쪽 「테마」 절이 정본, 메뉴 설정 칸은 이 문서가 정본).
- 메뉴 노드의 첫 줄 구분선(`divider`)·항목 설명(`description`) 칸은 `menu-contract.md` 「구분선·설명」 절이 정본.
