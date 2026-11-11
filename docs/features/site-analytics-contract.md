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
- AWS RDS PostgreSQL 은 TimescaleDB 확장을 지원하지 않는다 — **사용자 결정(2026-11-04): AWS 에서도 RDS 를 쓰지 않고 PostgreSQL 을 직접 올린다**(pg_cron 크론 때문). 그래서 옮길 때도 같은 자체 DB 에 확장만 붙이면 된다.

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

## 3단계 — 보관 정리·주소 묶음·주/월 순방문자 (설계 합의 2026-11-11 · **1차 구현 2026-11-11 브랜치 `claude/analytics-stage3`** — ①②③⑤⑥·뺄 계정 서버 + 관리자 화면, ④ 는 화면 목업만)
**의도**: 통계 표가 기한 없이 커지지 않게 모든 표에 상한을 두고, 광고주에게 줄 수 있는 WAU·MAU(방문자·회원)를 정확하게 센다. **GA4 는 넣지 않는다**(자체 통계 중심 — 광고주가 「구글 숫자」를 요구하거나 구글 광고 전환·인구통계가 필요해지면 검증용으로만 검토). 애드센스 수익·RPM 은 애드센스 보고서에서 본다.
아래가 구현되기 전까지는 위 절(원본 일 단위 보관·주소 전체 집계·집계 기한 없음)이 지금 동작이다.

### 지금 알려진 사실(2026-11-11 코드 확인)
- 원본(`analytics_page_views`)을 읽는 곳은 실시간(최근 30분)·수집 진단뿐 — 나머지 화면은 집계 표만 읽는다. 원본으로 집계를 다시 만드는 도구는 없다.
- `R__analytics_10` 은 달 조각 DROP 에 더해 걸친 조각의 기한 앞 줄을 날마다 DELETE 한다 → 보관은 정확히 「보관 일수」(30일이면 11월 5일에 10월 1~5일 원본 없음).
- 일별 집계 표(`analytics_page_daily`·`analytics_flow_daily`·`analytics_search_daily`·`analytics_referrer_daily` 등)와 `analytics_visit_hourly` 는 정리 크론이 없다. 글 주소(`/posts/{id}`)마다 줄이 생겨 방문량 따라 기한 없이 는다.
- 사이트 인기글(`R__batch_24_popular_rank`)은 글 행의 추천·댓글·조회수만 쓴다 — 방문 통계와 무관.
- 회원 로그인 세션은 마지막 활동 뒤 30일 유지, `users.last_login_at` 은 실제 로그인 때만 바뀐다 → 로그인 시각으로는 회원 MAU 를 셀 수 없다.

### 결정 ① 원본 보관 = 달 단위, 기본 2개월
- 설정 `raw_retention_days`(30·90·180·365) → **개월 수**(2·3·6·12, 이번 달 포함), 기본 **2 = 이번 달 + 지난달**(31~62일, 지난달은 늘 통째로 있음). 기존 값 옮기기: 30→2 · 90→3 · 180→6 · 365→12.
- `R__analytics_10`: 「이번 달부터 n번째 앞 달보다 오래된 조각 DROP」만 — 줄 DELETE 단계는 뺀다. 일 단위(예: 60일)는 31일 달이 이어지면 지난달 첫날을 놓쳐 버렸다(GA4 도 2·14개월, 달마다 몰아 지움).
- 수집 진단 「원본이 보관 기간 + 1일 넘게 남음」 판정 → 달 기준. 「설정 › 통계 수집」 선택지 글자 「2개월(이번 달 포함)」 식. 처리방침 초안(`site-analytics-privacy-draft.md`) 「최대 2개월(이번 달과 지난달)」.
- 법무 확인: 최소 보관 원칙 기준. 「접속 기록 3개월」(통신비밀보호법)은 IP 접속 로그, 「1년」은 관리자 개인정보 처리 기록 — 이 원본(IP 없음)이 해당하는지 확인.

### 결정 ② 집계 주소 = 정해진 주소만, 번호 조각은 `{id}`
- 서버가 신호를 받을 때(api 수집) 경로·직전 경로를 **user-web 라우트 기준으로** 바꾼다 — 동적 자리 중 게시판 `[slug]`·가젯 페이지 `[key]` 만 값을 남기고, 나머지 동적 자리(`[id]`·`[eventId]`·`[orderNo]` …)는 모양과 상관없이 `{id}`(2026-11-11 검토 보강 — 「숫자 조각만」 규칙은 `/me/orders/G2026111100001` 주문번호를 못 잡아 줄이 늘고 주문번호가 남는다). 그 주소로 원본·페이지별·이동 흐름을 모두 쌓는다(2026-11-11 결정 — 원본의 실제 글 주소를 읽는 곳이 없다: 실시간은 묶음 주소로 충분, 삭제 요청은 주소를 쓰지 않음. 「방문자 해시 + 읽은 글 번호」 기록이 남지 않아 개인정보도 가벼워진다). 실시간 화면은 원본을 읽으므로 자연히 같은 묶음으로 보인다.
  - 그대로: `/boards/{slug}`(예 `/boards/market`) · `/p/{key}` · `/tips` · `/popular` · `/settings/profile` …
  - 바꿈: `/posts/123` → `/posts/{id}` · `/group-buys/42` → `/group-buys/{id}` · `/groups/7/events/15` → `/groups/{id}/events/{id}` · `/users/88` → `/users/{id}`
- 글 번호로 게시판을 찾지 않는다(글 보기는 `/posts/{id}` 한 줄). 서버에서 바꾸는 까닭 = 신호는 누구나 보낼 수 있어 임의 주소로 줄을 늘리는 것도 막는다.
- 지금 user-web 동적 라우트(2026-11-11): `/boards/[slug]` · `/p/[key]` · `/posts/[id]` · `/group-buys/[id]` · `/groups/[id]` · `/groups/[id]/events/[eventId]` · `/users/[id]` · `/point-shop/[id]` · `/robotikon/[id]` · `/robotikon/studio/[id]` · `/messages/[id]` · `/me/orders/[orderNo]` · `/business/applications/[id]`. 라우트를 더하면 서버 라우트 표도 같이 고친다.
- **모르는 주소 막기**: 서버는 알려진 라우트 모양만 받는다. `[slug]`·`[key]` 는 실제 있는 게시판·가젯 페이지인지 서버 메모리 캐시(짧게 새로 읽음)로 확인하고, 라우트 표에 없거나 없는 slug·key 면 「알 수 없는 주소」 한 줄(`/{unknown}`)로 모은다 — 신호는 누구나 보낼 수 있어 임의 주소로 줄을 늘리지 못하게.
- 기존 줄: 집계 표는 마이그레이션 한 번(`regexp_replace` 후 같은 키 합치기). 기존 원본 줄은 고치지 않는다(보관 2개월 안에 지워진다 — 그동안 실시간에는 옛 줄이 섞이지 않는다, 최근 30분만 읽으므로). 관리자 화면(대시보드 「인기 페이지」·「방문자 통계 › 페이지」·서랍·이동 흐름)은 모양 그대로, 줄이 정해진 주소 단위가 된다 — `/posts/{id}` 같은 묶음 줄의 제목 찾기는 「글 보기」 식 화면 이름으로.
- 권장: 페이지별 집계(`analytics_page_daily`)를 「원본 기록」 스위치에서 떼어 「방문 수집」에 딸리게(작아졌으므로 — 원본을 꺼도 페이지 순위가 쌓이게).

### 결정 ③ 일별 13개월 + 월별 기한 없음
| 단계 | 보관 |
|---|---|
| 원본 | 2개월(결정 ①) |
| 시간별 `analytics_visit_hourly` · 일별 집계 전부 | **13개월** = 이번 달 + 지난 12개월(달력 단위, 작년 같은 날과 늘 비교 가능) |
| 월별 집계(새 표 — 일별과 같은 항목, `(달, 키)`) | 기한 없음 |
| 연별 | 만들지 않음 — 더할 수 있는 숫자라 월별 12줄의 합 |
- 매달 1일 DB 크론(pg_cron, analytics 스트림) 하나: ① 끝난 지난달 일별을 월별로 합침(더하기, 같은 달은 덮어써 다시 돌려도 같음) ② 13개월보다 오래된 일별·시간별 줄 삭제. 배치 목록(`BatchCatalog`)에 등록.
- 화면: 13개월이 지난 기간용 「월별 보기」(예: 최근 24개월 추이)는 목업부터. 기간 선택(최대 90일)은 그대로.

### 결정 ④ 주·월 순방문자(WAU·MAU) — 방문자와 회원, 정확값
- 방식: 하루 방문자와 같이 `(기간, 대상)` 표시를 `INSERT … ON CONFLICT DO NOTHING` 로 넣고 처음 들어간 줄만 +1(서버 2~3대여도 1명은 1명, 1분 몰아쓰기). 근사값(HyperLogLog)은 쓰지 않는다.
- 기간: 달력 기준 — 주는 월요일 시작, 한국 시간. 「최근 30일」처럼 움직이는 기간·연 순방문자는 만들지 않는다(연은 해시를 1년 들고 있어야 함).
- 방문자: 대상 = 방문자 해시 **16바이트 `uuid` 형**(HMAC 앞 16바이트, 겹칠 확률 사실상 0). 그날 처음 본 방문자(하루 표시에 새로 들어간 줄)만 주·월 표시에 넣어 본다. 하루 표시는 지금처럼 64자.
- 회원: 대상 = 회원 번호 `bigint`(표시 방식 — 로그인 시각 방식은 버림). 로그인하지 않고 본 회원은 빠진다(방문자 쪽에는 들어감).
- 보관: 표시는 이번 기간(+ 정리까지 며칠)만, 끝난 기간은 숫자만 남긴다. DAU·WAU·MAU 숫자는 기한 없음.
- 숫자 표(2026-11-11 결정): `(대상 VISITOR|MEMBER, 종류 WEEK|MONTH, 기간 시작, 날짜, 새로 센 수)` — 표시에 처음 들어간 줄을 그날 칸에 +1. 기간 순방문자 = 그 기간 줄의 합, 「같은 시점까지」(이번 달 vs 지난달 같은 날까지) = 그 날짜까지의 합 → 표시를 지운 뒤에도 비교된다. 하루 4줄(대상 2 × 종류 2), 1년 약 1,500줄(100KB 안팎) — 기한 없음.
- **뺄 계정(2026-11-11 결정)**: 운영자 회원 계정(`operator_member_accounts.user_id` — 「로보얌운영팀」 포함)·AI 회원(`ai_member_profiles.user_id`)의 신호는 **방문 통계 전체에서 뺀다** — 신호를 받을 때 로그인 세션의 회원이 이 둘에 있으면 그 신호를 버린다(원본·하루 방문자·신규·페이지뷰·지금 접속·페이지·유입·캠페인·이동·환경·주/월 순방문자 모두 안 셈, 사이트 검색어 셈도 같은 규칙). 수집 진단에는 「뺀 계정 신호」 수로 남긴다(무시한 신호와 같은 자리). 대상 회원 번호 묶음은 서버 메모리에 두고 짧게(예: 1분) 새로 읽는다. 로그인하기 전에 같은 브라우저로 본 것은 가를 수 없어 들어간다.
- **게시판 활동에서도 뺀다(2026-11-11 결정)**: `R__batch_38`(`board_activity_daily`·`site_activity_daily`)이 글·댓글을 셀 때 `author_id` 가 위 두 묶음에 있는 줄을 빼고(비회원 글·댓글은 지금처럼 센다), 「가입」도 그 회원을 뺀다. 같은 기준을 쓰는 관리자 대시보드 「회원 현황」 오늘 가입도 함께 맞춘다. 크론은 오늘·어제만 다시 세므로, 나중에 운영자 계정으로 이어진 회원의 지난 날 숫자는 바뀌지 않는다.
- 개인정보: 방문자 해시 보관이 이틀 → 최대 한 달 남짓(처리방침 고침) · 방문 기록 삭제 요청 크론(`R__analytics_13`)에 방문자 주·월 표시 추가 · 회원 표시는 **회원 탈퇴 파기 대상** + 처리방침.
- 수집 스위치(2026-11-11 결정): 「방문자·페이지뷰」(`visits`)에 딸린다 — 새 칸 없음(기본 꺼짐).

### 결정 ⑤ 핫딜 클릭 표시 정리(`hotdeal-contract.md` 와 같이)
- `deal_click_daily`(하루 같은 방문자 1번 판정)에 정리 배치가 없어 방문자 해시와 함께 기한 없이 쌓인다. 클릭 수는 핫딜 행 `click_count` 에 따로 있으므로 오늘·어제만 남기는 shared 배치를 더한다 — 지우는 방법은 결정 ⑥ 의 날짜 파티션 DROP(처음부터 파티션으로).

### 결정 ⑥ 판정 표는 날짜·기간 파티션 + DROP — 큰 DELETE 를 두지 않는다(2026-11-11 사용자 「그때 바꾸지 말고 지금」)
- **하루 방문 표시 `analytics_visit_marks`**: 지금은 매일 DELETE(`R__analytics_11` — 큰 규모면 하루 약 10만 줄을 지우고 vacuum 빈자리가 남는다) → `day` 범위 **하루 파티션**으로 바꾸고 오늘·어제만 남겨 지난 조각을 DROP. 기본 키 `(day, visitor_hash)` 가 파티션 열을 담아 `ON CONFLICT` 그대로 쓴다.
  - 조각 만들기: 크론이 오늘·내일·모레 조각을 미리 만들고(자정에 조각이 없어 쓰기가 실패하지 않게), 쓰는 쪽(api)은 조각 없음 오류를 받으면 한 번 만들고 다시 쓴다(원본 `analytics_ensure_page_view_partitions` 와 같은 방식 — 잠금 아래 한 번만).
  - 옮기기: 마이그레이션 한 번 — 같은 이름·같은 칸의 파티션 표를 새로 만들어 오늘·어제 줄만 옮기고 바꿔 끼운다(칸이 같아 롤링 배포 중 옛 jar 도 그대로 쓴다). `R__analytics_11` 은 DROP 방식으로 바꾼다. 삭제 요청 크론(`R__analytics_13`)은 부모 표에 지우므로 그대로.
- **주·월 순방문자 표시(결정 ④)**: 처음부터 기간 파티션 — 월 표시는 달 조각, 주 표시는 주 조각. 기간이 끝나고 정리 여유(며칠)가 지나면 조각 DROP(큰 규모면 기간마다 월 약 150만·주 약 50만 줄을 DELETE 하게 되므로).
- **핫딜 클릭 표시 `deal_click_daily`(결정 ⑤)**: 하루 파티션으로 만들어 오늘·어제만 남긴다.
- 권장(미확정): 광고 노출·클릭 판정 `ad_view_marks`(shared, 2일 보관 DELETE `R__batch_33`)도 같은 모양(`day` 가 기본 키 맨 앞)이라 같은 방식으로 바꾼다. 글 조회수 중복 방지 `post_views` 는 글 번호 해시 파티션 + 24시간 기준이라 날짜로 떼기 어려워 그대로 둔다.

### 디스크 추정(상한, 2026-11-11 — vacuum 빈자리·WAL·백업 20~30% 별도)
가정: 초기 하루 1천 방문·5천 PV / 중간 1만·5만 / 큰 규모 10만·50만, 정해진 주소 150~300개.
| | 초기 | 중간 | 큰 규모 |
|---|---|---|---|
| 원본 2개월 | 약 0.1GB | 약 0.9GB | 약 9GB |
| 일별·시간별 13개월 | 약 80MB | 약 0.22GB | 약 1GB |
| 판정 표 + 주·월 표시 | 약 5MB | 약 50MB | 약 0.5GB |
| **상한 합계** | **약 0.2GB** | **약 1.2GB** | **약 10.5GB** |
| 그 뒤 증가(월별만) | 1년 수 MB | 1년 수 MB | 1년 수십 MB |
지금 구조 그대로면 1년마다 +0.3 / +2 / +13GB 기한 없이 는다. 큰 규모에서 가장 크게 느는 일별 표는 검색어(열린 키).

### 3단계 검토 보강 (2026-11-11 — 구현 때 지킬 것)
- **세션 확인 비용**: 로그인 세션은 DB(`spring_session`)에 있어, 뺄 계정·회원 MAU 를 위해 신호마다 세션을 읽으면 페이지뷰마다 DB 읽기가 생긴다(「신호마다 DB 를 건드리지 않는다」와 충돌). → 각 api 서버 메모리에 「세션 → 회원 번호(없음 포함)」 캐시를 짧게(예: 10분) 둔다 — 같은 브라우저의 두 번째 신호부터 DB 를 읽지 않는다. 로그아웃·계정 연결이 늦게 반영되는 것(최대 캐시 시간)은 통계라 허용. 대안(로그인 때 서명 토큰을 내려 신호에 싣기)은 화면을 고쳐야 해 쓰지 않는다.
- **월별 표의 「방문자」**: 하루 방문자를 한 달 더한 값은 월 순방문자가 아니다 → 월별 방문 표 칸은 「일별 합」(`visitors_daily_sum` 식 이름)으로 두고, 월 순방문자는 결정 ④ 숫자 표에서만 읽는다. 화면 글자도 「일별 합」.
- **월별 합치기 시각**: 서버가 최대 1분 늦게 몰아 쓰므로 1일 자정 직후에 합치면 지난달 마지막 몇 분이 빠질 수 있다 → 매달 1일 00:30 이후 + 2일 한 번 더(같은 달은 덮어쓰기라 결과가 같다). 13개월 지난 줄 지우기는 합친 뒤에.
- **화면 종류(`kind`)도 서버가 정한다**: 지금은 브라우저가 보낸 `kind` 를 쓰는데 페이지별 표 열쇠가 `(날짜, 주소, kind)` 라 같은 주소에 kind 만 바꿔 보내면 줄이 최대 10배 는다 → 결정 ② 의 라우트 표가 kind 도 정하고 브라우저 값은 무시한다.
- **설정 칸은 바꾸지 말고 더한다(롤링 배포)**: API 서버 2~3대를 차례로 띄우는 동안 옛 jar 와 새 DB 가 함께 돈다 → `raw_retention_months` 를 새로 더하고 `raw_retention_days` 는 다음 버전에 지운다. 설정 변경 이력(`util-setting-history-messages.ts`·`settingHistory` 문구)은 옛 기록의 「일」 값도 읽어 보인다.
- **월별로 합칠 표(빠짐 없게)**: `analytics_page_daily` · `analytics_flow_daily` · `analytics_referrer_daily` · `analytics_campaign_daily` · `analytics_env_daily` · `analytics_search_daily` · `analytics_search_filtered_daily` · `analytics_bot_daily` → 같은 열쇠의 월별 표, `analytics_visit_hourly` → 월 방문(방문자 일별 합·신규·페이지뷰). 13개월 지우기도 이 표들 전부. 진단 표(`analytics_collector_*`)는 원래 정리 크론(`R__analytics_14`)대로.
- **기한 없이 두는 shared 통계 표(대상 밖, 작음)**: `board_activity_daily`·`site_activity_daily`(게시판 수 × 날) · `notification_send_daily` · `ad_daily_stats`(광고주 성과·정산 근거라 지우지 않는다).
- **회원 표시 넣기 줄이기**: 방문자는 「그날 하루 표시에 처음 들어간 때」만 주·월 표시를 넣어 보지만 회원은 그런 관문이 없다 → 서버마다 메모리에 「이번 기간에 이미 넣은 회원」 집합(기간이 바뀌면 비움)을 두어 DB 시도는 서버당·회원당·기간당 한 번.
- **삭제 요청의 해시 모양**: 주·월 표시는 `uuid` 라 삭제 크론은 64자 해시 앞 32자를 바꿔(`substr(visitor_hash, 1, 32)::uuid`) 지운다.
- **회원 탈퇴 파기**: 탈퇴 정리(`R__batch_36` 쪽)에 회원 주·월 표시 지우기를 넣는다. 분석 DB 를 따로 떼게 되면 이 지우기도 그 DB 에서.
- **기존 집계 합치기**: 옛 줄은 slug·key 를 확인하지 않고 동적 자리만 `{id}` 로(지운 게시판 기록도 남긴다), 같은 열쇠가 된 줄은 더해서 한 줄로.
- **게시판 slug 를 바꾸면**: 옛 slug 로 오는 새 신호는 「알 수 없는 주소」. 지난 줄은 옛 slug 그대로, 화면 제목을 못 찾으면 slug 를 그대로 보인다.
- **수집 진단 화면**: 「뺀 계정 신호」·「알 수 없는 주소」 수 칸 추가(admin-web 작은 수정).
- **처리방침 고칠 곳(한데 모음 — `site-analytics-privacy-draft.md`)**: 원본 보관 최대 2개월(이번 달과 지난달) · 원본·집계에 읽은 글·주문 번호를 남기지 않음(주소는 묶음으로만) · 주·월 순방문자 표시(방문자 해시 최대 한 달 남짓) · 회원 활동 표시(회원 번호, 탈퇴 때 파기) · 운영자·AI 계정 신호는 세지 않음(안내 불필요, 내부 기준).

### 3단계 성능 확인 (2026-11-11)
- 신호마다: 라우트 바꾸기·slug/key 확인·뺄 계정 확인·세션→회원 확인 모두 서버 메모리(DB 는 캐시가 빌 때만).
- 1분 몰아쓰기: 방문자 주·월 표시 문장 +2(그날 처음 본 방문자만), 회원 주·월 표시 문장 +2(서버당·회원당·기간당 한 번), 숫자 표 더하기 +1.
- 크론: 판정 표·원본·주/월 표시는 조각 DROP 만(큰 DELETE 없음, 결정 ⑥), 월 크론의 13개월 지우기는 정해진 주소 기준이라 한 달치 줄이 적다. 게시판 활동의 계정 빼기는 대상 목록이 작아 쿼리 부담이 작다.

### TimescaleDB — 지금은 넣지 않는다 (2026-11-11 결정)
- 3단계가 하는 일(시간 파티션 = 원본 달·판정 표 하루/기간 조각, 보관 = pg_cron 조각 DROP, 집계 = 1분 몰아쓰기 일별 + 월 크론 합치기)이 하이퍼테이블·보관 정책·연속 집계를 이미 대신한다. 남는 이점은 압축뿐인데 원본이 2개월 상한(큰 규모 약 9GB)이라 크지 않다.
- 설치형 배포라 넣으면 모든 설치본에 확장 설치·PostgreSQL 올릴 때 확장 버전 맞추기가 따라온다. 압축·연속 집계는 Timescale License(TSL) 기능이라 조건 확인이 늘어난다. 관리자 화면은 원본을 거의 읽지 않아(실시간 30분뿐) 원본 위 시계열 조회 이점도 쓰지 않는다.
- 옮기기 쉬운 모양은 그대로 지킨다: `analytics_` 접두사 · 별도 스트림·연결(`analyticsJdbc`) · 원본 기본 키 맨 앞이 시각 · 자체 PostgreSQL(RDS 아님).
- 다시 볼 기준: 원본을 6~12개월처럼 길게 보관해야 할 때(압축 이득) · 하루 페이지뷰가 수백만 단위가 될 때 · 원본에서 그때그때 묻는 분석 화면이 필요해질 때.

### 3단계 확인 결과 (2026-11-11 — 따로 바꿀 것 없음)
- **설치 프리셋·데모 팩**: 구조 프리셋(`export-install-preset.mjs`)에는 통계 설정이 없고, 데모 팩(`build-demo-pack.mjs`)은 `stats_collection_settings`·이력을 `EXCLUDE`, `analytics_` 로 시작하는 표를 `EXCLUDE_PREFIX` 로 뺀다 → 새 표도 `analytics_` 접두사면 자동으로 빠진다(shared 쪽 `deal_click_daily` 는 지금도 데모 팩 대상 — 파티션으로 바꿀 때 조각 이름이 담기지 않는지 확인).
- **user-web**: 바꿀 것 없음 — 브라우저는 지금처럼 실제 주소를 보내고 서버가 바꾼다.
- **기간 선택 최대 90일**: 그대로 둔다. 일별이 13개월 있어도 긴 기간은 「월별 보기」로 본다(한 화면에서 90일 넘는 일별 막대는 읽기 어렵고 묻는 양만 는다).
- **UTM 캠페인 값**: 열린 칸이라 임의 값으로 줄을 늘릴 여지가 있지만 칸마다 100자 제한 · 세션 첫 페이지에서만 받음 · 일별 13개월 보관이라 상한이 있다 → 지금은 그대로. 큰 규모가 되면 검색어와 같이 월별에 「그달 상위 N개」만 남긴다(아래 미확정과 함께 정함).

### 3단계 미확정
- 회원 활동을 세는 지점 — 권장: 방문 신호 `POST /api/visits` 에서 로그인 세션을 보고 센다(수집 스위치·봇 제외를 그대로 따름). 대안: 로그인한 모든 API 요청.
- 주소별 순방문자(정해진 주소마다 중복 없이 몇 명) — 셀지, 조회수만 둘지.
- 화면: WAU·MAU(「방문자 통계 › 개요」 순방문자 카드 — 이번 주·지난 주·이번 달·지난 달, 같은 시점 증감, 최근 12주·12개월 막대, 방문자/회원 전환 · 대시보드 「방문 현황」 한 줄) · 「월별 보기」 — 목업부터.
- 광고주용 산정 기준 문구(브라우저 기준·봇 제외·달력 월)를 화면에 적을지.
- 과거 소급: 방문자 MAU 는 남은 원본(최대 2개월)으로만, 회원 MAU 는 소급 불가 — 시작한 날부터 쌓임.
- 검색어·UTM 캠페인 월별을 「그달 상위 N개」만 남길지(N 값 포함).
- 광고 판정 `ad_view_marks` 도 날짜 파티션으로 바꿀지(결정 ⑥ 권장).

### 고칠 곳 목록(빠뜨리기 쉬운 것, 2026-11-11 확인)
- 서버: 보관 설정 묶음(`StatsCollection` · `StatsCollectionSettingEntity` · `AnalyticsSettingsAdminService`·`AnalyticsSettingsAdmin` DTO · `AnalyticsSettingsAdminController` · 요약 응답 `VisitAnalyticsAdmin` 의 보관 값) · `VisitDiagnosticsAdminService`(달 기준 판정·새 칸) · `VisitAnalyticsStore`(`{id}` 묶음 줄 제목) · api `VisitCollector`(라우트 표·kind·뺄 계정·세션 캐시)·`VisitBuffer`·`VisitFlusher`·`AnalyticsWriteStore`(주·월 표시·숫자 표) · `SearchController` 검색어 셈(뺄 계정) · 대시보드 「회원 현황」 오늘 가입 쿼리 · `R__analytics_10`·`11`·`13`·`R__batch_36`·`R__batch_38` · `BatchCatalog`(새 크론).
- 시험: `rawRetentionDays` 를 쓰는 시험 8개(`AnalyticsSettingsAdminHttpTest` · `VisitAnalyticsAdminServiceTest` · `PageViewPartitionTest` · `VisitStage2AdminHttpTest` · `StatsCollectionStage2Test` · `VisitAnalyticsAdminHttpTest` …)를 새 칸에 맞춘다.
- 관리자 화면: `pages/settings/analytics.vue`(보관 선택지) · `useAnalyticsSettingsApi(.types).ts` · 설정 이력 문구(`util-setting-history-messages.ts`·`i18n/locales/{ko,en}/settingHistory.ts`) · 수집 진단 새 칸 2개 · 「월별 보기」 · WAU·MAU 카드.

### QA 때 볼 것(QA 요청 때 시나리오로)
- 서버 3대 동시 기록에서 주·월 순방문자(방문자·회원) 1명 = 1명 · 주(일→월요일)·달(말일→1일) 자정 경계 · 「같은 시점까지」 합.
- 운영자 회원 계정·AI 회원 신호가 원본·집계·순방문자·검색어·게시판 활동·오늘 가입 모두에서 빠짐, 수집 진단 「뺀 계정 신호」.
- 라우트 표: 동적 라우트 13개 · 없는 slug/key · 모르는 주소 · kind 위조.
- 삭제 요청이 주·월 표시(`uuid`)까지 지움 · 탈퇴 때 회원 표시 지움.
- 조각: 원본 달 DROP(2개월) · 하루 표시·핫딜 클릭·주/월 표시 조각 미리 만들기와 DROP · 자정 조각 없음 오류 뒤 다시 쓰기.
- 월 크론: 두 번 돌려도 같은 결과 · 13개월 지난 줄 지우기 · 1일 00:30 실행 전 늦게 온 줄.
- 설정: 옛 일 값 → 개월 옮기기 · 설정 이력에 옛 「일」 값 표시 · 롤링 배포 중 옛 jar.

### 1차 구현 결과 (2026-11-11 — 컴파일·admin-web 타입 검사만, 시험은 QA 요청 때)
- 번호: shared **V189**(보관 개월 칸 + `deal_click_daily` 하루 파티션) · analytics **V5**(기존 페이지·이동 줄 `{id}` 합치기 + 진단 칸 `ignored_excluded`·`unknown_path`) · **V6**(`analytics_visit_marks` 하루 파티션) · **V7**(월별 표 9개 + `analytics_monthly_rollups`) · 크론 **`R__analytics_15`**(`analytics-monthly-rollup`) · shared **`R__batch_43`**(`deal-click-partitions`, 00:50). `R__analytics_10`·`11`·`R__batch_38` 고침. 다음 번호: shared V190 · analytics V8 · `R__batch_44` · `R__analytics_16`.
- 서버 새 부품: api `VisitRoutes`(라우트 표 — user-web 라우트를 더하면 `TEMPLATES` 도 고친다) · `VisitReferenceCache`(가젯 페이지 키·뺄 계정 1분) · `VisitSessionMembers`(`SESSION` 쿠키 → 회원 10분, 최대 10만, 세션을 만들지 않음) · shared `AnalyticsReferenceStore`(뺄 계정 조건 — 대시보드 「회원 현황」도 씀).
- 관리자 API 칸: 설정·요약 `rawRetentionMonths` · 진단 `instances[].ignoredExcluded`·`unknownPath`, `raw.retentionMonths`, 크론 8개 · 페이지 순위·서랍·「인기 페이지」 가젯은 `collect.visits` 기준.
- **계약과 다른 점(구현 때 정함)**:
  - 월 크론은 **매일 00:30** — 한국 1·2일을 pg_cron(UTC) 식으로 옮기면 날짜가 밀려서. 할 일이 없으면 SKIP, 1·2일엔 지난달을 덮어써 합치고, 합친 기록(`analytics_monthly_rollups`)이 없는 끝난 달은 따라잡아 합친다(첫 배포 포함). 합치기가 다 성공해야 13개월 지난 줄을 지운다.
  - `/{unknown}` 의 화면 종류는 OTHER(예전 브라우저 분류는 없는 게시판 `/boards/xyz` 도 BOARD 였다). 주소 맞추기는 vue-router 와 같게(끝 `/` 하나 뗌·정적 조각 대소문자 무시·같은 자리면 정적 주소 우선, slug·key 는 글자 그대로).
  - 글 → 다른 글 이동은 `/posts/{id}` → `/posts/{id}` 한 줄로 남는다(같은 경로 다시 보기 판정은 받은 실제 주소로).
  - 브라우저 kind 를 읽지 않으므로 kind 가 없거나 틀린 신호도 받는다(`ignoredInvalid` 로 세지 않음). 뺄 계정의 검색어는 세지 않지만 `ignoredExcluded` 에는 넣지 않는다(방문 신호만).
  - 원본 보관 판정(`RAW_BACKLOG`)의 수는 여전히 「넘친 날 수」, 달 1일엔 정리가 하루 늦는 것까지 정상으로 본다.
  - 데모 팩(`build-demo-pack.mjs`)은 `deal_click_daily` 를 담지 않는다(하루 조각이라 옛 날짜 줄이 새 설치에 들어갈 조각이 없고, 방문자 해시만 있다).
- **배포 순서**: api 먼저(analytics 스트림을 api 가 옮긴다 — 관리자 서버가 먼저 V5 전 DB 를 읽으면 진단 새 칸이 없어 오류). 롤링 중 옛 서버가 쓴 몇 분치는 옛 주소로 남는다(통계라 허용).
- **QA(2026-11-11)**: 실제 버그 2건을 고쳤다 — ⓐ 표시 표가 파티션이 되자 방문자 몰아쓰기의 `RETURNING (xmax = 0)` 이 0A000(파티션 표에선 시스템 칸을 못 돌려줌)로 매번 실패해 방문자·페이지뷰·지금 접속이 하나도 쌓이지 않았다 → `AnalyticsWriteStore.visits` 를 한 트랜잭션 세 문장(표시 넣기 `ON CONFLICT DO NOTHING RETURNING` → 이미 있던 표시의 신규 false→true `FOR UPDATE` 키 차례 → 시간 칸 더하기)으로. ⓑ 새 설치의 보관 기본값이 V133 기본 90일을 옮겨 3개월이 되던 것 → V189 가 한 번도 저장하지 않은 설정(`updated_at IS NULL`)은 2개월. 아래 낡은 시험은 새 동작에 맞게 고쳤고 shared·api·admin 묶음(방문 통계·핫딜·대시보드) 전부 통과.
- **QA 때 고친 시험**(동작이 바뀜): `PageViewPartitionTest`(일 → 달 경계) · `VisitCollectorGateTest`·`VisitStage2CollectTest`(kind·주소 묶음·페이지별 집계 스위치) · `VisitFlushMultiServerTest`·`VisitStage2FlushTest`·`VisitForgetCronTest`(판정 표 조각 — 시험 날짜에 조각 필요) · `VisitDiagnosticsAdminServiceTest`(RAW_BACKLOG) · `VisitAnalyticsAdminServiceTest`·`DashboardAnalyticsGadgetsTest`(스위치 기준) · `BoardActivityDailyBatchTest`·`HotdealGoTest`·`StatsCollectionStage2Test`.
- **화면(목업 — 확정 전)**: 「방문자 통계 › 개요」 순방문자 카드(방문자/회원, 이번 주·지난 주·이번 달·지난 달, 같은 시점 증감, 12주·12개월 막대) · 대시보드 「방문 현황」 한 줄 · 새 화면 `/analytics/visits/monthly` 「월별 보기」(메뉴 없음, 개요 추이 카드 링크). 목업 API 모양의 원천 = `apps/admin-web/app/composables/analytics/api/useVisitAnalyticsApi.types.ts` 3단계 주석(`GET …/visits/uniques` · `GET …/visits/monthly?from=YYYY-MM&to=YYYY-MM` · 대시보드 `VISITORS.visitorsThisWeek`·`visitorsThisMonth`). 지울 곳: `mock/visit-analytics-mock.ts` + `// MOCK:` 줄.
- 화면 확정 때 정할 것: 월별 보기는 지금 수집 스위치가 꺼지면 지난 숫자도 가린다 — 긴 기간 화면이라 보이게 할지 · 월별 응답에 회원 MAU 를 넣을지 · 월별 보기 메뉴 자리(관리자 메뉴는 DB — 서버 때 admin 마이그레이션).

### 결정 ④ 화면 확정 (2026-11-11 사용자 「이대로 확정」)
- 화면: 「방문자 통계 › 개요」 순방문자 카드 · 대시보드 「방문 현황」 한 줄 · 「월별 보기」 `/analytics/visits/monthly` — 목업 그대로. 응답 모양의 원천 = `apps/admin-web/app/composables/analytics/api/useVisitAnalyticsApi.types.ts`(`VisitUniques` · `VisitMonthly`) + 대시보드 `VISITORS.visitorsThisWeek`·`visitorsThisMonth`(`number | null`).
- 사용자 결정: ⓐ 월별 보기는 지금 수집 스위치가 꺼져 있어도 **쌓인 지난 기록을 보인다**(위에 「지금은 수집이 꺼져 있어 이번 달은 늘지 않아요」 안내) · ⓑ 월별 보기에 **회원 MAU 도** 넣는다(`months[].uniqueMembers: number | null`, 방문자/회원 전환) · ⓒ 메뉴 = 「방문자 통계」 묶음의 **개요 바로 아래**(`visitMonthly`, site.read — admin 메뉴 마이그레이션).
- API(관리자, site.read, 결과 캐시 60초 — 다른 방문 통계와 같게): `GET /api/admin/analytics/visits/uniques`(기간 없음, 오늘 기준) · `GET /api/admin/analytics/visits/monthly?from=YYYY-MM&to=YYYY-MM`(최대 36개월 — 400 규칙은 types 주석). 월별 보기의 아직 합치지 않은 달(이번 달·1~2일의 지난달 — `analytics_monthly_rollups` 에 없는 달)은 일별 표를 더해 채운다.

### 결정 ④ 구현 결과 (2026-11-11 — 컴파일·admin-web 타입 검사만, 시험·DB 적용은 QA 때)
- 번호: analytics **V8**(표시 표 4개 — `analytics_visitor_{week,month}_marks`(visitor_id uuid) · `analytics_member_{week,month}_marks`(member_id bigint), 모두 `period_start` 범위 파티션 · 숫자 표 `analytics_unique_counts(audience, period_kind, period_start, day, added)`) · 크론 **`R__analytics_16`**(`analytics-period-mark-partitions`, 매일 00:55 — 이번·다음 기간 조각 만들기, 끝나고 3일 지난 조각 DROP) · admin **V9**(메뉴 `visitMonthly` — 메뉴 구성을 저장한 설치에만 개요 뒤에 넣음). 다음 번호: shared V190 · analytics V9 · admin V10 · `R__batch_44` · `R__analytics_17`.
- 쓰기: 방문자는 그날 새로 넣은 하루 표시만 주·월 표시에 넣고, 회원은 버퍼가 모은 회원 번호를 서버 메모리 「이번 기간에 이미 넣은 회원」(커밋 뒤에만 기억, 종류마다 상한 20만)으로 걸러 넣는다 — 같은 트랜잭션, 모두 `ON CONFLICT DO NOTHING RETURNING`(시스템 칸 없음)·키 차례, 조각 없음(23514)이면 하루·기간 조각을 만들고 한 번 다시.
- 지우기: 삭제 요청(`R__analytics_13`)이 방문자 주·월 표시를 해시 앞 32자 → uuid 로 지운다(숫자 표는 남김). **회원 탈퇴는 `WithdrawalCompletionService` 가 `AnalyticsMemberMarks` 로 지운다**(계약의 `R__batch_36` 대신 — 분석 DB 를 떼어도 그 DB 에서 지워지게, 표가 아직 없으면 `to_regclass` 로 보고 건너뜀).
- API: `GET …/visits/uniques`·`GET …/visits/monthly`(types 와 칸 같음, 결과 캐시 60초 — 키에 오늘 날짜) · 대시보드 `VISITORS.visitorsThisWeek`·`visitorsThisMonth`. uniques 는 `visits` 가 꺼져 있으면 수를 null 로 준다(화면이 꺼짐 안내로 덮음). monthly 는 스위치와 상관없이 쌓인 숫자를 준다(결정 ⓐ), 합치지 않은 달은 시간별·일별에서 채운다.
- 수집 진단 크론 목록에 `analytics-period-mark-partitions` 를 더했다(9개). 화면: 목업을 걷어 내고 실제 API · 월별 보기 방문자/회원 전환(회원일 때 툴팁 셋째 줄 = 로그인 회원 비율) · 꺼짐 안내 · 메뉴 `menu.visitMonthly`(`pi pi-calendar`, 화면 메뉴 표 `util-admin-menu.ts` 에도 한 줄).
- **QA(2026-11-11)**: 실제 버그 없음 · 새 SQL(V8·`R__analytics_16`·삭제 요청 uuid·admin V9)은 Testcontainers·임시 PostgreSQL 18 에서 적용·동작 확인 · 낡은 시험 3건(`VisitFlushMultiServerTest` — 기간 조각 준비·4인자 실패 주입) 고침 · 새 시험: api `VisitUniqueFlushTest`(서버 3대·주/달 경계·조각 없음 다시 쓰기·회원 메모리) · `VisitPeriodMarkPartitionCronTest`(조각 만들기·DROP 경계·CALL) · `VisitForgetCronTest`(uuid 표시 지우기) · `WithdrawalFlowTest`(탈퇴 때 회원 표시 지우기), admin `VisitUniquesMonthlyServiceTest`(null/0·같은 시점·윤년·월별 섞기·400) · `VisitStage3AdminHttpTest`(403·400·꺼짐) · `DashboardAnalyticsGadgetsTest`(이번 주·이번 달). shared·api·admin 묶음 실패 0. 시험이 없는 것: V9 메뉴 자리(임시 DB 로만 확인). 알려진 점: `WithdrawalFlowTest` 의 기존 「모임장」 시험은 게시판 이름(`id % 1000`)이 겹치면 가끔 실패한다(이번 변경과 무관).
- QA 때 볼 것: 서버 3대 동시 쓰기에서 방문자·회원 1명 = 1명 · 주(일→월)·달(말일→1일) 경계·조각 없음 다시 쓰기 · 서버 재시작 뒤 같은 회원 두 번 세지 않음 · 삭제 요청·탈퇴 지우기 · 조각 크론 · `previousSamePoint`(31일 → 2월)·null/0 규칙 · 월별 합친 달과 안 합친 달 섞기·스위치 끈 값 · V9 메뉴 자리.

### 진행 순서·번호(2026-11-11 메모 기준 — 시작할 때 develop 최신에서 다시 확인)
1. 원본 달 단위(①) · 주소 `{id}`(②) · 핫딜 클릭 정리(⑤) · 판정 표 파티션(⑥) — 화면 변화가 작다.
2. 일별 13개월 + 월별(③) — 서버 먼저, 「월별 보기」 화면은 목업 확정 뒤.
3. WAU·MAU(④) — 화면 목업 확정 → 서버 → 연결.
- 다음 번호: shared V188 · analytics V5 · shared 배치 `R__batch_43` · analytics 크론 `R__analytics_15`.
