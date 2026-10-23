# 🏛️ Backend Core Guide (`_backend-core.md`)

## 1. 역할 및 기본 원칙
* 작업 전 빌드 설정·관련 코드·유사 기능을 확인하고 기존 구조를 따른다. 불필요한 계층·인터페이스·라이브러리를 추가하지 않는다.
* 스택: Spring Boot 4.1 · Java 25 · Gradle 멀티프로젝트(`server/shared`, `server/api` 8910, `server/admin` 8920) · JPA/QueryDSL/MapStruct/Flyway · PostgreSQL 18. **lombok 을 쓰지 않는다**(생성자 주입, 레코드 DTO, 직접 쓴 getter).
* 패키지: `com.community.<업무>/{controller,service,repository/master,entity,dto,exception}`, 공통 설정은 `com.community.zconfiguration/{jpa,mapper,exception,response,message,crypto,flyway,security,id}`. 두 앱이 같이 쓰는 코드는 `shared` 에 두고 `api` ↔ `admin` 은 서로 의존하지 않는다.

---

## 2. 주요 업무 및 규약

### 2-1. 계층별 책무 (상세 가이드)
* **Controller**: HTTP 수신·`@Valid`·Service 호출·응답 구성 (`controller.md`)
* **Service**: 업무 규칙·트랜잭션·데이터 조합 (`service.md`)
* **Repository**: 조회·저장 (`repository.md`)
* **Entity**: 상태와 불변 조건. `protected` 기본 생성자, 정적 팩터리·도메인 메서드로만 만들고 바꾼다(setter 금지). 시각은 `Instant`, 연관은 `LAZY`, `toString` 에 민감값을 싣지 않는다.
* **DTO**: Request/Response 분리 (`dto.md`) · **Mapper**: Entity → Response (`mapper.md`) · **예외·응답 문구** (`exception.md`)

### 2-2. 공통 규칙
* **ID**: Snowflake 를 앱이 발급한다(`SnowflakeIdGenerator`, `NODE_ID` 0~1023, 서버마다 다름). JSON 에서는 문자열이다.
* **보안**: 서버 세션(Spring Session JDBC, 쿠키 `SESSION`) · CSRF(`XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더) · 비밀번호 bcrypt(UTF-8 72바이트 초과는 입력 단계에서 `VALIDATION` 으로 거절 — `UserPolicy.passwordViolation`, 로그인은 예외 없이 불일치 처리) · 인증 없이 보호 API 를 부르면 JSON 401 · 설치 전 `/api/**` 는 503 `NOT_INSTALLED`.
* **로그인**: 실패 사유와 무관하게 항상 같은 `INVALID_CREDENTIALS`(없는 이메일도 해시 비교를 한 번 한다). 이메일 단위 5회 연속 실패 시 10분 잠금.
* **응답**: 모든 JSON 응답은 공통 래퍼 `ApiResponse`(`{success,data|error,meta}`)이고 요청마다 `X-Request-Id` 가 붙는다 (`controller.md`·`exception.md`).
* **비밀값**: 비밀번호·토큰·시크릿은 로그·오류·`toString` 에 남기지 않는다. DB 비밀번호는 `ENC(...)`(AES-256-GCM, 키 `ROBOYAM_CRYPTO_KEY`)로만 설정한다(local 만 평문 허용). 메일 비밀번호·소셜 시크릿도 같은 방식으로 암호화해 저장한다.
* **DB**: 스키마는 Flyway 만 만든다(`ddl-auto: none`). 스트림별 폴더 `db/migration/{shared,api,admin}` — `shared` 는 `flyway_schema_history`, 앱 스트림은 첫 파일에서 이력 표를 만든다. 번호는 스트림마다 연속·독립이고 적용된 파일은 고치지 않는다. **번호는 develop 에 합치는 시점에 매긴다** — 여러 세션·worktree 가 동시에 마이그레이션을 만들면 합치기 직전에 `ls` 로 마지막 번호를 다시 보고, 먼저 합쳐진 번호보다 작은 번호를 뒤늦게 합치지 않는다(shared 스트림은 `outOfOrder` 를 끄고 엄격 검증하므로 「적용되지 않은 앞 번호」가 생기면 서버가 뜨지 않는다). 아직 어느 영속 DB 에도 적용되지 않은 파일만 번호를 바꾸거나 지울 수 있고, 그 전에 개발 DB 의 `flyway_schema_history` 로 확인한다. 프로시저·함수·등록처럼 다시 만들어도 되는 것은 반복 마이그레이션 `R__<번호>_<이름>.sql`(내용이 바뀌면 다시 적용, 모두 멱등)에 둔다 — `db-batch.md`. 앱의 `roboyam.schema.stream` 이 자기 스트림을 정하고 api·admin 표는 서로 참조하지 않는다.
* **설정**: `roboyam-shared.yml`(+ `-local`)을 각 앱 `application.yml` 이 import 한다. 환경은 빌드가 아니라 실행 때 `SPRING_PROFILES_ACTIVE`(기본 local)로 고른다. 환경 변수: `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`(ENC)·`DB_POOL_SIZE`, `ROBOYAM_CRYPTO_KEY`, `ROBOYAM_INSTALL_TOKEN`, `NODE_ID`, `PUBLIC_URL`, `ADMIN_URL`, `ROBOYAM_PROXY_TRUSTED_HOPS`.
* **설치·배포**: 첫 기동은 설치 전 상태이고 브라우저 `/install` 에서 토큰(`ROBOYAM_INSTALL_TOKEN`, 없으면 기동 로그에 1회 출력, IP당 5회 실패 시 10분 잠금) → 루트 계정 → 사이트 → 메일(건너뛰기) → 소셜(건너뛰기) → 기본 게시판 구성 → 확인 → 설치. 설치가 끝나면 마법사 API 는 닫힌다. `installer/roboyam-install.sh` 가 비밀을 자동 생성해 DB 비밀번호를 `ENC(...)` 로 `.env`(권한 600)에 기록하며 실패하면 만든 것을 모두 지운다. 암호화 키를 잃으면 암호문을 풀 수 없으므로 따로 백업한다.
* **이미지·파일**: 올린 이미지는 `ImageStorage`(shared `com.community.media`, 지금은 로컬 디스크 `roboyam.storage.local-path`)에 두고 키에 버전 토큰을 넣어 내용이 바뀌면 주소가 바뀌게 한다. `/media/**` 는 api·admin 이 **같은 폴더를 둘 다 서빙**한다(admin 이 쓴다) — compose·설치기에서 같은 볼륨을 마운트한다. 이미지 처리(검사→다시 인코딩)는 shared 의 `ImageCodec`·`ImageGate`(동시 처리 제한) 아래에서만 하고(admin 사이트 이미지, api 글 이미지 `POST /api/images` — `board-public-contract.md` 「에디터·이미지」), 디코딩 실패는 500 이 아니라 `fields.file` 400 이다. 저장 순서는 검사·처리 → 저장소 쓰기 → DB 교체 트랜잭션 → 커밋 뒤 예전 파일 삭제(실패 시 새 파일 삭제). 구조는 `docs/features/site-settings-contract.md`.
* **배치**: 두 종류가 한 목록·한 로그 표(`batch_runs`)를 쓴다. 배치 정의의 원천은 코드(`BatchCatalog`)다. **JAVA** — 관리자 서버(`server/admin`, `com.community.batch`)가 예약 실행하고 사용 여부·cron 은 DB(`batch_jobs`)에 둔다. 새 배치는 `BatchJob` 구현(빈) 1개 + 카탈로그 1줄 + 메시지 번들(`batch.job.<키>.name/desc`, 결과 요약 키) 두 언어 — 스케줄·락·로그·실패 처리는 실행기(`BatchRunner`)가 하므로 배치마다 `@Scheduled`·`try-catch` 를 쓰지 않는다. **POSTGRESQL(DB 배치)** — 저장 프로시저를 pg_cron 이 부른다(정리·집계처럼 DB 안에서 끝나는 일). 새 배치는 카탈로그 1줄 + 메시지 번들 + 반복 마이그레이션 `R__batch_*.sql`(프로시저·등록)이고 `db-batch.md` 를 따른다. pg_cron 이 없어도 서버는 뜬다(DB 배치만 「사용 불가」). 구조·가정은 `docs/features/roles-contract.md` 「배치 인프라 결과」, `docs/features/batch-contract.md` 「DB 배치」.
* **감사 기록·내 정보**: 감사 기록은 업무 트랜잭션 안에서 `AuditLogService.record` 로 남기고(`AuditEvent` — `detail` 에 비밀번호·토큰·이메일 원문 금지), 바뀐 칸은 `before`/`after` 맵(또는 `from`/`to` 한 쌍)으로 담으면 조회가 「바뀐 내용」으로 옮긴다. **새 작업 이름 = `AuditActions` 상수 1개 + 번들 `audit.action.<코드>` 두 언어**(새 대상 종류는 `AuditLabels.TARGET_TYPES` + `audit.targetType.*`). 조회 `/api/admin/audit-logs`(`audit.read`)는 이메일을 내지 않고, 조회 자체는 기록하지 않는다. `/api/admin/profile` 은 본인 것만(`@AdminAuthenticated`, 경로에 ID 없음, 대상은 `AdminActor`)이고 개인 설정은 `operator_preferences`. 구조·가정은 `docs/features/roles-contract.md` 「4단계 구현 결과」.
* **관리자 알림**: 이벤트의 원천은 코드(`AdminNotificationEvent`)이고 새 이벤트 = enum 한 줄 + 번들 키 5개(ko·en) + 업무 서비스의 `publish` 한 줄이다. 발행은 업무 트랜잭션에 참여하고(롤백되면 알림도 없다) 실패해도 업무를 깨지 않으며(JDBC 세이브포인트), 이메일은 커밋 뒤, 배치 실패는 감시자(`batch_runs.notified_at`)가 낸다. 수신 규칙은 코드에 고정이다. 규약은 `notification.md`, 구조·가정은 `docs/features/roles-contract.md` 「7단계 구현 결과」.
* **로그인 전 공개 계정 API**(운영자 초대 수락·비밀번호 재설정 승인번호): `@AdminPublic` + `SecurityConfig` 허용 경로, 토큰·승인번호·비밀번호는 URL 이 아니라 본문으로 받고 해시로만 저장하며 로그·감사·`toString` 에 남기지 않는다. **계정 존재를 알리지 않는다** — 응답·작업량·요청 한도를 계정 유무와 무관하게 같게 하고(대상이 아니면 같은 모양의 「가짜 행」), 메일은 커밋 뒤 요청 스레드가 아닌 풀에서 보낸다. 오류로 끝나도 남아야 하는 시도 횟수는 `noRollbackFor`, 오류 응답에서도 남길 감사 기록은 `recordIsolated`. 구조·가정은 `docs/features/roles-contract.md` 「5단계 구현 결과」.
* **검증**: 개발 중에는 `./gradlew :server:api:compileJava :server:admin:compileJava` 같은 최소 검사만 한다. 컴파일 통과는 런타임 정상을 보증하지 않으므로 dev 서버에서 해당 API 를 한 번 실제로 호출한다. 전체 테스트·E2E·전체 빌드는 요청 시에만 실행한다.

---

## 3. 작성 예시
유형별 예시는 각 상세 가이드를 본다. 새 기능은 가장 가까운 기존 업무(`auth`·`installation`)의 구조를 따른다.

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ lombok 도입, 공통 응답 래퍼(`ApiResponse`) 밖의 JSON 본문 형태
2. ❌ 비밀번호·토큰·개인정보·세션 값을 로그·오류·`toString` 에 남기는 행위
3. ❌ 개발 중 전체 테스트·E2E 무단 실행, 적용된 마이그레이션 수정
