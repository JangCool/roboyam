-- 로보티콘 — 정산 정보 없이 탈퇴한 작가의 보류 정산을 「수동 지급 기록」으로 끝낸다(사용자 승인 2026-11-05). docs/features/emoticon-contract.md 「탈퇴 작가 미확정 정리(2026-11-05)」 미확정 1.
--
-- 무엇이 바뀌나
--   운영자는 계좌를 시스템에 넣지 않는다(개인정보를 새로 저장하지 않는다) — 은행에서 직접 보낸 뒤 그 사실만 기록한다(POST /api/admin/emoticons/settlements/{id}/manual-paid).
--   그 달과 그 앞의 굳히지 않은(예정·보류) 줄을 탈퇴 작가 규칙(최소 지급액 없음 · 개인 원천징수)으로 한 번에 굳힌다 —
--   지급액이 있는 줄은 곧바로 지급 완료(PAID — 확정 시각 = 지급 기록 시각), 지급액이 0 인 줄(0 이하 — 음수 이월)은 확정(CONFIRMED).
--   ① 정산 줄 지키기 트리거: 굳히지 않은 예정·보류 → PAID, 굳히지 않은 보류 → CONFIRMED 길을 연다(굳히지 않은 줄 → PAID 는 확정 시각 = 지급 시각일 때만 — 한 번에 굳히고 기록한다).
--      금액 칸 CHECK(V148)는 그대로 — 굳힌 값이 계산 관계 · 확정 가능 시각 ≤ 확정 시각을 지켜야 한다(서비스는 셀프 환불 기한 전이면 409).
--   ② 처리 기록에 MANUAL_PAID(사유 5~200자 필수 · 금액 = 그 줄의 지급액, 0 이상).

-- ── ① 정산 줄 지키기 ─────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION emoticon_settlements_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'emoticon_settlements rows cannot be deleted';
    END IF;
    IF (NEW.creator_id, NEW.sales_month, NEW.created_at) IS DISTINCT FROM (OLD.creator_id, OLD.sales_month, OLD.created_at) THEN
        RAISE EXCEPTION 'emoticon settlement key cannot change';
    END IF;
    IF OLD.confirmed_at IS NOT NULL AND (
            (NEW.sales_count, NEW.sales_amount, NEW.refund_count, NEW.refund_amount, NEW.vat_amount, NEW.base_amount, NEW.fee_rate_percent, NEW.fee_amount,
             NEW.share_amount, NEW.carried_in, NEW.withholding_amount, NEW.payout_amount, NEW.carried_out, NEW.payee_type, NEW.pay_date, NEW.closes_at, NEW.confirmed_at)
            IS DISTINCT FROM
            (OLD.sales_count, OLD.sales_amount, OLD.refund_count, OLD.refund_amount, OLD.vat_amount, OLD.base_amount, OLD.fee_rate_percent, OLD.fee_amount,
             OLD.share_amount, OLD.carried_in, OLD.withholding_amount, OLD.payout_amount, OLD.carried_out, OLD.payee_type, OLD.pay_date, OLD.closes_at, OLD.confirmed_at)
            OR (NEW.confirmed_by IS NOT NULL AND NEW.confirmed_by IS DISTINCT FROM OLD.confirmed_by)) THEN
        RAISE EXCEPTION 'confirmed emoticon settlement amounts cannot change';
    END IF;
    IF OLD.status = 'WAIVED' AND (NEW.status, NEW.hold_reason, NEW.paid_at) IS DISTINCT FROM (OLD.status, OLD.hold_reason, OLD.paid_at) THEN
        RAISE EXCEPTION 'waived emoticon settlement cannot change';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
            (OLD.status = 'ESTIMATED' AND NEW.status IN ('CONFIRMED', 'HELD', 'WAIVED'))
            OR (OLD.status = 'CONFIRMED' AND NEW.status IN ('HELD', 'PAID'))
            OR (OLD.status = 'HELD' AND OLD.confirmed_at IS NULL AND NEW.status IN ('ESTIMATED', 'WAIVED'))
            OR (OLD.status = 'HELD' AND OLD.confirmed_at IS NOT NULL AND NEW.status = 'CONFIRMED')
            -- 수동 지급 기록(V149): 굳히지 않은 보류 → 확정(지급액 0 인 줄) · 굳히지 않은 예정·보류 → 지급 완료(한 번에 굳히고 기록)
            OR (OLD.status = 'HELD' AND OLD.confirmed_at IS NULL AND NEW.status = 'CONFIRMED')
            OR (OLD.status IN ('ESTIMATED', 'HELD') AND OLD.confirmed_at IS NULL AND NEW.status = 'PAID' AND NEW.paid_at = NEW.confirmed_at)) THEN
        RAISE EXCEPTION 'emoticon settlement status % -> % is not allowed', OLD.status, NEW.status;
    END IF;
    IF OLD.paid_at IS NOT NULL AND ((NEW.paid_at, NEW.transfer_date, NEW.paid_memo) IS DISTINCT FROM (OLD.paid_at, OLD.transfer_date, OLD.paid_memo)
            OR (NEW.paid_by IS NOT NULL AND NEW.paid_by IS DISTINCT FROM OLD.paid_by)) THEN
        RAISE EXCEPTION 'emoticon settlement payout record cannot change';
    END IF;
    RETURN NEW;
END
$$;

-- ── ② 처리 기록 ─────────────────────────────────────────────────────────────
ALTER TABLE emoticon_settlement_events DROP CONSTRAINT ck_emoticon_settlement_events_action;
ALTER TABLE emoticon_settlement_events ADD CONSTRAINT ck_emoticon_settlement_events_action
    CHECK (action IN ('CONFIRMED', 'HELD', 'UNHELD', 'PAID', 'ACCOUNT_VIEWED', 'WAIVED', 'MANUAL_PAID'));
ALTER TABLE emoticon_settlement_events DROP CONSTRAINT ck_emoticon_settlement_events_note;
ALTER TABLE emoticon_settlement_events ADD CONSTRAINT ck_emoticon_settlement_events_note
    CHECK ((action NOT IN ('HELD', 'ACCOUNT_VIEWED', 'WAIVED', 'MANUAL_PAID') OR char_length(btrim(note)) BETWEEN 5 AND 200) AND (note IS NULL OR char_length(note) <= 200)
           -- 수동 지급 기록은 사유가 꼭 있다(NULL 이면 위 길이 검사가 NULL 로 지나가므로 따로 막는다)
           AND (action <> 'MANUAL_PAID' OR note IS NOT NULL));
-- 금액은 포기·수동 지급 기록에만(0 이상). 동의 시각은 포기 기록에만
ALTER TABLE emoticon_settlement_events DROP CONSTRAINT ck_emoticon_settlement_events_waived;
ALTER TABLE emoticon_settlement_events ADD CONSTRAINT ck_emoticon_settlement_events_waived
    CHECK ((action IN ('WAIVED', 'MANUAL_PAID')) = (amount IS NOT NULL) AND (amount IS NULL OR amount >= 0) AND (consented_at IS NULL OR action = 'WAIVED'));
