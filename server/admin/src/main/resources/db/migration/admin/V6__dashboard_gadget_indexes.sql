-- 관리자 대시보드 가젯이 인덱스로만 세게 하는 부분 인덱스 — docs/features/admin-dashboard-contract.md 「서버 부담 원칙」(사용자 결정 2026-11-03 「서버 부하가 최소화되어야 한다」)
--
-- admin 스트림에 두는 이유: 이 인덱스를 쓰는 질의는 관리자 대시보드(DashboardGadgetStore · DashboardCommerceStore)뿐이다. 표는 shared 스트림의 것이지만
-- 표·칸을 바꾸지 않고 인덱스만 더한다(shared 가 칸을 지우면 인덱스도 함께 지워진다). 이미 있으면 건너뛴다(IF NOT EXISTS).
-- 모두 부분 인덱스라 해당 줄(탈퇴·정지 회원, 송금 완료)만 담겨 작다.

-- 회원 현황(MEMBER_STATS) 오늘 탈퇴 — status = 'WITHDRAWN' AND deleted_at 범위(탈퇴 시각 = deleted_at)
CREATE INDEX IF NOT EXISTS ix_users_withdrawn_at ON users (deleted_at) WHERE status = 'WITHDRAWN';

-- 회원 현황(MEMBER_STATS) 정지 중 — 기간 정지·영구 정지(관리자 회원 목록의 「정지」와 같은 뜻), 인덱스만 읽는 COUNT
CREATE INDEX IF NOT EXISTS ix_users_suspended ON users (id) WHERE status IN ('SUSPENDED', 'BANNED');

-- 매출 요약(SALES_SUMMARY) 계좌 송금 환불 — 송금 완료 시각(sent_at) 범위. 기존 ix_cbt_status(status, created_at)는 송금 완료 시각으로 자를 수 없다
CREATE INDEX IF NOT EXISTS ix_cbt_sent ON commerce_bank_transfers (sent_at) WHERE status = 'SENT';
