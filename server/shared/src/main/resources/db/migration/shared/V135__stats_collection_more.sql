-- 통계 수집 2단계 스위치 — docs/features/site-analytics-contract.md 「2단계 — 환경·검색어·봇·수집 진단·정보주체 요청」(화면 확정 2026-11-04).
-- 세 칸 모두 기본 꺼짐이고, 방문 수집 전체(enabled)가 꺼지면 값은 그대로 두고 동작하지 않는다(visits · raw_events · referrers · flows 와 같은 규칙).
--   environment  : 환경(세션 첫 페이지 신호의 브라우저·OS·화면 너비 분류값 + 서버가 User-Agent 로 가른 기기) — analytics V4 analytics_env_daily
--   search_terms : 우리 사이트 검색어(공개 사이트 서버가 GET /api/search 첫 쪽에서 센다) — analytics V4 analytics_search_daily
--   bots         : 봇·크롤러 요청 수(공개 사이트 서버가 화면 HTML 요청의 User-Agent 로 센다) — analytics V4 analytics_bot_daily
-- 이력(stats_collection_setting_history.changes)의 field 차례: … flows · environment · searchTerms · bots · notificationSends · boardActivity.
ALTER TABLE stats_collection_settings ADD COLUMN IF NOT EXISTS environment  boolean NOT NULL DEFAULT false;
ALTER TABLE stats_collection_settings ADD COLUMN IF NOT EXISTS search_terms boolean NOT NULL DEFAULT false;
ALTER TABLE stats_collection_settings ADD COLUMN IF NOT EXISTS bots         boolean NOT NULL DEFAULT false;
