# 게시글 찬반 토론 계약

찬반 토론(찬성·반대·중립 투표와 의견)은 **게시글 기능**이다(사용자 결정 2026-10-24). 일반 게시판 글에서 글쓴이가 열고, 하이라이트는 추천글을 모두에게 보여 주는 역할만 한다. 하이라이트로 보낸 글에 토론이 있으면 그대로 이어 가고, 없으면 하이라이트 만들기·고치기에서 새로 열 수 있다.
토론 안의 규칙(입장 = 투표, 의견/일반 댓글 분리, 소급, 고정·고정 요청, 비추천, 숨김 공개)은 `popular-highlight-contract.md` 「공개 사이트 토론」 이하 그대로다 — 이 문서는 **토론이 어디에 붙고 누가 여는지**만 정한다.

## 결정 (2026-10-24, 사용자)
- **누가 여나**: 글쓴이(회원) + 게시판 옵션. 게시판 정책 `debateEnabled`(「글쓴이가 찬반 토론을 열 수 있음」)가 켜진 게시판에서 글쓰기·고치기에 「찬반 토론 열기」. 운영자(관리자)와 그 게시판의 매니저는 어느 글이든 열고 닫고 설정을 바꾼다.
- **토론 설정은 글마다**: 「중립도 받기」(기본 켬) · 「입장 바꾸기 허용」(기본 켬). 바꾸기를 끄면 공개 사이트는 투표 단추를 누를 때 「찬성으로 정할까요? 한 번 정하면 바꿀 수 없어요」 확인을 한 번 더 받고, 정한 뒤에는 다른 단추를 막는다(서버 `409 STANCE_CHANGE_LOCKED`). 나중에 끄면 이미 고른 입장·의견은 그대로, 그 뒤 바꾸기만 막는다.
- **하이라이트와의 관계**: 하이라이트는 토론을 갖지 않는다. 하이라이트 폼의 토론 칸은 그 **글의 토론**을 열고·설정한다(이미 있으면 그대로 보이고 유지). 하이라이트를 지우거나 내려도 토론은 닫히지 않는다.
- **투표(설문)는 별개 기능** — 토론 옮기기 다음 작업으로 따로 만든다(이 문서 밖).

## 가정 (구현하며 정함 — 사용자 확인 전)
- 게시판 정책 `debateEnabled` 기본값 **끔**(board_defaults false) — 쓰는 게시판에서 켠다.
- 글쓴이 규칙: 회원 글만(비회원 글 불가). 글쓴이가 연 토론(`openedBy MEMBER`)만 글쓴이가 바꾼다 — 운영자·매니저가 연 토론은 글쓴이에게 읽기 전용. 글쓴이는 **투표가 하나라도 있으면 닫을 수 없다**(`409 DEBATE_HAS_VOTES`) — 설정(중립·바꾸기)은 바꿀 수 있다. 게시판 옵션을 나중에 꺼도 이미 연 토론은 그대로.
- 닫기 = 토론 행을 지운다. 이미 고른 입장·쓴 의견·고정·고정 요청은 지우지 않는다(다시 열면 그대로 돌아온다). 닫힌 글의 표시는 지금의 「토론 닫힘」과 같다(의견·투표가 있으면 읽기 전용 구획).

## DB (shared `V79__post_debates.sql` — 커밋 전 V79 `highlight_stance_change_allowed` 를 대신한다. V80 = 게시글 관리, V81 = 게시글 투표, 커머스는 V82부터)
- `post_debates(post_id bigint PK, neutral_enabled boolean NOT NULL DEFAULT true, stance_change_allowed boolean NOT NULL DEFAULT true, opened_by_kind varchar(10) NOT NULL CHECK MEMBER|OPERATOR, opened_by bigint, created_at, updated_at, updated_by_kind, updated_by)` — 행이 있으면 토론 열림. 글은 파티션 표라 외래 키 없음.
- 옮기기: `highlights.debate = true` 인 행 → `post_debates`(neutral_enabled 그대로, OPERATOR, created_by). 그다음 `highlights.debate`·`highlights.neutral_enabled` 칸을 지운다.
- 게시판 정책: `boards.debate_enabled boolean`(NULL = 기본값) · `board_defaults.debate_enabled boolean NOT NULL DEFAULT false`.

## 권한 · 메뉴 (admin) — 범위 변경(사용자 결정 2026-10-24)
- 관리자에 「게시글 관리」를 따로 만들고(다음 작업) 토론 관리는 그 안 **글 상세 서랍의 토론 구획**으로 들어간다. 그래서 `debates` 메뉴·토론 목록·글 찾기는 만들지 않는다.
- 권한은 게시글 관리와 함께 쓰는 `post.read`·`post.manage`(이미 카탈로그에 있던 POST 묶음 코드 — `featureReady` 는 게시글 관리 화면이 생길 때 켠다).
- 의견 관리·고정·숨김·고정 요청은 하이라이트가 아니라 **글 번호**로: `/api/admin/highlights/{id}/opinions…`·`/pin-requests…` → `/api/admin/debates/{postId}/opinions…`·`/pin-requests…`(응답 모양은 지금과 같다, 권한 `post.read`/`post.manage`). 운영자 알림 `OPINION_PIN_REQUEST_PENDING` 은 `post.manage` 보유자, 링크 → `/posts?post={postId}`(게시글 관리 화면 주소).

## API
### 관리자 `/api/admin/debates`
- ~~`GET ?q=…` 목록 · `GET /post-search`~~ — 만들지 않는다(범위 변경, 게시글 관리가 대신한다).
- `DebateItem { postId, post: AdminPostRef|null, neutralEnabled, stanceChangeAllowed, openedBy: 'MEMBER'|'OPERATOR', votes: {pro,con,neutral,total}, opinions(의견 원댓글 수), pendingPinRequests, highlightId|null, createdAt, updatedAt }`
- `GET /{postId}` (`post.read`) → `DebateItem`(닫혀 있으면 404 `DEBATE_NOT_FOUND`).
- `PUT /{postId}` `{ enabled, neutralEnabled, stanceChangeAllowed }` (`post.manage`) → 200 `DebateItem` 또는 닫으면 204. 열 때 null 이면 기본(true). 감사 `DEBATE_OPEN`·`DEBATE_UPDATE`·`DEBATE_CLOSE`.
- 의견: `GET /{postId}/opinions?stance=&page=&size=` · `POST|DELETE /{postId}/opinions/{commentId}/pin` · `POST|DELETE /{postId}/opinions/{commentId}/hide` · `GET /{postId}/pin-requests` · `POST /{postId}/pin-requests/{requestId}/approve|reject` — 지금 하이라이트 의견 관리와 같은 본문·응답(`HighlightOpinionListView` 의 `highlightId` 칸은 `highlightId|null` 로 두고 `debate`=열림 여부).
### 관리자 하이라이트 (모양 유지, 뜻만 바뀜)
- `HighlightItem.debate`·`neutralEnabled`·`stanceChangeAllowed` = 그 **글의 토론**(`post_debates` — 없으면 false·true·true). `POST`/`PUT` 본문의 같은 칸은 글의 토론을 연다·닫는다·바꾼다(null = 그대로, 만들 때 `debate` null = 그대로). `debate false` 로 고치면 글의 토론을 닫는다(운영자라 투표가 있어도 닫힌다). `HighlightItem` 에 `debateOpenedBy: 'MEMBER'|'OPERATOR'|null` 를 더한다.
- 하이라이트 삭제·노출 끔은 토론과 무관.
### 공개 (server/api)
- 토론이 열려 있다 = `post_debates` 행이 있다(하이라이트·노출과 무관).
- `debate` 요약(글 상세·댓글 목록)에 더함: `stanceChangeAllowed`(닫혀 있으면 true) · `openedBy: 'MEMBER'|'OPERATOR'|null` · `canManage: boolean`(지금 보는 사람이 아래 `PUT /debate` 로 바꿀 수 있나 — 글쓴이 규칙 또는 그 게시판 매니저) · `canClose: boolean`(닫기까지 되나). `neutralEnabled` 는 닫혀 있으면 true.
- 게시판 정보(글쓰기·고치기 화면이 읽는 공개 게시판 응답)에 `debateEnabled`(유효 정책).
- `POST /api/posts` 본문에 `debate?: { neutralEnabled, stanceChangeAllowed } | null` — 있으면 글과 함께 토론을 연다(회원이고 `debateEnabled` 일 때만, 아니면 `400 fields.debate`).
- `PUT /api/posts/{id}/debate` `{ enabled, neutralEnabled, stanceChangeAllowed }` → 200 `DebateSummary`(글 상세의 `debate` 와 같은 모양). 로그인 필수. 글쓴이(게시판 `debateEnabled` 켜짐 + 토론이 없거나 `openedBy MEMBER`) 또는 그 게시판 매니저만, 아니면 `403 DEBATE_MANAGE_DENIED`. 글쓴이가 투표 있는 토론을 닫으면 `409 DEBATE_HAS_VOTES`. 글쓴이가 열면 `openedBy MEMBER`, 매니저가 열면 `OPERATOR` 와 같은 취급(글쓴이 읽기 전용).
- 글 고치기 화면은 고친 글 저장 뒤 토론 칸이 바뀌었을 때만 `PUT /debate` 를 부른다(글 수정 본문에는 토론 칸을 두지 않는다).
- `PUT /api/posts/{id}/stance` 의 `409 STANCE_CHANGE_LOCKED`·중립 꺼짐 `400 fields.stance` 판정은 `post_debates` 값으로.

## 화면
- user-web 글쓰기·고치기: 게시판 `debateEnabled` 이고 회원일 때 「찬반 토론」 칸 — 「찬반 토론 열기」 켬/끔, 켜면 「중립도 받기」·「입장 바꾸기 허용」(기본 켬, 끄면 「한 번 고른 입장은 바꿀 수 없어요」 안내). 고치기에서 운영자가 연 토론이면 읽기 전용 안내, 투표가 있으면 끄기 막고 안내.
- user-web 글 상세: 토론 구획은 `debate.enabled`(또는 투표·의견이 있으면 읽기 전용)로 보인다. `canManage` 면 토론 구획 머리에 「토론 설정」 → 같은 세 칸 대화창 → `PUT /debate`. 바꾸기 잠금이면 투표 확인창·다른 단추 막기·`409 STANCE_CHANGE_LOCKED` 토스트 후 다시 읽기.
- admin-web 「게시글 관리」(`/posts`, 다음 작업): 글 상세 서랍의 토론 구획 — 설정(열기·중립·바꾸기 허용·닫기)과 의견 관리(하이라이트 화면의 의견 서랍을 글 번호 기준으로 옮겨 같이 쓴다). `?post=` 면 그 글 서랍을 연다. ~~`/content/debates` 토론 관리~~ 없음.
- admin-web 하이라이트 폼: 「찬반 토론」 칸에 그 글의 토론 상태(글쓴이가 연 토론이면 「글쓴이가 연 토론」 표시), 열기·중립·바꾸기 허용. 목록의 의견 서랍은 글 번호로.
- admin-web 게시판 정책: `debateEnabled` 「글쓴이가 찬반 토론을 열 수 있음」(게시판·기본값 화면).

## 미확정
- 게시판 매니저가 공개 사이트에서 토론을 여는 입구는 글 상세 「토론 설정」만(토론 없는 글에는 매니저용 「찬반 토론 열기」 단추를 글 상세 메뉴에 둔다).
- 투표(설문) 기능 — 별개 계약으로.

## 서버 구현 결과 (2026-10-24 — 서버만, 실제 DB 에서 아직 돌려 보지 않았다)
- **DB** `V79__post_debates.sql`(옛 V79 `highlight_stance_change_allowed` 파일은 지웠다): 표·옮기기·칸 지우기·게시판 정책 칸은 위 「DB」 그대로. 다른 점: `opened_by_kind`·`updated_by_kind` CHECK 가 `MEMBER|MANAGER|OPERATOR` — 게시판 매니저가 열면 `MANAGER`(opened_by = 회원 ID)로 남기고, **API 의 `openedBy` 에는 `OPERATOR` 로 보인다**(글쓴이 읽기 전용이라는 뜻이 같다). 옮긴 토론은 `stance_change_allowed true`. 목록 색인 `ix_post_debates_created`.
- **저장 코드 하나**: shared `PostDebateWriter.apply(postId, enabled, neutral?, change?, byKind, by, now)` — 공개 `PUT /debate`·`POST /api/posts`·관리자 `PUT /api/admin/debates/{postId}`·하이라이트 만들기·고치기가 같이 쓴다. 열기는 `INSERT … ON CONFLICT DO NOTHING`(동시 열기 한 행), 닫기는 행 삭제(입장·의견·고정·고정 요청은 그대로).
- **게시판 정책** `debateEnabled`: `BoardFields.POLICY_FIELDS` 차례 `… pinRequestReviewers, debateEnabled, managerMax …`, 기본 설정 칸(불리언 — 기본 설정 PUT 에 빠지면 `400 fields.debateEnabled`, 다른 불리언 정책 칸과 같은 검사), 감사 `BOARD_UPDATE`·`BOARD_DEFAULTS_UPDATE`·`diffCount` 는 기존 정책 칸 처리 그대로. admin-web `util-board.ts` 에 같은 칸이 필요하다.
- **공개 API**
  - 공개 게시판 응답: `BoardView`(`GET /api/boards`·`/api/boards/writable`)와 `BoardDetailView`(`GET /api/boards/{slug}`) 끝에 `debateEnabled`(유효 정책).
  - `POST /api/posts` 의 `debate`: 비회원이거나 게시판 `debateEnabled` 꺼짐이면 칸 검사에서 `400 VALIDATION fields.debate`(문구 `debate.open.notAllowed`). 열면 `openedBy MEMBER`, 글 저장과 같은 트랜잭션.
  - `PUT /api/posts/{id}/debate`: 차례 = 401 → 글 읽기 규칙(404 POST_NOT_FOUND · 403 BOARD_INACTIVE · 403 POST_HIDDEN) → `enabled` 필수 `400 fields.enabled`(문구 `debate.enabled.required` — 계약에 없던 칸 오류) → `403 DEBATE_MANAGE_DENIED` → 글쓴이(매니저 아님)가 투표 있는 열린 토론을 닫으면 `409 DEBATE_HAS_VOTES`. 이미 닫힌 토론 닫기·같은 값은 그대로 200. 매니저 판정 = 고정 요청 확인과 같은 `PinReviewers.isBoardManager`(`board_staff` MANAGER, 스텝 아님). 글쓴이이면서 매니저면 매니저로 친다(`MANAGER` 로 열림). 감사 없음(회원 행위).
  - `debate` 요약(글 상세·댓글 목록·`PUT /debate` 응답): 계약 칸 그대로 끝에 `openedBy`·`canManage`·`canClose`. `canClose` = 열려 있고(매니저 또는 글쓴이이고 투표 0). `pendingPinRequests` 는 토론이 열렸거나 투표가 있을 때만 센다(아니면 null — 예전 「하이라이트 행이 있을 때」 대신).
- **관리자 API** `/api/admin/debates/{postId}`(목록·post-search 없음)
  - `GET` → `DebateItem`(계약 모양 — `opinions` = 지우거나 숨기지 않은 의견 원댓글 수, `pendingPinRequests` = 지우거나 숨긴 의견의 요청 제외), 없으면 `404 DEBATE_NOT_FOUND`.
  - `PUT` `enabled` 필수(`400 fields.enabled`). 열 때 글이 없거나 지운 글이면 `404 POST_NOT_FOUND`(숨김·19금·꺼진 게시판 글은 연다). 닫기·이미 닫힘 → 204. 감사 `DEBATE_OPEN`(after)·`DEBATE_UPDATE`(바뀐 칸 before/after)·`DEBATE_CLOSE`(before), 대상 `POST`, 하이라이트에서 했으면 `via: HIGHLIGHT`.
  - 의견·고정 요청: 예전 하이라이트 의견 관리와 같은 본문·응답 — `DebateOpinionListView { highlightId|null, postId, debate, open(= debate, 호환), neutralEnabled(닫혀 있으면 true), pro, con, neutral }`, `DebatePinRequestListView { highlightId|null, postId, items }`. 글 행이 없거나 번호 형식이 틀리면 `404 POST_NOT_FOUND`(예전 `HIGHLIGHT_NOT_FOUND` 자리), 토론이 닫혀 있어도 다룬다. 감사 이름은 기록 호환으로 `HIGHLIGHT_OPINION_*`·`HIGHLIGHT_PIN_REQUEST_*` 그대로(문구만 「토론 의견 …」), detail 에서 `highlightId` 는 뺐다.
  - `/api/admin/highlights/{id}/opinions…`·`/pin-requests…` 는 지웠다.
- **하이라이트**: `HighlightItem` 의 `debate`·`neutralEnabled`·`stanceChangeAllowed` 는 글의 토론(없으면 false·true·true) + `debateOpenedBy`. 저장 본문: `debate true` = 열기/설정, 고칠 때 `false` = 닫기, `null` = 그대로(열려 있으면 보낸 두 칸만 바꾼다). **가정과 다른 점: 만들 때 `debate false` 도 그대로**(글쓴이가 연 토론을 폼이 모르고 닫지 않게). 하이라이트 감사 `HIGHLIGHT_CREATE|UPDATE` 의 칸에서 토론 칸은 빠졌다(토론은 `DEBATE_*` 로).
- **운영자 알림** `OPINION_PIN_REQUEST_PENDING`: `post.manage` 보유자, 링크 `/posts?post={postId}`, 같은 글끼리 5분 묶음(dedupe `post:{id}`).
- **오류 코드(새)**: `403 DEBATE_MANAGE_DENIED` · `409 DEBATE_HAS_VOTES` · `404 DEBATE_NOT_FOUND`(관리자) · 칸 `fields.enabled`·`fields.debate`. 문구 ko·en.
