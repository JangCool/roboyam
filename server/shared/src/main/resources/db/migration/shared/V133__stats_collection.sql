-- 통계 수집 — docs/features/site-analytics-contract.md(화면 확정 2026-11-04). 사용자 기준: 정확성 + 성능, API 서버 2~3대, 신호마다 DB 에 쓰지 않음, 주기 작업은 pg_cron.
--
-- 이 파일(메인 DB · shared 스트림)에 두는 것 — TimescaleDB 로 옮길 대상이 아니라서 analytics_ 접두사를 쓰지 않는다:
--   ① stats_collection_settings  : 「설정 › 통계 수집」 한 행(id = 1). 모든 수집 기본 꺼짐 · 원본 보관 90일. 크론(R__batch_37·38, analytics 스트림 R__analytics_*)이 시작할 때 읽는다.
--   ② stats_collection_setting_history : 설정 변경 이력(광고 노출 자리 설정 이력 V120 과 같은 짜임). 값이 실제로 바뀐 저장만 한 줄.
--   ③ notification_send_daily / notification_send_failures : 알림 발송 결과(메일 MailDispatcher 경유 전부 · Slack · Mattermost). 앱이 메모리에 세다가 1분마다 더한다.
--      수신자·본문·외부 응답은 저장하지 않는다(채널 · 사유 코드 · 출처만). 실패 줄은 30일 뒤 R__batch_37 이 지운다.
--   ④ board_activity_daily / site_activity_daily : 게시판 활동 일별 집계. 원천이 메인 DB 표(posts · comments · users)라 분석 스트림 밖이다 — R__batch_38 이 10분마다 오늘·어제를 다시 센다.
-- 방문 표(analytics_*)는 analytics 스트림(server/api … db/migration/analytics)에 있다.

-- ── ① 설정 ───────────────────────────────────────────────────────────────────
-- enabled = 방문 수집 전체(꺼지면 visits · raw_events · referrers · flows 가 값은 그대로 두고 동작하지 않는다). notification_sends · board_activity 는 enabled 와 무관.
-- raw_retention_days = 방문 원본(analytics_page_views) 보관 일수 30 · 90 · 180 · 365.
CREATE TABLE stats_collection_settings (
    id                 smallint    PRIMARY KEY,
    enabled            boolean     NOT NULL DEFAULT false,
    visits             boolean     NOT NULL DEFAULT false,
    raw_events         boolean     NOT NULL DEFAULT false,
    raw_retention_days integer     NOT NULL DEFAULT 90,
    referrers          boolean     NOT NULL DEFAULT false,
    flows              boolean     NOT NULL DEFAULT false,
    notification_sends boolean     NOT NULL DEFAULT false,
    board_activity     boolean     NOT NULL DEFAULT false,
    updated_at         timestamptz,                -- 마지막 저장(저장한 적 없으면 NULL)
    updated_by         bigint,                     -- 마지막으로 저장한 운영자(operators.id — 지워져도 남도록 FK 없음)
    CONSTRAINT ck_stats_collection_settings_row CHECK (id = 1),
    CONSTRAINT ck_stats_collection_settings_retention CHECK (raw_retention_days IN (30, 90, 180, 365))
);
INSERT INTO stats_collection_settings (id) VALUES (1);

-- ── ② 설정 변경 이력 ──────────────────────────────────────────────────────────
-- changes = [{field, before, after}] — field 차례 enabled · visits · rawEvents · rawRetentionDays · referrers · flows · notificationSends · boardActivity.
-- 값은 글자: 켜기 'true'/'false', 보관 일수는 숫자 글자. 행은 마이그레이션이 만들어 두므로 action 은 UPDATE 뿐이다(첫 저장도 바뀐 칸만).
CREATE TABLE stats_collection_setting_history (
    id          bigint      PRIMARY KEY,                     -- Snowflake(앱 발급)
    action      varchar(10) NOT NULL,
    source      varchar(10) NOT NULL,
    operator_id bigint,
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_stats_collection_history_action CHECK (action IN ('UPDATE')),
    CONSTRAINT ck_stats_collection_history_source CHECK (source IN ('ADMIN')),
    CONSTRAINT ck_stats_collection_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);
CREATE INDEX ix_stats_collection_history_changed ON stats_collection_setting_history (changed_at DESC, id DESC);

-- ── ③ 알림 발송 기록 ──────────────────────────────────────────────────────────
-- day = 한국 날짜. channel = EMAIL · SLACK · MATTERMOST(admin-web notification.channelType.*). 서버마다 자기가 보낸 것만 더하므로(한 발송 = 한 서버) 서버가 여러 대여도 합이 정확하다.
CREATE TABLE notification_send_daily (
    day     date        NOT NULL,
    channel varchar(20) NOT NULL,
    sent    bigint      NOT NULL DEFAULT 0,
    failed  bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (day, channel)
);

-- 실패 한 건 — reason_code 는 ChannelPostResult 의 코드(문구 notification.channel.test.<코드>), source 는 Slack·Mattermost 도구 ID(메일은 NULL).
CREATE TABLE notification_send_failures (
    id          bigint      PRIMARY KEY,                     -- Snowflake(앱 발급)
    failed_at   timestamptz NOT NULL,
    channel     varchar(20) NOT NULL,
    reason_code varchar(40) NOT NULL,
    source      varchar(40)
);
-- 대시보드 「최근 실패」(최근 3건)와 30일 정리(R__batch_37)가 같은 인덱스를 쓴다
CREATE INDEX ix_notification_send_failures_at ON notification_send_failures (failed_at DESC);

-- ── ④ 게시판 활동 ────────────────────────────────────────────────────────────
-- R__batch_38(board-activity-daily)이 오늘·어제 두 날만 지우고 다시 넣는다(원천을 다시 센 값 — 더하기가 아니다). 그 전 날은 그대로 남는다.
-- 「글」 기준: 공개 게시판(소모임 게시판 제외)의 게시 글(status = PUBLISHED) 중 지금 지워지지 않은 것, 쓴 시각(= Snowflake ID) 기준. 숨김 글은 센다.
CREATE TABLE board_activity_daily (
    day      date   NOT NULL,
    board_id bigint NOT NULL,
    posts    bigint NOT NULL DEFAULT 0,
    comments bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (day, board_id)
);

CREATE TABLE site_activity_daily (
    day      date   PRIMARY KEY,
    posts    bigint NOT NULL DEFAULT 0,
    comments bigint NOT NULL DEFAULT 0,
    signups  bigint NOT NULL DEFAULT 0
);
