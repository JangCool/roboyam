# 게시판 레이아웃 계약 (**화면 확정 2026-10-30**)

**의도**: 테마마다 게시판 화면(목록 `/boards/{slug}` · 글 보기 `/posts/{id}`)의 둘레를 가젯으로 꾸민다. 홈과 같은 격자 편집기에 고정 칸 「게시판 본문」이 하나 있고, 공개 화면은 그 자리에 게시판 화면을 그린다.
배치·가젯·편집 방식(실제 화면 iframe 편집, ⚙ 설정 창)은 `site-theme-layout-contract.md` 홈 구성 · `gadget-page-contract.md` 「가젯 설정」과 같다 — 여기에는 다른 점만 적는다.

## 화면 (기준 코드)
- 관리자 `설정 › 테마 › 레이아웃` 페이지 종류 「게시판」 = `/settings/theme?kind=board&board=<slug>&screen=post`(`pages/settings/theme/[[tab]].vue`).
  - 미리 볼 게시판·「게시판 목록 | 글 보기」 `components/theme/BoardLayoutPicker.vue`(글 보기 = 그 게시판 최신 공개 글, `post.read` 없거나 글 없으면 목록만).
  - 편집 `HomeLayoutEditor`(prop `board`) — 적용 화면 스위치 · [추천 배치](묻고 바꿈) · 「테마 기본 배치로」(본문만). 순수 계산 `utils/util-board-layout.ts`.
- 공개: `components/board/BoardLayoutFrame.vue`(쓸지 판단, 아니면 슬롯만) · `BoardLayoutGrid.vue`(테마 홈 템플릿으로 격자, 본문 슬롯). 목록·글 보기 페이지는 내용을 프레임으로 감쌌을 뿐 동작은 같다. 소모임 글은 레이아웃을 쓰지 않는다.
  - 격자로 그리는 때: 그 화면이 켜져 있고, 본문 말고 가젯이 있거나 본문이 전체 폭이 아닐 때(미리보기 편집 중은 늘). 아니면 지금 화면 그대로.
  - 한 열(모바일): 템플릿의 한 열 순서를 정한 뒤 본문을 맨 앞으로(`UtilHomeGrid.bodyFirst`) — 본문 위에 통째로 놓인 가젯만 위에 남는다.

## 필드·검증
- 테마마다 하나, 모든 게시판에 같이. 저장 모양 `{ screens{list,post}, layout{columns, items}, gadgets[{id,type,title,desktop,mobile,style,config}] }`.
- `BOARD_BODY`: 꼭 하나(없거나 둘이면 `400 fields.layout.items`), 최소 12칸 기준 폭 6 · 높이 20(24칸이면 폭 ×2), ⚙·꾸미기 없음(보낸 `style`·`config` 무시), 제목 고정. 다른 가젯은 홈과 같은 8종 · 같은 한도·검사.
- 대상 게시판 범위 `boardScope` 에 `BOARD`(지금 보는 게시판) — 게시판 레이아웃에서만, `FOLDER` 는 여기서 400. 홈·페이지에서 `BOARD` 를 보내면 400. 새로 넣은 게시판 가젯의 처음 값은 `BOARD`.
- 기본 배치(저장 안 한 테마): 본문만 전체 폭, `screens` 둘 다 켬 → 공개 화면이 지금과 같다.
- 추천 배치(화면 단추, 저장 아님): 24칸 본문 17 + 오른쪽 7(12칸 8+4) — 「이 게시판 인기글」 POST_LIST POPULAR·BOARD·RANK·10 · 공동구매 5 · 인기 소모임 3, 오른쪽 열은 PC 전용.

## 권한·정책
- 조회 `site.read`, 저장 `site.manage`. 공개 조회는 로그인 없이(`no-store`).
- 감사 `BOARD_LAYOUT_UPDATE`(테마 · 칸 수·가젯 수 전후 · 넣은·뺀 수 · 적용 화면 전후). 관리자 알림은 사이트 설정 변경(변형 `boardLayout`).

## API (마이그레이션 shared V110 — V109 는 주소 검색 2단계 브랜치가 먼저 잡음, 2026-10-30)
| 주소 | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /api/admin/board-layouts/{theme}` | `site.read` | → `{ theme, screens, layout, gadgets[{id,type,title,desktop,mobile,style,config}], updatedAt, updatedBy }` — 저장 안 했으면 기본 배치 + `updatedAt: null` · 모르는 테마 `404` |
| `GET /api/admin/board-layouts/{theme}/defaults` | `site.read` | → `{ screens, layout, gadgets }` |
| `PUT /api/admin/board-layouts/{theme}` (CSRF) | `site.manage` | 위 저장 모양 → 조회 모양 · 홈 저장과 같은 400 + `screens.list|post`(불리언) |
| `GET /api/boards/{slug}/layout` (공개, `no-store`) · `?theme=`(미리보기만) | — | → `{ theme, screens, layout, gadgets[홈 가젯 응답 모양] }` — 본문 가젯 `data: null`, `BOARD` 범위는 이 slug 로 채움. 없는/꺼진 게시판은 게시판 조회와 같은 오류 |

## 화면 연결 (2026-10-30)
- 관리자·공개 `useBoardLayoutApi` 를 실제 API 로 바꾸고 목업(`?mock=board-layout*`)·「서버 미연결」 안내를 없앴다.
- 본문은 **종류가 `BOARD_BODY` 인 가젯**으로 찾는다(`UtilBoardLayout.bodyOf`) — ID 는 서버가 정한다(기본 배치 `seed-1`). 본문이 없는 배치에 화면이 넣는 본문은 `new-body`(서버가 새 본문으로 받는다).
- 저장 400 은 홈과 같이 저장 줄에(칸 문구를 덧붙임), `gadgets[i].*` 는 그 가젯 ⚙ 창의 탭 점·칸 아래에도(`UtilHomeLayoutEditor.saveErrors`). ⚙ 창은 관리자 응답의 `style`·`config` 만 쓴다(실제 화면이 공개 값을 알려 주던 우회는 없앴다).

## 미확정 사항
- 게시판별 덮어쓰기(특정 게시판만 다른 배치)는 없다.
- 소모임 게시판 글에 적용할지.
- 레이아웃이 켜진 화면은 응답이 오는 순간 격자로 바뀌며 본문이 한 번 다시 그려진다.
