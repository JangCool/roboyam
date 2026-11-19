# 아추!(아이로빗 추천) 페이지 · 메뉴 이름 그림 계약 (화면 확정 2026-11-19 — 서버 전)

**의도**: 운영자가 고른 하이라이트를 모아 보는 공개 페이지 「아추!」를 상단 메뉴에 둔다. 인기글(`/popular`, 반응 순위)은 그대로 두고 따로 있다. 메뉴의 「아추!」는 헤더 「아이로빗」 캐릭터 로고와 같은 귀여운 글꼴(개구체 Gaegu 700) 그림으로 보인다(고딕 글자 로고 글꼴 아님 — 사용자 2026-11-19) — 웹 글꼴을 싣지 않으려고 이름을 SVG 패스로 굳혀 둔다(사용자 결정).
**관련**: 하이라이트 관리 `popular-highlight-contract.md`(지금은 노출·순서만, 기간 없음) · 메뉴 `menu-contract.md` · 로고 글꼴 `robotikon/logo/build.mjs`(캐릭터 로고 글꼴 개구체 Gaegu 700).

## 화면 (확정)
- **`/achu?period=DAY|WEEK|MONTH&page=`**(기본 `DAY`, 기본값은 주소에서 뺀다) — `apps/user-web/app/pages/achu.vue`. 인기글과 같은 카드(`gadget-card` 머리 없음)·`PageTitleHead`(제목 = 「아추!」 로고 글꼴 그림, 화면 읽기 「아추! — 아이로빗 추천」)·기간 탭(키보드: 화살표·Home·End 로 초점, Enter·Space 로 고름).
- 탭 「일간 아추!」·「주간 아추!」·「월간 아추!」 + 탭 옆 범위(오늘 `11.19` · 주간 `11.16 ~ 11.22` · 월간 `11월`). 설명 한 줄은 기간마다(「오늘/이번 주에/이번 달에 운영자가 고른 글이에요」).
- 글 줄 = 홈 「아추!」 가젯 모바일 목록형 줄과 같은 틀: 줄마다 선 · 왼쪽 64×48(4:3) 사진 칸 · 제목 한 줄(500 · 댓글 수) · 정보 줄(게시판 · 시간 …… 추천). 사진 = 하이라이트 대표 이미지 → 글 썸네일, 없으면 게시판 표시색 14% 옅은 면 + 게시판 이름 첫 글자(60%). 차단한 회원 글은 접는다(`FoldedRow`). 제목 = 하이라이트 제목 → 글 제목.
- 쪽 번호(20개씩, `BoardPager`) · 빈 화면(일간이 비면 [주간 아추! 보기], 그 밖은 [게시판 둘러보기]) · 오류(다시 시도) · 로딩 뼈대.
- 문구는 i18n 업무 `achu`(user-web `i18n/locales/{ko,en}/achu.ts`), 규칙 `utils/util-achu.ts`, 문구 계산 `utils/util-achu-messages.ts`.

## 기간 정책 (사용자 2026-11-19 — 안 A)
- 기간은 운영자가 하이라이트로 **고른 시각**(노출 시작) 기준: 일간 = 오늘(서비스 달력 한국시간 0시~), 주간 = 이번 주(시작 요일은 인기글 설정 `weekStart`), 월간 = 이번 달 1일~.
- 노출 중인(`visible`) 하이라이트만, 지금 볼 수 없는 글(지운·가린·권한 밖)은 뺀다. 차례 = **고른 시각 최신순**(목업 가정 — 운영자 순서로 바꿀 수 있다).
- 한 글은 고른 시각이 든 기간에만 나온다(오늘 고른 글은 일간·주간·월간 모두에 든다 — 기간이 포함 관계라서).

## 메뉴 이름 그림 (확정)
- **관리자 메뉴 항목 창**(`MenuItemDialog`): 영문명 아래 「이름 모양」 = `보통 글자`(기본) · `로고 글꼴 그림`. 견본(`MenuTextStyleField`)에 그림이 보인다. 안내: 이름을 고치면 저장 때 다시 만들고, 영어 화면은 영문명을 보통 글자로.
- **공개 메뉴**: 항목에 `nameArt` 가 있으면 이름 자리에 그림(`AppMenuItemLabel` 한 곳 — PC 막대·드롭다운·세로 메뉴·모바일 상단 탭·모바일 메뉴 줄 모두). 채움 = 글자 색(`currentColor`, 「글자 색」 설정 그대로), 그림이면 「굵게」를 칠하지 않는다, 배지는 그대로 옆에. 영문명을 보이는 언어면 보통 글자. 화면 읽기는 `label`. 그림 칸 상자도 글자 한 줄 높이로 잡아 활성 밑줄이 다른 메뉴와 같은 거리에 온다(사용자 2026-11-19 「인기글처럼 떨어져 보여야」).
- 그림 모양: `nameArt { d, w, h, em, sw? }` — `d` 는 (0,0)~(w,h) 안의 채움 패스(소수 한 자리), `em` 은 그릴 높이(글자 크기 배수, 「아추!」 0.9), `sw` 는 같은 색 테두리 두께(viewBox 단위, 「아추!」 2 — 캐릭터 로고 `STROKE` 처럼 획을 두껍게 해 옆 메뉴 굵기와 맞춘다, 최대 10). 공개 화면은 `d` 가 그리기 명령·숫자만일 때만 그린다(아니면 보통 글자).
- 항목(ITEM)만. 폴더 이름 그림은 두지 않는다.

## API 후보 (서버 단계에서 확정)
- `GET /api/highlights?period&page&size`(api, 로그인 없이) → `{ items: [{ id, post: PostFeedRow, title, summary, imageUrl, pickedAt }], page, totalPages, totalCount, hasNext, period, from, to, weekStart }` — `from`·`to` 는 그 기간 범위(ISO UTC, `to` = 다음 기간 시작). `400 VALIDATION fields.period|page|size`. 화면 타입 `composables/highlight/api/useHighlightApi.types.ts`.
- 하이라이트에 **고른 시각** 칸(노출 시작 — 새로 고르거나 다시 노출할 때 갱신할지는 서버 단계에서 정한다). 기존 행의 값은 만든 시각으로 채운다.
- 메뉴 항목 `name_style`(`TEXT`·`LOGO`) + 그림 패스 칸. 관리자 트리 요청·응답에 `nameStyle`, 공개 `GET /api/menu` 항목에 `nameArt`(LOGO 이고 그림이 있을 때만).
- 메뉴 BUILTIN 키 `ACHU`(`/achu`, 로그인 불필요) — 서버 레지스트리·관리자 `UtilMenu.BUILTINS`·설치 프리셋(인기글 뒤, 이름 「아추!」, 이름 모양 LOGO)에 더한다.

## 미확정
- **그림을 어디서 만드나**: (가) 관리자 화면이 저장 때 opentype.js + 개구체 Gaegu 700 조각 글꼴로 만들어 보낸다(서버는 패스 글자만 검사) · (나) 서버가 저장 때 만든다(글꼴 파일을 서버에 두고 AWT 글리프 윤곽 — 리눅스 런타임 확인 필요). 설치 프리셋의 「아추!」 패스는 스크립트로 미리 굳힌다.
- 탭 안 차례(고른 시각 최신순 vs 운영자 순서), 다시 노출한 하이라이트의 고른 시각 갱신 여부.
- 글꼴 고지: 개구체(Gaegu)는 OFL — 로고 글꼴 고지(`header-logo` 남은 일)와 함께.
- 게시판 이모지 썸네일(홈 목록형 시험 브랜치의 목업)이 들어오면 이 페이지 글자 칸 앞에 이모지를 둘지.

## 화면 목업 상태 (2026-11-19, 브랜치 `claude/achu-page`)
- ⚠ MOCK `composables/highlight/api/mock/achu-mock.ts` — 실제 최신글 30개에 고른 시각을 붙여 하이라이트처럼(앞 3개 오늘·4개 이번 주·8개 이번 달). 스위치 `?mock=achu-empty|achu-day-empty|achu-fail`.
- ⚠ MOCK `composables/menu/api/mock/menu-achu-mock.ts`(user-web) — 받은 메뉴의 인기글 뒤·모바일 탭에 「아추!」(`ACHU`, `nameArt`)를 끼운다. `?mock=achu-text`(그림 없이) · `?mock=achu-off`.
- ⚠ MOCK `composables/menu/api/mock/menu-name-art-mock.ts`(admin-web) — 견본 그림은 「아추!」만, `nameStyle` 은 저장 요청에 아직 싣지 않는다(`UtilMenu.toDto` 의 MOCK/REAL 줄).
- 「아추!」 패스 원본 = `UtilAchu.TITLE_ART`(개구체 Gaegu 700).
