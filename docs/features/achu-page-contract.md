# 아추!(아이로빗 추천) 페이지 · 메뉴 이름 그림 계약 (화면 확정 2026-11-19 — 서버 전)

**의도**: 운영자가 고른 하이라이트를 모아 보는 공개 페이지 「아추!」를 상단 메뉴에 둔다. 인기글(`/popular`, 반응 순위)은 그대로 두고 따로 있다. 메뉴의 「아추!」는 헤더 「아이로빗」 캐릭터 로고와 같은 귀여운 글꼴(개구체 Gaegu 700) 그림으로 보인다(고딕 글자 로고 글꼴 아님 — 사용자 2026-11-19) — 웹 글꼴을 싣지 않으려고 이름을 SVG 패스로 굳혀 둔다(사용자 결정).
**관련**: 하이라이트 관리 `popular-highlight-contract.md`(지금은 노출·순서만, 기간 없음) · 메뉴 `menu-contract.md` · 로고 글꼴 `robotikon/logo/build.mjs`(캐릭터 로고 글꼴 개구체 Gaegu 700).

## 화면 (확정)
- **`/achu?period=DAY|WEEK|MONTH&page=`**(기본 `DAY`, 기본값은 주소에서 뺀다) — `apps/user-web/app/pages/achu.vue`. 인기글과 같은 카드(`gadget-card` 머리 없음)·`PageTitleHead`(제목 = 「아추!」 로고 글꼴 그림, 화면 읽기 「아추! — 아이로빗 추천」)·기간 탭(키보드: 화살표·Home·End 로 초점, Enter·Space 로 고름).
- 탭 「일간 아추!」·「주간 아추!」·「월간 아추!」 + 탭 옆 범위(오늘 `11.19` · 주간 `11.16 ~ 11.22` · 월간 `11월`). 설명 한 줄은 기간마다(「오늘/이번 주에/이번 달에 운영자가 고른 글이에요」).
- 글 줄 = 홈 「아추!」 가젯 모바일 목록형 줄과 같은 틀: 줄마다 선 · 왼쪽 64×48(4:3) 사진 칸 · 제목 한 줄(500 · 댓글 수) · 정보 줄(게시판 · 시간 …… 추천). 사진 = 하이라이트 대표 이미지 → 글 썸네일, 없으면 게시판 표시색 14% 옅은 면 + 게시판 이름 첫 글자(60%). 차단한 회원 글은 접는다(`FoldedRow`). 제목 = 하이라이트 제목 → 글 제목.
- 쪽 번호(20개씩, `BoardPager`) · 빈 화면(일간이 비면 [주간 아추! 보기], 그 밖은 [게시판 둘러보기]) · 오류(다시 시도) · 로딩 뼈대.
- 문구는 i18n 업무 `achu`(user-web `i18n/locales/{ko,en}/achu.ts`), 규칙 `utils/util-achu.ts`, 문구 계산 `utils/util-achu-messages.ts`.

## 기간 정책 (사용자 2026-11-19 — 안 A, 서버 단계 결정 포함)
- 대상 = **홈 하이라이트만**(`place = home` — 홈 「아추!」 가젯에 고른 글). 팁 묶음·가젯 페이지 하이라이트는 넣지 않는다.
- **고른 날** = 노출 시작(`show_from`)이 있으면 그 시각, 없으면 만든 시각(`created_at`) — 새 DB 칸 없음. 노출을 껐다 켜도 고른 날은 그대로.
- 일간 = 오늘(서비스 달력 한국시간 0시~), 주간 = 이번 주(시작 요일은 인기글 설정 `weekStart`), 월간 = 이번 달 1일~. 고른 날이 그 범위 안이고 지금보다 늦지 않은 것.
- **노출 기간이 끝난 것도 남긴다**(주간·월간이 모아 보기 역할). 운영자가 노출을 끈 것(`visible = false`)·지금 볼 수 없는 글(지운·가린·권한 밖)은 뺀다.
- 차례 = 고른 날 최신순(같으면 id 큰 것 먼저). 한 글은 고른 날이 든 기간에만 나온다(오늘 고른 글은 일간·주간·월간 모두에 든다).

## 메뉴 이름 그림 (확정)
- **관리자 메뉴 항목 창**(`MenuItemDialog`): 영문명 아래 「이름 모양」 = `보통 글자`(기본) · `로고 글꼴 그림`. 견본(`MenuTextStyleField`)에 그림이 보인다. 안내: 이름을 고치면 저장 때 다시 만들고, 영어 화면은 영문명을 보통 글자로.
- **공개 메뉴**: 항목에 `nameArt` 가 있으면 이름 자리에 그림(`AppMenuItemLabel` 한 곳 — PC 막대·드롭다운·세로 메뉴·모바일 상단 탭·모바일 메뉴 줄 모두). 채움 = 글자 색(`currentColor`, 「글자 색」 설정 그대로), 그림이면 「굵게」를 칠하지 않는다, 배지는 그대로 옆에. 영문명을 보이는 언어면 보통 글자. 화면 읽기는 `label`. 그림 칸 상자도 글자 한 줄 높이로 잡아 활성 밑줄이 다른 메뉴와 같은 거리에 온다(사용자 2026-11-19 「인기글처럼 떨어져 보여야」).
- 그림 모양: `nameArt { d, w, h, em, sw? }` — `d` 는 (0,0)~(w,h) 안의 채움 패스(소수 한 자리), `em` 은 그릴 높이(글자 크기 배수, 「아추!」 0.9), `sw` 는 같은 색 테두리 두께(viewBox 단위, 「아추!」 2 — 캐릭터 로고 `STROKE` 처럼 획을 두껍게 해 옆 메뉴 굵기와 맞춘다, 최대 10). 공개 화면은 `d` 가 그리기 명령·숫자만일 때만 그린다(아니면 보통 글자).
- 항목(ITEM)만. 폴더 이름 그림은 두지 않는다.

## API 후보 (서버 단계에서 확정)
- `GET /api/highlights?period&page&size`(api, 로그인 없이) → `{ items: [{ id, post: PostFeedRow, title, summary, imageUrl, pickedAt }], page, totalPages, totalCount, hasNext, period, from, to, weekStart }` — `from`·`to` 는 그 기간 범위(ISO UTC, `to` = 다음 기간 시작). `400 VALIDATION fields.period|page|size`. 화면 타입 `composables/highlight/api/useHighlightApi.types.ts`.
- 고른 시각 `pickedAt` = `coalesce(show_from, created_at)`(새 칸 없음).
- 메뉴 항목 `name_style`(`TEXT`·`LOGO`) + 그림 패스 칸. 관리자 트리 요청·응답에 `nameStyle`, 공개 `GET /api/menu` 항목에 `nameArt`(LOGO 이고 그림이 있을 때만).
- 메뉴 BUILTIN 키 `ACHU`(`/achu`, 로그인 불필요) — 서버 레지스트리·관리자 `UtilMenu.BUILTINS`·설치 프리셋(인기글 뒤, 이름 「아추!」, 이름 모양 LOGO)에 더한다.

## 남은 것
- (결정 2026-11-19) 그림은 **관리자 화면이 만든다** — 저장 전에 opentype.js 로 개구체 Gaegu 700 조각 글꼴을 받아 이름을 패스로 굳혀 `nameArt` 로 보낸다. 서버는 모양 검사·저장만. 설치 프리셋의 「아추!」 패스는 스크립트로 미리 굳힌다.
- 글꼴 고지: 개구체(Gaegu)는 OFL — 로고 글꼴 고지(`header-logo` 남은 일)와 함께.
- 게시판 목록 이모지(develop 7ded62b1 에 들어옴)를 이 페이지 사진 칸에도 쓸지 — 홈 목록형과 같게 하면 이모지 → 첫 글자.

## 구현 결과 (2026-11-19, 브랜치 `claude/achu-page` — 실제 서버에서 아직 돌려 보지 않았다)
- **shared V258** `menu_nodes.name_style`(TEXT 기본·LOGO, CHECK) · `name_art` jsonb(LOGO·ITEM 일 때만, CHECK). 합치기 전에 shared 마지막 번호를 다시 본다.
- **공개 `GET /api/highlights`**(api, permitAll, `Cache-Control: no-store`) — `PublicHighlightController`·`HighlightPeriodService`·`HighlightPeriodRepositoryQDSL`, 줄은 홈 하이라이트 가젯과 같은 `HighlightPicks`(보이는 글·켜진 게시판·19금 제외). 오류 `fields.period`(`highlight.period.invalid`)·`page`(1~100)·`size`(1~50). 마지막 쪽을 넘으면 빈 `items` 에 요청한 `page`, 전체 0건이면 `page=1`. 주 시작은 `popular_settings` 행이 없으면 MONDAY.
- **메뉴**: 관리자 트리 요청 `MenuNodeRequest`·응답 `MenuItemView` 끝에 `nameStyle`·`nameArt`(폴더는 늘 TEXT). 검사 오류 경로 `…nameStyle`·`…nameArt`·`…nameArt.d|w|h|em|sw`, 문구 `menu.nameStyle.choice`·`menu.nameArt.*`. 공개 `MenuItemNodeView.nameArt`(LOGO 이고 그림 있을 때만, 그 밖엔 null). 감사 요약 줄에 `[logo {w}x{h} #{해시}]`. `sw` 가 빠지면 null 로 저장·응답.
- **BUILTIN `ACHU`**(`/achu`, matchPrefix, 로그인 불필요) — 서버 `MenuBuiltin`·관리자 `UtilMenu.BUILTINS`·문구 「아추!」/「Achu!」. 설치 프리셋 `neutral.json`·`irobit.structure.json` 에 인기글 뒤 「아추!」(LOGO, `UtilAchu.TITLE_ART` 그림)를 넣었다(`irobit.structure.json` 은 `name_style`·`name_art` 칸을 모든 행에 더하고 sort_order 를 밀었다).
- **관리자 화면 그림 만들기** `composables/menu/useMenuNameArt.ts` + `utils/util-menu-name-art.ts` — `@fontsource/gaegu` 5.3.0 조각 woff(별칭 `#gaegu-font`)를 이름 글자에 맞춰 받아 `opentype.js` 1.3.4 로 패스(높이 = 잉크 높이 × 0.9/0.672, 테두리 2). 「아추!」는 `TITLE_ART` 와 같은 값이 나온다. 글꼴에 없는 글자(이모지 등)면 [적용]을 막고 안내.
- user-web 목업은 지웠다(`/achu` 는 실제 API, 메뉴는 서버 응답 그대로). 사진 칸은 `GadgetThumb`(게시판 목록 이모지 → 첫 글자).
- 검증: `compileJava`·`compileTestJava`(서버 셋), `typecheck`(두 앱). 테스트·로컬 DB 적용·실제 호출은 아직.
