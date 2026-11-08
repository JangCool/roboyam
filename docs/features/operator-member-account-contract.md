# 운영자 회원 계정 계약 (화면 확정 2026-11-05 — 사용자 「미확정 추천대로해」)

**의도**: 관리자 앱 운영자(`operators`)마다 사이트(user-web)에서 쓰는 회원 계정(`users`) 하나를 1:1 로 잇는다. 이은 「운영자 회원 계정」은 사이트에서 ① 로보티콘 모든 팩 ② 닉네임 옆 「운영자」 표시 ③ 모든 게시판 관리(매니저처럼 — 운영자의 관리자 앱 권한 거울)를 가진다. 힘은 운영자 기준으로 판정·기록되고, 운영자를 못 쓰게 되면 함께 멈춘다.
**근거**: 사용자 결정 2026-11-05(로보티콘 상점 세션 — 게시판 매니저·스텝은 산 팩만, 대신 운영자에 회원 계정 연결, 범위 = 모든 팩 + 운영자 표시 + 게시판 관리). 도메인 `docs/domain/operator-permissions.md` 확정 규칙 1·2 개정. 이어지는 계약: `board-staff-action-contract.md`·`board-sanction-contract.md`(처리·제재), `member-withdrawal-contract.md`(탈퇴), `emoticon-contract.md`(로보티콘 — 다른 브랜치).

## 화면 경로
- admin-web `/settings/operators` → 행 → 서랍 탭 「사이트 계정」(`OperatorMemberAccountPanel`) · 운영자 표 이름 옆 딱지 · 회원 상세(`/members`) 기본 정보 맨 위 딱지 「운영자 회원 계정 · {운영자 이름}」.
- user-web: 작성자 이름 옆 「운영자」 뱃지(`PostAuthorLabel` — 등급 뱃지 대신, `StaffBadge role="ADMIN"` = 사이트 용어 `ADMIN_BADGE`) · 공개 프로필 머리 · 게시판 머리 운영 줄 「운영자 계정으로 관리 중 · 하루 한도 없음 · [처리 내역]」 · `/settings/account` 읽기 전용 카드 · `/settings/account/withdraw` 막힘 안내.

## 정책
1. **연결 1:1**: 운영자 하나 ↔ 회원 하나. 회원 표(`users`)에는 값을 두지 않고 운영자 쪽 표가 회원을 가리킨다.
2. **연결 대상 = 같은 이메일**: 운영자 이메일과 같은 이메일(대소문자 무시)로 가입하고 **이메일 인증을 마친** 회원만. 본인 확인을 대신한다. 서버가 그 회원을 찾아 후보로 준다(관리자가 아무 회원이나 고르지 못한다). 이은 뒤 회원이 이메일을 바꿔도 연결은 그대로.
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
- `PUT …/member-account` `{ userId }`(`operator.manage`) → 같은 View. `userId` 가 지금 후보가 아니면 409 `OPERATOR_MEMBER_NOT_CANDIDATE`, 자격 미달 409 `OPERATOR_MEMBER_NOT_ELIGIBLE`, 이미 이어져 있으면 409 `OPERATOR_MEMBER_ALREADY_LINKED`, 루트 409 `OPERATOR_ROOT_PROTECTED`.
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
- 운영자에 회원 계정을 이을 때(`PUT /api/admin/operators/{id}/member-account`) 그 회원 프로필을 비공개(`PRIVATE`)로 바꾼다 — 같은 트랜잭션, 감사 `OPERATOR_MEMBER_LINK` 의 `profileMadePrivate`. 이을 때 한 번만이고, 회원 본인이 나중에 공개로 돌리면 그대로 둔다. 데모 로더·데모 팩의 「로보얌운영팀」도 비공개.
