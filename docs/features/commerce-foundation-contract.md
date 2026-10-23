# 커머스 0단계 공통 기반 계약 (ERD·패키지 제안 승인 2026-10-23 · 0-1 필드 암호화 구현 2026-10-23)

**의도**: 포인트 상점·결제·공구·충전(1~3단계)이 모두 기대는 공통 장치를 먼저 만든다 — 필드 암호화, 배치 보강, 관리자 2차 인증, 본인인증·기능 제한·탈퇴 대비, 돈 관련 요청 보호(멱등·속도 제한), 커머스 메일 대기열, 권한 코드. 정책 원천은 `docs/domain/commerce.md`(3·10-9·12·13·14·15·17·21절)이고 이 문서는 0단계에서 정한 구조와 결정만 적는다.

## 작업 단위와 순서
| 단위 | 내용 | 상태 |
|---|---|---|
| 0-1 | 필드 암호화 키링(`DataCipher`)·중복 확인 해시(`BlindIndex`), 설치 스크립트·compose 의 새 키 | **구현됨**(아래) |
| 0-2 | 배치 보강: 작업 core 를 shared `batch.core` 로, 실행 요청 표(「지금 실행」 비동기), 단계·건별 실패·체크포인트·재실행, 워커 기록 | 대기 — 배치 화면 목업 먼저 |
| 0-3 | 관리자 2차 인증(TOTP) | 대기 — 화면 목업 먼저 |
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

## 미확정
- 이미 설치된 환경(설치 스크립트로 깐 곳)에는 새 키가 자동으로 들어가지 않는다 — 업그레이드 스크립트가 없어 운영자가 `.env` 에 직접 넣는다(넣지 않아도 앱은 뜨고 커머스 개인정보 요청만 503).
- 설치 마법사 점검 목록(`InstallationChecks`)에 새 키 표시는 넣지 않았다 — 판매 스위치 켜는 조건(20절)을 만들 때 함께 본다.
