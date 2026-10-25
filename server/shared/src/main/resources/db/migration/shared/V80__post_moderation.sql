-- 관리자 「게시글 관리」 — docs/features/post-admin-contract.md 「DB」·「서버 구현 결과」
--
-- · posts.hidden_reason 에 STAFF(운영자가 숨김)를 더한다. 공개 목록은 게시판 옵션 staff_hidden_notice(V77)가 켜졌을 때만 STAFF 로 밝히고, 끄면 REPORTED 로 낸다.
--   posts 는 파티션 표 — 부모에서 DROP 뒤 다시 만들면 조각에도 함께 적용된다(V46 과 같은 방식).
-- · post_moderation_events: 글 조치 기록(관리자 글 상세의 「조치 기록」). 고치지 않고 쌓기만 한다.
--   type: HIDDEN · UNHIDDEN · DELETED · RESTORED · MOVED · NOTICE · PINNED · UNPINNED(운영자) · REPORT_HIDDEN(신고 자동 숨김 — 공개 서버 ReportService).
--   actor_kind: OPERATOR(actor_id = operators.id) · SYSTEM(actor_id NULL). 운영자가 지워져도 기록은 남는다(외래 키 없음 — 감사 기록과 같다).
--   reason: 운영자가 적은 사유(1~200자, SYSTEM 은 NULL). detail: 조치마다 덧붙임(이동 전후 게시판·말머리, 숨김 까닭·기간, 일괄 여부 등).
--   글 표는 파티션 표라 post_id 외래 키가 없다.
-- · 포인트 원장 사유에 POST_RESTORE(운영자가 지운 글을 복구 — 회수했던 만큼 다시 준다)를 더한다(V51 CHECK 를 다시 만든다).
-- · 회원 알림 종류에 POST_REMOVED(운영자가 내 글을 지웠다 — 제목·사유, 끌 수 없음)를 더한다.

-- ── 숨김 까닭 STAFF ─────────────────────────────────────────────────────────
ALTER TABLE posts DROP CONSTRAINT ck_posts_hidden_reason;
ALTER TABLE posts ADD CONSTRAINT ck_posts_hidden_reason CHECK (hidden_reason IS NULL OR hidden_reason IN ('REPORTED', 'ILLEGAL', 'RIGHTS', 'STAFF'));

-- ── 글 조치 기록 ────────────────────────────────────────────────────────────
CREATE TABLE post_moderation_events (
    id         bigint       PRIMARY KEY,
    post_id    bigint       NOT NULL,
    type       varchar(16)  NOT NULL,
    actor_kind varchar(10)  NOT NULL,
    actor_id   bigint,
    reason     varchar(200),
    detail     jsonb,
    created_at timestamptz  NOT NULL,
    CONSTRAINT ck_post_moderation_events_type CHECK (type IN ('HIDDEN', 'UNHIDDEN', 'DELETED', 'RESTORED', 'MOVED', 'NOTICE', 'PINNED', 'UNPINNED',
                                                             'REPORT_HIDDEN')),
    CONSTRAINT ck_post_moderation_events_actor CHECK (actor_kind IN ('OPERATOR', 'SYSTEM') AND (actor_kind = 'SYSTEM' OR actor_id IS NOT NULL))
);
CREATE INDEX ix_post_moderation_events_post ON post_moderation_events (post_id, id DESC);

-- ── 포인트 원장 사유 POST_RESTORE ───────────────────────────────────────────
-- ⚠ 합칠 때 합집합 확인 — 다른 세션(커머스 등)도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 사유를 담아야 한다).
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason CHECK (reason IN ('ATTENDANCE', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE',
    'TICKET_PURCHASE', 'ADMIN_ADJUST', 'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP', 'POST_RESTORE'));

-- ── 회원 알림 종류 POST_REMOVED ─────────────────────────────────────────────
-- ⚠ 합칠 때 합집합 확인 — V78 의 목록 + POST_REMOVED. 다른 세션(커머스 등)도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED'));
