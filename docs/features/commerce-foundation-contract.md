# 커머스 0단계 공통 기반 계약 (ERD·패키지 제안 승인 2026-10-23 · 0-1 필드 암호화 구현 2026-10-23)

**의도**: 포인트 상점·결제·공구·충전(1~3단계)이 모두 기대는 공통 장치를 먼저 만든다 — 필드 암호화, 배치 보강, 관리자 2차 인증, 본인인증·기능 제한·탈퇴 대비, 돈 관련 요청 보호(멱등·속도 제한), 커머스 메일 대기열, 권한 코드. 정책 원천은 `docs/domain/commerce.md`(3·10-9·12·13·14·15·17·21절)이고 이 문서는 0단계에서 정한 구조와 결정만 적는다.

## 작업 단위와 순서
| 단위 | 내용 | 상태 |
|---|---|---|
| 0-1 | 필드 암호화 키링(`DataCipher`)·중복 확인 해시(`BlindIndex`), 설치 스크립트·compose 의 새 키 | **구현됨**(아래) |
| 0-2 | 배치 보강: 작업 core 를 shared `batch.core` 로, 실행 요청 표(「지금 실행」 비동기), 단계·건별 실패·체크포인트·재실행, 워커 기록 | 대기 — 배치 화면 목업 먼저 |
| 0-3 | 관리자 2차 인증(TOTP) | **화면 확정 2026-10-23** · 서버 구현 중(아래 「0-3」) |
| 0-4 | 본인인증·기능 제한·탈퇴 대비 표, 데이터 키 교체 배치 | 대기 — 0-1 필요, 화면 목업 먼저 |
| 0-5 | 멱등·속도 제한·요청 제한 설정, 커머스 메일 대기열, 권한 코드 11개, 감사 보관 분류(13-4) | 대기 — 0-2 의 워커 기록 필요 |
순서: 0-1 → (0-2 ∥ 0-3) → (0-4 ∥ 0-5). 병행은 세션마다 worktree, 마이그레이션 번호는 develop 에 합치는 순서로 매긴다(`_backend-core.md`).

## 마이그레이션 계획 (가번호 — 합칠 때 확정)
| 가번호 | 스트림 | 표 | 단위 |
|---|---|---|---|
| V57 | shared | `batch_runs` 칸(run_mode·params·건수·resumed_from_run_id·checkpoint·request_id), `batch_run_steps`, `batch_run_failures`, `batch_run_requests`, `worker_heartbeats` | 0-2 |
| admin V3 | admin | `admin_security_settings`(1행·꺼짐), `operator_mfa`, `operator_mfa_recovery_codes`, `operator_trusted_devices` | 0-3 |
| V58 | shared | `identity_verifications`, `identity_verification_attempts`, `member_restrictions`, `identity_withdrawals`, `withdrawn_member_contacts` | 0-4 |
| V59 | shared | `idempotency_requests`, `request_rate_counters`(UNLOGGED), `request_rate_limits`(기본 4행) | 0-5 |
| V60 | shared | `mail_outbox` | 0-5 |
칸·제약·색인의 상세는 승인한 제안(세션 보고, 노트 `commerce-stage0-erd-package-proposal.md`)을 따르고 구현 세션이 이 표에 결과를 덧붙인다.

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
  - 감사 대상 `LOGIN_SECURITY`/`1`. 신뢰 기기 끊기 `cause` = SELF·PASSWORD_CHANGE·PASSWORD_RESET·REENROLL·ACCOUNT_LOCKED. 루트의 화면 초기화는 그 운영자의 `mfa:` 실패 기록도 지운다. 미등록 운영자 초기화도 `RESET` + 감사(`wasEnrolled:false`).
  - 메뉴: `AdminScreenRegistry.Screen` 에 `superOnly`. 메뉴 구성 API 는 원래 화면 권한을 내리지 않아 응답에는 없다 — 셸·메뉴 관리 표시는 admin-web `util-admin-menu.ts` 의 `superOnly`(메뉴 관리 화면은 「최고 관리자만」으로 보인다).
  - 쿠키 `ADMIN_MFA_TRUST` 의 Secure 는 `request.isSecure()` 를 따른다. 저장 시각은 마이크로초로 자른다(DB 반올림 때문에 10분 경계가 흔들렸다).

### 0-3 규칙 → 테스트 (server/admin/src/test, 163개 통과)
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
| **루트가 다른 운영자 OTP 초기화**(본인 409·비루트 403)·운영자 상세 조회 | `MfaRevocationTest` |
| **설치 직후 꺼짐**·설정 1행·범위 CHECK·해시 유일·연쇄 삭제 | `MfaSchemaTest`(실제 PostgreSQL) |
| 서버 초기화 명령 `admin_mfa_reset`(감사 SYSTEM·주입 불가)·정리 배치 | `MfaSchemaTest` |
| 비밀키·코드·복구 코드·쿠키 값이 감사에 없음, 감사 행위·문구·배치 카탈로그 | `MfaEnrollmentTest`, `MfaCatalogTest` |
| 메뉴: 소셜 로그인 다음·루트 전용 | `AdminScreenRegistryLoginSecurityTest` |
| 같은 TOTP·같은 복구 코드를 **동시에** 내면 하나만 통과(서비스 행 잠금 + 저장소 조건부 UPDATE 를 연결 여럿으로) | `MfaConcurrencyTest`(실제 PostgreSQL) |
| 비밀키·OTP·복구 코드·쿠키 값·암호문·비밀번호가 **서버 로그에 없음**, DTO `toString` 감춤 | `MfaLogSecrecyTest`(실제 필터 체인 흐름 + 출력 캡처) |
| 신뢰 기기 쿠키: https 면 Secure·HttpOnly·SameSite=Strict·path `/api/admin`·30일, 이 브라우저 기기/모두 끊으면 쿠키 지움, 다른 기기는 그대로 | `MfaTrustCookieHttpTest` |
- 실행: `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew :server:admin:test --rerun --tests 'com.community.mfa.*' --tests 'com.community.permission.config.AdminMfaPendingDeclarationTest' --tests 'com.community.menu.service.AdminScreenRegistryLoginSecurityTest'` → **163 통과·0 실패**.
- **테스트로 막지 못한 것**: 설치 스크립트의 실제 설치 폴더 동작(셸 — `bash -n` + 임시 postgres 에서 psql 부분만 손으로 확인), pg_cron 등록(시험 DB 에 없음 — 정의와 `CALL` 결과만), 운영 세션 쿠키 이름 `ADMIN_SESSION`(목 서블릿은 `SESSION`), 알림 메일·Slack 실제 발송(서랍 알림 행까지만), 운영 서버의 접근·프록시 로그.

## 미확정
- 이미 설치된 환경(설치 스크립트로 깐 곳)에는 새 키가 자동으로 들어가지 않는다 — 업그레이드 스크립트가 없어 운영자가 `.env` 에 직접 넣는다(넣지 않아도 앱은 뜨고 커머스 개인정보 요청만 503).
- 설치 마법사 점검 목록(`InstallationChecks`)에 새 키 표시는 넣지 않았다 — 판매 스위치 켜는 조건(20절)을 만들 때 함께 본다.
