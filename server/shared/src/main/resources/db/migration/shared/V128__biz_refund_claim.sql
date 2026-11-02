-- 관리자 「추가 환불(클레임)」(docs/features/biz-pause-contract.md 「추가 환불(클레임)」 — 사용자 결정 2026-11-02).
-- 환불 승인으로 닫힌 뒤(또는 끝난 뒤)에도 운영자가 남은 금액(중도 해지 수수료·쓴 날 몫 포함)을 더 돌려준다 — 환불 종류 CLAIM, 바로 승인(심사 대기 없음).
--
-- biz_refunds.type ∪ CLAIM(V127 목록 FULL_AUTO·PRORATED·SUSPEND_PRORATED·DOWNTIME·STOP_PRORATED 그대로). CLAIM 줄은 제안 내역 칸(prorated_amount·fee_rate·fee_amount·basis_at)이
-- 비어 있다(ck_br_stop_detail 의 「그 밖 종류」 갈래 그대로). 처리 기록 action(CLAIM_REFUNDED)·회원 알림 kind·감사 작업 이름(BIZ_APP_REFUND_CLAIM)은 DB CHECK 가 없어 고치지 않는다.
ALTER TABLE biz_refunds DROP CONSTRAINT ck_br_type;
ALTER TABLE biz_refunds ADD CONSTRAINT ck_br_type CHECK (type IN ('FULL_AUTO', 'PRORATED', 'SUSPEND_PRORATED', 'DOWNTIME', 'STOP_PRORATED', 'CLAIM'));
