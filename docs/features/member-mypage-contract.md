# 회원 마이페이지(공개 사이트) 계약 (**화면 목업 2026-10-23 — 사용자 화면 확인 대기**)

**의도**: 셸(헤더 프로필 메뉴·모바일 하단 탭·햄버거)의 「내 정보」「포인트」「알림」「계정·로그인 연결」이 실제 화면으로 가게 한다. 서버 API 가 있는 곳(로그인 정보·포인트 요약·회원 알림·표시 설정·소셜 연결)은 실제로 부르고, 없는 곳(프로필·포인트 내역·알림 설정)은 `// MOCK:`/`// REAL:` 짝 목업으로 화면을 먼저 확정한다.
**참고(읽기 전용)**: community `pages/me/{index,points}.vue`·`pages/settings/{index,profile}.vue`·`pages/notifications/*` — 로보얌에 없는 기능(스크랩·소모임·아이템샵·주문·등급 안내·쪽지·차단·메모·19금·닉네임 그림·출석 달력·교환)은 옮기지 않았다.

## 화면 (user-web, 모두 로그인 필요 — `auth` 미들웨어, 비로그인은 `/login?next=`)
| 경로 | 내용 | API |
|---|---|---|
| `/me` | 프로필 요약(뱃지·닉네임·이메일·가입일·한 줄 소개, 「프로필 편집」) · 포인트 요약 카드(잔액·오늘 적립 횟수/상한, 「포인트 내역」) · 바로가기(포인트 내역 · 알림(안 읽은 수) · 설정 4화면) · 관리자 앱(운영진만) · 로그아웃 | `GET /api/auth/me` · `GET /api/me/points` · 목업 `GET /api/me/profile` |
| `/me/points` | 보유 포인트 · 오늘 적립(글·댓글·추천 보너스 횟수/상한 막대) · 적립 규칙 요약 · 내역(전체/적립/차감 거르기, 20줄씩 더 보기) | `GET /api/me/points` · 목업 `GET /api/me/points/ledger` |
| `/notifications` | 알림 목록(20개씩 더 보기) · 묶음 거르기(전체/댓글·답글/계정) · 「안 읽은 것만」(불러온 알림 안에서) · 누르면 읽음 + 앱 안 `link` 로 이동 · 모두 읽음(거르기 중이면 그 묶음만) · 빈 상태 · 보관 일수 안내 · 알림 설정 톱니 | 실제 `GET /api/notifications` · `POST …/{id}/read` · `POST …/read-all[?filter=]` |
| `/settings` | 좁은 화면: 설정 목록 / 넓은 화면: 왼쪽 메뉴(`AppSideNav`) + 첫 화면(`/settings/profile`)으로 `replace` | — |
| `/settings/profile` | 닉네임·한 줄 소개 고치기, 하단 저장 줄. 닉네임 칸 아래 변경 안내(「가입 뒤 1번만 바꿀 수 있어요」 · 이미 바꿈 — 이미 바꿨으면 칸 잠금), 닉네임을 바꾸는 저장은 확인 대화상자(「이번에 바꾸면 앞으로는 바꿀 수 없어요」). 정지 중이면 읽기 전용 + 「제재 안내 보기」(`/sanction`), 저장 줄 없음 | 목업 `GET/PUT /api/me/profile` |
| `/settings/notifications` | 회원 이벤트별 앱 알림·이메일 받기 스위치(영역별), 잠긴 이벤트는 잠금 안내, 하단 저장 줄 | 목업 `GET/PUT /api/me/notification-settings` |
| `/settings/display` | 언어·시간대·시간 형식·날짜 형식(공용 `DisplayValueForm`), 「내 설정/사이트 기본값」 표시, 기본값으로 되돌리기, 하단 저장 줄 | 실제 `GET/PUT/DELETE /api/me/display` |
| `/settings/account` | 기존 계정·로그인 연결 화면을 묶음 안으로(내용 그대로) | 실제 `GET /api/me/social`·연결·해제 |
- 셸: 데스크톱 알림 패널에 「알림 모두 보기」(→ `/notifications`)·톱니(→ `/settings/notifications`)를 되살렸다. 헤더 메뉴·하단 탭·햄버거의 링크 주소는 그대로이고 이제 화면이 있다.
- 사업자 전용 계정(`role=BUSINESS`)은 포인트 카드·포인트 내역 줄이 없고 `/me/points` 는 안내 빈 상태.
- 저장 규칙: 세 설정 화면 모두 PUT 한 번으로 화면 전체를 저장 → 폼 끝의 `StickySaveBar`(admin-web 과 같은 모양을 user-web `components/common/` 에 둠), 바꾼 칸 수·되돌리기, 저장 안 한 변경이 있으면 떠나기 전에 확인.

## 실제 API 사용 메모
- 알림 목록 `filter` 는 `all|comment|system` 뿐이다 — **「안 읽은 것만」 서버 거르기가 없다**(화면은 불러온 것 안에서 거른다). 목록 응답의 `unreadCount` 로 셸 배지를 맞춘다(`useNotificationBadge().sync`).
- 포인트 요약의 `today.<일>.cap = 0` 이면 그 일은 「지급하지 않아요」. `attendanceAvailable=false` 면 출석 줄·규칙을 그리지 않는다. 인기글·교환권 규칙은 지급 지점이 없어 그리지 않는다.
- 시간 표시는 `useDisplaySettings().loadMine/saveOwn/resetOwn` — 응답의 효과값이 곧바로 앱 전체(언어 포함)에 적용된다. 기본값을 따르는 중에는 값을 고쳐야 저장할 수 있다(같은 값으로 「내 설정」을 만들지 않음).

## 목업 API 후보 (서버에 없음 — 화면 확정 뒤 구현)
### 프로필 `GET/PUT /api/me/profile` (로그인 401, 본인만, PUT CSRF, `no-store`)
```ts
interface NicknameChange { allowed: boolean; changeCount: number; freeLimit: number }   // community `nicknameChange` − nextChangeAt·ticketCount·ticketPrice
interface MyProfile { nickname: string; bio: string; joinedAt: string; nicknameChange: NicknameChange }
interface MyProfileRequest { nickname: string; bio: string }   // 두 칸 모두
```
- 닉네임 규칙 = 가입과 같은 `UserPolicy`(2~12자, 한글·영문·숫자·밑줄 + 글자 사이 공백 한 칸) + 예약어 → `400 VALIDATION fields.nickname`. 중복 → `409 NICKNAME_TAKEN`. 이미 한 번 바꿈 → `409 NICKNAME_CHANGE_LIMIT`. 정지·영구 정지 중 → `403 ACCOUNT_RESTRICTED`(읽기 `GET` 은 된다).
- `nicknameChange`: `allowed`(지금 바꿀 수 있나 = `changeCount < freeLimit`) · `changeCount`(가입 때 정한 닉네임은 세지 않음) · `freeLimit`(서버 상수 1). 기다리는 날짜 규칙은 없다.
- 한 줄 소개: 앞뒤 공백 제거 뒤 0~100자 → 넘으면 `400 VALIDATION fields.bio`. 닉네임이 그대로면(소개만) 변경 횟수를 쓰지 않고 변경 규칙·중복 검사도 하지 않는다.
- 저장 뒤 화면은 `GET /api/auth/me` 를 다시 받아 셸 닉네임을 맞춘다(서버는 세션 정보가 아니라 DB 에서 닉네임을 읽는다는 전제).

### 포인트 내역 `GET /api/me/points/ledger?kind=EARN|USE|REVOKE&size=1~50(기본 20)&cursor=` (로그인 401, `no-store`)
```ts
interface MyPointLedgerPage { items: MyPointLedgerItem[]; hasNext: boolean; nextCursor: string | null }
interface MyPointLedgerItem {
  entryId: string; kind: 'EARN'|'USE'|'REVOKE'; reasonCode: string;  // POST·COMMENT·LIKE_BONUS·POST_REVOKE·COMMENT_REVOKE·LIKE_BONUS_REVOKE·ADMIN_ADJUST
  reason: string;            // 서버가 요청 언어로 푼 사유 이름(관리자 원장 `reason` 과 같은 번들)
  note: string | null;       // 운영 조정 사유(회원 공개 문구)
  delta: number; balanceAfter: number;
  target: { postId: string; title: string | null; available: boolean } | null;  // 글·댓글 사유만, 볼 수 없으면 available=false·title=null
  createdAt: string;
}
```
- 관리자 원장(`point_ledger`)의 본인 줄만, 최신이 위(`id` 내림차순 커서). 오류 `400 VALIDATION fields.kind|size|cursor`. 운영자 이름·회원 정보는 싣지 않는다.

### 알림 설정 `GET/PUT /api/me/notification-settings` (로그인 401, 본인만, PUT CSRF, `no-store`)
```ts
interface MyNotificationSettings { events: MyNotificationSetting[]; emailReceivable: boolean; emailBlockedReason: 'NO_EMAIL'|'NOT_VERIFIED'|null }
interface MyNotificationSetting {
  code: MemberNotificationType; area: 'content'|'member'|'safety'; name: string; description: string;  // 번들 member.event.<코드>.*
  inAppOffered: boolean;   // 운영자 설정 enabled && inApp
  emailOffered: boolean;   // 운영자 설정 enabled && email
  memberCanTurnOff: boolean; lockReason: 'ACCOUNT'|'ACTION'|null;
  inApp: boolean; email: boolean;   // 내 선택 — 잠긴 이벤트·운영자가 안 보내는 채널은 늘 true
}
// PUT { events: [{ code, inApp, email }] } — 끌 수 있는 이벤트만 → MyNotificationSettings
```
- 카탈로그(`MemberEvent`) 중 준비 중이 아닌 것만, 카탈로그 차례. 오류: `400 VALIDATION fields.events[i].code|inApp|email` · `409 NOTIFICATION_SETTING_LOCKED`(잠긴 이벤트를 끄려 함).
- 적용 지점(서버 몫): 발행기가 `enabled && inApp && 회원 inApp` 일 때 알림함 줄을, `enabled && email && 회원 email` 일 때 메일을. 표 후보는 `member-notification-contract.md` 미확정 1(`member_notification_mutes(user_id, kind, channel …)`).

## 목업 가정
1. 프로필 닉네임 변경: 가입 뒤 1번만(목업 `FREE_LIMIT` = 1, 날짜 대기 없음). 확인 대화상자는 화면이 묻는다(서버 몫 아님). 정지 여부는 로그인 정보 `restriction`(정지·영구 정지만 — 경고는 아님)으로 미리 알고, 저장 때 `403 ACCOUNT_RESTRICTED` 가 와도 읽기 전용으로 바꾼다. 예약어 `admin·운영자·관리자·roboyam`, 중복 시험 값 `taken`·`사용중`. 목업은 서버 닉네임을 실제로 바꾸지 않는다(새로고침하면 처음 값).
2. 가입일·한 줄 소개는 로그인 정보(`/api/auth/me`)에 없어 프로필 API 가 준다.
3. 포인트 내역 목업 45줄은 요약 잔액과 맞지 않을 수 있다. 「사용(USE)」은 교환 기능이 없어 거르기에 두지 않았다.
4. 알림 설정 목업의 운영자 값: 5종 모두 앱 알림 켬, 이메일은 「내 글에 댓글」「포인트 회수」만. 운영자가 어느 채널로도 보내지 않는 이벤트는 화면에서 감춘다.
5. 개발 스위치(`?mock=`): `profile-fail`·`profile-save-fail`·`profile-used-up`(이미 1번 바꿈)·`profile-stale`(저장 때 `409 NICKNAME_CHANGE_LIMIT` — 다른 탭에서 먼저 바꾼 경우)·`profile-restricted`(저장 때 `403 ACCOUNT_RESTRICTED`) · `ledger-fail`·`ledger-empty` · `notify-settings-fail`·`notify-settings-save-fail`·`notify-settings-no-email`.

## 미확정
1. 알림 「안 읽은 것만」을 서버 거르기(`filter=unread` 또는 `unread=true`)로 둘지 — 지금은 불러온 것 안에서만.
2. 닉네임 변경 주기·예약어 목록의 주인(사이트 설정? 고정?), 닉네임 변경권(교환 기능)과의 관계.
3. 정지 회원의 프로필 수정 허용 여부, 한 줄 소개를 공개 프로필에도 보일지(공개 프로필 화면이 없다).
4. 알림 설정의 이메일 받기가 「수신 동의」(`message-template-contract.md` 미해결)와 같은 것인지.
5. `StickySaveBar`·`AppSideNav` 를 두 앱 공용 레이어로 옮길지(지금은 user-web 에 같은 모양으로 따로 있다).

## 결정 (2026-10-24, 사용자 "추천대로" — community 정책을 로보얌에 맞춤)
- **「안 읽은 것만」(미확정 1)**: 화면 거르기(불러온 알림 안에서)를 유지한다. 서버 거르기는 묶음 `filter` 뿐이다(community 와 같다).
- **닉네임 변경(미확정 2)**: **가입 때 정한 뒤 1번만 바꿀 수 있다**(2026-10-24 사용자 확정 — 처음 결정한 「무료 2회·30일 간격」을 바꿨다). 그 1번은 언제든 할 수 있고(날짜 대기 없음), 쓰고 나면 더 바꿀 수 없다(`409 NICKNAME_CHANGE_LIMIT`). **닉네임 변경권은 아이템샵이 생길 때 포인트로 사는 품목으로 만든다**(2026-10-24 사용자 확정 — 지금은 아이템샵이 없어 두지 않는다). 할 일은 아래 「후속 작업: 닉네임 변경권」. 횟수 1은 서버 상수, 예약어는 코드 고정(`UserPolicy`). 닉네임을 바꾸지 않는 저장(소개만)은 횟수를 쓰지 않는다. 이전 닉네임은 공개하지 않는다.
- **정지 회원·공개 범위(미확정 3)**: 정지 중에는 프로필(닉네임·한 줄 소개) 수정 불가 — `403 ACCOUNT_RESTRICTED`(글쓰기와 같다), 화면은 읽기 전용 + 제재 안내 링크. 한 줄 소개는 공개 프로필에 보인다(`member-activity-contract.md` 결정과 같다).
- **알림 설정의 이메일 받기(미확정 4)**: 회원이 켜고 끄는 「이메일로도 받기」 값이 회원 이벤트 이메일의 수신 여부다. 법정 광고성 정보 수신 동의는 이것과 따로이고 광고성 메일을 만들 때 정한다.
- **공용 레이어(미확정 5)**: `StickySaveBar`·`AppSideNav` 는 지금 옮기지 않는다. 두 앱 모양이 어긋나기 시작하면 `apps-packages` 로 옮긴다.

## 후속 작업: 닉네임 변경권 (아이템샵을 만들 때 — 빠뜨리지 말 것)
**확정(2026-10-24)**: 가입 뒤 1번의 변경을 쓴 다음부터는 바꿀 때마다 **아이템샵에서 포인트로 산 닉네임 변경권 1장**을 쓴다(날짜 대기 없음 — community 의 「30일 전에 앞당기기」는 로보얌에 없다). 그 전까지는 `409 NICKNAME_CHANGE_LIMIT`(더 못 바꿈)로 둔다. 규칙의 원본은 community `docs/api-profile.md` 「닉네임 변경 규칙」·`docs/api-point.md` 「교환」「닉네임 변경권」·`docs/api-shop.md`.

**규칙 (community 와 같게, 로보얌에 맞춤)**
- 사는 곳: 아이템샵 품목 `nickname-change`. 값은 포인트 설정의 `nicknameTicketPrice`(이미 저장만 되어 있다 — 기본 1,000P, `point-contract.md`). 차감은 `PointService` 한 길, 원장 이유 `TICKET_PURCHASE`. **환불 불가·유효기간 없음.** 이메일 인증 + 정지 아님.
- 쓰는 곳: `PUT /api/me/profile` 요청에 `useTicket: true`(선택, 기본 `false`).
  - 무료로 바꿀 수 있으면 `useTicket` 을 보내도 쓰지 않는다. 닉네임이 실제로 바뀔 때만 쓴다.
  - 소개만 저장·규칙 위반 `400`·중복 `409 NICKNAME_TAKEN`·정지 `403` 으로 실패하면 쓰지 않는다(같은 트랜잭션, 롤백).
  - 동시 요청: 프로필 행을 쓰기 잠금으로 읽은 뒤 센다 — 변경권 한 장이 두 번 쓰이지 않는다.
- 응답·오류 바꿀 것:
  - `MyProfile.nicknameChange` 에 `ticketCount`(보유 수)·`ticketPrice`(한 장 값, P)를 더한다.
  - `409 NICKNAME_CHANGE_LIMIT` → **`409 NICKNAME_TICKET_REQUIRED`**(무료를 다 썼고 `useTicket` 이 없거나 변경권이 없음, 최상위에 `ticketCount`·`ticketPrice`)로 바꾼다.

**지금 자리만 있는 곳 (그때 실제 값으로 바꾼다)**
| 곳 | 지금 |
|---|---|
| user-web `pages/settings/profile.vue`·`utils/util-nickname-change.ts`·`composables/profile/api/*`(types·mock) | 「다 씀」이면 잠금만. → 「변경권 사용」 확인과 `useTicket`, 보유 수 표시, 아이템샵 링크 |
| user-web `composables/point/api/usePointApi.types.ts`, server/api `MyPointService`·`PointSummaryView` | `GET /api/me/points` 의 `ticketCount` 가 늘 0 |
| server/admin `MemberGradePointAssembler`·`MemberGradePoint`, admin-web 회원 상세 | `nicknameTicketCount` = 0, 화면은 「준비 중」 |
| server/shared `PointSettingEntity`·`PointRules`, admin-web `pages/points/policy.vue` | `nicknameTicketPrice` 는 저장만 하고 쓰는 곳이 없다 |
- 데이터: 변경권 보유·사용 기록 표(community `nickname_tickets` 류)와 마이그레이션이 새로 필요하다. 아이템샵 구매 기록과 같은 구조로 둘지는 아이템샵 설계 때 정한다.
