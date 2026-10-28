-- 커머스 2b-3 공구 발송·요청·환불 — docs/features/commerce-groupbuy-contract.md 「마이그레이션 계획」 V102·결정 6·11·14·15·16·20·30·32·36·42~44·52~55·64·67~75, commerce.md 10-3·10-8·10-8b·10-9.
-- 번호: develop 의 shared 마지막 V101(2b-2) 다음(2026-10-28 확인).
--
-- 무엇이 생기나
--   · 택배사(commerce_carriers — 6개 시드, 전 환경) · 발송(group_buy_shipments — 주문당 처음 발송 하나 + 교환 재발송) · 에스크로 호출 대기열(group_buy_escrow_jobs — 커밋 뒤·재시도).
--   · 취소·반품 요청(group_buy_requests) · 요청 줄(group_buy_request_items — 줄마다 사유·수량·환불액) · 반품 처리 방법(group_buy_return_dispositions) · 요청 사진(group_buy_request_photos).
--   · 배송지 열람·명단 내보내기 기록(commerce_address_views) · 계좌 송금(commerce_bank_transfers — 결정 6·67, 계좌 암호문은 송금 완료 뒤 지운다).
--   · 단품 공급 불가(group_buy_supply_failures)·회원 선택(group_buy_supply_choices — 결정 53) · 운영 환불 승인 대기(group_buy_admin_refunds — 결정 70).
--   · 환불 기록(V101 group_buy_refunds) 보강: 반품비 추가 결제로 메운 몫·그 결제의 환불 칸(결정 68).
--   · 결제 모듈 소폭: 주문 종류 GROUP_BUY_RETURN_FEE(결정 68), 수동 처리 방법(결정 74).
-- 다른 모듈 표(users·payment_orders)에 외래키를 걸지 않는다(commerce.md 3절). 금액 bigint(원), 수량 integer, 시각 timestamptz, ID Snowflake(택배사는 코드).

-- ── 택배사(결정 11) — 조회 주소 템플릿은 https:// 이고 {no} 가 한 번. 발송 이력이 있는 코드는 지우지 못하고 끄기만(앱) ─────────────
CREATE TABLE commerce_carriers (
    code          varchar(20)   PRIMARY KEY,
    name          varchar(40)   NOT NULL,
    tracking_url  varchar(300)  NOT NULL,
    enabled       boolean       NOT NULL DEFAULT true,
    sort_order    integer       NOT NULL DEFAULT 0,
    updated_at    timestamptz,
    updated_by    bigint,
    CONSTRAINT ck_commerce_carriers_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),
    CONSTRAINT ck_commerce_carriers_url CHECK (tracking_url LIKE 'https://%' AND (length(tracking_url) - length(replace(tracking_url, '{no}', ''))) = 4)
);
INSERT INTO commerce_carriers (code, name, tracking_url, sort_order) VALUES
    ('CJ', 'CJ대한통운', 'https://trace.cjlogistics.com/next/tracking.html?wblNo={no}', 1),
    ('HANJIN', '한진택배', 'https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&wblnum={no}', 2),
    ('LOTTE', '롯데택배', 'https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo={no}', 3),
    ('EPOST', '우체국택배', 'https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1={no}', 4),
    ('LOGEN', '로젠택배', 'https://www.ilogen.com/web/personal/trace/{no}', 5),
    ('KDEXP', '경동택배', 'https://kdexp.com/service/delivery/etc/delivery.do?barcode={no}', 6);

-- ── 발송(결정 47·54 — 주문 한 번에, 교환은 같은 주문에 재발송) ─────────────────────────────────────────
-- kind ORIGINAL(처음 발송 — 주문당 하나) · EXCHANGE(교환 재발송 — request_id). tracking_no: 숫자·영문 6~30자로 정리해 저장(결정 11).
-- escrow_*_at: 에스크로 배송 정보 등록·구매 확정을 포트원에 마친 시각(커밋 뒤 호출 결과를 조건부로 — 결정 64). escrow_applicable: 에스크로 결제였는가(null = 아직 모름).
CREATE TABLE group_buy_shipments (
    id                  bigint        PRIMARY KEY,
    participation_id    bigint        NOT NULL REFERENCES group_buy_participations (id),
    group_buy_id        bigint        NOT NULL,
    kind                varchar(10)   NOT NULL,
    request_id          bigint,
    carrier_code        varchar(20)   NOT NULL REFERENCES commerce_carriers (code),
    tracking_no         varchar(30)   NOT NULL,
    shipped_at          timestamptz   NOT NULL,
    delivered_at        timestamptz,
    escrow_applicable   boolean,
    escrow_logistics_at timestamptz,
    escrow_completed_at timestamptz,
    created_by          bigint,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    CONSTRAINT ck_gbs_kind CHECK (kind IN ('ORIGINAL', 'EXCHANGE') AND ((kind = 'EXCHANGE') = (request_id IS NOT NULL))),
    CONSTRAINT ck_gbs_tracking CHECK (tracking_no ~ '^[A-Za-z0-9]{6,30}$'),
    CONSTRAINT ck_gbs_escrow CHECK (escrow_completed_at IS NULL OR escrow_logistics_at IS NOT NULL)
);
CREATE UNIQUE INDEX ux_gbs_original ON group_buy_shipments (participation_id) WHERE kind = 'ORIGINAL';
CREATE UNIQUE INDEX ux_gbs_exchange ON group_buy_shipments (request_id) WHERE kind = 'EXCHANGE';
CREATE INDEX ix_gbs_group ON group_buy_shipments (group_buy_id, shipped_at);
-- 구매 확정 대상 찾기(배송완료 뒤 7일 — 결정 14)
CREATE INDEX ix_gbs_escrow_due ON group_buy_shipments (delivered_at) WHERE escrow_completed_at IS NULL AND delivered_at IS NOT NULL;

-- 에스크로 호출 대기열(결정 64): kind LOGISTICS(등록 — 이미 등록됐으면 수정) · COMPLETE(구매 확정). status PENDING → DONE / SKIPPED(에스크로 결제 아님) / GAVE_UP.
CREATE TABLE group_buy_escrow_jobs (
    id              bigint        PRIMARY KEY,
    shipment_id     bigint        NOT NULL REFERENCES group_buy_shipments (id),
    kind            varchar(10)   NOT NULL,
    status          varchar(10)   NOT NULL DEFAULT 'PENDING',
    attempts        integer       NOT NULL DEFAULT 0,
    next_attempt_at timestamptz   NOT NULL,
    last_error      varchar(200),
    created_at      timestamptz   NOT NULL,
    completed_at    timestamptz,
    CONSTRAINT ck_gbej_kind CHECK (kind IN ('LOGISTICS', 'COMPLETE')),
    CONSTRAINT ck_gbej_status CHECK (status IN ('PENDING', 'DONE', 'SKIPPED', 'GAVE_UP')),
    CONSTRAINT ck_gbej_done CHECK ((status = 'PENDING') = (completed_at IS NULL) AND attempts >= 0)
);
-- 같은 발송·같은 종류의 끝나지 않은 일은 하나(송장 정정이 겹치면 그 줄이 새 값을 보낸다)
CREATE UNIQUE INDEX ux_gbej_pending ON group_buy_escrow_jobs (shipment_id, kind) WHERE status = 'PENDING';
CREATE INDEX ix_gbej_due ON group_buy_escrow_jobs (next_attempt_at) WHERE status = 'PENDING';

-- ── 취소·반품 요청(commerce.md 10-3·10-8, 결정 15·44·54·55·68·72) ─────────────────────────────────────
-- type CANCEL(발송 전 취소 요청 — 운영자 승인) · RETURN(배송완료 뒤 반품).
-- status AWAITING_FEE(반품비 부족분 결제 전 — 접수 전, closes_at 에 닫힘) → REQUESTED → APPROVED(회수 안내) → RECEIVED(돌려받음 — 3영업일 기한 시작) → COMPLETED
--        / REJECTED · WITHDRAWN(회원 철회) · SUPERSEDED(불발·중단·지연 취소·공급 불가로 주문이 전부 환불됨 — 결정 69) · EXPIRED(부족분을 결제하지 않음).
-- collection_method(반품만) COMPANY_PICKUP 업체회수 · CUSTOMER_SEND 고객발송. resolution REFUND · EXCHANGE(하자·오배송만 — 결정 54).
-- preview_amount: 요청 때 계산한 환불액(음수 = 부족분 — 결정 68·72). fee_*: 반품비 부족분 추가 결제(주문 종류 GROUP_BUY_RETURN_FEE).
-- compensation_amount: 고객이 낸 택배비 보상(하자·오배송 + 고객발송 — 운영자가 입력, 상한은 공구 설정).
CREATE TABLE group_buy_requests (
    id                     bigint         PRIMARY KEY,
    participation_id       bigint         NOT NULL REFERENCES group_buy_participations (id),
    group_buy_id           bigint         NOT NULL,
    user_id                bigint         NOT NULL,
    type                   varchar(10)    NOT NULL,
    status                 varchar(15)    NOT NULL,
    collection_method      varchar(20),
    resolution             varchar(10)    NOT NULL DEFAULT 'REFUND',
    seller_fault           boolean        NOT NULL DEFAULT false,
    memo                   varchar(1000),
    preview_amount         bigint         NOT NULL,
    fee_payment_order_id   bigint,
    fee_amount             bigint         NOT NULL DEFAULT 0,
    fee_paid_amount        bigint         NOT NULL DEFAULT 0,
    fee_refunded_amount    bigint         NOT NULL DEFAULT 0,
    closes_at              timestamptz,
    pickup_carrier_code    varchar(20)    REFERENCES commerce_carriers (code),
    pickup_tracking_no     varchar(30),
    received_at            timestamptz,
    compensation_amount    bigint         NOT NULL DEFAULT 0,
    reject_reason_code     varchar(20),
    reject_note            varchar(500),
    decided_by             bigint,
    decided_at             timestamptz,
    completed_at           timestamptz,
    created_at             timestamptz    NOT NULL,
    updated_at             timestamptz    NOT NULL,
    CONSTRAINT ck_gbrq_type CHECK (type IN ('CANCEL', 'RETURN')),
    CONSTRAINT ck_gbrq_status CHECK (status IN ('AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED', 'COMPLETED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED', 'EXPIRED')),
    CONSTRAINT ck_gbrq_method CHECK ((type = 'RETURN') = (collection_method IS NOT NULL) AND (collection_method IS NULL OR collection_method IN ('COMPANY_PICKUP', 'CUSTOMER_SEND'))),
    CONSTRAINT ck_gbrq_resolution CHECK (resolution IN ('REFUND', 'EXCHANGE') AND (resolution = 'REFUND' OR (type = 'RETURN' AND seller_fault))),
    CONSTRAINT ck_gbrq_fee CHECK (fee_amount >= 0 AND fee_paid_amount >= 0 AND fee_refunded_amount >= 0 AND fee_refunded_amount <= fee_paid_amount
        AND (fee_amount = 0 OR type = 'RETURN')),
    CONSTRAINT ck_gbrq_awaiting CHECK (status <> 'AWAITING_FEE' OR (fee_amount > 0 AND closes_at IS NOT NULL)),
    CONSTRAINT ck_gbrq_compensation CHECK (compensation_amount >= 0),
    CONSTRAINT ck_gbrq_reject CHECK ((status = 'REJECTED') = (reject_reason_code IS NOT NULL) AND (reject_reason_code IS NULL OR reject_reason_code IN
        ('CUSTOMER_DAMAGE', 'USED_VALUE_LOSS', 'TIME_VALUE_LOSS', 'OUT_OF_WINDOW', 'ALREADY_SHIPPED'))),
    CONSTRAINT ck_gbrq_pickup CHECK (pickup_tracking_no IS NULL OR (pickup_carrier_code IS NOT NULL AND pickup_tracking_no ~ '^[A-Za-z0-9]{6,30}$'))
);
-- 주문당 열린 요청은 하나(결정 44·66 → GROUP_BUY_REQUEST_OPEN). 부족분 결제 전(AWAITING_FEE)도 연다 — 회수 방식을 바꾸려면 철회 뒤 다시.
CREATE UNIQUE INDEX ux_gbrq_open ON group_buy_requests (participation_id) WHERE status IN ('AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED');
CREATE INDEX ix_gbrq_status ON group_buy_requests (status, created_at);
CREATE INDEX ix_gbrq_user ON group_buy_requests (user_id, created_at);
CREATE INDEX ix_gbrq_awaiting ON group_buy_requests (closes_at) WHERE status = 'AWAITING_FEE';

-- 요청 줄(결정 44): 주문 줄·수량·사유(CHANGE_OF_MIND 단순 변심 · DEFECT 하자 · WRONG_ITEM 오배송 · DAMAGED 배송 중 훼손 · OTHER 기타 — 앞 하나와 기타는 회원 부담)·줄 환불액.
-- prior_refund_offset: 그 줄에 앞서 금액만 돌려준 운영 환불을 이 줄 환불에서 뺀 몫(결정 71 「줄 금액 − 줄 기환불」).
CREATE TABLE group_buy_request_items (
    id                    bigint        PRIMARY KEY,
    request_id            bigint        NOT NULL REFERENCES group_buy_requests (id) ON DELETE CASCADE,
    participation_item_id bigint        NOT NULL REFERENCES group_buy_participation_items (id),
    sku_id                bigint        NOT NULL,
    qty                   integer       NOT NULL,
    reason                varchar(20)   NOT NULL,
    refund_amount         bigint,
    prior_refund_offset   bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_gbri_qty CHECK (qty > 0),
    CONSTRAINT ck_gbri_reason CHECK (reason IN ('CHANGE_OF_MIND', 'DEFECT', 'WRONG_ITEM', 'DAMAGED', 'OTHER')),
    CONSTRAINT ck_gbri_offset CHECK (prior_refund_offset >= 0),
    CONSTRAINT ux_gbri_item UNIQUE (request_id, participation_item_id)
);
CREATE INDEX ix_gbri_item ON group_buy_request_items (participation_item_id);

-- 반품 처리 방법(결정 15): 줄마다 방법별 수량, 합 = 그 줄 수량(앱). RESELL 은 공구 모집 중 + 단순 변심 줄만(앱 — GROUP_BUY_NOT_RESELLABLE).
CREATE TABLE group_buy_return_dispositions (
    id              bigint        PRIMARY KEY,
    request_item_id bigint        NOT NULL REFERENCES group_buy_request_items (id) ON DELETE CASCADE,
    disposition     varchar(20)   NOT NULL,
    qty             integer       NOT NULL,
    created_by      bigint,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ck_gbrd_disposition CHECK (disposition IN ('RESELL', 'DISCARD', 'RETURN_TO_SUPPLIER', 'KEEP')),
    CONSTRAINT ck_gbrd_qty CHECK (qty > 0),
    CONSTRAINT ux_gbrd UNIQUE (request_item_id, disposition)
);

-- 요청 사진(하자·오배송 회원 사진 · 반품 거절 근거 사진 — 각 최대 3장, 결정 55): 요청 없이 먼저 올리고 붙인다. 붙지 않은 사진은 24시간 뒤 정리(C 195).
CREATE TABLE group_buy_request_photos (
    id           bigint        PRIMARY KEY,
    request_id   bigint        REFERENCES group_buy_requests (id) ON DELETE CASCADE,
    owner_kind   varchar(10)   NOT NULL,
    owner_id     bigint        NOT NULL,
    image_key    varchar(300)  NOT NULL,
    created_at   timestamptz   NOT NULL,
    attached_at  timestamptz,
    CONSTRAINT ck_gbrp_owner CHECK (owner_kind IN ('MEMBER', 'OPERATOR')),
    CONSTRAINT ck_gbrp_attached CHECK ((request_id IS NULL) = (attached_at IS NULL))
);
CREATE INDEX ix_gbrp_request ON group_buy_request_photos (request_id);
CREATE INDEX ix_gbrp_loose ON group_buy_request_photos (created_at) WHERE request_id IS NULL;

-- ── 배송지 열람·명단 내보내기 기록(commerce.md 10-9) — 평문을 싣지 않는다 ─────────────────────────────
-- kind REVEAL(주문 하나 열람 — participation_id) · EXPORT(공구의 배송 명단 — row_count).
CREATE TABLE commerce_address_views (
    id               bigint        PRIMARY KEY,
    kind             varchar(10)   NOT NULL,
    participation_id bigint        REFERENCES group_buy_participations (id),
    group_buy_id     bigint        NOT NULL,
    operator_id      bigint        NOT NULL,
    reason           varchar(200)  NOT NULL,
    row_count        integer,
    created_at       timestamptz   NOT NULL,
    CONSTRAINT ck_cav_kind CHECK ((kind = 'REVEAL' AND participation_id IS NOT NULL) OR (kind = 'EXPORT' AND row_count IS NOT NULL AND row_count >= 0)),
    CONSTRAINT ck_cav_reason CHECK (length(btrim(reason)) BETWEEN 5 AND 200)
);
CREATE INDEX ix_cav_participation ON commerce_address_views (participation_id, created_at) WHERE participation_id IS NOT NULL;
CREATE INDEX ix_cav_group ON commerce_address_views (group_buy_id, created_at);

-- ── 계좌 송금(결정 6·39·67 — 9-3 과 같은 방식, 3단계 충전 환불이 이어 쓴다) ─────────────────────────────
-- source_kind GROUP_BUY_REFUND(PG 로 못 돌려준 공구 환불 — 환불 기록 ID) · GROUP_BUY_COMPENSATION(보상액 중 남은 결제액을 넘는 몫 — 환불 기록 ID).
-- status AWAITING_ACCOUNT(계좌 받기 전) → READY(계좌 있음) → SENT(송금 완료 — 계좌 암호문을 지운다, account_erased_at). 계좌 암호문은 DataCipher(용도 commerce.bank.*).
CREATE TABLE commerce_bank_transfers (
    id                 bigint        PRIMARY KEY,
    source_kind        varchar(30)   NOT NULL,
    source_id          bigint        NOT NULL,
    participation_id   bigint        REFERENCES group_buy_participations (id),
    user_id            bigint        NOT NULL,
    amount             bigint        NOT NULL,
    status             varchar(20)   NOT NULL,
    bank_enc           varchar(500),
    account_no_enc     varchar(500),
    holder_enc         varchar(500),
    account_erased_at  timestamptz,
    reason             varchar(200)  NOT NULL,
    created_by         bigint,
    created_at         timestamptz   NOT NULL,
    sent_by            bigint,
    sent_at            timestamptz,
    CONSTRAINT ck_cbt_source CHECK (source_kind IN ('GROUP_BUY_REFUND', 'GROUP_BUY_COMPENSATION')),
    CONSTRAINT ck_cbt_amount CHECK (amount > 0),
    CONSTRAINT ck_cbt_status CHECK (status IN ('AWAITING_ACCOUNT', 'READY', 'SENT')),
    CONSTRAINT ck_cbt_account CHECK ((status = 'READY') = (account_no_enc IS NOT NULL) AND (status <> 'SENT' OR (sent_at IS NOT NULL AND account_erased_at IS NOT NULL)))
);
CREATE UNIQUE INDEX ux_cbt_source ON commerce_bank_transfers (source_kind, source_id);
CREATE INDEX ix_cbt_status ON commerce_bank_transfers (status, created_at);

-- ── 단품 공급 불가(결정 53) · 회원 선택 ─────────────────────────────────────────────────────────────
CREATE TABLE group_buy_supply_failures (
    id           bigint        PRIMARY KEY,
    group_buy_id bigint        NOT NULL REFERENCES group_buys (id),
    sku_id       bigint        NOT NULL,
    reason       varchar(500)  NOT NULL,
    choice_until timestamptz   NOT NULL,
    operator_id  bigint,
    created_at   timestamptz   NOT NULL,
    CONSTRAINT fk_gbsf_sku FOREIGN KEY (sku_id, group_buy_id) REFERENCES group_buy_skus (id, group_buy_id),
    CONSTRAINT ux_gbsf_sku UNIQUE (sku_id)
);

-- choice KEEP_REST 나머지만 받기 · CANCEL_ALL 전체 취소. 기한까지 답이 없으면 KEEP_REST(defaulted). 기한 전 답 없는 주문은 발송하지 않는다.
CREATE TABLE group_buy_supply_choices (
    id                bigint        PRIMARY KEY,
    supply_failure_id bigint        NOT NULL REFERENCES group_buy_supply_failures (id),
    participation_id  bigint        NOT NULL REFERENCES group_buy_participations (id),
    user_id           bigint        NOT NULL,
    choice            varchar(15),
    deadline          timestamptz   NOT NULL,
    decided_at        timestamptz,
    defaulted         boolean       NOT NULL DEFAULT false,
    created_at        timestamptz   NOT NULL,
    CONSTRAINT ck_gbsc_choice CHECK ((choice IS NULL) = (decided_at IS NULL) AND (choice IS NULL OR choice IN ('KEEP_REST', 'CANCEL_ALL'))
        AND (NOT defaulted OR choice = 'KEEP_REST')),
    CONSTRAINT ux_gbsc UNIQUE (supply_failure_id, participation_id)
);
CREATE INDEX ix_gbsc_open ON group_buy_supply_choices (deadline) WHERE choice IS NULL;
CREATE INDEX ix_gbsc_participation ON group_buy_supply_choices (participation_id);

-- ── 운영 환불 승인 대기(결정 70) — 고액 문턱에 닿았는데 고액 권한이 없으면 여기에 올려 권한자가 승인 ────────────────────
-- kind PRE_SHIPPING(발송 전 — 줄·수량) · AMOUNT_ONLY(배송완료 — 금액만). status AWAITING_APPROVAL → APPROVED(refund_id) / REJECTED.
CREATE TABLE group_buy_admin_refunds (
    id               bigint        PRIMARY KEY,
    participation_id bigint        NOT NULL REFERENCES group_buy_participations (id),
    kind             varchar(15)   NOT NULL,
    amount           bigint        NOT NULL,
    lines            jsonb,
    seller_fault     boolean       NOT NULL DEFAULT false,
    reason           varchar(200)  NOT NULL,
    status           varchar(20)   NOT NULL,
    requested_by     bigint        NOT NULL,
    decided_by       bigint,
    refund_id        bigint,
    created_at       timestamptz   NOT NULL,
    decided_at       timestamptz,
    CONSTRAINT ck_gbar_kind CHECK (kind IN ('PRE_SHIPPING', 'AMOUNT_ONLY')),
    CONSTRAINT ck_gbar_status CHECK (status IN ('AWAITING_APPROVAL', 'APPROVED', 'REJECTED') AND ((status = 'AWAITING_APPROVAL') = (decided_at IS NULL))),
    CONSTRAINT ck_gbar_amount CHECK (amount >= 0)
);
CREATE UNIQUE INDEX ux_gbar_waiting ON group_buy_admin_refunds (participation_id) WHERE status = 'AWAITING_APPROVAL';

-- ── 환불 기록 보강(결정 68·75) ─────────────────────────────────────────────────────────────────
-- fee_covered: 반품비·재부과가 상품 환불보다 커서 반품비 추가 결제로 메운 몫(+ — 주문 결제에서 그만큼 더 돌려준다). fee_refund: 추가 결제의 남는 몫 환불(원인 RETURN_FEE_REFUND, 그 결제에서 취소).
-- operator_amount: 운영자가 금액을 정한 환불(운영 환불·보상) — 고액 문턱 누계(결정 70).
ALTER TABLE group_buy_refunds ADD COLUMN fee_covered bigint NOT NULL DEFAULT 0;
ALTER TABLE group_buy_refunds ADD COLUMN fee_refund bigint NOT NULL DEFAULT 0;
ALTER TABLE group_buy_refunds ADD COLUMN operator_amount bigint NOT NULL DEFAULT 0;
ALTER TABLE group_buy_refunds DROP CONSTRAINT ck_gbr_sum;
ALTER TABLE group_buy_refunds ADD CONSTRAINT ck_gbr_sum CHECK (amount = item_refund + shipping_refund - return_fee - reshipping_fee + reshipping_refund + compensation_pg
    + fee_covered + fee_refund);
ALTER TABLE group_buy_refunds ADD CONSTRAINT ck_gbr_fee CHECK (fee_covered >= 0 AND fee_refund >= 0 AND operator_amount >= 0
    AND (fee_refund = 0 OR cause = 'RETURN_FEE_REFUND'));
CREATE INDEX ix_gbr_request ON group_buy_refunds (request_id) WHERE request_id IS NOT NULL;

-- 발송 처리 기록: 주문 줄의 교환 출고 수량(판매 수량 불변 — 기록만, 결정 54)
ALTER TABLE group_buy_participation_items ADD COLUMN exchanged_qty integer NOT NULL DEFAULT 0;
ALTER TABLE group_buy_participation_items ADD CONSTRAINT ck_gbpi_exchanged CHECK (exchanged_qty >= 0);

-- ── 결제 모듈 소폭(결정 16·68·74) ─────────────────────────────────────────────────────────────────
ALTER TABLE payment_orders DROP CONSTRAINT ck_payment_orders_type;
ALTER TABLE payment_orders ADD CONSTRAINT ck_payment_orders_type CHECK (order_type IN ('POINT_CHARGE', 'GROUP_BUY', 'GROUP_BUY_RETURN_FEE', 'PAYMENT_TEST'));
-- 수동 처리 방법: CONSOLE_CANCEL(포트원 콘솔에서 취소 — 취소 ID 를 붙이고 취소 누계까지 올린다) · BANK_TRANSFER(계좌 송금 — 누계는 그대로, 불변식·대사가 따로 뺀다). null = 표시만(2a-3).
ALTER TABLE payment_cancels ADD COLUMN manual_method varchar(20);
ALTER TABLE payment_cancels ADD CONSTRAINT ck_payment_cancels_manual_method CHECK (manual_method IS NULL OR (status = 'MANUAL' AND manual_method IN ('CONSOLE_CANCEL', 'BANK_TRANSFER')));
