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
- **`adminUrl`**: **항상 `null`** (2026-10-21 변경). 운영자는 `operators` 별도 계정이라 공개 사이트에 로그인하지 않는다(`docs/domain/operator-permissions.md`). 응답 칸은 user-web 타입 호환을 위해 남겨 둔다. 공개 서버는 더 이상 `ADMIN_URL` 을 읽지 않는다. 공개 `role` 은 `USER` 뿐이다(`users.role` CHECK).
- **인증 메일 재발송**: `auth_tokens`(`VERIFY_EMAIL`, 유효 24시간, 새 링크를 만들면 이전 링크 무효, 토큰은 해시로 저장)를 만들고 `{공개 주소}/verify-email?token=…` 링크를 메일 설정(`mail_settings`, 설치 마법사가 저장)으로 보낸다. **60초에 한 번**(이전에 보낸 것도 포함). 메일 서버가 설정돼 있지 않거나 공개 주소가 없으면 보내지 못하고 로그만 남기며 `202` 로 끝난다. **인증 링크를 받아 처리하는 `POST /api/auth/email/verify` 와 `/verify-email` 화면은 이번 범위가 아니다**(회원가입 기능에서 만든다).
- 비밀번호·토큰·세션 값은 로그·오류·`toString` 에 남기지 않는다. 입력 규칙: 이메일 형식 254자 이하(소문자로 바꿔 비교), 비밀번호는 비어 있지 않음(로그인은 가입 규칙을 다시 검사하지 않는다). 72바이트를 넘는 비밀번호도 500 이 아니라 `401` 로 끝나야 한다.
- 로그인한 사용자 객체를 보안 컨텍스트에 어떻게 싣는지(권한 이름 등)는 이후 기능이 쓰기 쉽게 `ROLE_USER`·`ROLE_STAFF`·`ROLE_ADMIN` 으로 둔다.

## 이번 범위 밖 (화면은 목업 유지)
메뉴·게시판·알림·쪽지·포인트 API, 회원가입, 이메일 인증 처리, 비밀번호 재설정, 제재 상세, 관리자 앱 로그인. 프론트의 해당 `useXxxApi` 는 `// MOCK:` 로 남는다. (소셜 로그인은 아래 「소셜 로그인」 절 — 서버 구현 2026-10-23)

## 미확정 / 다음 작업
- 프론트의 `useAuthApi` 중 me·login·logout·providers·resend 만 `// REAL:` 로 교체한다(백엔드 완료 후). 교체하면 시드 계정(`seed/seed.json`) 목업 로그인은 쓸 수 없다 — 실제 로그인은 설치 마법사로 만든 루트 계정으로 한다. 시험용 시드 로더는 필요할 때 만든다.
- 비밀번호는 UTF-8 72바이트(BCrypt 한도)를 넘으면 거절한다(`UserPolicy.passwordViolation` — 설치 마법사가 쓰고, 회원가입·비밀번호 변경도 같은 메서드를 쓴다). 화면 사전 검사(`UtilValidation.password`)도 같은 순서로 판정한다.
- **`login_attempts` 오래된 행 정리는 관리자 앱 몫이다** — 서버(api)에는 배치를 두지 않는다. PostgreSQL `pg_cron` 이나 관리자 앱의 운영 작업으로 처리한다(제안: 매일 한 번, `updated_at` 이 24시간 넘고 `locked_until` 이 비었거나 그 전인 행 삭제. 잠금 창은 10분이라 지워도 판단에 영향이 없다).
- 약관 버전(`terms_version`)은 약관 기능에서 정한다.

## 소셜 로그인 (서버 구현 2026-10-23, 화면 연결은 다음 단계)
**의도**: 카카오·네이버·구글 OAuth 2.0 인가 코드 흐름으로 로그인·가입·연결한다. 처음 보는 소셜 계정은 바로 가입시키지 않고 가입 완료 화면(약관·닉네임)을 거치며, 이메일이 겹쳐도 **자동 연결하지 않는다**(계정 탈취 방지). 코드: `server/api/.../sociallogin/**`.

### 흐름·API (JSON 은 공통 래퍼의 `data`/`error`, 모두 `Cache-Control: no-store`)
| | 요청 | 응답 |
|---|---|---|
| `GET /api/auth/social/{provider}/start?next=` | 페이지 이동(`location.assign`) | `302` 공급자 인가 주소 · `404 PROVIDER_NOT_FOUND`(모름·꺼짐·키 없음·암호화 키/공개 주소 없음 — 구분 안 함) · `400 VALIDATION`(`fields.next`) · `429 SOCIAL_RATE_LIMITED`(+`retryAfterSeconds`) |
| `GET /api/auth/social/{provider}/callback` | 공급자가 부름 | 늘 `302` 사용자 앱 경로(아래 표) |
| `GET /api/auth/social/pending` | — | `200 { provider, providerName, suggestedNickname, emailMasked, emailVerified, next }` · `404 NO_PENDING` |
| `POST /api/auth/social/complete` | `{ nickname, agreeTerms, agreePrivacy, ageConfirmed }` | `201` me 와 같은 모양(가입 즉시 로그인) · `400 VALIDATION`(`fields` 순서 nickname→agreeTerms→agreePrivacy→ageConfirmed) · `409 NICKNAME_TAKEN` · `409 EMAIL_TAKEN`·`409 SOCIAL_ALREADY_REGISTERED`(그 사이 생김 — 로그인 화면으로) · `404 NO_PENDING` · `429` |
| `GET /api/me/social` (로그인) | — | `200 { hasPassword, providers: [{ id, name, linked, linkable, canUnlink }] }` (카카오·네이버·구글 순 3개) |
| `POST /api/me/social/{provider}/link` (로그인·CSRF) | — | `200 { redirectUrl }`(공급자 인가 주소 — 화면이 `location.href`) · `404 PROVIDER_NOT_FOUND` · `409 SOCIAL_ALREADY_LINKED` · `429` |
| `DELETE /api/me/social/{provider}` (로그인·CSRF) | — | `204` · `404 SOCIAL_NOT_LINKED` · `409 SOCIAL_LAST_LOGIN_METHOD`(비밀번호 없는 회원의 마지막 연결) |

`{provider}` = `kakao`·`naver`·`google`. 버튼 노출은 기존 `GET /api/auth/providers`(켜짐 + 키 있음 + **암호화 키 있음**으로 조였다)를 그대로 쓴다 — 사이트 정보에는 더하지 않았다.

**콜백 결과 → 사용자 앱 경로**(같은 사이트 상대 경로만, 쿼리는 정해진 코드와 공급자 id 뿐)
| 결과 | 경로 |
|---|---|
| 연결된 소셜 계정 로그인 | `next`(시작 때 검사한 값) 또는 `/` |
| 처음 보는 소셜 계정 | `/signup/social` (서버 세션에 가입 대기) |
| 처음 보는 소셜 계정 + 이메일이 기존 회원과 같음 | `/login?error=email_exists&provider=kakao` |
| 로그인 실패 | `/login?error=social_failed&provider=kakao&reason=…` (state 를 못 찾으면 `provider` 없음) |
| 연결 성공(이미 이 회원에 연결돼 있던 경우 포함) | `/settings/account?linked=kakao` |
| 다른 회원에 연결된 소셜 계정 | `/settings/account?link=taken&provider=kakao` |
| 연결 실패 | `/settings/account?link=failed&provider=kakao&reason=…` |

`reason`: `cancelled`(동의 거부) · `invalid_state`(없음·위조·재사용·다른 브라우저·로그아웃으로 흐름이 사라짐) · `expired`(10분 초과) · `provider_error`(토큰 교환·회원 정보·ID 토큰 검증 실패, 그 사이 꺼짐) · `account_unavailable`(연결된 회원이 탈퇴) · `session_changed`(연결을 시작한 회원과 지금 회원이 다름) · `already_linked`(이 회원에 그 공급자의 다른 계정이 있음).

### 정책(구현)
- **state** 256비트 일회용(세션에 해시만, 콜백에서 꺼내며 지움, 한 세션 최대 3개), 유효 10분(`roboyam.social.state-ttl`). **PKCE S256**: 카카오·구글(네이버는 미지원 — state 만). **구글** `nonce`(해시 보관) + ID 토큰 `iss`·`aud`(+여럿이면 `azp`)·`exp`(60초 차이 허용)·`iat`·`nonce`·`sub` 확인. 서명은 확인하지 않는다 — 토큰 엔드포인트에서 TLS 로 직접 받은 ID 토큰이라 OIDC Core 3.1.3.7(6)에 따라 TLS 서버 확인으로 대신한다. `alg=none` 거절.
- **`next`**: `/` 로 시작, `//`·`/\`·`/%2f`·`/%5c` 시작 불가, 역슬래시·공백·제어·비ASCII 불가, 512자 이하, 스킴·호스트 없음. 어기면 시작을 400 으로 거절한다(조용히 `/` 로 바꾸지 않는다). 연결 흐름은 `next` 를 받지 않는다.
- **세션**: 로그인·가입 완료 때 `LoginSession.establish`(옛 세션 폐기 → 새 ID, 30일). 가입 대기를 둘 때도 `changeSessionId`. 비로그인으로 새로 만든 세션은 기한을 흐름+가입 대기(25분)로 짧게 둔다. 로그인 기록은 이메일 로그인과 같이 `last_login_at` 갱신 + 서버 로그(공급자·회원 ID). 감사 기록(운영자용 `audit_logs`)은 이메일 로그인과 같이 남기지 않는다.
- **로그인 불가 상태**: 탈퇴·삭제 회원은 `account_unavailable`. 정지·영구 정지는 이메일 로그인과 같이 로그인되고 `restriction` 으로 알린다.
- **이메일 겹침**: 처음 보는 소셜 계정의 공급자 이메일이 기존 회원과 같으면(확인 여부 무관) 연결·가입하지 않고 `email_exists`. 연결은 로그인한 회원이 `POST /api/me/social/{provider}/link` 로 시작한 흐름에서만, 콜백이 **같은 회원의 세션**으로 돌아올 때만 된다. 한 회원에 한 공급자 하나, 같은 소셜 계정은 한 회원에만(유일 제약 `ux_user_identities_provider`).
- **가입 대기**: 서버 세션에만(공급자·회원 번호·이메일·검증 여부·닉네임 후보·next), 유효 15분(`pending-ttl`), 공급자 토큰 없음. **가입**: 비밀번호 없음, 이용약관·개인정보·만 14세 동의를 `terms_agreed_at`·`age_confirmed_at` = 지금(개인정보 동의는 따로 저장하는 칸이 없다 — 관리자 회원 상세와 같음), `terms_version` = `v1`(자리표시자), 닉네임은 `UserPolicy.nicknameViolation` + 중복(대소문자 무시). 이메일은 **공급자가 확인했다고 한 경우만** `email_verified_at` 을 채운다(카카오 `is_email_valid && is_email_verified`, 구글 `email_verified`, 네이버는 알려 주지 않아 늘 미확인) — 미확인이면 가입 뒤 인증 메일(`auth_tokens` VERIFY_EMAIL, 기존 재발송 흐름)을 시작한다. 닉네임 후보는 카카오 프로필 닉네임·네이버 별명 중 규칙에 맞는 것만(실명 안 씀, 구글은 늘 null).
- **비밀**: 클라이언트 시크릿은 콜백에서만 복호화, 공급자 액세스 토큰은 회원 정보를 읽고 버린다(저장 안 함). 시크릿·코드·state·토큰·이메일·회원 번호는 로그·응답·예외에 없다. 외부 호출 접속 3초·응답 5초·본문 64KB·리다이렉트 안 따라감, 공급자 응답 본문은 사용자에게 돌려주지 않는다.
- **공급자 주소는 코드 고정**(`SocialEndpoints`). 시험용 덮어쓰기 `roboyam.social.test-endpoints.enabled/base-url`(env `ROBOYAM_SOCIAL_TEST_ENDPOINTS_ENABLED`·`_BASE_URL`, 기본 꺼짐)은 **local 프로필이고 dev·prod 가 아니며 주소가 루프백(숫자 IP·localhost)일 때만** 쓰이고, 그 밖에는 경고 로그를 남기고 무시한다.
- **요청 한도**(IP, 서버 메모리, 10분 창): 시작(로그인·연결) 30번, 가입 완료 10번 → `429 SOCIAL_RATE_LIMITED`. 서버가 여러 대면 대수만큼 늘 수 있다.
- **콜백 URL** = 사이트 공개 주소(`site_settings.public_url`, 설치 마법사·설정) + `/api/auth/social/{provider}/callback`. 공개 주소가 없으면 시작이 404.

### 가정·미해결
- **공급자 콘솔에 등록할 Redirect URI**: `{공개 주소}/api/auth/social/kakao/callback` · `/naver/callback` · `/google/callback`. 공개 주소를 바꾸면 콘솔도 바꿔야 한다. 운영은 `/api/**` 가 공개 주소 호스트에서 api 서버로 가야 한다(설치형은 같은 서버가 화면도 내준다, 로컬 Nuxt 개발 서버는 `/api` 프록시).
- 카카오 PKCE(`code_challenge`)와 카카오 동의 항목(이메일·닉네임)은 실제 콘솔에서 확인이 필요하다(가짜 공급자로만 검증). 구글 ID 토큰 서명 검증(JWKS)은 하지 않았다(위 근거).
- 화면 경로 `/signup/social`·`/settings/account` 는 user-web 에 아직 없다(다음 단계에서 만든다). 연결 결과 쿼리 모양은 community `api-account.md` 와 같게 두었다.
- `terms_version` 실제 값, 가입 완료 동시 요청의 `409 SOCIAL_ALREADY_REGISTERED`/`EMAIL_TAKEN` 뒤 가입 대기는 남는다(15분 뒤 만료 — 화면은 이 두 코드에서 로그인 화면으로 보낸다). 이메일 겹침 안내는 다른 소셜로 가입할 길이 없다(이메일 가입 화면이 아직 없다). 로그인 연속 실패 잠금(`login_attempts`)은 소셜과 무관하다. 마이그레이션은 없다(V53 미사용).

## 소셜 로그인 프론트 연결 결과 (2026-10-23)
- **실제 연결**: `useAuthApi.startSocialLogin(id, next)` → `location.assign('/api/auth/social/{id}/start?next=…')`. `next` 는 서버 `SocialReturnPaths` 규칙을 화면이 미리 보고(`UtilRedirect.socialNext` — 비ASCII 는 퍼센트 인코딩, 맞지 않으면 생략) 맞을 때만 붙인다. 버튼은 기존대로 `GET /api/auth/providers` 를 따른다. 가입 대기·완료는 `useAuthApi.socialPending`·`completeSocialSignup`(+`useAuth.completeSocialSignup`), 연결 관리는 `composables/auth/api/useMeSocialApi.ts`.
- **`/login`**: `error=email_exists|social_failed|social_expired` 안내에 `reason` 문구(`login.reason*`, 모르는 값은 일반 문구)를 더했다. `provider` 가 없으면 "소셜". 이메일 겹침 안내의 길 이름은 「사용자 메뉴 › 계정·로그인 연결」.
- **`/signup/social`**(레이아웃 `auth`, `guest`): 공급자·마스킹 이메일·닉네임 기본값, `emailVerified=false` 이고 이메일이 있으면 인증 메일 안내. 약관 동의는 원본 `TermsAgreement` 를 옮긴 `components/auth/TermsAgreement.vue`. 201 → `UtilSanction.afterLoginPath(restriction, pending.next ?? '/')`. 404 → `/login?error=social_expired`, `NICKNAME_TAKEN` → 닉네임 칸, `EMAIL_TAKEN` → `/login?error=email_exists&provider=…`, `SOCIAL_ALREADY_REGISTERED` → 토스트(서버 문구) + `/login`. `auth` 레이아웃에 토스트(`AppToaster`)를 두었다.
- **`/settings/account`**(`middleware: 'auth'` 새로 둠 — 비로그인은 `/login?next=`): 로그인 수단(비밀번호 유무) + 공급자 3줄(연결·해제, 꺼진 공급자는 단추 없음, 마지막 수단은 해제 비활성). 돌아온 `linked`·`link=taken`·`link=failed&reason` 은 토스트/안내로 보이고 쿼리를 지운다. 들어가는 길은 헤더 프로필 메뉴의 고정 항목 「계정·로그인 연결」(user-web 은 메뉴 관리의 MYPAGE 그룹을 그리지 않는다 — 관리자 레지스트리 `SETTINGS_ACCOUNT` 는 이미 이 경로다).
- **검증**: 일회용 DB·jar(9061)·가짜 공급자(카카오·네이버 모양)·`nuxt dev` 3267, 공개 주소 `http://localhost:3267` 로 콜백이 프록시를 거쳐 돌아오는 것 확인. 신규 가입 완료(미확인·확인 이메일)·닉네임 중복·이메일 겹침·취소·invalid_state·가입 대기 만료·연결 성공/취소/taken·해제·마지막 해제 409 거절·연결된 소셜 로그인 후 `next`(한글 경로) 복귀.
- **미확정**: 연결된 소셜로 로그인한 정지 회원의 제재 안내 착륙(원본 `useLoginLanding`)은 없다. `/signup`(이메일 가입)·`/password/forgot`·`/terms`·`/privacy` 화면은 아직 없어 링크가 비어 있다. 구글은 가짜 공급자로 시험하지 않았다.

## 관리자 소셜 로그인 설정 (서버 구현 2026-10-23, 화면은 다음 단계)
**의도**: 설치 마법사에서만 넣던 카카오·네이버·구글 클라이언트 ID·시크릿을 관리자 앱에서 보고·고치고·지운다. 화면은 community `admin/app/pages/social-login.vue`·`SocialProviderCard.vue` 를 옮기고, API 모양은 community `docs/api-admin-social.md` 를 따르되 roboyam 차이를 더했다. 코드: `server/admin/.../social/**`, shared `com.community.social`.

### API (`/api/admin/social-providers`, 공통 래퍼, 모두 `Cache-Control: no-store`, 쓰기는 CSRF)
| | 권한 | 요청 | 응답 |
|---|---|---|---|
| `GET /api/admin/social-providers` | `site.read` | — | `200 { cryptoKeyConfigured, publicUrlConfigured, providers: [항목 ×3] }` |
| `PUT /api/admin/social-providers/{provider}` | `site.secret` | `{ clientId, clientSecret?, enabled? }` | `200 항목` · `400 VALIDATION`(`fields.clientId`→`clientSecret`→`enabled` 한꺼번에) · `404 PROVIDER_NOT_FOUND` · `503 CRYPTO_KEY_MISSING` · `403 PERMISSION_DENIED` |
| `DELETE /api/admin/social-providers/{provider}` | `site.secret` | — | `204`(없는 설정도 204, 멱등) · `404 PROVIDER_NOT_FOUND` |
| `GET /api/admin/social-providers/history?provider&page&size` | `site.read` | provider 생략 = 전체, page 0부터(기본 0), size 1~100(기본 20) | `200 { items, total, page, size }` · `400 VALIDATION`(`fields.provider`·`page`·`size`) |

- `{provider}` = `KAKAO`·`NAVER`·`GOOGLE` 또는 소문자 id(대소문자 무시). 목록 순서는 **카카오 · 네이버 · 구글 고정**(공개 로그인 버튼과 같다 — community 의 NAVER 먼저와 다르다).
- **항목** `{ provider, id, label, callbackUrl, source, enabled, active, clientId, secretSet, configured, updatedAt, updatedBy }`
  - `provider` 대문자 코드, `id` 소문자(`/api/auth/providers` 의 id), `label` 요청 언어 이름.
  - `callbackUrl` = `{site_settings.public_url}/api/auth/social/{id}/callback`(공급자 콘솔에 등록할 값). 공개 주소가 없으면 `null`.
  - `source` = `SETTINGS`(행 있음) · `NONE`(미등록). roboyam 은 환경 변수 키가 없어 `ENV` 는 나오지 않는다.
  - `enabled` = 저장된 켜짐 값(미등록이면 false). `active` = 지금 공개 로그인 화면에 버튼이 나오는가(켜짐 + 키 + 서버 암호화 키 — `GET /api/auth/providers` 와 같은 기준).
  - `secretSet` = 시크릿이 있고 **지금 키로 풀 수 있는가**(값은 절대 내보내지 않는다). `configured` = clientId + 풀 수 있는 시크릿. 키가 없으면 풀어 볼 수 없어 `secretSet` 은 암호문 유무다.
  - `updatedAt` 마지막 저장 시각(설치 값이면 설치 시각), `updatedBy` 운영자 이름(없거나 지워졌으면 null).
- **이력 항목** `{ id, provider, providerLabel, action(CREATE|UPDATE|DELETE), source(INSTALL|ADMIN), operatorId, operatorName, operatorDeleted, changedAt, changes: [{ field, labelKey, before, after }] }` — 최신이 위(`changedAt`↓, `id`↓). `operatorName` 은 운영자를 찾을 수 없으면 요청 언어의 「삭제된 운영자」 + `operatorDeleted: true`. `labelKey` = `settingHistory.field.<field>`(admin-web i18n 이 정할 키).

### 정책
- **검사 순서**: 권한(401/403) → 공급자 이름(404) → 암호화 키(503, 입력이 틀려도 먼저 — community 와 같다) → 입력(400). `DELETE` 는 암호화 키가 없어도 된다.
- **입력**: clientId 앞뒤 공백 자른 뒤 1~200자·공백/제어 문자 없음(필수). clientSecret 1~500자·공백/제어 문자 없음, **생략·빈 값이면 저장된 값 유지**. 저장된 것이 없으면 `fields.clientSecret`(「처음 등록할 때는…」), 저장된 것을 지금 키로 풀 수 없으면 `fields.clientSecret`(「다시 입력해 주세요」). V3 스키마가 clientId·시크릿 NOT NULL 이라 「켜려면 둘 다 있어야 함」은 저장 자체의 조건이다. `enabled` 생략 = true. 오류 문구에 입력값을 싣지 않는다.
- **공개 주소가 없으면 켜기를 거절**한다(`fields.enabled`) — 콜백 주소를 만들 수 없어 공개 시작이 404 가 되기 때문. 끈 채로 저장은 된다. (설치가 공개 주소를 필수로 받고 `public_url` 이 NOT NULL 이라 실제로는 빈 값일 때만 해당)
- **바뀐 칸이 없으면 쓰지 않는다**(시각·감사·이력 그대로). 같은 시크릿을 다시 넣은 것은 저장된 값과 풀어서 비교해 「안 바뀜」으로 본다.
- **삭제** = 행을 지운다 → 그 공급자는 미등록(`NONE`)·꺼짐. 시크릿만 지우는 동작은 없다(스키마상 시크릿 없는 행이 없다 — community 의 「설정 삭제」와 같다).
- **감사**(`audit_logs`): `SOCIAL_PROVIDER_UPDATE`(만들기·고치기) · `SOCIAL_PROVIDER_DELETE`, 대상 `SOCIAL_PROVIDER` + 공급자. `enabled` 만 before/after 값, clientId·clientSecret 은 칸 이름과 `SET`(만들기)/`CHANGED` 표시만 — **clientId 원문·가린 값·시크릿은 감사에 없다**.
- **공개 반영**: 공개 서버(`SocialProviderService`·`SocialClientRegistry`)는 캐시 없이 요청마다 DB 를 읽는다 → 저장이 커밋된 직후 요청부터 `/api/auth/providers`·시작·콜백이 새 값을 쓴다(서버가 여러 대여도 같다).

### 변경 이력 (`social_provider_setting_history`, shared V53, 메일 설정 이력과 같은 규칙의 별도 표, 보관 무기한)
- 저장마다 한 줄: 공급자 · 운영자 · 시각 · 출처 `INSTALL`(설치 마법사 소셜 단계 — `SocialSettingsService.create`) / `ADMIN`(이 API) · `changes [{field, before, after}]`. 같은 값 재저장은 줄이 없다.
- 값의 모양: **clientSecret 은 값 없이 표시 코드만** `SET`(처음 넣음) · `CHANGED`(바꿈, before 는 `SET`) · `CLEARED`(설정 삭제). **clientId 는 끝 4자리만** `****abcd`(4자 이하는 `****`). `enabled` 는 `"true"`/`"false"`. 만들기는 before null, 삭제는 after null(시크릿만 `SET`→`CLEARED`).
- 이미 설치된 DB: V53 이 지금 행마다 `INSTALL · CREATE` 첫 줄을 만든다(시각 = `installation.installed_at`, 없으면 행의 `updated_at`, 운영자 = 행의 `updated_by`). 관리자 수정 API 가 없던 시점이라 지금 값 = 설치 값이다.
- `operator_id` 는 FK 없음(운영자가 지워져도 이력 유지 — `audit_logs.actor_id` 와 같다).

### 가정·미해결
- **권한**: 새 권한 없이 기존 `site.read`(조회·이력) / `site.secret`(저장·삭제, 설명 「소셜 로그인 키·메일 서버 비밀번호 같은 비밀 값을 바꿉니다」)를 썼다. `site.secret` 의 `featureReady` 는 **서버·프론트 모두 아직 false** — 화면을 붙일 때 서버 `Permission.java` 와 `util-permission-catalog.ts` 를 같이 true 로 바꾼다. `site.read` 의 i18n 설명(「일반」 화면만 언급)도 그때 고친다.
- **관리자 메뉴(제안, 레지스트리 미변경)**: `AdminScreenRegistry` 의 `menu.system` 묶음, `siteGeneral` 다음에 `s("socialLogin", "/settings/social-login", "menu.socialLogin", "pi pi-key", Permission.SITE_READ)`. 프론트가 페이지·`menu.socialLogin` 문구를 만들 때 같이 넣는다.
- 관리자 알림(`SITE_SETTINGS_CHANGED`)은 보내지 않는다(링크가 `/settings/general` 고정이라 맞지 않음) — 필요하면 화면 단계에서 정한다.
- 풀 수 없는 시크릿(키 교체)도 공개 `/api/auth/providers` 에는 버튼이 나온다(기존 공개 기준은 복호화를 해 보지 않는다) — 관리자 목록은 `active: true` · `secretSet: false` 로 보인다. 콜백에서 `provider_error` 로 끝난다.
- `social_provider_settings.updated_by` 는 `operators` FK(ON DELETE 없음, V8)라 마지막으로 저장한 운영자를 지우면 FK 오류가 날 수 있다(기존 스키마 — 이번에 고치지 않음).
- 같은 공급자를 처음 등록하는 두 요청이 동시에 오면 하나는 PK 충돌로 500 이 될 수 있다(드묾 — 행이 있으면 행 잠금으로 줄 세운다).

### 프론트 결과 (관리자 소셜 로그인 설정, 2026-10-23)
- **화면** admin-web `/settings/social-login`(메뉴 설정 › 시스템 › 「소셜 로그인」, `site.read`). 공급자 카드 3장(서버 순서, `components/social/SocialProviderCard.vue` — community 카드를 옮김: 상태 딱지 켜짐/켜 둠·안 보임/꺼 둠/미등록 + `active` 기준 설명, 콜백 주소 복사, 클라이언트 ID, 시크릿 「입력됨」(`NotificationSecretField` 재사용), 켜기, 설정 삭제) + 「설정 이력」 구획(공급자 거르기). 구획은 `AppGadget anchor` + 우측 구역 내비.
- **저장 단위** 카드마다(카드 맨 아래 [되돌리기]·[저장], 왼쪽 [설정 삭제]). 한 번에 하나만(진행 중 다른 카드 잠금). 시크릿 필수 판정 `!secretSet`, 행이 있는데 `secretSet=false` 면 카드 위 「다시 입력」 경고. `callbackUrl=null` 이면 「공개 주소를 먼저 정해 주세요」 + 일반 설정 링크(일반 화면에는 아직 공개 주소 칸이 없다 — 미확정). `cryptoKeyConfigured=false` 면 상단 경고 + 입력·저장 잠금(삭제는 됨).
- **오류** `fields.clientId|clientSecret|enabled` 는 그 칸 아래(켜기는 스위치 아래), 503 `CRYPTO_KEY_MISSING`(경고도 켬)·404·네트워크는 카드 안 한 줄. 화면 사전 검사는 `utils/util-social-login.ts`(서버 `SocialSettingsService` 규칙).
- **읽기 전용** `site.secret` 없으면 입력 readonly/잠금, 저장·삭제 단추 없음. `site.secret` `featureReady` 는 서버 `Permission`·프론트 카탈로그 모두 true 로 바꿨다.
- **코드** API `composables/social/api/useSocialProviderSettingsApi.ts`, 이력 공용 `composables/common/useSettingHistory.ts`(+`.types.ts`)·`components/common/SettingHistoryTable.vue`·`utils/util-setting-history-messages.ts`, 문구 `i18n/locales/{ko,en}/social.ts`·`settingHistory.ts`(칸 이름 `settingHistory.field.*`).
- **검증** 일회용 DB·jar(api 9111·admin 9112)·`nuxt dev` 3270: 설치 API 로 카카오(켬)·네이버(끔) 등록 → 화면에 그대로, 이력 첫 줄 「설치 · 처음 설정」. 구글 시크릿 없이 저장 → 칸 오류, 넣고 저장 → 켜짐·이력. 카카오 ID 만 바꿔 저장 → 시크릿 유지(`입력됨`). 네이버 삭제(확인창) → 미등록·이력 「삭제」(시크릿 입력함 → 지움). 공급자 거르기. `site.read` 만 가진 운영자 → 읽기 전용, 메뉴에 「일반·소셜 로그인·메일 서버」.
