# 운영자 회원 계정 계약 (화면 확정 2026-11-05 — 사용자 「미확정 추천대로해」)

**의도**: 관리자 앱 운영자(`operators`)마다 사이트(user-web)에서 쓰는 회원 계정(`users`) 하나를 1:1 로 잇는다. 이은 「운영자 회원 계정」은 사이트에서 ① 로보티콘 모든 팩 ② 닉네임 옆 「운영자」 표시 ③ 모든 게시판 관리(매니저처럼 — 운영자의 관리자 앱 권한 거울)를 가진다. 힘은 운영자 기준으로 판정·기록되고, 운영자를 못 쓰게 되면 함께 멈춘다.
**근거**: 사용자 결정 2026-11-05(로보티콘 상점 세션 — 게시판 매니저·스텝은 산 팩만, 대신 운영자에 회원 계정 연결, 범위 = 모든 팩 + 운영자 표시 + 게시판 관리). 도메인 `docs/domain/operator-permissions.md` 확정 규칙 1·2 개정. 이어지는 계약: `board-staff-action-contract.md`·`board-sanction-contract.md`(처리·제재), `member-withdrawal-contract.md`(탈퇴), `emoticon-contract.md`(로보티콘 — 다른 브랜치).

## 화면 경로
- admin-web `/settings/operators` → 행 → 서랍 탭 「사이트 계정」(`OperatorMemberAccountPanel`) · 운영자 표 이름 옆 딱지 · 회원 상세(`/members`) 기본 정보 맨 위 딱지 「운영자 회원 계정 · {운영자 이름}」.
- user-web: 작성자 이름 옆 「운영자」 뱃지(`PostAuthorLabel` — 등급 뱃지 대신, `StaffBadge role="ADMIN"` = 사이트 용어 `ADMIN_BADGE`) · 공개 프로필 머리 · 게시판 머리 운영 줄 「운영자 계정으로 관리 중 · 하루 한도 없음 · [처리 내역]」 · `/settings/account` 읽기 전용 카드 · `/settings/account/withdraw` 막힘 안내.

## 정책
1. **연결 1:1**: 운영자 하나 ↔ 회원 하나. 회원 표(`users`)에는 값을 두지 않고 운영자 쪽 표가 회원을 가리킨다.
2. **연결 대상 = 관리자가 고른 회원**(2026-11-24 개정 — 아래 「세션 정리 결정 2026-11-24」): 관리자가 회원을 검색해 고르고, 연결 자격(정책 3)만 맞으면 잇는다 — **운영자와 같은 이메일이 아니어도 된다**. 운영자 이메일과 같은 이메일(대소문자 무시)로 가입한 회원은 지금처럼 서버가 찾아 **추천 후보**(`candidate`)로 준다. 이은 뒤 회원이 이메일을 바꿔도 연결은 그대로. ~~(옛 규칙: 같은 이메일 회원만, 관리자가 아무 회원이나 고르지 못한다)~~
3. **연결 자격**(연결할 때 판정): 회원 상태 정상(정지·탈퇴 진행·탈퇴 아님) · 이메일 인증 · 사업자 계정 아님 · 다른 운영자에 이어져 있지 않음. 사유 `EMAIL_NOT_VERIFIED`·`MEMBER_RESTRICTED`·`BUSINESS_ACCOUNT`·`LINKED_TO_OTHER`(여럿 가능).
4. **연결하면 그 회원의 게시판 매니저·스텝 지정이 모두 끝난다**(`board_staff` 행 삭제 — 종료 사유 칸은 없으므로 연결 감사 `detail.releasedBoards` 에 끝난 게시판 ID 를 남긴다). 연결된 동안은 매니저·스텝 후보에서 자격 미달(`OPERATOR_ACCOUNT`).
5. **연결·해제 권한**: `operator.manage`. 대상이 최고 관리자(루트)면 루트 본인만(`OPERATOR_ROOT_PROTECTED`). 해제는 언제든 된다.
6. **힘이 살아 있는 조건 — 요청마다 판정**: 연결 있음 + 운영자 `canLogin(now)`(승인·안 잠김·만료 표시 없음·사용 중·기간 안) + 회원 상태 정상(사이트 정지 중이 아님·탈퇴 진행 아님). 하나라도 어긋나면 세 힘(뱃지 포함)이 바로 꺼진다(캐시 없음). 멈춘 까닭 `OPERATOR_UNAVAILABLE`·`MEMBER_SUSPENDED`.
7. **게시판 관리 = 관리자 앱 권한 거울**(루트는 전부, 역할이 없으면 게시판 관리 힘 없음 — 표시·팩은 그대로):
   | 사이트에서 | 필요한 관리자 권한 |
   |---|---|
   | 글 숨기기·글 삭제·댓글 삭제 | `post.manage` |
   | 작성자 제재(게시판 경고·쓰기 제한) | `sanction.manage` |
   | 처리 내역 보기(그 게시판 전체·처리자 포함) | `keeper-action.read` |
   | 처리 되돌리기 | `keeper-action.restore` |
   - **하루 한도 없음**(세지도 않는다). 모든 공개 게시판(소모임 게시판 제외)에서.
   - 처리 불가 대상: 공지·고정 글, 소모임 글, 본인 것, **다른 운영자 회원 계정의 글·댓글** → `409 BOARD_STAFF_TARGET_PROTECTED`. 게시판 매니저·스텝의 글은 처리할 수 있다(감독).
   - 제재 대상 보호: 다른 운영자 회원 계정·본인은 제재 불가(같은 409). 반복 제재 검토 건수에는 운영자 회원 계정의 제재도 합산한다.
   - 되돌리기: 운영자 회원 계정은 그 게시판 모든 처리(스텝·매니저·다른 운영자)를 되돌릴 수 있다(`keeper-action.restore`). **매니저는 운영자 회원 계정의 처리를 되돌리지 못한다**(`canRestore` false, 서버 403).
8. **`viewer.staff` 도 준다**(미확정 → 결정 2026-11-05): 모든 공개 게시판에서 운영진 전용 말머리(공지)·운영진 전용 글쓰기·쓰기 잠금 면제를 매니저·스텝과 같이 쓴다(권한과 무관 — 힘이 살아 있으면).
9. **보호**: 운영자 회원 계정의 글·댓글은 게시판 매니저·스텝(과 다른 운영자 회원 계정)에게 `staffProtected`·`sanctionProtected` = true. 차단(`BlockService`)에서 운영진처럼 보호한다.
10. **표시**: 힘이 살아 있으면 작성자 `author.operator = true`(글 목록·글 보기·댓글·공개 프로필). 숨긴 글·지운 댓글처럼 작성자를 비우는 자리는 false.
11. **기록**: 사이트에서 한 처리·제재·되돌리기는
    - `board_staff_actions`(처리자 `actor_role = OPERATOR`, `actor_id` = 회원, `actor_operator_id` = 운영자) · `board_sanctions`(`issued_role = OPERATOR`, `issued_operator_id`) · `post_moderation_events`(`actor_kind = OPERATOR`, `actor_id` = 운영자, 덧붙임 `viaUserId`·`boardStaffActionId`·`reasonCode`)
    - **감사 로그**: `actor_type OPERATOR`, `actor_id` = 운영자, 행위 `SITE_POST_HIDE`·`SITE_POST_DELETE`·`SITE_COMMENT_DELETE`·`SITE_BOARD_SANCTION`·`SITE_ACTION_RESTORE`, `detail` = `{ viaUserId, boardId, postId, commentId?, reasonCode }` — 「운영자 X 가 회원 계정 Y 로」.
    - 연결·해제도 감사 `OPERATOR_MEMBER_LINK`·`OPERATOR_MEMBER_UNLINK`(대상 OPERATOR, `detail {userId}`).
    - 공개 쪽 처리자 비공개, 작성자 알림은 운영자 조치와 같은 종류·문구(스텝 처리와 같다). 매니저 처리 내역의 처리자 칸은 「운영자」(`actor.role = OPERATOR`, 닉네임 = 그 회원 닉네임).
12. **탈퇴**: 연결된 동안 회원 본인 탈퇴 `409 OPERATOR_ACCOUNT_LINKED`(「운영자 계정에 연결된 동안은 탈퇴할 수 없어요. 관리자에게 연결 해제를 요청해 주세요.」). 관리자 강제 탈퇴도 같은 409 — 먼저 해제한다.
13. **사이트 제재**: 운영자가 운영자 회원 계정에도 줄 수 있다. 정지 중에는 힘이 멈춘다(6). 정지가 연결을 끊지는 않는다.
14. **2차 인증**(미확정 → 결정 2026-11-05): 사이트 회원 로그인에 2차 인증을 더하지 않는다. 알려진 위험 — 운영자 회원 계정의 비밀번호가 새면 그 운영자 권한 범위의 게시판 관리가 열린다. 완화: 힘은 관리자 앱 권한 범위를 넘지 않고, 모든 처리가 감사에 운영자 이름으로 남고, 해제·사용 중지로 바로 끊긴다. 강화(사이트 2차 인증 또는 「최근 관리자 앱 로그인」 조건)는 뒷일.
15. **로보티콘**: 운영자 회원 계정은 모든 팩을 가진 것으로 본다(토큰 검사 통과). 로보티콘 코드는 이 브랜치에서 고치지 않는다 — 아래 shared 서비스를 부른다.

## shared 서비스 (로보티콘 `EmoticonGuard` 가 부를 것)
`com.community.operator.service.OperatorMemberAccountService`
- `Optional<ActiveOperatorAccount> activeOperator(long userId)` — 힘이 살아 있으면(정책 6) `ActiveOperatorAccount(long operatorId, String operatorName, long userId, boolean root, Set<String> permissions)`.
- `boolean isActiveOperatorAccount(long userId)` — 위가 있는가(로보티콘 모든 팩·표시).
- `Set<Long> activeOperatorUserIds(Collection<Long> userIds)` — 목록 작성자 표시용(한 질의).
- 로보티콘 연결 예: `EmoticonGuard` 의 「가진 팩」 판정 앞에 `if (operatorAccounts.isActiveOperatorAccount(userId)) return;`.

## API
공통: `ApiResponse`, ID 문자열, 시각 UTC ISO, 쓰기 CSRF.
### 관리자 (server/admin, `operator.read` 읽기)
- `GET /api/admin/operators/{operatorId}/member-account` → `OperatorMemberAccountView { linked: Linked|null, candidate: Candidate|null, powers: { items: [{ key, permission|null, granted }], dailyLimit: null } }` — `linked`·`candidate` 는 함께 오지 않는다.
  - `Linked { userId, nickname, grade, linkedAt, linkedBy: {operatorId, operatorName}|null, active, pausedReasons: [OPERATOR_UNAVAILABLE|MEMBER_SUSPENDED] }`
  - `Candidate { userId, nickname, grade, joinedAt, boardManagerCount, boardStaffCount, eligible, ineligibleReasons: [...정책 3], linkedOperatorName|null }`
  - `powers.items.key`: `ROBOTICON_ALL`·`OPERATOR_BADGE`(permission null, 늘 granted) · `POST_MODERATE`·`COMMENT_DELETE`(`post.manage`) · `AUTHOR_SANCTION`(`sanction.manage`) · `ACTION_READ`(`keeper-action.read`) · `ACTION_RESTORE`(`keeper-action.restore`). 정확한 칸은 admin-web `useOperatorMemberAccountApi.types.ts` 가 정본.
- `GET …/member-account/candidates?q=`(`operator.read`) → `{ items: SearchCandidate[] }` 최대 20건 — 2026-11-24 새로(아래 「세션 정리 결정 2026-11-24」).
- `PUT …/member-account` `{ userId }`(`operator.manage`) → 같은 View. 없는 회원 404 `MEMBER_NOT_FOUND`, 자격 미달 409 `OPERATOR_MEMBER_NOT_ELIGIBLE`(+ 최상위 `ineligibleReasons`), 이미 이어져 있으면 409 `OPERATOR_MEMBER_ALREADY_LINKED`, 루트 409 `OPERATOR_ROOT_PROTECTED`. ~~409 `OPERATOR_MEMBER_NOT_CANDIDATE`~~(2026-11-24 없앰).
- `DELETE …/member-account`(`operator.manage`) → 같은 View. 없으면 409 `OPERATOR_MEMBER_NOT_LINKED`.
- `OperatorView.memberAccount: { userId, nickname, active } | null` · 회원 상세 `operatorAccount: { operatorId, operatorName, active } | null`.
- 게시판 담당 후보 `ineligible` 에 `OPERATOR_ACCOUNT`.
- 강제 탈퇴 409 `OPERATOR_ACCOUNT_LINKED`.
- 스텝 처리 감독(`/api/admin/board-staff-actions`)·게시판 제재 이력의 처리자 `role` 에 `OPERATOR`(이름 = 회원 닉네임, `operatorName` 덧붙임).
### 공개 (server/api)
- `GET /api/auth/me`·로그인 응답 `operatorAccount: { operatorName } | null`(힘이 살아 있을 때).
- 작성자 `author.operator: boolean` · 공개 프로필 `operator: boolean`.
- `viewer.staff = true`, `viewer.staffTools = { operator: true, manager: true, abilities: {postHide, postDelete, commentDelete(= post.manage), sanction(= sanction.manage), restore(= keeper-action.restore)}, post: null, comment: null, sanction: null, resetsAt }` — 네 권한 중 하나라도 있을 때(없으면 null, 매니저·스텝 규칙과 같다). 매니저·스텝 응답은 `operator: false` + 숫자 한도 그대로.
- 처리(`/api/board-staff/posts|comments/…`) 응답 `quota: null`, 제재 preview·issue `quota: null`.
- 처리 내역 `GET /api/board-staff/boards/{boardId}/actions`: 운영자 회원 계정은 `keeper-action.read` 가 있으면 전체(처리자 포함, `manager: true`), 없으면 404 `BOARD_STAFF_NOT_FOUND`. 제재 내역도 같은 규칙(`keeper-action.read`). 줄 `actor.role` 에 `OPERATOR`.
- 탈퇴 `POST /api/me/withdrawal` 409 `OPERATOR_ACCOUNT_LINKED`(`GET /api/me/withdrawal` 은 그대로).

## DB (shared `V141__operator_member_accounts.sql` — develop 의 V142 은 사업자 서류 접수. ⚠ 로보티콘 브랜치가 V142~V143 을 가번호로 쓴다 — 합칠 때 뒤에 합치는 쪽이 번호를 다시 매긴다)
- `operator_member_accounts(operator_id bigint PK → operators ON DELETE CASCADE, user_id bigint NOT NULL UNIQUE → users ON DELETE CASCADE, linked_at timestamptz NOT NULL, linked_by bigint → operators ON DELETE SET NULL)`.
- `board_staff_actions`: `actor_role` CHECK ∪ `OPERATOR`, `actor_operator_id bigint → operators ON DELETE SET NULL`(OPERATOR 줄이면 필수 CHECK). `restored_by_kind` 는 그대로(MANAGER·OPERATOR — 운영자 회원 계정이 되돌리면 OPERATOR + `restored_by_id` = 운영자).
- `board_sanctions`: `issued_role` CHECK ∪ `OPERATOR`, `issued_operator_id bigint → operators`(OPERATOR 줄이면 필수).
- `post_moderation_events.actor_kind` 는 이미 `OPERATOR` 가 있다(그대로).
- `audit_logs.action` 은 자유 글자(새 값만 쓴다).

## 미확정
- 없음. 사업자 전환·이메일 변경 뒤 연결 유지(정책 2·3 은 연결 때만 판정)는 가정 — 바꾸려면 여기서 고친다.

## 서버 구현 결과 (2026-11-05, 미커밋)
- **DB** shared `V141__operator_member_accounts.sql`(develop V140 = 사업자 서류 접수라 V140 에서 옮김): `operator_member_accounts`(계약 그대로) · `board_staff_actions.actor_operator_id` + `actor_role` ∪ OPERATOR · `board_sanctions.issued_operator_id` + `issued_role` ∪ OPERATOR · 짝 CHECK `(자리 = OPERATOR) = (운영자 칸 있음)` · 운영자 칸 부분 색인. ⚠ 두 운영자 칸 FK 는 **ON DELETE 동작 없음**(계약의 SET NULL 은 「OPERATOR 줄이면 필수」 CHECK 와 부딪침 — 운영자 행 삭제는 승인 대기 반려뿐이라 실제로 막히지 않는다).
- **shared**: `OperatorMemberAccountStore`(JDBC — 판정 한 문장, 운영자 판정 글자는 `OperatorEntity#canLogin` 과 이중 구현) · `OperatorMemberAccountService`(계약 시그니처 그대로 + `allActiveOperatorUserIds`·`isLinked`·`linkOfUser|Operator`·`permissionsOf`, 시계는 `Clock` 빈) · `OperatorAccountLinkedException`(회원/관리자 문구만 다름). `BoardStaffActionService`·`BoardSanctionService` 가 운영자 회원 계정 길을 같이 탄다(판정 순서는 매니저·스텝과 같음). 탈퇴 막기는 `WithdrawalRequestWriter.apply`(회원 행 잠금 뒤 — 본인·강제 둘 다).
- **정한 것**: ① 사이트 **쓰기 제한 해제**는 `sanction.manage`(관리자 앱 해제 입구와 같은 권한 — 계약 표에 없던 칸), 매니저는 운영자 회원 계정이 준 제한을 풀지 못한다(되돌리기와 같은 규칙, 줄 `canLift` false · 403). ② 사업자 계정 = 사업자 회원 역할 또는 살아 있는 사업자 신원(PENDING·APPROVED·SUSPENDED). ③ `MEMBER_RESTRICTED` = 상태가 ACTIVE 가 아님. ④ 보호·표시·운영진은 **힘이 살아 있을 때만**(멈춘 운영자 회원 계정은 보통 회원), 탈퇴 막기·게시판 담당 자격은 **이어져 있기만 해도**. ⑤ 감사 행위 하나 더: `SITE_BOARD_SANCTION_LIFT`. 감사 대상: 글 처리·되돌리기 POST, 댓글 삭제 COMMENT, 제재·해제 MEMBER. 보관 분류는 일반 운영. ⑥ 공개 API 에 IP 를 넘기려고 `BoardStaffController` 가 `ClientIpResolver` 를 쓴다(shared 서비스 메서드에 `ip` 인자).
- **화면 연결 때 화면 쪽이 바꿀 것**: user-web `BoardStaffActionRow.actor.role`·`BoardSanctionIssuer.role` 에 `OPERATOR` · 처리·되돌리기·해제 응답 줄은 `canRestore`/`canLift` false. admin-web 스텝 처리 감독 `actor` · 게시판 제재 `issuer` 에 `operatorName`(새 칸, 운영자 회원 계정 줄만 값) · 게시판 담당 후보 `ineligible` `OPERATOR_ACCOUNT` 문구 · 오류 코드 `OPERATOR_ACCOUNT_LINKED`(본인·강제 탈퇴) · `OPERATOR_MEMBER_*`.
- **검증**: `compileJava`·`compileTestJava`(shared·api·admin) 통과. 새 테스트 api `OperatorMemberAccountSiteTest` 30개 · admin `OperatorMemberAccountAdminTest` 14개 통과(Testcontainers). 기존 관련(탈퇴·사업자 뱃지·감사 분류·게시판 관리) shared 14 · api 64 · admin 35 통과. bootRun·전체 테스트 안 함.
- **안 건드린 것**: 토론 핀 검토(`PinReviewers`)·공개 메뉴(`PublicMenuService`)·신고(`ReportService`)의 「운영진」 판정은 그대로 게시판 매니저·스텝만 본다.

## 화면 연결 (2026-11-05, 미커밋)
- 목업 걷어 냄: admin-web `operator/api/mock/operator-member-account-mock.ts`·`member/api/mock/member-operator-account-mock.ts`·`common/mock/mock-util.ts`(다른 사용처 없음), user-web `operator-account/api/mock/operator-account-mock.ts` 와 `// MOCK:` 줄 전부 — 이제 실제 API 를 부른다. 운영자 표 딱지는 연결·해제 뒤 목록을 다시 받는다.
- 서버 차이 반영: user-web 처리·제재 내역 처리자 `role` ∪ `OPERATOR` → 「… · 운영자」(`UtilBoardSanctionMessages.issuerLine`). admin-web 처리자 `role` ∪ `OPERATOR` + `operatorName` → 딱지 「운영자 · {운영자 이름}」, 한 줄 「{닉네임} (운영자 · {운영자 이름})」(스텝 처리 감독·게시판 제재·이의제기), 게시판 담당 후보 `OPERATOR_ACCOUNT` 문구. `OPERATOR_ACCOUNT_LINKED` 는 두 앱 모두 서버 문구를 그대로 보인다. 그 밖 칸은 서버 DTO 와 같음(`OperatorView.memberAccount`·`MemberDetail.operatorAccount` 는 필수 칸 `| null` 로 맞춤).
- 검증: 두 앱 `pnpm typecheck` 통과. dev 서버·브라우저 확인은 안 함.

## 운영팀 계정 기본 비공개 프로필 (사용자 2026-11-08)
- 운영자에 회원 계정을 이을 때(`PUT /api/admin/operators/{id}/member-account`) 그 회원 프로필을 비공개(`PRIVATE`)로 바꾼다 — 같은 트랜잭션, 감사 `OPERATOR_MEMBER_LINK` 의 `profileMadePrivate`. 이을 때 한 번만이고, 회원 본인이 나중에 공개로 돌리면 그대로 둔다. 데모 로더·데모 팩의 「아이로빗운영팀」도 비공개.

## 새 계정 만들기(2026-11-24 — 사용자 「목업 나오면 바로 서버까지 붙여서 운영 배포」, 화면 확정 단계 생략)
**의도**: 운영자에게 맞는 회원 계정이 없을 때 관리자가 사이트 회원 계정을 새로 만들어 그 운영자에 바로 잇는다. **정책 2 「같은 이메일」의 예외** — 이메일은 운영자 이메일과 달라도 되고, 본인 확인은 그 주소로 가는 메일(인증 또는 비밀번호 정하기)이 대신한다. 이메일 인증 전 계정을 잇는다(정책 3 의 인증 조건은 이 길에 없다, 정책 6 힘 판정에도 인증 조건을 더하지 않는다).
- 화면: 서랍 「사이트 계정」 탭 → 「새 계정 만들기」(`OperatorMemberCreateDialog`). 타입 정본 admin-web `useOperatorMemberAccountApi.types.ts`(`OperatorMemberCreateRequest`·`OperatorMemberCreateResult`).
- **API** `POST /api/admin/operators/{operatorId}/member-account`(`operator.manage`, CSRF) `{ email, nickname, passwordMode: DIRECT|MAIL, password|null }` → **201** `{ userId, email(정규화한 소문자), nickname, passwordMode, mailsSent: [EMAIL_VERIFICATION|PASSWORD_SETUP], account: OperatorMemberAccountView(linked 채움) }`. `MAIL` 인데 `password` 가 와도 쓰지 않는다(거절하지 않음).
- **판정 순서**: ① 400 `VALIDATION`(한꺼번에) `fields.email`(`UserPolicy.emailViolation`) · `fields.nickname`(`UserPolicy.nicknameViolation` — **예약어 안 봄**) · `fields.passwordMode`(DIRECT·MAIL 아님, 문구 `operatorMember.passwordMode.invalid`) · `fields.password`(DIRECT 만 — 비면 `user.password.required`, 그 밖은 `UserPolicy.passwordViolation`) ② 404 `OPERATOR_NOT_FOUND` ③ 409 `OPERATOR_ROOT_PROTECTED`(루트는 루트 본인만) ④ 409 `OPERATOR_MEMBER_ALREADY_LINKED` ⑤ 409 `EMAIL_TAKEN`(대소문자 무시) ⑥ 409 `NICKNAME_TAKEN`(대소문자 무시) ⑦ 409 `REJOIN_BLOCKED`(탈퇴 뒤 재가입 제한 — 공개 가입과 같은 차례·문구·`retryAfterSeconds`·`rejoinAvailableAt`) · 권한 없음 403. 동시에 같은 이메일·닉네임이면 유일 제약이 같은 409.
- **한 트랜잭션**: 회원(`users` — 비밀번호 있음·**이메일 인증 전**·약관 버전 = 지금 약관(`TermsService.currentAgreementVersion`, 공개 가입과 같은 칸 — 관리자가 운영팀 계정으로 만든 것으로 본다)·마지막 로그인 = 만든 시각(공개 가입과 같은 팩터리)) + 프로필(기본 등급, **비공개로 시작**) + `operator_member_accounts` 연결(`linked_by` = 만든 운영자) + 메일 대기열 한 통 + 감사 **`OPERATOR_MEMBER_CREATE`**(대상 OPERATOR, `detail {userId, passwordMode}` — 비밀번호·토큰·이메일 없음, 보관 분류 일반 운영). 연결 감사 `OPERATOR_MEMBER_LINK` 는 따로 남기지 않는다(CREATE 가 겸한다). 커밋 뒤 가입 무료 운영 로보티콘(공개 가입과 같은 길, 실패해도 성공).
- **비밀번호**: DIRECT = 관리자가 입력(공개 가입 규칙). MAIL = 아무도 모르는 무작위 비밀번호의 해시를 두고(링크에서 정할 때까지 로그인 불가) 「비밀번호 정하기」 링크를 보낸다. 링크가 지나도 「비밀번호 찾기」로 이어갈 수 있다(그 경로는 인증을 끝내지 않는다 — 로그인 뒤 인증 메일 다시 받기).
- **메일**(메일 대기열 `mail_outbox` — 커밋 뒤 배치를 켠 관리자 서버의 워커가 30초마다 보내고 실패하면 재시도, 계정은 남는다): DIRECT → `MEMBER_EMAIL_VERIFY`(공개 가입과 같은 인증 메일·문구, 링크 `{공개 주소}/verify-email?token=`, 24시간 `irobit.auth.verify-token-hours`) · MAIL → 새 필수 안내 메일 **`MEMBER_PASSWORD_SETUP`**(보안 메일, 「계정이 만들어졌어요 — 비밀번호를 정해 주세요」, 링크 `{공개 주소}/password/reset?token=` = 비밀번호 재설정 화면 그대로, 72시간 `irobit.account.setup-token-hours`, 운영자가 알림 문구 화면에서 문구를 바꿀 수 있다).
  - 링크 토큰은 대기열에 넣지 않는다 — 보낼 때 shared `AccountLinkMails` 가 새로 만들고 해시만 저장한다(재시도마다 새 링크, 앞 링크 무효). 이 두 종류는 인증 전 주소로도 간다(다른 대기열 메일은 인증한 주소만). 공개 주소가 없으면 재시도, 이미 인증했으면 보내지 않음.
  - 정하기 링크 = `auth_tokens.purpose` **`SETUP_PASSWORD`**(shared `V269`). 재설정 확인·재설정 API(`/api/auth/password/reset/check`·`/reset`)가 RESET_PASSWORD 와 같이 받고, 이 링크로 정하면 **이메일 인증도 끝난다**(첫 인증이면 인증 링크와 같은 뒤처리 — 선착순 보상 이벤트·가입 포인트). 재설정·정하기 링크 어느 쪽으로든 비밀번호를 바꾸면 둘 다 무효. 인증 링크 화면(`/verify-email`)에서는 못 쓴다.
- 로그인: DIRECT 계정은 공개 가입과 같이 인증 전에도 로그인은 되고 글·댓글·추천·쪽지는 인증 뒤(사이트 기존 규칙 그대로). MAIL 계정은 비밀번호를 정하기 전에는 로그인할 수 없다.
- 그대로 둔 것: 사이트 「운영자 회원 계정」의 닉네임 변경이 예약어를 건너뛰는 규칙(`MyProfileService`). 회원 만들기 DB 일은 api `MemberSignupWriter` 를 shared(같은 패키지)로 옮겨 공개 가입과 같이 쓴다(돌려주는 값 = 회원 ID). 재가입 제한 예외 `AuthRejoinBlockedException` 도 shared 로(같은 패키지).
- **검증**: `compileJava`·`compileTestJava`(shared·api·admin) 통과. 새 시험 admin `OperatorMemberCreateAdminTest` 4개(HTTP 201·DB 행·감사·대기열·보낼 때 링크·판정 순서·403) · api `PasswordSetupLinkTest` 2개(정하기 링크 → 인증 끝 · 재설정 링크는 인증 안 바꿈 · 인증 화면에서 못 씀) 통과(Testcontainers). 재가입 제한 409 는 시험하지 않음(공개 가입과 같은 호출).

## 세션 정리 결정 2026-11-24 — 같은 이메일이 아니어도 잇는다(사용자 「같은 이메일이 아니어도 연결 된다고 회원 계정과 운영자 계정 말이야」)
**정본**: 정책 2 「같은 이메일 회원만」 제한을 없앤다. 관리자가 회원을 검색해 고르면 정책 3 자격(인증 · 정상 회원 · 사업자 아님 · 다른 운영자에 안 이어짐)만 판정해 잇는다. 같은 이메일 회원은 `GET …/member-account` 의 `candidate` 로 추천한다(그대로). 정책 4(게시판 담당 끝내기)·프로필 비공개·감사 `OPERATOR_MEMBER_LINK` 는 그대로, 감사 `detail` 에 `sameEmail`(운영자 이메일과 같았나) 을 더한다.
- **검색** `GET /api/admin/operators/{operatorId}/member-account/candidates?q=`(`operator.read`) → `{ items: [{ userId, memberNo, nickname, maskedEmail, grade, joinedAt, boardManagerCount, boardStaffCount, eligible, ineligibleReasons, linkedOperatorName }] }` 최대 20건.
  - `memberNo` = 관리자 회원 관리의 회원번호(= 회원 ID, `userId` 와 같은 값). `maskedEmail` = 관리자 회원 목록의 가림(`MemberMasking.email` — `ab***@gmail.com`), **늘 가린다**(검색 입구 권한이 `operator.read` 라 `member.pii` 를 보지 않는다). 이메일 없는 회원(소셜)은 `null`.
  - `q`: 앞뒤 공백을 자르고 2~100자(어기면 400 `VALIDATION fields.q` — 문구는 공용 검색 키 `search.q.tooShort`·`search.q.tooLong`). 숫자만 = 회원번호 일치(숫자만인 닉네임의 부분 일치도 함께, 회원번호 일치가 먼저) · `@` 포함 = 이메일 **전체 주소** 일치(대소문자 무시 — 관리자 회원 목록이 개인정보 권한 없이 찾는 방식과 같다, 일부로는 못 찾는다) · 그 밖 = 닉네임 부분 일치(대소문자 무시).
  - 탈퇴를 마친 회원(WITHDRAWN·익명화)과 **이 운영자에 이미 이어진 회원은 뺀다**. 탈퇴 진행·정지 회원은 `MEMBER_RESTRICTED`, 다른 운영자에 이어진 회원은 `LINKED_TO_OTHER` + `linkedOperatorName` 으로 온다. 정렬 = 정확히 맞는 것(회원번호 · 닉네임 전체 일치 · 이메일) 먼저 → 가입 최신순.
- **연결** `PUT …/member-account { userId }` 판정 순서: 404 `OPERATOR_NOT_FOUND` → 409 `OPERATOR_ROOT_PROTECTED` → 409 `OPERATOR_MEMBER_ALREADY_LINKED` → 404 `MEMBER_NOT_FOUND`(없거나 ID 형식이 틀림 — 회원 행 잠금) → 409 `OPERATOR_MEMBER_NOT_ELIGIBLE`(오류 본문 최상위 `ineligibleReasons: [...]`). `OPERATOR_MEMBER_NOT_CANDIDATE` 코드·문구는 지웠다.
- 서버: shared `OperatorMemberAccountStore`(`candidateById`·`searchCandidates`, 후보 줄에 `email`) · admin `OperatorMemberAccountAdminService#searchCandidates`·`link` · `OperatorMemberAccountController` `GET /candidates` · DTO `OperatorMemberCandidates`. DB 변경 없음.
- 검증: `compileJava`(shared·admin·api)·admin `compileTestJava` 통과. admin `OperatorMemberAccountAdminTest` 19개 통과(Testcontainers — 다른 이메일 회원 연결·`sameEmail`·없는 회원 404·자격 미달 409 + 사유·검색 닉네임/이메일/회원번호·가린 이메일·2자 미만 400·403·탈퇴/이 운영자 연결 제외·다른 운영자 연결 사유).
