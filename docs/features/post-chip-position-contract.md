# 글 목록 말머리·상태 칩 위치 계약 (시안 확정 2026-11-08)

글 목록 줄에서 말머리(게시판 글 분류)·처리 상태 칩이 제목 앞에 줄줄이 붙어 제목 시작이 밀리던 것을 바꾼다. 칩 위치를 세 가지로 두고 **기본은 「제목 위」**(사용자 결정 — 시안 ②), 나머지는 옵션.

## 유형
| 값 | 이름 | 모양 |
|---|---|---|
| `ABOVE` (기본) | 제목 위 | **윗줄(줄 폭 전체)**: 왼쪽 칩(말머리·처리 상태… — 말머리가 없으면 회색 「일반」 칩), 오른쪽 끝 조회(눈 아이콘)·추천(엄지 아이콘) → 제목 → 아바타 · 닉네임 · 등급 · 날짜(이어서, 가운데점 구분 — 날짜는 표준형·유동형 규칙 그대로). 썸네일은 윗줄 아래, 제목 줄부터 오른쪽(사용자 확정 2026-11-08 — 윗줄은 늘 있음). 원래 조회·추천을 보이지 않던 목록은 그 칸 생략 |
| `BEFORE` | 제목 앞 | 지금 모양(칩 → 제목 한 줄 안, 날짜·조회·추천은 메타 줄). 데스크톱 게시판 목록(LIST)은 지금의 표형 그대로 |
| `BELOW` | 닉네임 아래 | 제목 → 아바타·닉네임·날짜(·조회·추천) → 칩 줄. 칩이 없으면 두 줄 |

- 칩 줄에 들어가는 것: 말머리(`PostPrefixTag`), 처리 상태(`PostStatusTag`), 소모임 공개 라벨(`GroupTag`), 19금 표시. 비밀글 자물쇠·고정 압정은 제목 앞 작은 아이콘으로 그대로(칩 아님). 투표 칩·이미지 표시·[댓글 수]·N 은 제목 뒤 그대로.
- 썸네일이 있으면 `ABOVE` 윗줄은 썸네일보다 위(줄 폭 전체), `BELOW` 칩 줄은 글자 칸(썸네일 옆) 안에서 쌓인다.
- **적용 범위(사용자 확정 2026-11-08 — 데스크톱도 같음)**: 게시판 목록(LIST — 모바일 줄·데스크톱), 카드형(CARD), 갤러리형(GALLERY) 카드 이름 줄, 피드 줄(최신글·인기글·검색·회원 작성글·소모임 글·팁·강좌·사용기 최신 글). 데스크톱 LIST 는 `ABOVE`·`BELOW` 면 표 대신 모바일과 같은 줄 모양(한 줄에 글 하나 — 여백·글자·썸네일만 조금 크게), `BEFORE` 면 지금의 표형. 갤러리는 원래 말머리·조회·추천을 보이지 않아 칩(상태·소모임 라벨·19)만 위치를 따르고 「일반」·숫자는 없다.
- 위치값 원천: 게시판 목록 = 게시판 정보 `chipPosition`(게시판 하나 응답), 섞인 목록 = `GET /api/site/info` 의 `boardDisplay.listChipPosition`. 고정 공지 한 줄 목록(`BoardPinnedList`)·핫딜 카드·갤러리(`HotdealPostGrid`)·홈 가젯 줄은 이번 범위 밖(칩은 제목 앞 그대로).
- 핫딜 목록(LIST, `HotdealRowItem` — 사용자 시안 확정 2026-11-08): 칩 위치 설정과 상관없이 한 모양 고정 — 윗줄 = 딜 배지(광고·제휴·종료/품절·마감 임박·HOT 등) + 말머리(없으면 「일반」) · 세로 구분선 · 상점 아이콘 + 몰 이름 · 오른쪽 끝 추천·조회 → 사진 + 제목 / 가격·할인율·배송 → 작성자 · 날짜 · 댓글.

## 설정 자리
- **사이트 기본**: 관리자 설정의 게시판(또는 일반) 설정 묶음에 「목록 칩 위치」 — `ABOVE`(기본)·`BEFORE`·`BELOW`. 게시판이 따로 고르지 않으면 이 값.
- **게시판별**: 관리자 게시판 관리의 목록 모양 칸에 「말머리·상태 칩 위치」 — 「사이트 기본 따르기」(null)·제목 위·제목 앞·닉네임 아래.

## 저장·API (서버)
- `boards.chip_position varchar(8)` null 허용(null = 사이트 기본), CHECK `ABOVE|BEFORE|BELOW`. 사이트 기본은 기존 사이트 설정 저장소에 칸 하나(기본 `ABOVE`). shared 마이그레이션 = develop 최신 다음(지금 V169).
- 공개: 게시판 정보 응답(게시판 목록·게시판 하나)에 `chipPosition`(게시판 값 또는 사이트 기본을 푼 **실제 값**), 섞인 목록(최신글·인기글·검색·홈 가젯·팁·강좌·사용기 등)이 쓸 사이트 기본은 공개 사이트 설정 응답에 `listChipPosition`.
- 관리자: 게시판 저장·읽기에 `chipPosition`(null 허용), 사이트 설정 저장·읽기에 `listChipPosition`. 잘못된 값은 400 칸 오류. 감사 기록은 기존 게시판·설정 저장 흐름 그대로.

## 서버 구현 결과 (2026-11-08)
- **마이그레이션**: shared `V169__board_chip_position.sql` — `board_defaults.list_chip_position varchar(8) NOT NULL DEFAULT 'ABOVE'` + CHECK, `boards.chip_position varchar(8)` NULL 허용 + CHECK(`ABOVE`·`BEFORE`·`BELOW`). 값 목록 원천 `BoardFields.CHIP_POSITIONS`.
- **사이트 기본의 자리(정리 결정)**: 사이트 설정 행(`site_settings`)이 아니라 **게시판 기본 설정 행(`board_defaults`)** — 관리자 화면 「게시판 > 기본 설정 > 일반」(`/boards/defaults/general`)이 기존 「게시판」 설정 묶음이고, 게시판 칸 null = 이 행의 값이라는 기존 상속 규칙(V42)과 같다. 공개 `boardDisplay`(날짜 표시 방식)도 이미 이 행에서 읽는다.
- **관리자 — 사이트 기본**: `GET`·`PUT /api/admin/boards/defaults` 의 `defaults.listChipPosition`(읽기 `board.read`, 저장 `board.settings`). 저장 때 칸이 없거나 null 이면 지금 값 그대로(예전 화면 호환), 소문자는 대문자로 맞춤, 모르는 값 400 `fields.listChipPosition`. 감사 `BOARD_DEFAULTS_UPDATE`(tab `general`, before/after). 응답 `overrides.listChipPosition` = 게시판 칸을 따로 고른 게시판 목록(「따르는 게시판 수」에서도 빠진다).
- **관리자 — 게시판별**: `GET /api/admin/boards/{id}` 의 `chipPosition`(null = 사이트 기본, 그 값은 같은 응답 `defaults.listChipPosition`), `PUT /api/admin/boards/{id}` 의 `chipPosition` — **null·칸 없음 = 바꾸지 않음, 빈 글자 `""` = 「사이트 기본 따르기」(null 저장)**(`writeTemplate` 과 같은 규칙), `ABOVE`·`BEFORE`·`BELOW`(소문자 허용), 모르는 값 400 `fields.chipPosition`. 목록 모양 칸이라 기본 정보 묶음 권한 `board.manage`. 감사 `BOARD_UPDATE` before/after(사이트 기본은 빈 글자로 남음), `diffCount` 에 세지 않는다.
- **공개 — 게시판**: `GET /api/boards`(목록 `boards[].chipPosition`), `GET /api/boards/{slug}`(`chipPosition`), 소모임 게시판 `GET /api/groups/{id}/board`(같은 `BoardDetailView`) — 모두 **푼 실제 값**(게시판 값 ?? 사이트 기본, 늘 셋 중 하나). `GET /api/boards/{slug}/posts` 응답에는 게시판 정보가 없어 칸을 더하지 않았다(화면은 게시판 하나 응답의 값을 쓴다).
- **공개 — 사이트 기본**: `GET /api/site/info` 의 **`boardDisplay.listChipPosition`**(섞인 목록용, 기존 `boardDisplay.dateDisplay` 옆).
- **캐시**: 관리자 앱은 다른 서버라 바로 비울 수 없고 기존 사본 주기대로 늦게 닿는다 — 게시판 사본(`BoardCatalog`) 최대 15초, 사이트 설정 사본(`/api/site/info`, ETag 포함) 최대 20초. 다른 게시판 설정과 같은 반영 지연.
- **화면 후속**: admin-web `util-board.ts` 의 기본 설정 칸 정의(`SPECS`)에 `listChipPosition`(CHOICE, 게시판별 덮어쓰기 아님)을 더해야 `BoardFields.DEFAULTS` 와 맞는다. 게시판 저장에서 「사이트 기본 따르기」는 `""` 로 보낸다.
- **목록 숫자 강조(추가, shared `V170__board_list_highlight.sql`)**: 글 목록 윗줄 추천·조회 숫자가 기준 이상이면 강조한다. 사이트 기본만 있고 게시판별 덮어쓰기는 없다. 같은 `board_defaults` 행에 둔다.
  - 관리자 `GET`·`PUT /api/admin/boards/defaults` 의 `defaults` 칸 세 개:
    - `listLikeHighlight`: 정수 0~100000, 기본 10, 0 = 강조 안 함
    - `listViewHighlight`: 정수 0~10000000, 기본 1000, 0 = 강조 안 함
    - `listHighlightColor`: `#rrggbb`, 기본 `#e5484d`(빨강). 앞뒤 공백을 떼고 소문자로 맞춰 저장한다.
  - 칸이 없거나 null 이면 그대로 둔다. 범위를 벗어나면 400 `fields.<칸>`(`board.field.range`), 색 모양이 틀리면 400 `fields.listHighlightColor`(`board.field.color`). 감사 `BOARD_DEFAULTS_UPDATE` before/after.
  - 공개 `GET /api/site/info` 의 `boardDisplay.likeHighlight`·`viewHighlight`·`highlightColor`. 사본은 최대 20초 늦게 반영된다.
  - admin-web `util-board.ts` `SPECS` 에도 같은 범위로 넣어야 한다. 색 칸은 새 칸 종류 `COLOR`(`BoardFields.HEX_COLOR`)다.
