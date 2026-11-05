-- 로보티콘 S4(작가 월 정산) — docs/features/emoticon-contract.md 「서버 구현 단계」 S4 · 「S4 구현 결과」.
-- ⚠ 합칠 때 번호 재확인(가번호 V145 — S1 V142 · S2 V143 · S3 V144). develop 에 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 무엇이 바뀌나
--   ① 정산 표(emoticon_settlements — 작가 × 판매 달 한 줄, 키 = (작가, 달 첫날)). 예정(ESTIMATED)인 동안 금액은 저장하지 않고 조회 때 구매 기록에서 계산한다
--      (서비스 EmoticonSettlementCalculator — 계산식은 EmoticonSettlementMath 한 곳). 줄은 보류·계좌 보기·확정처럼 상태·기록이 생길 때 만든다.
--      확정(CONFIRMED)하는 순간 판매 · 환불 · 부가세 · 정산 기준 · 수수료율(그때 설정) · 수수료 · 작가 몫 · 이월 들어옴 · 원천징수 · 지급액 · 이월 나감 · 작가 구분 · 지급일 ·
--      확정 가능 시각을 굳힌다 — 그 뒤로는 트리거가 고치지 못하게 막는다(금액 칸 사이의 계산 관계도 CHECK 가 본다).
--      상태: ESTIMATED → CONFIRMED → PAID(지급 완료 기록 — 이체는 사이트 밖) · ESTIMATED|CONFIRMED → HELD(사유) → 보류 전 상태(굳힌 줄은 CONFIRMED, 아니면 ESTIMATED).
--   ② 처리 기록(emoticon_settlement_events — 추가만): 확정 · 보류 · 보류 풀기 · 지급 완료 · 계좌 원문 보기(사유).
--   ③ 구매 기록에 「들어간 정산 달」(settlement_month — 판매가 들어간 달) · 「환불을 뺀 정산 달」(refund_settlement_month): 확정할 때 그 줄에 들어간 판매·환불에 찍는다(한 번만).
--      아직 찍히지 않은 판매·환불이 예정 계산의 입력이다 — 판매는 결제 달, 환불은 판매가 아직 확정 전이면 판매 달 · 이미 확정된 판매면 환불한 달(확정 뒤 환불은 환불한 달에서 뺀다).
--      들어갈 달이 이미 굳었으면(늦게 들어온 결제 등) 다음에 열린 달로 넘긴다. 관리자 판매 줄의 settlementMonth 는 settlement_month.
-- 새 표는 users 에 외래 키를 걸지 않는다(V142 과 같다 — 탈퇴해도 정산 기록은 남는다). 운영자는 ON DELETE SET NULL.
-- 정기 작업(R__batch)은 없다 — 예정 금액은 조회 때 계산하고, 확정은 운영자가 한다.

-- ── ① 정산 표 ───────────────────────────────────────────────────────────────
CREATE TABLE emoticon_settlements (
    creator_id          bigint        NOT NULL,
    sales_month         date          NOT NULL,
    status              varchar(10)   NOT NULL,
    -- 굳힌 값(확정 때) — 확정 전에는 모두 null
    sales_count         integer,
    sales_amount        bigint,
    refund_count        integer,
    refund_amount       bigint,
    vat_amount          bigint,
    base_amount         bigint,
    fee_rate_percent    integer,
    fee_amount          bigint,
    share_amount        bigint,
    carried_in          bigint,
    withholding_amount  bigint,
    payout_amount       bigint,
    carried_out         bigint,
    payee_type          varchar(10),
    pay_date            date,
    closes_at           timestamptz,
    confirmed_at        timestamptz,
    confirmed_by        bigint        REFERENCES operators (id) ON DELETE SET NULL,
    -- 보류(지금 보류 중일 때만)
    hold_reason         varchar(200),
    held_at             timestamptz,
    held_by             bigint        REFERENCES operators (id) ON DELETE SET NULL,
    -- 지급 완료 기록
    paid_at             timestamptz,
    paid_by             bigint        REFERENCES operators (id) ON DELETE SET NULL,
    transfer_date       date,
    paid_memo           varchar(200),
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    PRIMARY KEY (creator_id, sales_month),
    CONSTRAINT ck_emoticon_settlements_month CHECK (extract(day FROM sales_month) = 1),
    CONSTRAINT ck_emoticon_settlements_status CHECK (status IN ('ESTIMATED', 'CONFIRMED', 'PAID', 'HELD')),
    -- 굳힘: 확정 시각이 있으면 굳힌 칸이 모두 있고, 없으면 모두 없다. 예정은 굳히지 않은 줄, 확정·지급은 굳힌 줄(보류는 둘 다 있다)
    CONSTRAINT ck_emoticon_settlements_frozen CHECK (
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
        AND (status NOT IN ('CONFIRMED', 'PAID') OR confirmed_at IS NOT NULL)),
    -- 굳힌 금액의 계산 관계(EmoticonSettlementMath 와 같은 정수 식 — 음수 순판매는 부호만 바꿔 같은 식):
    --   정산 기준 = ±⌊|판매 − 환불| × 10 ÷ 11⌋ · 부가세 = (판매 − 환불) − 정산 기준 · 작가 몫 = ±⌊|정산 기준| × (100 − 수수료율) ÷ 100⌋ · 수수료 = 정산 기준 − 작가 몫
    --   합 = 작가 몫 + 이월 들어옴. 지급하면 지급액 = 합 − 원천징수(개인 = ⌊합 × 3.3% ÷ 10⌋ × 10, 사업자 0) · 이월 나감 0. 지급하지 않으면(최소 지급액 미만 · 0 이하) 지급액·원천징수 0 · 이월 나감 = 합
    CONSTRAINT ck_emoticon_settlements_amounts CHECK (confirmed_at IS NULL OR (
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
        AND closes_at <= confirmed_at
        AND pay_date > sales_month)),
    CONSTRAINT ck_emoticon_settlements_hold CHECK ((status = 'HELD') = (hold_reason IS NOT NULL) AND (hold_reason IS NULL) = (held_at IS NULL)
        AND (hold_reason IS NULL OR char_length(btrim(hold_reason)) BETWEEN 5 AND 200) AND (held_by IS NULL OR held_at IS NOT NULL)),
    CONSTRAINT ck_emoticon_settlements_paid CHECK ((status = 'PAID') = (paid_at IS NOT NULL) AND (paid_at IS NULL) = (transfer_date IS NULL)
        AND (paid_memo IS NULL OR (paid_at IS NOT NULL AND char_length(paid_memo) <= 200)) AND (paid_by IS NULL OR paid_at IS NOT NULL)
        AND (paid_at IS NULL OR payout_amount > 0))
);
CREATE INDEX ix_emoticon_settlements_month ON emoticon_settlements (sales_month, creator_id);
-- 달마다 「그 앞의 마지막 굳힌 줄」(이월의 시작) — 작가별 최근 달
CREATE INDEX ix_emoticon_settlements_frozen ON emoticon_settlements (creator_id, sales_month DESC) WHERE confirmed_at IS NOT NULL;

-- 정산 줄 지키기 — 지우기 금지 · 작가·달 바꾸기 금지 · 굳힌 칸은 확정 뒤 고치지 못한다 · 상태는 정한 길로만 · 지급 기록은 한 번
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
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
            (OLD.status = 'ESTIMATED' AND NEW.status IN ('CONFIRMED', 'HELD'))
            OR (OLD.status = 'CONFIRMED' AND NEW.status IN ('HELD', 'PAID'))
            OR (OLD.status = 'HELD' AND OLD.confirmed_at IS NULL AND NEW.status = 'ESTIMATED')
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
CREATE TRIGGER tr_emoticon_settlements_guard BEFORE UPDATE OR DELETE ON emoticon_settlements FOR EACH ROW EXECUTE FUNCTION emoticon_settlements_guard();

-- ── ② 처리 기록(추가만) ─────────────────────────────────────────────────────
CREATE TABLE emoticon_settlement_events (
    id           bigint        PRIMARY KEY,
    creator_id   bigint        NOT NULL,
    sales_month  date          NOT NULL,
    action       varchar(20)   NOT NULL,
    at           timestamptz   NOT NULL,
    operator_id  bigint        REFERENCES operators (id) ON DELETE SET NULL,
    note         varchar(200),
    CONSTRAINT fk_emoticon_settlement_events_settlement FOREIGN KEY (creator_id, sales_month) REFERENCES emoticon_settlements (creator_id, sales_month),
    CONSTRAINT ck_emoticon_settlement_events_action CHECK (action IN ('CONFIRMED', 'HELD', 'UNHELD', 'PAID', 'ACCOUNT_VIEWED')),
    -- 보류·계좌 보기는 사유(5~200자)가 있다
    CONSTRAINT ck_emoticon_settlement_events_note CHECK ((action NOT IN ('HELD', 'ACCOUNT_VIEWED') OR char_length(btrim(note)) BETWEEN 5 AND 200)
        AND (note IS NULL OR char_length(note) <= 200))
);
CREATE INDEX ix_emoticon_settlement_events_settlement ON emoticon_settlement_events (creator_id, sales_month, at);

CREATE OR REPLACE FUNCTION emoticon_settlement_events_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'emoticon_settlement_events is append-only';
    END IF;
    -- 운영자가 지워져 처리자 칸이 비는 것만 받는다
    IF (NEW.id, NEW.creator_id, NEW.sales_month, NEW.action, NEW.at, NEW.note) IS DISTINCT FROM (OLD.id, OLD.creator_id, OLD.sales_month, OLD.action, OLD.at, OLD.note)
            OR (NEW.operator_id IS NOT NULL AND NEW.operator_id IS DISTINCT FROM OLD.operator_id) THEN
        RAISE EXCEPTION 'emoticon_settlement_events is append-only';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_emoticon_settlement_events_guard BEFORE UPDATE OR DELETE ON emoticon_settlement_events FOR EACH ROW EXECUTE FUNCTION emoticon_settlement_events_guard();

-- ── ③ 구매 기록의 정산 달 ───────────────────────────────────────────────────
ALTER TABLE emoticon_purchases ADD COLUMN settlement_month date;
ALTER TABLE emoticon_purchases ADD COLUMN refund_settlement_month date;
ALTER TABLE emoticon_purchases ADD CONSTRAINT fk_emoticon_purchases_settlement FOREIGN KEY (creator_id, settlement_month) REFERENCES emoticon_settlements (creator_id, sales_month);
ALTER TABLE emoticon_purchases ADD CONSTRAINT fk_emoticon_purchases_refund_settlement FOREIGN KEY (creator_id, refund_settlement_month)
    REFERENCES emoticon_settlements (creator_id, sales_month);
-- 판매가 들어간 달은 결제된 구매에만 · 환불을 뺀 달은 환불된 구매에만이고 그 판매도 이미 정산에 들어갔다 · 결제 달보다 앞일 수 없다(늦은 결제는 뒤로 넘긴다) · 환불은 판매보다 앞 달일 수 없다
ALTER TABLE emoticon_purchases ADD CONSTRAINT ck_emoticon_purchases_settlement CHECK (
    (settlement_month IS NULL OR (paid_at IS NOT NULL AND creator_id IS NOT NULL AND settlement_month >= date_trunc('month', paid_at AT TIME ZONE 'Asia/Seoul')::date))
    AND (refund_settlement_month IS NULL OR (refunded_at IS NOT NULL AND settlement_month IS NOT NULL AND refund_settlement_month >= settlement_month)));
-- 예정 계산의 입력(아직 정산에 들어가지 않은 판매·환불)만 따로 색인 — 확정되면 색인에서 빠져 오래 쌓여도 빠르다
CREATE INDEX ix_emoticon_purchases_unsettled_sales ON emoticon_purchases (creator_id) WHERE paid_at IS NOT NULL AND settlement_month IS NULL AND creator_id IS NOT NULL;
CREATE INDEX ix_emoticon_purchases_unsettled_refunds ON emoticon_purchases (creator_id)
    WHERE refunded_at IS NOT NULL AND refund_settlement_month IS NULL AND creator_id IS NOT NULL;
-- 굳힌 줄의 팩별 내역
CREATE INDEX ix_emoticon_purchases_settlement ON emoticon_purchases (creator_id, settlement_month) WHERE settlement_month IS NOT NULL;
CREATE INDEX ix_emoticon_purchases_refund_settlement ON emoticon_purchases (creator_id, refund_settlement_month) WHERE refund_settlement_month IS NOT NULL;

-- 구매 기록 지키기(V144) + 정산 달은 한 번만 찍고, 찍을 때 그 정산 줄이 확정(굳힘)돼 있어야 한다
CREATE OR REPLACE FUNCTION emoticon_purchases_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'emoticon_purchases rows cannot be deleted';
    END IF;
    IF (NEW.id, NEW.user_id, NEW.pack_id, NEW.creator_id, NEW.pack_name, NEW.amount, NEW.refund_notice_agreed_at, NEW.expires_at, NEW.created_at)
            IS DISTINCT FROM (OLD.id, OLD.user_id, OLD.pack_id, OLD.creator_id, OLD.pack_name, OLD.amount, OLD.refund_notice_agreed_at, OLD.expires_at, OLD.created_at) THEN
        RAISE EXCEPTION 'emoticon purchase snapshot cannot change';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT ((OLD.status = 'PENDING' AND NEW.status IN ('PAID', 'CLOSED')) OR (OLD.status = 'PAID' AND NEW.status = 'REFUNDED')) THEN
        RAISE EXCEPTION 'emoticon purchase status % -> % is not allowed', OLD.status, NEW.status;
    END IF;
    IF (OLD.closed_at IS NOT NULL AND NEW.closed_at IS DISTINCT FROM OLD.closed_at)
            OR (OLD.payment_order_id IS NOT NULL AND NEW.payment_order_id IS DISTINCT FROM OLD.payment_order_id)
            OR (OLD.paid_at IS NOT NULL AND NEW.paid_at IS DISTINCT FROM OLD.paid_at)
            OR (OLD.refund_until IS NOT NULL AND NEW.refund_until IS DISTINCT FROM OLD.refund_until)
            OR (OLD.used_at IS NOT NULL AND NEW.used_at IS DISTINCT FROM OLD.used_at)
            OR (OLD.refunded_at IS NOT NULL AND NEW.refunded_at IS DISTINCT FROM OLD.refunded_at)
            OR (OLD.refund_kind IS NOT NULL AND NEW.refund_kind IS DISTINCT FROM OLD.refund_kind)
            OR (OLD.refund_reason IS NOT NULL AND NEW.refund_reason IS DISTINCT FROM OLD.refund_reason)
            OR (OLD.refund_cancel_id IS NOT NULL AND NEW.refund_cancel_id IS DISTINCT FROM OLD.refund_cancel_id)
            OR (OLD.refund_completed_at IS NOT NULL AND NEW.refund_completed_at IS DISTINCT FROM OLD.refund_completed_at)
            OR (NEW.refunded_by IS NOT NULL AND NEW.refunded_by IS DISTINCT FROM OLD.refunded_by AND OLD.refunded_at IS NOT NULL)
            OR (OLD.settlement_month IS NOT NULL AND NEW.settlement_month IS DISTINCT FROM OLD.settlement_month)
            OR (OLD.refund_settlement_month IS NOT NULL AND NEW.refund_settlement_month IS DISTINCT FROM OLD.refund_settlement_month) THEN
        RAISE EXCEPTION 'emoticon purchase recorded fields cannot change';
    END IF;
    -- 「사용함」은 보유 중(PAID)인 구매에 처음 한 번만 찍는다(환불한 뒤에는 찍지 않는다)
    IF NEW.used_at IS DISTINCT FROM OLD.used_at AND NOT (OLD.status = 'PAID' AND NEW.status = 'PAID') THEN
        RAISE EXCEPTION 'emoticon purchase used_at can be set only while paid';
    END IF;
    -- 정산 달은 그 줄을 확정(굳힘)하는 처리만 찍는다
    IF (NEW.settlement_month IS DISTINCT FROM OLD.settlement_month AND NOT EXISTS (
            SELECT 1 FROM emoticon_settlements s WHERE s.creator_id = NEW.creator_id AND s.sales_month = NEW.settlement_month AND s.confirmed_at IS NOT NULL))
        OR (NEW.refund_settlement_month IS DISTINCT FROM OLD.refund_settlement_month AND NOT EXISTS (
            SELECT 1 FROM emoticon_settlements s WHERE s.creator_id = NEW.creator_id AND s.sales_month = NEW.refund_settlement_month AND s.confirmed_at IS NOT NULL)) THEN
        RAISE EXCEPTION 'emoticon purchase settlement month must point to a confirmed settlement';
    END IF;
    RETURN NEW;
END
$$;
