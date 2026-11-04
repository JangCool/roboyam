-- 「방문자 통계」 화면(site-analytics-contract 「「방문자 통계」 화면 (화면 확정 2026-11-04)」 — 서버 결정) 을 위한 표·인덱스.
--
-- 1) 캠페인 일별 집계 — 원본(analytics_page_views)을 읽지 않고 캠페인 표를 그린다. 「유입 경로」(referrers) 수집에 딸린다(원본 기록 스위치와 무관).
--    쓰기: api VisitFlusher 의 유입처 몰아쓰기와 같은 트랜잭션에서 한 문장 더하기 upsert(세션 첫 페이지에 utm_* 가 하나라도 있으면 방문 1).
--    신호 하나는 서버 한 대만 받으므로 서버마다 자기 몫을 더해도 합이 정확하다(유입처·이동과 같은 근거). 빈 utm 칸은 '' (기본 키에 NULL 을 둘 수 없어서).
CREATE TABLE analytics_campaign_daily (
    day          date         NOT NULL,
    utm_source   varchar(100) NOT NULL DEFAULT '',
    utm_medium   varchar(100) NOT NULL DEFAULT '',
    utm_campaign varchar(100) NOT NULL DEFAULT '',
    visits       bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (day, utm_source, utm_medium, utm_campaign)
);

-- 2) 이동 흐름의 「들어온 곳」(기준 페이지·페이지 서랍) — 기간(day 범위) + to_path = ? 로 읽는다.
--    기본 키 (day, from_path, to_path) 로는 from_path 가 사이에 있어 그 날 이동 줄을 모두 훑어야 한다. 「나간 곳」(from_path = ?)은 기본 키로 충분하다.
--    쓰기 부담: 1분 몰아쓰기의 이동 upsert 에서 새 (day, from, to) 줄이 생길 때만 이 인덱스에 한 칸 더 넣는다. 이미 있는 줄의 moves 더하기는
--    인덱스 칸(day·to_path)을 바꾸지 않아 HOT 갱신이 될 수 있다(moves 는 어느 인덱스에도 넣지 않는다 — 넣으면 매분 갱신마다 인덱스도 고쳐 써야 한다).
--    세션 첫 페이지 「바로 들어옴」 줄(from_path = '')도 같은 표에 쌓인다(V3 와 함께 바뀐 수집기).
CREATE INDEX IF NOT EXISTS ix_analytics_flow_daily_to ON analytics_flow_daily (day, to_path);
