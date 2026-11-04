-- 방문자 통계 2단계 — docs/features/site-analytics-contract.md 「2단계 — 환경·검색어·봇·수집 진단·정보주체 요청」(화면 확정 2026-11-04).
-- 쓰기는 모두 api 의 VisitFlusher(서버마다 메모리에 모았다가 1분마다·종료 때 몰아 쓴다 — 문장 수는 방문량과 무관)가 한다. 예외: 삭제 요청은 공개 API 가 한 줄씩 넣는다(드문 요청).
-- 서버 여러 대 정확성: 신호·요청 하나는 서버 한 대만 받으므로 아래 일별 더하기 upsert 는 서버마다 자기 몫을 더해도 합이 정확하다(유입처·이동과 같은 근거).
-- 개인정보: 원본 User-Agent · 원본 화면 크기 · IP · 원본 방문자 ID 를 저장하지 않는다(분류값 · 다듬은 검색어 · 방문자 해시만).
-- 정리(pg_cron): R__analytics_13(삭제 요청 처리 5분) · R__analytics_14(진단 기록 7일 · 처리 끝난 요청 1년).

-- ── 환경 — 방문(세션 첫 페이지) 기준 ───────────────────────────────────────────
-- device : MOBILE · TABLET · DESKTOP (서버가 User-Agent 로 가른다)
-- browser: CHROME · SAFARI · EDGE · SAMSUNG · WHALE · FIREFOX · OTHER / os: WINDOWS · MACOS · ANDROID · IOS · LINUX · OTHER (브라우저가 분류해 보낸 값, 목록 밖이면 OTHER)
-- screen : W480 · W768 · W1024 · W1440 · WIDE (창 너비 구간)
-- 네 분류를 한 줄에 두고 관리자 화면이 기간으로 묶어 분류별 합을 낸다(하루 줄 수 ≤ 3 × 7 × 6 × 5 = 630). 기본 키 앞(day) 범위로 읽는다.
CREATE TABLE analytics_env_daily (
    day     date       NOT NULL,
    device  varchar(8) NOT NULL,
    browser varchar(8) NOT NULL,
    os      varchar(8) NOT NULL,
    screen  varchar(6) NOT NULL,
    visits  bigint     NOT NULL DEFAULT 0,
    PRIMARY KEY (day, device, browser, os, screen)
);

-- ── 우리 사이트 검색어 — 공개 검색 API 의 첫 쪽만 ─────────────────────────────
-- term = 다듬은 검색어(앞뒤 공백 · 연속 공백 한 칸 · 영문 소문자, 2~50자). 이메일·전화번호·주민등록번호 모양과 길이 밖은 저장하지 않고 아래 걸러진 수만 더한다.
-- no_result = 그중 결과가 0건이던 검색 수. 「마지막으로 결과 없던 날」은 no_result > 0 인 날의 최댓값(따로 칸을 두지 않는다).
CREATE TABLE analytics_search_daily (
    day       date        NOT NULL,
    term      varchar(50) NOT NULL,
    searches  bigint      NOT NULL DEFAULT 0,
    no_result bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (day, term)
);

CREATE TABLE analytics_search_filtered_daily (
    day      date   PRIMARY KEY,
    searches bigint NOT NULL DEFAULT 0
);

-- ── 봇·크롤러 요청 — 공개 화면 HTML 요청(SPA 의 index.html) 하나 = 1 ─────────────
-- kind: GOOGLE · NAVER · BING · DAUM · OTHER_CRAWLER · TOOL
CREATE TABLE analytics_bot_daily (
    day      date        NOT NULL,
    kind     varchar(16) NOT NULL,
    requests bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (day, kind)
);

-- ── 수집 진단 — 서버(instance)마다 1분에 한 문장 ─────────────────────────────
-- beats : 마지막 1분 기록 시각(신호가 없어도 1분마다). 관리자 「수집 진단」이 멈춘 서버를 이것으로 안다.
-- hourly: 정시 칸 × 서버 — 기록(쓰기 묶음) 성공·실패 수 · 메모리 상한으로 버린 줄 · 무시한 신호(봇 · 형식 오류)
-- failures: 기록 실패 한 줄(사유 코드 DB_TIMEOUT · DB_UNAVAILABLE · CONSTRAINT · UNKNOWN + 예외 이름·SQLSTATE·묶음 이름 — 값·문장 없음)
-- 7일 보관(R__analytics_14). 관리자 화면은 최근 24시간만 읽는다.
CREATE TABLE analytics_collector_beats (
    instance      varchar(64) PRIMARY KEY,
    last_write_at timestamptz NOT NULL
);
CREATE INDEX ix_analytics_collector_beats_seen ON analytics_collector_beats (last_write_at);

CREATE TABLE analytics_collector_hourly (
    hour            timestamptz NOT NULL,
    instance        varchar(64) NOT NULL,
    flush_ok        bigint      NOT NULL DEFAULT 0,
    flush_fail      bigint      NOT NULL DEFAULT 0,
    dropped         bigint      NOT NULL DEFAULT 0,
    ignored_bot     bigint      NOT NULL DEFAULT 0,
    ignored_invalid bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (hour, instance)
);

CREATE TABLE analytics_collector_failures (
    failed_at timestamptz NOT NULL,
    id        bigint      GENERATED ALWAYS AS IDENTITY,
    instance  varchar(64) NOT NULL,
    reason    varchar(20) NOT NULL,
    detail    varchar(200),
    PRIMARY KEY (failed_at, id)
);

-- ── 방문 기록 삭제 요청(정보주체 요청) ─────────────────────────────────────────
-- 공개 POST /api/visits/forget 이 한 줄 넣고(방문자 ID 해시만 — 원본 ID · IP 없음), DB 크론(R__analytics_13, 5분)이 원본·판정 표시·지금 접속에서 그 해시의 줄을 지운 뒤
-- status = DONE · deleted_rows · processed_at 을 적고 visitor_hash 를 비운다(NULL). 합산 표(시간·일별)는 사람을 가리키지 않아 남긴다.
-- 같은 해시의 대기 줄은 하나만(유일 인덱스 — 서버 여러 대가 동시에 받아도). 처리 끝난 줄은 1년 뒤 지운다(R__analytics_14).
CREATE TABLE analytics_forget_requests (
    id           bigint      PRIMARY KEY,                -- Snowflake(앱 발급) — 화면의 요청 번호
    visitor_hash varchar(64),                            -- 처리하면 NULL
    received_at  timestamptz NOT NULL,
    status       varchar(8)  NOT NULL,
    deleted_rows bigint,                                 -- 처리 중 누적(대기 중에는 화면에 보이지 않는다)
    processed_at timestamptz,
    CONSTRAINT ck_analytics_forget_requests_status CHECK (status IN ('PENDING', 'DONE')),
    CONSTRAINT ck_analytics_forget_requests_hash CHECK ((status = 'PENDING') = (visitor_hash IS NOT NULL))
);
CREATE UNIQUE INDEX ux_analytics_forget_requests_pending ON analytics_forget_requests (visitor_hash) WHERE status = 'PENDING';
-- 관리자 목록(최신 차례) · 지난 30일 수 · 1년 보관 정리
CREATE INDEX ix_analytics_forget_requests_received ON analytics_forget_requests (received_at, id);
