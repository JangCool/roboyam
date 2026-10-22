# 회원 표시 설정(공개 사이트) 계약 (서버 구현 2026-10-23 · 화면 연결 2026-10-23 — 아래 「프론트 연결 결과」)

**의도**: 같은 시각을 어느 시간대·어떤 시간/날짜 모양·어느 언어 단어로 그릴지를 서버 한 곳에서 합쳐 공개 사이트 화면에 준다. 층은 **개인 값(회원이 고른 값) → 사이트 표시 기본값(관리자 설정 > 일반 「표시 기본값」) → 처음 값**이고, 화면은 효과값 하나만 받는다.
**원본(읽기 전용)**: community `docs/api-display.md`. **화면 기준**: user-web `composables/display/api/useDisplayApi.ts`(머리말 = 규약)·`useDisplayApi.types.ts`(목업 `mock/display-mock.ts` 는 연결하며 지웠다). 관리자 운영자 표시 형식(`operator_preferences`, `server/admin/.../profile`)과 같은 값 규칙(`DisplayValuesPolicy`, 이번에 shared 로 옮김)을 쓴다.

## API (`server/api`, `ApiResponse`, 모두 `Cache-Control: no-store`)
| | 요청 → 응답 |
|---|---|
| `GET /api/display/effective` (비로그인 가능) | → `{ values, from, serviceZoneId }` |
| `GET /api/me/display` (로그인) | → `{ own, defaults, effective }` — 행을 만들지 않는다 |
| `PUT /api/me/display` (로그인·CSRF) | `{ values: { zoneId, timeFormat, dateFormat, language? } }` → `{ own, defaults, effective }` |
| `DELETE /api/me/display` (로그인·CSRF) | → `{ own: null, defaults, effective }` **200(본문 있음)** — 행이 없어도 성공 |
- `values`·`own`·`defaults`·`effective` 모양 = `{ language, zoneId, timeFormat, dateFormat }`(user-web `DisplayValues` 와 같은 네 칸). `own` 은 정한 적 없으면 `null`. `effective = own ?? defaults`.
- `from`: `USER`(개인 행 있음) · `DEFAULT`(사이트 설정 행의 기본값) · `FALLBACK`(설정 행 없음 — 설치 전엔 `/api/**` 가 503 이라 실제로는 거의 안 나온다). `serviceZoneId` 는 늘 `Asia/Seoul`.
- 오류: 틀린 칸은 한꺼번에 `400 VALIDATION` + `fields.zoneId|timeFormat|dateFormat`(+ `language` 를 보냈는데 `ko|en` 이 아니면 `fields.language`), 아무것도 저장하지 않는다. `values` 가 없거나 칸을 빼먹으면 그 칸이 오류다. 비로그인 `401 UNAUTHENTICATED`, CSRF 없음 `403 CSRF_INVALID`. 문구는 사이트 표시 기본값과 같은 번들 키 `siteSetting.display.*`.
- 경로·본문에 회원 id 가 없다(본인 것만, 세션의 `AuthUser`).

## 정책·구현
- **사이트 기본값의 출처**: `/api/site/info` 의 `displayDefaults` 와 같은 사본(`PublicSiteService`, 20초)을 읽는다 → 두 응답이 서로 어긋나지 않는다. 관리자가 기본값을 바꾸면 최대 20초 뒤 따라온다(기존 사이트 설정 전파 방식).
- **개인 값은 네 칸을 통째로 덮는다**(칸별 상속 없음). 기본값과 같은 값을 저장해도 행이 생기고 그 뒤 기본값이 바뀌어도 따라가지 않는다(되돌리기 = 행 삭제). 한 문장 upsert 대신 회원 행 잠금(`users` `PESSIMISTIC_WRITE`) 뒤 찾기 → 만들기/고치기로 같은 회원의 동시 저장을 줄 세운다. 탈퇴(`isGone`)한 계정의 저장은 `401`.
- **언어(`language`)는 선택 칸**: 화면 타입 `DisplayValues` 에 있어 함께 저장한다. 빼면 지금 개인 값의 언어, 개인 값이 없으면 사이트 기본 언어를 쓴다.
- 감사 기록 없음(회원 본인의 읽기 취향 — 운영자 감사 대상이 아니다).
- **표**: Flyway shared `V52__member_display_and_site_theme.sql` — `member_display_preferences(user_id PK → users ON DELETE CASCADE, language, zone_id, time_format, date_format, created_at, updated_at)` + 언어·시간 형식·날짜 형식 CHECK(시간대는 서비스가 검사). 엔티티·저장소는 shared(`com.community.user.entity.MemberDisplayPreferenceEntity`·`...repository.master.MemberDisplayPreferenceRepository`).
- **탈퇴 정리**: roboyam 에 아직 탈퇴 흐름이 없다(`users` 행을 남기고 상태만 바꾸는 코드 없음). 회원 행 삭제는 CASCADE 로 함께 지워지고, 탈퇴 흐름을 만들 때 그 트랜잭션에서 `MemberDisplayPreferenceRepository.deleteById(userId)` 를 부른다(저장소 주석에 적어 둠).

## 프론트 연결(`// MOCK:` → `// REAL:`) 때 주의
- 응답 모양은 목업 타입과 같다. 다른 점: ① 서버 처음 값(설정 행 없음)의 날짜 형식은 `YMD_DASH`(서버 `SiteSettingEntity.DEFAULT_*`, `/api/site/info` 와 같음) — 화면 `UtilTime.FALLBACK_DISPLAY` 는 `YMD_DOT`. ② `DELETE` 는 204 가 아니라 200 + 본문. ③ PUT 에서 `language` 를 빼도 된다(목업 `sanitize` 처럼 `ko` 로 바꾸지 않고 지금 값을 유지).
- `useDisplaySettings` 의 「`/api/site/info` 의 `displayDefaults` 로 바꿔 끼우기」는 이제 서버가 같은 값을 주므로 남겨 둬도 결과가 같다(지워도 된다).
- 관리자 회원 상세 `settings.display`·`displayOwn` 은 이 표로 채울 수 있다(행 있으면 그 값·`true`, 없으면 사이트 기본값·`false`) — 이번 작업은 회원 상세를 건드리지 않았다(`MemberSettings` 레코드를 다른 작업이 만들 수 있어 충돌 회피).

## 미확정
1. 회원의 `language` 를 화면 언어(i18n)로도 쓸지, 시각 단어에만 쓸지(지금은 값만 저장·전달).
2. 서버 문구(메일·오류 문구)에 회원 개인 시간대를 덧붙이는 규칙(community 「서버 문구 규칙」)은 이번 범위 밖.
3. 개인 시간대가 나중에 JDK 목록에서 빠지면 지금은 그대로 내려간다(화면이 모르는 시간대면 한국 시간으로 그린다).
4. user-web 에 회원이 개인 표시 값을 고르는 화면(「시간 표시」)이 아직 없다 — `useDisplaySettings().loadMine/saveOwn/resetOwn` 은 준비돼 있고 부르는 화면만 없다.

## 프론트 연결 결과 (2026-10-23)
- user-web `useDisplayApi`·`useThemeApi` 를 `// REAL:` 로 바꾸고 `display/api/mock/`·`theme/api/mock/` 를 지웠다. `PUT`·`DELETE` 는 `useApiClient({ refreshCsrf: auth.me })`, `DELETE` 는 `client.del<MyDisplayResponse>`(200 본문). 화면은 `language` 까지 네 칸을 보낸다.
- `useDisplaySettings` 의 폴백(실패·404 → 기억값 → 시스템 폴백)과 「사이트 정보의 `displayDefaults` 로 바꿔 끼우기」는 그대로 뒀다(결과가 서버와 같고, 효과값을 받기 전 첫 값을 맞춘다).
- **시스템 폴백 날짜 형식을 서버에 맞췄다**: 공유 레이어 `@roboyam/display-time` `UtilTime.FALLBACK_DISPLAY` 를 `YMD_DOT` → `YMD_DASH`(서버 `SiteSettingEntity.DEFAULT_DATE_FORMAT`). 두 앱 공용이라 admin-web 도 값을 받기 전·로그아웃 뒤 그림이 대시 형식이 된다(받은 뒤는 서버 값이라 그대로). 같은 규칙으로 고르개의 모르는 형식 대체값(`UtilDatePicker`)과 날짜 형식 선택지 순서(`UtilDisplayChoices.DATE_FORMATS`, 처음 기본값이 먼저)도 `YMD_DASH` 를 앞으로 했다.
- 일회용 환경에서 확인: 비로그인 `effective`(from `DEFAULT`, `YMD_DASH`)·`me` 401, 로그인 뒤 `saveOwn`→효과값 즉시 적용·새로고침 뒤 `from: USER` 유지, 틀린 시간대 400 `fields.zoneId`, `resetOwn` 200 `own: null`, 공개 테마 `{theme:'default'}`(`max-age=60`·`ETag`).
