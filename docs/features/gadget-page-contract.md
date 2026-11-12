# 가젯 페이지 계약 (**화면 확정 2026-10-29**)

**의도**: 홈처럼 가젯 격자로 꾸미는 공개 페이지를 여러 개 만든다(사용자 결정 2026-10-29 — 「커뮤니티」 전용 화면이 아니라 일반화). 첫 페이지 「커뮤니티」, 다음 「주제」.
페이지 정보(이름·설명·영문 주소·기준 폴더·글쓰기 단추)는 하나, 배치는 홈처럼 **테마마다 따로**다. 메뉴 폴더의 「누르면 가는 곳」이 페이지를 가리킬 수 있다.
배치·가젯 모양·편집 방식(실제 화면 iframe 편집)은 `site-theme-layout-contract.md` 의 홈 구성과 같다 — 여기에는 다른 점만 적는다.

## 화면 (기준 코드)
- 관리자 `설정 › 테마 › 레이아웃` 의 페이지 종류 「페이지」 = `/settings/theme?kind=page&page=<id>`(`pages/settings/theme/[[tab]].vue`).
  - 고르기 줄 `components/theme/GadgetPagePicker.vue`(목록 · 공개 주소 새 탭 · [페이지 정보] · [새 페이지], 없으면 빈 안내) · 만들기/고치기 창 `GadgetPageDialog.vue`(처음 배치 미리, [페이지 지우기]).
  - 편집은 홈과 같은 `GadgetLayoutEditor`(prop `page`) + `ThemeLivePreview`(prop `path` `/p/{key}` · `page` 머리). 「테마 기본 배치로」 대신 「처음 배치로」.
  - 가젯 ⚙ 설정 창 `GadgetSettingsDialog.vue` — 칸·범위·처음 값의 원천은 admin `utils/util-gadget-config.ts`(아래 「가젯 설정」). 홈에서도 같은 창이다.
- 공개 `/p/{key}`(user-web `pages/p/[key].vue`) — 지금 테마의 홈 템플릿(`GadgetGrid`). 없는 페이지는 「찾을 수 없어요」.
  - 머리(사용자 결정 2026-11-05): 이름·설명은 화면에 그리지 않는다(이름은 화면 읽기용 `h1.sr-only` 만). 글쓰기 단추를 켰으면 오른쪽 아래 떠 있는 둥근 글쓰기 단추(`components/page/GadgetPageWriteFab.vue` — 하위 메뉴 없이 누르면 바로 `/write?board=`). 관리자의 「설명」 칸은 지금 공개 화면에 쓰이지 않는다.
- 메뉴 관리: 「누르면 가는 곳 › 사이트 화면 › 목록에서 고르기」 맨 위 「가젯 페이지」 묶음(연결 유형 `GADGET_PAGE`, 아래 「세션 정리 결정」).

## 필드·검증
- `key` 영문 주소: `^[a-z][a-z0-9-]{1,29}$`, 겹치지 않음(`400 fields.key`). 공개 주소 `/p/{key}`. 바꿔도 메뉴 연결은 페이지 ID 라 따라간다(옛 주소는 열리지 않는다).
- `name` 1~40자(필수) · `nameEn` 0~40자(빈 글자 = null) · `description` 0~200자 · 줄바꿈·제어 문자 금지(이름).
- `folderId` 기준 폴더 = 공개 메뉴 `main` 의 폴더 ID(깊이 무관) 또는 null. 없는 폴더·폴더가 아닌 노드는 `400 fields.folderId`. 폴더가 나중에 지워지면 기준 폴더 없음으로 본다(FK 없음 — 메뉴 저장은 트리를 지우고 다시 넣는다).
  - 폴더 게시판 = 그 폴더 아래(하위 폴더까지) `BOARD` 항목의 게시판, 메뉴 차례, 겹치면 한 번. 꺼진 게시판은 공개 데이터에서 빠진다.
- `writeButton` 참/거짓. 글쓰기 단추는 `/write?board=<기준 폴더의 첫 켜진 게시판>`(없으면 `/write`) — 판정은 글쓰기 화면·서버.
- 페이지 수 한도 50.
- 처음 배치(만들 때 서버가 기준 폴더의 **켜진** 게시판으로 만든다 — 서버 `GadgetPageInitial`, admin `UtilGadgetPage.initialLayout` 은 만들기 창의 미리 보기, 24칸 · 줄마다 꽉 차게, 가젯 ID `seed-<n>`):
  「{폴더} 인기글」(POST_LIST POPULAR·기간 탭·대상 FOLDER, 24칸) / 게시판 카드(BOARD_CARDS, 갤러리로 쓴 게시판을 뺀 폴더 게시판마다 하나 PICK, 12+12 두 열, 홀수면 마지막 24칸) /
  「{폴더} 최신글」(POST_LIST LATEST 16개 두 열·대상 FOLDER, 24칸) / 맨 아래 「{게시판}」(GALLERY PICK 8개, 24칸 — 사진·영상 게시판: 목록 모양 `GALLERY` 인 첫 폴더 게시판, 없으면 갤러리 보기 전환(`viewToggle`)이 켜진 첫 게시판, 없으면 이 줄 없음). 기준 폴더가 없으면 이름 자리는 「전체」.
  커뮤니티 예(사용자 결정 2026-10-29): 커뮤니티 인기글 / 자유게시판 · 유머게시판(두 열) / 커뮤니티 최신글 / 영상·짤(전체 폭, 맨 아래).
  높이(h = 최소 높이, 행): 인기글 15 · 갤러리 11 · 게시판 카드 줄 13 · 최신글 15 — 보통 내용에 맞췄다(내용이 길면 격자가 늘린다). **가젯 상자 높이는 운영자 높이 hPx(= h×15 + (h−1)×16)와 내용 높이 contentPx(내용 + 테마 위·아래 여백, 정수)로 정한다. hPx − contentPx 가 한 칸(31px) 이상이면 운영자가 일부러 키운 것으로 보고 상자 = hPx 로 늘린다(내용은 위에서 시작하고 남는 공간은 아래, 빈 상태 안내는 가운데). 한 칸 미만이거나 hPx 가 더 작으면 상자 = contentPx 로 정확히 맞춰 위·아래 여백이 같다(한 칸 미만은 칸 단위 반올림일 뿐이다). 데스크톱 배치는 저장된 x·w·y 의 읽는 순서로 위에서부터 px 단위로 쌓는다: 각 가젯의 위 끝 = 앞선 가젯 중 열이 겹치는 것들의 아래 끝 중 가장 낮은 값 + 16px(없으면 0). 같은 y·같은 h 로 나란히 둔 짝은 각자 위 규칙으로 정한 상자 높이 중 가장 큰 값으로 맞춰 아래 끝을 맞추고(user-web `UtilGadgetGrid.place` 「같은 줄 맞추기」), 늘어난 쪽도 내용은 위에서 시작한다. 모바일 한 열과 게시판 본문 칸은 내용 높이 그대로다**(사용자 결정 2026-10-31 「가젯을 늘리고 줄이면 높이도 따라야」 · 「최소 상태에서 상하 여백 동일」 — 칸마다 절대 위치, 저장 형식·admin 끌기 스냅·충돌 계산은 그대로, 첫 그리기는 재기 전이라 hPx 로 그렸다가 한 번 다시 쌓는다). 편집기는 칸을 점선으로 그리고(점선 = 상자), 크기 끌기 중 놓일 h 로 상자를 바로 늘이고 줄인다. 크기 꼬리표는 최적 칸 수 need = max(1, ceil((contentPx + 16) / 31))(내용이 다 들어가는 가장 작은 칸 수 — 이때 남는 0~30px 은 반올림이라 상자 = contentPx)와 h 를 견준다: h < need 빨강 「더 줄일 수 없어요 — 최소 N칸」 · h > need 주황 「n칸 줄여도 내용이 다 보여요」(n = h − max(need, admin 종류별 최소 높이 — 미리보기 메시지 `minH`)) · 같으면 없음(저장은 막지 않음 — 주황을 따라 줄인 끝은 늘 「표시 없음」). 칸에는 실제로 늘린 px 를 `data-grid-stretched` 로 붙인다(0 이면 없음 — `scripts/gadget-ink-padding.js` 가 읽음).
  저장한 적 없는 테마는 이 처음 배치를 보인다(꾸미기는 그 테마의 기본 꾸미기 + 아이콘 — 아이로빗 테마는 홈 아이로빗 시드처럼 흰 카드 + 아이콘 머리). 기준 폴더를 바꿔도 처음 배치는 다시 만들지 않는다.

## 가젯 설정(⚙ — 홈·가젯 페이지·게시판 레이아웃 공통, 2026-10-29 넓힘 · 2026-10-30 8종 전부 + 모양·노출, 화면 확정)
창은 「내용」「모양」「노출」 세 탭(`GadgetSettingsDialog.vue`, 범위의 원천 admin `util-gadget-config.ts`·`util-gadget-style.ts` = 공개 읽기 user-web `UtilGadget`).
| 종류 | 내용 칸(서버 JSON `config`) |
|---|---|
| `GROUP_BUY` | `count` 1~10(기본 5) · `sort` DEADLINE·NEWEST·POPULAR (기존) |
| `POST_LIST` | `boardScope` · `boardIds` · `count` 1~30(기본 10) · `sort` LATEST·POPULAR·VIEWS·LIKES·COMMENTS · `periodTabs`(POPULAR 일 때만 참) · 보기 모양 `rowStyle` HEADLINE·RANK·ARTICLE·COMPACT · `columns` 1·2 · `thumbnail` AUTO·NONE·RIGHT·LEFT · `titleLines` 0·1·2 · `lead` NONE·HERO · `meta` BOARD·PREFIX·COMMENTS·TIME·AUTHOR 부분집합 · `dividers` · `newBadgeHours` 0~72 · `hotBadgeLikes` 0~1000 (`badge`·`rank` 는 화면이 보내지 않음 — 저장 값 유지) |
| `GALLERY` | `boardScope` · `boardIds` · `count` 1~24(기본 6) |
| `BOARD_CARDS` | `boardScope` · `boardIds`(카드로 보일 게시판) · `perBoard` 1~10(기본 4) · `cardHead` 카드 머리(로고·이름·「게시판 가기」) 보이기(기본 켬 — 게시판 하나짜리 카드는 꺼서 가젯 제목과 겹치지 않게, 처음 배치의 카드는 끔) · `columns` AUTO·2·3 · `outline`·`dividers` 카드 외곽선·줄 구분선(기본 끔) · `leadPlaceholder` 사진 없는 대표 글에 「이미지 없음」 자리(기본 켬 — 카드끼리 모양이 같게, 2026-11-06) |
| `HIGHLIGHT` | `count` 1~6(기본 5 — 공개 데이터도 이 수) · `lead` HERO·NONE(기본 HERO) · `thumbs` ALL·FIRST·NONE(기본 ALL) · `showAuthor` 작성자(아바타·닉네임) 보이기(기본 켬 — 칸이 없는 옛 가젯도 켬, 2026-11-10) |
| `GROUPS` | `count` 1~6(기본 3) |
| `QUICK_LINKS` | `shape` PILL·TAB · `items` 0~12개 `{label 1~12자(앞뒤 공백 제거), kind ALL·POPULAR·LATEST·GROUPS·GROUP_BUY·BOARD·PATH, value}` — `BOARD` 는 있는 게시판 slug, `PATH` 는 `/` 로 시작하는 사이트 안 경로(`//`·스킴·공백·제어 문자·역슬래시 없음, 300자 이하), 나머지는 null |
| `NOTICE_TEXT` | `text` 0~300자(줄바꿈 허용, 평문) · `format` PLAIN·BULLETS |
| `AD` | `adType` WIDE_BANNER·STRIP_BANNER·SQUARE_BANNER·SMALL_BANNER(기본 WIDE_BANNER) — 공개 `data` null, 화면이 `/api/ads` 로 채움(`ad-contract.md` 「공개 노출 서버 구현 결정」) |
| `AD_TEXT_LINE` | `count` 1~5(기본 3) — 위와 같음 |
| 공통 — `HIGHLIGHT`·`POST_LIST`·`BOARD_CARDS`·`GALLERY`·`GROUPS`·`GROUP_BUY`·`HOT_DEALS`·`QUICK_LINKS` | `hover` 마우스 올림 효과(boolean, 기본 켬 — 칸이 없는 옛 가젯도 켬, 2026-11-10). 창은 「모양」 탭 「마우스 올림 효과」. 공개: 줄 항목 옅은 바탕(`gadget-hover` — surface-100/다크 800, 좌우 8px 넓힘), 테두리 상자(소모임 작은 카드·바로가기) 상자 바탕(`gadget-hover-box`), 사진 카드 제목 밑줄(`gadget-hover-photo`) — `main.css`. 안내 글·광고 2종은 칸 없음(서버가 버림). 오류 `config.hover` boolean 아님 → `homeLayout.gadget.config.option` |
- **모양·노출(모든 종류)**: `title` 1~40자(줄바꿈·제어 문자 금지 — 저장 가젯도 바꾼다) · `desktop`·`mobile`(둘 다 false 금지) · `style`: `icon` `^pi-[a-z0-9-]{2,60}$` 또는 null(제목 아이콘 칸 전부는 아래 「가젯 제목 아이콘」) · `headerStyle` BAR·UNDERLINE·FILLED·PLAIN·HIDDEN · `titleSize` S·M·L · `titleColor`·`cardColor` 프리셋 12색 이름·`#rrggbb`·null · `subtitle` 0~40 · `cardLook` DEFAULT·FLAT·OUTLINE·TINT · `mobileFold` NONE·OPEN·CLOSED · `empty{keep, message, text 0~40}`. 오류 `400 fields.gadgets[i].title|desktop|mobile|style.<칸>`.
- `boardScope`: `ALL` 전체(글 목록·갤러리는 최신글과 같은 범위, 게시판 카드는 켜진 일반 게시판 앞 6개) · `FOLDER` 이 페이지의 기준 폴더 게시판 전체(가젯 페이지에서만 — 그 밖에서 보내면 400) · `BOARD` 지금 보는 게시판(게시판 레이아웃에서만 — `board-layout-contract.md`) · `PICK` `boardIds`(게시판 slug 1~20개, 고른 차례).
  저장된 설정에 `boardScope` 가 없으면(옛 시드) `boardIds` 가 있으면 PICK, 없으면 ALL 로 읽는다. FOLDER 인데 기준 폴더가 없거나 게시판이 없으면 ALL 과 같다.
- 저장: `style`·`config` 는 보낸 칸만 저장 값에 덮는다(보내지 않은 칸 — `badge` 등 — 은 그대로), `null` 이면 그대로. 오류 `400 fields.gadgets[i].config.<칸>`.
- 새로 넣은 가젯의 처음 꾸미기(서버 `GadgetKinds.baseStyle(theme, type)`): 다음식·네이버식 카드 + 막대 머리 · 아이로빗 흰 카드 + 장식 없는 머리 + 종류 아이콘(홈 아이로빗 시드와 같은 모양, 2026-10-30) · 기본 카드 없는 구역.
- 관리자 응답: `config` 는 이 표의 칸(저장 값, 없으면 처음 값 — 8종 전부), `style` 은 위 칸 전부(저장 값, 없으면 테마 기본 꾸미기). 기본·처음 배치 응답도 같다.

## 가젯 제목 아이콘 (사용자 시안 확정 2026-11-10 — 모든 종류 공통, 홈·가젯 페이지 `/p/{key}`·게시판 레이아웃 같은 머리 부품 `GadgetFrame`)
- 칸은 꾸미기 `style`(테마 기본이 있는 모양 값이라 `config` 가 아니라 여기 — 서버 `GadgetStyles`·검사 `GadgetPatches.style`, admin `UtilGadgetStyle`, 공개 `UtilGadget.readStyle`·`titleIcon`). ⚙ 「모양」 탭 「제목 아이콘」 구획(`GadgetTitleIconFields`).
  | 칸 | 값 |
  |---|---|
  | `iconShow` | boolean — 테마 기본: **모든 테마 true**(아이로빗도 — 사용자 2026-11-10 「아이로빗 기본 보이기」, 처음 시안의 「제목만」을 바꿈). 칸이 없는 옛 가젯·시드도 테마 기본으로 읽는다 |
  | `iconSource` | `PRIME`(기본)·`TABLER`·`UPLOAD` |
  | `icon` | PrimeIcons `^pi-[a-z0-9-]{2,60}$`(30→60 으로 넓힘 — 8.0.2 의 가장 긴 이름 49자)·null |
  | `iconTabler` | `{ name ^[a-z0-9]+(-[a-z0-9]+)*$ ≤60, paths[1~40]{ d(경로 명령·숫자만, ≤3000자 · 합 ≤12000), fill?/stroke? currentColor·none, opacity? 0~1 } }`·null — 관리자가 고른 Tabler outline 한 개의 path 조각을 그대로 저장, 공개 화면은 그 조각만 요소로 그린다(웹폰트·전체 목록을 싣지 않는다, `v-html` 없음) |
  | `iconUpload` | `{ fileId, url, svg }`·null — 저장 때는 `fileId` 만 믿고 서버가 파일(용도 `GADGET_ICON`)에서 주소·SVG 여부를 채우며 파일을 `IN_USE` 로. 없는 파일 → `style.iconUpload` `homeLayout.gadget.style.iconFile` |
  | `iconColor` | null(글자색 그대로 — 제목 색, 옅은 네모면 제목 색→주 색상)·프리셋 12색·`#rrggbb`. 올린 PNG·WEBP 는 칠하지 않는다(설정 창에서 숨김), 올린 SVG 는 모양만 따서(CSS mask) 칠한다 |
  | `iconBg` | `NONE`·`SOFT`(옅은 네모 — 아이콘 색 12%, 다크 18%) — 테마 기본: 아이로빗 SOFT, 그 밖 NONE |
- 직접 올리기 `POST /api/admin/home-layouts/icons`(site.manage, multipart `file`) → 201 `{ fileId, url, svg }`. SVG(로고 SVG 와 같은 `SvgInspector` — script·on*·foreignObject·외부 href/url() 이 있으면 **거절**, 10만 자) 또는 PNG·WEBP(다시 만들기), 200KB. 400 `fields.file` `gadgetIcon.type`·`gadgetIcon.size`·`site.svg.*`. 키 `gadget-icon/{버전}/icon.{svg|png|webp}`, 파일 용도 `GADGET_ICON`(**shared V178** — `ck_files_purpose`). 쓰지 않은 파일은 「올리다 만 파일 정리」가 지운다.
- 아이콘 고르기(관리자만): PrimeIcons 8.0.2 360개(`prime-icon-names.ts`) · Tabler Icons **3.49.0**(`@tabler/icons`, admin-web 의존성 — **MIT 라이선스**, 패키지의 LICENSE 로 충분 · 출처 표시 불필요) outline 5,184개는 고르기 창을 열 때만 `?raw` 로 읽는다. 검색 = 영어 이름·태그 + 한국어 별칭 몇 개, 한 번에 160개까지.
- 기본 아이콘(사용자 2026-11-10 「가젯마다 어울리는 아이콘」): 아이로빗 홈 시드 인기글 Tabler `flame` · 영상·짤 `photo-video` · 인기 소모임 `users-group`(나머지 PrimeIcons 그대로), 가젯 페이지 처음 배치 인기글 `flame` · 갤러리 `photo-video`(처음 배치 JSON 의 `tabler` 이름 → 서버 `GadgetTablerIcons` 가 그림 조각을 채움). 저장 가젯은 **shared V180** 이 처음 아이콘 그대로인 아이로빗 가젯만 바꿨다(게시판 카드는 지금 데모 게시판 이름별 — 팁과강좌 `pi-lightbulb` · 사용기 `message-star` · 자유 `pi-comments` · 유머 `mood-happy` · AI·로봇·테크 `robot` · 시사 `news` · 산업·주식 `chart-candle` · 게임 `device-gamepad-2` · 스포츠 `ball-football` · 연애·결혼 `pi-heart` · 직장·취업 `pi-briefcase`).
- 미정: 가젯에서 바꿔 뺀 올린 아이콘 파일(IN_USE)은 지우지 않는다(여러 테마·가젯이 같은 파일을 가리킬 수 있음 — 정리 배치는 나중에).

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
- **가젯 카드 안 여백(2026-10-31 — 공개 광고 목업 브랜치에서 모든 가젯에 적용)**: 위·아래·좌·우가 같게 보이도록 테마 카드 여백을 「아래 = 좌우, 위 = 좌우 − 머리 줄 안쪽 여백」으로 바꿨다(user-web `main.css` 의 `--gadget-card-pad`). 머리를 숨긴 카드(`data-headless`)는 네 쪽 모두 좌우 값이다. 상자 높이·배치는 위 「처음 배치」 높이 문장이 정본이다(상자 = 내용 + 위·아래 여백, px 배치·간격 16).
- 페이지마다 SEO 설명·대표 이미지, 페이지 머리 꾸미기(배경·아이콘)는 없다.
- 「주제」 페이지는 같은 방식으로 운영자가 만든다(기준 폴더 = 주제).
- 관리자 미리보기에서 새로 넣은 가젯은 공개 응답의 같은 종류 데이터를 빌려 보인다(저장하면 서버가 설정대로 고른다 — 홈과 같다).

## 세션 정리 결정 (2026-10-29)
- 메뉴 연결 유형 `GADGET_PAGE` 의 정본은 `menu-contract.md` 「가젯 페이지 연결」. 이미 「준비 중」으로 있던 `PAGE`(고정 페이지 — CMS)와 다른 유형이다.
- 가젯 ⚙ 설정 칸(위 표)의 정본은 이 문서다 — `site-theme-layout-contract.md` 의 「공구 가젯만」 서술은 이 표로 넓어졌다.

## 제목 카드에 가젯 넣기 (2026-11-10)
- 가젯 ⚙ 「노출」 탭의 「페이지 제목 카드에 넣기」(설정 `config.pageHead`, 기본 끔) — 가젯 페이지만(서버 `GadgetPatches` 가 PAGE 자리에서만 받는다), 항목이 있는 종류(`HOVER_KINDS` — 안내 글·광고 2종 제외).
- 켜면 그 가젯은 격자에서 빠지고 페이지 제목 카드 안 굵은 밑줄 아래에 한 줄 전체 폭으로 붙는다(팁·강좌·사용기처럼 제목과 첫 내용이 한 카드). 아래 가젯은 위로 당겨진다.
- 테마마다 가젯이 따로라 페이지 정보 창이 아니라 가젯 설정에 둔다. 한 페이지에 하나 — 편집기가 다른 가젯을 끈다(여럿이면 공개 화면은 읽는 순서 첫 것). 관리자 미리보기 편집 중에는 격자에 그대로 둔다.
- 제목 카드는 템플릿의 `lead` 슬롯으로 그린다(가젯 모양이 템플릿 것을 따르게). 기본 데이터: 커뮤니티·핫 로토픽의 인기글이 켜져 있다(설치 프리셋 포함).
