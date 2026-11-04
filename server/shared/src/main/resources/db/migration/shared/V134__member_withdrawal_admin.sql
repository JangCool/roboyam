-- 회원 탈퇴 관리(관리자 — docs/features/member-withdrawal-contract.md 「관리자」, admin-web /members/withdrawals).
--   · member_withdrawals 에 신청 경로(source: MEMBER 회원 본인 · OPERATOR 운영자 강제 — 기존 줄은 모두 MEMBER),
--     강제 탈퇴의 사유 종류·운영 메모·운영자, 운영자 대리 취소의 요청 경로·운영 메모·운영자를 더한다.
--   · 목록(상태 탭·신청일·완료일 거르기·정렬)과 요약 4칸(진행 중 · 오늘 완료 · 이번 달 완료 · 10일 넘긴 진행 중)이 인덱스로만 세게 색인을 더한다.
--     진행 중 수·10일 넘긴 진행 중은 V132 의 ix_member_withdrawals_scheduled (requested_at, id) WHERE status = 'SCHEDULED' 를 그대로 쓴다.
--   · 개인정보(이메일·닉네임)는 여전히 넣지 않는다 — 운영 메모는 운영자가 적는 처리 사유(문의 번호·통화 시각 등)다.

ALTER TABLE member_withdrawals
    ADD COLUMN source             varchar(10)  NOT NULL DEFAULT 'MEMBER',
    ADD COLUMN force_reason_kind  varchar(20),
    ADD COLUMN force_memo         varchar(500),
    ADD COLUMN force_operator_id  bigint,
    ADD COLUMN cancel_channel     varchar(12),
    ADD COLUMN cancel_memo        varchar(500),
    ADD COLUMN cancel_operator_id bigint;

ALTER TABLE member_withdrawals
    ADD CONSTRAINT ck_mw_source CHECK (source IN ('MEMBER', 'OPERATOR')),
    -- 운영자 강제 탈퇴면 사유 종류·메모·운영자가 모두 있고, 회원 신청이면 모두 없다
    ADD CONSTRAINT ck_mw_force CHECK ((source = 'OPERATOR') = (force_reason_kind IS NOT NULL AND force_memo IS NOT NULL AND force_operator_id IS NOT NULL)),
    ADD CONSTRAINT ck_mw_force_kind CHECK (force_reason_kind IS NULL OR force_reason_kind IN ('UNDER_14', 'POLICY_VIOLATION', 'ON_BEHALF', 'OTHER')),
    -- 운영자 대리 취소면 경로·메모·운영자가 모두 있고(취소된 줄만), 회원 본인 취소·취소 아님이면 모두 없다
    ADD CONSTRAINT ck_mw_cancel_operator CHECK ((cancel_operator_id IS NULL) = (cancel_channel IS NULL) AND (cancel_channel IS NULL) = (cancel_memo IS NULL)),
    ADD CONSTRAINT ck_mw_cancel_channel CHECK (cancel_channel IS NULL OR cancel_channel IN ('CS_INQUIRY', 'PHONE', 'EMAIL', 'OTHER')),
    ADD CONSTRAINT ck_mw_cancel_status CHECK (cancel_operator_id IS NULL OR status = 'CANCELED');

-- 목록 기본 정렬(신청일 내림)·신청일 기간·사유 분포(신청일 기간, 상태 무관)
CREATE INDEX ix_member_withdrawals_requested ON member_withdrawals (requested_at DESC, id DESC);
-- 상태 탭(진행 중·완료·취소) + 신청일 정렬·기간
CREATE INDEX ix_member_withdrawals_status_requested ON member_withdrawals (status, requested_at DESC, id DESC);
-- 요약 「오늘 완료」·「이번 달 완료」(완료 시각 범위 count)와 완료 탭의 완료일 기간·정렬
CREATE INDEX ix_member_withdrawals_completed ON member_withdrawals (completed_at DESC, id DESC) WHERE status = 'COMPLETED';
-- 취소 탭의 취소일 정렬
CREATE INDEX ix_member_withdrawals_canceled ON member_withdrawals (canceled_at DESC, id DESC) WHERE status = 'CANCELED';
