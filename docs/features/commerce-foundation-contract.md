# 커머스 0단계 공통 기반 계약 (ERD·패키지 제안 승인 2026-10-23 · 0-1 필드 암호화 구현 2026-10-23)

**의도**: 포인트 상점·결제·공구·충전(1~3단계)이 모두 기대는 공통 장치를 먼저 만든다 — 필드 암호화, 배치 보강, 관리자 2차 인증, 본인인증·기능 제한·탈퇴 대비, 돈 관련 요청 보호(멱등·속도 제한), 커머스 메일 대기열, 권한 코드. 정책 원천은 `docs/domain/commerce.md`(3·10-9·12·13·14·15·17·21절)이고 이 문서는 0단계에서 정한 구조와 결정만 적는다.

## 작업 단위와 순서
| 단위 | 내용 | 상태 |
|---|---|---|
| 0-1 | 필드 암호화 키링(`DataCipher`)·중복 확인 해시(`BlindIndex`), 설치 스크립트·compose 의 새 키 | **구현됨**(아래) |
| 0-2 | 배치 보강: 작업 core 를 shared `batch.core` 로, 실행 요청 표(「지금 실행」 비동기), 단계·건별 실패·체크포인트·재실행, 워커 기록 | **서버 구현·화면 연결됨**(아래) |
| 0-3 | 관리자 2차 인증(TOTP) | **화면 확정·서버 구현·화면 연결됨**(아래 「0-3」, 규칙 테스트 통과) |
| 0-4 | 본인인증·기능 제한·탈퇴 대비 표, 데이터 키 교체 배치 | **화면 확정·서버 구현·화면 연결됨**(아래 「0-4」, 규칙 테스트 408 통과) — 포트원 브라우저 SDK 는 사용자 승인 대기 |
| 0-5 | 멱등·속도 제한·요청 제한 설정, 커머스 메일 대기열, 권한 코드 11개, 감사 보관 분류(13-4) | 대기 — 0-2 의 워커 기록 필요 |
순서: 0-1 → (0-2 ∥ 0-3) → (0-4 ∥ 0-5). 병행은 세션마다 worktree, 마이그레이션 번호는 develop 에 합치는 순서로 매긴다(`_backend-core.md`).

## 마이그레이션 계획 (가번호 — 합칠 때 확정)
| 가번호 | 스트림 | 표 | 단위 |
|---|---|---|---|
| V61 (V57 은 develop 의 알림 작업이 먼저 씀) | shared | `batch_runs` 칸(run_mode·params·건수·resumed_from_run_id·checkpoint·request_id), `batch_run_steps`, `batch_run_failures`, `batch_run_requests`, `worker_heartbeats` | 0-2 |
| admin V3 | admin | `admin_security_settings`(1행·꺼짐), `operator_mfa`, `operator_mfa_recovery_codes`, `operator_trusted_devices` | 0-3 |
| 가번호 V62 | shared | `identity_verifications`, `identity_verification_attempts`, `member_restrictions`, `identity_withdrawals`, `withdrawn_member_contacts`, `portone_settings` | 0-4 |
| 가번호 | shared | `idempotency_requests`, `request_rate_counters`(UNLOGGED), `request_rate_limits`(기본 4행) | 0-5 |
| 가번호 | shared | `mail_outbox` | 0-5 |
칸·제약·색인의 상세는 승인한 제안(세션 보고, 노트 `commerce-stage0-erd-package-proposal.md`)을 따르고 구현 세션이 이 표에 결과를 덧붙인다.
- **번호(2026-10-23 정리)**: shared V57~V61 은 이미 쓰였다(V57~V60 알림 작업, V61 0-2). 0-4·0-5 는 가번호로 쓰고 **develop 에 먼저 합치는 쪽이 V62**, 나중 쪽은 합칠 때 다음 번호로 파일 이름을 바꾼다(이미 적용된 파일이 아니므로 고쳐도 된다).

## 세션 정리 결정 (제안 ⑥ — 모두 추천안으로 승인 2026-10-23)
1. 본인인증 표 이름은 `identity_verifications`(문서의 `user_identity` 는 V2 `user_identities`(소셜 계정 연결)와 헷갈려 바꿨다).
2. DI·CI 해시를 **둘 다** 저장하고 각각 유일 — 본인인증 PG 를 바꿔도 1인 1계정이 깨지지 않게. 재가입 제한 해시 종류도 `IDENTITY_DI`·`IDENTITY_CI` 로 나눈다.
3. 해시 키는 데이터 키와 따로(`ROBOYAM_INDEX_KEY`) — 바꿀 수 없는 키.
4. 관리자 OTP 비밀키는 **`ROBOYAM_CRYPTO_KEY`(`SecretEncryptor`)** 로 암호화 — 이 키는 dev·prod 에서 없으면 앱이 뜨지 않아 잠김 위험이 없다.
5. 2FA 표는 **admin 스트림**, 그 정리 배치도 admin 스트림 `R__`.
6. 배치 설정은 `roboyam.batch.enabled` 하나(스케줄러·요청 집기·워커) — 지금 `roboyam.batch.scheduler-enabled` 를 바꾼다(0-2).
7. 배치 「지금 실행」은 요청 표에 넣고 202 + 요청 번호, 화면은 상태를 다시 읽는다(0-2 — `batch-contract.md` 갱신).
8. 2FA 를 켠 뒤 들어온 운영자의 유예 시작 = 켠 시각과 계정을 만든 시각 중 늦은 쪽.
9. 포트원 설정(상점 ID·API 비밀키·본인인증 채널 키)은 관리자 화면의 DB 설정(소셜 로그인 설정처럼 `SecretEncryptor` 암호화). 0단계는 본인인증 칸만, 2a 에서 결제 칸을 더한다. 포트원/가짜 구현 선택은 배포 설정(프로퍼티), dev·prod 에서 가짜면 기동을 막는다.
10. 메일 대기열은 보낸 뒤 본문을 남기지 않고 「언제·종류·가린 주소」만 남긴다. 보관 기간 숫자는 0-5 에서 정한다(설정값).
11. 커머스 메일 종류는 알림 「문구 › 필수 안내 메일」 목록에 들어갈 자리로 본다(`message-template-contract.md` 확정본 — 겉모양은 메일 템플릿). 알림 문구 서버 단계 전까지 문구는 메시지 번들.
12. 새 권한의 역할 유형: `shop.*`·`point.adjust.large`·`point.refund.large`·`point.refund.approve` = 포인트·등급(`GRADE_POINT`), `member.withdrawn.contact` = 회원 조회(`MEMBER_VIEW`), `commerce.groupbuy.cancel`·`commerce.refund.large` = 공구 주문(`GB_ORDER`), `commerce.payment.read`·`commerce.reconcile.manage` = 공구 정산(`GB_SETTLE`). `docs/domain/operator-permissions.md` 유형 표도 같이 고친다(0-5).
13. 감사 보관 분류(13-4)는 0단계(0-5)에 넣는다.
14. 탈퇴 대비 두 표와 정리 배치는 지금 만들고(본인인증이 재인증 제한을 읽는다), 쓰는 코드는 탈퇴 기능 때.
15. OTP 등록 QR 코드: admin-web 에 QR 라이브러리를 더한다(0-3 에서 라이브러리·버전을 정해 의존성 추가).
16. 워커 멈춤 알람(19절)은 4단계 — 0단계는 `worker_heartbeats` 기록만.
17. 메일 발송 워커는 배치를 켠 관리자 서버에서만 돈다 — 관리자 서버가 꺼지면 메일이 늦어질 뿐 사라지지 않는다(받아들임).
18. **세션 정리 결정(0-4 ∥ 0-5, 2026-10-23)**: 새 권한 중 `member.withdrawn.contact` 는 **0-4** 가 서버 `Permission`·admin-web `util-permission-catalog.ts`·`operator-permissions.md` 유형 표에 더하고, 나머지 권한 10개와 감사 보관 분류(13-4, `AuditRetention`)는 **0-5** 가 맡는다. 0-4 의 새 감사 행위는 합칠 때 0-5 의 보관 분류표에 한 줄씩 넣는다(연락처 열람 = 개인정보 열람 분류). 겹치는 파일(`Permission`·`AuditActions`·`ErrorCode`·메시지 번들·`util-permission-catalog.ts`·`util-admin-menu.ts`)은 줄만 더한다.

## 0-1 구현 결과 (2026-10-23)
- **`DataCipher`**(shared `zconfiguration.crypto`): AES-256-GCM, 암호문 `d<버전>:` + base64(IV 12 ‖ 암호문 ‖ 태그 16), 용도 이름(`DataPurpose`, 소문자로 시작하는 소문자·숫자·`.`·`-` 2~60자)을 AAD 로 묶는다 — 다른 칸에 옮긴 암호문·다른 용도로는 풀리지 않는다. `encrypt`·`decrypt`·`isCurrent`(지금 키인가 — 교체 배치가 고를 때, DB 에서는 `NOT LIKE 'd<버전>:%'`)·`reencrypt`·`activeVersion`.
  - 키: `roboyam.data-crypto.key` = `ROBOYAM_DATA_KEY`(`<버전 1~999>:<base64 32바이트>`), `roboyam.data-crypto.previous-keys` = `ROBOYAM_DATA_KEY_PREVIOUS`(쉼표로 여럿, 풀기만). 키가 없으면 앱은 뜨고(WARN 로그) 쓰는 요청만 503 `DATA_KEY_MISSING`. 형식이 틀리거나, 예전 키만 있거나, 같은 버전에 다른 키가 둘이면 기동을 멈춘다. 암호문 버전의 키가 설정에 없으면 503 `DATA_KEY_MISSING`(버전만 ERROR 로그). 키·평문·암호문은 로그·오류에 싣지 않는다.
  - 키 교체 절차: 새 키를 버전을 올려 `ROBOYAM_DATA_KEY` 에, 옛 키를 `ROBOYAM_DATA_KEY_PREVIOUS` 에 두고 재기동 → 키 교체 배치(0-4)가 옛 암호문을 모두 새 키로 → 예전 키를 비운다.
- **`BlindIndex`**: HMAC-SHA256(용도 이름 ‖ NUL ‖ 값) 16진 64자, 키 `roboyam.data-crypto.index-key` = `ROBOYAM_INDEX_KEY`(base64 32바이트). 값 정규화는 부르는 쪽. 키가 없으면 503 `INDEX_KEY_MISSING`.
- 오류 코드 `DATA_KEY_MISSING`·`INDEX_KEY_MISSING`(503, 공통 `ErrorCode`) + 번들 ko·en.
- 설정: `roboyam-shared.yml` 에 자리, **local 만 개발용 고정 키**(`roboyam-shared-local.yml`, 저장소에 공개된 값 — 다른 환경에 쓰지 않는다).
- 설치·배포: `installer/roboyam-install.sh` 가 두 키를 만들어 `.env` 에 `ROBOYAM_DATA_KEY=1:<키>`·빈 `ROBOYAM_DATA_KEY_PREVIOUS`·`ROBOYAM_INDEX_KEY` 로 쓰고 백업 경고를 출력한다. `installer/templates/compose.yml`·`docker/dev`·`docker/prod`(앱·관리자) compose 와 `.env.example` 에 세 변수를 더했다. 새 키는 기존 `SecretCipherTool keygen`(base64 32바이트)으로 만들고 데이터 키는 앞에 `1:` 을 붙인다.
- 아직 쓰는 곳이 없다 — 첫 사용처는 0-4(탈퇴 회원 연락처·본인인증 해시)다.
- **테스트**(commerce.md 18절 — 규칙은 모두 테스트, 통과 필수): `server/shared/src/test/java/com/community/zconfiguration/crypto/` 의 `DataCipherTest`(24)·`BlindIndexTest`(8)·`DataCryptoSettingsTest`(3) 35개 통과(`./gradlew :server:shared:test --tests 'com.community.zconfiguration.crypto.*'`, 2026-10-23).
  | 규칙(10-9·결정 3) | 테스트 |
  |---|---|
  | 키 버전을 붙인 암호문, 풀면 원래 값, 같은 값도 매번 다른 암호문 | `DataCipherTest.암복호화` |
  | 용도가 다르거나 고친 암호문·다른 키는 풀리지 않는다, 설정 비밀값 `v1:` 과 섞이지 않는다 | `DataCipherTest.암복호화` |
  | 키 교체: 옛 키로 풀고 새 키로 잠근다, 지금 키 판정, 다시 암호화, 예전 키가 빠지면 503 | `DataCipherTest.키_교체` |
  | 키가 없으면 앱은 뜨고 그 요청만 503 `DATA_KEY_MISSING`·`INDEX_KEY_MISSING` | `DataCipherTest.키가_없을_때` · `BlindIndexTest` |
  | 잘못된 키 형식은 기동을 멈추고, 키·평문을 오류 문구에 싣지 않는다 | `DataCipherTest.잘못된_설정은_기동을_멈춘다` · `BlindIndexTest` |
  | 해시는 용도·값이 같을 때만 같고(HMAC-SHA256 용도 ‖ NUL ‖ 값), 정규화는 부르는 쪽 | `BlindIndexTest` |
  | local 만 개발용 키, 공통 설정(dev·prod)에는 기본값 없음, 세 키는 서로 다르다 | `DataCryptoSettingsTest` |
  - 테스트로 막지 않은 것: 설치 스크립트가 키를 만들어 `.env` 에 쓰는 것(셸 — `bash -n` 문법 검사만), compose 의 변수 전달(실행 확인 안 함).

## 0-2 구현 결과 (2026-10-23 — 화면 API 는 `batch-contract.md` 「0-2 보강」)
- **구조(15-2)**: shared `com.community.batch.core` — `BatchCatalog`·`BatchCategory`·`BatchDefinition`, `BatchJob`(`definition`·`steps`·`mainStep`·`params`·`resumable`·`validate`·`run(BatchContext)`), `BatchContext`·`BatchStep`(덩어리 처리 도우미 `chunks(ChunkSource, ChunkProcessor)`), `BatchRunner`, `BatchRunRecorder`·`BatchFailureRecorder`, `BatchLockService`, `BatchRegistry`, `BatchParameterValidator`, `BatchRequestQueue`(요청 넣기·`SKIP LOCKED` 집기·남은 요청 정리)·`BatchRequestExecutor`(집은 요청 실행), `DbBatchCaller`(DB 배치 `CALL`), `WorkerHeartbeats`, `BatchErrorText`·`BatchMessageCodec`. 작업 구현 `FileOrphanCleanupJob` 은 shared `com.community.media.batch`. admin `com.community.batch` 에는 화면(컨트롤러·DTO·`BatchQueryService`·`BatchAdminService`·cron·`DbBatchGateway`)과 「언제 돌릴지」(`BatchScheduler`·`BatchRequestPoller`·`BatchBootstrapRunner`)만. 의존은 admin → shared.
- core 의 기록은 **JDBC**(`JdbcTemplate` + 앱의 트랜잭션 관리자) — 덩어리 저장 확정과 단계 건수를 한 트랜잭션에, 실행 기록·실패 건은 따로 확정해야 하고, `SKIP LOCKED`·`ON CONFLICT`·부분 유일 색인을 쓰며, 별도 배치 서버로 뗄 때 JPA 구성 없이 돌게 하려고. 화면 조회는 읽기 전용 JPA 엔티티(`BatchRunStepEntity`·`BatchRunFailureEntity`·`BatchRunRequestEntity`) + QueryDSL(`BatchRunDetailRepositoryQDSL`).
- **마이그레이션 V61**(shared, 가번호 — V57~V60 은 develop 의 알림 작업이 먼저 써서 2026-10-23 develop 에 맞추며 옮김): `batch_runs` 칸 8개 + `ux_batch_runs_resumed`(RESUME 은 원래 실행당 한 줄), `batch_run_steps`(PK run_id·step_name, CASCADE), `batch_run_failures`(CASCADE, 미해결 색인), `batch_run_requests`(끝나지 않은 요청 배치당 하나 — 부분 유일 색인, 집기 색인), `worker_heartbeats`, `batch_db_jobs.cadence`. 반복 마이그레이션: `R__batch_00_helpers.sql` 에 `batch_hourly_cron`·`batch_default_cron`·`batch_define_hourly`(기존 함수의 인자·반환형은 그대로), `R__batch_14` 가 끝난 요청도 정리.
- **설정**: `roboyam.batch.enabled`(예약·요청 집기·워커, 옛 `scheduler-enabled` 대체) · `request-poll-millis`(2000) · `request-stale-seconds`(120) · `heartbeat-idle-seconds`(30), `spring.task.scheduling.pool.size: 4`. 작업별 `batch_params`: `chunk_size`(기본 500, 1~10000) · `failure_limit`(기본 100, 0~100000).
- **워커 기록**: 요청 집기(`batch-request-poller`)·배치 알림 감시(`batch-run-watcher`)가 `worker_heartbeats` 를 쓴다(일이 없으면 30초에 한 번). 알림 감시의 켜기는 그대로 `roboyam.notification.watcher-enabled` 다.
- **테스트**(commerce.md 18절): shared `com.community.batch.core.*` 32개 + admin `com.community.batch.*` 7개 통과(실제 PostgreSQL). 규칙 → 테스트: 덩어리 저장 확정·실패 덩어리만 한 건씩·성공 건 건별 기록 없음·실패 한도·건수 규칙·고를 수 없는 단계·실패 문구 가림 = `BatchChunkProcessingTest` / 실패 지점부터·한 번만 이어 받기·실패 건만 다시 = `BatchRerunTest` / 파라미터·단계 검사 = `BatchParameterValidatorTest` / 요청 하나·`SKIP LOCKED`·집을 때 실행 중이면 거절·남은 요청 정리 = `BatchRequestQueueTest` / 잠금 = `BatchLockTest` / 워커 기록 = `WorkerHeartbeatsTest` / 매시 도우미·로그 정리 CASCADE = `DbBatchHelpersTest` / 가림 = `BatchErrorTextTest` / `roboyam.batch.enabled` = admin `BatchEnabledSwitchTest` / 화면 API 한 바퀴 = admin `BatchScreenApiSmokeTest`.

## 시험 기반 (2026-10-23 — commerce.md 18절: 규칙은 모두 테스트, 통과 필수)
- **Testcontainers(PostgreSQL)는 커머스 테스트에만 쓴다**(사용자 승인 2026-10-23). 버전은 Spring Boot BOM(4.1.1 → Testcontainers 2.0.5), 시험 PC 에 Docker 가 있어야 한다.
- 공용 시험 DB: `server/shared/src/testFixtures/java/com/community/testsupport/CommerceTestDatabase.java`(Gradle `java-test-fixtures`).
  - shared 의 시험은 그대로 쓴다. api·admin 의 시험은 그 모듈 `build.gradle` 에 `testImplementation testFixtures(project(':server:shared'))` 를 더한다.
  - `CommerceTestDatabase.migrated("admin")` 이 컨테이너(`postgres:18`, 시험 JVM 하나에 하나)를 띄우고, 앱과 같은 길(`SchemaMigration.STANDARD`, shared → 자기 스트림)로 이관한 `DataSource` 를 준다(풀 없음 — 동시성 시험은 연결을 따로 잡는다). 스프링 문맥에는 `jdbcUrl()`·`username()`·`password()`.
  - DB 하나를 시험들이 같이 쓴다 — 자기가 만든 행만 보고(겹치지 않는 ID), 표 전체를 비우지 않는다. pg_cron 은 없다(DB 배치 프로시저는 `CALL` 로 직접 부른다).
- 확인 시험 `CommerceTestDatabaseTest` 5개 통과(실제 컨테이너): shared 스트림이 끝까지 이관, 포인트 원장 수정 금지 트리거(5-1·5-4), 잔액 음수 불가(5-4), 「사유 + 대상」 유일(3절), 연결 여러 개.
- 실행: `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew :server:<모듈>:test --tests '<패키지>.*'`(이 PC 의 셸에는 `JAVA_HOME` 이 없다).

## 0-3 관리자 2차 인증 (화면 확정 2026-10-23)
**의도**: 비밀번호가 맞아도 세션을 바로 만들지 않고 「2차 대기」로 둔 뒤 앱 OTP(TOTP)로 마저 확인한다. 정책은 `commerce.md` 13-3, 여기는 화면·API·데이터 규칙만.

### 화면 (admin-web — 실제 서버 연결 2026-10-23)
- `/login` 같은 카드 안 단계: 비밀번호 → **VERIFY**(6자리 OTP 또는 복구 코드 `XXXXX-XXXXX`, 「이 기기에서 N일 동안 다시 묻지 않기」) · **GRACE**(유예 마감·남은 날, [지금 등록]/[나중에 하기]) · **ENROLL**(QR + 비밀키 4자씩 + 확인 코드 → 복구 코드 10개, 복사·`.txt` 내려받기 `<사이트>-recovery-codes-YYYYMMDD.txt`, 「저장했어요」 체크해야 진행). 「다른 계정으로 로그인」 = 대기 취소.
- `/me?tab=security` 「로그인 보안」: 사이트 상태 안내, 내 OTP(등록·복구 코드 새로 만들기(현재 OTP)·다시 등록(비밀번호)), 신뢰 기기 표(이 기기 표시·끊기·모두 끊기).
- `/settings/login-security`(설정 › 시스템, **루트에게만 메뉴**): 상태·켠 시각·켠 사람, 등록 현황(전체·등록·유예 중·유예 지남, 미등록 최대 10명), 사용·유예 일수(1~90, 기본 14)·기기 신뢰 개월(1~12, 기본 1 = 30일)·OTP 실패 한도(3~10, 기본 5, 넘으면 10분 잠금). 루트 미등록이면 스위치 잠금. 켜기·끄기 확인창.
- 운영자 상세 「계정 상태」 탭 아래 「2차 인증」: 등록됨 / 미등록 / 유예 중 D-N / 유예 지남 / 사용 안 함, 신뢰 기기 수, 루트가 다른 운영자에게만 [OTP 초기화].
- 코드: `apps/admin-web/app/composables/mfa/**`, `components/mfa/**`, `components/profile/ProfileSecurityTab.vue`, `pages/settings/login-security.vue`, `pages/login.vue`, `utils/util-admin-menu.ts`(`superOnly`), QR 은 `uqr@0.1.3`(승인).

### 화면 확정 때 정한 것 (추천안 승인)
1. 유예 마감은 저장하지 않고 **매번 계산** — `max(켠 시각, 계정을 만든 시각) + 지금 유예 일수`. 켜진 채로 일수를 바꾸면 바로 반영된다.
2. 운영자 상세의 구획은 「계정 상태」 탭 아래.
3. 미등록 목록 → 운영자 화면은 목록만 연다(`?id=` 로 상세 바로 열기는 나중).
4. 루트 본인의 OTP 초기화는 화면에서 막는다(409 `MFA_RESET_SELF`) — 서버 접근자의 초기화 명령으로만.
5. 복구 코드 파일 이름 `<사이트 이름>-recovery-codes-YYYYMMDD.txt`.

### 상태 판정 (비밀번호가 맞은 뒤)
| 조건 | 결과 |
|---|---|
| 사이트 꺼짐(등록 여부 무관) | 바로 로그인(`mfa` 없음) |
| 켜짐 · 등록됨 · 유효한 신뢰 기기 쿠키 | 바로 로그인 |
| 켜짐 · 등록됨 | **VERIFY** 대기 |
| 켜짐 · 미등록 · 유예 안 | 로그인 + `mfa.stage = GRACE`(안내만) |
| 켜짐 · 미등록 · 유예 지남 | **ENROLL** 대기(등록해야 로그인) |
- 대기 세션: 옛 세션을 버리고 새 세션에 `{operatorId, stage, 만든 시각}` 만 둔다. 보안 컨텍스트가 없어 **로그인으로 취급되지 않는다**(`@AdminAuthenticated`·`@RequirePermission` 은 401). 유효 5분, 그 사이 계정이 로그인할 수 없게 되면 끝낸다. 통과하면 세션 ID 를 다시 바꿔 정상 로그인 세션을 만든다.
- 감사 `LOGIN_SUCCESS` 와 마지막 로그인 시각은 **세션이 완성될 때** 남긴다(`detail.mfa` = `OFF`·`TRUSTED_DEVICE`·`TOTP`·`RECOVERY_CODE`·`ENROLLED`·`GRACE`). 비밀번호 단계 성공은 비밀번호 실패 횟수만 지운다.

### API (공통 래퍼, 모두 `Cache-Control: no-store`, 쓰기는 CSRF)
| | 선언 | 요청 → 응답 |
|---|---|---|
| `POST /api/admin/auth/login` | 공개 | 응답에 `mfa: { stage: VERIFY\|ENROLL\|GRACE, graceEndsAt?, trustDays }`(없으면 null). VERIFY·ENROLL 이면 `authenticated:false` |
| `GET /api/admin/auth/me` | 공개 | 대기 세션이면 `authenticated:false` + 같은 `mfa`(새로고침해도 단계 유지) |
| `POST /api/admin/auth/mfa/verify` | `@AdminMfaPending`(VERIFY) | `{ code? \| recoveryCode?, rememberDevice }` → 200 me(+복구 코드면 `recoveryCodesLeft`) · 400 `MFA_CODE_INVALID`(+`remainingAttempts`) · 400 `MFA_CODE_REUSED` · 400 `RECOVERY_CODE_INVALID` · 429 `MFA_LOCKED`(+`retryAfterSeconds`) · 401 `MFA_PENDING_EXPIRED` |
| `POST /api/admin/auth/mfa/cancel` | 공개 | 204(대기 세션 버림) |
| `POST /api/admin/auth/mfa/enrollment` · `…/enrollment/confirm` | `@AdminMfaPending`(ENROLL) | 시작 → `{ secret, otpauthUri, issuer, accountName }` · 확정 `{ code }` → `{ recoveryCodes[10], me }`(로그인 완료) |
| `POST /api/admin/mfa/enrollment` · `…/enrollment/confirm` | 로그인 | 자발 등록(이미 등록 409 `MFA_ALREADY_ENROLLED`) · 확정 → `{ recoveryCodes[10] }` · 400 `MFA_ENROLLMENT_NOT_STARTED` |
| `GET /api/admin/mfa/me` | 로그인 | `{ siteEnabled, enrolled, enrolledAt, lastUsedAt, recoveryCodesLeft, graceEndsAt, trustDays }` |
| `POST /api/admin/mfa/recovery-codes` | 로그인 | `{ code }`(현재 OTP) → `{ recoveryCodes[10] }`, 옛 코드 모두 무효 |
| `POST /api/admin/mfa/reenroll` | 로그인 | `{ password }` → 등록 시작과 같은 응답 · 400 `INVALID_CURRENT_PASSWORD`. 확정하면 옛 비밀키·복구 코드·**신뢰 기기 모두** 무효 |
| `GET /api/admin/mfa/trusted-devices` · `DELETE …/{id}` · `DELETE …` | 로그인 | `[{ id, agent, ip, trustedAt, expiresAt, lastUsedAt, current }]` · 204 · 404 `MFA_DEVICE_NOT_FOUND` |
| `GET` · `PUT /api/admin/login-security` | 로그인 + **루트만**(권한 코드 없음) | `{ enabled, enabledAt, enabledBy, graceDays, trustMonths, maxFailures, rootEnrolled, summary }` · PUT `{ enabled, graceDays, trustMonths, maxFailures }` · 403 `PERMISSION_DENIED` · 409 `MFA_ROOT_NOT_ENROLLED` · 400 `VALIDATION` |
| `GET /api/admin/operators/{id}/mfa` | `operator.read` | `{ siteEnabled, enrolled, enrolledAt, graceEndsAt, graceExpired, trustedDevices }` |
| `DELETE /api/admin/operators/{id}/mfa` | 로그인 + **루트만** | 204 · 409 `MFA_RESET_SELF` · 404 |

### 데이터·보안 규칙
- **TOTP**: RFC 6238, HMAC-SHA1, 6자리, 30초, 앞뒤 1칸 허용. 받아들인 시간 칸을 저장하고 **그 칸 이하의 코드는 거절**(`MFA_CODE_REUSED`) — 같은 코드 재사용·옛 코드 방지. 비밀키 20바이트 무작위(base32), **`SecretEncryptor`(ROBOYAM_CRYPTO_KEY)로 암호화**해 저장(키 없으면 503 `CRYPTO_KEY_MISSING`). 등록 중인 비밀키도 암호화해 DB 에 두고 10분 지나면 버린다. issuer = 사이트 이름, 계정 = 운영자 이메일.
- **OTP 실패 제한**: `login_attempts` 의 키 `sha256("mfa:" + 운영자 ID)`, 한도 = 설정 `max_failures`, 잠금 10분(관리자 로그인 잠금 시간과 같은 값). 대기 세션의 verify·강제 등록 확정과 로그인 세션의 복구 코드 재발급·자발 등록 확정이 같은 키를 쓴다. 성공하면 지운다.
- **복구 코드**: 10개, `XXXXX-XXXXX`(혼동 글자 뺀 대문자·숫자), 대소문자·하이픈 무시. SHA-256(운영자 ID ‖ 코드)만 저장, (운영자, 해시) 유일, 쓰면 `used_at` — 한 번만.
- **신뢰 기기**: 쿠키 `ADMIN_MFA_TRUST`(256비트 무작위, HttpOnly·SameSite=Strict·https 면 Secure·path `/api/admin`, 만료 = 신뢰 일수). DB 에는 해시만(유일), 기간 = 설정 개월 × 30일(1~12개월). 다른 운영자의 기기 기록으로는 통과하지 않는다. **비밀번호 변경·재설정, OTP 재등록, 계정 잠금(상태 잠김), 루트의 OTP 초기화** 때 그 운영자의 신뢰 기기를 모두 지운다.
- **설정**: `admin_security_settings` 1행(CHECK id = 1), 설치 직후 **꺼짐**·14일·1개월·5회. 루트만 바꾸고 켜려면 루트 본인이 등록돼 있어야 한다. 켜면 `mfa_enabled_at` = 지금(유예 시작). 바꾼 칸만 감사 `MFA_SETTINGS_UPDATE` `{before, after}`, **끄면 운영자 알림(심각도 높음)**.
- **감사**: `MFA_ENROLL`·`MFA_REENROLL`·`MFA_RECOVERY_CODES_REGENERATE`·`MFA_FAILURE`(사유)·`MFA_RECOVERY_CODE_USED`·`MFA_TRUSTED_DEVICE_REVOKE`·`MFA_RESET`(via `ADMIN`·`SERVER_COMMAND`)·`MFA_SETTINGS_UPDATE`. 비밀키·코드·복구 코드·쿠키 값은 감사·로그·오류에 없다.
- **서버 초기화 명령**: admin V3 의 DB 함수 `admin_mfa_reset(이메일)`(그 운영자의 OTP·복구 코드·신뢰 기기 삭제 + 감사 `SYSTEM` `MFA_RESET` via `SERVER_COMMAND`)을 설치 폴더의 `installer/roboyam-mfa-reset.sh <이메일>` 이 DB 컨테이너의 psql 로 부른다.
- **표(admin V3)**: `admin_security_settings` · `operator_mfa`(운영자당 1행: 비밀키 암호문·등록 시각·마지막 시간 칸·마지막 사용·등록 중 비밀키 암호문·시작 시각) · `operator_mfa_recovery_codes` · `operator_trusted_devices`. 운영자가 지워지면 연쇄 삭제. 만료 신뢰 기기·버려진 등록 중 비밀키 정리는 admin 스트림 `R__` DB 배치.

### 0-3 서버 구현 결과 (2026-10-23)
- **코드**: `server/admin/.../mfa/**`(`totp/Totp`·`Base32` 직접 구현, `MfaStore` JDBC, 서비스 `MfaPolicy`(판정·유예)·`MfaVerifyService`·`MfaEnrollmentService`·`MfaAccountService`·`TrustedDeviceService`·`LoginSecurityService`·`OperatorMfaService`, 컨트롤러 4개, 쿠키 `MfaTrustCookie`), `permission/annotation/AdminMfaPending`(인터셉터 Kind `MFA_PENDING`, 컨트롤러가 `PendingMfa` 를 받는다), `adminauth/controller/AdminLoginFinisher`(비밀번호 뒤 판정 → 대기 또는 로그인 완성 — 로그인과 **비밀번호 재설정이 같이 쓴다**). 마이그레이션 **admin `V3__admin_mfa.sql`**(표 4개 + 함수 `admin_mfa_reset(text)` → `RESET`·`NOT_FOUND`, 감사 ID 는 서버 번호 1022) + admin `R__batch_30_admin_mfa_cleanup.sql`(`admin-mfa-cleanup`, 매일 04:10). 설치 `installer/roboyam-mfa-reset.sh [--dir] [--project] <이메일>`(종료 0 초기화 · 3 운영자 없음 · 1 오류 · 2 사용법, 이메일은 `psql -v` 변수, 패키지에 포함).
- **계약에 더한 것**
  - 오류: 409 `MFA_NOT_ENROLLED`(미등록이 복구 코드 재발급). 형식 오류(숫자 6자리 아님·복구 코드 형식 아님·둘 다/둘 다 없음)는 400 `VALIDATION` `fields.code`·`fields.recoveryCode` 이고 실패 횟수에 세지 않는다. `MFA_CODE_REUSED` 도 세지 않는다(감사만). 한도에 닿은 **그 요청부터** 429 `MFA_LOCKED`. `remainingAttempts` 는 공통 오류 본문 최상위 칸(`ErrorResponse`).
  - 대기 단계가 다른 API(VERIFY 대기로 ENROLL API)·대기 중 OTP 초기화 → 401 `MFA_PENDING_EXPIRED`.
  - 비밀번호 재설정 응답도 `authenticated:false` + VERIFY/ENROLL 일 수 있다(재설정이 신뢰 기기를 끊으므로 대개 VERIFY).
  - 등록 확정에 쓴 시간 칸을 마지막 칸으로 저장 — 같은 코드로 곧바로 로그인할 수 없다. 미등록이 「다시 등록」을 부르면 일반 등록 시작.
  - 알림: 새 수신 규칙 `ALL_OPERATORS`(활성 운영자 전원). `MFA_DISABLED` SECURITY·CRITICAL·이메일(끈 사람도 받는다 — 루트 세션을 남이 쓴 경우 알아채게), `MFA_ENABLED` SECURITY·INFO(켠 사람 제외), 링크 `/me?tab=security`.
  - **「2차 인증 꺼짐」 알림은 「팀에만 보내기」 불가**(2026-10-23 사용자 확정 — 알림 「팀에만 보내기」와의 세션 정리 결정): 누군가 2차 인증을 몰래 껐을 때 모든 운영자가 바로 알아채야 하는 보안 경보라, 받는 범위를 일부 팀으로 줄이면 경보가 닿지 않는 운영자가 생긴다. 400 `fields.defaultRecipients` + 이유 문구, 이벤트 관리 화면은 받는 범위를 잠그고 「보안 경보 — 늘 운영자 전원」 + 이유 툴팁. 「켜짐」 알림은 막지 않는다. 상세는 `notification-contract.md` 「팀에만 보내기」의 예외.
  - 감사 대상 `LOGIN_SECURITY`/`1`. 신뢰 기기 끊기 `cause` = SELF·PASSWORD_CHANGE·PASSWORD_RESET·REENROLL·ACCOUNT_LOCKED. 루트의 화면 초기화는 그 운영자의 `mfa:` 실패 기록도 지운다. 미등록 운영자 초기화도 `RESET` + 감사(`wasEnrolled:false`).
  - 메뉴: `AdminScreenRegistry.Screen` 에 `superOnly`. 메뉴 구성 API 는 원래 화면 권한을 내리지 않아 응답에는 없다 — 셸·메뉴 관리 표시는 admin-web `util-admin-menu.ts` 의 `superOnly`(메뉴 관리 화면은 「최고 관리자만」으로 보인다).
  - 쿠키 `ADMIN_MFA_TRUST` 의 Secure 는 `request.isSecure()` 를 따른다. 저장 시각은 마이크로초로 자른다(DB 반올림 때문에 10분 경계가 흔들렸다).

### 0-3 규칙 → 테스트 (server/admin/src/test, 168개 통과)
| 규칙 | 테스트 |
|---|---|
| TOTP RFC 6238 부록 B 벡터·30초 칸, ±1칸 허용·2칸 거절, 같은 코드·옛 칸 재사용 거절 | `TotpTest`, `MfaVerifyTest.OTP`, `MfaEnrollmentTest`(확정 코드 재사용) |
| base32, 비밀키 20바이트·**`SecretEncryptor` 암호화 저장(DB 에 평문 없음)**·키 없으면 503, otpauth 주소 | `Base32Test`, `MfaSecretsTest`, `MfaEnrollmentTest.등록` |
| 복구 코드 10개·형식·혼동 글자 없음·대소문자/하이픈 무시·해시만·**한 번만**·다른 운영자 불통 | `RecoveryCodesTest`, `MfaVerifyTest.복구_코드` |
| 유예 = max(켠 시각, 계정 생성) + 일수, 일수 변경 즉시 반영, 경계, 꺼지면 없음 | `MfaPolicyTest.유예_마감` |
| 상태 판정 표 5줄(꺼짐이면 등록자도 안 물음·켜면 **전원 필수**) | `MfaPolicyTest.상태_판정_표`, `MfaLoginFlowTest.상태_판정_표`(실제 로그인) |
| 대기 세션은 로그인 아님·`@AdminMfaPending` 만 통과·5분·대기 중 잠기면 끝·통과 시 세션 ID 교체·me 새로고침 유지 | `MfaLoginFlowTest.대기_세션`, `MfaHttpTest`(실제 보안 필터 체인), `AdminMfaPendingDeclarationTest` |
| `LOGIN_SUCCESS`·마지막 로그인은 세션 완성 때(`detail.mfa` 6종) | `MfaLoginFlowTest`, `MfaVerifyTest` |
| OTP 실패 한도·10분 잠금·`mfa:` 키·성공 시 지움 | `MfaVerifyTest.실패_제한`, `MfaHttpTest` |
| 기기 신뢰 1~12개월 × 30일·**기본 1개월**·만료·다른 운영자 불통·쿠키 속성 | `MfaVerifyTest.신뢰_기기`, `MfaLoginFlowTest`, `MfaPolicyTest` |
| **비밀번호 변경·재설정·OTP 재등록·계정 잠금·루트 초기화 때 신뢰 기기 모두 끊기** | `MfaRevocationTest.신뢰_기기_끊기`, `MfaLoginFlowTest`(재설정), `MfaEnrollmentTest.다시_등록` |
| 다시 등록(비밀번호 확인, 확정 전 옛 키 유효, 확정 후 옛 키·코드·기기 무효)·등록 중 10분·이미 등록 409·복구 코드 재발급 | `MfaEnrollmentTest` |
| **루트만** 설정·**켜려면 루트 본인 등록 먼저**(409)·켜면 유예 시작·바꾼 칸만 감사·범위 검사 | `LoginSecurityTest` |
| **끌 때 감사 + 심각도 높은 알림**, 켤 때 등록 안내 | `LoginSecurityTest.끄기`·`켜기`, `MfaCatalogTest` |
| 꺼짐 알림은 「팀에만」 불가(400 + 이유, DB 값이 false 여도 전원에게), 켜짐 알림은 막지 않음, 이유 문구 두 언어 | `MfaDisabledAlertScopeTest`, `MfaCatalogTest.팀에만_보내기를_못_하는…` |
| **루트가 다른 운영자 OTP 초기화**(본인 409·비루트 403)·운영자 상세 조회 | `MfaRevocationTest` |
| **설치 직후 꺼짐**·설정 1행·범위 CHECK·해시 유일·연쇄 삭제 | `MfaSchemaTest`(실제 PostgreSQL) |
| 서버 초기화 명령 `admin_mfa_reset`(감사 SYSTEM·주입 불가)·정리 배치 | `MfaSchemaTest` |
| 비밀키·코드·복구 코드·쿠키 값이 감사에 없음, 감사 행위·문구·배치 카탈로그 | `MfaEnrollmentTest`, `MfaCatalogTest` |
| 메뉴: 소셜 로그인 다음·루트 전용 | `AdminScreenRegistryLoginSecurityTest` |
| 같은 TOTP·같은 복구 코드를 **동시에** 내면 하나만 통과(서비스 행 잠금 + 저장소 조건부 UPDATE 를 연결 여럿으로) | `MfaConcurrencyTest`(실제 PostgreSQL) |
| 비밀키·OTP·복구 코드·쿠키 값·암호문·비밀번호가 **서버 로그에 없음**, DTO `toString` 감춤 | `MfaLogSecrecyTest`(실제 필터 체인 흐름 + 출력 캡처) |
| 신뢰 기기 쿠키: https 면 Secure·HttpOnly·SameSite=Strict·path `/api/admin`·30일, 이 브라우저 기기/모두 끊으면 쿠키 지움, 다른 기기는 그대로 | `MfaTrustCookieHttpTest` |
- 실행: `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew :server:admin:test --rerun --tests 'com.community.mfa.*' --tests 'com.community.permission.config.AdminMfaPendingDeclarationTest' --tests 'com.community.menu.service.AdminScreenRegistryLoginSecurityTest'` → **168 통과·0 실패**(develop b9df197 합친 뒤).
- **테스트로 막지 못한 것**: 설치 스크립트의 실제 설치 폴더 동작(셸 — `bash -n` + 임시 postgres 에서 psql 부분만 손으로 확인), pg_cron 등록(시험 DB 에 없음 — 정의와 `CALL` 결과만), 운영 세션 쿠키 이름 `ADMIN_SESSION`(목 서블릿은 `SESSION`), 알림 메일·Slack 실제 발송(서랍 알림 행까지만), 운영 서버의 접근·프록시 로그.

## 0-4 본인인증·기능 제한·탈퇴 대비 (화면 확정 2026-10-24)
**의도**: 현금이 오가는 기능(충전·공구·유상 환불·가입 포인트) 앞에 본인인증(1인 1계정·만 19세)을 두고, 계정 정지와 따로 특정 기능만 막는 「기능 제한」과 탈퇴 대비 표(재가입 제한 해시·연락처 분리 보관)를 만든다. 정책 원천은 `commerce.md` 12-1·12-3·17-2·17-3·10-9, 여기는 화면·API·데이터 규칙만.

### 화면
- **user-web** `/settings/identity`(설정 › 본인인증): 미인증(용도·저장하는 것 안내, [휴대폰 본인인증 하기]) / 인증됨(인증 일시, 「만 19세 이상·미만」만 — 생년월일 전체는 안 보임, 다시 인증 없음) / 미성년(막는 안내 + `adultFrom`). 실패 안내: 이미 다른 계정·탈퇴 후 30일(`retryAfter`)·업체 오류·시간 지남, 창 닫기는 조용히.
  - 결제 직전 확인 `useIdentityGate().ensure({ purpose: CHARGE|GROUP_BUY, requireAdult })`(대화상자는 `layouts/default.vue` 에 하나). 충전·공구 화면이 없어 시연은 `/dev/identity`(개발 모드만).
  - 가짜 구현이면 「가짜 본인인증(개발용)」 대화상자(생년월일·사람 번호 — 같은 번호 = 같은 사람).
  - 기능 제한 안내 부품 `FeatureRestrictionNotice`(403 `MEMBER_RESTRICTED` 의 `restriction`).
- **admin-web 회원 상세 서랍**: 「본인 인증」 구획(인증 여부·시각·만 19세 이상·수단, 「준비 중」 뗌), 새 구획 「기능 제한」(「제재 이력」 앞, 진행 중 / 지난 제한, [제한 걸기]·[해제] = `sanction.manage`, 탈퇴 회원은 부여 단추 없음), 탈퇴 회원 기본 정보 아래 「연락용 이메일 · 분리 보관」(열람 = `member.withdrawn.contact` + 사유, 10분 뒤 다시 가림, 열람 기록).
- **admin-web `/settings/portone`**(설정 › 시스템, 소셜 로그인 다음, 메뉴 `site.read`): 지금 구현(포트원/가짜, 배포 설정이라 읽기만), 「본인인증」 카드(상점 ID·API 비밀키·본인인증 채널 키 — **값은 안 보이고 설정 여부·시각·바꾼 사람만**), 저장·지우기 `site.secret`, 암호화 키 없음 경고.

### 화면 확정 때 정한 것 (추천안 승인 2026-10-24)
1. 같은 종류 기능 제한이 겹쳐도 막지 않는다(경고만) — 늦게 끝나는 쪽까지 막힌다.
2. 해제 사유 필수(5~200자) — `member_restrictions.lift_reason` 칸을 더한다.
3. 탈퇴 회원에게는 기능 제한을 걸 수 없다(화면은 단추 없음, 서버 409 `MEMBER_WITHDRAWN`).
4. 탈퇴 연락처를 다시 가리는 시간은 10분 고정(배송지 열람 설정값은 2단계).
5. 포트원 화면은 `/settings/portone` 따로(0-5 의 `/settings/commerce` 와 파일이 겹치지 않게). 메뉴를 0-5 의 「커머스」 묶음으로 옮길지는 develop 에 합칠 때 정한다.

### API (공통 래퍼, 오류의 추가 값은 오류 본문 최상위 칸)
| | 선언 | 요청 → 응답 |
|---|---|---|
| `GET /api/identity-verifications/me` | 회원 로그인 | `{ verified, verifiedAt, adult, adultFrom }`(미인증이면 `verified:false`, 나머지 null) |
| `POST /api/identity-verifications` | 회원 로그인 | → `{ verificationId, provider: PORTONE\|FAKE, storeId?, channelKey? }` · 409 `IDENTITY_ALREADY_VERIFIED` · 503 `IDENTITY_NOT_CONFIGURED`(포트원인데 설정 칸이 빔) |
| `POST /api/identity-verifications/{id}/confirm` | 회원 로그인 | 본문 `{ fake?: { birthDate, personKey } }`(가짜 구현에서만 읽음) → me 와 같은 모양 · 404 `IDENTITY_VERIFICATION_NOT_FOUND`(남의 번호 포함) · 410 `IDENTITY_VERIFICATION_EXPIRED`(발급 30분) · 409 `IDENTITY_ALREADY_LINKED` · 409 `IDENTITY_REVERIFY_BLOCKED`(+`retryAfter` YYYY-MM-DD, 서비스 시간대) · 409 `IDENTITY_ALREADY_VERIFIED` · 502 `IDENTITY_PROVIDER_FAILED` · 400 `VALIDATION` |
| `GET /api/admin/members/{memberId}` | `member.read` | 응답에 `identity: { verified, verifiedAt, adult, provider } \| null`(탈퇴 회원 null) |
| `GET /api/admin/members/{memberId}/restrictions` | `member.read` | `[{ id, type, status: ACTIVE\|EXPIRED\|LIFTED, source: AUTO\|OPERATOR, autoRule, reason, adminMemo, startsAt, until, createdBy{id,name}?, createdAt, liftedBy?, liftedAt, liftReason }]` 진행 중 먼저 |
| `POST …/restrictions` | `sanction.manage` | `{ type, until\|null, reason(5~200), adminMemo(~1000) }` → 만든 한 건 · 409 `MEMBER_WITHDRAWN` · 400 `VALIDATION`(until 이 지금 이전 등) |
| `POST …/restrictions/{id}/lift` | `sanction.manage` | `{ reason(5~200) }` → 한 건 · 404 `RESTRICTION_NOT_FOUND` · 409 `RESTRICTION_NOT_ACTIVE` |
| `GET /api/admin/members/{memberId}/withdrawn-contact` | `member.read` | `{ stored, withdrawnAt, purgeAfter, reveals: [{ operatorName, revealedAt, reason }] }`(이메일 없음) |
| `POST …/withdrawn-contact/reveal` | `member.withdrawn.contact` | `{ reason(5~200) }` → `{ email, maskAt }`(`no-store`) · 404 `WITHDRAWN_CONTACT_NOT_FOUND` |
| `GET /api/admin/portone-settings` | `site.read` | `{ identityProvider, cryptoKeyConfigured, identityReady, fields: [{ key: storeId\|apiSecret\|identityChannelKey, configured, updatedAt, updatedBy }] }` — 값은 없다 |
| `PUT /api/admin/portone-settings/identity` | `site.secret` | `{ storeId?, apiSecret?, identityChannelKey? }`(보낸 칸만) → GET 과 같은 모양 · 503 `CRYPTO_KEY_MISSING` · 400 `VALIDATION` |
| `DELETE /api/admin/portone-settings/identity/{key}` | `site.secret` | 204 · 404 `PORTONE_FIELD_NOT_FOUND` |

### 데이터·업무 규칙
- **본인인증(12-1, 결정 1·2·3)**: 서버가 인증 번호를 먼저 발급(`identity_verification_attempts`, 회원·구현·상태·만든 시각)하고, 확정 때 그 번호로 업체 결과를 조회한다(포트원 `GET /identity-verifications/{id}`, 상태 VERIFIED 만). DI·CI 는 `BlindIndex`(용도 `identity.di`·`identity.ci`)로 해시만 저장하고 **각각 유일**, 원문·이름·휴대폰 번호는 저장하지 않는다. `provider_tx_id` 유일(같은 인증 결과 재사용 불가). 다른 회원에 묶인 DI 또는 CI 면 409 `IDENTITY_ALREADY_LINKED`(동시에 들어와도 유일 제약이 막는다).
- **재인증 30일 제한(12-1·17-2)**: `identity_withdrawals` 에 같은 종류(`IDENTITY_DI`·`IDENTITY_CI`)·해시가 `expires_at > 지금` 으로 있으면 409 `IDENTITY_REVERIFY_BLOCKED` + `retryAfter`. 기간은 설정값(기본 30일, 탈퇴 때 `expires_at` 으로 박는다). 기록 API(`IdentityWithdrawalRecorder`)는 지금 만들고 부르는 곳은 탈퇴 기능 때(결정 14).
- **만 19세(12-1)**: 서비스 시간대의 오늘 기준 만 나이. `adultFrom` = 19번째 생일(2월 29일생은 평년에 3월 1일 — 민법 기간 계산). `requireAdult(userId)` → 미인증 403 `IDENTITY_REQUIRED`, 미성년 403 `IDENTITY_UNDERAGE`(+`adultFrom`). 생일이 지나면 다시 인증하지 않아도 통과.
- **구현 선택(결정 9)**: `roboyam.identity.provider` = `portone` | `fake`(공통 설정, local 기본 fake). **dev·prod 프로필에서 fake 면 기동을 멈춘다.** 포트원 설정은 `portone_settings`(키별 `SecretEncryptor` 암호문·바꾼 시각·바꾼 사람), 감사에 값 없이 칸 이름만.
- **기능 제한(12-3)**: `member_restrictions`(+ `lift_reason`, `auto_rule`). 진행 중 = 해제 안 됨 ∧ 시작 ≤ 지금 ∧ (끝 없음 ∨ 끝 > 지금). 막는 것: `POINT_FREEZE` = 포인트 적립·사용·충전(충전 포인트 환불 신청은 허용), `GROUP_BUY_BAN` = 새 공구 참여, `CHARGE_BAN` = 새 충전. `MemberRestrictionService.assertAllowed(userId, 기능)` → 403 `MEMBER_RESTRICTED` + `restriction{type, until(같은 종류 중 가장 늦은 끝, 무기한이 있으면 null), reason}`(운영 메모 없음). 지금 있는 포인트 적립은 동결이면 **조용히 건너뛴다**(글쓰기는 성공). 자동 부여 `grantAuto(userId, GROUP_BUY_BAN, 24시간, GROUP_BUY_HOLD_EXPIRED)` — 같은 종류가 그보다 늦게까지 진행 중이면 만들지 않는다. 해제는 조건부 UPDATE(이미 해제·만료면 409). 부여·해제 모두 감사, 부여 때 회원 알림(종류·끝·공개 사유 — 운영 메모 없음). `hasActive(userId)` 는 탈퇴 전 검증(17-1)이 쓴다.
- **탈퇴 연락처(17-2)**: `withdrawn_member_contacts`(회원 번호 PK, 이메일 `DataCipher` 암호문 — 용도 `member.withdrawn-contact.email`, 탈퇴 시각, `purge_after` = 마지막 거래일 + 5년). 일반 회원 조회 쿼리는 이 표를 읽지 않는다. 열람은 권한 + 사유 → **감사(`WITHDRAWN_CONTACT_REVEAL`, 사유 포함·이메일 없음)를 같은 트랜잭션에 남긴 뒤에만** 값을 준다. 열람 기록은 감사에서 읽는다.
- **정리 DB 배치 `member-privacy-retention`**(shared `R__`, 매일): 만료된 재가입 제한 해시(`expires_at ≤ 지금`), 파기 예정일이 지난 연락처, 30일(설정값) 지난 인증 시도.
- **데이터 키 교체 Java 배치 `data-key-rotation`**(shared `batch.core`, 수동 실행 위주): `DataCipher` 를 쓰는 칸 목록(지금은 연락처 이메일)마다 단계 하나, 지금 키가 아닌 암호문(`NOT LIKE 'd<버전>:%'`)을 키셋으로 덩어리 처리 → `reencrypt` → 조건부 UPDATE(읽은 값 그대로일 때만). 체크포인트로 이어 받기, 풀 수 없는 행은 건별 실패.
- **권한**: `member.withdrawn.contact`(SENSITIVE, 유형 `MEMBER_VIEW`, featureReady true).
- **감사 행위**: `MEMBER_RESTRICTION_GRANT`(운영자·`SYSTEM` 자동 — detail `source`·`type`·`until`·`autoRule`) · `MEMBER_RESTRICTION_LIFT` · `WITHDRAWN_CONTACT_REVEAL` · `PORTONE_SETTINGS_UPDATE`(바꾼 칸 이름) · `PORTONE_SETTINGS_DELETE`. 본인인증 성공·실패는 회원 행동이라 감사가 아니라 인증 시도 표에 남긴다.

### 0-4 서버 구현 결과 (2026-10-26)
- **코드**: shared `com.community.identity`(`IdentityVerifier` — `PortOneIdentityVerifier`·`FakeIdentityVerifier`, `IdentityConfig` 기동 검사, `IdentityVerificationService`, `AdultAge`, JDBC `IdentityStore`) · `restriction`(`MemberRestrictionService`·`MemberRestrictionStore`, `RestrictionType`·`RestrictedFeature`) · `withdrawal`(`IdentityWithdrawalRecorder`·`WithdrawnContactStore`) · `portone`(`PortOneSettingsService`·`PortOneSettingStore`) · `datakey.batch`(`DataKeyRotationJob`·`DataKeyColumns`) · `zconfiguration.time.ServiceTime`(서비스 날짜 KST). api `IdentityVerificationController`. admin `MemberRestrictionController`·`WithdrawnContactController`·`PortOneSettingsController`, 회원 상세 `identity`, `AdminScreenRegistry` `portone`, `AuditLabels` `PORTONE_SETTINGS`.
- **마이그레이션**(가번호 — 합칠 때 다시 매김): shared `V…__member_identity_restrictions.sql`(표 6개 + `member_notifications.type` CHECK 에 `FEATURE_RESTRICTED`), `R__batch_…_member_privacy_retention.sql`(매일 04:20, 덩어리 5000·한 번 100000, `attempt_retention_days` 30). 새 표는 FK 없음, ID Snowflake(인증 번호 `iv-<snowflake>`).
- **계약에 더한 것**
  - 오류 추가 값: `CommonException.extra(이름, 값)` → `ErrorResponse` 가 최상위 칸으로 낸다(`@JsonAnyGetter`, 정해진 칸 이름은 못 씀).
  - 본인인증: 서비스 날짜는 KST 고정(`ServiceTime`). `retryAfter` = 막힘이 끝나는 순간을 KST 날짜로 올림. 끝난 번호(실패 포함)·구현이 바뀐 뒤의 번호는 410. 입력 400·503 은 시도를 끝내지 않는다. 시도 실패 기록은 업무 트랜잭션과 따로 확정. 해시 키가 없으면 시작부터 503 `INDEX_KEY_MISSING`(저장 못 할 유료 인증을 열지 않게). 포트원 READY(미완료)도 502 로 끝. `provider_tx_id` = 포트원 인증 번호 / 가짜 `fake:<번호>`.
  - 기능 제한: 걸기 201. 자동 부여는 `reason` NULL — 화면·알림에 번들 `restriction.autoRule.<규칙>.reason`. 두 종류가 같은 기능을 막으면(충전 = 동결·충전 제한) 늦게 끝나는 쪽을 싣는다. `grantAuto` 는 회원별 `pg_advisory_xact_lock`, 탈퇴·없는 회원은 건너뜀. 감사의 무기한은 `indefinite:true`. 회원 알림 `FEATURE_RESTRICTED`(SAFETY, 끌 수 없음).
  - 회원 상세 `identity`: 미인증 `{verified:false, …null}`, null 은 탈퇴 회원뿐.
  - 연락처: 탈퇴 안 한 회원 GET 은 `stored:false`, reveal 은 404(감사 없음). `purgeAfter` 는 서비스 날짜. 값을 푸는 길은 `reveal` 하나뿐(감사 먼저). 분리 보관 표를 읽는 main 코드는 허용 목록(`WithdrawnContactRows`·`DataKeyColumns`)뿐 — 소스 검사 테스트.
  - 포트원: 칸 지우기는 값만 NULL(마지막 변경 표시 유지). 연락처·포트원 응답 `no-store`.
  - 키 교체 배치 `data-key-rotation`: Java, **예약 기본 꺼짐**(cron `0 0 5 * * *` 자리만), 칸마다 단계(`withdrawn-contact-email`), 풀 수 없는 행 `DATA_KEY_UNREADABLE` 건별 실패(키 버전만), 읽은 뒤 바뀐 행은 건너뜀, 지금 키 없으면 실행 FAIL.
  - 설정: `roboyam.identity.provider`(공통 portone, local fake)·`verification-ttl`(30m), `roboyam.withdrawal.rejoin-block-days`(30)·`contact-retention-years`(5).

### 0-4 규칙 → 테스트
| 규칙 | 테스트 |
|---|---|
| 서버가 인증 번호 먼저 발급, 이미 인증 409 | `IdentityVerificationFlowTest.시작`, `IdentityHttpTest` |
| DI·CI 해시만 저장(원문·이름·번호 없음), me 상태 | `IdentityVerificationFlowTest.확정` |
| 남의·없는 번호 404, 30분 만료 410(직전 통과), 끝난 번호 재사용 불가, 입력 400 은 시도 유지 | `IdentityVerificationFlowTest.확정`, `IdentityHttpTest`, `FakeIdentityVerifierTest` |
| 1인 1계정(DI 만·CI 만 같아도), 동시 확정 하나만(서비스 + 유일 제약) | `IdentityVerificationFlowTest.일인_일계정`, `MemberIdentitySchemaTest` |
| 재인증 30일 제한 + `retryAfter`, 30일 뒤 통과, DI·CI 어느 쪽이든 | `IdentityVerificationFlowTest.탈퇴_후_재인증_제한`, `IdentityHttpTest` |
| 만 19세(KST 날짜 경계), 2월 29일생 = 3월 1일, 생일 뒤 재인증 없이 통과, 미인증 403 | `AdultAgeTest`, `ServiceTimeTest`, `IdentityVerificationFlowTest.만_19세` |
| 포트원 조회 모양·VERIFIED 만·오류 502·로그에 비밀값/CI/DI 없음·설정 없음 503 | `PortOneIdentityVerifierTest`, `PortOneIdentitySettingsTest` |
| dev·prod 에서 fake 면 기동 실패, 공통 기본 portone·local fake | `IdentityConfigTest` |
| 해시 키 없으면 503 | `IdentityKeyMissingTest` |
| 오류 추가 값 최상위 칸, 기존 모양 유지 | `ErrorResponseExtrasTest`, `IdentityHttpTest` |
| 막는 것 표(충전 포인트 환불 신청은 허용), 403 + `restriction`(가장 늦은 끝·무기한 null·메모 없음), 끝 시각에 풀림, 겹침 허용 | `RestrictionTypeTest`, `MemberRestrictionTest.막는_것` |
| 운영자 부여 검증·탈퇴 409·감사·알림(메모 없음), 알림 문구 | `MemberRestrictionTest.운영자_부여`·`회원_알림_문구`, `MemberRestrictionHttpTest.걸기` |
| 자동 부여 24시간·SYSTEM 감사·더 늦은 진행 중이면 생략·동시 하나만·탈퇴 생략 | `MemberRestrictionTest.자동_부여` |
| 해제 사유 5~200·조건부·409/404·동시 하나만·감사 | `MemberRestrictionTest.해제`, `MemberRestrictionHttpTest.해제` |
| 목록 진행 중 먼저·상태 판정·`hasActive` | `MemberRestrictionTest.목록` |
| 포인트 동결이면 적립을 조용히 건너뜀(글쓰기 성공) | `PointFreezeEarnTest` |
| 재가입 해시(이메일 정규화·30일·소셜 구분·본인인증 해시 옮김) | `WithdrawalPreparationTest.재가입_제한_해시` |
| 연락처 데이터 키 암호화·파기 예정일 = 마지막 거래 + 5년 | `WithdrawalPreparationTest.탈퇴_회원_연락처` |
| 열람: 전용 권한(`member.pii` 로는 안 됨)·사유·감사 먼저(이메일 없음)·10분·`no-store`·열람 기록·404 | `WithdrawnContactHttpTest`, `WithdrawalPreparationTest`, `WithdrawnContactStoreTest` |
| 일반 조회가 분리 보관 표를 읽지 않음 | `WithdrawnContactStoreTest` |
| 표의 유일 제약·CHECK·FK 없음 | `MemberIdentitySchemaTest` |
| 정리 배치(만료 해시·파기 연락처·30일 시도, 설정값) | `MemberPrivacyRetentionBatchTest` |
| 회원 상세 identity(생년월일·해시 없음, 탈퇴 null) | `MemberDetailIdentityTest` |
| 포트원 설정: 값이 응답·감사에 없음·암호문 저장·권한 `site.read`/`site.secret`·지우기·404·암호화 키 없음 503 | `PortOneSettingsHttpTest`, `PortOneCryptoKeyMissingHttpTest`, `PortOneSettingsServiceTest` |
| 새 핸들러 권한 선언·권한별 403 | `Commerce04PermissionDeclarationTest`, `MemberRestrictionHttpTest.권한` |
| 권한 `member.withdrawn.contact`(MEMBER_VIEW·SENSITIVE)·admin-web 사본 일치·메뉴 줄·감사 대상 | `Commerce04AdminCatalogTest`, `AdminScreenRegistryLoginSecurityTest` |
| 오류 코드·감사·배치·알림 이벤트 문구 두 언어 | `Commerce04CatalogTest` |
| 키 교체: 새 키로·지금 키는 안 건드림·조건부 UPDATE·건별 실패·체크포인트 이어 받기·0-1 절차 끝까지(예전 키 뺀 서버에서 읽힘) | `DataKeyRotationJobTest` |
- **실행**(2026-10-26): `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew --continue :server:shared:test --rerun --tests 'com.community.{commerce,identity,restriction,withdrawal,portone,zconfiguration,batch.core,datakey,testsupport}.*' :server:api:test --rerun --tests 'com.community.{identity,restriction,point,withdrawal}.*' :server:admin:test --rerun --tests 'com.community.{commerce,permission.config,menu,batch,mfa}.*'`(패턴은 하나씩 `--tests`) → **shared 133 · api 67 · admin 208(0-3 의 mfa 168 포함) 통과, 0 실패**. admin-web·user-web 타입 검사 통과.
- **테스트로 막지 못한 것**: 실제 포트원 호출(응답 모양·`storeId` 쿼리 필요 여부·다날/KCP 의 ci·di·인증 번호 길이 제한은 포트원 V2 문서 기준으로 만들고 실제 호출로 확인하지 못함 — 대역 HTTP 로만), pg_cron 등록(시험 DB 에 없음 — 정의와 `CALL` 만), 기능 제한 알림 메일 실제 발송(알림함 행·문구까지), 운영 키 교체의 재기동(시험은 `DataCipher` 를 키별로 새로 만들어 대신), 포트원 설정 동시 저장(마지막 저장이 이김 — 시험 안 함), 화면(타입 검사만 — 브라우저 확인은 개발자).

### 0-4 화면 연결 (2026-10-26)
- 목업 파일·`?mock=` 스위치를 지우고 실제 API 로 바꿨다. 오류 추가 값은 기존 `ApiError.extras` 가 최상위 칸을 그대로 읽는다.
- **포트원 브라우저 SDK 는 넣지 않았다**(새 의존성 — 사용자 승인 필요). 서버가 PORTONE 이면 화면은 「포트원 인증창은 아직 연결되지 않았어요」로 끝난다(`useIdentity.ts` `TODO(포트원 SDK)`). local 은 FAKE 라 끝까지 된다.
- `/dev/identity`: 인증을 되돌리는 API 가 없어 상태 바꾸기 대신 실제 상태 보기·다시 받기. 기능 제한 안내는 403 을 주는 공개 API(충전·공구)가 아직 없어 화면에서 만든 오류로 미리 보기.
- 403 `IDENTITY_REQUIRED`·`IDENTITY_UNDERAGE`·`MEMBER_RESTRICTED` 를 받는 화면은 충전·공구 화면이 생길 때 `useIdentityGate`·`FeatureRestrictionNotice` 로 붙인다.
- user-web 회원 알림 종류에 `FEATURE_RESTRICTED`(아이콘 `pi pi-ban`).

## 미확정
- 이미 설치된 환경(설치 스크립트로 깐 곳)에는 새 키가 자동으로 들어가지 않는다 — 업그레이드 스크립트가 없어 운영자가 `.env` 에 직접 넣는다(넣지 않아도 앱은 뜨고 커머스 개인정보 요청만 503).
- 설치 마법사 점검 목록(`InstallationChecks`)에 새 키 표시는 넣지 않았다 — 판매 스위치 켜는 조건(20절)을 만들 때 함께 본다.
