-- 소모임 4단계(일정) — docs/features/group-contract.md 「전체 개발 계획」 4단계(community docs/api-groups.md 9절 · community V24__group_events.sql 을 로보얌에 맞춘 것).
-- 번호는 develop 병합(2026-10-26, 커머스 1-3 의 V89 다음) 때 V90~V93 으로 다시 매겼다.
--
-- group_events          : 소모임의 모임 일정. 멤버에게만 보이고 모임장 · 일정 관리(EVENTS)를 맡은 매니저가 만들고 고치고 취소한다. 지난 일정은 지우지 않고 보관한다
--                         (취소는 status = 'CANCELED'). closes_at 은 「끝나는 시각」 — ends_at 이 있으면 그 값, 없으면 시작 + 2시간(앱이 저장할 때 계산한다).
--                         목록의 다가오는/지난 구분을 SQL 한 조건으로 하려는 것이다. 일정별 정원은 두지 않는다(모임 정원만). id 는 Snowflake.
--                         해산·비활성이 되어도 일정은 지우지 않는다(소모임이 열려 있지 않으면 일정 API 가 404 · 403 이다 — 원본과 같다).
-- group_event_responses : (일정, 회원) 한 쌍에 한 행 — 참석(GOING) · 불참(NOT_GOING) · 미정(MAYBE). 응답을 지우면 행을 지운다.
--                         집계는 지금 멤버(group_members.status = 'ACTIVE')의 응답만 센다 — 나가거나 강퇴된 멤버의 응답은 남아 있어도 빠지고 다시 가입하면 돌아온다.
CREATE TABLE group_events (
    id            bigint        PRIMARY KEY,
    group_id      bigint        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    creator_id    bigint        NOT NULL REFERENCES users (id),
    title         varchar(60)   NOT NULL,
    description   varchar(1000),
    starts_at     timestamptz   NOT NULL,
    ends_at       timestamptz,
    closes_at     timestamptz   NOT NULL,
    mode          varchar(8)    NOT NULL CONSTRAINT ck_group_events_mode CHECK (mode IN ('ONLINE', 'OFFLINE')),
    place         varchar(100),
    status        varchar(8)    NOT NULL DEFAULT 'ACTIVE' CONSTRAINT ck_group_events_status CHECK (status IN ('ACTIVE', 'CANCELED')),
    cancel_reason varchar(100),
    canceled_at   timestamptz,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_group_events_ends CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_group_events_closes CHECK (closes_at >= starts_at),
    CONSTRAINT ck_group_events_place CHECK (mode <> 'OFFLINE' OR place IS NOT NULL)
);

-- 다가오는 일정(취소되지 않은 것을 시작 시각 순으로 — 내 소모임의 다음 일정도)과 지난 일정(모임별 최근 순)
CREATE INDEX ix_group_events_upcoming ON group_events (group_id, starts_at, id) WHERE status = 'ACTIVE';
CREATE INDEX ix_group_events_group ON group_events (group_id, starts_at DESC, id DESC);

CREATE TABLE group_event_responses (
    event_id     bigint       NOT NULL REFERENCES group_events (id) ON DELETE CASCADE,
    user_id      bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    response     varchar(9)   NOT NULL CONSTRAINT ck_group_event_responses_response CHECK (response IN ('GOING', 'NOT_GOING', 'MAYBE')),
    responded_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, user_id)
);

-- 참석자 목록(응답 최신순)과 내 응답 조회
CREATE INDEX ix_group_event_responses_going ON group_event_responses (event_id, responded_at DESC, user_id) WHERE response = 'GOING';
CREATE INDEX ix_group_event_responses_user ON group_event_responses (user_id, event_id);

-- ── 회원 알림 종류: 소모임 일정(GROUP_EVENT — 만들기 · 수정 · 취소, 덧붙임 eventAction) ──
-- 회원이 끌 수 있는 유일한 소모임 알림(잠금 없음 — 개인 설정의 「소모임 일정」). 알림 설정 행은 넣지 않는다(V90 와 같다). 종류 목록은 V92 의 목록 ∪ GROUP_EVENT.
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED',
                    'GROUP_POST_UNPUBLISHED',
                    'GROUP_MANAGER_APPOINTED', 'GROUP_MANAGER_PERMISSIONS_CHANGED', 'GROUP_MANAGER_REMOVED', 'GROUP_MANAGER_RESIGNED',
                    'GROUP_KICKED_BY_MANAGER',
                    'GROUP_EVENT'));
