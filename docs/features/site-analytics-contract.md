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

## 2단계 — 환경·검색어·봇·수집 진단·정보주체 요청 (화면 확정 2026-11-04, 전환은 하지 않음)
- 메뉴 `visitStats` 에 실시간 다음 차례로(모두 site.read): 환경 `visitEnvironment` `/analytics/visits/environment` · 검색어 `visitSearch` `…/search` · 봇·크롤러 `visitBots` `…/bots` · 수집 진단 `visitHealth` `…/health`(기간 없음, 최근 24시간) · 정보주체 요청 `visitRequests` `…/requests`. 응답 모양의 원천은 `useVisitAnalyticsApi.types.ts`(`VisitEnvironment`·`VisitSearch`·`VisitBots`·`VisitHealth`·`VisitForgetPage`) — 각 응답에 접은 `collect`.
- 설정 칸 추가(기본 꺼짐, 방문 수집 전체가 꺼지면 잠김): `environment` · `searchTerms` · `bots`. 수집 진단·정보주체 요청은 스위치 없음(늘 동작). `GET /api/site/info` 의 `analytics` 에 `environment`.
- 환경: 신호 `env: { browser, os, screen } | null` — 세션 첫 페이지이고 `environment` 가 켜졌을 때만, 분류값만(원본 UA·화면 크기 원값 없음). **기기는 서버가 UA 로 가른다**(지금 방식 그대로, 사용자 결정 2026-11-04). 방문(세션 첫 페이지) 기준 일별 집계.
- 검색어: 신호 칸 없음 — 서버가 사이트 검색 요청에서 직접 센다(첫 쪽만 — 쪽 넘김은 다시 세지 않음). 다듬기(앞뒤 공백·연속 공백·소문자) 뒤 2자 미만·50자 초과·전화번호·이메일 모양은 저장 없이 「걸러진 검색」 횟수만. 결과 0건은 따로 셈.
- 봇: 봇 UA 요청을 종류별(구글·네이버·빙·다음·기타 크롤러·도구) 일별로 센다. 비율 = 봇 ÷ (봇 + 사람 페이지뷰) — 대략값임을 화면에 적음.
- 수집 진단: 서버(인스턴스)마다 1분 기록 성공·실패·버린 신호(버퍼 넘침)·무시한 신호(봇·형식)를 몰아 쓰고, 최근 실패(7일 보관), 크론 상태(배치 실행 기록), 원본 보관(가장 오래된 날·조각 수). 상태(정상·지연·멈춤·꺼짐)·문제 문장은 서버가 판정.
- 정보주체 요청: 공개 `POST /api/visits/forget { visitorId }`(로그인·CSRF 없음) → 202 `{ requestId, receivedAt }`, 빈도 제한 넘으면 429. 서버는 방문자 ID 해시만 요청 표에 두고 DB 크론(5분)이 원본·판정 표·지금 접속에서 지운다(합산 숫자는 사람을 가리키지 않아 남김). 처리한 뒤 해시는 비운다. 관리자 목록은 확인용.
- 사용자 화면 `/privacy` 「방문 통계」 칸: 수집 거부 스위치(이 브라우저만, 로컬 스토리지) · 방문 기록 지우기. **거부를 켤 때 방문자 ID 가 있으면 「기록도 지우고 거부」를 먼저 권한다**(지우기 실패면 거부도 하지 않음, 사용자 결정 2026-11-04). 처리방침 문구 초안 `docs/features/site-analytics-privacy-draft.md`(법률 검토 전).
- 서버 구현(2026-11-04): shared **V135**(설정 칸 3개 — PUT 에서 빠지면 저장값 유지) · analytics **V4**(`analytics_env_daily`·`analytics_search_daily`·`analytics_search_filtered_daily`·`analytics_bot_daily`·`analytics_collector_beats`·`analytics_collector_hourly`·`analytics_collector_failures`·`analytics_forget_requests`) · 크론 `R__analytics_13` `analytics-forget-requests`(5분) · `R__analytics_14` `analytics-log-cleanup`(매일 00:45 — 진단 7일, 처리 끝난 요청 1년).
- 봇 셈 지점: user-web 은 SPA(`ssr: false`)라 운영에서 api `SiteHeadFilter` 가 `index.html` 을 내릴 때 화면 HTML 요청 하나를 1로 센다(`/api/**` 는 세지 않아 중복 없음). `/api` 만 두드리는 도구·로컬 Nuxt 개발 서버로 온 봇은 안 잡힌다. 배포 확인(2026-11-04): dev 는 Caddy → app, prod 는 ALB → app 이고 `pnpm generate` 정적 파일을 Spring Boot 가 내리므로 이 지점에서 센다. 봇 판정 `BotClassifier` 에 네이버(Yeti)·다음을 더해 **방문 신호에서도 이 둘을 봇으로 뺀다**.
- 검색어: `SearchController` 성공 응답 중 `page == 1` 만. 주민등록번호 모양도 거른다. 환경: 맥 UA + `env.os=IOS` 면 TABLET.
- 진단: 서버 이름 기본 `api-<NODE_ID>`(`roboyam.analytics.instance-name`). 판정 — 마지막 기록 3분 넘음 WARN·10분 ERROR, 24시간 안 기록 실패 WARN(지난 1시간이면 ERROR), 버린 신호 WARN, 크론 마지막 실패 ERROR·주기 2배 지남 WARN, 원본이 보관 기간+1일 넘게 남음 WARN, 다음 달 조각 없음 WARN(그 달 마지막 3일 ERROR). 상태 OFF → STOPPED(3분 안 기록 서버 0) → DELAYED → OK.
- 삭제 요청: 원본에 `visitor_hash` 인덱스를 두지 않고, 받은 지 2분 넘은 대기 요청을 최대 500개씩 모아 월 조각마다 `visitor_hash = ANY(…)` 한 번(1분 몰아쓰기 직전 신호가 처리 뒤에 써지는 것을 막으려고 2분). 빈도 제한 IP당 1시간 5회(서버 메모리, IP 저장 안 함 — 서버 2~3대면 그 배수까지) + **대기 요청 전체 상한 1,000개**(DB, 서버 여러 대가 함께 지킴 — 넣기 문장에 조건으로, 넘으면 503 `VISIT_FORGET_BUSY` Retry-After 300초, 같은 해시가 대기 중이면 상한과 상관없이 그 요청 반환). 요청 번호는 Snowflake 숫자. 같은 해시가 대기 중이면 그 요청을 돌려준다.
- 알려진 점: 지운 그날 같은 브라우저가 다시 오면 방문자 1로 다시 센다(판정 표시를 지웠으므로). 요청 목록은 캐시 60초.

## 테스트 (QA 2026-11-04)
- api `com.community.analytics.*`(수집 조건·유입 분류 43, 서버 여러 대 동시 기록·자정 경계 7, 원본 파티션·보관 경계 6) · shared `com.community.analytics.*`(게시판 활동 크론 멱등·제외, 알림 발송 기록 8) · admin `com.community.analytics.*`·`DashboardAnalyticsGadgetsTest`(설정 이력·권한·disabled, 어제 같은 시각 7) — Testcontainers.
- 관리자 방문자 통계 API 7개(2026-11-04): admin `VisitAnalyticsAdminServiceTest` 34(고정 시계 — 칸 수·같은 시각 비교·오늘 칸 제외·LIKE 이스케이프·「바로 들어옴」·캐시)·`VisitResultCacheTest` 4·`VisitAnalyticsAdminHttpTest` 4(기간 검증·권한 403·no-store). 2단계 서버 뒤 analytics·메뉴·배치 목록 시험 166건 다시 돌려 통과.
- 2단계(QA 2026-11-04): api `VisitStage2CollectTest` 95(환경·봇 분류·`SiteHeadFilter`·검색어 거르기·`SearchController` 첫 쪽)·`VisitStage2FlushTest` 10(서버 3대 동시 합·진단)·`VisitForgetRequestTest` 8(같은 해시 한 줄·대기 상한·빈도 제한)·`VisitForgetCronTest` 6(`R__analytics_13·14` 를 `CALL` 로 — 그 해시만·여러 조각·500 묶음·중간 실패 이어 받기) · shared `StatsCollectionStage2Test` 4 · admin `VisitStage2AdminServiceTest` 12·`VisitDiagnosticsAdminServiceTest` 18(판정 경계)·`VisitStage2AdminHttpTest` 5. 찾은 버그 1건 수정: 「지난 1시간 안 기록 실패 = ERROR」가 정시 칸으로 잘려 앞 칸의 실패를 놓침 → 실패 기록의 정확한 시각도 함께 센다. pg_cron 실제 등록·사용자 화면 신호 규칙은 시험 밖.
- 알려진 경미한 점: 정시 직전·직후에 다른 서버로 간 같은 방문자는 먼저 쓴 서버의 시간 칸에 들어간다(하루 합은 정확). 사용자 화면 신호 규칙은 프론트 테스트 도구가 없어 시나리오로만.

## 미확정
- 「방문 분석」 화면(기간·페이지 순위·유입·이동 흐름 그림)은 다음 단계 목업부터.
- 페이지별 방문자 수(중복 없이)는 원본에서 필요할 때 계산(이번 범위 밖).
