# 게시판 스텝 처리 계약 (**정책 2026-11-04 — 도메인 결정 + 사용자 「다음 단계 진행」 · 서버·화면 동시 구현 중**)

**의도**: 게시판 매니저·스텝이 맡은 게시판에서 글 숨기기·글 삭제·댓글 삭제를 하루 한도 안에서 한다. 매니저는 그 게시판의 처리를 처리자와 함께 보고 되돌리며, 운영자는 관리자 「스텝 처리 감독」에서 모든 게시판의 처리를 보고 되돌린다. 게시판 제재(`board-sanction-contract.md`)의 다음 단계이고 표·한도·화면 자리를 같이 쓴다.
**근거**: `docs/domain/operator-permissions.md` 「게시판 매니저 · 스텝」(처리 규칙·하루 한도·공개 표시·기록) · community `docs/api-board-keeper.md` 4-2·4-3·4-5·9·11절 · 운영자 조치 규칙 `post-admin-contract.md`(숨김·삭제·복구)·`report-admin-contract.md`(운영자 댓글 삭제).

## 정책 (도메인 문서 그대로 + 이 계약이 정한 것 ★)
1. **할 수 있는 일**: 글 숨기기 · 글 삭제 · 댓글 삭제(댓글 숨기기는 없다). 역할 권한 `board.post.hide`·`board.post.delete`·`board.comment.delete`(매니저·스텝 둘 다 기본 보유). 계정 정상·이메일 인증, 처리할 때마다 다시 판정.
2. **효과는 운영자 조치와 같다**: 숨기기 = `hidden_reason STAFF` + 고정 해제 + 작성자 알림 `POST_HIDDEN`(사유 포함) · 글 삭제 = 소프트 삭제 + 포인트 회수 + `POST_REMOVED` · 댓글 삭제 = 운영자 댓글 삭제와 같은 규칙(댓글 수·포인트 회수·답글 있으면 자리 남김) + `COMMENT_REMOVED`. ★ 알림은 운영자 조치와 **같은 종류·같은 문구**(공개 쪽에서 운영자인지 스텝인지 구별되지 않게 — 처리자 비공개). 메일 없음. `staffHiddenNotice` 표시도 운영자 숨김과 같다.
3. **사유**: 코드 필수 — `SPAM`·`ABUSE`·`INVESTMENT`·`OFF_TOPIC`(`OTHER` 는 제재에만 → `400 fields.reasonCode`). ★ 자유 입력 없음(코드만).
4. **처리 불가 대상**: 공지·고정 글 · 같은 게시판 매니저·스텝의 글·댓글 · 소모임 글 · 본인 것 → 모두 `409 BOARD_STAFF_TARGET_PROTECTED`(문구 「처리할 수 없는 글·댓글이에요」 — 누가 스텝인지 새지 않게). ★ 비회원 글·댓글은 처리할 수 있다(스팸 정리).
5. **하루 한도**: 처리자 1명·게시판 1개, 글(숨기기+삭제 합산) `staffPostDailyLimit` · 댓글 삭제 `staffCommentDailyLimit`(게시판 ?? 기본값), 한국 시간 0시, 되돌려도 돌려받지 않는다. 원자적 증가(`board_staff_usage.posts|comments`), 넘으면 `409 BOARD_STAFF_DAILY_LIMIT`(`kind` POST|COMMENT·`limit`·`used`·`resetsAt`). 실패한 시도는 세지 않는다(한 트랜잭션).
6. **되돌리기(복구)**: 숨김 해제 · 글 복구(회수한 포인트 되돌림) · 댓글 복구. 할 수 있는 사람 = 그 게시판 **매니저**(`board.staff-action.restore`, 자기 게시판의 모든 스텝·매니저 처리) · **운영자**(`keeper-action.restore`). 스텝은 되돌리지 못한다(본인 것도). ★ 사유 1~200자. 복구는 작성자에게 알리지 않는다(운영자 복구와 같다). 이미 되돌림 `409 BOARD_STAFF_ACTION_RESTORED`, 글 상태가 달라졌으면 `409 POST_STATE_INVALID`(파기 `POST_PURGED`).
7. **운영자 게시글 관리로 풀어도** 그 글의 되돌리지 않은 스텝 처리 기록에 되돌림(운영자)을 채운다(기록이 어긋나지 않게).
8. **처리자 비공개**: 처리자는 운영자 화면과 그 게시판 매니저의 처리 내역에만. 스텝 내역은 본인 것만(처리자 칸 없음). 운영자 글 조치 기록(`post_moderation_events`)에는 `actor_kind = BOARD_STAFF`(회원 id)로 남기고 관리자 화면에 「스텝 닉네임(매니저/스텝)」으로 보인다.
9. **운영자 권한**: `keeper-action.read`(스텝 처리 감독 조회) · `keeper-action.restore`(되돌리기, 민감) — featureReady → true.

## API (서버·화면이 이 모양을 그대로 쓴다)
공통: `ApiResponse`, ID 문자열, 시각 UTC ISO, 쓰기 CSRF. `BoardStaffQuota = {limit, used, remaining}`.
### 공개 (server/api)
- `GET /api/boards/{slug}` 의 `viewer.staffTools` 를 넓힌다: `{ manager, abilities: {postHide, postDelete, commentDelete, sanction, restore}, post: BoardStaffQuota, comment: BoardStaffQuota, sanction: BoardStaffQuota, resetsAt }` — 그 게시판 매니저·스텝이고 계정 정상·인증이며 네 처리 권한 중 하나라도 있으면 온다(없으면 null). `abilities.restore` = 매니저 + `board.staff-action.restore`.
- `POST /api/board-staff/posts/{postId}/hide` `{reasonCode}` → 200 `{ quota: BoardStaffQuota, resetsAt }`(글 한도)
- `POST /api/board-staff/posts/{postId}/delete` `{reasonCode}` → 200 같은 모양
- `POST /api/board-staff/comments/{commentId}/delete` `{postId, reasonCode}` → 200 같은 모양(댓글 한도). `postId` 필수(댓글 표 해시 파티션) — 그 글의 댓글이 아니면 404 `COMMENT_NOT_FOUND`.
- 오류(판정 순서): 401 → 404 `POST_NOT_FOUND`·`COMMENT_NOT_FOUND` → 403 `BOARD_STAFF_FORBIDDEN`(그 게시판 매니저·스텝 아님·권한 없음·계정 막힘) → 409 `BOARD_STAFF_TARGET_PROTECTED` → 409 `POST_STATE_INVALID`·`COMMENT_STATE_INVALID`(이미 숨김·삭제) → 409 `BOARD_STAFF_DAILY_LIMIT` → 실행.
- `GET /api/board-staff/boards/{boardId}/actions?kind=ALL|POST|COMMENT&page=`(1부터, 20개) → `{ manager, items: [BoardStaffActionRow], page, totalPages, totalCount }` — 매니저 = 그 게시판 전체, 스텝 = 본인 것만. 매니저·스텝이 아니면 404 `BOARD_STAFF_NOT_FOUND`.
  - `BoardStaffActionRow { id, kind: POST_HIDE|POST_DELETE|COMMENT_DELETE, reasonCode, target: {postId, commentId|null, title(글 제목 — 처리 때 스냅숏), excerpt|null(댓글 앞 40자 스냅숏)}, targetAuthor: {nickname}(회원 닉네임 또는 비회원 이름), actor: {nickname, role: MANAGER|STAFF}|null(매니저에게만), createdAt, restored: {at}|null, canRestore }`
- `POST /api/board-staff/actions/{id}/restore` `{reason}` → 200 `BoardStaffActionRow`(매니저만 — 아니면 403 `BOARD_STAFF_FORBIDDEN`).
- 게시판 제재 처리 기록(`kind SANCTION`)은 이 목록에 넣지 않는다(제재 내역 화면이 따로 있다).
### 관리자 (server/admin)
- `GET /api/admin/board-staff-actions?board=&kind=ALL|POST_HIDE|POST_DELETE|COMMENT_DELETE&restored=ALL|OPEN|RESTORED&q=(처리자 닉네임)&from=&to=&page=&size=`(`keeper-action.read`, size 기본 20·최대 50) → `{ total, page, size, boards: [{id, slug, name, nameEn}], items: [AdminBoardStaffAction] }`
  - `AdminBoardStaffAction { id, board: {id, slug, name, nameEn}, kind, reasonCode, actor: {id, nickname, role}, target: {postId, commentId|null, title, excerpt|null, author: {kind: MEMBER|GUEST, id|null, name}, state: PUBLIC|HIDDEN|DELETED}, createdAt, restored: {at, by: {kind: MANAGER|OPERATOR, name}, reason}|null }`
- `GET /api/admin/board-staff-actions/{id}` → `AdminBoardStaffAction`
- `POST /api/admin/board-staff-actions/{id}/restore` `{reason}`(`keeper-action.restore`) → 200 `AdminBoardStaffAction`. 감사 `BOARD_STAFF_ACTION_RESTORE`.
- `AdminScreenRegistry`: `staffActions`(`/staff-actions`, `keeper-action.read`, `pi pi-user-edit`, safety 묶음 `boardSanctions` 다음).
- 게시글 관리 상세의 조치 기록에 `actor.kind = BOARD_STAFF`(이름 = 닉네임 + 자리).

## DB (shared `V138__board_staff_actions.sql`)
- `board_staff_actions`: `kind` CHECK 에 `POST`·`COMMENT`, `action` 에 `HIDE`·`DELETE` · 되돌림 칸(`restored_at`·`restored_by_kind MANAGER|OPERATOR`·`restored_by_id`·`restore_reason`) · 스냅숏 칸(`target_title`·`target_excerpt`·`target_author_name`) · 「되돌리지 않은 처리」 부분 색인(글별). 한도 칸은 V137 의 `board_staff_usage.posts|comments`.
- `post_moderation_events.actor_kind` CHECK 에 `BOARD_STAFF`.

## 화면
### user-web
- 글 상세 ⋯: 매니저·스텝(`staffTools.abilities`)에게 「글 숨기기」·「글 삭제」(공지·고정 글·본인 글에는 없음) — 사유 시트(코드 4개 필수 + 확인) → 토스트 「글을 숨겼어요 · 오늘 남은 글 처리 n건」 → 게시판 목록으로.
- 댓글 ⋯: 「댓글 삭제」(본인·지운 댓글 제외) → 같은 시트 → 토스트 → 댓글 목록 새로.
- 게시판 머리 줄: 「게시판 운영 중 · 오늘 남은 처리 글 n · 댓글 n · 제재 n · [처리 내역]」.
- `/boards/[slug]/actions` 「처리 내역」(글·댓글) + 이미 있는 `/boards/[slug]/sanctions` 「제재 내역」 — 두 화면 위에 같은 탭 머리. 매니저는 [되돌리기](사유 1~200자).
### admin-web
- `/staff-actions` 「스텝 처리 감독」(메뉴 커뮤니티 안전, `keeper-action.read`): 거르기(게시판·종류·되돌림 여부·처리자 닉네임·기간) → 표(게시판·종류·대상(제목/댓글 앞부분 + 작성자)·사유·처리자(닉네임 + 매니저/스텝)·시각·되돌림) → 서랍 `?action=`(상세·글 링크 `/posts?post=`·[되돌리기](`keeper-action.restore`, 사유 1~200자)).
- 권한 카탈로그 `keeper-action.read`·`keeper-action.restore` featureReady true.

## 미확정
- 없음(이 계약의 ★ 는 도메인 문서가 비워 둔 칸을 운영자 조치 규칙에 맞춰 정한 것 — 사용자가 다르게 원하면 고친다).

## 서버 구현 결과 (2026-11-04, 미커밋 · 테스트 없음)
- **DB** shared `V138__board_staff_actions.sql`: `board_staff_actions` kind CHECK ∪ `POST`·`COMMENT`, action ∪ `HIDE`·`DELETE` + 짝 CHECK(댓글은 DELETE 만) · **`reason_code`**(SPAM·ABUSE·INVESTMENT·OFF_TOPIC — 글·댓글 줄 필수, 제재 줄 비움) · 스냅숏 `target_title`·`target_excerpt`(40자)·`target_author_name` · 되돌림 `restored_at`·`restored_by_kind`(MANAGER·OPERATOR)·`restored_by_id`(FK 없음)·`restore_reason`(1~200, 칸 함께 CHECK, 제재 줄은 되돌림 없음) · 색인: 열린 글 처리(글별, 정책 7) · 게시판별·처리자별·전체 내역(제재 줄 뺀 부분 색인). `post_moderation_events.actor_kind` **varchar(10)→(12)**(`BOARD_STAFF` 11자) + CHECK ∪ `BOARD_STAFF`.
- **shared**: `PostModerationActions`(숨기기·숨김 해제·삭제·복구 몸통 — 운영자·스텝 공용, 숨김 해제·복구 때 그 글의 열린 스텝 처리 기록을 채움 = 정책 7) · `BoardStaffActionService`(도구·글 숨기기/삭제·댓글 삭제·내역·매니저/운영자 되돌리기) · `BoardStaffActionStore`(JDBC) · `BoardStaffActionException`. 관리자 `AdminPostModerationService` 의 몸통은 이걸 부른다(동작 같음 — 신고 처리 숨김·삭제·무시 복구도 같은 길). `PostStateInvalidException`·`PostPurgedException` 은 admin → shared 로 옮김(같은 패키지).
  - 처리 판정: 404(글 — 임시저장·없음 / 댓글 — 그 글의 댓글 아님·`postId` 없음) → 403 → 400 `fields.reasonCode` → 글 행 잠금(→ 댓글 행) → 409 대상 보호(공지 말머리·고정 · 같은 게시판 매니저·스텝 · 소모임 글 · 본인, 문구 「처리할 수 없는 글·댓글이에요」) → 409 상태(글: 숨김은 공개 글만·삭제는 지우지 않은 글 / 댓글: 지운 댓글·지운 글의 댓글 `COMMENT_STATE_INVALID`) → 409 한도(`kind`·`limit`·`used`·`resetsAt`) → 조치 + 처리 기록 + 조치 기록(`BOARD_STAFF` + 덧붙임 `staffRole`·`boardStaffActionId`·`reasonCode`) + 알림, 한 트랜잭션.
  - 알림: 운영자와 같은 `POST_HIDDEN`(by STAFF)·`POST_REMOVED`·`COMMENT_REMOVED`, 사유 = 사유 코드의 한국어 이름(「스팸·광고」), **메일 없음**(발행기에 메일 끄는 변형 추가).
  - 되돌리기: 글 행(→ 댓글 행) 잠금 → 기록 조건부 UPDATE(409 `BOARD_STAFF_ACTION_RESTORED`) → 숨김 해제(지금 까닭이 STAFF 인 숨긴 글만, 아니면 409 `POST_STATE_INVALID`) · 글 복구(파기 `POST_PURGED`, 포인트 되돌림) · 댓글 복구(지운 댓글만 · 파기면 409 `COMMENT_STATE_INVALID`, 보이면 댓글 수 +1). 매니저 되돌리기의 조치 기록은 `BOARD_STAFF`(MANAGER), 운영자는 `OPERATOR` + 감사 `BOARD_STAFF_ACTION_RESTORE`(대상 POST).
- **api**: `viewer.staffTools` = `{manager, abilities{postHide,postDelete,commentDelete,sanction,restore}, post, comment, sanction, resetsAt}`(네 권한 중 하나라도 있을 때만 — 지난 단계의 「`board.sanction.issue` 없으면 null」을 바꿈) · `POST /api/board-staff/posts/{id}/hide|delete` · `POST /api/board-staff/comments/{id}/delete` → `{quota, resetsAt}` · `GET /api/board-staff/boards/{boardId}/actions?kind=&page=` · `POST /api/board-staff/actions/{id}/restore` → 줄(`canRestore` false).
- **admin**: `/api/admin/board-staff-actions`(목록 `page` 0부터 · `q` 처리자 닉네임 앞뒤 공백 제거·50자·최소 없음 · `from`·`to` 형식 400 · `/{id}` · `/{id}/restore`) · `keeper-action.read`·`.restore` featureReady true · `AdminScreenRegistry` `staffActions` · 게시글 관리 상세 조치 기록 행위자 `{kind:'BOARD_STAFF', id(회원), name(닉네임), role}`(`role` 칸 새로 — 그 밖 행위자는 null).
- **계약과 다른 점·정한 것**: 표에 `reason_code` 칸을 더했다(계약 DB 절에 없음). 댓글 줄 `target.state` 는 댓글의 지금 상태 — 신고로 숨겨진 댓글이면 `HIDDEN` 도 나온다. 댓글 복구는 회수한 포인트를 되돌리지 않는다(포인트 되돌림은 글만 있다). 없는 처리 기록은 404 `BOARD_STAFF_ACTION_NOT_FOUND`(제재 기록 id 도). 홈 목록 캐시는 서버에 없어 무효화할 것이 없다.
- **검증**: `compileJava`·`compileTestJava`(shared·api·admin) 통과. 로컬 DB(V135 까지)에 V136+V137+V138 을 `BEGIN … ROLLBACK`(CHECK 7가지 막힘 · 조건부 되돌림 · 한도 증가 1→2→0행 · 판정·한도·한 줄 질의 · EXPLAIN 부분 색인). 테스트·bootRun·API 호출 안 함.
