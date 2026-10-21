# 로그인 · default 레이아웃 계약 (화면 확정 2026-10-21)

**의도**: 이메일+비밀번호 로그인과 로그인 상태 조회, 로그아웃, 인증 메일 재발송. default 레이아웃(헤더·메뉴·배너)이 `GET /api/auth/me` 로 사용자 상태를 그린다. 이번 백엔드 범위는 **인증 API 5개**뿐이다.
**상세 규칙의 출처**: community 의 `docs/api-auth.md` (`D:\project\community\workspace\community\docs\api-auth.md`) — 공통 규약·사용자 객체·오류 응답이 같다. 아래는 roboyam 에서 달라지는 점만 적는다.

## 화면
- `/login`(+ 카드 레이아웃 `auth`), default 레이아웃. 타입·동작의 기준은 화면 코드: `apps/user-web/app/composables/auth/api/useAuthApi.types.ts`, `composables/auth/api/useAuthApi.ts`(`// REAL:` 줄이 실제 호출 모양), `utils/util-auth-messages.ts`, `pages/login.vue`.

## API (이번 범위)
아래 JSON·오류 모양은 공통 응답 래퍼의 `data`/`error` 내용이다(`.claude/guides/backend/exception.md`).

| | 요청 | 응답 |
|---|---|---|
| `GET /api/auth/me` | — | `200 { authenticated, user, restriction, adminUrl }` (항상 200, CSRF 쿠키를 내려준다) |
| `GET /api/auth/providers` | — | `200 { providers: [{ id, name }] }` — `social_provider_settings` 에서 **활성이고 키가 있는 것만**(없으면 빈 배열) |
| `POST /api/auth/login` | `{ email, password }` | `200` me 와 같은 모양 · `401 INVALID_CREDENTIALS` · `429 LOGIN_LOCKED`(+`retryAfterSeconds`, `Retry-After`) · `400 VALIDATION` |
| `POST /api/auth/logout` | — | `204` (비로그인이어도 204) |
| `POST /api/auth/email/resend` | — | `202` · `409 ALREADY_VERIFIED` · `429 RESEND_COOLDOWN`(+`retryAfterSeconds`) · `401 UNAUTHENTICATED` |

## 정책
- **로그인 실패**: 이메일이 없든, 비밀번호가 틀리든, 소셜만 가입한 계정(비밀번호 없음)이든, 탈퇴한 계정이든 **같은 `401 INVALID_CREDENTIALS`**. 없는 이메일에도 비밀번호 해시 비교를 한 번 해서 응답 시간으로 계정 존재를 알 수 없게 한다.
- **잠금**: **이메일 단위** 5회 연속 실패 시 10분 잠금(`login_attempts`). 5번째 틀린 응답까지는 `401`, 그 뒤가 `429`, 잠긴 동안은 맞는 비밀번호도 `429`, 잠금이 풀리면 처음부터 다시 센다, 성공하면 횟수를 지운다. 없는 이메일도 같은 방식으로 센다.
- **세션**: Spring Session JDBC, 쿠키 `SESSION`(HttpOnly, SameSite=Lax), 마지막 활동 뒤 **30일**(활동하면 연장). 로그인 성공 때 **세션 ID 를 바꾼다**(세션 고정 방지). 로그아웃은 세션을 없앤다. 설치 마법사 인증 세션(30분)과 섞이지 않게 한다(설치 인증 표시는 세션 단위로 따로 둔다).
- **로그인 상태 계정**: `status = ACTIVE` 는 정상. `SUSPENDED`·`BANNED` 도 로그인은 되고 응답의 `restriction` 으로 알린다. `WITHDRAWN`·`deleted_at` 이 있는 계정은 로그인 불가(`INVALID_CREDENTIALS`). 성공하면 `users.last_login_at` 을 갱신한다.
- **`restriction`**: 제재 표(`sanctions`)는 아직 없다. `status` 가 `SUSPENDED`/`BANNED` 일 때만 `{ status, until: null, pendingNotices: 0 }`, 그 밖에는 `null`. 기간·경고 상세는 제재 기능을 구현할 때 채운다.
- **`user` 객체**: `id` 는 **문자열**(Snowflake). `grade` 는 `profiles.grade` 1~4 → `SEEDLING`·`REGULAR`·`LOYAL`·`VETERAN`, `gradeLabel` 은 새싹·일반·단골·베테랑. `emailVerified` 는 `email_verified_at` 유무. `authMethods` 는 비밀번호가 있으면 `EMAIL` + `user_identities` 의 공급자(`KAKAO`·`NAVER`·`GOOGLE`). `businessStatus` 는 `null`(사업자 기능 없음).
- **`adminUrl`**: 환경 변수 `ADMIN_URL`(`roboyam.admin-url`)이 있고, `role = ADMIN` 이며 `status = ACTIVE` 일 때만 값이 있다. 그 밖에는 `null`(운영진 메뉴 권한은 아직 없다).
- **인증 메일 재발송**: `auth_tokens`(`VERIFY_EMAIL`, 유효 24시간, 새 링크를 만들면 이전 링크 무효, 토큰은 해시로 저장)를 만들고 `{공개 주소}/verify-email?token=…` 링크를 메일 설정(`mail_settings`, 설치 마법사가 저장)으로 보낸다. **60초에 한 번**(이전에 보낸 것도 포함). 메일 서버가 설정돼 있지 않거나 공개 주소가 없으면 보내지 못하고 로그만 남기며 `202` 로 끝난다. **인증 링크를 받아 처리하는 `POST /api/auth/email/verify` 와 `/verify-email` 화면은 이번 범위가 아니다**(회원가입 기능에서 만든다).
- 비밀번호·토큰·세션 값은 로그·오류·`toString` 에 남기지 않는다. 입력 규칙: 이메일 형식 254자 이하(소문자로 바꿔 비교), 비밀번호는 비어 있지 않음(로그인은 가입 규칙을 다시 검사하지 않는다). 72바이트를 넘는 비밀번호도 500 이 아니라 `401` 로 끝나야 한다.
- 로그인한 사용자 객체를 보안 컨텍스트에 어떻게 싣는지(권한 이름 등)는 이후 기능이 쓰기 쉽게 `ROLE_USER`·`ROLE_STAFF`·`ROLE_ADMIN` 으로 둔다.

## 이번 범위 밖 (화면은 목업 유지)
메뉴·게시판·알림·쪽지·포인트 API, 회원가입, 이메일 인증 처리, 비밀번호 재설정, 소셜 로그인 흐름, 제재 상세, 관리자 앱 로그인. 프론트의 해당 `useXxxApi` 는 `// MOCK:` 로 남는다.

## 미확정 / 다음 작업
- 프론트의 `useAuthApi` 중 me·login·logout·providers·resend 만 `// REAL:` 로 교체한다(백엔드 완료 후). 교체하면 시드 계정(`seed/seed.json`) 목업 로그인은 쓸 수 없다 — 실제 로그인은 설치 마법사로 만든 루트 계정으로 한다. 시험용 시드 로더는 필요할 때 만든다.
- 비밀번호는 UTF-8 72바이트(BCrypt 한도)를 넘으면 거절한다(`UserPolicy.passwordViolation` — 설치 마법사가 쓰고, 회원가입·비밀번호 변경도 같은 메서드를 쓴다). 화면 사전 검사(`UtilValidation.password`)도 같은 순서로 판정한다.
- **`login_attempts` 오래된 행 정리는 관리자 앱 몫이다** — 서버(api)에는 배치를 두지 않는다. PostgreSQL `pg_cron` 이나 관리자 앱의 운영 작업으로 처리한다(제안: 매일 한 번, `updated_at` 이 24시간 넘고 `locked_until` 이 비었거나 그 전인 행 삭제. 잠금 창은 10분이라 지워도 판단에 영향이 없다).
- 약관 버전(`terms_version`)은 약관 기능에서 정한다.
