# 방문 분석·통계 수집 계약 (설계 합의·**화면 확정 2026-11-04**)

**의도**: 공개 사이트의 방문자·페이지뷰·지금 접속·인기 페이지·유입 경로·이동 흐름과, 알림 발송 결과·게시판 활동을 모은다. 관리자 대시보드 가젯(`admin-dashboard-contract.md`)과 다음 단계 「방문 분석」 화면이 쓴다. **모든 수집은 기본 꺼짐**이고 「설정 › 통계 수집」에서 항목별로 켠다.
**사용자 기준(2026-11-04)**: 정확성과 성능 둘 다 중요 · **API 서버 2~3대 전제**로 숫자가 정확할 것 · 신호마다 DB 에 쓰지 않는다 · **자바 배치 지양, 주기 작업은 DB 크론(pg_cron)** · 나중에 **TimescaleDB 로 옮기기 쉽게**(표 접두사·스트림·패키지 분리).

## 방문자 식별
- 브라우저 로컬 스토리지의 무작위 방문자 ID(UUID, GA 의 쿠키 ID 와 같은 역할 — 날마다 바뀌지 않아 신규·재방문 구분 가능). 저장 불가면 세션 저장소 → 메모리 순으로 대체. 탭마다 세션 ID(30분 쉬면 새 세션).
- 서버는 원본 ID 대신 `BlindIndex` HMAC 해시만 저장. IP·원본 UA 저장 안 함. 봇·빈 UA 는 무시(광고 노출과 같은 정규식).
- 처리방침: 브라우저 저장소 식별값·방문 기록 보관을 개인정보 처리방침에 적어야 한다(관리자 약관 화면에서 운영자가 넣음).

## 수집·쓰기 (앱 — 쓰기 버퍼, 배치 아님)
- 신호 `POST /api/visits`(로그인·CSRF 없음): `visitorId`·`sessionId`·`path`(쿼리 뺌)·화면 종류·직전 페이지·세션 첫 페이지의 유입(외부 도메인·`utm_*`)·신규 여부. 수집이 꺼져 있으면 브라우저가 보내지 않고(`/api/site/info` 로 스위치 받음) 서버도 무시.
- 각 API 서버는 메모리에 모았다가 **1분마다(그리고 종료 때) 몇 문장으로 몰아 쓴다** — 문장 수는 방문량과 무관.
  - 방문자: 그 1분에 처음 본 방문자 해시를 `analytics_visit_marks(day, visitor_hash)` 에 한 문장 `INSERT … ON CONFLICT DO NOTHING RETURNING` → **실제로 들어간 줄만** 방문자 +n. 같은 방문자를 여러 서버가 넣어도 DB 유일 키로 한 서버만 성공(동시면 PostgreSQL 이 순서를 정하고 나머지는 건너뜀) → 서버가 몇 대든 1명은 1명. 넣기와 더하기는 한 트랜잭션. **신규 방문자**: 표시에 「신규」 칸(analytics V2) — 늦게 온 신규 신호가 이미 있는 표시를 false→true 로 바꿀 때만 신규 +1(첫 화면은 서버 A, 다음 화면은 서버 B 가 받고 B 가 먼저 써도 신규가 빠지지 않음, QA 재현 2026-11-04).
  - 페이지뷰·페이지별 조회·유입처별 방문·이동(A→B) 횟수: 신호 1번 = 서버 1대라 서버별로 더해도 정확 → 시간·일 집계 표에 더하기 upsert.
  - 지금 접속: `analytics_visit_active(visitor_hash, last_seen)` 한 문장 upsert, 「지금 접속」 = last_seen 5분 안 COUNT(DB 에서 한 번 — 서버 여러 대여도 중복 없음).
  - 원본(켰을 때만): `analytics_page_views` 에 여러 줄 한 번에.
- 잃을 수 있는 것: 서버가 갑자기 꺼지면 아직 안 쓴 최대 1분치(통계라 허용). 「오늘 봤음」 표시는 DB 에 있어 재시작해도 다시 세지 않음.
- 페이지뷰는 본 횟수(새로 고침 포함), 방문자는 중복 없이. 같은 주소 2초 안 신호는 브라우저에서 한 번으로.
- 알림 발송: 발송 지점(메일 `MailDispatcher` 경유 전부, Slack·Mattermost)에서 성공·실패를 메모리에 세다가 1분마다 `notification_send_daily` 에 더하기, 실패만 사유 코드와 함께 `notification_send_failures`(수신자·본문 저장 안 함).

## 주기 작업 (DB 크론, 자바 배치 0개)
- 게시판 활동: 10분마다 오늘·어제만 다시 세어 일별 표(글·댓글·가입·게시판별) — 메인 DB 표가 원천이라 분석 스트림 밖.
- 분석 원본: 매일 다음 달 파티션 만들기·보관 기간(30·90·180·365일, 기본 90) 지난 파티션 지우기. 판정 표 정리(marks 2일, active 10분, 발송 실패 30일).
- 각 크론은 시작할 때 설정을 보고 꺼져 있으면 건너뛴다.

## 옮기기 쉽게
- 표 접두사 `analytics_`, 마이그레이션 별도 스트림 `analytics`(표 + 크론 `R__analytics_*`). 원본 표 기본 키 맨 앞이 시각(`viewed_at, id`) — TimescaleDB hypertable 조건. 다른 표와 외래 키 없음.
- 자바 `com.community.analytics`(collect·store·aggregate·settings), DB 접근은 분석 전용 연결 `analyticsJdbc`(지금 메인 DB, 설정만 바꾸면 다른 DB), JPA 없이 SQL.
- 사용자 화면 공용 레이어 `apps-packages/analytics`(`@roboyam/analytics`) — 방문자 ID·세션·신호.
- AWS RDS PostgreSQL 은 TimescaleDB 확장을 지원하지 않는다(옮길 때 EC2 직접 또는 Timescale Cloud).

## 설정 (`analytics_settings`, 조회 site.read · 변경 site.manage · 감사 기록)
`enabled`(방문 수집 전체) · `visits` · `rawEvents` + `rawRetentionDays` · `referrers` · `flows` · `notificationSends` · `boardActivity` — 전부 기본 꺼짐. 꺼진 항목의 대시보드 가젯은 「수집이 꺼져 있어요」(묶음 응답 `disabled: type[]`).

## 화면 (확정 2026-11-04, 기준 코드 `apps/admin-web`)
- `pages/settings/analytics.vue` 「설정 › 통계 수집」(메뉴: 설정 › 일반, 약관 근처, `pi pi-chart-bar`, site.read) — 수집 방식 안내 · 방문 수집(전체 + 방문자·페이지뷰·지금 접속 / 원본 기록 + 보관 30·90·180·365일 / 유입 경로 / 이동 흐름) · 알림·게시판 집계(알림 발송 기록 / 게시판 활동 집계) · 한 번에 저장 · 변경 이력. 하위 항목은 전체가 꺼지면 잠김(값 유지). 원본 기록을 켜면 처리방침 안내.
- 대시보드: 꺼진 항목의 가젯 「수집이 꺼져 있어요」(site.manage 면 설정 링크 `/settings/analytics#analytics-<칸>`). 새 가젯 `POPULAR_PAGES`(인기 페이지, limit 5|10)·`REFERRERS`(유입 경로) — 묶음 「방문 분석」(방문 현황 포함).

## API (확정)
- 공개 `POST /api/visits`(로그인·CSRF 없음) `{ visitorId(UUID), sessionId(UUID), path(쿼리 뺀 경로 ≤300), kind(HOME·BOARD·POST·GROUP_BUY·HOTDEAL·GROUP·POINT_SHOP·SEARCH·MY·OTHER), prevPath|null, referrer|null(세션 첫 페이지의 외부 주소만), utm|null{source,medium,campaign}, newVisitor }` → 204(형식이 틀려도 204 로 무시).
- 공개 `GET /api/site/info` 에 `analytics: { enabled, visits, rawEvents, referrers, flows }` 를 더한다 — 브라우저는 `enabled` 이고 하나라도 켜졌을 때만 신호를 보낸다.
- 관리자 `GET/PUT /api/admin/analytics/settings`(site.read / site.manage·CSRF·감사) `{ enabled, visits, rawEvents, rawRetentionDays(30|90|180|365), referrers, flows, notificationSends, boardActivity, updatedAt, updatedBy }` · `GET …/settings/history?page&size`(기존 설정 이력 모양). 설정 표는 메인 DB(shared 스트림 — TimescaleDB 로 옮길 대상이 아니라서 `analytics_` 접두사를 쓰지 않음).
- 대시보드 묶음 응답에 `disabled: type[]`(권한은 있으나 수집이 꺼진 종류). 새 종류 응답: `POPULAR_PAGES` `{ items[{ path, kind, title|null, titleEn|null, views }] }` · `REFERRERS` `{ total, groups[{ group: SEARCH|SOCIAL|DIRECT|OTHER_SITE|CAMPAIGN, visits }], domains[{ domain, visits }] }`. `VISITORS` 에 `newVisitorsToday`.

## 「방문자 통계」 화면 (화면 확정 2026-11-04)
- 메뉴 묶음 `visitStats` 「방문자 통계」(대시보드 바로 아래, `pi pi-chart-line`, site.read) — 「설정 › 통계 수집」은 원래 자리. 그누보드 7 「방문자 통계」를 기준으로 더 보기 좋게(쪽마다 한 줄 요약·증감 칩·미니 추이).
  | 쪽 | 메뉴 키 | 주소 |
  |---|---|---|
  | 개요 | `visitOverview` | `/analytics/visits` |
  | 페이지 | `visitPages` | `/analytics/visits/pages` (줄 → 오른쪽 서랍: 일별 조회·들어온/나간 곳) |
  | 유입 경로 | `visitReferrers` | `/analytics/visits/referrers` (분류 비율·분류별 일별 쌓은 막대·사이트 10·캠페인) |
  | 이동 흐름 | `visitFlows` | `/analytics/visits/flows` (흐름 그림 들어온 5 → 기준 → 나간 5, 「바로 들어옴」, 상위 이동 10, `path` 쿼리) |
  | 요일·시간대 | `visitTime` | `/analytics/visits/time` (요일×시간 히트맵·붐비는/한산한 때·시간대별 평균 + 하루 넷 비중·요일별 하루 평균 — 두 카드 같은 높이) |
  | 실시간 | `visitRealtime` | `/analytics/visits/realtime` (지금 접속·최근 30분 분당 페이지뷰·최근 5분 페이지·유입, 보일 때만 30초 새로 고침) |
- 기간: 주소 `from`·`to`(한국 날짜, 최대 90일), 쪽을 옮겨도 유지, 메뉴로 들어오면 마지막 기간(브라우저 저장). 여러 날 「방문자」는 일별 합(툴팁·일평균).
- API(관리자, site.read, 모양의 원천 `apps/admin-web/app/composables/analytics/api/useVisitAnalyticsApi.types.ts`): `GET /api/admin/analytics/visits/summary` · `…/pages`(kind·q·page·size) · `…/pages/detail`(path) · `…/referrers`(detail) · `…/flows`(path?) · `…/time` · `…/realtime`.
- 서버 구현(2026-11-04): analytics **V3** — `analytics_campaign_daily(day, utm_source, utm_medium, utm_campaign, visits)`(빈 칸 '', 세션 첫 페이지에 utm 이 있으면 방문 1 — **「유입 경로」 수집에 딸림**, 원본을 읽지 않음) · `ix_analytics_flow_daily_to (day, to_path)`. 세션 첫 페이지는 이동 `('' → path)` 한 줄(응답에서는 들어온 곳 `path: null`). 기간 오류 400: 비었거나·형식·`from>to`·90일 초과 = `fields.from`, 오늘 뒤 = `fields.to`. 결과 캐시 60초(실시간 20초 공유), 키에 수집 스위치 포함. 페이지 순위·서랍 일별은 `collect.rawEvents`(원본과 함께 쌓이는 `analytics_page_daily`). `q` 는 경로·게시판 이름만(글·공구 제목은 큰 표라 제외).
- 서버 결정(권장 채택): 세션 첫 페이지도 이동 「바로 들어옴」 줄로 저장(`from_path=''`) · 캠페인은 일별 집계 표(원본을 읽지 않음) · 이동 흐름 `(day, to_path)` 인덱스 · 결과 캐시(실시간 15~30초 공유, 나머지 60초) · 실시간 분당 숫자는 원본 최근 30분(원본 꺼짐이면 지금 접속만).

## 테스트 (QA 2026-11-04)
- api `com.community.analytics.*`(수집 조건·유입 분류 43, 서버 여러 대 동시 기록·자정 경계 7, 원본 파티션·보관 경계 6) · shared `com.community.analytics.*`(게시판 활동 크론 멱등·제외, 알림 발송 기록 8) · admin `com.community.analytics.*`·`DashboardAnalyticsGadgetsTest`(설정 이력·권한·disabled, 어제 같은 시각 7) — Testcontainers.
- 알려진 경미한 점: 정시 직전·직후에 다른 서버로 간 같은 방문자는 먼저 쓴 서버의 시간 칸에 들어간다(하루 합은 정확). 사용자 화면 신호 규칙은 프론트 테스트 도구가 없어 시나리오로만.

## 미확정
- 「방문 분석」 화면(기간·페이지 순위·유입·이동 흐름 그림)은 다음 단계 목업부터.
- 페이지별 방문자 수(중복 없이)는 원본에서 필요할 때 계산(이번 범위 밖).
