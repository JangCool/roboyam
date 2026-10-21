-- 감사 로그 조회(GET /api/admin/audit-logs)와 내 로그인 이력(GET /api/admin/profile/login-history)을 위한 색인 — docs/features/roles-contract.md 4단계
--
-- 목록은 「발생 시각 최근순, 같으면 id 최근순」으로 한 쪽씩 읽는다. V7 의 색인은 시각만 있어 같은 시각의 줄을 가르려면 정렬이 한 번 더 든다 →
-- (occurred_at DESC, id DESC) 로 바꾼다. 작업 거르기도 같은 모양으로 맞춘다. 앞 칸이 같은 옛 색인(ix_audit_logs_occurred · ix_audit_logs_action)은
-- 새 색인이 그대로 대신하므로 지운다(쓰기 비용만 늘리는 중복이 된다).
CREATE INDEX ix_audit_logs_occurred_id ON audit_logs (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_logs_action_occurred_id ON audit_logs (action, occurred_at DESC, id DESC);
DROP INDEX ix_audit_logs_occurred;
DROP INDEX ix_audit_logs_action;

-- 「실패·거부만 보기」 — 실패는 드물어서 부분 색인이 작고 빠르다
CREATE INDEX ix_audit_logs_not_success ON audit_logs (occurred_at DESC, id DESC) WHERE result <> 'SUCCESS';

-- 내 로그인 이력: 한 사람의 감사 기록 중 로그인 성공·실패만 최근 N건. 일을 많이 한 운영자의 다른 기록을 훑지 않게 이 두 작업만 색인한다
CREATE INDEX ix_audit_logs_login ON audit_logs (actor_id, occurred_at DESC, id DESC)
    WHERE action IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE') AND actor_id IS NOT NULL;
