# 익명 게시판 계약

**의도**: 닉네임 없이 털어놓는 게시판. 공개 화면에는 글·댓글 작성자가 「익명」으로만 보이고, 운영자 화면에도 작성자는 **봉인**된다. 제재는 신원을 몰라도 「이 글 작성자」로 걸 수 있고, 신원은 별도 민감 권한 + 사유 + 감사 기록으로만 연다(봉인 열람 — 사용자 결정 2026-11-10, 「완전 익명」안은 버림).

화면 확정: 2026-11-10(대화 시안 — 게시판 설정 · 목록 · 상세 · 글쓰기 안내 · 운영자 게시글 관리 · 작성자 열람 창).

## 화면 경로
- user-web: `/boards/anonymous`(목록), 글 상세, 글쓰기·댓글 입력 — 다른 게시판과 같은 화면, 작성자 표시만 다르다.
- admin-web: 게시판 › 기본 설정 「작성자 표시」, 게시글·댓글 관리·신고 처리(봉인 표시 · 「이 글 작성자 제재」 · 「작성자 열람」), 운영자 권한 목록.

## 정책
1. **게시판 설정** `author_mode`: `NICKNAME`(기본) · `ANONYMOUS`. 일반 게시판(`kind = GENERAL`)만 익명을 고를 수 있다(핫딜·소모임·운영 게시판 거절). 익명 게시판은 **회원만 쓴다**(비회원 글·댓글 허용 설정이 있어도 401 — 비회원은 이름을 적으므로).
2. **쓸 때 저장**: 글·댓글에 `anonymous` 표시를 쓸 때 저장한다(그때의 게시판 설정). 게시판을 나중에 `NICKNAME` 으로 돌려도 이미 쓴 글·댓글은 익명이다. 반대로 익명으로 바꾸기 전 글은 닉네임 그대로다. 작성자 회원 ID(`author_id`)는 그대로 저장한다(봉인).
3. **글 안 번호**: 한 글에서 처음 댓글을 다는 회원 순서로 익명1, 익명2 … (서버가 매긴다, 표 `post_anonymous_numbers` — 글·회원 하나에 번호 하나). 같은 회원은 그 글에서 늘 같은 번호(댓글을 지우고 다시 달아도). 글쓴이는 번호를 받지 않고 「익명」 + 글쓴이 표시. 다른 글에서는 번호가 새로 매겨진다.
4. **공개 화면에서 감추는 것**: 닉네임 · 회원 ID · 등급 · 아바타 · 꾸미기 · 자기소개 · 프로필 링크 · 운영자/사업자 표시 · AI 배지 · 작성자 메모. 목록·상세·댓글·홈/가젯·최신글·인기글·검색 결과·알림(「익명님이 …」)·멘션 알림 모두. 검색의 「작성자로 찾기」에 익명 글·댓글은 걸리지 않는다. 회원 프로필의 「작성한 글·댓글」에서 익명 글·댓글은 빠진다(본인이 보는 자기 목록도 — 본인 화면이 남에게 보일 수 있으므로). 게시판 매니저(스텝 처리 화면 포함)에게도 같다.
5. **본인 표시**: 내 글이면 수정·삭제가 지금처럼 된다. 내 댓글에는 「나」 표시. 댓글 입력 칸은 내가 이 글에서 받은 번호를 보여 준다(받기 전이면 「익명」, 글쓴이면 「익명(글쓴이)」).
6. **그대로인 것**: 금칙어 · 회원 정지 · 게시판 제재 · 작성 간격 · 하루 한도 · 같은 글 반복 · 포인트 · 승급 · 추천 · 신고. 차단(회원 차단)은 익명 작성자에게 걸 수 없다(차단 단추 없음). 이미 차단한 회원의 익명 글을 가리는지는 지금 차단 규칙대로 둔다.
7. **AI 회원**은 익명 게시판에 쓰지 않는다(AI 배지와 맞지 않음 — `AiMemberRules.blockedReason` 「항상 막힘」에 익명 게시판 추가, 관리자 계정의 쓸 곳에서도 고를 수 없다).
8. **운영자 화면 봉인**: 관리자 서버의 게시글·댓글 관리 목록/상세, 신고 처리, 회원 상세의 작성 글 목록, 감사 기록 목록에서 익명 글·댓글의 작성자는 `sealed` 로만 나간다(닉네임·회원 ID·IP 없음). 회원 상세 「작성한 글」에서도 빠진다(거기 나오면 봉인이 깨진다).
9. **이 글 작성자 제재**: 익명 글·댓글에서 회원 정지·게시판 제재를 「대상 = 이 글/댓글의 작성자」로 건다(서버가 작성자를 찾는다). 제재 결과·제재 목록에서도 그 회원은 닉네임 대신 「익명 글 작성자」로 보인다 — 제재 목록을 회원 단위로 보여 주는 화면이면 그 줄은 「익명 글 작성자(봉인)」. 열람 권한이 있는 운영자도 제재 화면에서는 봉인 그대로(열람 창으로만 연다).
10. **작성자 열람**: 새 민감 권한 `anonymous-author.reveal`(기본은 최고 관리자만 — 역할에 줄 수 있다, 게시판 매니저는 가질 수 없다). 열람 창에서 사유(필수 고르기: `LEGAL_REQUEST` 수사기관 요청 · `RIGHTS_REPORT` 명예훼손·권리침해 신고 · `ABUSE` 반복 도배·악용 · `OTHER` 기타) + 메모(필수, 500자)를 적어야 열린다. 결과는 회원 요약(닉네임 · 회원 ID · 상태, 회원 상세로 이동). 열람마다 감사 기록(대상 글/댓글 · 사유 · 메모 — 메모는 감사 상세에만) + 다른 운영자 알림(관리자 알림 이벤트). 탈퇴로 작성자 정보가 지워졌으면 「탈퇴한 회원」.
11. 데모·설치 프리셋: 「익명게시판」(`/boards/anonymous`, 말머리 고민·회사·연애, 커뮤니티 묶음) 을 프리셋에 넣는다. 소개 문구 「닉네임 없이 털어놓는 곳 · 운영자도 작성자를 볼 수 없어요」.

## 데이터 (shared V184)
- `boards.author_mode text NOT NULL DEFAULT 'NICKNAME' CHECK (author_mode IN ('NICKNAME','ANONYMOUS'))`
- `posts.anonymous boolean NOT NULL DEFAULT false`, `comments.anonymous boolean NOT NULL DEFAULT false`
- `post_anonymous_numbers(post_id bigint, user_id bigint, number int, created_at)` PK(post_id, user_id), UNIQUE(post_id, number) — 번호는 글 행 잠금(또는 이 표의 최대값 + 1, 충돌 시 재시도) 아래에서 매긴다.

## API 계약 (화면이 기대하는 모양 — 서버·화면 같이 지킨다)
- 공개 게시판 정보(`EffectiveBoard` → 게시판 상세·목록 응답의 board): `authorMode: 'NICKNAME' | 'ANONYMOUS'`.
- 작성자 칸(`PostAuthorView`): 익명이면 `kind: 'ANONYMOUS'`, `memberId/name/grade/gradeLabel/role/ipMasked/memo/decoration/avatarUrl/avatarStillUrl/bio/profile/ai` 는 모두 null, `business/operator` false, 새 칸 `anonymous: { number: number | null, op: boolean }`(글 작성자면 `number: null, op: true`; 댓글 작성자가 글쓴이면 `op: true`; 그 밖은 번호). 익명이 아니면 `anonymous: null`.
- 댓글 목록 응답(`CommentListResponse`): 새 칸 `viewerAnonymous: { number: number | null, op: boolean } | null` — 익명 글이고 로그인한 회원이면 이 글에서 그 회원의 표시(번호 받기 전이면 `number: null, op: false`), 아니면 null.
- 글쓰기·댓글 입력은 요청 모양이 바뀌지 않는다(익명 여부는 서버가 게시판 설정으로 정한다).
- 알림: 회원 알림의 행위자 이름은 익명 글·댓글이면 「익명」(번호 없이) — 알림 본문을 만드는 곳에서 서버가 정한다.
- 관리자 게시판 설정 요청·응답: `authorMode`.
- 관리자 게시글·댓글·신고 응답의 작성자: 익명이면 `author: { sealed: true, kind: null, id: null, name: null, withdrawn: false }` — 기존 작성자 모양에 `sealed: boolean` 을 더한다.
- `POST /api/admin/anonymous-authors/reveal` `{ postId?: string, commentId?: string, reason: 'LEGAL_REQUEST'|'RIGHTS_REPORT'|'ABUSE'|'OTHER', memo: string }` → `{ memberId, nickname, status, withdrawn: boolean }`. 권한 `anonymous-author.reveal`, 익명이 아닌 글이면 409.
- 제재: 기존 회원 제재(정지)·게시판 제재 **만들기 요청에 칸을 더한다** — 대상 회원 ID 대신 `anonymousPostId?: string` 또는 `anonymousCommentId?: string` 을 보내면 서버가 그 글/댓글의 작성자를 대상으로 한다(익명 글이 아니면 409, 둘 다/셋 다 보내면 400). 응답·목록·서랍·이의제기에서 그 제재 대상은 지금 모양 그대로 `user` 칸에 `sealed: true`(`id`·`nickname`·`grade`·`status` null — 세션 정리 결정 2026-11-10). 엔드포인트 경로는 그대로. 현황은 `GET /api/admin/sanctions/preview?anonymousPostId=…|anonymousCommentId=…`(같은 모양 + `user.sealed`), 봉인 대상 영구 정지는 `confirmNickname` 을 보지 않는다. 신고 처리의 「이 글 작성자 제재」는 `reportCaseId` 를 함께 보낸다.

## 미확정
- 익명 게시판 데모 원고(글·댓글)는 이번에 넣지 않는다 — 데모 팩을 다시 만들 때 함께.
- 차단한 회원의 익명 글을 가리는 것이 신원 추측에 쓰일 수 있는지(정책 6) — 지금 규칙 유지, 문제 되면 다시.

## 서버 구현 결과 (2026-11-10, 미커밋)
- **마이그레이션** shared `V184__anonymous_board.sql`(가번호): `boards.author_mode`(CHECK) · `posts.anonymous` · `comments.anonymous` · `post_anonymous_numbers`(PK(post_id,user_id) · UNIQUE(post_id,number) · user FK CASCADE) · `member_sanctions.sealed` · `board_sanctions.sealed`. admin 스트림은 쓰지 않았다(새 화면·메뉴 없음).
- **설정**: `BoardEntity.authorMode` → `EffectiveBoard.authorMode`/`anonymous()`(일반 게시판이 아니면 늘 NICKNAME — `BoardFields.anonymousAllowed`). 공개 `BoardView`·`BoardDetailView`, 관리자 `BoardDetailView`·`BoardSaveRequest.authorMode`(null = 그대로, 모르는 값·일반 게시판이 아닌데 ANONYMOUS → 400 `fields.authorMode`, 권한 묶음 `board.settings`, 감사 before/after).
- **쓰기**: 익명 게시판의 비회원 쓰기는 `BoardAccessPolicy` 가 `LOGIN_REQUIRED`(401 — `writable`·`viewer.writeBlockedReason` 도 같다). 글은 `PostWriteService.create` 에서 `markAnonymous`. 댓글은 **「글이 익명이거나 게시판이 지금 익명」**이면 익명(`AnonymousAuthors.anonymousComment` — 게시판을 되돌린 뒤에도 익명 글의 글쓴이가 닉네임 댓글로 드러나지 않게, 이때 비회원 댓글 401). 번호는 댓글 저장 트랜잭션 안 `AnonymousAuthors.assign` — `PostCounterQueries.lockWriter("anon:<글ID>")`(작성 간격과 같은 pg_advisory_xact_lock) 아래 최대값 + 1, 표 UNIQUE 가 마지막으로 막는다. 글쓴이 표시(op)는 **익명 글**의 작성자만(익명이 아닌 글의 글쓴이는 번호를 받는다).
- **공개 응답**: `PostAuthorView.anonymous(number, op)`(kind ANONYMOUS, 신원 칸 null — `withMemo`/`withAi`/`withProfile` 은 익명이면 아무것도 붙이지 않는다). 목록·상세·피드/가젯/최신/인기/검색/팁(`PostRowData.anonymous`)·댓글(베스트 포함)·의견 고정 요청 검토 · 의견 쓴 사람 목록(익명 댓글은 뺀다). 익명이면 댓글 `staffProtected`·글 `staffAuthor` 표시 false. `CommentListResponse.viewerAnonymous`. `ReplyToView` 에 `anonymous` 칸을 더했고 `name` 은 「익명3」(글쓴이·번호 전은 「익명」).
- **감춘 질의**: 회원 프로필의 작성 글·댓글·통계(본인 포함) · 통합 검색 작성자 · 게시판 안 검색 닉네임 · 관리자 게시글 작성자 검색 · 관리자 신고 작성자 검색 · 관리자 제재 목록 닉네임 검색(봉인 제재 제외).
- **알림**: `CommentNotice.anonymous` — 알림 행에 일으킨 회원을 남기지 않고 읽을 때(`MemberNotificationService`) 익명 댓글이면 `actor: { kind: 'ANONYMOUS', memberId: null, name: '익명' }`. 메일은 `MemberEmailRequested.anonymousActor` → 받는 사람 언어의 「익명」. 의견 고정 요청 알림도 같다. 멘션 알림은 코드에 아직 없다(예약 이벤트 MENTION).
- **관리자 봉인**: 게시글 목록·상세 `AdminPostAuthorView.sealed` · 글 요약 `AdminPostRefView.authorKind = SEALED`(대시보드 최근 글·하이라이트 포함, ID·이름 null) · 토론 의견 `authorKind SEALED` · 신고 목록·상세 `ReportAuthorView.sealed` + 작성자 카드 `sealed: true`(봉인 현황) + 신고 당시 작성자 이름 숨김(새 신고는 이름 스냅숏을 남기지 않음) · 스텝 처리 감독 `Author.kind SEALED` · user-web 스텝 처리 내역 `Author.sealed`(이름 스냅숏을 남기지 않음).
- **제재**: `POST /api/admin/sanctions` · `GET /api/admin/sanctions/preview` · 신고 처리 곁들임 제재(익명 사건이면 자동 봉인) · user-web `POST /api/board-staff/sanctions` · `GET /api/board-staff/sanctions/preview` 가 `anonymousPostId`/`anonymousCommentId` 를 받는다(shared `AnonymousAuthorLookup` — 없음 404 `ANONYMOUS_TARGET_NOT_FOUND`, 익명 아님 409 `NOT_ANONYMOUS`, 회원 ID 와 같이 보내면 400 `fields.userId`, 둘 다면 400 `fields.anonymousCommentId`). 봉인 제재는 `SanctionUserRefView.sealed` · user-web `BoardSanctionTargetView.sealed`, 봉인 현황은 이력 줄까지 봉인, 봉인 제재의 이의제기 상세는 회원 이력을 싣지 않는다. 감사 대상은 회원 대신 그 글·댓글(+ `sealed: true` — 해제·무효 포함).
- **열람**: `POST /api/admin/anonymous-authors/reveal`(admin `AnonymousAuthorController`·`AnonymousAuthorRevealService`, no-store) — 계약 모양 그대로, 메모 1~500자, 탈퇴면 `nickname null · withdrawn true`. 권한 `Permission.ANONYMOUS_AUTHOR_REVEAL`(`anonymous-author.reveal`, 제재 유형 SENSITIVE, `sanction.ban` 다음 — 어떤 역할에도 미리 넣지 않아 최고 관리자만, 회원 권한에는 없음). 감사 `ANONYMOUS_AUTHOR_REVEAL`(대상 POST/COMMENT · reason · memo, 회원 ID 없음 · 보관 분류 개인정보 열람 2년). 관리자 알림 `ANONYMOUS_AUTHOR_REVEALED`(SAFETY·WARNING, 열람 권한자 중 연 사람 제외, 링크 `/settings/audit?action=ANONYMOUS_AUTHOR_REVEAL`).
- **AI 회원**: `AiMemberRules.blockedReason` → `ANONYMOUS`(관리자 쓸 곳 고르기의 `blockedReason` 값이 하나 늘었다), 게시판을 되돌린 익명 글에도 AI 댓글을 쓰지 않는다.
- **프리셋**: `roboyam.structure.json` boards 에 `author_mode` 칸(기존 줄 모두 NICKNAME) + 「익명게시판」(id 112650000000000000, slug anonymous, 말머리 고민·회사·연애 + 공통 4개 꺼짐) + 커뮤니티 묶음 맨 뒤 메뉴 줄, `roboyam.json` boards 목록에도 추가(이미 설치된 곳의 「한 번 적용」 경로는 작성자 표시 칸이 없어 닉네임 게시판으로 만든다). 내보내기 스크립트는 칸을 DB 에서 읽으므로 고치지 않았다.
- **못 감춘 곳**: 관리자 회원 상세 활동 수·받은 신고 수(승급과 같은 집계라 익명 글 포함, 숫자만) · 차단 가림(정책 6 유지) · 회원 상세 현황의 경고 수·다음 단계(봉인 제재도 센 수 — `sealedCount` 로 「봉인 n건 포함」만 알린다)와 봉인 제재로 걸린 진행 중 정지(`active`, 아래 「회원 단위 화면」). (반복 검토·대상 보호·회원 단위 화면의 줄·링크는 아래 2026-11-11 에서 막음)

### 보강 (2026-11-11, 사용자 결정 — 미커밋)
- **반복 검토 봉인**: 세기는 그대로(봉인 제재도 센다). 줄을 이루는 제재(`reviewSanctions` 범위 — 열 때 7일 앞/마지막 닫힘 뒤 ~ 닫은 때·지금, 무효 포함) 가운데 하나라도 `sealed` 면 그 줄 전체 봉인 — `GET /api/admin/board-sanction-reviews`(목록 `items[]`)·`GET …/{id}`·`POST …/{id}/close` 응답의 `user` = `{ id: null, nickname: null, grade: null, status: null, sealed: true }`, 새 칸 `sealedSource: { postId, commentId } | null`(가장 최근 봉인 제재의 계기 — **둘 중 하나만** 채움: 댓글이면 `commentId` 만, 글이면 `postId` 만. 봉인 아닌 줄은 null). 봉인 검토 상세의 `sanctions`·`reviewSanctionIds`·`voidedSanctionIds` 는 봉인 제재만, `otherBoards` 는 빈 목록(수 `recentCount`·`recentBoardCount` 는 그대로). 닫기 감사(`BOARD_SANCTION_REVIEW_CLOSE`) 대상은 회원 대신 그 계기 댓글/글 + `sealed: true`(shared `BoardSanctionStore.latestSealedBetween`·`BoardSanctionService.sealedSource`).
- **중립 오류**: 봉인 대상의 대상 보호(본인 · 탈퇴 · 운영자 회원 계정 · 같은 게시판 매니저·스텝 · 대상 행 없음)는 409 `ANONYMOUS_TARGET_UNAVAILABLE`(「지금 이 작성자에게는 제재를 걸 수 없어요.」) — user-web `GET /api/board-staff/sanctions/preview`·`POST /api/board-staff/sanctions`(`anonymousPostId|anonymousCommentId`), 스텝 처리 `POST /api/board-staff/posts/{id}/hide|delete`·`/comments/{id}/delete` 의 익명 글·댓글. 공지·고정·소모임 보호는 신분과 무관해 그대로 `BOARD_STAFF_TARGET_PROTECTED`. 관리자 사이트 제재(`POST /api/admin/sanctions`·preview·신고 처리 곁들임 제재)에는 역할 보호가 없어 바꿀 것이 없고, preview `blockedReason`(`ALREADY_BANNED`·`SUSPENDED`)은 제재 이력에서 나오는 값이라(같은 내용이 `active`·`history` 에도 있고 화면이 다음 단계 안내에 쓴다) 그대로 둔다.
- **남은 걱정**: ~~관리자 회원 단위 화면이 봉인 제재를 그 회원 줄로 보여 준다~~(아래 「회원 단위 화면」에서 막음) · 봉인 현황(preview)의 이력 줄 중 봉인 아닌 제재의 번호로 제재 목록에서 회원을 찾을 수 있다.
- **회원 단위 화면 (2026-11-11, 사용자 결정)**: 셈(단계 · 경고 수 · 반복 검토 세기 · 쓰기 제한 판정 · 회원 찾기 `/targets` 의 `warningCount`)은 봉인 제재도 그대로 센다. 회원이 드러난 관리자 화면에서만 봉인 제재 줄·수를 뺀다(회원 거르기 없는 전체 목록은 지금처럼 `user.sealed` 줄로 남는다).
  - 회원 상세 `boardSanctions`(`GET /api/admin/members/{id}`): `boards[]`(게시판별 수·`activeUntil`) · `total` · `active` · `recent[]` 에서 봉인 제재를 빼고, `openReviews` 는 센 제재에 봉인이 하나라도 있는 열린 검토(봉인 검토)를 세지 않는다.
  - `GET /api/admin/sanctions?userId=` · `GET /api/admin/board-sanctions?userId=`: 봉인 제재 줄을 싣지 않는다(`total` 도). `counts` 는 거르기와 무관한 전체 수라 그대로.
  - `GET /api/admin/sanctions/preview?userId=`(회원 상세 제재 구획 · 신고 상세 작성자 카드 `sanction` 도 같은 함수): `history` 는 최근 30개 범위에서 봉인 줄을 뺀다. 새 최상위 칸 `sealedCount: number` = `warningCount` 에 들어간 봉인 경고 수(유효한 봉인 경고, `warningCount` 이하, 없으면 0) — 화면 「경고 n회(봉인 m건 포함)」. 진행 중 정지·영구 정지가 봉인 제재면 `active.sealed: true`(+ `active.reportCase` null, 끝나는 날·ID 그대로 — 「정지 풀기」 `POST /api/admin/sanctions/{id}/lift` 는 봉인 제재도 그대로 해제된다, 감사는 회원 대신 `sealed: true`). 봉인 대상 현황(`anonymousPostId|anonymousCommentId`)은 지금 그대로, `sealedCount` 0.
  - `SanctionLine` 에 새 칸 `sealed: boolean`(모든 응답 — `user.sealed` 와 같은 값, 일반 줄 false).
  - 회원 이력: 게시판 제재 상세(`GET /api/admin/board-sanctions/{id}`)의 `history` 와 이의제기 상세의 회원 이력(`history`·`boardHistory`)에서 봉인 줄을 뺀다 — 봉인 게시판 제재의 상세는 회원 이력을 싣지 않는다(빈 목록 — 전에는 그 회원의 다른 제재 줄이 닉네임째 나왔다).
  - 봉인 아닌 반복 검토 상세: `sanctions`(이 게시판 최근 제재)에서 봉인 줄을 빼고 `otherBoards` 의 수도 봉인 제재를 빼고 센다(`recentCount`·`recentBoardCount` 는 그대로).
  - 이의제기 목록은 회원 거르기가 없어 바꿀 것이 없다. 회원 목록의 상태 칩·회원 본인 쪽(user-web 내 제재·알림)은 그대로.
