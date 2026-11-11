-- 방문 통계 3단계 — docs/features/site-analytics-contract.md 「3단계」 결정 ③(일별 13개월 + 월별 기한 없음) · 「검토 보강 — 월별로 합칠 표」.
--
-- 일별 집계 8개 + 시간별 방문(analytics_visit_hourly)을 끝난 달마다 같은 열쇠의 월별 표로 합친다 — DB 배치 `analytics-monthly-rollup`(R__analytics_15, 매일 00:30 KST,
-- 매달 1·2일에 지난달을 덮어써 합친다 — 서버가 최대 1분 늦게 몰아 쓰므로 2일에 한 번 더). 그 뒤 「이번 달 + 지난 12개월」보다 오래된 일별·시간별 줄을 지운다(합친 달만).
-- month = 그 달 1일(한국 달). 월별 표는 기한 없이 둔다(정해진 주소 기준이라 작다 — 연별은 만들지 않는다, 월별 12줄의 합).
-- 월별 방문 표의 방문자는 「일별 합」(visitors_daily_sum — 하루 방문자를 한 달 더한 값)이다. 월 순방문자가 아니다 — 월 순방문자는 결정 ④ 의 숫자 표에서만 읽는다.
-- 열쇠·칸은 일별 표와 같다(빈 칸 '' 규칙 포함). 관리자 「월별 보기」 API 는 화면 확정 뒤에 만든다(이번에는 쌓기만).
-- 진단 표(analytics_collector_*)는 합치지 않는다(원래 정리 크론 R__analytics_14 대로 7일).

CREATE TABLE analytics_visit_monthly (
    month              date   PRIMARY KEY,
    visitors_daily_sum bigint NOT NULL DEFAULT 0,
    new_visitors       bigint NOT NULL DEFAULT 0,
    page_views         bigint NOT NULL DEFAULT 0
);

CREATE TABLE analytics_page_monthly (
    month date         NOT NULL,
    path  varchar(300) NOT NULL,
    kind  varchar(12)  NOT NULL,
    views bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (month, path, kind)
);

CREATE TABLE analytics_flow_monthly (
    month     date         NOT NULL,
    from_path varchar(300) NOT NULL,
    to_path   varchar(300) NOT NULL,
    moves     bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (month, from_path, to_path)
);

CREATE TABLE analytics_referrer_monthly (
    month           date         NOT NULL,
    referrer_group  varchar(12)  NOT NULL,
    referrer_domain varchar(253) NOT NULL DEFAULT '',
    visits          bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (month, referrer_group, referrer_domain)
);

CREATE TABLE analytics_campaign_monthly (
    month        date         NOT NULL,
    utm_source   varchar(100) NOT NULL DEFAULT '',
    utm_medium   varchar(100) NOT NULL DEFAULT '',
    utm_campaign varchar(100) NOT NULL DEFAULT '',
    visits       bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (month, utm_source, utm_medium, utm_campaign)
);

CREATE TABLE analytics_env_monthly (
    month   date       NOT NULL,
    device  varchar(8) NOT NULL,
    browser varchar(8) NOT NULL,
    os      varchar(8) NOT NULL,
    screen  varchar(6) NOT NULL,
    visits  bigint     NOT NULL DEFAULT 0,
    PRIMARY KEY (month, device, browser, os, screen)
);

CREATE TABLE analytics_search_monthly (
    month     date        NOT NULL,
    term      varchar(50) NOT NULL,
    searches  bigint      NOT NULL DEFAULT 0,
    no_result bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (month, term)
);

CREATE TABLE analytics_search_filtered_monthly (
    month    date   PRIMARY KEY,
    searches bigint NOT NULL DEFAULT 0
);

CREATE TABLE analytics_bot_monthly (
    month    date        NOT NULL,
    kind     varchar(16) NOT NULL,
    requests bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (month, kind)
);

-- 합친 달 기록 — 그 달을 월별로 합쳤다(rolled_at = 마지막으로 합친 시각). 이 줄이 있는 달만 13개월이 지나면 일별·시간별 줄을 지운다
-- (지우다 멈춘 달을 남은 줄로 다시 합쳐 월별을 덮어쓰지 않게 — 합치기는 이 줄이 없는 달, 또는 지난달을 매달 1·2일에만 다시 한다).
CREATE TABLE analytics_monthly_rollups (
    month     date        PRIMARY KEY,
    rolled_at timestamptz NOT NULL
);
