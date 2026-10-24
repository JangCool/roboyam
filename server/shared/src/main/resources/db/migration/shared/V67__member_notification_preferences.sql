-- 회원 개인 알림 설정 — docs/features/member-mypage-contract.md 「알림 설정」·「결정」(이메일로도 받기 = 회원 이벤트 메일 수신 여부)
--
-- 회원이 이벤트(MemberEvent 코드)마다 고른 앱 알림·이메일 받기. 줄이 없으면 둘 다 켬이다.
-- 발행기(MemberNotificationPublisher)는 운영자 설정(member_event_settings)의 enabled·in_app·email 과 이 값을 함께 본다 —
-- 앱 알림 = enabled && in_app && 회원 in_app, 메일 = enabled && email && 회원 email.
-- 회원이 끌 수 없는 이벤트(코드 잠금 MemberEventLockReason 또는 운영자 member_can_turn_off = false)는 이 값을 무시하고 보낸다.
-- (member-notification-contract.md 미확정 1 의 후보 member_notification_mutes 대신 이 이름·모양으로 정했다 — 채널 두 칸을 한 줄에 둔다.)
CREATE TABLE member_notification_preferences (
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_code varchar(40) NOT NULL,
    in_app     boolean     NOT NULL,
    email      boolean     NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, event_code),
    CONSTRAINT ck_member_notification_preferences_code CHECK (event_code ~ '^[A-Z][A-Z0-9_]*$')
);
