# 역할 · 운영자 · 등급 계약 (**화면 확정 2026-10-21** — 백엔드 구현 단계)

**의도**: 운영자(관리자 앱 계정)와 그 역할·권한, 회원 역할(등급·회원 유형·게시판 단위·사용자 정의), 등급 설정을 관리자 앱에서 다룬다. 화면은 확정됐고(`apps/admin-web` 목업이 기준), 서버는 아직 `server/admin` 에 진입점만 있다(`V1~V6`).

## 백엔드 구현 순서와 가정 (2026-10-21)
단계는 순서대로 한다(앞 단계가 뒤 단계의 기반이다). 한 단계가 끝나고 사용자가 확인한 뒤 다음으로 간다.
1. **운영자 기반**(이번 단계): 권한 카탈로그(코드 enum `영역.행위`, 시작 때 DB 와 맞춤) · `operators`/`roles`/`role_permissions`/`operator_roles` 표 · 관리자 서버 로그인·세션·`me` · 권한 검사(`@RequirePermission` + 인터셉터, **선언 없는 API 거절** fail-closed) · 역할 API · 운영자 API(+초대·반려) · 설치 마법사 루트 계정을 `operators` 의 최고 관리자로 · 감사 기록 쓰기(표·서비스).
2. 등급 설정 + 회원 조회(읽기): 지금 있는 표(`users`·`profiles`)로 가능한 범위만. 포인트·동의 이력·공동구매 등 아직 기능이 없는 칸은 목업으로 남긴다.
3. 사이트 일반 설정(`site-settings-contract.md`): 이미지 업로드·변환·저장소.
- **1단계 구현 결과(2026-10-21)**: Flyway shared `V7`(operators·permissions·roles·role_permissions·operator_roles·user_roles·operator_invitations·audit_logs)·`V8`(기존 ADMIN·STAFF 회원을 `operators` 로 이전, `users.role` 을 `USER` 만 허용), admin 스트림 `V1`(`admin_session`). 권한 78개(운영자)+19개(회원) enum 이 원천이고 기동 때 DB 와 맞춘다. 관리자 API 17개는 모두 `@RequirePermission`/`@AdminAuthenticated`/`@AdminPublic` 선언이 있어야 기동한다(fail-closed). 오류 코드는 프론트 목업 기준에 더해 `PERMISSION_DENIED`·`ROLE_TYPE_DERIVED`(등급 파생에 만들기)·`ROLE_GENERAL_SUPER_ONLY`·`ROLE_IN_USE` 등. 로그인 `role` 은 호환용이며 판정은 `isSuper`·`permissions` 로 한다. 초대 수락 경로는 `{ADMIN_URL}/invitation?token=` 로 가정(수락 API·화면 미구현), 운영자 생성은 임시 비밀번호를 메일로 안내(재설정 기능 없음). 서버 시드에는 영역별 관리자 같은 운영자 역할이 없고 내장 역할은 회원 4개뿐이다. 반려한 신청은 행을 지우고 감사 기록에만 남긴다.
- **2단계 구현 결과(2026-10-21)**: Flyway shared `V9__grades`(`grades`·`grade_settings` 신설, `profiles.grade` → `grade_key` FK 이전 후 칸·CHECK 삭제, `grade_manual`·`bio`, `ix_users_created`). `grade_rules` 표는 아무도 읽지 않지만 삭제하지 않았다(→ 배치 인프라 단계의 V11 에서 삭제). **배포 주의: `profiles.grade` 를 지우므로 api·admin 을 같은 버전으로 함께 올린다.** API: `GET/PUT /api/admin/grades`, `POST …/grades/preview`, `GET /api/admin/members`·`…/{id}`·`…/role-options`(권한 `grade.read`/`grade.manage`/`member.read`, 이메일은 `member.pii` 없으면 마스킹, 상세 열람은 감사 `MEMBER_VIEW`). 오류: `GRADE_LIMIT`·`GRADE_FIRST_PROTECTED`·`GRADE_NAME_DUPLICATED`·`GRADE_RULES_NOT_MONOTONIC`(409, `fields` 없음)·`MEMBER_NOT_FOUND`·`MEMBER_PERIOD_REVERSED`·`MEMBER_POINT_RANGE_REVERSED`. 등급 저장 때 `ROLE_GRADE_<키>` 파생 역할이 만들어지고(기본 권한 = 바로 아래 등급 복사) 삭제·이름 변경도 따라간다. 승급 판정(`GradePromotionService`)은 가입 일수·활동 수 AND 조건, ACTIVE 회원만, 올리기만 한다. 활동 수는 글·댓글 표가 없어 0 으로 본다(**활동 조건이 있는 등급은 그 표가 생기기 전까지 도달 불가**). 배치 스케줄러(`promoteByElapsedTime`)는 배치 인프라 단계에서 연결됐고(아래), 글/댓글 작성 시 즉시 판정(`promoteOnEvent`) 호출 지점은 아직 없다. 회원 상세에서 서버가 비워 주는 칸(포인트·활동·제재·신고·동의 상세·알림 설정·공동구매·연락처·프로필·휴면 등)은 null/빈 배열 — 프론트 `MemberDetail` 타입을 nullable 로 바꾸고 빈 구획을 EmptyState 로 처리해야 한다. 공개 `gradeLabel` 은 이제 운영자가 입력한 한국어 이름이다(영어 이름 `labelEn` 은 후속).
- **배치 인프라 결과(2026-10-21)** — 화면 계약은 `docs/features/batch-contract.md`.
  - **구조**: 배치 **정의의 원천은 코드**(`admin` `BatchCatalog` enum — 키·분류·기본 cron·기본 사용 여부, 이름·설명은 메시지 번들 `batch.job.<키>.name/desc`)이고 서버가 뜰 때 DB `batch_jobs` 와 맞춘다(없는 배치만 기본값으로 넣고, 운영자가 바꾼 값은 덮어쓰지 않으며, 카탈로그에서 사라진 행은 지운다). **새 배치 = `BatchJob` 구현(빈) 1개 + 카탈로그 1줄 + 번들 문구**(짝이 안 맞으면 기동 실패). 일은 `BatchResult(처리 건수, 요약 문구 키+인자)` 를 돌려주고 예외로 실패를 알린다. 실행 주체는 **관리자 서버(`server/admin`)** 다 — 배치를 켜고·끄고·바꾸고·돌리는 곳이 관리자 앱이고, 공개 서버(`api`)는 스케줄러를 갖지 않는다. 엔티티·QueryDSL 저장소는 `shared`(Q 클래스 생성 위치), 나머지는 `admin` `com.community.batch`.
  - **테이블**(Flyway shared `V10__batches`): `batch_jobs`(`job_key` PK, `enabled`, `cron`, 마지막 실행 요약 `last_*`, `updated_by`) · `batch_runs`(실행 로그: `id`, `job_key`, `status` RUNNING/OK/FAIL/SKIP, `trigger_type` SCHEDULED/MANUAL, `scheduled_at`, `started_at`, `finished_at`, `affected_count`, `message_key`+`message_args`(요약, 요청 언어로 풀림), `error_message`, `operator_id`, `node_id`). 유일 색인 2개: `(job_key, scheduled_at)`(예약 시각마다 한 줄 — 서버가 여러 대여도 한 번만) · `(job_key) WHERE RUNNING`(동시에 하나만 실행 중). `V11__drop_grade_rules` — `grade_rules` 삭제(코드·이후 마이그레이션 어디서도 읽지 않음을 확인, 데이터는 V9 에서 `grades` 로 이전됨).
  - **스케줄러·cron**: Spring 내장 `ThreadPoolTaskScheduler` + `CronExpression`(의존성 추가 없음). 6칸 식(초 분 시 일 월 요일), 시간대 `Asia/Seoul`(`roboyam.batch.zone`). 서버가 켜진 배치마다 다음 실행 시각(slot)을 직접 계산해 그 시각에 한 번 예약하고 끝나면 다시 계산한다 — 서버가 여러 대여도 slot 이 같다. 사용 여부·cron 은 DB 가 원천이라 **재기동 없이** 바뀐다: 변경한 서버는 커밋 직후(`@TransactionalEventListener`), 다른 서버는 `roboyam.batch.refresh-seconds`(30초) 주기 폴링으로 따라온다. 서버가 내려가 있던 동안의 시각은 되살리지 않는다. 설정 키(`admin application.yml` `roboyam.batch.*`): `scheduler-enabled`(true) · `zone` · `refresh-seconds` · `min-interval-seconds`(60) · `log-retention-days`(90) · `pool-size`(4).
  - **중복 실행 방지(락)**: 배치마다 PostgreSQL 어드바이저리 잠금(세션 잠금, `pg_try_advisory_lock(7340002, hashtext(키))`, 배치가 도는 동안 연결 하나를 쥠 — 서버가 죽으면 DB 가 연결과 함께 풀어 준다)과 위 유일 색인의 이중 방어. 잠금을 못 잡은 쪽은 예약이면 조용히 건너뛰고 수동이면 409 `BATCH_RUNNING`. **비정상 종료로 남은 「실행 중」 줄**: 잠금을 쥔 쪽은 지금 그 배치를 돌리는 서버가 없다는 뜻이라, 기동 때·폴링(30초)마다·다음 실행 직전에 남은 RUNNING 줄을 FAIL(`batch.error.interrupted`)로 닫는다 — 다른 서버가 정말로 돌리는 중이면 잠금을 못 잡아 건드리지 않는다.
  - **실행 결과**: 일이 정상 종료하면 처리 건수 0 → `SKIP`(건너뜀, 「할 일 없음」), 1 이상 → `OK`, 예외 → `FAIL`(사유는 가장 깊은 원인의 예외 이름 + 첫 줄, 이메일·password/token 값·PostgreSQL `Key (칸)=(값)` 의 값을 가려 500자까지 — 전체 스택트레이스는 서버 로그에만). 꺼진 배치의 예약은 아무것도 남기지 않고, **수동 실행은 꺼져 있어도 돈다**.
  - **API**(`/api/admin`, 권한은 카탈로그의 `batch.*` — 새 권한 코드 추가 없음): `GET /batch-jobs`(`batch.read`) · `PUT /batch-jobs/{jobName}` `{enabled}`(`batch.manage`) · `PUT /batch-jobs/{jobName}/schedule` `{schedule}`(`batch.manage`) · `POST /batch-jobs/{jobName}/run`(`batch.run` 민감 조치, **끝날 때까지 기다림**, 실행 중이면 409) → 모두 **바뀐 목록 전체** `BatchJobView[]`; `GET /batch-logs?jobName&status&trigger&from&to&page&size`(`batch.read`, 시작 시각 최근순, `size` 1~100 기본 20) · `GET /batch-logs/{logId}`. 오류: 404 `BATCH_NOT_FOUND`·`BATCH_LOG_NOT_FOUND`, 409 `BATCH_RUNNING`, 400 `VALIDATION`(`fields.schedule`/`jobName`/`status`/`trigger`/`from`/`to`/`page`/`size`). 감사: `BATCH_TOGGLE`·`BATCH_SCHEDULE_CHANGE`(변경이 있을 때만, 전후 값)·`BATCH_RUN`(실행 결과 포함, 일이 실패해도 요청은 200).
  - **cron 안전장치(가정)**: 6칸이 아니거나 해석 불가·앞으로 실행 시각 없음(예: 2월 30일)·**앞으로 64번의 실행에서 인접 실행 간격이 60초 미만**(초 칸에 여러 값/`*`)이면 400 `fields.schedule`.
  - **등록된 Java 배치**: `grade-promotion` 「등급 일수 승급」(분류 MEMBER, 기본 `0 30 4 * * *` = 매일 04:30 KST — 정책 문서 미확정 11 의 **가정**, 처리 건수 = 승급 인원, `GradePromotionService.promoteByElapsedTime` 호출만; 시작 때 `grade_settings` 행을 잠가 등급 저장과 겹치지 않게 했다). 정리 배치는 아래 DB 배치로 모두 옮겼다(`batch-log-cleanup` 도 Java 배치가 아니라 DB 배치다 — 같은 키를 이어 쓰고 옛 `batch_jobs` 행은 기동 때 지워진다). 등급 설정 저장 직후의 소급 승급은 기존 그대로(활동 조건·새 등급·옵션 변경은 즉시, 일수 조건을 낮춘 몫은 저장 전·후 일수 중 큰 값으로 판정) 이 배치가 올린다.
  - **DB 배치(저장 프로시저 + pg_cron, 2026-10-21)** — 상세는 `batch-contract.md` 「DB 배치」, 규칙은 `.claude/guides/backend/db-batch.md`. Java 배치와 한 화면·한 API·한 로그 표(`batch_runs.source = POSTGRESQL`). 실행 주체는 DB(pg_cron)라 서버 대수와 무관하게 한 번, 관리자 서버가 내려가 있어도 돈다. 사용 여부·cron 은 `cron.job` 이 원천(`cron.alter_job`), 정의는 카탈로그(이름·설명·분류) + `batch_db_jobs`(프로시저·기본 cron) + `batch_params`(보관 일수·덩어리·상한). Flyway shared `V20__db_batches`(`batch_runs.source`·`batch_db_jobs`·`batch_params`·색인) + 반복 마이그레이션 `R__batch_00_helpers`(공통 함수) · `R__batch_10~14_*`(프로시저+등록, 운영자가 바꾼 cron·사용 여부·설정값을 덮어쓰지 않는다). 이미지: 공식 `postgres:18` + `postgresql-18-cron`(`docker/postgres/`, 설치기 `templates/postgres/`), 서버 설정 `shared_preload_libraries=pg_cron`·`cron.database_name`·`cron.use_background_workers=on`·`cron.timezone=Asia/Seoul`. **pg_cron 이 없어도 서버·Java 배치는 정상** — DB 배치만 `available=false` + 이유. 정리 배치(쌓이는 표 조사 결과, 보관 값은 모두 `batch_params` 로 바꾼다):
    | 표 | 어떻게 쌓이나 | 이미 정리되나 | 규칙(기본) | 배치 키 | 기본 실행(KST) |
    |---|---|---|---|---|---|
    | `auth_tokens` | 인증·재설정 메일마다 1행 | 아니오 | 만료 + 사용(있으면) 후 7일 | `auth-token-cleanup` | 03:00 |
    | `login_attempts` | 이메일(공개)·`admin:`+이메일(관리자) 키마다 1행, 성공/잠금 해제 뒤에도 남음 | 아니오 | 마지막 갱신 30일 + 잠금 풀림 | `login-attempt-cleanup` | 03:10 |
    | `operator_invitations`(+`_roles` 연쇄) | 초대마다 1행 | 아니오(취소만 행 삭제) | 만료 후/수락 후 30일 | `operator-invitation-cleanup` | 03:20 |
    | `audit_logs` | 로그인·권한 변경마다 | 아니오 | 365일(가정) | `audit-log-retention` | 03:30 |
    | `batch_runs` | 배치 실행마다(Java·DB) | Java 배치였던 `batch-log-cleanup` → DB 배치로 이전 | 끝난 지 90일(가정) | `batch-log-cleanup` | 03:40 |
    | `spring_session`·`admin_spring_session`(+`_attributes` 연쇄) | 로그인 세션마다 | **예** — Spring Session JDBC 4.1.1 의 `JdbcIndexedSessionRepository` 가 앱마다 매분(`0 * * * * *`, `cleanup-cron` 미변경) 자기 표의 `expiry_time` 지난 행 삭제 | 배치 없음 | — | — |
    | `users`(탈퇴·미인증)·`profiles`·`user_identities` | 가입마다 | 아니오 | 파기/미인증 정리 **정책 없음 → 만들지 않음** | — | — |
    - 나머지 표(`installation`·`site/mail/social 설정`·`grades`·`permissions`·`roles` …)는 단일 행·설정·고정 목록이라 쌓이지 않는다.
  - **같이 고친 2단계 발견점**: 역할 목록(`GET /api/admin/roles`) 정렬 — 제품 역할 먼저, 등급이 아닌 역할은 만든 순서, **등급 역할은 `grades.level` 서열 순**(가운데에 끼운 등급도 서열 자리에). 오류 응답에서 `fields` 가 없으면 **키 자체를 내지 않는다**(`ErrorResponse` 의 `fields` 만 `NON_EMPTY` — 칸 오류가 있는 응답은 그대로, `.claude/guides/backend/exception.md` 도 고침). 활동 수 0·포인트/제재 수 0 은 글·댓글·포인트·제재 표가 없어 지금 만들 수 없다(표가 생길 때 `GradePromotionService.activityReachable`·회원 상세 집계에 더한다).
- **가정**(정책 문서의 미확정 세부, 구현 때 바꿀 수 있다): 관리자 로그인 실패 5회 잠금(해제는 10분 뒤 자동, 공개 사이트 `LoginAttemptService` 방식 참고), 세션 비활동 2시간·최대 12시간·쿠키 이름 `ADMIN_SESSION`(공개 `SESSION` 과 분리), 초대 유효 72시간, 반려 사유 200자, 감사 보관 1년(보관 배치는 후속), 운영자 비밀번호 규칙은 공개 사이트 가입 규칙과 같다.
- **1단계 범위 밖**: 초대 링크를 받아 비밀번호를 정하는 화면·API(관리자 화면에 없음 — 데이터 모델과 토큰만 만든다), 신청서를 받는 공개 화면, 감사 로그 조회 API, 비밀번호 재설정, 회원 역할 수동 부여.
**정책의 출처**: `docs/domain/operator-permissions.md`(확정 규칙·역할 유형 표·추가 확정). 이 문서는 화면이 정한 모양과 API 후보만 적는다.

## 화면 (admin-web — 인증·역할·운영자·등급·회원 조회는 실제 서버 연결 완료, 회원 상세의 서버에 데이터가 없는 구획은 「아직 연동되지 않았어요」 안내)
| 경로 | 내용 |
|---|---|
| `/settings/roles/operator` | 운영자 역할. 탭 = **전체** · **종합** · 콘텐츠 · 커뮤니티 안전 · 회원 · 소모임 · 핫딜 · 공동구매 · 사업자·광고 · 시스템(영역). 영역 안은 세부 유형 소제목(24개)으로 묶는다 |
| `/settings/roles/member` | 회원 역할. 탭 = **전체** · 회원 유형 · 게시판 단위 · 사용자 정의. 어느 탭에서든 [역할 추가](사용자 정의 유형으로 생성) |
| `/settings/roles/grade` | 등급 역할(회원 역할의 한 종류). 탭 줄 없음, 등급 설정에서 자동 생성되는 파생물 → 만들기·이름 수정 불가, `/grades` 링크 |
| `/settings/operators` | 운영자 목록(탭: 승인 대기 포함 상태별 + **초대 대기**). 승인·**반려**·초대·상태·역할 부여 |
| `/grades` | 등급 설정. [저장]·[되돌리기]는 페이지 하단 떠 있는 저장 줄(`StickySaveBar`, 가이드 `forms.md` 2-4) |
| `/members` | 회원 조회(읽기 전용) — 회원 역할 표시만 |
- 메뉴(`util-admin-menu.ts`): 설정 > 운영자와 권한 > 운영자 · 역할 관리(하위: 회원 역할 · 운영자 역할 · 등급 역할). `/settings/roles` 와 모르는 종류는 `/settings/roles/operator` 로 간다. **화면 안에는 운영자/회원 전환이 없다** — 메뉴가 종류를 정한다. 탭은 `?tab=`(`all` = 전체, 기본).
- 타입·동작의 기준은 화면 코드: `app/composables/{role,operator,grade,member}/api/*.types.ts`, `app/utils/util-role.ts`, `util-permission-catalog.ts`, `util-grade.ts`, `util-operator.ts`.

## 규칙 (화면에서 정한 것)
- **운영자 역할 유형** = 카탈로그 세부 유형 24개(id `op_<유형id>`), 영역 9개 아래. 역할 하나는 세부 유형 하나에 속하고 후보 권한은 그 유형 것만. **종합 관리** 유형은 유형 제한 없이 고르며 최고 관리자만 만들고 고친다.
- **종합 탭**의 영역별 관리자(`<영역> 관리자`, 권한 = 그 영역 전부)는 목업 시드다(카탈로그에서 계산). 서버 시드로 둘지 미정.
- **회원 역할 유형 4종**: 등급(파생, `ROLE_GRADE_<key>`) · 회원 유형(제품 고정) · 게시판 단위(매니저·스텝, 제품 고정) · 사용자 정의(운영자가 추가). 새로 만들 수 있는 것은 사용자 정의뿐.
- 공통: 권한 합집합, 권한 상승 방지(자기 권한 범위 안에서만 부여), 내장 역할 삭제 불가. 권한: 조회 `role.read`·`operator.read`·`grade.read`, 변경 `*.manage`. 감추는 것은 편의이고 막는 것은 서버다.
- **운영자 계정**: 4개 상태(승인·잠금·만료·사용)와 계정 기간. 루트(최고 관리자)는 어떤 변경도 거절. 등록 두 방식 — 생성/초대(**초대 링크**, 유효 72시간, 같은 이메일의 유효 초대는 409) · 신청 후 승인/반려(반려는 승인 대기 건만, 사유 선택 200자).
- **등급**: 순서 있는 목록 2~10단계, 첫 등급은 삭제·이동 불가, 삭제하면 소속 회원은 바로 아래 등급으로 이동. 자동 승급 조건(가입 일수·글+댓글 수) — 활동 조건은 즉시, 일수 조건은 배치(주기 미확정). 수동 상향 옵션(`CONTINUE_AUTO` 기본 / 자동 승급 중지). 기준을 낮추거나 등급을 추가·삭제하면 저장 전 영향 미리보기 후 확인(소급 승급). 등급 목록+옵션+뱃지를 **PUT 한 번**으로 저장.

## API 후보 (확정 전 — 목업이 부르는 모양)
| | |
|---|---|
| `GET /api/admin/role-types?axis` | 역할 유형(`roleTypeId` `description` `axis` `area` `derived` `creatable` `roleCount`). 운영자 유형 24개를 서버가 줄지 화면 카탈로그로만 둘지 미정 |
| `GET/POST /api/admin/roles` · `PUT/DELETE …/{id}` · `PUT …/{id}/permissions` | 역할 목록·생성 `{roleTypeId, roleId, roleName, description}`·수정·삭제·권한(`{permissionCodes}`) |
| `GET/POST /api/admin/operators` · `PUT …/{id}` · `PUT …/{id}/status` · `POST …/{id}/rejection {reason}` | 운영자 목록(`tab`, `keyword`)·등록·수정·상태·반려(204) |
| `GET/POST /api/admin/operator-invitations` · `DELETE …/{id}` | 초대 목록(`invitationId` `email` `roles[]` `invitedAt` `expiresAt` `expired`)·생성·취소(204) |
| `GET/PUT /api/admin/grades` · `POST …/preview` | 등급 목록+옵션 조회·일괄 저장 · 저장 전 영향 미리보기(승급 인원·이동 회원 수) |
| `GET /api/admin/members` · `…/{id}` · 역할 선택지 | 회원 조회(읽기). 회원에게 사용자 정의 역할을 **수동 부여하는 API·화면은 아직 없다** |
- 오류 코드(목업): 403 `PERMISSION_ESCALATION`, 409 `ROLE_BUILTIN` · `ROLE_TYPE_FIXED` · `INVITATION_DUPLICATED` · `OPERATOR_NOT_PENDING`, 404 `INVITATION_NOT_FOUND`.

## 미확정 사항
1. 사용자 정의 역할을 회원에게 수동 부여하는 화면·API.
2. 반려한 신청의 보관 방식, 반려 사유를 신청자에게 알릴지, 만료된 초대의 보관·재발송, 초대 유효기간 값(72시간은 가정).
3. 등급 일수 조건 배치의 실행 주기·시각(하루 한 번 04:30 KST 는 가정 — 운영자가 cron 으로 바꿀 수 있다), 소급 승급 시 알림·이력, 활동 수 정의(삭제·숨김 제외 여부) — `operator-permissions.md` 미확정 11.
4. 종합 탭 영역별 관리자 역할을 서버 시드로 둘지, 권한 중 화면이 아직 없는 것(`featureReady:false`)까지 줄지.
5. 운영자 로그인 세부(잠금 해제 방식, 비밀번호 재설정), 감사 보관 기간(1년은 가정), 신청 항목·승인자 권한.
6. 서버 구조: `operators` 별도 테이블·역할/권한 3표, `users.role` 의 운영자 값 제거, 관리자 서버 인증 분리(`operator-permissions.md` "이 정책이 바꾸는 현재 코드").
