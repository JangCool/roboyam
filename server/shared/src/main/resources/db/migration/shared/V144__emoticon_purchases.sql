-- 로보티콘 S3(유료 팩 실결제 · 「사용함」 기록 · 셀프·예외 환불 · 판매 내역) — docs/features/emoticon-contract.md 「서버 구현 단계」 S3 · 「S3 구현 결과」.
-- ⚠ 합칠 때 번호 재확인(가번호 V144 — S1 V142 · S2 V143). develop 에 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 무엇이 바뀌나
--   ① 구매 기록(emoticon_purchases): 결제 준비 한 번 = 한 줄(결제 주문 payment_orders.order_ref_id = 이 줄 ID, 주문 종류 ROBOTIKON_PACK).
--      산 때의 팩 이름·금액·작가 사본, 환불 고지 동의 시각, 결제 완료(결제 시각 · 셀프 환불 기한 = 결제 시각 + 그때의 환불 기간), 처음 쓴 시각(used_at — 글·댓글 저장 트랜잭션),
--      환불(시각 · 종류 SELF/EXCEPTION · 사유 · 운영자 · PG 취소 대기열 줄 · PG 취소 완료 시각). 상태 PENDING(결제 대기) → PAID(보유) / CLOSED(새 준비가 대신했거나 결제가 거절됐다)
--      · PAID → REFUNDED. 지우기·되돌리기·금액 고치기는 트리거가 막는다(S4 월 정산의 입력 — 판매 = paid_at·amount, 환불 = refunded_at·amount).
--   ② 보유는 받기 줄(emoticon_acquisitions)에 출처로 둔다: kind ∪ PURCHASE + purchase_id(산 구매 줄). (회원, 팩) 하나에 줄 하나라
--      무료일 때 받은 회원 · 산 회원 모두 「이미 가진 팩」이고, 산 팩이 무료가 된 뒤 「받기」를 눌러도 새 받기 줄·받은 수·작가 보상이 생기지 않는다(받기 보상은 줄마다 한 번).
--      환불하면 그 줄에 removed_at(보유 해제). 토큰 검사·내 로보티콘·팩의 acquired 는 그대로 이 표 하나를 본다.
--   ③ 결제 주문 종류 ∪ ROBOTIKON_PACK(V112 목록 ∪).
-- 새 표는 users 에 외래 키를 걸지 않는다(V142 과 같다). 운영자는 ON DELETE SET NULL.

-- ── ③ 결제 주문 종류 ─────────────────────────────────────────────────────────
ALTER TABLE payment_orders DROP CONSTRAINT ck_payment_orders_type;
ALTER TABLE payment_orders ADD CONSTRAINT ck_payment_orders_type CHECK (order_type IN ('POINT_CHARGE', 'GROUP_BUY', 'GROUP_BUY_RETURN_FEE', 'PAYMENT_TEST',
                                                                                      'BIZ_APPLICATION', 'ROBOTIKON_PACK'));

-- ── ① 구매 기록 ─────────────────────────────────────────────────────────────
CREATE TABLE emoticon_purchases (
    id                      bigint        PRIMARY KEY,
    user_id                 bigint        NOT NULL,
    pack_id                 bigint        NOT NULL REFERENCES emoticon_packs (id),
    -- 산 때의 사본 — 작가(운영 팩은 늘 무료라 실제로는 늘 있다) · 팩 이름 · 금액(그 뒤 가격을 바꿔도 이 구매는 그대로)
    creator_id              bigint,
    pack_name               varchar(40)   NOT NULL,
    amount                  integer       NOT NULL,
    status                  varchar(10)   NOT NULL,
    refund_notice_agreed_at timestamptz   NOT NULL,
    expires_at              timestamptz   NOT NULL,
    created_at              timestamptz   NOT NULL,
    closed_at               timestamptz,
    payment_order_id        bigint        REFERENCES payment_orders (id),
    paid_at                 timestamptz,
    refund_until            timestamptz,
    used_at                 timestamptz,
    refunded_at             timestamptz,
    refund_kind             varchar(10),
    refund_reason           varchar(200),
    refunded_by             bigint        REFERENCES operators (id) ON DELETE SET NULL,
    refund_cancel_id        bigint        REFERENCES payment_cancels (id),
    refund_completed_at     timestamptz,
    CONSTRAINT ck_emoticon_purchases_status CHECK (status IN ('PENDING', 'CLOSED', 'PAID', 'REFUNDED')),
    CONSTRAINT ck_emoticon_purchases_amount CHECK (amount BETWEEN 100 AND 100000 AND amount % 100 = 0),
    CONSTRAINT ck_emoticon_purchases_name CHECK (char_length(btrim(pack_name)) >= 1),
    CONSTRAINT ck_emoticon_purchases_expires CHECK (expires_at > created_at),
    CONSTRAINT ck_emoticon_purchases_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    -- 결제 완료 계열(PAID·REFUNDED)만 결제 칸(결제 주문 · 결제 시각 · 셀프 환불 기한)이 있다
    CONSTRAINT ck_emoticon_purchases_paid CHECK ((status IN ('PAID', 'REFUNDED')) = (paid_at IS NOT NULL)
        AND (paid_at IS NULL) = (payment_order_id IS NULL) AND (paid_at IS NULL) = (refund_until IS NULL)
        AND (refund_until IS NULL OR refund_until > paid_at)),
    -- 「사용함」은 결제된 구매에만
    CONSTRAINT ck_emoticon_purchases_used CHECK (used_at IS NULL OR paid_at IS NOT NULL),
    -- 환불 칸은 환불된 구매에만. 셀프 환불은 사유가 없고 쓰지 않은 구매를 기한 안에만, 예외 환불은 사유 5~200자
    CONSTRAINT ck_emoticon_purchases_refund CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL)
        AND (refunded_at IS NULL) = (refund_kind IS NULL)
        AND (refund_kind IS NULL OR refund_kind IN ('SELF', 'EXCEPTION'))
        AND (refund_reason IS NULL OR refund_kind = 'EXCEPTION')
        AND (refund_kind IS DISTINCT FROM 'EXCEPTION' OR char_length(btrim(refund_reason)) BETWEEN 5 AND 200)
        AND (refund_kind IS DISTINCT FROM 'SELF' OR (used_at IS NULL AND refunded_at < refund_until))
        AND (refunded_at IS NULL OR refunded_at >= paid_at)
        AND (refunded_by IS NULL OR refund_kind = 'EXCEPTION')
        AND (refund_cancel_id IS NULL OR refunded_at IS NOT NULL)
        AND (refund_completed_at IS NULL OR refunded_at IS NOT NULL))
);
-- 회원·팩마다 보유 중인 구매는 하나 · 결제 대기도 하나(새 결제 준비가 앞 것을 닫는다) · 결제 주문 하나에 구매 하나
CREATE UNIQUE INDEX ux_emoticon_purchases_paid ON emoticon_purchases (user_id, pack_id) WHERE status = 'PAID';
CREATE UNIQUE INDEX ux_emoticon_purchases_pending ON emoticon_purchases (user_id, pack_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX ux_emoticon_purchases_payment ON emoticon_purchases (payment_order_id) WHERE payment_order_id IS NOT NULL;
CREATE INDEX ix_emoticon_purchases_member ON emoticon_purchases (user_id, paid_at DESC) WHERE paid_at IS NOT NULL;
CREATE INDEX ix_emoticon_purchases_paid_at ON emoticon_purchases (paid_at DESC, id DESC) WHERE paid_at IS NOT NULL;
CREATE INDEX ix_emoticon_purchases_pack ON emoticon_purchases (pack_id, paid_at) WHERE paid_at IS NOT NULL;
-- S4 월 정산의 입력 — 작가별 판매(결제 시각) · 환불(환불 시각)
CREATE INDEX ix_emoticon_purchases_creator_paid ON emoticon_purchases (creator_id, paid_at) WHERE paid_at IS NOT NULL;
CREATE INDEX ix_emoticon_purchases_creator_refunded ON emoticon_purchases (creator_id, refunded_at) WHERE refunded_at IS NOT NULL;

-- 구매 기록 지키기 — 지우기 금지 · 사본·금액·시각 고치기 금지 · 한 번 찍은 칸(결제·사용·환불)은 바꾸지 않는다 · 상태는 PENDING → PAID|CLOSED, PAID → REFUNDED 만
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
            OR (NEW.refunded_by IS NOT NULL AND NEW.refunded_by IS DISTINCT FROM OLD.refunded_by AND OLD.refunded_at IS NOT NULL) THEN
        RAISE EXCEPTION 'emoticon purchase recorded fields cannot change';
    END IF;
    -- 「사용함」은 보유 중(PAID)인 구매에 처음 한 번만 찍는다(환불한 뒤에는 찍지 않는다)
    IF NEW.used_at IS DISTINCT FROM OLD.used_at AND NOT (OLD.status = 'PAID' AND NEW.status = 'PAID') THEN
        RAISE EXCEPTION 'emoticon purchase used_at can be set only while paid';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_emoticon_purchases_guard BEFORE UPDATE OR DELETE ON emoticon_purchases FOR EACH ROW EXECUTE FUNCTION emoticon_purchases_guard();

-- ── ② 보유 = 받기 줄의 출처(FREE · PURCHASE) ───────────────────────────────
ALTER TABLE emoticon_acquisitions ADD COLUMN purchase_id bigint REFERENCES emoticon_purchases (id);
ALTER TABLE emoticon_acquisitions DROP CONSTRAINT ck_emoticon_acquisitions_kind;
-- 산 줄만 구매를 가리킨다(환불해 보유를 뺀 줄도 그 구매를 가리킨 채 남는다 — 그 팩이 무료가 된 뒤 받으면 FREE 로 돌아간다)
ALTER TABLE emoticon_acquisitions ADD CONSTRAINT ck_emoticon_acquisitions_kind CHECK (kind IN ('FREE', 'PURCHASE') AND (kind = 'PURCHASE') = (purchase_id IS NOT NULL));
CREATE INDEX ix_emoticon_acquisitions_purchase ON emoticon_acquisitions (purchase_id) WHERE purchase_id IS NOT NULL;
