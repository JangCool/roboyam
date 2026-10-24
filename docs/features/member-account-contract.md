# 회원 계정 흐름(공개 사이트) 계약 (화면 확정 · 서버 구현·화면 연결 2026-10-24)

**의도**: community 에만 있던 「계정 흐름」 화면(이메일 가입·이메일 인증·비밀번호 찾기/재설정·소셜 가입자 안내·이메일 변경·비밀번호 변경·온보딩)을 user-web 으로 옮긴다. 서버 API 가 있는 곳(인증 메일 재발송·로그인 정보·소셜 연결 상태)은 실제로, 없는 곳은 community 계약 모양 그대로 `// MOCK:`/`// REAL:` 짝 목업으로 화면을 먼저 확정한다.
**원본(읽기 전용)**: community `frontend/app/pages/{signup/index,verify-email,onboarding}.vue`·`password/{forgot,reset,social}.vue`·`settings/account/{index,email,password}.vue`, API 계약 community `docs/api-auth.md`·`docs/api-account.md`.

## 화면 (user-web)
| 경로 | 셸·미들웨어 | 내용 | API |
|---|---|---|---|
| `/signup` | `auth`·`guest` | (소셜이 켜져 있으면 방식 선택) → 1/3 이메일·비밀번호·확인 → 2/3 닉네임(미리보기) → 3/3 약관 3개(`TermsAgreement`) → `/verify-email` | 목업 `POST /api/auth/signup`·`GET email-available`·`GET nickname-available` · 실제 `GET /api/auth/providers` |
| `/verify-email` | `auth`·`auth-unless-token`(새로 둠) | `?token=` 있으면 링크 확인(인증 완료·이메일 변경 완료·변경 실패(겹침)·만료·쓸 수 없음·오류), 없으면 「인증 메일을 보냈어요」 + 다시 받기(60초) · 이미 인증이면 안내 | 목업 `POST /api/auth/email/verify` · **실제** `POST /api/auth/email/resend` · 실제 `GET /api/auth/me` |
| `/password/forgot` | `auth`·`guest` | 이메일 → 항상 같은 「보냈어요」(계정 존재를 알리지 않음) + 다시 보내기(60초) | 목업 `POST /api/auth/password/forgot` |
| `/password/reset?token=` | `auth` | 링크 확인 → 새 비밀번호·확인(규칙 줄) → 완료(로그인으로). 토큰 없음·만료·이미 씀 = 같은 안내 | 목업 `GET …/reset/check`·`POST …/reset` |
| `/password/social?provider=` | `auth` | 소셜 전용 회원에게 가는 메일 링크 화면 — 「○○로 가입한 계정이에요」 + 그 공급자 로그인 단추(켜져 있을 때) | 실제 `GET /api/auth/providers`(단추) |
| `/onboarding` | `auth`·`auth` | 환영(등급 뱃지·닉네임) → 운영 원칙 3장(출처·비방 금지·투자 고지 — 문구는 초안 그대로, 예시만 로봇·AI 주제) → 시작하기(`/`), 건너뛰기 | 없음(로그인 정보만) |
| `/settings/account` | 설정 묶음 | **로그인 정보 구획을 더함**: 이메일 + 인증됨/미인증 딱지, 미인증이면 「인증 메일 다시 받기」(배너와 같은 쿨다운), 비밀번호 유무, 「이메일 변경」「비밀번호 변경」 줄(소셜 가입자는 감추지 않고 「사용 불가」+이유). 소셜 연결은 그대로 | 실제 `GET /api/me/social`·`/api/auth/me`·resend |
| `/settings/account/email` | 설정 묶음 | 현재 이메일(읽기 전용)·새 이메일·현재 비밀번호 → 「보냈어요」(새 주소, 60초 뒤 다시 보내기 — 비밀번호 다시 받음, 다른 이메일로 다시 입력), 변경 대기 안내, 소셜 가입자 안내 | 목업 `GET /api/me/account`·`POST /api/me/email-change` |
| `/settings/account/password` | 설정 묶음 | 현재·새·확인(규칙 + 「현재 비밀번호와 다르게」) → 완료 화면 + 토스트, 잠금 안내(분 단위), 소셜 가입자 안내 | 목업 `GET /api/me/account`·`PUT /api/me/password` |

- 들어가는 길: 로그인 화면의 「가입하기」「다른 계정으로 가입」「비밀번호 찾기」·잠금 안내의 「비밀번호 재설정」(기존 링크가 이제 화면이 있다), 헤더·서랍의 「가입하기」, 인증 배너의 「안내 보기」, 인증 완료의 「시작하기」(→ `/onboarding`).
- 설정 묶음 왼쪽 메뉴: 「계정·로그인 연결」 아래 「이메일 변경」「비밀번호 변경」을 들여 써서 보인다(`UtilSettings.ENTRIES[].children`, `AppSideNav` 의 `sub`). 하위 화면에 있으면 그 줄만 짚는다. 좁은 화면의 돌아가기는 하위 화면에서 「‹ 계정·로그인 연결」. 좁은 화면 목록·`/me` 바로가기는 그대로(4줄).

## 필드·검증 (화면 사전 검사 = 서버 `UserPolicy`·community 「입력 규칙」)
- 이메일: 형식·254자(`UtilValidation.email`). 가입은 입력이 멈추고 400ms 뒤 `email-available`(`useDebouncedCheck` — 늦은 응답 무시), 확인 실패여도 다음 단계로 갈 수 있다(제출 때 서버가 다시 본다).
- 비밀번호(가입·재설정·변경의 새 비밀번호): 8~64자·UTF-8 72바이트·영문+숫자(`PasswordRules`). 확인 칸 일치 전에는 단추가 꺼진다. 변경은 「현재와 다르게」까지.
- 닉네임: 2~12자, 한글·영문·숫자·밑줄 + 글자 사이 공백 한 칸, 400ms 뒤 `nickname-available`(사유 `LENGTH|CHARS|DUPLICATE|RESERVED`), 12자 넘게 넣으면 안내.
- 약관: `agreeTerms`·`agreePrivacy`·`ageConfirmed` 모두 true(소셜 가입과 같은 부품). 이용약관·개인정보처리방침의 「보기」 링크는 관리자 옵션(약관 요약 `GET /api/terms/summary` 의 `showLinks` — `useTermsSummary().showLinks`, `terms-contract.md`)이 켜졌을 때만 보인다(`TermsAgreement`).
- 비밀번호는 그 화면의 `ref` 에만, 성공·잠금 때 비운다. 주소·저장소·토스트·로그에 없다.

## API — 실제
`POST /api/auth/email/resend`(202 · 409 `ALREADY_VERIFIED` · 429 `RESEND_COOLDOWN`+`retryAfterSeconds`) · `GET /api/auth/me` · `GET /api/auth/providers` · `GET /api/me/social`(`hasPassword`) — `login-contract.md` 그대로.

## API — 계정 흐름 (2026-10-24 서버 구현 — 처음엔 목업, 모양은 community 계약 그대로. 응답은 공통 래퍼의 `data`/`error`)
| | 요청 | 응답 · 오류 |
|---|---|---|
| `POST /api/auth/signup` | `{ email, password, nickname, agreeTerms, agreePrivacy, ageConfirmed }` | `201` me 모양(가입 즉시 로그인, 인증 메일 발송 — 60초 재발송 제한에 포함) · `400 VALIDATION`(`fields` 한 번에) · `409 EMAIL_TAKEN` · `409 NICKNAME_TAKEN` |
| `GET /api/auth/email-available?email=` | — | `{ available }` · `400 VALIDATION` |
| `GET /api/auth/nickname-available?nickname=` | — | `{ available, reason: LENGTH\|CHARS\|DUPLICATE\|RESERVED\|null }` |
| `POST /api/auth/email/verify` | `{ token }`(로그인 없이) | `200 { verified, changed }`(`changed`=이메일 변경 링크) · `400 TOKEN_INVALID` · `410 TOKEN_EXPIRED` · `409 EMAIL_TAKEN`(변경 링크만). 링크 `{공개 주소}/verify-email?token=`(서버 재발송이 이미 이 주소로 만든다), 24시간 |
| `POST /api/auth/password/forgot` | `{ email }` | 늘 `202`(계정 존재를 알리지 않음, 60초 안 반복은 조용히 무시) · 형식만 `400 VALIDATION`. 메일: 비밀번호 있는 회원 → `{공개 주소}/password/reset?token=`(1시간), 소셜 전용 → `/password/social?provider=` |
| `GET /api/auth/password/reset/check?token=` | — | `{ valid: true }` · `400 TOKEN_INVALID` · `410 TOKEN_EXPIRED` |
| `POST /api/auth/password/reset` | `{ token, newPassword }` | `204`, 그 계정의 **모든 세션 종료** · `400 VALIDATION`(`fields.newPassword`) · `400 TOKEN_INVALID` · `410 TOKEN_EXPIRED` · `400 SAME_PASSWORD` |
| `GET /api/me/account` | 로그인 | `{ email, emailVerified, hasPassword, pendingEmail(앞 한 글자만 남긴 가림), providers[] }`(`providers` = `/api/me/social` 의 줄) |
| `PUT /api/me/password` | `{ currentPassword, newPassword }` | `204`(이 기기만 남고 다른 세션 종료, 실패 횟수 초기화) · `401 CURRENT_PASSWORD_WRONG`(로그인 실패와 같은 횟수) · `429 LOGIN_LOCKED`+`retryAfterSeconds` · `400 SAME_PASSWORD` · `400 VALIDATION` · `409 PASSWORD_NOT_SET` |
| `POST /api/me/email-change` | `{ newEmail, password }` | `202`(새 주소에 변경 링크 24시간 + 지금 주소에 알림 메일, 누르기 전까지 지금 이메일 유지, 새 요청은 이전 링크 무효) · `400 VALIDATION`(`fields.newEmail`) · `409 EMAIL_TAKEN`(지금 주소와 같아도) · `401 CURRENT_PASSWORD_WRONG` · `429 LOGIN_LOCKED` · `429 RESEND_COOLDOWN`(60초) · `409 EMAIL_CHANGE_NOT_ALLOWED` |

코드: `composables/auth/api/useAuthApi.ts` · `composables/account/api/useAccountApi.ts`(+`.types.ts`). 새 오류 문구 `error.CURRENT_PASSWORD_WRONG`·`PASSWORD_NOT_SET`·`EMAIL_CHANGE_NOT_ALLOWED`.

## 목업 가정 (2026-10-24 실제 API 연결로 목업 파일·스위치는 지웠다 — 기록용)
1. **목업 가입은 서버 세션을 만들지 못한다** — 응답으로 화면 상태만 로그인한 것처럼 둔다. 그 상태로 다른 화면에서 실제 API 를 부르면 401 → 「로그인 시간이 지났어요」로 간다. 새로고침하면 사라진다.
2. `GET /api/me/account` 목업은 실제 `/api/me/social`(비밀번호 유무·공급자) + 로그인 정보(이메일·인증)로 만들고, 변경 대기 이메일만 메모리에 둔다.
3. 갈래 값(비밀번호 값은 쓰지 않는다): 이메일에 `taken` → 이미 가입/사용 중 · 닉네임 `taken`·`사용중` 중복, `admin`·`운영자`·`관리자`·`roboyam` 예약어 · 메일 링크 토큰 `expired`·`invalid`·`changed`·`taken`(인증), `expired`·`invalid`(재설정).
4. `?mock=` 스위치: `signup-fail` · `email-check-fail` · `nickname-check-fail` · `verify-fail` · `forgot-fail` · `reset-check-fail` · `reset-fail` · `reset-same` · `account-fail` · `account-social` · `account-pending` · `password-wrong` · `password-locked` · `password-change-fail` · `password-not-set` · `email-send-fail` · `email-cooldown` · `email-not-allowed`.
5. 목업은 비밀번호를 비교·저장하지 않는다(맞고 틀림은 스위치로만). 비밀번호 변경·재설정은 실제로 바뀌지 않는다.
6. 이메일·비밀번호 변경(`/settings/account/*`)은 정지 중에도 막지 않는다(화면·목업 모두 정지 검사 없음).

## 백엔드에 필요한 것
- 위 목업 API 9개(경로·모양·오류 코드 그대로). 토큰은 기존 `auth_tokens`(용도 `VERIFY_EMAIL` 외 재설정·이메일 변경 용도 추가), 링크 주소는 사이트 공개 주소 기준.
- 가입은 `UserPolicy`(이메일·비밀번호·닉네임) + 약관 3개 + `terms_version`(소셜 가입과 같은 `v1`), 세션은 `LoginSession.establish`, 인증 메일은 기존 `EmailVerificationService` 흐름(재발송 60초에 포함).
- 비밀번호 변경·이메일 변경의 현재 비밀번호 확인은 로그인 실패 횟수(`login_attempts`, 5회·10분)와 같이 센다. 재설정·변경 뒤 다른 세션 끊기(Spring Session JDBC).
- 이메일 변경 알림 메일(지금 주소)·재설정 메일·소셜 전용 안내 메일의 필수 안내 문구(`RequiredMail` 에 추가 필요 — 알림 문구 엔진).

## community 대비 뺀 것·바꾼 것
- 가입: 사업자 전용 계정 가입 링크(로보얌에 사업자 가입 화면 없음), 「본인인증도 없어요」(본인인증은 다른 세션 범위), 「닉네임은 30일에 1번」(로보얌은 가입 뒤 1번만 바꿀 수 있다 — `member-mypage-contract.md` 결정, 가입 닉네임 단계가 그렇게 알린다). 닉네임 칸은 별도 부품(`NicknameField`) 대신 화면 안에 둠(한 화면만 씀).
- 계정 화면: 로그아웃·회원 탈퇴 줄(탈퇴는 커머스 세션 「탈퇴 대비」 범위), 변경 대기 이메일 안내(`GET /api/me/account` 가 생기면 옮긴다). 모바일 안쪽 헤더(`InnerHeader`) 대신 설정 묶음의 돌아가기 링크.
- `/password/social` 은 community 처럼 **안내 화면**이다(비밀번호 만들기 아님 — community 에도 만들기 API 가 없다).

## 미확정
1. 소셜 전용 회원이 비밀번호를 **만드는** 기능을 둘지(요청에는 있었으나 community 에 없다 — 둔다면 API·화면을 새로 정해야 한다).
2. 온보딩 운영 원칙 문구(원본 시안 초안 — 전문 검토 대상)와 로보얌에 맞는 예시(「휴머노이드 출하량」) 여부.
3. ~~약관 본문 화면(`/terms`·`/privacy`)이 없어 가입의 「보기」 링크가 빈 화면이다.~~ → 해결: `/terms`·`/privacy` 화면이 있고, 「보기」 링크는 `useTermsSummary().showLinks`(관리자 옵션)를 따른다.
4. 가입 직후 인증 메일 재발송 60초 제한을 서버가 첫 메일에도 거는지(community 는 건다 — 화면은 가입 직후 `markSent()`).
5. 정지 회원의 이메일·비밀번호 변경 허용 여부(community 는 개인 설정이라 허용).

## 결정 (2026-10-24, 사용자 "추천대로")
- **소셜 회원 비밀번호 만들기**: 지금은 만들지 않는다(community 에도 없는 기능). `/password/social` 은 안내 화면으로 둔다.

## 결정 (2026-10-24 2차, 사용자 "추천대로" — community 정책을 로보얌에 맞춤)
- **온보딩 문구(미확정 2)**: 운영 원칙 3장은 원본 초안 문구를 쓰고, 예시만 로보얌 주제(로봇·AI)로 바꾼다. 문구 전문 검토는 오픈 전 할 일로 남긴다.
- **약관 본문(미확정 3)**: `/terms`·`/privacy` 화면은 약관 기능에서 만든다(community 에도 없다). 그 전까지 가입 약관 동의의 「보기」 링크는 숨긴다(동의 체크는 그대로 필수).
- **첫 인증 메일의 60초 제한(미확정 4)**: 건다. 서버의 재발송 60초는 가입 때 보낸 첫 메일도 센다(`login-contract.md` 「이전에 보낸 것도 포함」과 같다). 화면은 가입 직후 `markSent()` 를 유지한다.
- **정지 회원의 이메일·비밀번호 변경(미확정 5)**: 허용한다 — 개인 설정이다(community `api-account.md`). 정지 중에 막는 것은 탈퇴뿐이다.

## 서버 구현 (2026-10-24)
목업 API 10개를 서버에 만들었다(경로·모양·오류 코드는 위 표 그대로). 코드: `server/api/.../auth/controller/AccountFlowController.java`(가입·확인·메일 링크·비밀번호 찾기/재설정), `.../auth/service/{SignupService,MemberSignupWriter,EmailLinkService,PasswordResetService,PasswordResetTokenService,PasswordForgotThrottle,AccountMailSender,MemberSessionTerminator,TermsVersionProvider}.java`, `server/api/.../account/**`(`/api/me/account`·비밀번호 변경·이메일 변경). 마이그레이션 shared `V64__auth_token_email_change.sql`(용도 `CHANGE_EMAIL` + `new_email`). V65 미사용.

**처리한 「백엔드에 필요한 것」·미확정**
- 토큰: 기존 `auth_tokens`(해시만 저장) — `VERIFY_EMAIL` 24시간, `RESET_PASSWORD` 60분(`roboyam.account.reset-token-minutes`), `CHANGE_EMAIL` 24시간(`email-change-token-hours`, 새 주소는 `new_email`). 새 링크는 같은 용도의 이전 링크를 무효로 한다. 정리 배치(`R__batch_10`)는 용도와 무관해 그대로.
- 가입: `UserPolicy` + 약관 3개(`fields` 한 번에: email→password→nickname→agreeTerms→agreePrivacy→ageConfirmed) → `409 EMAIL_TAKEN` → `409 NICKNAME_TAKEN`(동시 가입의 유일 제약 위반도 같은 코드) → 저장 → `LoginSession.establish` → 201 me. 첫 인증 메일도 `auth_tokens` 행을 만들어 **재발송 60초에 센다**(미확정 4 결정).
- **약관 버전**: `server/api/src/main/java/com/community/auth/service/TermsVersionProvider.java` 의 `currentTermsVersion()` 한 곳(지금 자리표시 `v1`). 이메일 가입과 소셜 가입 마무리(`SocialAccountWriter`)가 둘 다 이것을 부른다 — 약관 기능을 합칠 때 이 몸통만 바꾼다.
- 닉네임 확인 사유 순서 LENGTH → CHARS → RESERVED → DUPLICATE(대소문자 무시). **예약어**는 새로 정했다(`UserPolicy.isReservedNickname` — admin·운영자·관리자·운영진·roboyam·로보얌, 소문자·공백/밑줄 제거 뒤 포함 여부). 닉네임 변경 API 는 마이페이지 범위.
- 메일 링크 확인(`POST /api/auth/email/verify`): 없음·모름·다른 용도·이미 씀·탈퇴 회원 → 400 `TOKEN_INVALID` → 410 `TOKEN_EXPIRED` → (변경 링크) 새 주소가 그 사이 쓰이면 409 `EMAIL_TAKEN`(링크는 쓰지 않은 채 남음). 변경 완료 = `email`·`email_verified_at` 교체 + 옛 주소의 인증 링크 무효, 다른 기기 세션은 유지.
- **계정 존재 숨김**: 비밀번호 찾기는 요청 스레드에서 형식 검사 → 이메일 해시 기준 메모리 60초 제한 → 작업 풀(2스레드·대기열 200, 넘치면 버림)에 넘기고 늘 202. 계정 조회·토큰·메일은 풀에서 하므로 응답 내용·시간이 계정 유무와 무관하다(관리자 앱 `PasswordResetMailSender` 와 같은 방식). 비밀번호 있는 회원은 DB 의 최근 발급 시각으로도 60초를 막는다. 소셜 전용 회원은 가장 먼저 연결한 공급자로 `/password/social?provider=` 안내 메일.
- 재설정: 400 VALIDATION → 토큰 400/410 → 400 SAME_PASSWORD → 교체 + 재설정 링크 모두 무효 + 로그인 실패 횟수 초기화 + **그 회원의 모든 세션 종료**. check 와 적용 사이에 비밀번호가 바뀌었으면 400 `TOKEN_INVALID`(계약에 더한 동작).
- 비밀번호 변경: 409 PASSWORD_NOT_SET → 400 VALIDATION(`fields.currentPassword`·`newPassword`) → 429 LOGIN_LOCKED → 401 CURRENT_PASSWORD_WRONG(로그인 실패와 같은 `login_attempts` 횟수) → 400 SAME_PASSWORD → 204. 성공 = **이 세션만 남기고 다른 세션 종료**, 이 세션도 ID 를 바꾼다(`changeSessionId`), 재설정 링크 무효, 실패 횟수 초기화.
- 이메일 변경: 409 EMAIL_CHANGE_NOT_ALLOWED(비밀번호 또는 이메일 없음) → 400(`fields.newEmail`·`password`) → 429 LOGIN_LOCKED → 401 → 409 EMAIL_TAKEN(지금 주소 포함) → 429 RESEND_COOLDOWN(회원당 60초) → 202(이전 변경 링크 무효, 새 주소 링크 + 지금 주소 알림을 작업 풀로 발송). 비밀번호를 맞히면 실패 횟수를 초기화한다.
- **정지 회원**의 이메일·비밀번호 변경은 막지 않는다(미확정 5 결정). 탈퇴·삭제 회원의 세션은 401.
- 세션 종료: Spring Session JDBC `findByPrincipalName(회원 ID)`(`AuthUser#getName()`, 색인 `spring_session_ix3`) → `deleteById`. 관리자 세션 표와는 닿지 않는다.
- 필수 안내 메일 4종을 `RequiredMail` 에 더함: `MEMBER_PASSWORD_RESET`·`MEMBER_PASSWORD_SOCIAL`·`MEMBER_EMAIL_CHANGE`(새 주소)·`MEMBER_EMAIL_CHANGE_NOTICE`(지금 주소), 모두 보안 메일(필수 변수 `resetLink`·`socialLink`·`changeLink`). 기본 문구는 `BuiltinMessages`·`messages_*.properties`. 공개 주소·메일 서버가 없으면 로그만 남긴다. admin-web 은 서버 카탈로그를 읽어 고칠 것 없음.
- 새 오류 코드: `TOKEN_INVALID`(400)·`TOKEN_EXPIRED`(410)·`SAME_PASSWORD`(400)·`CURRENT_PASSWORD_WRONG`(401)·`PASSWORD_NOT_SET`(409)·`EMAIL_CHANGE_NOT_ALLOWED`(409).
- 비밀번호·토큰·이메일은 로그·오류·예외에 없다(요청 DTO 는 `toString` 재정의).

**서버 쪽 남은 것**
- 비밀번호 찾기 60초 제한(메모리)은 서버마다 따로다. 가입·확인·verify·forgot 에 IP 단위 요청 한도는 없다.
- `auth_tokens.new_email` 은 평문(`users.email` 과 같은 수준, 만료 뒤 배치가 지움).
- `notificationTemplate.requiredMail.MEMBER_EMAIL_VERIFY.description` 문구를 「가입·인증 메일 다시 요청 때」로 고쳤다(이메일 변경 확인은 `MEMBER_EMAIL_CHANGE` 가 보낸다 — 사용자 지시 2026-10-24).

## 화면 연결 (2026-10-24)
- `useAuthApi`(가입·이메일/닉네임 확인·메일 링크 확인·비밀번호 찾기·재설정 확인·재설정)와 `useAccountApi`(내 계정·비밀번호 변경·이메일 변경)를 실제 호출로 바꾸고 `auth-mock.ts`·`account-mock.ts` 를 지웠다.
- 가입 201 은 실제 세션을 만든다(`useAuth.signup` 이 응답 me 를 담는 흐름 그대로). CSRF 는 `XSRF-TOKEN` 쿠키라 가입·비밀번호 변경으로 세션 ID 가 바뀌어도 따로 갱신하지 않는다. 재설정 성공 뒤 화면이 me 를 다시 읽어, 같은 계정이 로그인 중이었다면 비로그인으로 맞춰진다.
- `CURRENT_PASSWORD_WRONG`(401)은 공통 클라이언트의 「로그인 시간이 지났어요」(401 `UNAUTHENTICATED` 만)로 가지 않고 칸 오류로 붙는다.
- `VALIDATION` 칸 오류: 이메일 변경은 `newEmail`·`password` 를 함께, 가입은 email·password·nickname·약관 오류를 모두 붙이고 가장 앞 단계로 돌아간다. 새 i18n 키 없음(필요한 `error.*` 가 이미 있었다).
- `pendingEmail` 은 서버가 가린 값을 그대로 보인다.
