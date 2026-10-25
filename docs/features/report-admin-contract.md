# 신고 관리 계약 (관리자)

운영자가 글·댓글 신고를 **대상별 사건**으로 모아 보고, 검토해서 조치(숨김·삭제·자동 숨김 확정)하거나 무시한다. community `docs/api-report.md` 관리자 절·`admin/app/pages/reports`를 로보얌 신고 모델(`reports` 표·자동 숨김·게시판 신고 정책)에 맞춘 것. 제재(정지·이의제기)는 커머스 병합 뒤 — 여기에는 붙일 자리만 둔다.

## 결정 (2026-10-24, 사용자)
- **신고 남용 차단은 「허위 신고로 표시한 무시」만 센다**(게시판 정책 반려 제한 N회/M일 → K일 신고 금지의 「반려」 = 허위 표시). 그냥 무시는 세지 않는다.
- **닫힌 사건 뒤의 새 신고는 새 사건으로 0부터** — 자동 숨김 셈·운영자 알림(PER_CASE)도 새로 시작한다.
- **조치 사유 = 사유 코드 + 글**: 코드는 신고 사유와 같은 체계(`SPAM_AD`·`ABUSE`·`OBSCENE`·`ILLEGAL_FILMING`·`RIGHTS`·`OTHER`), 글 1~500자. 작성자 알림에 코드 이름과 글이 간다.

## community 와 다른 점
- 대상은 **글·댓글만**(소모임·프로필 이미지·닉네임 이미지·쪽지 대상과 그 조치·열람 기록은 뺀다).
- 로보얌은 **자동 숨김이 있다**(신고 정책) → 조치에 「자동 숨김 확정」, 무시하면 자동 숨김을 **복구**한다(게시판 신고 정책 화면이 이미 약속한 「반려하면 자동 복구」).
- 신고자 결과 알림을 만든다(community 에는 없음) — 게시판 정책 `notifyReporterOnResult` 를 따른다. 작성자 알림은 `notifyAuthorOnAction` 을 따른다(자동 숨김 알림 포함).
- 상태 거르기는 건수 없는 라디오(게시글 관리와 같은 결정). 대기 건수는 메뉴 뱃지에만(관리자 메뉴가 뱃지를 지원하면).
- 제재: 조치 요청에 `sanction` 칸 없음 — 서랍 작성자 카드에 「제재 — 준비 중」 자리만.

## DB (shared `V82__report_cases.sql` — 커머스 shared 는 **V83부터**)
- `report_cases(id, case_no(사람이 읽는 번호 — 시퀀스), target_type POST|COMMENT, target_id, post_id, board_id, target_author_id|null, status PENDING|IN_REVIEW|RESOLVED|DISMISSED, report_count, first_reported_at, last_reported_at, reviewer_id → operators, review_started_at, closed_at, closed_by → operators, action HIDE|DELETE|KEEP_HIDDEN|DISMISS|null, reason_code|null, note|null, false_report boolean, created_at, updated_at)` — **대상마다 열린(PENDING·IN_REVIEW) 사건 하나**(부분 유일 색인). 색인 (status, last_reported_at desc) 등.
- `reports.case_id` 추가. 기존 PENDING 신고는 대상별 사건으로 묶어 옮긴다(이미 처리된 옛 신고는 사건 없이 둔다).
- `reports.status` CHECK 에 `DISMISSED` 추가: 사건이 조치로 닫히면 그 신고들 `ACCEPTED`, 허위 표시 무시면 `REJECTED`(반려 제한이 세는 값 — 기존 코드 그대로 산다), 그냥 무시면 `DISMISSED`. 다시 열면 `PENDING`.
- `report_events(id, case_id, type REPORTED|AUTO_HIDDEN|REVIEW_STARTED|RESOLVED|DISMISSED|REOPENED, actor_kind OPERATOR|SYSTEM|MEMBER, actor_id, detail jsonb, created_at)`.
- 회원 알림 type CHECK 에 `COMMENT_REMOVED`(운영자가 내 댓글을 지움)·`REPORT_RESULT`(내 신고 처리 결과) — 지금까지 목록과 합집합(⚠ 커머스 합집합 주석).
- 보유기간 배치(`R__batch_18`)가 **열린 사건의 신고는 지우지 않게**.

## 신고 접수 쪽 고침 (server/api `ReportService`)
- 신고가 들어오면 그 대상의 열린 사건에 붙이거나 새 사건을 연다(`report_count`·`last_reported_at`·이벤트 REPORTED).
- **자동 숨김 셈·PER_CASE 알림 셈은 열린 사건의 신고만** 센다(지금은 상태를 거르지 않아 반려 뒤 새 신고 1건에 바로 다시 숨는 결함).
- 자동 숨김이 걸리면 이벤트 AUTO_HIDDEN, 작성자 알림은 `notifyAuthorOnAction` 이 켜졌을 때만.
- 운영자 알림 `REPORT_RECEIVED` 링크 `/reports?report={caseId}`.

## API `/api/admin/reports` (`report.read` 조회 · `report.manage` 처리, 응답 `ApiResponse`, ID 문자열, 쓰기 CSRF, `no-store`)
- `GET ?status=&targetType=&reason=&searchField=&q=&from=&to=&board=&postId=&sort=&page=&size=` → `{ total, page(0부터), size, items: [ReportCaseItem] }`
  - `status`: `OPEN`(기본 — 대기+검토 중) · `PENDING` · `IN_REVIEW` · `RESOLVED` · `DISMISSED` · `ALL`. `targetType` POST|COMMENT. `reason` = 신고 사유 코드(그 사유 신고가 하나라도 있는 사건). `searchField`: `TITLE`(대상 글 제목) · `AUTHOR`(대상 작성자 닉네임·비회원 이름) · `CASE_NO`. `from`/`to` = 마지막 신고 시각 UTC ISO(게시글 관리와 같은 규칙). `postId` = 그 글과 그 글의 댓글 사건(게시글 관리 「신고 대기」에서 넘어옴). `sort`: `newest`(마지막 신고 최신, 기본) · `oldest` · `most`(신고 많은 순).
  - `ReportCaseItem { id, caseNo, status, targetType, target: {postId, commentId|null, title, excerpt(앞 80자), state: PUBLIC|HIDDEN|DELETED, hiddenReason|null, hiddenUntil|null}, board: {id, name}, author: {kind, id|null, name, withdrawn}, reportCount, reasons: [{code, count}], firstReportedAt, lastReportedAt, reviewer: {id, name}|null, closedAt|null, closedBy|null, action|null }`
- `GET /summary` → `{ pending, inReview }`.
- `GET /{id}` → `{ case: ReportCaseItem, content: {format, body|null, snapshot: {title, body, authorName}(첫 신고 때 스냅샷)}, reports: [{id, reporter: {id, name, withdrawn}, reason, memo|null, createdAt, status}], authorCard: {id|null, name, grade|null, joinedAt|null, closedCases: {resolved, dismissed}(이 작성자 대상의 최근 90일), sanction: null}, events: [...], publicUrl|null }` — **PENDING 이면 열람과 함께 IN_REVIEW**(이벤트 REVIEW_STARTED, reviewer = 나). 신고자 정보는 운영자에게만.
- `POST /{id}/resolve` `{ action: HIDE|DELETE|KEEP_HIDDEN, reasonCode, note }` (`report.manage`) → 200 상세. 열린 사건만(`409 REPORT_CASE_CLOSED`). `HIDE` = 공개 대상만 운영자 숨김(STAFF), `KEEP_HIDDEN` = 이미 자동 숨김된 대상을 확정(권리침해 임시조치면 `hidden_until` 을 지워 계속 숨김), `DELETE` = 삭제(글은 게시글 관리 삭제와 같은 규칙 — 포인트 회수·`POST_REMOVED`; 댓글은 새 운영자 댓글 삭제 — 댓글 수·포인트 회수·`COMMENT_REMOVED`). 상태가 안 맞으면 `409 REPORT_ACTION_INVALID`. 글 조치는 `post_moderation_events` 에도 남긴다.
- `POST /{id}/dismiss` `{ reasonCode?, note?, falseReport }` → 200 상세 — 자동 숨김(REPORTED·ILLEGAL·RIGHTS)이었으면 **복구**, 운영자 숨김(STAFF)은 그대로.
- `POST /dismiss` `{ ids(1~50), falseReport, note? }` → `{ done: [id], failed: [{id, code}] }` — 일괄은 무시만.
- `POST /{id}/reopen` → 200 상세 — 무시한 사건만(조치한 사건 `409 REPORT_CASE_CLOSED`), 같은 대상에 이미 새 열린 사건이 있으면 `409 REPORT_CASE_OPEN_EXISTS`. 복구했던 자동 숨김은 다시 숨기지 않는다. 허위 표시를 풀고 신고들 PENDING.
- 조치·무시가 끝나면: 신고자 알림 `REPORT_RESULT`(게시판 `notifyReporterOnResult`, 사건의 신고자 모두 — 「신고하신 글이 조치됐어요」/「위반이 확인되지 않았어요」), 작성자 알림(`notifyAuthorOnAction` — 숨김 `POST_HIDDEN`/`COMMENT_HIDDEN`, 삭제 `POST_REMOVED`/`COMMENT_REMOVED`, 사유 코드 이름 + 글).
- 감사: `REPORT_RESOLVE` · `REPORT_DISMISS` · `REPORT_BULK_DISMISS` · `REPORT_REOPEN`(대상 REPORT_CASE). 오류: `REPORT_CASE_NOT_FOUND`(404) · `REPORT_CASE_CLOSED` · `REPORT_ACTION_INVALID` · `REPORT_CASE_OPEN_EXISTS`(409), 칸 `fields.action|reasonCode|note|ids`.
- 권한 `report.read`·`report.manage` featureReady true. 메뉴: 묶음 「커뮤니티 안전」(새 묶음, 콘텐츠 묶음 앞) 에 `reports` `/reports` `menu.reports` `pi pi-flag` `report.read`. 권한 정리: 쓰이지 않는 `bannedword.read`·`bannedword.settings` 를 카탈로그에서 뺀다(금지어는 게시판 기본 설정 › 보안 = `board.settings`) — 역할에 저장된 행이 있으면 같은 마이그레이션에서 지운다.
- 게시글 관리: 목록 「신고」 칸(`openReports` = 그 글 열린 사건의 신고 수)을 누르면 `/reports?postId={id}`.

## 화면 admin-web `/reports` 「신고 관리」
- 조회 폼(게시글 관리와 같은 구조): 검색 대상(제목·작성자·사건 번호) + 검색어 · 대상(전체·글·댓글 라디오) · 사유(Select) · 게시판 · 마지막 신고일 기간(빠른 선택) · 상태 라디오(처리 대기·검토 중·처리됨·무시·전체, 건수 없음) · 검색·초기화.
- 표: 체크(열린 사건, `report.manage`) · 사건 번호 · 대상(글/댓글 칩 + 제목·발췌, 대상 상태 알약 — 자동 숨김·임시조치 기한) · 게시판 · 작성자 · 신고 수 · 사유 분포 칩 · 마지막 신고 · 상태 알약(검토자) · ⋯. 정렬은 「마지막 신고」·「신고 수」 머리. 쪽 크기 고르개. 주소 쿼리 전부 + `report`.
- 일괄 줄: 고른 열린 사건 [무시](허위 표시 체크) → 결과 대화창.
- 서랍(`?report=`, 이전/다음): 대상(원문 + 신고 당시 스냅샷 비교, [게시글 관리에서 보기] `/posts?post=`, [공개 사이트에서 보기]) · 신고 목록(신고자·사유·메모·시각) · 작성자 카드(제재 자리 「준비 중」) · 처리(열린 사건 + `report.manage`: [숨기기]/[자동 숨김 확정]/[삭제]/[무시] — 사유 코드 Select + 글, 무시는 허위 표시 체크) · 처리 결과(닫힌 사건: 조치·사유·처리자, 무시한 사건 [다시 열기]) · 처리 기록.
- `report.read` 만 있으면 읽기 전용 안내 + 처리·체크 없음.
- user-web: 알림 종류 `COMMENT_REMOVED`·`REPORT_RESULT` 아이콘/타입.

## 미확정
- 제재 연결(조치와 함께 정지 등) — 커머스 병합 뒤.
- 게시판 매니저·스텝의 신고 처리 — 나중.
- 권리침해 게시자의 재게시 요청(이의) — 나중.

## 서버 구현 결과 (2026-10-24)
- **DB** shared `V82__report_cases.sql`: `report_cases`(번호 = 시퀀스 `report_case_no_seq`, 열린 사건 부분 유일 색인 `ux_report_cases_open_target`) · `reports.case_id`(옛 PENDING 신고를 대상별 사건으로 옮김 — 사건 ID = 그 묶음의 가장 작은 신고 ID) · `reports.status` 에 DISMISSED · `report_events` · 회원 알림 CHECK 에 `COMMENT_REMOVED`·`REPORT_RESULT`(⚠ 합집합) · 회원 이벤트 `REPORT_RESULT` 를 처음 값(사용·앱 알림 켬)으로(V59 가 「준비 중」이라 꺼 둔 값) · `bannedword.*` 권한·역할 행 삭제. `R__batch_18` 신고 기록 축은 열린 사건의 신고를 세지도 지우지도 않는다.
- **공유 코드**: `ReportCaseEntity`·`ReportEventEntity`·`ReportCaseRepository`·`ReportEventRepository`·`ReportCaseLog`(shared `com.community.report`). 대상 단위 잠금 키 `ReportCaseEntity.lockKey` — 신고 접수와 「다시 열기」가 같이 쓴다(차례: 대상 잠금 → 사건 행 → 대상 행).
- **접수(api `ReportService`)**: 열린 사건을 잠가 읽어 붙이거나 새로 연다 → 기록 REPORTED(신고자 ID 는 기록에 남기지 않고 `detail.reportId` 만 — 보유기간 배치가 지우는 `reports` 에만 신고자를 둔다) → 자동 숨김 셈은 `countedReporters(caseId)` → AUTO_HIDDEN → 작성자 알림은 `notifyAuthorOnAction` 일 때만 → `REPORT_RECEIVED` 는 PER_CASE 면 사건의 첫 신고(`report_count == 1`), 링크 `/reports?report={caseId}`.
- **관리자(admin `com.community.report`)**: `AdminReportController` · `AdminReportQueryService` · `ReportCaseActionService` · `AdminReportRepositoryQDSL`. 글 조치는 `AdminPostModerationService#hideForReport·unhideForReport·deleteForReport`(같은 트랜잭션, 조치 기록 덧붙임 `reportCaseId`·`reasonCode`, 사유 = 글 앞 200자), 자동 숨김 확정은 조치 기록 `HIDDEN`(덧붙임 `keepHidden true`·`hiddenReason`). 댓글은 `CommentEntity#hideByStaff·keepHidden·unhideReported·delete` + 댓글 수·포인트 회수(`REF_COMMENT`).
- **계약과 다른 점·더한 것**
  - `ReportCaseItem` 에 `reasonCode`·`note`·`falseReport` 를 더했다(열린 사건은 null/false). `reviewer`·`closedBy` = `{id, name}|null`.
  - 상세 `events[]` = `{id, type, actor: {kind, id|null, name|null}, detail|null, createdAt}`(actor 는 늘 있다 — MEMBER·SYSTEM 은 id·name null). RESOLVED/DISMISSED 덧붙임 `action`·`reasonCode`·`note`·`falseReport`(+ `revokedPoints` · 무시로 자동 숨김을 풀면 `restored` · 일괄이면 `bulk`), REOPENED `falseReportCleared`.
  - `authorCard.grade` = 등급 키. 비회원은 `id`·`grade`·`joinedAt` null, `closedCases` 0.
  - **열람 검토 시작은 `report.manage` 가 있을 때만**(읽기 권한만이면 상태를 바꾸지 않는다).
  - `target.title`·`excerpt` 는 원문을 파기했으면 null(화면은 `content.snapshot`). 댓글 사건의 `title` = 그 댓글의 글 제목.
  - 목록 칸 오류: `fields.searchField`·`q`(CASE_NO 가 숫자가 아님)·`from`·`to`(형식·뒤집힘). 모르는 `status`·`targetType`·`reason`·`sort` 는 기본값(OPEN·전체·전체·newest).
  - 조치 칸 오류 `fields.action|reasonCode|note`, 일괄 `fields.ids|note`. 무시의 `reasonCode` 는 고르면 검사, `note` 500자.
  - 일괄 실패 코드 `REPORT_CASE_NOT_FOUND`·`REPORT_CASE_CLOSED`·`INTERNAL`.
  - 「자동 숨김 확정」·「숨기기」 모두 작성자 알림은 운영자 숨김 문구(`POST_HIDDEN`·`COMMENT_HIDDEN` + 덧붙임 `by STAFF`·`reasonCode`). 사유 문구 = 「코드 이름: 글」(서버 번들 `report.reason.<코드>`).
  - 신고자 알림 `REPORT_RESULT` 덧붙임 `result`(ACTIONED·NO_VIOLATION)·`targetType`·`snapshot`(신고 당시 제목·앞부분), 링크는 볼 수 있는 그 글. 회원이 끌 수 있는 이벤트(잠금 없음).
  - 메뉴 묶음 키 `menu.safety`(아이콘 `pi pi-shield`). 메뉴 뱃지 기능이 없어 뱃지는 만들지 않았다(`/summary` 만).
  - 권한 유형 `BADWORD`(PermissionType)는 권한 0개로 남겨 두었다(화면 카탈로그 TYPES 와 함께 정리할 일).
- **검증**: `compileJava`(shared·api·admin)·`compileTestJava`(shared·admin) 통과, V82 + 고친 `R__batch_18` 을 로컬 DB 에서 BEGIN…ROLLBACK 으로 적용해 봄(옛 PENDING 신고 묶기 확인). 서버를 다시 띄우지 않아 새 API 실제 호출·테스트는 하지 않았다.
