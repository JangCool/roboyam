-- 사업자 신청 건(광고·핫딜 — 사전 심사 → 승인 → 온라인 결제 또는 수동 결제(계약서 사본 + 입금 확인) → 예약). docs/features/biz-application-contract.md,
-- docs/plans/biz-ad-hotdeal/00-overview.md ★-1~★-5, docs/domain/commerce.md 8·15·18절.
--   · 요금표(biz_price_rules — 자리 하나에 한 줄) · 신청 건(biz_applications) · 자리를 차지하는 줄 + 금액 줄(biz_application_items — 한도 엔진 하나)
--   · 소재 버전(biz_creative_versions — 옛 버전 지우지 않음) · 계약서 사본(biz_contract_files — DB bytea 암호문, 지우지 않음) · 입금 확인(biz_deposits)
--   · 환불(biz_refunds) · 처리 기록(biz_application_events)
--   · 결제 주문 종류 BIZ_APPLICATION · 회원 알림 종류 BIZ_APPLICATION · 파일 용도 BIZ_AD_IMAGE(광고 이미지 — 공개 노출이라 /media)
-- 새 표는 결제 모듈 표에 외래키를 걸지 않는다(commerce.md 3절 — 결제는 order_type + order_ref_id 로 가리킨다). 금액 bigint(원), 시각 timestamptz, ID Snowflake.
-- 기간은 반열림 [start_at, end_at) 이고 분 단위(초·소수 0)다. 화면 단계(예약·진행·종료)는 SCHEDULED 안에서 시각으로 계산한다(저장하지 않는다).

-- ── 요금표 — 상품(자리) 하나에 한 줄. 하루 단가(DAY) × 일수 또는 건당(POST). capacity = 같은 시각에 함께 나갈 수 있는 건수(POST 는 한도 없음) ──
CREATE TABLE biz_price_rules (
    product        varchar(20)   PRIMARY KEY,
    unit           varchar(4)    NOT NULL,
    unit_price     bigint        NOT NULL,
    capacity       integer,
    max_days       integer       NOT NULL,
    max_lead_days  integer       NOT NULL,
    accepting      boolean       NOT NULL DEFAULT true,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     bigint,
    CONSTRAINT ck_bpr_product CHECK (product IN ('WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY',
                                                 'HOTDEAL_POST')),
    CONSTRAINT ck_bpr_unit CHECK (unit IN ('DAY', 'POST')),
    CONSTRAINT ck_bpr_price CHECK (unit_price >= 0),
    CONSTRAINT ck_bpr_capacity CHECK ((unit = 'POST' AND capacity IS NULL) OR (unit = 'DAY' AND capacity >= 1)),
    CONSTRAINT ck_bpr_days CHECK (max_days >= 1 AND max_lead_days >= 1)
);

-- 시드 = user-web 목업 값(가정 — 관리자 목업과 단가가 달라 공개 화면 쪽을 택함). 요금표 편집 API 는 후속
INSERT INTO biz_price_rules (product, unit, unit_price, capacity, max_days, max_lead_days) VALUES
    ('WIDE_BANNER',      'DAY',  50000, 2, 14, 60),
    ('STRIP_BANNER',     'DAY',  30000, 3, 14, 60),
    ('SQUARE_BANNER',    'DAY',  20000, 4, 30, 60),
    ('SMALL_BANNER',     'DAY',  10000, 6, 30, 60),
    ('TEXT_LINE',        'DAY',   5000, 5, 30, 60),
    ('HOTDEAL_TOP',      'DAY',  20000, 3,  7, 60),
    ('HOTDEAL_CATEGORY', 'DAY',  10000, 2,  7, 60),
    ('HOTDEAL_POST',     'POST', 30000, NULL, 30, 60);

-- ── 신청 건 ────────────────────────────────────────────────────────────────────────────────────────────────
CREATE SEQUENCE biz_application_no_seq;

CREATE TABLE biz_applications (
    id                       bigint        PRIMARY KEY,
    -- 화면에 보이는 번호(BA-2026-00001) — 한국 연도 + 일련번호
    application_no           varchar(20)   NOT NULL,
    kind                     varchar(20)   NOT NULL,
    status                   varchar(20)   NOT NULL,
    -- BUSINESS = 사업자가 신청 · OPERATOR = 운영자 직접 등록(계정 없는 광고주 가능 — 수동 결제로 시작, 등록 API 는 광고 일정표 세션)
    source                   varchar(10)   NOT NULL,
    business_profile_id      bigint        REFERENCES business_profiles (id),
    user_id                  bigint,
    advertiser_name          varchar(60),
    -- 거래 근거 스냅샷(신청 때의 상호·등록번호 끝 4자리 — 5년 보관)
    company_name             varchar(60),
    biz_no_last4             char(4),
    title                    varchar(100)  NOT NULL,
    start_at                 timestamptz   NOT NULL,
    end_at                   timestamptz   NOT NULL,
    preferred_method         varchar(6)    NOT NULL,
    -- 승인 때 확정한 결제 방식(승인 전 null)
    payment_method           varchar(6),
    -- 요금표 계산(신청·기간 변경 때마다 다시 계산)
    quote_supply             bigint        NOT NULL,
    quote_vat                bigint        NOT NULL,
    quote_total              bigint        NOT NULL,
    -- 승인 확정 스냅샷(결제 금액 = approved_total). 조정했으면 사유
    approved_supply          bigint,
    approved_vat             bigint,
    approved_total           bigint,
    adjust_reason            varchar(200),
    approved_at              timestamptz,
    approved_by              bigint,
    payment_deadline_at      timestamptz,
    deposit_deadline_at      timestamptz,
    paid_at                  timestamptz,
    payment_order_id         bigint,
    -- 대체되지 않은 계약서 사본 수(서비스가 같은 트랜잭션에서 맞춘다) · 입금 확인 시각 — 수동 확정 CHECK 가 본다
    active_contract_count    integer       NOT NULL DEFAULT 0,
    deposit_confirmed_at     timestamptz,
    reject_code              varchar(20),
    reject_text              varchar(200),
    -- 내림(사업자 정지 SUSPENDED · 긴급 내림 EMERGENCY)
    taken_down_at            timestamptz,
    takedown_cause           varchar(10),
    takedown_text            varchar(200),
    -- 정책 동의(H4 — 광고 정책·환불 규정 버전). 사업자 신청 건은 필수
    ad_policy_version        varchar(20),
    refund_policy_version    varchar(20),
    agreed_at                timestamptz,
    -- 알림 보낸 표시(배치가 한 번만)
    payment_due_notified_at  timestamptz,
    start_notified_at        timestamptz,
    end_notified_at          timestamptz,
    closed_at                timestamptz,
    version                  integer       NOT NULL DEFAULT 0,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    CONSTRAINT ux_biz_applications_no UNIQUE (application_no),
    CONSTRAINT ck_ba_kind CHECK (kind IN ('AD_BANNER', 'AD_TEXT_LINE', 'HOTDEAL_POST', 'HOTDEAL_PLACEMENT')),
    CONSTRAINT ck_ba_status CHECK (status IN ('SUBMITTED', 'REJECTED', 'CANCELED', 'AWAITING_PAYMENT', 'EXPIRED', 'AWAITING_DEPOSIT', 'SCHEDULED', 'TAKEN_DOWN')),
    CONSTRAINT ck_ba_source CHECK (source IN ('BUSINESS', 'OPERATOR')),
    CONSTRAINT ck_ba_source_business CHECK (source <> 'BUSINESS' OR (business_profile_id IS NOT NULL AND user_id IS NOT NULL AND company_name IS NOT NULL
                                                                      AND biz_no_last4 IS NOT NULL AND ad_policy_version IS NOT NULL
                                                                      AND refund_policy_version IS NOT NULL AND agreed_at IS NOT NULL)),
    CONSTRAINT ck_ba_source_operator CHECK (source <> 'OPERATOR' OR business_profile_id IS NOT NULL OR advertiser_name IS NOT NULL),
    CONSTRAINT ck_ba_period CHECK (end_at > start_at AND date_trunc('minute', start_at) = start_at AND date_trunc('minute', end_at) = end_at),
    CONSTRAINT ck_ba_methods CHECK (preferred_method IN ('ONLINE', 'MANUAL') AND (payment_method IS NULL OR payment_method IN ('ONLINE', 'MANUAL'))),
    CONSTRAINT ck_ba_quote CHECK (quote_supply >= 0 AND quote_vat >= 0 AND quote_total = quote_supply + quote_vat),
    CONSTRAINT ck_ba_approved_amount CHECK (approved_total IS NULL OR (approved_supply >= 1 AND approved_vat >= 0 AND approved_total = approved_supply + approved_vat)),
    -- 승인 뒤 상태면 승인 스냅샷이 있다
    CONSTRAINT ck_ba_approved CHECK (status NOT IN ('AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'SCHEDULED', 'TAKEN_DOWN')
                                     OR (payment_method IS NOT NULL AND approved_total IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT ck_ba_awaiting_payment CHECK (status <> 'AWAITING_PAYMENT' OR (payment_method = 'ONLINE' AND payment_deadline_at IS NOT NULL)),
    CONSTRAINT ck_ba_awaiting_deposit CHECK (status <> 'AWAITING_DEPOSIT' OR (payment_method = 'MANUAL' AND deposit_deadline_at IS NOT NULL)),
    -- 수동 확정(★-2 · F3): 예약(·내림)인 수동 건은 대체되지 않은 계약서 1장 이상 + 입금 확인이 둘 다 있다 — 서비스가 409 BIZ_DEPOSIT_INCOMPLETE 로 먼저 막고, DB 가 한 번 더 막는다
    CONSTRAINT ck_ba_manual_confirmed CHECK (status NOT IN ('SCHEDULED', 'TAKEN_DOWN') OR payment_method <> 'MANUAL'
                                             OR (active_contract_count >= 1 AND deposit_confirmed_at IS NOT NULL)),
    -- 온라인 확정: 예약(·내림)인 온라인 건은 결제 완료(결제 시각·결제 주문)가 있다
    CONSTRAINT ck_ba_online_paid CHECK (status NOT IN ('SCHEDULED', 'TAKEN_DOWN') OR payment_method <> 'ONLINE'
                                        OR (paid_at IS NOT NULL AND payment_order_id IS NOT NULL)),
    CONSTRAINT ck_ba_contract_count CHECK (active_contract_count >= 0),
    CONSTRAINT ck_ba_rejected CHECK (status <> 'REJECTED' OR (reject_code IS NOT NULL AND reject_text IS NOT NULL)),
    CONSTRAINT ck_ba_reject_code CHECK (reject_code IS NULL OR reject_code IN ('CREATIVE_SPEC', 'POLICY_VIOLATION', 'LINK_BROKEN', 'PERIOD_UNAVAILABLE',
                                                                               'FALSE_PRICE', 'OTHER')),
    CONSTRAINT ck_ba_takedown CHECK (status <> 'TAKEN_DOWN' OR (taken_down_at IS NOT NULL AND takedown_cause IS NOT NULL)),
    CONSTRAINT ck_ba_takedown_cause CHECK (takedown_cause IS NULL OR takedown_cause IN ('SUSPENDED', 'EMERGENCY'))
);
-- 사업자의 내 신청 건 · 관리자 큐(상태·기한) · 훅(사업자별) · 배치(결제 기한·시작·끝)
CREATE INDEX ix_ba_user ON biz_applications (user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX ix_ba_profile ON biz_applications (business_profile_id, status) WHERE business_profile_id IS NOT NULL;
CREATE INDEX ix_ba_status ON biz_applications (status, created_at);
CREATE INDEX ix_ba_payment_deadline ON biz_applications (payment_deadline_at, id) WHERE status = 'AWAITING_PAYMENT';
CREATE INDEX ix_ba_scheduled ON biz_applications (start_at, end_at) WHERE status = 'SCHEDULED';

-- ── 자리를 차지하는 줄 + 금액 줄 — 모든 종류가 쓴다(배너·한 줄 = 그 자리 1줄, 핫딜 글 = POST 1줄 + 고른 노출 1줄, 핫딜 노출 = 노출 1줄).
--    한도 판정 = 같은 상품의 「자리를 차지하는 상태」(AWAITING_PAYMENT·AWAITING_DEPOSIT·SCHEDULED·TAKEN_DOWN) 신청 건 줄 중 겹치는 줄의 최대 동시 겹침 수 < capacity ──
CREATE TABLE biz_application_items (
    id              bigint        PRIMARY KEY,
    application_id  bigint        NOT NULL REFERENCES biz_applications (id),
    product         varchar(20)   NOT NULL REFERENCES biz_price_rules (product),
    unit_price      bigint        NOT NULL,
    units           integer       NOT NULL,
    supply          bigint        NOT NULL,
    start_at        timestamptz   NOT NULL,
    end_at          timestamptz   NOT NULL,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ux_biz_items UNIQUE (application_id, product),
    CONSTRAINT ck_bai_amount CHECK (unit_price >= 0 AND units >= 1 AND supply = unit_price * units),
    CONSTRAINT ck_bai_period CHECK (end_at > start_at)
);
CREATE INDEX ix_bai_product ON biz_application_items (product, start_at, end_at);

-- ── 소재 버전(A2) — 1부터, 바꾸면 새 버전(옛 버전 지우지 않음). 승인 때 그 버전에 approved_at ──
CREATE TABLE biz_creative_versions (
    id               bigint        PRIMARY KEY,
    application_id   bigint        NOT NULL REFERENCES biz_applications (id),
    version          integer       NOT NULL,
    content          jsonb         NOT NULL,
    created_by_type  varchar(10)   NOT NULL,
    created_by       bigint,
    created_at       timestamptz   NOT NULL,
    approved_at      timestamptz,
    CONSTRAINT ux_biz_creatives UNIQUE (application_id, version),
    CONSTRAINT ck_bcv_version CHECK (version >= 1),
    CONSTRAINT ck_bcv_by CHECK (created_by_type IN ('MEMBER', 'OPERATOR'))
);

-- ── 계약서 사본(★-2) — 사업자등록증처럼 DB 의 암호문(DataCipher, 용도 biz.contract), 형식은 내용 첫 바이트로(PDF·JPEG·PNG), 10MB. 지우지 않는다 —
--    바꾸면 새 파일을 더하고 옛 파일은 「대체됨」(replaced_*). 보존 5년(법무 확인) ──
CREATE TABLE biz_contract_files (
    id                    bigint        PRIMARY KEY,
    application_id        bigint        NOT NULL REFERENCES biz_applications (id),
    file_name             varchar(120)  NOT NULL,
    content_type          varchar(40)   NOT NULL,
    size_bytes            integer       NOT NULL,
    content               bytea         NOT NULL,
    sha256                char(64)      NOT NULL,
    uploaded_by           bigint,
    uploaded_at           timestamptz   NOT NULL,
    replaced_at           timestamptz,
    replaced_by           bigint,
    replaced_by_file_id   bigint        REFERENCES biz_contract_files (id),
    CONSTRAINT ck_bcf_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_bcf_size CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_bcf_replaced CHECK (replaced_at IS NULL OR replaced_by_file_id IS NOT NULL)
);
CREATE INDEX ix_bcf_application ON biz_contract_files (application_id, uploaded_at);

-- ── 입금 확인(★-2) — 신청 건당 한 줄(고치면 덮고 처리 기록·감사에 남긴다). 입금액 ≠ 확정 금액이면 사유 ──
CREATE TABLE biz_deposits (
    application_id    bigint        PRIMARY KEY REFERENCES biz_applications (id),
    amount            bigint        NOT NULL,
    deposited_on      date          NOT NULL,
    depositor_name    varchar(60)   NOT NULL,
    mismatch_reason   varchar(200),
    confirmed_by      bigint        NOT NULL,
    confirmed_at      timestamptz   NOT NULL,
    CONSTRAINT ck_bd_amount CHECK (amount >= 1)
);

-- ── 환불(H1·H2) — 시작 전 전액 자동(FULL_AUTO) · 진행 중 일할 심사(PRORATED) · 정지로 내린 진행 중 건의 일할 심사(SUSPEND_PRORATED).
--    온라인은 결제 모듈 취소(payment_cancel_id), 수동은 운영자 송금 표시(transfer_status). 신청 건의 refund = 가장 최근 줄 ──
CREATE TABLE biz_refunds (
    id                 bigint        PRIMARY KEY,
    application_id     bigint        NOT NULL REFERENCES biz_applications (id),
    type               varchar(20)   NOT NULL,
    status             varchar(10)   NOT NULL,
    method             varchar(6)    NOT NULL,
    paid_amount        bigint        NOT NULL,
    proposed_amount    bigint        NOT NULL,
    used_days          integer       NOT NULL,
    total_days         integer       NOT NULL,
    approved_amount    bigint,
    reason             varchar(300),
    decision_reason    varchar(200),
    payment_cancel_id  bigint,
    transfer_status    varchar(10),
    transferred_at     timestamptz,
    transferred_by     bigint,
    transfer_note      varchar(200),
    -- 돈이 돌아간 시각(온라인 PG 취소 성공 · 수동 송금 완료 · 0원 승인)
    completed_at       timestamptz,
    requested_at       timestamptz   NOT NULL,
    decided_at         timestamptz,
    decided_by         bigint,
    CONSTRAINT ck_br_type CHECK (type IN ('FULL_AUTO', 'PRORATED', 'SUSPEND_PRORATED')),
    CONSTRAINT ck_br_status CHECK (status IN ('REQUESTED', 'APPROVED', 'DENIED')),
    CONSTRAINT ck_br_method CHECK (method IN ('ONLINE', 'MANUAL')),
    CONSTRAINT ck_br_amounts CHECK (paid_amount >= 0 AND proposed_amount BETWEEN 0 AND paid_amount
                                    AND (approved_amount IS NULL OR approved_amount BETWEEN 0 AND paid_amount)),
    CONSTRAINT ck_br_days CHECK (total_days >= 1 AND used_days BETWEEN 0 AND total_days),
    CONSTRAINT ck_br_approved CHECK (status <> 'APPROVED' OR approved_amount IS NOT NULL),
    CONSTRAINT ck_br_transfer CHECK (transfer_status IS NULL OR (method = 'MANUAL' AND transfer_status IN ('PENDING', 'DONE'))),
    CONSTRAINT ck_br_transfer_done CHECK (transfer_status IS DISTINCT FROM 'DONE' OR transferred_at IS NOT NULL)
);
-- 열린 환불(심사 대기)은 신청 건당 하나
CREATE UNIQUE INDEX ux_biz_refunds_open ON biz_refunds (application_id) WHERE status = 'REQUESTED';
CREATE INDEX ix_br_application ON biz_refunds (application_id, requested_at DESC);
CREATE INDEX ix_br_cancel ON biz_refunds (payment_cancel_id) WHERE payment_cancel_id IS NOT NULL;

-- ── 처리 기록 — action 코드(화면이 번역) · 행위자 종류 · 메모 300자(개인정보 없음). 계약서 열람도 여기(+ 감사) ──
CREATE TABLE biz_application_events (
    id              bigint        PRIMARY KEY,
    application_id  bigint        NOT NULL REFERENCES biz_applications (id),
    action          varchar(30)   NOT NULL,
    actor_type      varchar(10)   NOT NULL,
    actor_id        bigint,
    note            varchar(300),
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ck_bae_actor CHECK (actor_type IN ('MEMBER', 'OPERATOR', 'SYSTEM'))
);
CREATE INDEX ix_bae_application ON biz_application_events (application_id, created_at, id);

-- ── 결제 주문 종류: BIZ_APPLICATION(대상 = 신청 건). V102 목록 ∪ BIZ_APPLICATION ──
ALTER TABLE payment_orders DROP CONSTRAINT ck_payment_orders_type;
ALTER TABLE payment_orders ADD CONSTRAINT ck_payment_orders_type CHECK (order_type IN ('POINT_CHARGE', 'GROUP_BUY', 'GROUP_BUY_RETURN_FEE', 'PAYMENT_TEST',
                                                                                      'BIZ_APPLICATION'));

-- ── 파일 용도: BIZ_AD_IMAGE(사업자 광고 이미지 — 회원이 올린다, 공개 노출이라 /media). V75 목록 ∪ BIZ_AD_IMAGE ──
ALTER TABLE files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE files ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO', 'POST_IMAGE', 'HIGHLIGHT_IMAGE', 'BIZ_AD_IMAGE'));

-- ── 회원 알림 종류: BIZ_APPLICATION(덧붙임 kind — 접수·승인·반려·결제 완료·기한 임박·시작·종료 임박·중단·환불 등). V111 목록 ∪ BIZ_APPLICATION.
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다) ──
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED',
                    'GROUP_POST_UNPUBLISHED',
                    'GROUP_MANAGER_APPOINTED', 'GROUP_MANAGER_PERMISSIONS_CHANGED', 'GROUP_MANAGER_REMOVED', 'GROUP_MANAGER_RESIGNED',
                    'GROUP_KICKED_BY_MANAGER',
                    'GROUP_EVENT',
                    'GROUP_PUBLISH_REQUESTED', 'GROUP_PUBLISH_APPROVED', 'GROUP_PUBLISH_REJECTED', 'GROUP_POST_PUBLISHED', 'GROUP_VISIBILITY_CHANGED',
                    'GROUP_POST_MEMBERS_ONLY', 'GROUP_POST_RELEASED',
                    'GROUP_BUY',
                    'BUSINESS',
                    'BIZ_APPLICATION'));
