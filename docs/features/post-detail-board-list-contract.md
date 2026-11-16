# 글 상세 아래 게시판 목록 — 계약

## 의도
글 상세에서 이전글·다음글은 그대로 두고, 댓글 카드 아래에 그 게시판 글 목록(쪽 번호 포함)을 보여 준다. 목록에서 들어왔으면 보던 쪽·거르기 그대로, 바로 들어왔으면 그 글이 들어 있는 쪽(사용자 2026-11-16, 목업 없이 바로 구현).

## 화면 경로
- user-web `/posts/{id}` — 댓글 카드 다음 카드 「목록」. 게시판 목록 화면(`/boards/{slug}`)과 같은 테마 목록 부품·`BoardPager`.
- 목록 → 상세 링크에 목록 문맥 쿼리를 싣는다: `/posts/{id}?page=3&prefix=…&status=…&tag=…&sort=oldest&q=…`(`UtilBoardQuery.build` 와 같은 키·기본값 생략). 핫딜은 `sort=latest|popular|ending&hideEnded&store`.
- 상세 아래 목록의 쪽 번호 → `/posts/{지금 글}?…&page=N`(글은 그대로, 목록만 바뀜). 목록의 글 → `/posts/{그 글}?{같은 문맥}`.
- [목록으로]·모바일 뒤로 → `/boards/{slug}?{같은 문맥}`.
- 지금 보는 글은 목록에서 강조한다.
- 그 아래 카드: 탭 [인기글](기본) · [최신글] — 사이트 전체 기준, `/popular`(실시간)·`/latest` 와 같은 목록 10개 + [더 보기]. 게시판 옵션과 상관없이 보이고 소모임 글에도 보인다(사용자 2026-11-16, 디시 구성 참고). 서버 변경 없음(`GET /api/home/popular`·`/api/home/latest`).
- admin-web 게시판 편집 → 「글 상세 아래 목록」 고르기.

## 필드·정책
- 게시판 옵션 `detailList`: `OFF` · `LIST`(기본 — 휴대폰도 모바일 게시판 목록과 같은 모양) · `COMPACT`(휴대폰은 촘촘한 한 줄 — 제목·댓글 수·날짜, PC 는 게시판 목록 그대로).
  - `boards.detail_list varchar(8) NOT NULL DEFAULT 'LIST'` + CHECK. 사이트 기본(board_defaults)은 두지 않는다. 소모임 게시판도 같은 칸.
- 목록 조회 `around={postId}`: `page` 가 없을 때만 쓴다. 같은 거르기·정렬로 그 글보다 앞에 오는 글 수 ÷ 한 쪽 글 수(`listSize`) + 1 이 그 쪽. 고정 공지로 따로 나오는 글(거르기 없음일 때)이면 1쪽. 목록에 없는 글(숨김 기준 다름·다른 게시판·거르기에 안 맞음)이면 1쪽.
- 목록은 화면이 그 근처까지 내려왔을 때 부른다(본문 속도에 영향 없음).

## API
- `GET /api/boards/{slug}/posts?…&around={postId}` — 응답은 기존 `PostListResponse`(`page` 가 계산된 쪽). 핫딜(`kind DEAL`) 목록도 같은 칸.
- `GET /api/groups/{id}/posts?page=&around={postId}` — 소모임 글 목록 쪽 방식에 같은 칸.
- 게시판 응답(`BoardView`/`BoardDetailView`, 소모임 `GET /api/groups/{id}/board`)에 `detailList`.
- admin `BoardSaveRequest.detailList`(null = 그대로), admin `BoardDetailView.detailList`.

## 2차(사용자 2026-11-16)
- 상세 아래 목록의 제목 영역이 좁다 → 게시판 목록 화면과 같은 폭(칸 비율)으로.
- [인기글]·[최신글] 탭: PC·모바일 **각각** 켜고 끄는 옵션 — 사이트 기본(`board_defaults`, 기본 PC 끔·모바일 켬) + 게시판별 덮어쓰기(NULL = 사이트 기본). 칸 이름 후보 `detailFeedTabsPc`·`detailFeedTabsMobile`.
- 모바일에서 두 탭은 가로를 5:5 로 나눠 채운다.
- 탭 목록에서 19금 글은 성인이어도 빠진다(`adult-board-contract.md`).

## 미확정
- 사이트 기본값(board_defaults) 칸 — 필요해지면 추가.
