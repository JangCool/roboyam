# 메일 서버 설정 (관리자) 계약

설치 마법사에서만 넣을 수 있던 메일 서버(SMTP) 설정을 관리자 화면에서 조회·저장·시험 발송하고, 저장마다 설정 이력을 남긴다.
화면은 하모니카3 메일 설정 화면 방식(`settings/application/mail.vue`, `useMailSettingApi.ts`)을 따르고, 이력은 소셜 로그인 설정 이력(`login-contract.md` 「관리자 소셜 로그인 설정」)과 같은 모양이다.

## 화면
- 경로 제안: admin-web `/settings/mail`. 메뉴 등록(`AdminScreenRegistry`)은 프론트 단계에서 메뉴와 함께 한다 — 키 `mailSettings`, 주소 `/settings/mail`, 문구 키 `menu.mailSettings`, 아이콘 `pi pi-envelope`, 권한 `Permission.SITE_READ`, 묶음 `SETTINGS` 의 `menu.system`(`siteGeneral` 옆).
- 칸: 서버 주소 · 포트 · 보안 방식(`NONE`·`STARTTLS`·`SSL`) · 사용자 이름 · 비밀번호(입력됨 표시만) · 보내는 사람 이메일 · 보내는 이름 · 제목 머리말(V55). 하모니카3 의 제품 주소·사용 여부·최근 성공/실패 칸은 로보얌 표(`mail_settings`)에 없어 넣지 않았다.

## API (`server/admin`, 응답은 공통 래퍼 `ApiResponse`)
| 메서드·경로 | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /api/admin/mail-settings` | `site.read` | → `MailSettingsView` |
| `PUT /api/admin/mail-settings` | `site.secret` | `{host, port, security, username, password, fromAddress, senderName?, subjectPrefix?}` → `MailSettingsView` |
| `POST /api/admin/mail-settings/test` | `site.secret` | `{to}` → `{succeeded, reason, detail}` |
| `GET /api/admin/mail-settings/history?page&size` | `site.read` | → `{items, total, page, size}` |

`MailSettingsView` = `{configured, host, port, security, username, passwordSet, fromAddress, senderName: string|null, subjectPrefix: string|null, cryptoKeyConfigured, updatedAt, updatedByName}`
- 설치 마법사가 넣은 행(`mail_settings` id 1)을 그대로 읽는다. 없으면(설치 때 건너뜀) `configured:false` 이고 값 칸은 모두 `null`, `passwordSet:false`.
- **비밀번호는 값을 내보내지 않는다** — `passwordSet` 만. `cryptoKeyConfigured` 는 서버에 `ROBOYAM_CRYPTO_KEY` 가 있는지(없으면 새 비밀번호 저장이 503, 인증 메일도 못 보낸다). `updatedByName` 은 지워진 운영자면 「삭제된 운영자」.

## 저장 규칙
- 검사는 설치 마법사와 같다(`MailSettingsService.normalize`) — 틀린 칸을 모아 400 `VALIDATION` `fields.<칸>`(`host`·`port`·`security`·`username`·`password`·`fromAddress`). 보안 방식은 비우면 `NONE`.
- 비밀번호를 **비워 보내면 저장된 값을 그대로 둔다.** 다만 사용자 이름 · 서버 주소(대소문자 무시) · 보안 방식 중 하나라도 바꾸면서 비우면 400 `fields.password`(「비밀번호를 다시 입력해 주세요」) — 저장된 자격 증명이 다른 서버로 가거나 암호화 없이 나가지 않게 한다(포트만 바꾸는 것은 그대로 둔다).
- 비밀번호를 보내면 암호화해 바꾼다(키 없으면 503 `CRYPTO_KEY_MISSING`). 저장된 것과 같은 비밀번호면(풀어서 견준다) 바뀐 것이 아니다.
- **사용자 이름을 비우면 비밀번호도 지운다**(인증 없이 보냄). 비밀번호만 있고 사용자 이름이 없으면 `fields.username`.
- **보내는 이름 · 제목 머리말**(`mail_settings.sender_name varchar(50)` · `subject_prefix varchar(30)`, Flyway shared V55, 둘 다 NULL 허용): 선택 칸. 앞뒤 공백을 자르고 비면(빈 글자·공백만·생략) null 로 저장. `senderName` 1~50자 · `subjectPrefix` 1~30자(글자 수 = 코드 포인트), 줄바꿈·제어 문자(줄/문단 구분자 포함) 불가 — 메일 헤더 주입 방지. 위반은 400 `fields.senderName`·`fields.subjectPrefix`(`mail.senderName.length|control`, `mail.subjectPrefix.length|control`). 검사는 shared `MailSettingsService.normalizeSenderName`·`normalizeSubjectPrefix`. **설치 마법사는 이 두 칸을 받지 않는다**(관리자 화면에서만, 설치 API 모양 그대로 — 설치 때는 null).
- 값이 하나도 안 바뀌면 아무것도 쓰지 않는다(시각·감사·이력 그대로).
- 반영: 실제 발송(`MailDispatcher` — 인증·재설정·운영자 초대·알림 메일)은 캐시 없이 **보낼 때마다 이 행을 읽는다** → 커밋 직후 모든 서버(api·admin, 여러 대)의 다음 발송이 새 값을 쓴다.

## 보내는 이름 · 제목 머리말 적용 (모든 메일)
회원 인증, 운영자 초대·자격 증명·비밀번호 재설정 승인번호, 관리자 알림 메일, 메일 설정 시험 메일, 알림 도구 이메일 시험 — 모두 `MailDispatcher` 한 곳에서 붙인다(템플릿은 그대로).
- From: `senderName` 이 있으면 `"보내는 이름" <fromAddress>`(Jakarta Mail `InternetAddress(address, personal, "UTF-8")` — 한글은 RFC 2047 인코딩, 따옴표·쉼표·꺾쇠는 RFC 5322 따옴표 처리), 없으면 주소만.
- 제목: `subjectPrefix` 가 있으면 `"{머리말} {제목}"`, 없으면 지금 제목 그대로.
- **자체 `[사이트 이름]` 을 붙이는 메일**(관리자 알림, 회원 인증, 운영자 초대·자격 증명·재설정 승인번호): 머리말이 있으면 `[사이트 이름]` 을 빼고 `"{머리말} {제목}"`, 없으면 지금 그대로 `[사이트 이름] {제목}`. 템플릿이 태그 붙인 제목과 뺀 제목을 함께 만들어 `MailDispatcher.sendTagged` 로 넘긴다. 번들: `mail.verify|operatorCredentials|operatorInvite|operatorReset.subject` 는 사이트 이름 없는 제목이고, 태그는 공통 `mail.common.siteTaggedSubject=[{0}] {1}`(ko·en)이 붙인다(관리자 알림은 기존 `notification.mail.subject`).
- 시험 메일·알림 도구 이메일 시험은 원래 사이트 이름이 없어 머리말만 앞에 붙는다.
- 저장 때 막지만, DB 를 직접 고친 값에 제어 문자가 있어도 발송 때 공백으로 바꿔 헤더가 갈라지지 않는다.

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
- 보내는 이름 · 제목 머리말도 같은 규칙(`field: senderName|subjectPrefix`, 값 그대로, 지우면 `after: null`). 칸 차례 host · port · security · username · password · fromAddress · senderName · subjectPrefix. 같은 값 재저장은 이력 없음.
- 칸 이름 문구는 admin-web i18n `settingHistory.field.host|port|security|username|password|fromAddress|senderName|subjectPrefix` 가 정한다.
- 보관: 무기한(행이 작고 실제 변경만 남는다).

## 감사·권한
- 감사: `MAIL_SETTINGS_UPDATE`(대상 `SITE`/`mail`, `detail.fields` = 바뀐 칸 이름만) · `MAIL_SETTINGS_TEST`(성공 여부·원인 코드만, 받는 주소 없음). 번들 `audit.action.*` ko·en.
- 권한: 새 권한 없이 기존 `site.read`(조회·이력) · `site.secret`(저장·시험) — 소셜 로그인 설정과 같다. `site.secret` 의 `featureReady` 는 화면을 붙이면서 서버·프론트 모두 `true` 로 바꿨다.

## 미확정
- 시험 발송의 받는 주소를 운영자 본인 이메일로 기본 채울지(화면 결정, 서버는 아무 주소나 받는다).
- 메일 서버 주소의 내부망 제한(알림 도구의 `OutboundTargetGuard` 같은)은 두지 않았다 — 설치 마법사와 같고, `site.secret` 가진 운영자만 바꾼다.

## 프론트 결과 (2026-10-23)
- **화면** admin-web `/settings/mail`(메뉴 설정 › 시스템 › 「메일 서버」, `site.read`) — 하모니카3 방식: 「메일 서버」 구획 하나(서버 주소·포트·보안 방식(SelectButton, 바꾸면 기본 포트 따라감)·사용자 이름·비밀번호(「입력됨」)·보내는 사람 이메일·**보내는 이름·제목 머리말**) + 구획 맨 아래 [시험 발송] · [되돌리기]·[저장], 그리고 「설정 이력」 구획(소셜과 같은 `SettingHistoryTable`).
- **보내는 이름·제목 머리말**: 계약 확정분(shared V55, 다른 세션이 서버 구현 중)을 화면에 먼저 넣었다 — 응답에 칸이 없으면 null 로 다루고, `PUT` 에는 빈 값이면 null 로 보낸다. 사전 검사 1~50자·1~30자·줄바꿈 금지, `fields.senderName|subjectPrefix` 는 칸 아래. 서버가 합쳐지기 전 확인 환경에서는 저장해도 값이 돌아오지 않아 빈 칸으로 되돌아간다(서버 반영 뒤 최종 확인 필요).
- **비밀번호 안내**: 사용자 이름·서버 주소(대소문자 무시)·보안 방식을 바꾸며 비밀번호를 비우면 칸에 바로 「다시 입력해 주세요」를 보이고 저장을 막는다(서버 `sameTarget` 과 같은 규칙, `utils/util-mail-settings.ts`). 사용자 이름을 비우면 「비밀번호도 지워져요」 안내. 암호화 키가 없으면 상단 경고 + 비밀번호 칸 잠금.
- **설정 없음**(`configured=false`): 「아직 메일 서버가 없어요」 + 빈 폼(STARTTLS·587). 시험 발송은 저장된 설정이 있고 고친 값이 없을 때만(옆에 까닭).
- **시험 발송**: 대화상자 `{to}` — 기본값은 로그인한 운영자 이메일(`me.user.email`, 없으면 빈 칸). 결과는 대화상자 안(성공 / 실패는 `reason` 코드별 `mail.reason*` 문구, 모르는 코드는 서버 `detail`). `fields.to` 는 칸 아래, 429 `MAIL_TEST_RATE_LIMITED` 는 `retryAfterSeconds` 와 함께 한 줄.
- **읽기 전용** `site.secret` 없으면 입력 readonly, 저장 줄·시험 단추 없음.
- **코드** API `composables/mail/api/useMailSettingsApi.ts`, 규칙·문구 `utils/util-mail-settings.ts`·`util-mail-settings-messages.ts`, 문구 `i18n/locales/{ko,en}/mail.ts`, 이력 칸 이름 `settingHistory.field.host|port|security|username|password|fromAddress|senderName|subjectPrefix`.
- **검증** 일회용 환경: 설치 API 로 메일(Mailpit·사용자 이름+비밀번호) 넣고 설치 → 화면에 그대로·이력 첫 줄 「설치」. 시험 발송 성공(Mailpit 수신), 서버 주소만 바꿈 → 비밀번호 다시 입력 안내·저장 막힘(서버도 같은 400 확인), 서버 칸 오류 표시(`fields.security` 를 요청 가로채기로 유도), 사용자 이름 비우고 저장 → 이력 「사용자 이름 → (없음) · 비밀번호 입력함 → 지움」, 포트 1 로 저장 후 시험 → 「연결하지 못했어요」, 연속 시험 → 429 남은 초, 받는 주소 형식 오류. `site.read` 만 가진 운영자 읽기 전용, 설정 행이 없을 때 빈 폼 안내.
- **미확정**: 시험 발송의 연결 실패는 서버가 분당 한도에 세지 않는 것으로 보인다(접속 실패 5번 뒤에도 429 가 바로 나지 않았다 — 계약 「실제로 접속하는 시험만 센다」와 같음).
