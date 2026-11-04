-- 방문 분석 표 — docs/features/site-analytics-contract.md(화면 확정 2026-11-04). analytics 스트림(이력 표 flyway_schema_history_analytics)의 첫 파일.
--
-- 옮기기 쉽게(TimescaleDB): 접두사 analytics_ · 다른 표와 외래 키 없음 · 원본 표의 기본 키 맨 앞이 시각(viewed_at, id — hypertable 조건) · JPA 없이 SQL(analyticsJdbc).
-- 이 스트림은 공개 사이트 서버(api)만 이관한다(쓰는 쪽이 표의 주인 — SchemaMigrationConfig). 관리자 서버는 집계 표를 읽기만 한다.
-- 개인정보: IP·원본 User-Agent·원본 방문자 ID 를 저장하지 않는다. 방문자·세션 키는 서버 HMAC(BlindIndex, 용도 analytics.visitor · analytics.session) 16진 64자.
--
-- 쓰기(api 의 VisitFlusher — 서버마다 메모리에 모았다가 1분마다·종료 때 몇 문장으로 몰아 쓴다. 문장 수는 방문량과 무관):
--   analytics_visit_marks  : 「그 날 이미 셌다」 표시. 한 문장 INSERT … ON CONFLICT DO NOTHING RETURNING 으로 실제 들어간 줄만 방문자 +1 — 서버가 몇 대든 1명은 1명.
--   analytics_visit_hourly : 시간(정시 시각)별 방문자·신규 방문자·페이지뷰 — 더하기 upsert.
--   analytics_visit_active : 「지금 접속」 = last_seen 5분 안 COUNT.
--   analytics_page_views   : 원본(「원본 기록」을 켰을 때만) — 여러 줄 한 문장.
--   analytics_page_daily · analytics_referrer_daily · analytics_flow_daily : 일별 더하기 upsert.
-- 정리(pg_cron, R__analytics_*): 원본 월 조각 만들기·보관 기간 지난 조각 지우기 · marks 2일 · active 10분.

-- ── 원본 페이지뷰(월 조각 — 한국 달 경계) ────────────────────────────────────
-- kind: HOME · BOARD · POST · GROUP_BUY · HOTDEAL · GROUP · POINT_SHOP · SEARCH · MY · OTHER(브라우저가 보낸 화면 종류)
-- referrer_group: 세션 첫 페이지만 — SEARCH · SOCIAL · DIRECT · OTHER_SITE · CAMPAIGN(utm_* 가 있으면). 세션 첫 페이지가 아니면 NULL
-- device: MOBILE · DESKTOP(User-Agent 에서 서버가 가른다 — 원본 UA 는 남기지 않는다)
CREATE TABLE analytics_page_views (
    id              bigint       NOT NULL,                 -- Snowflake(앱 발급)
    viewed_at       timestamptz  NOT NULL,
    visitor_hash    varchar(64)  NOT NULL,
    session_hash    varchar(64)  NOT NULL,
    path            varchar(300) NOT NULL,
    kind            varchar(12)  NOT NULL,
    prev_path       varchar(300),
    referrer_domain varchar(253),
    referrer_group  varchar(12),
    utm_source      varchar(100),
    utm_medium      varchar(100),
    utm_campaign    varchar(100),
    device          varchar(8)   NOT NULL,
    new_visitor     boolean      NOT NULL,
    PRIMARY KEY (viewed_at, id)
) PARTITION BY RANGE (viewed_at);

-- ── 「그 날 셌다」 표시 · 지금 접속 ───────────────────────────────────────────
CREATE TABLE analytics_visit_marks (
    day          date        NOT NULL,                     -- 한국 날짜
    visitor_hash varchar(64) NOT NULL,
    PRIMARY KEY (day, visitor_hash)
);

CREATE TABLE analytics_visit_active (
    visitor_hash varchar(64) PRIMARY KEY,
    last_seen    timestamptz NOT NULL
);
-- 「지금 접속」(last_seen ≥ 지금 - 5분 COUNT)과 10분 정리가 같은 인덱스를 쓴다
CREATE INDEX ix_analytics_visit_active_seen ON analytics_visit_active (last_seen);

-- ── 집계 ─────────────────────────────────────────────────────────────────────
-- hour = 정시 시각(timestamptz — 한국 시간은 UTC 와 정시 단위로 어긋나 한국 정시와 같다). visitors = 그 시간에 그 날 처음 본 방문자 수라 하루 합 = 그 날 방문자 수(중복 없음)
CREATE TABLE analytics_visit_hourly (
    hour         timestamptz PRIMARY KEY,
    visitors     bigint NOT NULL DEFAULT 0,
    new_visitors bigint NOT NULL DEFAULT 0,
    page_views   bigint NOT NULL DEFAULT 0
);

CREATE TABLE analytics_page_daily (
    day   date         NOT NULL,
    path  varchar(300) NOT NULL,
    kind  varchar(12)  NOT NULL,
    views bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (day, path, kind)
);
-- 「인기 페이지」(오늘 많이 본 차례 상위 N)는 기본 키 앞부분(day)으로 그 날 줄만 읽어 정렬한다 — views 인덱스를 따로 두지 않는다(1분마다 바뀌는 칸이라 인덱스가 쓰기를 무겁게 한다)

-- 세션 첫 페이지마다 방문 1. 도메인이 없는 분류(DIRECT · CAMPAIGN)는 '' (기본 키에 NULL 을 둘 수 없어서)
CREATE TABLE analytics_referrer_daily (
    day             date         NOT NULL,
    referrer_group  varchar(12)  NOT NULL,
    referrer_domain varchar(253) NOT NULL DEFAULT '',
    visits          bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (day, referrer_group, referrer_domain)
);

-- 직전 페이지 → 이번 페이지 이동 횟수(같은 주소 다시 보기는 세지 않는다)
CREATE TABLE analytics_flow_daily (
    day       date         NOT NULL,
    from_path varchar(300) NOT NULL,
    to_path   varchar(300) NOT NULL,
    moves     bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (day, from_path, to_path)
);
