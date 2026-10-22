-- 회원 알림(공개 사이트의 종 알림) — docs/features/member-notification-contract.md. 관리자 알림(admin_notifications)과 표·API·종류가 모두 따로다.
-- 한 줄 = 받는 회원 한 명에게 간 알림. 문구는 저장하지 않고 읽을 때 종류(type) + 칸으로 요청 언어로 푼다(글 제목·작성자 이름은 읽을 때의 현재 값).
-- type: COMMENT(내 글에 새 댓글) · REPLY(내 댓글에 답글) · POINTS_REVOKED(포인트 회수 — 운영 조정 차감) · POST_HIDDEN · COMMENT_HIDDEN(신고로 내 글·댓글이 자동 숨김).
-- actor_user_id: 일으킨 회원(비회원이면 NULL 이고 actor_name 에 그때 이름). 회원이 탈퇴해도 줄은 남는다(화면은 「탈퇴한 회원」) — 외래 키 없음.
-- post_id · comment_id: 가리키는 글·댓글(파티션 표라 외래 키를 두지 않는다). group_count: 묶인 댓글 수(같은 글의 새 댓글을 board_notification_settings.group_window_minutes 안에서 한 줄로 묶는다).
-- payload: 종류별 덧붙임(JSON 객체) — 숨김 알림의 당시 제목·발췌·임시조치 끝 시각, 포인트 회수의 종류·양·공개 사유. 이메일·IP·비밀번호 같은 개인정보는 싣지 않는다.
-- 받는 회원이 지워지면(users 행 삭제) 같이 지운다. 탈퇴(status = WITHDRAWN)한 회원의 줄은 정리 배치(R__batch_20, member-notification-cleanup)가 지운다.
CREATE TABLE member_notifications (
    id             bigint       PRIMARY KEY,
    user_id        bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type           varchar(30)  NOT NULL,
    actor_user_id  bigint,
    actor_name     varchar(40),
    post_id        bigint,
    comment_id     bigint,
    group_count    integer      NOT NULL DEFAULT 1,
    payload        jsonb,
    read_at        timestamptz,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT ck_member_notifications_type CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN')),
    CONSTRAINT ck_member_notifications_group CHECK (group_count >= 1),
    CONSTRAINT ck_member_notifications_payload CHECK (payload IS NULL OR jsonb_typeof(payload) = 'object')
);
-- 내 알림 목록(최신이 위, 커서 = id) · 안 읽은 수 · 묶기 찾기(같은 글의 안 읽은 COMMENT)
CREATE INDEX ix_member_notifications_user ON member_notifications (user_id, id DESC);
CREATE INDEX ix_member_notifications_unread ON member_notifications (user_id, id DESC) WHERE read_at IS NULL;
-- 정리 배치: 읽은 지 오래된 것 · 만든 지 오래된 것
CREATE INDEX ix_member_notifications_read_at ON member_notifications (read_at) WHERE read_at IS NOT NULL;
CREATE INDEX ix_member_notifications_created ON member_notifications (created_at);
