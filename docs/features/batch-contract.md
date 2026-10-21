# 배치 계약 (서버 구현됨 2026-10-21 — Java 배치 + DB 배치(저장 프로시저·pg_cron). 화면 연결은 후속)

**의도**: 관리자가 정해진 시각에 도는 일(배치)을 보고, 켜고 끄고, 주기(cron)를 바꾸고, 지금 실행하고, 실행 결과 로그를 본다. 배치는 두 종류다 — **JAVA**(관리자 서버가 돌린다)와 **POSTGRESQL**(DB 의 저장 프로시저를 pg_cron 이 부른다). 한 화면·한 API·한 로그 표(`batch_runs`)에 같이 보인다. 배치 정의(이름·설명·분류)는 코드(카탈로그)가 원천이다. 구조·테이블·락·가정은 `roles-contract.md` 「배치 인프라 결과」, DB 배치의 설치·규약은 아래 「DB 배치」.

## 화면 (admin-web — **실제 서버 연결 완료**, 2026-10-21: Java·DB(pg_cron) 배치 한 목록, 「어디서」 딱지, 사용 불가 DB 배치는 흐리게+이유, 주기 변경 대화상자 `batch.manage`, 지금 실행 `batch.run`)
| 경로 | 내용 |
|---|---|
| `/settings/batches` | 배치 목록: 이름·설명·주기(cron)·켜기/끄기·마지막 실행·지금 실행 |
| `/settings/batch-logs` | 실행 로그: 배치·결과 필터, 시작/끝·상태·처리 건수·메시지 |
- 타입의 기준: `app/composables/batch/api/useBatchApi.types.ts`. 서버 응답은 그 타입에 아래 칸을 **더한** 모양이다(어긋남은 아래 「프론트 타입과의 차이」).

## API (`/api/admin`, 응답은 `ApiResponse`)
| | 권한 | 내용 |
|---|---|---|
| `GET /batch-jobs` | `batch.read` | `BatchJobView[]` (카탈로그 순서 — Java 와 DB 배치가 섞여 있다) |
| `PUT /batch-jobs/{jobName}` `{enabled}` | `batch.manage` | 켜기/끄기 → 바뀐 목록 전체. DB 배치는 `cron.alter_job(active)` |
| `PUT /batch-jobs/{jobName}/schedule` `{schedule}` | `batch.manage` | cron 수정(재기동 없이 반영) → 바뀐 목록 전체. 화면에 아직 입력칸이 없다. **문법이 source 마다 다르다**(아래) |
| `POST /batch-jobs/{jobName}/run` | `batch.run`(민감) | 지금 실행 — 끝날 때까지 기다린다 → 바뀐 목록 전체. 꺼져 있어도 돈다. 일이 실패해도 200(마지막 실행 상태 `FAIL`). DB 배치는 같은 저장 프로시저를 `CALL` |
| `GET /batch-logs?jobName&status&trigger&from&to&page&size` | `batch.read` | `{items, total, page(0부터), size}`, 시작 시각 최근순. Java·DB 로그가 한 목록 |
| `GET /batch-logs/{logId}` | `batch.read` | 상세 |
- `jobName` = 배치 키. Java: `file-orphan-cleanup`. DB: `grade-promotion`(업무 배치 — 아래) · `auth-token-cleanup` · `login-attempt-cleanup` · `operator-invitation-cleanup` · `audit-log-retention` · `batch-log-cleanup` · `notification-cleanup`(관리자 알림 정리, 03:50 — `docs/features/notification-contract.md`). 오류: 404 `BATCH_NOT_FOUND`·`BATCH_LOG_NOT_FOUND`, **409 `BATCH_RUNNING`**(이미 실행 중), **409 `BATCH_UNAVAILABLE`**(DB 배치를 지금 쓸 수 없음 — pg_cron 없음·미등록. 이유는 목록의 `unavailableReason`), 400 `VALIDATION`(`fields.schedule` — 형식 오류·실행 시각 없음·간격 60초 미만, 로그 조건 칸).
- `BatchJobView`: 목업 칸(`jobName displayName description i18n descI18n source schedule enabled lastRunAt lastStatus lastMessage runnable`) + `category`(`MEMBER`/`SYSTEM`) · `defaultSchedule` · `nextRunAt` · `lastAffectedCount` · `running` + **`available`**(쓸 수 있나) · **`unavailableReason`**(쓸 수 없는 이유, 요청 언어로 푼 글 — 쓸 수 있으면 null) · **`scheduleZone`**(`schedule` 을 해석하는 시간대 IANA 이름). `displayName`·`description`·`lastMessage` 는 **서버가 요청 언어로 푼 글**이고 `i18n`·`descI18n` 은 항상 null(화면은 `dbText` 규칙대로 서버 글을 쓴다).
  - `source`: `JAVA` 또는 `POSTGRESQL`. `schedule`/`defaultSchedule` 의 문법: JAVA = Spring 6칸(초 분 시 일 월 요일), POSTGRESQL = pg_cron 5칸(분 시 일 월 요일). 시간대: JAVA = `Asia/Seoul`, POSTGRESQL = pg_cron 의 `cron.timezone`(우리 구성은 `Asia/Seoul`, 설정이 없으면 `GMT`) — 그래서 `scheduleZone` 을 같이 본다.
  - `runnable`: JAVA 는 항상 true, POSTGRESQL 은 `available` 과 같다. `available=false`(DB 배치만 가능)면 `enabled=false`·`nextRunAt=null` 이고 `schedule` 은 기본 cron(모르면 빈 글)이며, 켜기/끄기·cron·지금 실행은 409 `BATCH_UNAVAILABLE`.
- `BatchLogView`: 목업 칸 + `logId`(문자열) · `trigger`(`SCHEDULED`/`MANUAL`) · `operatorName`(수동 실행한 운영자). `source` 는 그 줄을 만든 쪽(`JAVA`/`POSTGRESQL`). `jobName` 은 키, `displayName` 은 배치 이름(카탈로그에서 사라진 배치는 키 그대로). `message` 는 성공/건너뜀이면 결과 요약, 실패면 사유. 상세는 거기에 `scheduledAt` · `summary` · `errorMessage` · `nodeId`(DB 배치 줄은 null).
- 상태 값: `RUNNING`(실행 중) · `OK` · `FAIL` · `SKIP`(처리 건수 0). 실패한 DB 배치 줄의 `affectedCount` 는 실패하기 전까지 처리한 건수일 수 있다(Java 배치는 null).

## 프론트 타입과의 차이 (연결 완료 — 아래는 연결 당시 기록이며 화면은 이미 반영했다)
1. `BatchStatus` 에 **`RUNNING`** 이 없다 — 서버는 실행 중인 줄과 마지막 실행 상태에 `RUNNING` 을 준다(`util-batch.ts`·`util-batch-messages.ts` 의 딱지와 필터에 추가).
2. 목업의 `jobName`(예: `audit-retention`)은 서버의 키(`audit-log-retention` …)와 다르다. **`source: 'POSTGRESQL'` 배치가 실제로 있다** — 목업의 「DB 가 돌립니다」 분기는 더는 「지금 실행 불가」가 아니다: DB 배치도 `runnable`(= `available`)이 true 면 **지금 실행·켜기/끄기·cron 수정이 모두 된다**(서버가 `cron.alter_job`·`CALL` 을 대신한다). `runnable=false`/`available=false` 일 때만 막고 `unavailableReason` 을 그대로 보인다.
3. 서버는 `i18n`/`descI18n` 을 null 로 준다(이름·설명이 이미 번역돼 있다). 목록 호출은 `Accept-Language` 를 따른다.
4. 새 칸(`nextRunAt`·`defaultSchedule`·`category`·`running`·`available`·`unavailableReason`·`scheduleZone`·`logId`·`trigger`·`operatorName`)과 cron 수정·로그 상세·기간/실행 방식 필터는 화면에 아직 없다. cron 입력칸을 만들 때 **source 별 문법 안내**(6칸 / 5칸)가 필요하고, 서버가 틀린 식에 `fields.schedule` 문구로 어느 문법인지 알려 준다.
5. 지금 실행은 **`batch.run`** 권한이고 켜기/끄기는 `batch.manage` 다(목업 주석의 「권한 구분 미정」이 정해졌다).

## DB 배치 (저장 프로시저 + pg_cron)

### 구조
- **실행 주체는 DB**(pg_cron)다. 서버를 몇 대 띄우든 DB 가 한 번만 돌린다 — 앱 락이 필요 없다. 관리자 서버가 내려가 있어도 돈다(공개 서버만 있는 설치에서도 돈다). 사용 여부·현재 cron 은 **`cron.job` 이 원천**이고(`batch_jobs` 에 두지 않는다 — 옮겨 적으면 어긋난다), 이름·설명·분류는 코드(`BatchCatalog` 의 `POSTGRESQL` 줄 + 메시지 번들 `batch.job.<키>.name/desc`)다. 기본 cron·프로시저 이름은 마이그레이션이 `batch_db_jobs` 표에 둔다.
- **로그는 같은 `batch_runs` 표**다(`source='POSTGRESQL'`). 프로시저가 직접 쓴다: 시작(`RUNNING`) → 끝(`OK`/`SKIP`/`FAIL`), 처리 건수, 요약(`message_key` + 인자 JSON — 서버가 요청 언어로 푼다), 실패 사유(SQLSTATE + 메시지 첫 줄, 이메일·password/token 값·`(칸)=(값)` 의 값을 가려 500자). 덩어리마다 COMMIT 하므로 시작 줄은 바로 보이고, 중간에 실패해도 그때까지 지운 건수와 사유가 남는다.
- **동시 실행 방지**: Java 배치와 같은 어드바이저리 잠금(`pg_try_advisory_lock(7340002, hashtext(키))`)을 프로시저가 쥔다 + `batch_runs` 유일 색인(RUNNING 하나·예약 시각마다 한 줄). pg_cron 이 도는 중에 「지금 실행」을 누르면 프로시저가 아무것도 하지 않고 돌아와 앱이 409 `BATCH_RUNNING` 을 준다. DB 가 죽어 남은 `RUNNING` 줄은 다음 실행 시작 때(또는 관리자 서버의 주기 점검이) `FAIL`(`batch.error.interrupted`)로 닫는다.
- **마이그레이션**(Flyway shared): `V20__db_batches.sql`(`batch_runs.source`, `batch_db_jobs`, `batch_params`, 색인 2개) + 반복 마이그레이션 `R__batch_00_helpers.sql`(공통 함수) · `R__batch_1x_<이름>.sql`(배치마다: 프로시저 + 설정 기본값 + `batch_define`). 반복 마이그레이션은 내용이 바뀌면 다시 적용되고 모두 멱등이다.

### 설치·설정
| 환경 | 이미지 | 설정 |
|---|---|---|
| local · dev compose | `docker/postgres/Dockerfile` (공식 `postgres:18` + `postgresql-18-cron`, 앱 compose 가 `build:` 로 만든다) | compose `command`: `shared_preload_libraries=pg_cron` · `cron.database_name=roboyam`(앱 DB 와 같아야 한다) · `cron.use_background_workers=on` · `cron.timezone=Asia/Seoul` |
| 설치형(`installer/`) | `installer/templates/postgres/Dockerfile`(위의 사본)을 설치기가 `<프로젝트>-postgres:18` 로 빌드. `make-package.sh` 가 패키지에 넣는다 | 위와 같은 `command` (`templates/compose.yml`) |
| 운영(RDS) | 해당 없음(RDS 는 compose 에 없다) | RDS 파라미터 그룹: `shared_preload_libraries` 에 `pg_cron` 추가(재부팅), `cron.database_name = roboyam`. **RDS 의 pg_cron 은 GMT 로 돈다** — 기본 cron 은 한국시간으로 적은 값을 GMT 로 환산해 등록한다(`batch_daily_cron`). 확장은 마스터 계정이 `CREATE EXTENSION pg_cron`. 앱 계정이 슈퍼유저가 아니면 `GRANT USAGE ON SCHEMA cron TO <앱 계정>` |
- 이유: `CREATE EXTENSION pg_cron` 은 슈퍼유저 권한이, 패키지·`shared_preload_libraries`·`cron.database_name` 은 서버 설치·재기동이 필요해 **앱이 대신 못 한다**. 그래서 이미지/서버 설정은 설치 쪽이 맡고, 앱(Flyway)은 가능한 만큼만 한다 — 확장이 없으면 만들 수 있을 때만 만들고(`batch_enable_pg_cron()`: 패키지·설정이 맞고 앱 계정이 슈퍼유저일 때) 아니면 건너뛴다. 처음 만드는 DB 는 이미지의 init 스크립트(`10-pg-cron.sh`)가 슈퍼유저로 `CREATE EXTENSION` 한다.
- **pg_cron 이 없어도 서버는 뜬다.** 마이그레이션은 확장 유무를 검사해(`batch_pg_cron_status()`) 등록을 건너뛰고, 관리자 서버는 기동 때 `batch_enable_pg_cron()`+`batch_register_missing()` 을 시도하다 실패해도 경고 로그만 남긴다. 그때 목록에서 DB 배치는 `available=false` + `unavailableReason`(`MISSING_PACKAGE` 서버에 패키지 없음 · `NOT_PRELOADED` 서버 설정에 없음 · `WRONG_DATABASE` `cron.database_name` 이 다른 DB · `NOT_CREATED` 확장 미생성(슈퍼유저가 해야 함) · `NO_PERMISSION` cron 스키마 권한 없음 · `UNREADABLE` · `NOT_REGISTERED` DB 에 미등록)이고, Java 배치는 영향이 없다. 프로시저 자체는 항상 만들어지므로 DBA 가 `CALL batch_…()` 로 직접 돌릴 수 있다.
- **pg_cron 을 나중에 도입한 설치**: 서버 설정+재기동+확장 생성(또는 앱 계정이 슈퍼유저면 생략) 뒤 관리자 서버를 한 번 재시작하면 기동 때 `batch_register_missing()` 이 빠진 배치를 기본 cron 으로 등록한다.
- **이미 설치된 환경(pg_cron 없음) 업그레이드**: ① 새 이미지를 만든다(local·dev: `docker compose up -d --build` — 볼륨은 그대로, PostgreSQL 18 호환). 설치형: 설치 폴더에서 `docker build -t <프로젝트>-postgres:18 <패키지>/templates/postgres`, `compose.yml` 의 postgres 에 `image: <프로젝트>-postgres:18`·`pull_policy: never`·위 `command` 를 넣고 `docker compose up -d postgres`. ② 앱을 새 버전으로 올린다 — Flyway 가 V20 과 R__ 를 적용하고 관리자 서버가 기동 때 확장을 만들고(`POSTGRES_USER` 가 슈퍼유저) 배치를 등록한다. 앱을 올린 뒤에 DB 를 바꿨다면 ②를 한 번 더(관리자 서버 재시작). 확장 생성이 막혀 있으면(슈퍼유저 아님): `psql -U <슈퍼유저> -d roboyam -c 'CREATE EXTENSION pg_cron'` 한 번.
- **cron 시간대를 나중에 바꾸면**(`cron.timezone`) 이미 등록된 cron 식은 옛 시간대 기준 숫자 그대로라 시각이 어긋난다 — 바꾸지 않는 것을 권한다. 바꿨다면 `cron.alter_job` 으로 식을 다시 맞춘다.

### 프로시저 규약 (새 DB 배치를 만들 때) — `.claude/guides/backend/db-batch.md`
1. `BatchCatalog` 에 `XXX("키", 분류, BatchSource.POSTGRESQL, null, true)` 한 줄 + 메시지 번들 두 곳(`batch.job.<키>.name/desc`, 결과 요약 `batch.result.…` + `….none`) — **카탈로그에 한 줄만 있고 SQL 이 없으면 목록에 「미등록」(`NOT_REGISTERED`)으로 보이고, SQL 만 있고 카탈로그에 없으면 목록에 나오지 않는다**(pg_cron 에는 남는다).
2. 반복 마이그레이션 `R__batch_<번호>_<이름>.sql`: 프로시저 `batch_<키의 하이픈을 밑줄로>(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)`(plpgsql). 본문 순서: `batch_run_begin`(NULL 이면 이미 도는 중 → RETURN) → `COMMIT` → 덩어리 루프(`BEGIN … EXCEPTION WHEN OTHERS` 안에서 일하고 블록을 나온 뒤 `COMMIT`; 실패는 `batch_error_text` 로 담아 루프 탈출) → `batch_run_close(…)` → `COMMIT`. 기존 `R__batch_1x` 파일을 복사해 시작한다. 설정값은 `batch_param_seed`(처음에만 넣음)+`batch_param`(읽기, 범위 밖이면 기본값). 끝에 `SELECT batch_define('키', '프로시저', 시, 분)` — 한국시간 시각을 pg_cron 시간대로 환산해 **pg_cron 에 없을 때만** 등록한다(있으면 운영자가 바꾼 cron·사용 여부를 건드리지 않는다).
3. 규칙: `EXCEPTION` 블록 안에서는 `COMMIT` 할 수 없다(블록을 닫은 뒤 한다). 대량 삭제는 덩어리(기본 5000)+한 번 실행 상한(기본 100000). 처리 건수 0 은 SKIP. 비밀값을 메시지에 싣지 않는다(`batch_error_text` 가 한 번 더 가린다). 이미 적용된 `V` 파일은 고치지 않는다 — 프로시저는 `R__` 를 고친다(체크섬이 바뀌면 다음 기동에 다시 적용).
4. **기본 cron 을 바꾸는 릴리스**: 이미 등록된 곳에는 반영되지 않는다(운영자 값 보존). 꼭 바꿔야 하면 새 `V` 마이그레이션에서 `cron.alter_job` 을 부르되 pg_cron 이 있을 때만(`batch_pg_cron_ready()`).

### cron 문법 차이
| | JAVA | POSTGRESQL (pg_cron) |
|---|---|---|
| 칸 | 6칸: 초 분 시 일 월 요일 | 5칸: 분 시 일 월 요일 (+ `N seconds` 형식) |
| 시간대 | `Asia/Seoul`(`roboyam.batch.zone`) | pg_cron 의 `cron.timezone` (우리 구성 `Asia/Seoul`, 기본 `GMT`) |
| 특수 | `?` `L` `W` `#` `@daily` 등 Spring 문법 | 일 칸 `$`(말일), 월·요일 영문 이름. `? L W #` `@daily` 는 **거절** |
| 간격 하한 | 앞으로 64번의 실행에서 인접 간격 ≥ 60초 | 같다. `N seconds`(1~59초)는 하한 때문에 거절 |
| 일·요일 동시 지정 | AND | pg_cron(Vixie)은 OR — `nextRunAt` 이 어긋날 수 있다(둘 다 지정하는 식은 쓰지 않는 것을 권한다) |
- 서버가 이미 정규화(공백 정리)해 `cron.alter_job` 에 넘기고, pg_cron 이 거절하면 같은 400 `fields.schedule` 로 답한다.

### 등록된 DB 배치 (정리 배치 — 안 쌓이게, 그리고 업무 배치 `grade-promotion`)
- **`grade-promotion` 「등급 일수 승급」(MEMBER, 매일 04:30 KST)** — 정리가 아니라 **업무 배치**다. 예전 Java 배치를 사용자 지시로 DB 배치로 옮겼다(키를 이어 써서 로그·화면 이름이 그대로). 규칙·설정값·잠금은 `roles-contract.md` 「배치 인프라 결과」, 구현은 `R__batch_15_grade_promotion.sql`. **같은 판정이 Java(`GradePromotionService.promoteAll`/`promoteOnEvent`, `GradeRepositoryQDSL#promotable`)에도 있어 둘은 같이 고친다**(서로를 가리키는 주석이 있다). pg_cron 이 없으면 이 배치가 돌지 않아 승급이 멈춘다 → 기동 때 WARN 로그(`pg_cron 이 없어 DB 업무 배치 'grade-promotion' 가 돌지 않는다`)와 목록의 「사용 불가」+이유. DBA 는 `CALL batch_grade_promotion();` 으로 직접 돌릴 수 있다. Java 폴백은 없다.
`docs/features/roles-contract.md` 「배치 인프라 결과」의 표를 본다. 설정값(`batch_params`)은 `UPDATE batch_params SET param_value = … WHERE job_key = … AND param_key = …` 로 바꾼다(화면은 후속). 범위를 벗어난 값은 무시하고 기본값을 쓴다.
- 운영 확인 쿼리: `SELECT jobname, schedule, active FROM cron.job;` · `SELECT * FROM cron.job_run_details ORDER BY start_time DESC LIMIT 20;`(pg_cron 자체 기록 — 프로시저 밖의 실패가 여기 있다) · `SELECT * FROM batch_runs WHERE source='POSTGRESQL' ORDER BY started_at DESC LIMIT 20;`

## 가정 / 미확정
- 등급 승급 시각(04:30 KST 하루 한 번)·cron 간격 하한 60초·`batch.run` 을 지금 실행에만 쓰는 것, **DB 배치의 보관 기간·시각(03:00~03:40 KST)·덩어리 5000·상한 100000(등급 승급은 상한 1000000)** 은 **가정**.
- 지금 실행은 요청 스레드에서 끝날 때까지 돈다 — 오래 걸리는 배치가 생기면 비동기(202 + 상태 조회)로 바꿔야 한다. DB 배치도 같다(한 번 실행 상한 100000건이라 지금은 짧다).
- 알림(배치 실패 알림)·실행 이력의 상세 처리 내역(건별)·감사 로그 조회 화면·`batch_params` 를 고치는 화면은 후속.
- 서버가 내려가 있던 동안 놓친 Java 배치 예약은 따라잡지 않는다(다음 시각부터). DB 배치는 DB 가 내려가 있던 동안 놓친 시각을 pg_cron 이 따라잡지 않는다.
- **정책이 없어 DB 배치로 만들지 않은 것**: 탈퇴 회원(`users.deleted_at`) 개인정보 파기, 이메일 인증을 끝내지 않은 가입 계정 정리 — 보관 기간 정책 확정 뒤에 추가한다. 세션 표(`spring_session`, `admin_spring_session`)는 Spring Session 이 매분 정리하므로 배치를 두지 않았다(아래 표).
