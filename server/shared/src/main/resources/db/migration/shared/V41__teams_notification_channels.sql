-- 조직 구성(팀) + 알림 도구(채널) + 이벤트별 전달 도구·추가 수신 팀 — docs/features/notification-contract.md 「알림 도구·조직 구성」
--
-- · 팀: 이름(대소문자 무시 유일)·설명·사용 여부·정렬 순서. 팀원 = 운영자 계정. 팀원이 있는 팀은 지울 수 없다(앱이 409, DB 도 RESTRICT 로 막는다).
-- · 알림 도구: 사용자가 만드는 Slack · Mattermost 만 이 표의 행이다. 서랍·이메일·브라우저는 코드가 내려주는 기본 제공 항목이라 행이 없고,
--   바꿀 수 있는 상태(사용 여부·마지막 연결 시험)만 notification_builtin_channels 에 둔다(서랍은 항상 켜져 있어 행이 없다).
-- · 비밀(봇 토큰·웹훅 주소)은 ROBOYAM_CRYPTO_KEY 로 암호화한 암호문(v1:…)만 저장한다. 응답·로그·감사에는 평문도 암호문도 내보내지 않는다.
-- · 이벤트 전달: 이메일은 기존 notification_events.email_enabled, 브라우저는 새 browser_enabled(서버가 하는 일은 저장뿐 — 순수 클라이언트 동작),
--   Slack·Mattermost 는 notification_event_channels, 추가 수신 팀은 notification_event_teams. 도구·팀을 지우면 이벤트 선택에서도 함께 빠진다(ON DELETE CASCADE).

-- ── 팀 ──────────────────────────────────────────────────────────────────────
CREATE TABLE teams (
    id          bigint       PRIMARY KEY,
    team_name   varchar(50)  NOT NULL,
    description varchar(500),
    enabled     boolean      NOT NULL DEFAULT true,
    sort_order  integer      NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_teams_name CHECK (length(btrim(team_name)) > 0)
);
CREATE UNIQUE INDEX ux_teams_name ON teams (lower(team_name));
CREATE INDEX ix_teams_sort ON teams (sort_order, id);

-- ── 팀원 ────────────────────────────────────────────────────────────────────
-- 운영자가 지워지면(반려한 신청 등) 팀원 행도 함께 지운다. 팀은 팀원이 있으면 못 지운다(RESTRICT).
CREATE TABLE team_members (
    team_id     bigint       NOT NULL REFERENCES teams (id) ON DELETE RESTRICT,
    operator_id bigint       NOT NULL REFERENCES operators (id) ON DELETE CASCADE,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    PRIMARY KEY (team_id, operator_id)
);
CREATE INDEX ix_team_members_operator ON team_members (operator_id);

-- ── 알림 도구(Slack · Mattermost) ──────────────────────────────────────────
-- channel_type: SLACK · MATTERMOST (카카오·SMS 는 새로 만들 수 없다).
-- channel_target: Slack 채널(#ops-alerts) / Mattermost(봇 방식) 채널 이름 또는 id. mattermost_mode: webhook | bot. server_url: Mattermost(봇 방식) 서버 주소(비밀 아님).
-- bot_token_enc · webhook_url_enc: 암호문. 마지막 연결 시험 결과는 시각·성공 여부·짧은 사유 코드만 둔다(외부 응답 본문은 저장하지 않는다).
CREATE TABLE notification_channels (
    id               bigint       PRIMARY KEY,
    channel_type     varchar(20)  NOT NULL,
    channel_name     varchar(100) NOT NULL,
    description      varchar(300),
    enabled          boolean      NOT NULL DEFAULT true,
    channel_target   varchar(300),
    mattermost_mode  varchar(10),
    server_url       varchar(300),
    bot_token_enc    text,
    webhook_url_enc  text,
    last_test_at     timestamptz,
    last_test_ok     boolean,
    last_test_code   varchar(40),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_notification_channels_type CHECK (channel_type IN ('SLACK', 'MATTERMOST')),
    CONSTRAINT ck_notification_channels_mode CHECK (mattermost_mode IS NULL OR mattermost_mode IN ('webhook', 'bot')),
    CONSTRAINT ck_notification_channels_name CHECK (length(btrim(channel_name)) > 0)
);
CREATE UNIQUE INDEX ux_notification_channels_name ON notification_channels (lower(channel_name));

-- ── 기본 제공 도구의 상태(이메일 · 브라우저) ────────────────────────────────
-- 도구의 정의는 코드가 내려주고 이 표는 운영자가 바꾸는 값(사용 여부)과 마지막 연결 시험 결과만 든다. 행이 없으면 켜져 있고 시험한 적이 없는 것이다.
CREATE TABLE notification_builtin_channels (
    channel_key    varchar(20)  PRIMARY KEY,
    enabled        boolean      NOT NULL DEFAULT true,
    last_test_at   timestamptz,
    last_test_ok   boolean,
    last_test_code varchar(40),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_notification_builtin_channels_key CHECK (channel_key IN ('email', 'browser'))
);
INSERT INTO notification_builtin_channels (channel_key) VALUES ('email'), ('browser');

-- ── 이벤트별 전달 도구 · 추가 수신 팀 ───────────────────────────────────────
ALTER TABLE notification_events ADD COLUMN browser_enabled boolean NOT NULL DEFAULT false;

CREATE TABLE notification_event_channels (
    event_code varchar(60)  NOT NULL REFERENCES notification_events (code) ON DELETE CASCADE,
    channel_id bigint       NOT NULL REFERENCES notification_channels (id) ON DELETE CASCADE,
    created_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_code, channel_id)
);
CREATE INDEX ix_notification_event_channels_channel ON notification_event_channels (channel_id);

CREATE TABLE notification_event_teams (
    event_code varchar(60)  NOT NULL REFERENCES notification_events (code) ON DELETE CASCADE,
    team_id    bigint       NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    created_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_code, team_id)
);
CREATE INDEX ix_notification_event_teams_team ON notification_event_teams (team_id);
