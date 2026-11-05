-- 로보티콘 — 정산 정보 없이 수익이 남은 작가의 탈퇴(사용자 지시 2026-11-05 「미확정까지 적용해」). docs/features/emoticon-contract.md 「탈퇴 작가 미확정 정리(2026-11-05)」.
--
-- 무엇이 바뀌나
--   ① 정산 줄 상태에 WAIVED(포기 — 작가가 남은 수익을 받지 않기로 했다 · 운영자가 보류를 사유와 함께 포기로 끝냈다)를 더한다.
--      포기는 굳힌 줄이다(confirmed_at · 굳힌 칸이 모두 있다). 지급액·원천징수는 0, 넘긴 금액 = 작가 몫 + 넘어온 금액(기존 금액 CHECK 그대로) — 그 넘긴 금액이 양수면
--      「포기한 금액」이고 다음 달로 이어지지 않는다(EmoticonSettlementStore.SettlementRow#carryForward — 음수(받을 돈)는 그대로 이어진다).
--      예정·보류(굳히지 않은) 줄만 포기가 된다(이미 굳힌 줄의 금액은 바꾸지 않는다). 포기한 줄은 더 바뀌지 않는다(트리거).
--      확정 가능 시각(셀프 환불 기한)이 지나기 전에도 포기한다(탈퇴 완료 순간) — 그 뒤 환불은 다음에 열린 달에서 빼고 음수 이월 = 끝남(기존 규칙)이라 CHECK 의 「확정 가능 시각 ≤ 확정 시각」은
--      포기한 줄에는 보지 않는다.
--   ② 처리 기록에 WAIVED + 금액(amount — 그 줄에서 포기한 금액, 0 이상) · 회원 동의 시각(consented_at — 회원이 동의했을 때만, 운영자 포기 처리는 null). 사유(note 5~200자)가 있다.
--   ③ 탈퇴 신청 줄에 로보티콘 수익 포기 동의 금액(forfeit_emoticon_earnings — 동의 시각은 기존 forfeit_consented_at).
--   ④ 파기 배치(R__batch_41)는 포기한 줄의 넘긴 금액을 0 으로 본다(끝난 것) — 그 파일에서 고친다.

-- ── ① 정산 줄 ───────────────────────────────────────────────────────────────
ALTER TABLE emoticon_settlements DROP CONSTRAINT ck_emoticon_settlements_status;
ALTER TABLE emoticon_settlements ADD CONSTRAINT ck_emoticon_settlements_status CHECK (status IN ('ESTIMATED', 'CONFIRMED', 'PAID', 'HELD', 'WAIVED'));

ALTER TABLE emoticon_settlements DROP CONSTRAINT ck_emoticon_settlements_frozen;
ALTER TABLE emoticon_settlements ADD CONSTRAINT ck_emoticon_settlements_frozen CHECK (
    (confirmed_at IS NULL) = (sales_count IS NULL) AND (confirmed_at IS NULL) = (sales_amount IS NULL)
    AND (confirmed_at IS NULL) = (refund_count IS NULL) AND (confirmed_at IS NULL) = (refund_amount IS NULL)
    AND (confirmed_at IS NULL) = (vat_amount IS NULL) AND (confirmed_at IS NULL) = (base_amount IS NULL)
    AND (confirmed_at IS NULL) = (fee_rate_percent IS NULL) AND (confirmed_at IS NULL) = (fee_amount IS NULL)
    AND (confirmed_at IS NULL) = (share_amount IS NULL) AND (confirmed_at IS NULL) = (carried_in IS NULL)
    AND (confirmed_at IS NULL) = (withholding_amount IS NULL) AND (confirmed_at IS NULL) = (payout_amount IS NULL)
    AND (confirmed_at IS NULL) = (carried_out IS NULL) AND (confirmed_at IS NULL) = (payee_type IS NULL)
    AND (confirmed_at IS NULL) = (pay_date IS NULL) AND (confirmed_at IS NULL) = (closes_at IS NULL)
    AND (confirmed_by IS NULL OR confirmed_at IS NOT NULL)
    AND (status <> 'ESTIMATED' OR confirmed_at IS NULL)
    AND (status NOT IN ('CONFIRMED', 'PAID', 'WAIVED') OR confirmed_at IS NOT NULL));

ALTER TABLE emoticon_settlements DROP CONSTRAINT ck_emoticon_settlements_amounts;
ALTER TABLE emoticon_settlements ADD CONSTRAINT ck_emoticon_settlements_amounts CHECK (confirmed_at IS NULL OR (
    sales_count >= 0 AND sales_amount >= 0 AND refund_count >= 0 AND refund_amount >= 0
    AND fee_rate_percent BETWEEN 0 AND 90
    AND payee_type IN ('INDIVIDUAL', 'BUSINESS')
    AND base_amount = CASE WHEN sales_amount - refund_amount < 0 THEN -((refund_amount - sales_amount) * 10 / 11) ELSE (sales_amount - refund_amount) * 10 / 11 END
    AND vat_amount = (sales_amount - refund_amount) - base_amount
    AND share_amount = CASE WHEN base_amount < 0 THEN -((-base_amount) * (100 - fee_rate_percent) / 100) ELSE base_amount * (100 - fee_rate_percent) / 100 END
    AND fee_amount = base_amount - share_amount
    AND payout_amount >= 0 AND withholding_amount >= 0
    AND ((payout_amount = 0 AND withholding_amount = 0 AND carried_out = share_amount + carried_in)
         OR (payout_amount > 0 AND carried_out = 0 AND payout_amount = share_amount + carried_in - withholding_amount
             AND withholding_amount = CASE WHEN payee_type = 'INDIVIDUAL' THEN (share_amount + carried_in) * 33 / 10000 * 10 ELSE 0 END))
    -- 포기한 줄은 지급하지 않는다 · 확정 가능 시각 전에도 포기한다(탈퇴 완료 순간)
    AND (status <> 'WAIVED' OR payout_amount = 0)
    AND (closes_at <= confirmed_at OR status = 'WAIVED')
    AND pay_date > sales_month));

-- 정산 줄 지키기(V145) + 포기: 굳히지 않은 예정·보류 줄만 포기가 되고, 포기한 줄은 더 바뀌지 않는다
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
            OR (OLD.status = 'HELD' AND OLD.confirmed_at IS NOT NULL AND NEW.status = 'CONFIRMED')) THEN
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
ALTER TABLE emoticon_settlement_events ADD COLUMN amount bigint;
ALTER TABLE emoticon_settlement_events ADD COLUMN consented_at timestamptz;

ALTER TABLE emoticon_settlement_events DROP CONSTRAINT ck_emoticon_settlement_events_action;
ALTER TABLE emoticon_settlement_events ADD CONSTRAINT ck_emoticon_settlement_events_action
    CHECK (action IN ('CONFIRMED', 'HELD', 'UNHELD', 'PAID', 'ACCOUNT_VIEWED', 'WAIVED'));
ALTER TABLE emoticon_settlement_events DROP CONSTRAINT ck_emoticon_settlement_events_note;
ALTER TABLE emoticon_settlement_events ADD CONSTRAINT ck_emoticon_settlement_events_note
    CHECK ((action NOT IN ('HELD', 'ACCOUNT_VIEWED', 'WAIVED') OR char_length(btrim(note)) BETWEEN 5 AND 200) AND (note IS NULL OR char_length(note) <= 200));
-- 포기 기록만 금액이 있다(0 이상). 동의 시각은 포기 기록에만(회원 동의 — 운영자 포기 처리는 없다)
ALTER TABLE emoticon_settlement_events ADD CONSTRAINT ck_emoticon_settlement_events_waived
    CHECK ((action = 'WAIVED') = (amount IS NOT NULL) AND (amount IS NULL OR amount >= 0) AND (consented_at IS NULL OR action = 'WAIVED'));

CREATE OR REPLACE FUNCTION emoticon_settlement_events_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'emoticon_settlement_events is append-only';
    END IF;
    -- 운영자가 지워져 처리자 칸이 비는 것만 받는다
    IF (NEW.id, NEW.creator_id, NEW.sales_month, NEW.action, NEW.at, NEW.note, NEW.amount, NEW.consented_at)
            IS DISTINCT FROM (OLD.id, OLD.creator_id, OLD.sales_month, OLD.action, OLD.at, OLD.note, OLD.amount, OLD.consented_at)
            OR (NEW.operator_id IS NOT NULL AND NEW.operator_id IS DISTINCT FROM OLD.operator_id) THEN
        RAISE EXCEPTION 'emoticon_settlement_events is append-only';
    END IF;
    RETURN NEW;
END
$$;

-- ── ③ 탈퇴 신청의 로보티콘 수익 포기 동의 ───────────────────────────────────
ALTER TABLE member_withdrawals ADD COLUMN forfeit_emoticon_earnings bigint;
ALTER TABLE member_withdrawals ADD CONSTRAINT ck_mw_forfeit_emoticon CHECK (forfeit_emoticon_earnings IS NULL
    OR (forfeit_emoticon_earnings > 0 AND forfeit_consented_at IS NOT NULL));
