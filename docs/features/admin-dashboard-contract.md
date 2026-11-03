# 관리자 대시보드 계약 (화면 확정 2026-11-03)

**의도**: 관리자 첫 화면(`/`)을 가젯 격자로 바꾼다. 운영자마다 자기 구성(넣기·빼기·옮기기·크기·가젯 설정)을 따로 저장하고, 업무별 가젯 32종에서 고른다. 가젯 데이터는 **서버에 부담이 없는 수준**(아래 「서버 부담 원칙」)만 보인다.
**사용자 결정(2026-11-03)**: 가젯형 · 운영자별 편집 · 처리할 일 카드 아래 링크 줄 고정 · 숫자 칸은 한 줄 행(이름 왼쪽·숫자 오른쪽) 또는 작은 카드 · **격자 48칸 · 28px 걸음**(세밀한 조절) · 최소 크기 6분의 1 폭 · 124px 높이 · 줄인 뒤 옆 빈칸은 자동으로 메우지 않음 · 배경색은 ⚙ 에서 고르고 **추천 색은 중요한 가젯만**(처리할 일·커머스 호박, 신고 장미) · **기본 구성 = 확정 때 운영자가 꾸민 배치**.

## 화면 (기준 코드 `apps/admin-web`)
- `pages/index.vue` — 제목 줄 [화면 편집] · 편집 도구 줄 [가젯 추가] [기본 구성으로] [취소] [저장] · 나갈 때 저장 안 한 변경 확인. lg 미만은 한 열(읽는 순서)이고 편집 불가.
- `components/dashboard/DashboardGrid.vue`(격자 · 끌기 · 모서리 크기 · 키보드 ←→ 한 칸, ↑↓ 위아래 가젯과 자리 바꿈, Shift+방향키 크기 · 배경색 칠하기) · `DashboardGadgetFrame.vue`(머리 「HH:mm 기준」·바로 가기·새로 고침, 로딩·오류·권한 없음·빈 상태) · `DashboardAddDialog.vue` · `DashboardGadgetSettingsDialog.vue` · 종류별 `gadget/Dashboard<Kind>Gadget.vue`.
- 계산·종류표의 원천: `utils/util-dashboard-layout.ts`(`KINDS_12` 종류표 · `DEFAULT_LAYOUT` · `RECOMMENDED_COLOR` · `read`·`setBox`·`nudge`·`add`·`compact`). 위로 당기기는 홈 배치와 같은 `UtilHomeLayout.compact`.

## 구성(저장 값)
- `{ version: 2, columns: 48, gadgets: [{ id, type, x, y, w, h, config }] | null }` — `null` = 저장한 적 없음(화면이 권한으로 기본 구성을 만든다).
- 칸: `x` 0~47 · `w` 종류별 최소 폭(대개 8 = 6분의 1)~48 · `y` 0~2000 · `h` 5~80(행 12px + 간격 16px, 가젯 높이 = h×28−16 px, 고정 — 넘치면 가젯 안 스크롤). 가젯 최대 40개. 종류 32(`DASHBOARD_GADGET_TYPES`), `MEMO`·`SHORTCUTS` 만 여러 개.
- 옛 저장본: `columns` 12·24 는 `x`·`w` 를 48 기준으로 늘리고, `version` 1(행 40px)은 `y`·`h` 를 2배로 읽는다(같은 자리·같은 px).
- `config`: `title` 1~40자 · `refresh` 60|300(초, 없으면 끔 — 최소 1분, 탭이 안 보이면 멈춤) · `limit` 5|10(목록형) · `days` 7|30(추이형) · `text` 0~1000자(`MEMO`) · `links` 관리자 화면 주소 ≤12(`SHORTCUTS`) · `color` `NONE`·`SLATE`·`BLUE`·`SKY`·`TEAL`·`GREEN`·`AMBER`·`ORANGE`·`ROSE`·`VIOLET`(없으면 추천 색). 종류에 없는 칸은 버린다.
- 권한이 빠진 가젯: 보기에선 그리지 않고 위로 당긴다. 편집에선 흐리게 「권한이 없어 보이지 않는 가젯」으로 두고 뺄 수 있다(저장 값에서 지우지 않는다).

## 가젯 종류 (권한 = 그중 하나라도 있으면)
| 묶음 | 종류 | 권한 | 내용 |
|---|---|---|---|
| 처리할 일 | `TODO` | ad.read·hotdeal.read·business.read | 기존 처리할 일(신청 큐별 건수·사업자 심사·핫딜 확인 필요·위장 홍보 의심) — 기존 목록 API 를 그대로 부른다 |
| 처리할 일 | `COMMERCE_TODO` | commerce.order/product/payment.read | 커머스 처리할 일(commerce.md 19절) — `GET /api/admin/commerce/dashboard` |
| 처리할 일 | `TODAY_SCHEDULE` | ad.read·commerce.product.read·pointshop.read·batch.read | 오늘·내일 일정(광고 시작·종료, 공구 마감, 포인트 상점 판매, 예약 배치 — 항목마다 그 권한) |
| 커뮤니티 | `REPORTS` · `POST_ACTIVITY` · `RECENT_POSTS` · `BOARD_RANKING` · `POPULAR_NOW` · `VISITORS` · `GROUP_REQUESTS` | report.read · post.read · post.read · post.read · popular.read · site.read · group.read | 신고 대기·검토 중 + 오래된 대기 · 오늘 글·댓글(어제 대비)+7일 · 최근 글 · 활발한 게시판 5 · 지금 인기글 · 방문자·페이지뷰·지금 접속 + 시간대 막대 · 개설 신청 |
| 회원 | `MEMBER_STATS` · `SIGNUP_TREND` · `RECENT_SIGNUPS` · `SANCTIONS` · `NICKNAME_IMAGES` | member.read(제재 sanction.read) | 오늘 가입·탈퇴·7일 안 휴면·정지 중 · 가입 추이 · 최근 가입 · 진행 중 제재·이의 신청 대기 + 이의 신청 3건 · 닉네임 이미지 심사 대기 |
| 핫딜·광고 | `HOTDEAL_STATUS` · `HOTDEAL_TOP` · `AD_PERFORMANCE` · `AD_SCHEDULE` | hotdeal.read · ad.read | 진행 중·오늘 등록·확인 필요 · 인기 핫딜 · 어제/7일 노출·클릭 · 이번 주 시작·종료 |
| 공구·결제 | `GROUPBUY_PROGRESS` · `GROUPBUY_QNA` · `SALES_SUMMARY` · `ORDER_TREND` · `RECONCILE` | commerce.product.read · commerce.payment.read | 모집 중 공구 달성률 · 답변 대기 Q&A · 오늘/어제/이번 달/지난달 같은 기간 결제·환불 · 일별 결제 추이 · 최근 대사·웹훅 실패 |
| 포인트 | `POINT_SUMMARY` · `POINTSHOP` | point.read · pointshop.read | 오늘 지급·사용·7일 안 만료 + 환불 승인 대기(point.refund.approve 일 때만) · 오늘 구매·품절 임박 |
| 시스템 | `BATCH_HEALTH` · `NOTIFICATION_HEALTH` · `AUDIT_RECENT` · `OPERATOR_SECURITY` | batch.read · notification.read · audit.read · operator.read | 24시간 실패·실행 중 배치 · 오늘 발송·채널별 실패 · 최근 운영자 활동 · 잠긴·2차 인증 미설정 운영자 |
| 개인 | `MEMO` · `SHORTCUTS` | 없음 | 메모 · 바로가기(config 만, 서버 데이터 없음) |
응답 모양의 원천은 서버 `server/admin/.../dashboard/dto/response/gadget/*` = 화면 `composables/dashboard/api/useDashboardGadgetApi.types.ts`. 이름이 있는 줄은 영어 이름 칸(`boardNameEn`·`titleEn`·`nameEn`)을 함께 준다.

## 서버 부담 원칙
- 숫자 = 상태별 COUNT 한 번(인덱스로, 서버 60초 캐시 가정) · 목록 = 최대 10행 · 추이 = 배치·집계 표(일별·시간별)만 읽고 원본을 GROUP BY 하지 않는다.
- 화면은 놓인 가젯만 부른다. 자동 새로 고침은 가젯별 선택(끔·1분·5분).

## 권한·정책
- 구성 읽기·저장 = 로그인한 운영자 본인 것만(별도 권한 없음). 감사 기록 없음(개인 화면 설정).
- 가젯 데이터 = 종류별 권한. 권한 없는 종류는 그 칸 `null`(한 종류만 요청했으면 403). 권한 판단은 서버.

## API 후보
- `GET /api/admin/me/dashboard` → 위 구성(저장한 적 없으면 `gadgets: null`) · `PUT /api/admin/me/dashboard`(CSRF) 같은 본문 → 저장한 값 · `400 fields.gadgets[i].<칸>`.
- `GET /api/admin/dashboard/gadgets?types=A,B&limit.A=5&days.B=30` → `{ generatedAt, gadgets: { [type]: data | null }, unavailable: type[] }` — `unavailable` = 권한은 있지만 서버가 아직 줄 수 없는 종류(화면 「준비 중」). 화면은 처음 그릴 때 놓인 가젯을 한 요청으로 묶고, 새로 고침은 그 종류만 부른다.
- `GET /api/admin/commerce/dashboard` → `CommerceDashboardView`(커머스 처리할 일, 아직 서버 없음).

## 미확정
- 방문 현황(`VISITORS`) — 방문 집계 표가 없다(시간별 집계 새로 필요). 알림 발송 상태 — 채널별 실패 집계 원천 확인 필요.
- 제재(`SANCTIONS`) — 권한 `sanction.read` 「기능 준비 중」, 제재 화면 없음(지금은 회원 관리로 이동).
- 서버 캐시 방식(60초) · 대시보드 데이터 묶음 호출 vs 종류별 호출.
