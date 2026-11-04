-- 게시판 스텝 처리(docs/features/board-staff-action-contract.md — 정책 1~9 · DB 절). 매니저·스텝이 맡은 게시판에서 하는 글 숨기기 · 글 삭제 · 댓글 삭제와 그 되돌리기.
-- V137 의 처리 기록 표(board_staff_actions — 지금은 kind SANCTION 만)와 하루 사용량(board_staff_usage.posts · comments 자리)을 넓힌다. 이미 적용된 V137 은 고치지 않는다.
-- · board_staff_actions: kind + POST · COMMENT, action + HIDE · DELETE(글 = HIDE · DELETE, 댓글 = DELETE, 제재 = ISSUE · LIFT — 짝 CHECK).
--   사유 코드 reason_code(SPAM · ABUSE · INVESTMENT · OFF_TOPIC — 정책 3, 자유 입력 없음, OTHER 는 제재에만) — 글·댓글 처리에는 필수, 제재 줄은 비운다(제재 사유는 board_sanctions).
--   스냅숏(처리 때): target_title(글 제목) · target_excerpt(댓글 앞 40자) · target_author_name(회원 닉네임 또는 비회원 이름) — 글이 지워지거나 파기돼도 내역이 읽힌다.
--   되돌림(정책 6·7): restored_at · restored_by_kind MANAGER(그 게시판 매니저 — restored_by_id = 회원) · OPERATOR(운영자 — restored_by_id = 운영자) · restore_reason(1~200자).
--   한 번만 되돌린다 — 앱이 조건부 UPDATE(… WHERE restored_at IS NULL)로 막는다. 운영자 게시글 관리 · 신고 처리로 풀어도 열린 처리 기록을 채운다(OPERATOR).
--   restored_by_id 는 회원·운영자 둘 중 하나라 FK 가 없다(처리 기록은 쌓기만 하고 지우지 않는다).
-- · 「되돌리지 않은 처리」 부분 색인(글별) — 운영자가 글을 풀 때 그 글의 열린 처리 기록을 찾는다. 내역 목록(게시판 전체 · 처리자 본인 · 관리자 전체)은 제재 줄을 뺀 부분 색인.
-- · post_moderation_events.actor_kind + BOARD_STAFF(actor_id = 회원 ID — 정책 8, 관리자 게시글 관리 「조치 기록」에 「스텝 닉네임(매니저/스텝)」으로).
-- 한도 칸은 V137 의 board_staff_usage.posts(글 숨기기 + 삭제 합산) · comments(댓글 삭제) — 원자적 증가는 앱(INSERT … ON CONFLICT … DO UPDATE … WHERE 칸 < 한도).
-- 감사 행위 BOARD_STAFF_ACTION_RESTORE(운영자 되돌리기)는 DB CHECK 가 없다(audit_logs.action 은 자유 글자). 회원 알림 종류는 운영자 조치와 같은 POST_HIDDEN · POST_REMOVED ·
-- COMMENT_REMOVED 라 member_notifications CHECK 는 그대로다.

-- ── 처리 기록: 글·댓글 처리 ─────────────────────────────────────────────────
ALTER TABLE board_staff_actions DROP CONSTRAINT ck_board_staff_actions_kind;
ALTER TABLE board_staff_actions DROP CONSTRAINT ck_board_staff_actions_action;

ALTER TABLE board_staff_actions ADD COLUMN reason_code        varchar(12);
ALTER TABLE board_staff_actions ADD COLUMN target_title       varchar(200);
ALTER TABLE board_staff_actions ADD COLUMN target_excerpt     varchar(40);
ALTER TABLE board_staff_actions ADD COLUMN target_author_name varchar(40);
ALTER TABLE board_staff_actions ADD COLUMN restored_at        timestamptz;
ALTER TABLE board_staff_actions ADD COLUMN restored_by_kind   varchar(8);
ALTER TABLE board_staff_actions ADD COLUMN restored_by_id     bigint;
ALTER TABLE board_staff_actions ADD COLUMN restore_reason     varchar(200);

ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_kind CHECK (kind IN ('SANCTION', 'POST', 'COMMENT'));
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_action CHECK (action IN ('ISSUE', 'LIFT', 'HIDE', 'DELETE'));
-- 종류와 행위의 짝 — 댓글 숨기기는 없다(정책 1)
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_pair CHECK ((kind = 'SANCTION' AND action IN ('ISSUE', 'LIFT'))
    OR (kind = 'POST' AND action IN ('HIDE', 'DELETE')) OR (kind = 'COMMENT' AND action = 'DELETE'));
-- 글·댓글 처리는 대상 글 · 사유 코드가 있다(댓글이면 댓글도). 제재 줄은 사유 코드를 비운다
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_target CHECK (kind = 'SANCTION'
    OR (post_id IS NOT NULL AND reason_code IS NOT NULL AND (kind = 'POST') = (comment_id IS NULL)));
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_reason_code CHECK (reason_code IS NULL
    OR (kind <> 'SANCTION' AND reason_code IN ('SPAM', 'ABUSE', 'INVESTMENT', 'OFF_TOPIC')));
-- 되돌림은 글·댓글 처리에만, 칸이 함께 찬다
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_restored CHECK ((restored_at IS NULL) = (restored_by_kind IS NULL)
    AND (restored_at IS NULL OR (kind <> 'SANCTION' AND restored_by_kind IN ('MANAGER', 'OPERATOR') AND restored_by_id IS NOT NULL AND restore_reason IS NOT NULL)));
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_restore_reason CHECK (restore_reason IS NULL OR char_length(btrim(restore_reason)) BETWEEN 1 AND 200);

-- 되돌리지 않은 처리(글별) — 운영자 숨김 해제 · 복구가 그 글의 열린 처리 기록을 채운다(정책 7)
CREATE INDEX ix_board_staff_actions_open_post ON board_staff_actions (post_id, action) WHERE restored_at IS NULL AND kind = 'POST';
-- 내역: 매니저 = 그 게시판 전체 · 스텝 = 본인 것(정책 8) · 관리자 「스텝 처리 감독」 전체 — 제재 줄(kind SANCTION)은 넣지 않는다
CREATE INDEX ix_board_staff_actions_board_content ON board_staff_actions (board_id, created_at DESC, id DESC) WHERE kind <> 'SANCTION';
CREATE INDEX ix_board_staff_actions_actor_content ON board_staff_actions (board_id, actor_id, created_at DESC, id DESC) WHERE kind <> 'SANCTION';
CREATE INDEX ix_board_staff_actions_content ON board_staff_actions (created_at DESC, id DESC) WHERE kind <> 'SANCTION';

-- ── 글 조치 기록: 행위자 BOARD_STAFF(회원 ID) ────────────────────────────────
-- 'BOARD_STAFF' 는 11자라 V80 의 varchar(10) 를 넓힌다(길이만 늘리는 변경 — 표를 다시 쓰지 않는다)
ALTER TABLE post_moderation_events ALTER COLUMN actor_kind TYPE varchar(12);
ALTER TABLE post_moderation_events DROP CONSTRAINT ck_post_moderation_events_actor;
ALTER TABLE post_moderation_events ADD CONSTRAINT ck_post_moderation_events_actor
    CHECK (actor_kind IN ('OPERATOR', 'SYSTEM', 'BOARD_STAFF') AND (actor_kind = 'SYSTEM' OR actor_id IS NOT NULL));
