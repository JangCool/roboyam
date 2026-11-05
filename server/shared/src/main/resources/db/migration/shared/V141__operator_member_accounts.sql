-- 운영자 회원 계정(docs/features/operator-member-account-contract.md — 정책 1~15 · DB 절, docs/domain/operator-permissions.md 확정 규칙 1·2 개정 2026-11-05).
-- 관리자 앱 운영자(operators) 하나에 사이트 회원 계정(users) 하나를 1:1 로 잇는다. 회원 표에는 값을 두지 않고 운영자 쪽 표가 회원을 가리킨다(정책 1).
-- ⚠ develop 의 V140 은 사업자 서류 접수(biz_doc_intakes) — 이 파일은 V141. 로보티콘 브랜치도 V140~V141 을 가번호로 쓴다 — 합칠 때 뒤에 합치는 쪽이 번호를 다시 매긴다.
--
-- · operator_member_accounts: 운영자 PK(운영자 하나에 하나) + 회원 유일(회원 하나에 하나). 운영자·회원이 지워지면 연결도 지워진다(반려된 신청 운영자 행 삭제 등).
--   linked_by = 이은 운영자(그 운영자가 지워지면 비운다). 힘(정책 6)은 요청마다 운영자 상태 + 회원 상태로 판정한다 — 표에 「켜짐」 칸을 두지 않는다.
-- · board_staff_actions: 처리자 자리 actor_role ∪ OPERATOR(actor_id = 그 회원, actor_operator_id = 운영자 — OPERATOR 줄이면 반드시, 아니면 비움).
--   되돌림 restored_by_kind 는 그대로(MANAGER · OPERATOR) — 운영자 회원 계정이 되돌리면 OPERATOR + restored_by_id = 운영자.
-- · board_sanctions: 준 사람 자리 issued_role ∪ OPERATOR(issued_by = 그 회원, issued_operator_id = 운영자 — OPERATOR 줄이면 반드시, 아니면 비움).
--   운영자 회원 계정이 쓰기 제한을 풀면 lifted_by_type OPERATOR + lifted_by_operator = 운영자(V137 칸 그대로).
--   ⚠ 두 운영자 칸의 FK 는 ON DELETE 동작이 없다(NO ACTION) — 「OPERATOR 줄이면 필수」 CHECK 와 SET NULL 이 부딪치므로. 운영자 행은 승인 대기 신청의 반려 때만 지워지고
--   그 운영자는 사이트에서 처리할 수 없었으므로(힘은 승인된 운영자에게만) 실제로 막히는 일은 없다.
-- · post_moderation_events.actor_kind 는 이미 OPERATOR 가 있다(actor_id = 운영자, 덧붙임 viaUserId — 앱). audit_logs.action 은 자유 글자(새 값만 쓴다).

-- ── 연결 ─────────────────────────────────────────────────────────────────────
CREATE TABLE operator_member_accounts (
    operator_id bigint      PRIMARY KEY REFERENCES operators (id) ON DELETE CASCADE,
    user_id     bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    linked_at   timestamptz NOT NULL,
    linked_by   bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ux_operator_member_accounts_user UNIQUE (user_id)
);

-- ── 처리 기록: 처리자 자리 OPERATOR ─────────────────────────────────────────────
ALTER TABLE board_staff_actions DROP CONSTRAINT ck_board_staff_actions_role;
ALTER TABLE board_staff_actions ADD COLUMN actor_operator_id bigint REFERENCES operators (id);
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_role CHECK (actor_role IN ('MANAGER', 'STAFF', 'OPERATOR'));
ALTER TABLE board_staff_actions ADD CONSTRAINT ck_board_staff_actions_operator CHECK ((actor_role = 'OPERATOR') = (actor_operator_id IS NOT NULL));
CREATE INDEX ix_board_staff_actions_operator ON board_staff_actions (actor_operator_id) WHERE actor_operator_id IS NOT NULL;

-- ── 게시판 제재: 준 사람 자리 OPERATOR ──────────────────────────────────────────
ALTER TABLE board_sanctions DROP CONSTRAINT ck_board_sanctions_role;
ALTER TABLE board_sanctions ADD COLUMN issued_operator_id bigint REFERENCES operators (id);
ALTER TABLE board_sanctions ADD CONSTRAINT ck_board_sanctions_role CHECK (issued_role IN ('MANAGER', 'STAFF', 'OPERATOR'));
ALTER TABLE board_sanctions ADD CONSTRAINT ck_board_sanctions_operator CHECK ((issued_role = 'OPERATOR') = (issued_operator_id IS NOT NULL));
CREATE INDEX ix_board_sanctions_operator ON board_sanctions (issued_operator_id) WHERE issued_operator_id IS NOT NULL;
