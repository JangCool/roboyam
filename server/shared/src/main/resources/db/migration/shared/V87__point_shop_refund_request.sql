-- 커머스 1-2 정지 중 환불 신청 — docs/features/commerce-stage1-contract.md 「1-2 정지 중 환불 신청」(사용자 2026-10-26 「환불 신청 → 운영자 수락」).
-- ⚠ 합칠 때 번호 재확인(가번호 V87 — V86 이 로컬 DB 에 적용됐을 수 있어 V86 은 고치지 않는다).
-- ⚠ 합칠 때 합집합 확인 — 회원 알림 종류 CHECK 를 다시 만든다(V85 의 목록 ∪ 포인트 상점 환불 신청 결과 둘). 그 뒤에 이 CHECK 를 또 만드는 쪽이 모든 값을 담아야 한다.
--
-- 무엇이 바뀌나
--   · 환불 신청(point_shop_refund_requests): 정지·영구 정지 중인 회원의 셀프 환불은 바로 처리하지 않고 신청(PENDING)으로 남긴다. 조건(안 씀 + 기한 안)은 신청 순간에 판정하고
--     고정한다. 운영자(pointshop.refund)가 수락(APPROVED — 기존 환불과 같은 처리)·거절(REJECTED — 사유 5~200자). 같은 구매에 대기 신청은 하나(부분 유일 색인).
--     결정은 한 번뿐(트리거). users·operators 외래키는 걸지 않는다(돈 기록 — 결정 5·구매의 refunded_by_operator_id 와 같다).
--   · 구매의 환불 종류(refund_kind): SELF(회원 셀프) · EXCEPTION(운영자 예외 환불 — 사유) · REQUEST(신청 수락 — 처리 운영자). 기존 환불 줄은 운영자가 있으면 EXCEPTION, 없으면 SELF.
--   · 회원 알림 종류: POINT_SHOP_REFUND_APPROVED · POINT_SHOP_REFUND_REJECTED. 운영자 알림 이벤트(POINT_SHOP_REFUND_REQUESTED)는 코드가 원천이라 표를 고치지 않는다
--     (서버가 뜰 때 notification_events 와 맞춘다 — 닉네임 그림 심사 대기와 같다).

-- ── 환불 신청 ────────────────────────────────────────────────────────────────
CREATE TABLE point_shop_refund_requests (
    id                     bigint       PRIMARY KEY,
    purchase_id            bigint       NOT NULL REFERENCES point_shop_purchases (id),
    user_item_id           bigint       NOT NULL REFERENCES user_items (id),
    user_id                bigint       NOT NULL,
    status                 varchar(10)  NOT NULL DEFAULT 'PENDING',
    requested_at           timestamptz  NOT NULL,
    decided_at             timestamptz,
    decided_by_operator_id bigint,
    reject_reason          varchar(200),
    CONSTRAINT ck_point_shop_refund_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- 결정 칸은 결정한 신청에만, 함께. 거절 사유는 거절에만(반드시)
    CONSTRAINT ck_point_shop_refund_requests_decided CHECK ((status = 'PENDING') = (decided_at IS NULL) AND (status = 'PENDING') = (decided_by_operator_id IS NULL)
                                                           AND (status = 'REJECTED') = (reject_reason IS NOT NULL)),
    CONSTRAINT ck_point_shop_refund_requests_order CHECK (decided_at IS NULL OR decided_at >= requested_at)
);
-- 같은 구매에 대기 신청은 하나 — 동시에 두 번 신청해도 한 줄(서비스가 구매 행을 잠가 먼저 막고 이 색인이 끝까지 막는다)
CREATE UNIQUE INDEX ux_point_shop_refund_requests_pending ON point_shop_refund_requests (purchase_id) WHERE status = 'PENDING';
CREATE INDEX ix_point_shop_refund_requests_purchase ON point_shop_refund_requests (purchase_id, id DESC);
CREATE INDEX ix_point_shop_refund_requests_item ON point_shop_refund_requests (user_item_id) WHERE status = 'PENDING';

-- 정체 칸은 고치지 않는다. 상태는 PENDING → APPROVED·REJECTED 한 번만(결정 칸은 그때 한 번 채운다)
CREATE FUNCTION point_shop_refund_requests_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.purchase_id IS DISTINCT FROM OLD.purchase_id OR NEW.user_item_id IS DISTINCT FROM OLD.user_item_id OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.requested_at IS DISTINCT FROM OLD.requested_at THEN
        RAISE EXCEPTION 'point_shop_refund_requests identity is fixed';
    END IF;
    IF OLD.status <> 'PENDING' AND (NEW.status IS DISTINCT FROM OLD.status OR NEW.decided_at IS DISTINCT FROM OLD.decided_at
       OR NEW.decided_by_operator_id IS DISTINCT FROM OLD.decided_by_operator_id OR NEW.reject_reason IS DISTINCT FROM OLD.reject_reason) THEN
        RAISE EXCEPTION 'point_shop_refund_requests decision is final';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_point_shop_refund_requests_guard BEFORE UPDATE ON point_shop_refund_requests FOR EACH ROW EXECUTE FUNCTION point_shop_refund_requests_guard();

-- ── 구매의 환불 종류 ─────────────────────────────────────────────────────────
ALTER TABLE point_shop_purchases ADD COLUMN refund_kind varchar(10);
UPDATE point_shop_purchases SET refund_kind = CASE WHEN refunded_by_operator_id IS NULL THEN 'SELF' ELSE 'EXCEPTION' END WHERE status = 'REFUNDED';
ALTER TABLE point_shop_purchases DROP CONSTRAINT ck_point_shop_purchases_exception;
-- SELF = 운영자·사유 없음 · EXCEPTION = 운영자 + 사유 · REQUEST = 운영자(수락), 사유 없음(거절 사유는 신청 줄에). 환불 종류는 환불한 줄에만
ALTER TABLE point_shop_purchases ADD CONSTRAINT ck_point_shop_purchases_refund_kind CHECK (
    (refund_kind IS NULL) = (status <> 'REFUNDED')
    AND (refund_kind IS NULL OR refund_kind IN ('SELF', 'EXCEPTION', 'REQUEST'))
    AND (refund_kind IS DISTINCT FROM 'SELF' OR (refunded_by_operator_id IS NULL AND refund_reason IS NULL))
    AND (refund_kind IS DISTINCT FROM 'EXCEPTION' OR (refunded_by_operator_id IS NOT NULL AND refund_reason IS NOT NULL))
    AND (refund_kind IS DISTINCT FROM 'REQUEST' OR (refunded_by_operator_id IS NOT NULL AND refund_reason IS NULL))
    AND (refund_kind IS NOT NULL OR (refunded_by_operator_id IS NULL AND refund_reason IS NULL)));

-- 사본 고정 트리거에 환불 종류도 넣는다(환불한 뒤에는 바뀌지 않는다)
CREATE OR REPLACE FUNCTION point_shop_purchases_snapshot_fixed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.item_id IS DISTINCT FROM OLD.item_id OR NEW.item_code IS DISTINCT FROM OLD.item_code
       OR NEW.item_name IS DISTINCT FROM OLD.item_name OR NEW.item_kind IS DISTINCT FROM OLD.item_kind OR NEW.usage_type IS DISTINCT FROM OLD.usage_type
       OR NEW.price_point IS DISTINCT FROM OLD.price_point OR NEW.duration_days IS DISTINCT FROM OLD.duration_days
       OR NEW.refund_notice_agreed_at IS DISTINCT FROM OLD.refund_notice_agreed_at OR NEW.use_ledger_id IS DISTINCT FROM OLD.use_ledger_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'point_shop_purchases snapshot is fixed';
    END IF;
    IF OLD.status = 'REFUNDED' AND (NEW.status IS DISTINCT FROM OLD.status OR NEW.refund_ledger_id IS DISTINCT FROM OLD.refund_ledger_id
       OR NEW.refunded_at IS DISTINCT FROM OLD.refunded_at OR NEW.refunded_by_operator_id IS DISTINCT FROM OLD.refunded_by_operator_id
       OR NEW.refund_reason IS DISTINCT FROM OLD.refund_reason OR NEW.refund_kind IS DISTINCT FROM OLD.refund_kind) THEN
        RAISE EXCEPTION 'point_shop_purchases refund is final';
    END IF;
    RETURN NEW;
END $$;

-- ── 회원 알림 종류: 포인트 상점 환불 신청 결과(수락·거절) ─────────────────────
-- 알림 설정 행(member_event_settings)은 넣지 않는다 — 줄이 없으면 코드의 처음 값(MemberEvent)을 쓴다(V84·V85 와 같다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED'));
