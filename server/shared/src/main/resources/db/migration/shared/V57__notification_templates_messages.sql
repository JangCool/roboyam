-- 알림 템플릿(도구별 겉모양)·문구(대상 × 채널 × 언어별 제목과 본문)·대상별 채널 설정 — docs/features/message-template-contract.md 「개정 4」 · 「서버 구현 결과(엔진·관리 API)」
--
-- 기본(builtin) 템플릿·문구는 <b>코드가 원천</b>이라 이 표들에 행이 없다(코드 카탈로그 MessageTargetCatalog · BuiltinMessages · BuiltinTemplates 가 조회 때 합성한다 —
-- 내용은 메시지 번들 문구에서 만든다). 표에는 운영자가 만든 사용자 템플릿·문구와 대상별 선택·규칙만 든다.
--   * 기본 문구 id: builtin-<대상 코드>-<채널>-<언어>(예: builtin-BATCH_RUN_FAILED-EMAIL-ko), 기본 템플릿 id: mail-member · mail-operator · slack-default · mattermost-default.
--   * 사용자 행의 id 는 Snowflake(앱 발급). 문구가 템플릿을, 선택·규칙이 문구를 가리킬 때는 글자 id(기본 것 또는 Snowflake 의 10진 글자)로 가리킨다 — 기본 것이 표에 없어 FK 를 걸지 않는다.
--     쓰는 중인 것을 지우지 못하게 하는 검사(409 TEMPLATE_IN_USE · MESSAGE_IN_USE)는 서버가 한다.
--   * 운영자가 지워져도 행·이력은 남는다(created_by · updated_by · operator_id 에 FK 없음 — 감사 기록과 같다).

-- 메일 템플릿(옛 「메일 레이아웃」) — 이메일 문구가 하나를 고른다. 받는 사람(쓰는 대상) 회원 · 운영자 · 모두
CREATE TABLE notification_mail_templates (
    id             bigint       PRIMARY KEY,
    name           varchar(50)  NOT NULL,
    audience       varchar(10)  NOT NULL,
    show_logo      boolean      NOT NULL,
    show_site_name boolean      NOT NULL,
    header_color   varchar(10),                         -- 팔레트(BLUE·INDIGO·TEAL·GREEN·AMBER·ORANGE·RED·SLATE), null 이면 띠 없음
    footer_ko      text         NOT NULL DEFAULT '',    -- 평문(줄바꿈 가능) + 공통 변수, 500자
    footer_en      text         NOT NULL DEFAULT '',
    frame_ko       text         NOT NULL,               -- 간단 서식 HTML(서버가 정화), {{content}} 정확히 한 번
    frame_en       text         NOT NULL,
    created_by     bigint,
    created_at     timestamptz  NOT NULL,
    updated_by     bigint,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT ck_notification_mail_templates_audience CHECK (audience IN ('MEMBER', 'OPERATOR', 'ANY'))
);

-- Slack · Mattermost 템플릿 — 메시지 카드(첨부)의 겉모양. 종류(tool)는 알림 도구 종류와 같다
CREATE TABLE notification_chat_templates (
    id                 bigint      PRIMARY KEY,
    tool               varchar(12) NOT NULL,
    name               varchar(50) NOT NULL,
    show_severity_icon boolean     NOT NULL,
    bold_title         boolean     NOT NULL,
    color_info         varchar(10) NOT NULL,            -- 팔레트(GREEN·BLUE·TEAL·PURPLE·AMBER·ORANGE·RED·GRAY)
    color_warning      varchar(10) NOT NULL,
    color_critical     varchar(10) NOT NULL,
    show_site_name     boolean     NOT NULL,
    show_sent_at       boolean     NOT NULL,
    show_open_button   boolean     NOT NULL,
    open_label_ko      varchar(20) NOT NULL DEFAULT '',
    open_label_en      varchar(20) NOT NULL DEFAULT '',
    frame_ko           text        NOT NULL,            -- 그 앱 표기 + {{content}} 정확히 한 번, 500자
    frame_en           text        NOT NULL,
    created_by         bigint,
    created_at         timestamptz NOT NULL,
    updated_by         bigint,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT ck_notification_chat_templates_tool CHECK (tool IN ('SLACK', 'MATTERMOST'))
);

CREATE INDEX ix_notification_chat_templates_tool ON notification_chat_templates (tool, id);

-- 카카오톡(알림톡) 템플릿 — 준비 중(알림 도구에서 카카오가 업체 계약 전). 표만 두고 행은 없다(관리 API 는 읽기만 — 등록·검수 흐름은 계약 「미확정」 12)
CREATE TABLE notification_kakao_templates (
    id            bigint      PRIMARY KEY,
    name          varchar(50) NOT NULL,
    template_code varchar(30) NOT NULL,                 -- 카카오에 등록한 알림톡 템플릿 코드
    status        varchar(10) NOT NULL,
    emphasis      varchar(50) NOT NULL DEFAULT '',
    approved_body text        NOT NULL,                 -- 카카오에 승인된 본문(#{이름} 자리 포함)
    mappings      jsonb       NOT NULL DEFAULT '[]',    -- [{placeholder, variable, sample}]
    buttons       jsonb       NOT NULL DEFAULT '[]',    -- [{name, linkVariable}]
    channel_name  varchar(50) NOT NULL DEFAULT '',
    created_by    bigint,
    created_at    timestamptz NOT NULL,
    updated_by    bigint,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT uq_notification_kakao_templates_code UNIQUE (template_code),
    CONSTRAINT ck_notification_kakao_templates_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_notification_kakao_templates_mappings CHECK (jsonb_typeof(mappings) = 'array'),
    CONSTRAINT ck_notification_kakao_templates_buttons CHECK (jsonb_typeof(buttons) = 'array')
);

-- 사용자 문구 — 대상(이벤트 · 필수 안내 메일) × 채널 × 언어. 앱 알림이 아니면 그 채널의 템플릿 하나를 가리킨다
--   * IN_APP: subject 제목 한 줄(100) · body 평문(300) · template_id null
--   * EMAIL: subject 제목 한 줄(200) · body 간단 서식 HTML(서버가 정화, 버튼 블록 하나까지, 20000) · template_id 메일 템플릿
--   * SLACK · MATTERMOST: subject 제목 한 줄(200) · body 그 앱 표기(1500, 비어도 됨) · template_id 그 종류의 템플릿
--   * KAKAO: subject · body 빈 글자(승인된 알림톡 본문을 그대로 보낸다) · template_id 알림톡 템플릿 — 준비 중이라 지금은 만들 수 없다
CREATE TABLE notification_messages (
    id          bigint      PRIMARY KEY,
    target_type varchar(16) NOT NULL,
    target_code varchar(64) NOT NULL,
    channel     varchar(12) NOT NULL,
    locale      varchar(2)  NOT NULL,
    name        varchar(50) NOT NULL,
    template_id varchar(40),
    subject     varchar(200) NOT NULL DEFAULT '',
    body        text        NOT NULL DEFAULT '',
    created_by  bigint,
    created_at  timestamptz NOT NULL,
    updated_by  bigint,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT ck_notification_messages_target_type CHECK (target_type IN ('EVENT', 'REQUIRED_MAIL')),
    CONSTRAINT ck_notification_messages_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'SLACK', 'MATTERMOST', 'KAKAO')),
    CONSTRAINT ck_notification_messages_locale CHECK (locale IN ('ko', 'en')),
    CONSTRAINT ck_notification_messages_template CHECK ((channel = 'IN_APP') = (template_id IS NULL))
);

CREATE INDEX ix_notification_messages_target ON notification_messages (target_type, target_code, channel, id);
CREATE INDEX ix_notification_messages_template ON notification_messages (channel, template_id);

-- 대상 × 채널의 「언어별 쓸 문구」와 특수 상황 규칙. 행이 없으면 언어별 기본 문구를 쓰고 규칙이 없다
--   * selected_ko · selected_en: 문구 id(기본 것 또는 사용자 문구의 Snowflake 글자). 빈 글자면 그 언어의 기본 문구(기본 문구가 없는 채널 — 카카오톡 — 은 쓸 문구 없음)
--   * rules: [{id, key, value, messageId}] — 위에서부터 차례로 보고 맞는 첫 규칙의 문구(언어와 상관없이 고른다). 10개까지
CREATE TABLE notification_message_selections (
    target_type varchar(16) NOT NULL,
    target_code varchar(64) NOT NULL,
    channel     varchar(12) NOT NULL,
    selected_ko varchar(40) NOT NULL DEFAULT '',
    selected_en varchar(40) NOT NULL DEFAULT '',
    rules       jsonb       NOT NULL DEFAULT '[]',
    updated_by  bigint,
    updated_at  timestamptz NOT NULL,
    PRIMARY KEY (target_type, target_code, channel),
    CONSTRAINT ck_notification_message_selections_target_type CHECK (target_type IN ('EVENT', 'REQUIRED_MAIL')),
    CONSTRAINT ck_notification_message_selections_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'SLACK', 'MATTERMOST', 'KAKAO')),
    CONSTRAINT ck_notification_message_selections_rules CHECK (jsonb_typeof(rules) = 'array')
);

-- 변경 이력 — 설정 이력(mail_setting_history · V54)과 같은 모양. 값이 실제로 바뀐 저장마다 한 줄이고 고치거나 지우지 않는다(대상 행이 지워져도 남는다).
--   changes = [{field, before, after}] — 바뀐 칸만. 본문·틀은 글자 요약(80자), 템플릿은 그때의 이름, 색·쓰는 대상은 코드(조회 때 요청 언어로 푼다), 켜고 끄기는 "true"/"false"
CREATE TABLE notification_message_history (
    id          bigint      PRIMARY KEY,
    message_id  bigint      NOT NULL,
    action      varchar(10) NOT NULL,
    source      varchar(10) NOT NULL DEFAULT 'ADMIN',
    operator_id bigint,
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_notification_message_history_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_notification_message_history_source CHECK (source IN ('ADMIN')),
    CONSTRAINT ck_notification_message_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);

CREATE INDEX ix_notification_message_history_message ON notification_message_history (message_id, changed_at DESC, id DESC);

CREATE TABLE notification_mail_template_history (
    id          bigint      PRIMARY KEY,
    template_id bigint      NOT NULL,
    action      varchar(10) NOT NULL,
    source      varchar(10) NOT NULL DEFAULT 'ADMIN',
    operator_id bigint,
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_notification_mail_template_history_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_notification_mail_template_history_source CHECK (source IN ('ADMIN')),
    CONSTRAINT ck_notification_mail_template_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);

CREATE INDEX ix_notification_mail_template_history_template ON notification_mail_template_history (template_id, changed_at DESC, id DESC);

CREATE TABLE notification_chat_template_history (
    id          bigint      PRIMARY KEY,
    template_id bigint      NOT NULL,
    action      varchar(10) NOT NULL,
    source      varchar(10) NOT NULL DEFAULT 'ADMIN',
    operator_id bigint,
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_notification_chat_template_history_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_notification_chat_template_history_source CHECK (source IN ('ADMIN')),
    CONSTRAINT ck_notification_chat_template_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);

CREATE INDEX ix_notification_chat_template_history_template ON notification_chat_template_history (template_id, changed_at DESC, id DESC);
