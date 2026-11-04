# 회원 제재·이의제기 계약 (**정책·화면 확정 2026-11-04 · 서버 구현 중**)

**의도**: 운영자가 회원에게 사이트 제재(경고 → 경고 → 정지 3일 → 7일 → 30일 → 영구 정지)를 주고 풀며, 회원은 제재 안내를 확인하고 이의제기를 한다. 정지는 `users.status`(SUSPENDED·BANNED)를 바꾸고, 그 상태를 보고 막는 일(글·댓글·쪽지·소모임·핫딜·신고·출석·포인트 상점·공구 구매)은 이미 서버에 있다(`BoardActorResolver` 등). 근거는 community `docs/api-sanction.md` 1~5절을 로보얌 운영자 권한에 맞춘 것.
**별개 기능**: 이용 제한(포인트 동결·공구 금지·충전 금지 — `server/shared/.../restriction/**`, 표 `member_restrictions`)은 계정 상태를 바꾸지 않는 기능별 제한이다. 표·화면·단계를 섞지 않는다(관리자 API 는 둘 다 `sanction.manage` 를 쓴다).

## 결정 (2026-11-04, 사용자)
1. **사이트 제재만** 만든다. 게시판 한정 제재(매니저·스텝의 경고·쓰기 제한, `board.sanction.issue`)는 스텝 처리 기능과 함께 따로 — 단계·횟수도 따로 센다.
2. **경고·제재는 시간이 지나도 사라지지 않는다**(community 그대로). 유효한(`VOIDED` 아닌) 제재로 다음 단계를 센다.
3. **이의제기는 제재 시작 후 30일 안에만** 받는다(community 는 기한 없음 — 로보얌 결정). 30일이 지나면 `409 APPEAL_EXPIRED`, 화면에 마감 시각을 보인다.
4. **신고 처리에서 바로 제재**한다 — 조치(숨기기·자동 숨김 확정·삭제)에 작성자 제재를 곁들이거나 「제재만」. 같은 트랜잭션, 제재에 신고 사건이 이어진다.
5. 아래 「정책」 1~7 은 제안대로(사용자 이의 없음).

## 정책
1. **단계**(`kind` · `rank`): `WARNING`(1회째 1 · 2회째 2) → `SUSPEND_3`(3) → `SUSPEND_7`(4) → `SUSPEND_30`(5) → `BAN`(6). 다음 단계 = 유효한 제재의 경고 수 `w`·가장 높은 정지 단계로 서버가 계산(정지가 없으면 `w<2` 경고, 아니면 3일 · 3일이면 7일 · 7일이면 30일 · 30일이면 영구). 다음 단계보다 **낮은** 제재는 못 준다(`SANCTION_STEP_TOO_LOW`). **높은** 제재 = 단계 건너뛰기(`skipStep` + 사유).
2. **대상**: 탈퇴(진행 중 포함)·없는 회원 404, 이미 영구 정지 409. **정지 중인 회원에게는 영구 정지만**(진행 중 정지는 `ENDED` 로 대체). 운영자는 `users` 가 아니라 따로라 「운영진 계정」 막기는 없다.
3. **권한**: 조회 `sanction.read` · 경고·기간 정지 부여·기간 정지 해제 `sanction.manage` · 영구 정지·단계 건너뛰기·영구 정지 해제 `sanction.ban`(민감 조치) · 이의제기 인용·기각 `appeal.manage`(목록·상세 보기는 `sanction.read`). 영구 정지는 회원 닉네임을 직접 입력해야 한다(`confirmNickname`). 네 권한 모두 featureReady → true.
4. **정지 중**: 지금 서버 동작 그대로 — 로그인·읽기·설정·알림·이의제기·내 제재 보기·이미 한 주문 조회·환불은 되고 새 활동은 막힌다. 정지가 시작되면 그 회원의 게시판 매니저·스텝 지정이 같은 트랜잭션에서 끝난다(되살아나지 않음). 등급 자동 승급은 멈춘다(`GradeProgress.PAUSED_SANCTION`).
5. **상태**(`status`): `ACTIVE`(유효·진행 중) · `ENDED`(기간 끝 또는 영구 정지로 대체) · `LIFTED`(운영자 해제 — 정지·영구만, 횟수에 남음) · `VOIDED`(이의제기 인용 — 횟수에서 빠짐, 진행 중 정지는 즉시 풀림). 해제·인용으로 진행 중 정지가 사라지면 회원은 즉시 `ACTIVE`.
6. **이의제기**: 제재 하나에 1번(경고 포함, 기각 뒤 재접수 불가 `APPEAL_ALREADY`), `VOIDED` 불가(`SANCTION_NOT_APPEALABLE`), 시작 후 30일 안(`APPEAL_EXPIRED`), 사유 10~500자, 정지·영구 정지 중에도. 처리는 인용/기각 + 답변 1~500자 필수. 동시 처리는 행 잠금으로 한 번만(`409 APPEAL_CLOSED`). 접수번호 `no` 는 짧은 순번.
7. **만료**: DB 크론(pg_cron) 1분마다 끝난 정지를 `ENDED` + 회원 `ACTIVE`. 로그인 정보(`/api/auth/me`)·내 제재를 읽을 때도 지난 정지를 먼저 끝낸다(크론이 늦어도 회원에게는 정확).
8. **알림·감사**: 회원 이벤트 `SANCTION_ISSUED`·`SANCTION_LIFTED`·`APPEAL_ANSWERED`(끌 수 없는 계정 알림, 묶음 `system`, 누르면 `/sanction`). 메일은 부여·이의제기 답변만, 인증된 이메일에게. 운영자 알림 `SANCTION_APPEAL_FILED`(예약 → 켬, `appeal.manage` 보유자). 감사 `SANCTION_ISSUE`·`SANCTION_LIFT`·`APPEAL_ACCEPT`·`APPEAL_REJECT`.

## 화면
### admin-web (목업 → 확정 대기)
- `/sanctions` 「제재」(메뉴 「커뮤니티 안전」, `sanction.read`): 조회(범위 전체·진행 중·끝남 / 종류 / 닉네임·제재 번호 / 기간) · 표 · 서랍 `?sanction=`(상세·해제·그 회원 이력·신고 사건 링크) · [제재하기] 대화창(회원 찾기 → 현황·단계 선택지 → 사유).
- `/appeals` 「이의제기」(`sanction.read`, 처리는 `appeal.manage`): 상태(대기·인용·기각·전체), 오래된 순 기본, 서랍 `?appeal=`(이의 사유·제재·신고 근거·회원 이력·[인용]/[기각] + 답변).
- 회원 상세 제재 구획: 요약(경고 n/2·진행 중·다음 단계) + [제재하기]·[해제] + 이력(범위 칸 제거, 위 단계 이름).
- 신고 서랍: 처리 폼에 「작성자 제재」 곁들이기 + 조치 「제재만」, 작성자 카드에 제재 요약.
- 대시보드 「제재」 가젯: 화면은 이미 있다(`DashboardSanctions{active, appealsPending, appeals[3]}`) — 서버가 값을 내면 끝.
### user-web (목업 있음 — 고칠 것)
- `/sanction`·`/sanction/appeal`: 이의제기 마감(`appealDeadline`) 표시, 지난 제재는 「기한이 지났어요」, `409 APPEAL_EXPIRED`. 알림 종류 3개.

## API 후보 (화면 확정 뒤 다듬는다)
- 회원: `GET /api/me/sanctions` · `POST /api/me/sanctions/{id}/acknowledge` · `POST /api/me/appeals` — 모양은 `member-activity-contract.md` 「제재」 + `MySanction.appealDeadline`.
- 관리자: `GET /api/admin/sanctions` · `GET /api/admin/sanctions/preview?userId=` · `POST /api/admin/sanctions` · `POST /api/admin/sanctions/{id}/lift` · `GET /api/admin/appeals` · `GET /api/admin/appeals/summary` · `GET /api/admin/appeals/{id}` · `POST /api/admin/appeals/{id}/accept|reject` · 신고 `resolve` 에 `sanction` 칸 + 조치 `NONE`.
- 목업에서 더한 것: `GET /api/admin/sanctions/{id}`(서랍 주소로 바로 들어올 때) · `GET /api/admin/sanctions/targets?q=`(대화창 회원 찾기, 2자 이상, 최대 10명) · 해제·인용·기각은 204 대신 바뀐 줄·상세를 돌려준다. community 「최고 관리자만」(`requiresAdmin`·`ADMIN_ONLY`) → `requiresBan`·`403 PERMISSION_DENIED`.
- 모양의 원천(화면): admin `composables/sanction/api/useSanctionApi.types.ts`(`SanctionLine`·`SanctionPreview`·`SanctionOption`·`SanctionTarget`·`SanctionCreateRequest`·`SanctionLiftRequest`·`SanctionListView`·`SanctionQuery`) · `useAppealApi.types.ts`(`AppealItem`·`AppealListView`·`AppealDetail`·`AppealAnswerRequest`·`AppealSummary`) · 신고 `ReportResolveRequest.action` 에 `NONE` + `sanction?`, 사건 조치 `SANCTION_ONLY`, `ReportAuthorCard.sanction: SanctionPreview | null`. 회원 상세의 `sanctions`·`sanctionSummary` 는 화면이 읽지 않는다(제재 구획은 `preview` 로 그림 — 서버에서 지울 후보).
- 서버가 같이 고칠 것: `AdminScreenRegistry` 에 `sanctions`·`appeals`, `Permission`·`util-permission-catalog.ts` featureReady true, 회원 알림 종류 3개(user-web 타입·아이콘은 더함).
- 서버 번호: shared `V136`(V135 는 방문자 통계 2단계 `V135__stats_collection_more.sql` 이 씀 — 세션 정리 2026-11-04)·`R__batch_39`(합치기 직전 다시 확인).

## 연결 (2026-11-04)
- **실제 API 로 바뀐 것**: admin `useSanctionApi`(목록·`/{id}`·`/targets`·`/preview`·부여·해제)·`useAppealApi`(목록·`/summary`·`/{id}`·인용/기각) · 신고 `resolve` 가 `sanction`·`NONE` 을 그대로 보낸다(응답 `issuedSanction` → 토스트) · 신고 서랍 작성자 카드와 서랍에서 연 처리 대화창은 `authorCard.sanction`(서버 preview)을 쓰고, 줄 메뉴로 서랍 밖 사건을 처리할 때만 `/preview` 를 따로 받는다 · user-web `useSanctionApi`(`/api/me/sanctions`·acknowledge·appeals, `restriction` 은 로그인 정보 실제 값). 권한 카탈로그 네 권한 `featureReady` true.
- **지운 목업**: admin `composables/sanction/api/mock/sanction-mock.ts`·`composables/common/mock/mock-util.ts`(쓰는 곳 없음) · user-web `composables/sanction/api/mock/sanction-mock.ts`(`common/mock/mock-util.ts` 는 성인 확인 목업이 써서 남김). `?mock=` 스위치(`sanction-*`·`appeal-*`·`sanction-no-ban`)는 모두 없어졌다. `preview(userId, hint)` 의 `hint` 도 없앴다.
- **서버에 맞춘 것**: 부여 입구 = `sanction.manage` 또는 `sanction.ban`(영구 정지·건너뛰기 선택지는 `sanction.ban` 만 있어도 누를 수 있다 — `UtilSanction.choices`·`canIssue`) · 신고 처리의 비회원·탈퇴 작성자 404 `SANCTION_TARGET_NOT_FOUND`(코드로 갈라 현황을 새로 받고 대화창 안 서버 문구) · `NONE` 인데 제재 없음 `fields.sanction` → 단계 칸 아래, 제재 칸 오류 `fields["sanction.kind|reason|skipStep|confirmNickname"]` → 고르개·제재 사유 칸 아래 · 범위 ACTIVE 의 끝 지난 정지는 서버가 ENDED 로 주므로 [해제]가 나오지 않는다. 화면 타입은 서버 DTO 와 칸 이름·null 여부가 같다(번호·사건 번호는 숫자).
- **남은 차이**: 회원 상세 API 의 `sanctions`·`sanctionSummary` 는 여전히 비어 있고 화면은 읽지 않는다(서버 정리 후보). 화면 확인(브라우저)·테스트는 아직 하지 않았다.

## 미확정
- (화면 확정 때 채운다)

## 서버 구현 결과 (2026-11-04, 미커밋 · 테스트 없음)
- **DB** shared `V136__member_sanctions.sql`(⚠ V135 는 방문자 통계 2단계 몫 — 합칠 때 번호 다시 확인): `member_sanctions`(번호 = 시퀀스 `member_sanction_no_seq`, `warning_no` 저장 — 경고가 무효가 되면 남은 경고를 다시 매김, `acknowledged_at`·`lifted_*`·`voided_at`·`ended_at`, `report_case_id` → `report_cases` SET NULL) · `sanction_appeals`(번호 = `sanction_appeal_no_seq`, 제재당 하나 유일 `ux_sanction_appeals_sanction`) · 색인: 회원별 최근 · **진행 중 정지 회원당 하나** 부분 유일 `ux_member_sanctions_running` · 만료 `ix_member_sanctions_expire(ends_at) WHERE ACTIVE` · 목록 전체/진행 중/끝남 부분 색인 · 이의 대기 오래된 순 `ix_sanction_appeals_pending`. `report_cases.action` varchar(16) + CHECK 에 `SANCTION_ONLY` · 회원 알림 CHECK 에 3종(⚠ 합집합). 감사 행위는 DB CHECK 없음.
- **만료 배치** `R__batch_39_sanction_expire.sql`(키 `sanction-expire`, 분류 MEMBER 업무 배치, 1분마다): ① 끝난 기간 정지 → ENDED(ended_at = ends_at) + 다른 진행 중 정지가 없으면 회원 ACTIVE ② 막힌 상태(SUSPENDED·BANNED)인데 제재 기록이 있고 진행 중 정지가 없는 회원 → ACTIVE(탈퇴 취소로 옛 상태가 돌아온 경우). 회원마다 회원 행 `FOR UPDATE` 뒤 조건부 UPDATE. Java 쪽 같은 판정 = `MemberSanctionStore#endDue·releaseMember`(이중 구현 주석).
- **shared `com.community.sanction`**: `MemberSanctionService`(지연 만료 `settle` · 부여 · 해제 · 경고 확인 · 이의제기 접수 · 인용/기각) · `SanctionLadder`(단계 계산 — 목업 `previewOf` 와 같은 계산) · `MemberSanctionStore`(JDBC). 동시성: 회원 행 잠금 아래 다시 계산(차례 이의제기 행 → 회원 행 → 제재 행, 신고 처리는 사건 → 대상 → 회원) · 부분 유일 색인 · `ON CONFLICT` · 조건부 UPDATE. 단계별 권한은 `SanctionOperator`(admin 이 거부 감사 + 403 을 넣어 준다).
- **api**: `GET /api/me/sanctions` · `POST /api/me/sanctions/{id}/acknowledge`(204) · `POST /api/me/appeals`(201 `{id,no,status}`) — `/api/me/**` 로그인 규칙(401), 상태·인증 검사 없음. `MeService.of` 는 읽기 전 지연 만료(쓰기 트랜잭션) + `restriction{status, until, pendingNotices}`(정상이고 확인할 경고가 없으면 null, 탈퇴 진행 중 null).
- **admin**: `/api/admin/sanctions`(목록 · `/targets` · `/preview` · `/{id}` — `sanction.read` / 부여 201 · `/{id}/lift` 200 — 입구 `anyOf(sanction.manage, sanction.ban)`, 단계별은 서비스) · `/api/admin/appeals`(목록 · `/summary` · `/{id}` — `sanction.read` / `/{id}/accept|reject` 200 상세 — `appeal.manage`). 신고 `resolve` 에 `sanction{kind, skipStep, confirmNickname, reason}` + 조치 `NONE`(사건 조치 `SANCTION_ONLY`) — 같은 트랜잭션, 제재 오류면 조치도 롤백, 제재에 `report_case_id`, 응답 상세에 `issuedSanction`. 작성자 카드 `sanction` = preview(비회원·탈퇴 null). `AdminScreenRegistry` 에 `sanctions`·`appeals`(커뮤니티 안전, `sanction.read`). 대시보드 `SANCTIONS` 제공자(`active` = 진행 중 정지·영구 정지, `appealsPending`, 오래된 대기 3건 — `kind` 는 요청 언어 이름).
- **권한·알림·감사**: `sanction.read`·`sanction.manage`·`appeal.manage`·`sanction.ban` featureReady true. 회원 이벤트 `SANCTION_ISSUED`·`SANCTION_LIFTED`·`APPEAL_ANSWERED`(SAFETY, 잠금 ACCOUNT, 링크 `/sanction`, **처음 이메일 = 부여·답변만 켬** — 운영자가 이벤트 설정에서 끌 수 있음, 인증된 이메일만). 운영자 알림 `SANCTION_APPEAL_FILED` 예약 해제(링크 `/appeals?appeal={appealId}`). 감사 `SANCTION_ISSUE`(대상 MEMBER — 끝낸 게시판 지정 `boardStaffEnded`, 대체한 정지 `replacedSanctionId`) · `SANCTION_LIFT` · `APPEAL_ACCEPT` · `APPEAL_REJECT`(보관 분류 일반 운영). 정지 시작 때 `board_staff` 행 삭제(같은 트랜잭션).
- **화면 타입과 다른 점·정한 것**
  - 오류 코드는 화면 주석 그대로. 다만 신고 처리의 비회원·탈퇴 작성자는 **404** `SANCTION_TARGET_NOT_FOUND`(화면 목업은 409 — 코드만 보면 같다). `action: SANCTION_ONLY` 직접 전송은 400 `fields.action`(`VALIDATION`), `NONE` 인데 `sanction` 없으면 400 `fields.sanction`. 제재 칸 오류는 `fields["sanction.kind|reason|skipStep|confirmNickname"]`.
  - 목록 `from > to` 는 `fields.from`(목업과 같다). 형식 오류는 그 칸. 범위 ACTIVE 는 끝이 지난(크론 전) 정지를 뺀다. 관리자 읽기는 지연 만료를 쓰지 않고 끝이 지난 정지를 ENDED(`endedAt` = 끝)로 보인다.
  - 해제·인용·기각은 204 대신 바뀐 줄·상세(화면 기대대로). 부여 `reportCaseId` 가 없는 사건이면 404 `REPORT_CASE_NOT_FOUND`.
  - 영구 정지 해제 권한 = `sanction.ban` 만 있어도 된다(기간 정지 부여·해제는 `sanction.manage`). 부여 입구도 둘 중 하나.
  - 알림 문구: 제목 「{제재 이름} 제재를 받았어요 (끝, 한국 시간)」·「… 제재가 풀렸어요」·「… 이의제기에 답변이 왔어요(인용·기각)」, 본문 = 사유·답변(해제 사유는 회원에게 보내지 않는다).
  - 회원 상세의 `sanctions`·`sanctionSummary` 는 그대로 비어 있다(화면이 preview 로 그림).
- **검증**: `compileJava`·`compileTestJava`(shared·api·admin) 통과. 로컬 DB(roboyam-local-postgres)에 V136 을 `BEGIN … ROLLBACK` 으로 적용(색인·CHECK·유일 색인 막힘·`ON CONFLICT`·EXPLAIN 부분 색인 확인), 스키마 사본 임시 DB 에 V136 + R__batch_39 적용 후 `CALL batch_sanction_expire()` 실행 확인(만료·바로잡기·제재 없는 SUSPENDED 그대로, 실행 로그 OK). 테스트 작성·실행·bootRun·API 실제 호출은 하지 않았다.
- **프론트 후속**: admin `useSanctionApi.ts`·`useAppealApi.ts`·`useReportApi.ts` MOCK → REAL(`preview(userId, hint)` 의 `hint` 인자 제거, `resolveWithSanctionMock` 삭제), `util-permission-catalog.ts` 네 권한 featureReady true, user-web `useSanctionApi.ts` MOCK → REAL.
