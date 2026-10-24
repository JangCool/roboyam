# 게시글 투표 계약

글에 투표(설문) 하나를 붙인다. 찬반 토론과는 별개 기능이고 한 글에 둘 다 있을 수 있다. community `docs/api-board.md` 「투표 (3-2)」를 로보얌에 맞춘 것(로보얌에는 임시저장·예약 발행이 없어 그 부분은 뺀다).

## 결정 (2026-10-24, 사용자)
- **게시판 옵션, 기본 끔**: 게시판 정책 `pollEnabled`(「글쓴이가 투표를 붙일 수 있음」, `board_defaults` false). 회원 글만(비회원 글 불가).
- **다시 투표 허용은 투표마다 글쓴이가 고른다**: `changeAllowed`(기본 끔). 끄면 투표 단추를 누를 때 「이 선택으로 투표할까요? 한 번 투표하면 바꿀 수 없어요」 확인을 한 번 더 받는다. 켜면 마감 전까지 다른 선택으로 다시 투표할 수 있다(취소는 없다 — 다시 고르기만).
- **결과 공개**: 투표한 사람은 바로, 마감되면 모두(투표 안 한 회원·비회원 포함). 그 전에는 결과·참여 수를 내보내지 않는다.

## 규칙 (community 그대로)
- `PollInput { question, options: string[], multiple, closesAt|null, changeAllowed }` — 질문 공백 정리 뒤 1~100자, 선택지 빈 칸 빼고 2~6개·각 1~50자·대소문자 무시 서로 다름, `closesAt` 은 시간대 포함 ISO, 지금+1분 ~ 365일 안(없으면 마감 없음). 오류는 글 오류와 함께 `400 VALIDATION fields."poll.question"|"poll.options"|"poll.closesAt"`. 게시판 옵션이 꺼졌거나 비회원이면 `400 fields.poll`.
- 익명: 누가 무엇을 골랐는지는 어떤 API 에도 내보내지 않는다(관리자 포함).
- **첫 표가 들어온 투표는 고치거나 뺄 수 없다**(`409 POLL_LOCKED`, 지금과 똑같은 내용을 다시 보내면 글만 고친다). 고치는 쪽 `FOR UPDATE`, 투표하는 쪽 `FOR SHARE`. 고치지 않은 지난 마감 시각은 다시 검사하지 않는다.
- 마감은 시각이 지나면 그 자리에서 마감(배치 없음).

## DB (shared `V81__post_polls.sql` — 커머스 shared 는 **V82부터**)
- `polls(id, post_id UNIQUE, question, multiple, change_allowed, closes_at, created_at, updated_at)` · `poll_options(id, poll_id → polls ON DELETE CASCADE, position, label)` · `poll_votes(poll_id, user_id, option_id, created_at, PK(poll_id, user_id, option_id))` — 득표·참여 수는 `poll_votes` 에서 센다(필요하면 `poll_options.vote_count`·`polls.voter_count` 사본, 서버 판단). 글은 파티션 표라 post_id 외래 키 없음.
- 게시판 정책 `boards.poll_enabled boolean`(NULL = 기본값) · `board_defaults.poll_enabled boolean NOT NULL DEFAULT false`.

## API (server/api — 응답 `ApiResponse`, ID 문자열, 쓰기 CSRF)
- `POST /api/posts`·`PUT /api/posts/{id}` 본문에 `poll?: PollInput|null` — 글쓰기: 있으면 붙인다. 고치기: 보내면 만들기/고치기, `null` 이면 빼기(첫 표 뒤면 409 POLL_LOCKED, 같은 내용은 그대로), **칸을 아예 빼면 그대로**.
- 글 상세 `poll: PollView|null`:
  ```
  PollView { id, question, multiple, changeAllowed, closesAt|null, closed,
    options: [{id, label}], voted, myOptionIds: [], canVote, voteBlockedReason: CLOSED|LOGIN_REQUIRED|EMAIL_NOT_VERIFIED|ACCOUNT_RESTRICTED|null,
    resultsVisible, voterCount|null, results: [{optionId, votes}]|null, editable }
  ```
  `canVote` = 로그인·이메일 인증·정지 아님·마감 전이고 (아직 투표 안 함 또는 `changeAllowed`). `editable` = 글쓴이이고 첫 표 전.
- `GET /api/posts/{id}/poll` → `PollView`(조회수 올리지 않음). `404 POLL_NOT_FOUND`.
- `POST /api/posts/{id}/poll/votes` `{ optionIds }` → `200 PollView`. 단일 = 정확히 1개, 복수 = 1개 이상, 중복·남의 선택지 `400 fields.optionIds`. 같은 선택 다시 = 200(멱등). 이미 투표했고 다른 선택: `changeAllowed` 면 바꾼다, 아니면 `409 ALREADY_VOTED`. 마감 `409 POLL_CLOSED`. 숨김·지운 글은 글 읽기 규칙대로. 글쓴이도 투표할 수 있다.
- `DELETE /api/posts/{id}/poll` → 204 — 글쓴이는 첫 표 전만(`409 POLL_LOCKED`), 다른 사람 `403`.
- 게시판 응답(글쓰기·고치기 화면이 읽는 것)에 `pollEnabled`.
- 관리자(server/admin): 게시글 관리 상세 `GET /api/admin/posts/{id}` 에 `poll: AdminPollView|null`(질문·선택지별 득표·참여 수·마감·복수·다시 투표 허용 — 누가 골랐는지는 없음), `DELETE /api/admin/posts/{id}/poll` `{reason}` (`post.manage`, 표가 있어도) → 204, 감사 `POST_POLL_DELETE`, 조치 기록 `POLL_DELETED`. 목록 줄에 `poll: boolean`.

## 화면
- user-web 글쓰기·고치기: 게시판 `pollEnabled` 이고 회원이면 「투표」 칸 — 「투표 붙이기」 켬/끔, 질문, 선택지(2~6, 추가·삭제·순서), 「복수 선택」, 「마감」(날짜·시각, 없으면 마감 없음 — display-time DateTimePicker), 「다시 투표 허용」(기본 끔, 끄면 「한 번 투표하면 바꿀 수 없어요」 안내). 고치기에서 `editable false`(표가 있음)면 읽기 전용 + 「표가 들어와 투표를 고칠 수 없어요」. 찬반 토론 칸 위나 아래에 같은 모양으로.
- user-web 글 상세: 본문 아래(찬반 토론 위) 투표 카드 — 질문·복수/마감 표시·선택지(라디오/체크) → [투표하기]. `changeAllowed false` 면 확인창. 투표 뒤·마감 뒤 결과 막대(퍼센트 = votes / voterCount, 복수는 합이 100 넘을 수 있음)·「N명 참여」·내 선택 표시. `changeAllowed` 면 [다시 투표]. 결과 비공개면 「투표하면 결과를 볼 수 있어요」/마감 안내. `voteBlockedReason` 안내. 글쓴이 `editable` 이면 [투표 지우기].
- 목록: 투표 있는 글 제목 옆에 투표 아이콘 칩(토론 칩처럼) — 목록 응답에 `hasPoll`.
- admin-web 게시판 정책 `pollEnabled` 「글쓴이가 투표를 붙일 수 있음」(게시판·기본값 화면, 「찬반 토론·댓글 추천」 구획). 게시글 관리 서랍에 「투표」 구획(결과 표 + [투표 지우기] 사유 대화창), 목록 칩.
- 시드 로더: `seed.json` 의 `poll` 을 이제 붙인다(「투표는 건너뜀」 없앰) — 그 게시판 `pollEnabled` 가 꺼져 있으면 로더가 건너뛰고 알린다.

## 미확정
- 투표 알림(마감·결과)은 없다.

## 서버 구현 결과 (2026-10-24)
- **DB** `V81__post_polls.sql`: 위 「DB」 그대로 + `poll_votes.user_id → users ON DELETE CASCADE`(post_stances 와 같음), `boards/board_defaults.poll_enabled`, `post_moderation_events` type CHECK 에 `POLL_DELETED`(V80 CHECK 다시 만듦 — 합칠 때 합집합 확인). 득표·참여 수 사본 칸은 두지 않았다(`poll_votes` 를 센다).
- **코드**: shared `com.community.poll`(`PollQueries` — JDBC, `FOR UPDATE`/`FOR SHARE`·같은 사람 투표는 어드바이저리 잠금, `PollNotFoundException`), api `com.community.poll`(`PollRules` 검사 · `PollWriter` 글쓰기·고치기 · `PollReader` 보기 · `PollService`/`PollController` 읽기·투표·지우기), admin `AdminPostQueryService`·`AdminPostModerationService#deletePoll`. 게시판 정책 `pollEnabled` 는 V79 `debateEnabled` 와 같은 길(칸 차례 바로 뒤, 기본값 PUT 에서 빠지면 400).
- **PUT 의 칸 구분**: record 로는 「빠짐」과 「null」이 갈리지 않아 `PostUpdateRequest.poll` 을 `JsonNode` 로 받는다(Jackson 3: 빠짐 = Java null, JSON null = `NullNode` — `Optional` 은 둘 다 empty 라 못 씀). 객체가 아니거나 모양이 틀리면 `400 fields.poll`.
- **계약에 더한 판정**
  - 고치기: 비회원 글에 값을 보내면 `400 fields.poll`. 게시판 `pollEnabled` 가 꺼진 뒤에는 **같은 내용·빼기만** 되고 새로 붙이거나 바꾸면 `400 fields.poll`(이미 붙은 투표는 그대로). 같은 내용 = 질문·선택지 차례·복수·다시 투표·마감(마이크로초까지)이 모두 같음.
  - 투표 자격: 비로그인(탈퇴 포함) 401 · 정지 `403 ACCOUNT_RESTRICTED` · 이메일 인증 전 `403 EMAIL_NOT_VERIFIED`(판정 차례는 `BoardAccessPolicy` 와 같다 — 정지 → 인증). 게시판 등급·잠금·역할 권한은 투표에 걸지 않는다.
  - `voteBlockedReason`: 마감이면 `CLOSED`, 아직 안 했거나 `changeAllowed` 면 회원 까닭, 이미 했고 다시 투표 불가면 null.
  - 공개 `DELETE /api/posts/{id}/poll`·읽기·투표는 글 읽기 규칙(404 · `403 BOARD_INACTIVE` · 숨긴 글 `403 POST_HIDDEN`)을 먼저 본다. 지우기 차례: 투표 없음 404 `POLL_NOT_FOUND` → 글쓴이 아님 `403 FORBIDDEN` → 표 있음 `409 POLL_LOCKED`.
  - `hasPoll` 은 게시판 글 목록 `PostRowView` 와 섞인 목록 `PostFeedRowView`(최신·인기·검색·회원 작성글) 둘 다(숨긴 줄은 false).
  - 관리자 `AdminPollView { id, question, multiple, changeAllowed, closesAt, closed, voterCount, options:[{id,label,votes}], createdAt }`. `DELETE /api/admin/posts/{id}/poll` 은 글 상태와 무관(지운 글도), 감사·조치 기록 덧붙임은 `pollId`·`question`·`voterCount` 만.
- **시드 로더**: 게시판 `pollEnabled` 가 켜졌을 때만 `poll` 을 싣고 `votes` 를 공개 API 로 넣는다. 꺼져 있으면 글만 쓰고 「못 한 것」에 알린다(로더는 게시판 정책을 바꾸지 않음 — 기본값이 꺼짐이라 지금 시드 `ai` 게시판은 건너뜀). `closesInHours` 앞으로 = 지금+그 시간(최소 2분), 0 이하 = 마감 없이 만들고 표를 넣은 뒤 SQL 로 마감 시각을 되돌린다.
