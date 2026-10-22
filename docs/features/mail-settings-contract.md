# 메일 서버 설정 (관리자) 계약

설치 마법사에서만 넣을 수 있던 메일 서버(SMTP) 설정을 관리자 화면에서 조회·저장·시험 발송하고, 저장마다 설정 이력을 남긴다.
화면은 하모니카3 메일 설정 화면 방식(`settings/application/mail.vue`, `useMailSettingApi.ts`)을 따르고, 이력은 소셜 로그인 설정 이력(`login-contract.md` 「관리자 소셜 로그인 설정」)과 같은 모양이다.

## 화면
- 경로 제안: admin-web `/settings/mail`. 메뉴 등록(`AdminScreenRegistry`)은 프론트 단계에서 메뉴와 함께 한다 — 키 `mailSettings`, 주소 `/settings/mail`, 문구 키 `menu.mailSettings`, 아이콘 `pi pi-envelope`, 권한 `Permission.SITE_READ`, 묶음 `SETTINGS` 의 `menu.system`(`siteGeneral` 옆).
- 칸: 서버 주소 · 포트 · 보안 방식(`NONE`·`STARTTLS`·`SSL`) · 사용자 이름 · 비밀번호(입력됨 표시만) · 보내는 사람 이메일. 하모니카3 의 보내는 이름·제목 머리말·제품 주소·사용 여부·최근 성공/실패 칸은 로보얌 표(`mail_settings`)에 없어 넣지 않았다.

## API (`server/admin`, 응답은 공통 래퍼 `ApiResponse`)
| 메서드·경로 | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /api/admin/mail-settings` | `site.read` | → `MailSettingsView` |
| `PUT /api/admin/mail-settings` | `site.secret` | `{host, port, security, username, password, fromAddress}` → `MailSettingsView` |
| `POST /api/admin/mail-settings/test` | `site.secret` | `{to}` → `{succeeded, reason, detail}` |
| `GET /api/admin/mail-settings/history?page&size` | `site.read` | → `{items, total, page, size}` |

`MailSettingsView` = `{configured, host, port, security, username, passwordSet, fromAddress, cryptoKeyConfigured, updatedAt, updatedByName}`
- 설치 마법사가 넣은 행(`mail_settings` id 1)을 그대로 읽는다. 없으면(설치 때 건너뜀) `configured:false` 이고 값 칸은 모두 `null`, `passwordSet:false`.
- **비밀번호는 값을 내보내지 않는다** — `passwordSet` 만. `cryptoKeyConfigured` 는 서버에 `ROBOYAM_CRYPTO_KEY` 가 있는지(없으면 새 비밀번호 저장이 503, 인증 메일도 못 보낸다). `updatedByName` 은 지워진 운영자면 「삭제된 운영자」.

## 저장 규칙
- 검사는 설치 마법사와 같다(`MailSettingsService.normalize`) — 틀린 칸을 모아 400 `VALIDATION` `fields.<칸>`(`host`·`port`·`security`·`username`·`password`·`fromAddress`). 보안 방식은 비우면 `NONE`.
- 비밀번호를 **비워 보내면 저장된 값을 그대로 둔다.** 다만 사용자 이름 · 서버 주소(대소문자 무시) · 보안 방식 중 하나라도 바꾸면서 비우면 400 `fields.password`(「비밀번호를 다시 입력해 주세요」) — 저장된 자격 증명이 다른 서버로 가거나 암호화 없이 나가지 않게 한다(포트만 바꾸는 것은 그대로 둔다).
- 비밀번호를 보내면 암호화해 바꾼다(키 없으면 503 `CRYPTO_KEY_MISSING`). 저장된 것과 같은 비밀번호면(풀어서 견준다) 바뀐 것이 아니다.
- **사용자 이름을 비우면 비밀번호도 지운다**(인증 없이 보냄). 비밀번호만 있고 사용자 이름이 없으면 `fields.username`.
- 값이 하나도 안 바뀌면 아무것도 쓰지 않는다(시각·감사·이력 그대로).
- 반영: 실제 발송(`MailDispatcher` — 인증·재설정·운영자 초대·알림 메일)은 캐시 없이 **보낼 때마다 이 행을 읽는다** → 커밋 직후 모든 서버(api·admin, 여러 대)의 다음 발송이 새 값을 쓴다.

## 시험 발송
- **저장된 설정으로** 실제 발송 길(`MailDispatcher`)로 한 통 보낸다(화면은 저장한 뒤 시험한다). 받는 주소 형식 검사 400 `fields.to`.
- 실패도 200 `succeeded:false`. `reason`: `NOT_CONFIGURED` · `CRYPTO_KEY_MISSING` · `CONNECT_FAILED` · `AUTH_FAILED` · `TLS_FAILED` · `TIMEOUT` · `REJECTED`(주소 거절) · `FAILED`. `detail` 은 그 코드를 요청 언어로 푼 짧은 문구 — SMTP 응답 원문·자격 증명·서버 주소·받는 주소를 싣지 않는다(예외의 종류만 보고 가른다, 로그에는 예외 클래스 이름만).
- 운영자당 분당 5번(메일 서버에 실제로 접속하는 시험만 센다), 넘으면 429 `MAIL_TEST_RATE_LIMITED` + `retryAfterSeconds`·`Retry-After`. 서버 한 대 안에서 센다.
- 결과는 설정 이력이 아니라 **감사에만** 남긴다.

## 설정 이력 (`mail_setting_history`, Flyway shared V54)
- 값이 실제로 바뀐 저장마다 한 줄: `id, action(CREATE|UPDATE|DELETE), source(INSTALL|ADMIN), operator_id, changed_at, changes(jsonb [{field, before, after}])`. 운영자 FK 없음(지워져도 이력이 남는다).
- 설치 마법사의 첫 저장 = `CREATE · INSTALL`(설치 트랜잭션 안, `MailSettingsService.create`). 설치 때 건너뛰고 관리자 화면에서 처음 저장 = `CREATE · ADMIN`, 그 뒤는 `UPDATE · ADMIN`. 메일 설정 지우기는 없다(`DELETE` 는 소셜 이력과 CHECK 를 맞춘 자리).
- 이미 설치된 DB: V54 가 지금 값(= 설치 때 값)으로 `CREATE · INSTALL` 첫 줄을 만든다. 시각은 `installation.installed_at`(없으면 행의 저장 시각).
- 값은 모두 글자(포트도). **비밀번호는 값 없이 표시 코드만** — 처음 넣음 `null → SET`, 바꿈 `SET → CHANGED`, 지움 `SET → CLEARED`.
- 조회 응답 항목: `{id, action, source, operatorId(string|null), operatorName, operatorDeleted, changedAt, changes:[{field, labelKey:'settingHistory.field.<field>', before, after}]}` — 최신이 위(시각, 같으면 id), `size` 1~100(기본 20), `page` 0부터, 틀리면 400 `fields.page`/`fields.size`. 운영자를 못 찾으면 `operatorName` 「삭제된 운영자」(요청 언어) + `operatorDeleted:true`.
- 칸 이름 문구는 admin-web i18n `settingHistory.field.host|port|security|username|password|fromAddress` 가 정한다.
- 보관: 무기한(행이 작고 실제 변경만 남는다).

## 감사·권한
- 감사: `MAIL_SETTINGS_UPDATE`(대상 `SITE`/`mail`, `detail.fields` = 바뀐 칸 이름만) · `MAIL_SETTINGS_TEST`(성공 여부·원인 코드만, 받는 주소 없음). 번들 `audit.action.*` ko·en.
- 권한: 새 권한 없이 기존 `site.read`(조회·이력) · `site.secret`(저장·시험) — 소셜 로그인 설정과 같다. `site.secret` 의 `featureReady` 는 아직 `false` 이고, 화면이 생길 때 서버 `Permission` 과 프론트 `util-permission-catalog.ts` 를 함께 `true` 로 바꾼다.

## 미확정
- 보내는 이름(표시 이름)·제목 머리말 칸을 둘지(표에 칸 추가 필요).
- 시험 발송의 받는 주소를 운영자 본인 이메일로 기본 채울지(화면 결정, 서버는 아무 주소나 받는다).
- 메일 서버 주소의 내부망 제한(알림 도구의 `OutboundTargetGuard` 같은)은 두지 않았다 — 설치 마법사와 같고, `site.secret` 가진 운영자만 바꾼다.
