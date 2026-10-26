# 게시글 관리 계약 (관리자)

운영자가 모든 게시판의 글을 찾아 숨기기·숨김 해제·삭제·복구·이동·공지 지정·고정을 하고, 조치 기록과 글의 찬반 토론을 한 화면에서 본다. community `docs/api-content.md` 게시글 관리 절·`admin/app/pages/posts`를 로보얌 글 상태 모델·권한·UI(admin-web PrimeVue)에 맞춰 옮긴 것이다.

## 결정 (2026-10-24, 사용자)
- **운영자가 숨긴 글의 공개 표시 = 댓글과 같은 게시판 옵션** `staffHiddenNotice`(V77): 켜면 목록에 「운영자가 숨긴 글이에요」 자리, 끄면 「신고로 숨겨진 글이에요」(지금 신고 숨김과 같은 모양). 글을 직접 열면 지금처럼 `403 POST_HIDDEN`.
- **운영자 삭제 = 작성자 알림 + 포인트 회수**: 새 회원 알림 `POST_REMOVED`(제목·사유, 끌 수 없음). 그 글로 받은 포인트는 본인 삭제(`PostWriteService.revokeContent`)와 같은 규칙으로 회수. **복구하면 회수한 만큼 다시 준다**(원장 사유 `POST_RESTORE`).
- **복구 권한 = 새 민감 권한 `post.restore`**. 일괄 삭제는 이미 있는 민감 권한 `post.bulk-delete`.

## community 와 다른 점
- 뺌: 소모임(비공개 소모임 탭·`restricted`·`POST_GROUP_MANAGED` — 로보얌에 소모임 기능이 아직 없다, 생기면 다시 붙인다), 출하(`UNSHIPPED`·`RESHIPPED`), 예약 발행, `TAKEN_DOWN`, 글 유형·설문(`kindInfo`·`poll`), 지킴이 복구 예외, 담당 게시판 범위(`BOARD_SCOPE_FORBIDDEN`).
- 권한: 메뉴 키 `POSTS` 대신 `post.read`(조회·조치 기록·토론 보기) · `post.manage`(숨김·해제·삭제·이동·공지·고정·일괄 숨김/해제, 토론 열기·의견 관리) · `post.restore`(복구) · `post.bulk-delete`(일괄 삭제). 네 개 모두 `featureReady true`.
- 고정 한도: community 의 2개 대신 게시판 정책 `pinnedMax`(0이면 고정 불가 → `409 PIN_LIMIT`).
- 상태: 로보얌 글 칸(`hiddenAt/hiddenReason/hiddenUntil` · `deleteScheduledAt` · `deletedAt` · `purgedAt` · `adult` · 비회원 글) 기준. 원문을 파기한(`purgedAt`) 글은 복구 불가(`409 POST_PURGED`).

## DB (shared `V80__post_moderation.sql` — V81 = 게시글 투표, V82 = 신고 관리, 월요일 커머스 shared 는 **V83부터**)
- `posts.hidden_reason` CHECK 에 `STAFF` 추가(파티션 부모에서 DROP 뒤 다시).
- `post_moderation_events(id, post_id, type, actor_kind(OPERATOR|SYSTEM), actor_id, reason varchar(200), detail jsonb, created_at)` — type: `HIDDEN · UNHIDDEN · DELETED · RESTORED · MOVED · NOTICE · PINNED · UNPINNED · REPORT_HIDDEN`(신고 자동 숨김도 여기에 남긴다 — `ReportService.autoHide`). 색인 `(post_id, id desc)`.
- 회원 알림 type CHECK 에 `POST_REMOVED`(V78 목록과 합집합 — 커머스 병합 때 다시 합집합).

## API `/api/admin/posts` (응답 `ApiResponse`, ID 문자열, 쓰기 CSRF, `Cache-Control: no-store`)
- `GET ?searchField=&q=&createdFrom=&createdTo=&board=&prefix=&state=&sort=&page=&size=` (`post.read`) → `{ total, page, size, items: [AdminPostItem] }`
  - `searchField`: `TITLE`(기본)·`AUTHOR`·`ID`(대소문자 무시, 그 밖 `400 fields.searchField`) — `q` 는 그 칸만 찾는다. TITLE = 제목 일부, AUTHOR = 작성자 닉네임(회원)·비회원 이름 일부(둘 다 2자 이상·대소문자 무시·50자에서 자름, 한 글자는 무시), ID = 글 번호 정확히 일치(앞의 `#` 허용, 숫자가 아니면 `400 fields.q`). **본문 검색 없음**(성능), 「숫자면 글 번호」 자동 판별 없음. `createdFrom`·`createdTo`: 작성 시각 UTC ISO 순간(양 끝 포함, 회원 관리 `joinedFrom/joinedTo` 와 같은 규칙 — 화면이 사이트 시간대 하루 시작·끝을 바꿔 보낸다), 형식 오류 `400 fields.createdFrom|createdTo`, 시작 > 끝 `400 fields.createdTo`. `board` = 게시판 ID, `prefix` = 말머리 ID(게시판을 고른 뒤). `state`: `ALL`(기본)·`PUBLIC`·`HIDDEN`·`DELETED`. `sort`: `newest`(기본)·`oldest`. `size` 기본 20, 최대 50. 상태별 건수는 세지 않는다(2026-10-24 사용자 — 상태는 라디오로 고르고 숫자를 보이지 않는다).
  - `AdminPostItem { id, board: {id, name, nameEn, slug}, prefix: {id, name}|null, title, author: {kind: MEMBER|GUEST, id|null, name, withdrawn}, state: PUBLIC|HIDDEN|DELETED, hiddenReason|null(REPORTED|ILLEGAL|RIGHTS|STAFF), hiddenAt|null, hiddenUntil|null, deletedAt|null, deleteScheduledAt|null, purged, pinned, notice(말머리가 공통 NOTICE), adult, debate(토론 열림), likeCount, commentCount, viewCount, openReports, createdAt }`
- `GET /board-options` (`post.read`) → `{ boards: [{id, slug, name, nameEn, kind, pinnedMax, prefixes: [{id, name, staffOnly, common}]}] }`(켜진 게시판·말머리 — `kind` 는 게시판 종류, 글 옮기기 창은 `GROUP` 을 뺀다)
- `GET /{id}` (`post.read`) → `{ post: AdminPostItem, content: {format: TEXT|HTML, body|null(파기됨이면 null), editedAt|null}, events: [{id, type, actor: {kind, id|null, name|null}, reason|null, detail|null, at}] }` — 지운 글 본문도 보인다(파기 전까지). 없는 글 `404 POST_NOT_FOUND`.
- 조치 (`post.manage`, 사유 `reason` 1~200자 필수 → `400 VALIDATION fields.reason`, 상태가 맞지 않으면 `409 POST_STATE_INVALID`, 성공 204, 감사 `POST_HIDE|UNHIDE|DELETE|RESTORE|MOVE|NOTICE|PIN|UNPIN`, 조치 기록 한 줄):
  - `POST /{id}/hide` `{reason, notifyAuthor}` — 공개 글만. `hidden_reason STAFF`. `notifyAuthor` 면 기존 `POST_HIDDEN` 알림(사유 포함). 고정 해제.
  - `POST /{id}/unhide` `{reason}` — 숨긴 글(까닭 무엇이든 — 신고 숨김도 운영자가 풀 수 있다).
  - `POST /{id}/delete` `{reason}` — 지우지 않은 글. 알림 `POST_REMOVED` + 포인트 회수. 고정 해제. 작성자 본인이 운영자여도 그대로(운영자 계정과 회원 계정은 다르다).
  - `POST /{id}/restore` `{reason}` (`post.restore`) — 지운 글, 파기 전(`409 POST_PURGED`). 숨김 상태는 그대로 둔다. 회수한 포인트를 되돌린다.
  - `POST /{id}/move` `{boardId, prefixId|null, reason}` — 같은 자리 `400 fields.prefixId`, 그 게시판의 켜진 말머리가 아니면 `400 fields.prefixId`, 꺼진 게시판·소모임 게시판(`kind=GROUP`, 2026-10-26) `400 fields.boardId`. 고정 해제.
  - `POST /{id}/notice` `{reason}` — 공개 글을 그 게시판의 공통 「공지」 말머리로 바꾸고 고정. 한도 넘으면 `409 PIN_LIMIT`.
  - `POST /{id}/pin` · `/unpin` `{reason}` — 공개 글 고정·해제(한도 `PIN_LIMIT`).
- `POST /bulk` `{action: HIDE|UNHIDE|DELETE, ids(1~50), reason, notifyAuthor}` (`post.manage`, DELETE 는 `post.bulk-delete` 도) → 200 `{ done: [id], failed: [{id, code}] }` — 글마다 따로(한 글 실패가 다른 글을 막지 않음). 감사 `POST_BULK` 한 줄 + 글마다 조치 기록.
- 글의 토론: 이미 있는 `/api/admin/debates/{postId}`·`/opinions…`·`/pin-requests…`(post-debate-contract) 를 그대로 쓴다.

## 공개 쪽 영향 (server/api)
- 운영자 숨김(`STAFF`) 글: 목록의 `hiddenReason` 이 게시판 `staffHiddenNotice` 켬이면 `STAFF`, 끔이면 `REPORTED` 로 나간다. user-web 목록 자리 문구에 `STAFF` 「운영자가 숨긴 글이에요」.

## 화면 admin-web `/posts` 「게시글 관리」 (메뉴 콘텐츠 묶음 맨 앞, `pi pi-file-edit`, `post.read`)
- 조회 폼(회원 관리와 같은 구조): 검색 대상(제목·작성자·글번호) + 검색어 · 게시판 · 말머리(게시판 아래 줄) · 작성일 기간(빠른 선택) · 상태 라디오 전체/공개/숨김/삭제(건수 없음) · 검색·초기화. 정렬은 표의 「작성일」 머리, 쪽 크기 고르개는 쪽 넘김 옆. 모두 주소 쿼리(`?field=&q=&board=&prefix=&from=&to=&state=&sort=&page=&size=&post=`).
- 줄(DataTable): 체크(공개·숨김 글, `post.manage`) · 번호(복사) · 게시판 · 말머리·고정/공지/19금/토론 칩 · 제목 · 작성자(비회원·탈퇴 표시) · 상태 알약(숨김 까닭·시각, 삭제 예정·파기 표시) · 신고 대기 · 추천/댓글/조회 · 작성일 · ⋯ 조치 메뉴. 줄·제목을 누르면 오른쪽 Drawer(`?post=`, 이전/다음 글).
- Drawer: 글 요약 · 본문(지운 글은 「지운 글」 띠, 파기면 「원문 파기됨」) · **찬반 토론 구획**(토론 상태 요약 + [토론 설정](`DebateSettingsDialog`) + [의견 관리](`DebateOpinionsDrawer`)) · 조치 기록 · 바닥 조치 단추(상태가 허락하는 것만) · [공개 사이트에서 보기].
- 조치: 사유 대화창(숨기기는 「작성자에게 알림」 체크) · 이동 대화창(게시판 → 말머리, 「고정은 풀려요」) · 공지 지정(`PIN_LIMIT` 은 대화창 안 문구) · 복구는 `post.restore` 가 있을 때만 단추. 일괄 줄: 고른 글에 숨기기·숨김 해제·삭제(삭제는 `post.bulk-delete`) → 결과 대화창(글마다 처리됨/못 함 + 까닭).
- `post.read` 만 있으면 읽기 전용 안내 + 체크·조치 없음(조치 기록·토론 보기는 됨).
- 알림 링크 `/posts?post={postId}` 로 오면 그 글 Drawer 를 연다.

## 미확정
- 공지 목록 화면·예약 발행·담당 게시판 범위·게시판 매니저 처리 기록(`actor_kind MANAGER`)은 나중.
- 소모임이 생기면 비공개 소모임 탭·가림 규칙을 붙인다.

## 서버 구현 결과 (2026-10-24)
- **코드**: admin `com.community.post` — `AdminPostController`·`AdminPostQueryService`(읽기)·`AdminPostModerationService`(조치·일괄)·`AdminPostRepositoryQDSL`(목록·건수·고정 수·대기 신고·토론·댓글 게시판 옮기기). shared — `PostEntity` 조치 메서드(`staffHide`·`unhide`·`staffDelete`·`restore`·`moveTo`·`changePrefix`·`pin`·`unpin`), 조치 기록 `PostModerationEventEntity`·`PostModerationLog`(api `ReportService` 자동 숨김도 `REPORT_HIDDEN` 을 남긴다), `PointService.restoreContent`, 회원 알림 `postHiddenByStaff`·`postRemoved`.
- **DB** shared `V80__post_moderation.sql`: 계약 DB 절 + **포인트 원장 사유 CHECK 에 `POST_RESTORE`**(V51 CHECK 를 다시 만든다 — ⚠ 커머스 병합 때 합집합) + 회원 알림 type CHECK(V78 목록 + `POST_REMOVED`, ⚠ 합집합).
- **목록**: `page` 는 **0부터**(감사 로그·포인트·회원과 같다 — 화면 주소 1쪽 → API 0), 응답 `page` 도 0부터. `board`·`prefix` 의 깨진 ID → 빈 결과(건수 0), `prefix` 는 `board` 가 있을 때만 본다. 검색(2026-10-24 변경): `searchField` 한 칸만 — ID 는 자릿수와 무관(0·19자리 넘침은 빈 결과), 제목은 `lower(title)` trgm 색인, 작성자는 프로필 닉네임 하위 질의 + 비회원(`author_id IS NULL`) 이름. 작성 기간은 `created_at` 조건 + id 범위(Snowflake, ±1시간 여유)를 함께 걸어 월 조각 가지치기·id 색인을 탄다. 400 은 칸 오류를 모아 `VALIDATION` 한 번(문구 `post.admin.query.*`), 그 뒤 기간 뒤집힘.
- **AdminPostItem**: `hiddenReason`·`hiddenAt`·`hiddenUntil` 은 지금 숨김 중일 때만(지운 글이라도 숨긴 채면 값이 있다). `deletedAt` 은 예약 삭제 시각이 지나 아직 확정 전이면 예약 시각. `author.name` 은 회원이면 지금 닉네임(프로필이 없으면 빈 글자), `withdrawn` = 탈퇴·삭제·프로필 없음. `openReports` = 글 신고(댓글 신고 제외) 중 `PENDING`.
- **상세**: 계약 + **`publicUrl: string|null`**(사이트 설정 공개 주소 + `/posts/{id}`, 공개 주소가 없으면 null). `events[].detail` 은 JSON 객체 — 숨김 `notifyAuthor`, 해제 `hiddenReason`(풀기 전), 삭제 `revokedPoints`, 복구 `restoredPoints`, 이동 `fromBoardId·fromPrefixId·toBoardId·toPrefixId`, 공지 `fromPrefixId·toPrefixId`, 신고 숨김 `hiddenReason·hiddenUntil`, 고정이 풀렸으면 `unpinned true`, 일괄이면 `bulk true`. `actor.name` 은 운영자 지금 이름(SYSTEM·지운 운영자 null).
- **오류**: 사유 비었음·200자 초과 `400 fields.reason`(앞뒤 공백을 뗀 글자 수, 사유는 공백을 떼고 저장) — 사유 검사가 404 보다 먼저다. 이동: `boardId` 없음·꺼진/없는 게시판 `400 fields.boardId`, 켜진 말머리가 아님 `400 fields.prefixId`, 같은 자리 `400 fields.prefixId`(검사 차례: 사유 → 게시판 → 말머리 → 404 → 409 → 같은 자리). `prefixId: null`(말머리 없음)은 늘 받는다 — **로보얌에는 「말머리 필수」 게시판 정책이 없다**. 공지 지정에 켜진 공통 「공지」 말머리가 없으면 `400 fields.prefixId`. 복구는 지운 글이 아니면 409 `POST_STATE_INVALID` → 파기 409 `POST_PURGED` → 임시저장 409 `POST_STATE_INVALID`. `PIN_LIMIT` 메시지는 한도 숫자를 싣는다.
- **상태 규칙**: 숨김 해제·공지·고정은 지운 글에 409. 고정 해제는 고정만 돼 있으면 상태와 무관. 고정 한도는 그 게시판의 보이는(지우지 않은) 고정 글 수로 센다(숨긴 고정 글도 센다 — 운영자 숨김은 고정을 푸므로 신고 숨김만 해당).
- **일괄**: 검사(`fields.action` → `fields.ids`(같은 ID 는 한 번, 1~50) → `fields.reason`)를 모아 한 번에 400, 그 뒤 DELETE 는 `post.bulk-delete`(없으면 403 `PERMISSION_DENIED` + 거부 감사). `failed[].code` 는 `POST_NOT_FOUND`·`POST_STATE_INVALID`(예상 밖 오류는 `INTERNAL`). 감사 `POST_BULK` 대상 `POST`/`bulk`, 덧붙임 `action·reason·done·failed(ID)·notifyAuthor`.
- **알림·포인트**: 운영자 숨김 + 「작성자에게 알림」 → `POST_HIDDEN`(덧붙임 `by STAFF`·`reason`, 기본 문구 「운영자가 내 글을 숨겼어요」 / 본문 「제목 · 사유: …」, 문구 조건 `hideReason=STAFF`, 변수 `reason` 추가). 운영자 삭제 → `POST_REMOVED`(제목 「운영자가 내 글을 지웠어요: {제목}」, 본문 = 사유, 링크 없음, 덧붙임 `snapshot·reason·revokedPoints`) — 회원 이벤트 `POST_REMOVED`(SAFETY, 처리 알림 잠금). 따로 `POINTS_REVOKED` 알림은 내지 않는다. 복구 되돌림은 탈퇴·삭제 회원 제외(정지 회원은 받는다), 알림 없음.
- **공개 쪽**: `PostRowData` 에 `hiddenReason` 을 더해 게시판 목록·최신/인기 피드·프로필 글 목록이 `publicHiddenReason`(옵션 켬 + STAFF → `STAFF`, 그 밖 `REPORTED`)을 쓴다. 글 상세의 `403 POST_HIDDEN` 문구는 그대로(신고 숨김 문구).
- **알려진 한계**: 원장 유일 색인(사유·대상) 때문에 「삭제 → 복구 → 다시 삭제」의 두 번째 삭제는 포인트를 다시 회수하지 않는다(두 번째 복구도 되돌리지 않는다). 고정 한도 확인은 게시판 단위 잠금이 없어 동시에 두 글을 고정하면 한도를 1 넘을 수 있다.
