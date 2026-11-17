-- 체험단 · 사용기 구입 경로(docs/features/trial-group-contract.md 「구현 결정」 1~14, 2026-11-17)
--
-- 광고주가 제품을 대고 회원이 사용기를 쓰는 체험단을 광고 상품(TRIAL)으로 연다. 사이트는 제품값·보증금을 받지 않는다(결제·정산 없음) — 광고 상품 금액만 광고 신청 건에 남긴다.
-- 개인정보: 신청 때 고른 배송지는 신청 줄에 암호문 사본(DataCipher, 용도 trial.address.* — 공구 참여 사본과 같은 방식)으로 두고, 반납 확인(제공형은 받음) 뒤 파기 배치가 지운다.
-- 구매 인증 사진은 비공개 — DataCipher.encryptBytes bytea(사업자 서류와 같은 방식), 확인 뒤 설정 일수(기본 30일)가 지나면 파기.
-- users 외래키를 걸지 않는다(포인트·커머스 새 표 규칙 — 탈퇴 정리는 회원 쪽이 한다). 체험단 표 안쪽만 외래키로 잇는다.

-- ── 1) 광고 상품 TRIAL — 요금표·가격 기간표에 값 매기는 단위·1명당 발송비 칸 ──────────────────────────────
-- price_unit: PER_RECRUIT(모집 건당) · PER_PERSON(뽑힌 1명당, 기본). shipping_fee_per_person: 운영자가 대신 보낼 때만 1명당 더한다(기본 0).
-- 두 칸은 TRIAL 만 쓰고 다른 상품은 NULL. 가격 기간표 줄은 NULL 이면 「직전 값 그대로」(예전 예약 경로가 두 칸을 모르고 넣어도 깨지지 않게).
ALTER TABLE biz_price_rules DROP CONSTRAINT ck_bpr_product;
ALTER TABLE biz_price_rules ADD CONSTRAINT ck_bpr_product CHECK (product IN ('WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'HALF_BANNER', 'WING_BANNER',
                                                                         'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY', 'HOTDEAL_POST', 'TRIAL'));
ALTER TABLE biz_price_rules
    ADD COLUMN price_unit              varchar(12),
    ADD COLUMN shipping_fee_per_person bigint;
ALTER TABLE biz_price_rules ADD CONSTRAINT ck_bpr_trial_terms CHECK (
    (product = 'TRIAL') = (price_unit IS NOT NULL AND shipping_fee_per_person IS NOT NULL)
    AND (price_unit IS NULL OR price_unit IN ('PER_RECRUIT', 'PER_PERSON'))
    AND (shipping_fee_per_person IS NULL OR shipping_fee_per_person >= 0));
ALTER TABLE biz_price_schedules
    ADD COLUMN price_unit              varchar(12),
    ADD COLUMN shipping_fee_per_person bigint;
ALTER TABLE biz_price_schedules ADD CONSTRAINT ck_bps_trial_terms CHECK (
    (product = 'TRIAL' OR (price_unit IS NULL AND shipping_fee_per_person IS NULL))
    AND (price_unit IS NULL OR price_unit IN ('PER_RECRUIT', 'PER_PERSON'))
    AND (shipping_fee_per_person IS NULL OR shipping_fee_per_person >= 0));
-- 처음 가격 0원 · 인원당 · 발송비 0원. 건당 상품(POST — 한도 없음)으로 둔다. 최대 일수·시작일 범위는 쓰지 않지만 NOT NULL 이라 1·365.
INSERT INTO biz_price_rules (product, unit, unit_price, capacity, max_days, max_lead_days, accepting, price_unit, shipping_fee_per_person) VALUES
    ('TRIAL', 'POST', 0, NULL, 1, 365, true, 'PER_PERSON', 0);

-- 모집 = 광고 신청 건(kind TRIAL) 한 건과 1:1. 운영자 등록(OPERATOR)으로 SUBMITTED 에 머물고 광고 심사 큐에는 보이지 않는다(앱 — BizDomain 이 TRIAL 을 빼고 센다).
ALTER TABLE biz_applications DROP CONSTRAINT ck_ba_kind;
ALTER TABLE biz_applications ADD CONSTRAINT ck_ba_kind CHECK (kind IN ('AD_BANNER', 'AD_TEXT_LINE', 'HOTDEAL_POST', 'HOTDEAL_PLACEMENT', 'TRIAL'));

-- ── 2) 체험단 설정(행 하나) ───────────────────────────────────────────────────────────────────
-- give_price_limit: 제공형(받아서 가짐) 제품값 상한(원, 기본 5만) — 넘으면 대여형으로만. disclosure_text: 체험단 사용기 표시 상자 문구({광고주}·{제공/대여} 자리표시),
-- sponsor_disclosure_text: 협찬 사용기 표시 상자 문구. return_remind_first_days·second_days: 반납 기한 당일(0)·7일 뒤 알림, unreturned_days: 이 일수가 지나면 미반납 확정(14).
-- review_days: 사용기 기한 기본(14 — 모집 만들기의 처음 값), late_ban_days: 사용기 기한 넘김 신청 금지 일수(90), late_revoke_strikes: 이 횟수면 리뷰어 칭호 정지(2).
-- proof_bonus_points: 구매 인증 승인 보너스(100P), proof_photo_days: 구매 인증 사진 보관(확인 뒤 30일), stock_keep_days: 입고 뒤 보관 기한(60일).
CREATE TABLE trial_settings (
    id                         integer       PRIMARY KEY,
    give_price_limit           bigint        NOT NULL DEFAULT 50000,
    disclosure_text            varchar(300)  NOT NULL DEFAULT '이 글은 {광고주}로부터 제품을 무상으로 {제공/대여}받아 작성한 글입니다',
    sponsor_disclosure_text    varchar(300)  NOT NULL DEFAULT '이 글은 업체로부터 제품을 제공받아 작성한 글입니다',
    return_remind_first_days   integer       NOT NULL DEFAULT 0,
    return_remind_second_days  integer       NOT NULL DEFAULT 7,
    unreturned_days            integer       NOT NULL DEFAULT 14,
    review_days                integer       NOT NULL DEFAULT 14,
    late_ban_days              integer       NOT NULL DEFAULT 90,
    late_revoke_strikes        integer       NOT NULL DEFAULT 2,
    proof_bonus_points         integer       NOT NULL DEFAULT 100,
    proof_photo_days           integer       NOT NULL DEFAULT 30,
    stock_keep_days            integer       NOT NULL DEFAULT 60,
    updated_at                 timestamptz   NOT NULL DEFAULT now(),
    updated_by                 bigint,
    CONSTRAINT ck_trial_settings_one CHECK (id = 1),
    CONSTRAINT ck_trial_settings_values CHECK (
        give_price_limit BETWEEN 0 AND 100000000
        AND char_length(btrim(disclosure_text)) BETWEEN 1 AND 300 AND char_length(btrim(sponsor_disclosure_text)) BETWEEN 1 AND 300
        AND return_remind_first_days BETWEEN 0 AND 60 AND return_remind_second_days > return_remind_first_days AND unreturned_days > return_remind_second_days
        AND unreturned_days <= 120 AND review_days BETWEEN 1 AND 90 AND late_ban_days BETWEEN 1 AND 3650 AND late_revoke_strikes BETWEEN 1 AND 10
        AND proof_bonus_points BETWEEN 0 AND 10000 AND proof_photo_days BETWEEN 1 AND 365 AND stock_keep_days BETWEEN 1 AND 365)
);
INSERT INTO trial_settings (id) VALUES (1);

-- ── 3) 모집 ─────────────────────────────────────────────────────────────────────────────────
-- form: GIVE 제공형(받아서 가짐) · LEND 대여형(반납). shipper: ADVERTISER 광고주가 보냄(회원 동의 — 광고주에게 이름·연락처·주소 제공) · OPERATOR 운영자가 대신(주소는 광고주에게 가지 않는다).
-- status: DRAFT 준비 · RECRUITING 모집 중(리뷰어 먼저 신청 창 포함) · SELECTING 뽑는 중(신청 마감) · RUNNING 진행 중(뽑기 확정) · CLOSED 끝남 · CANCELLED 취소.
-- reviewer_share: 리뷰어 몫 %(기본 60) — 뽑기에서 리뷰어 몫·일반 몫을 따로 센다. reviewer_gold_hours·silver_hours: 리뷰어 금·은이 일반 신청 시작보다 먼저 신청할 수 있는 시간(48·24, 동은 모두와 함께).
-- review_days: 받은 날부터 사용기 기한 일수 · return_days: 받은 날부터 반납 기한 일수(대여형만, 제공형 NULL).
-- price_unit·unit_price·shipping_fee_per_person: 만들 때의 요금표 값(그때 적용되는 가격 기간표 값) 사본 — 금액은 뽑기를 마친 뒤 확정(selected_count·amount).
CREATE TABLE trials (
    id                       bigint        PRIMARY KEY,
    advertiser_id            bigint        NOT NULL REFERENCES ad_advertisers (id),
    biz_application_id       bigint        NOT NULL REFERENCES biz_applications (id),
    title                    varchar(100)  NOT NULL,
    product_name             varchar(100)  NOT NULL,
    product_price            bigint        NOT NULL,
    form                     varchar(4)    NOT NULL,
    review_board_id          bigint        NOT NULL REFERENCES boards (id),
    recruit_count            integer       NOT NULL,
    reviewer_share           integer       NOT NULL DEFAULT 60,
    reviewer_gold_hours      integer       NOT NULL DEFAULT 48,
    reviewer_silver_hours    integer       NOT NULL DEFAULT 24,
    apply_start_at           timestamptz   NOT NULL,
    apply_end_at             timestamptz   NOT NULL,
    review_days              integer       NOT NULL,
    return_days              integer,
    shipper                  varchar(10)   NOT NULL,
    status                   varchar(10)   NOT NULL DEFAULT 'DRAFT',
    description              varchar(5000) NOT NULL DEFAULT '',
    image_key                varchar(200),
    price_unit               varchar(12)   NOT NULL,
    unit_price               bigint        NOT NULL,
    shipping_fee_per_person  bigint        NOT NULL,
    selected_count           integer,
    amount                   bigint,
    created_by               bigint        NOT NULL,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    selected_at              timestamptz,
    selected_by              bigint,
    closed_at                timestamptz,
    cancelled_at             timestamptz,
    CONSTRAINT ux_trials_application UNIQUE (biz_application_id),
    CONSTRAINT ck_trials_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 100 AND char_length(btrim(product_name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_trials_price CHECK (product_price >= 0 AND unit_price >= 0 AND shipping_fee_per_person >= 0),
    CONSTRAINT ck_trials_form CHECK (form IN ('GIVE', 'LEND')),
    CONSTRAINT ck_trials_shipper CHECK (shipper IN ('ADVERTISER', 'OPERATOR')),
    CONSTRAINT ck_trials_status CHECK (status IN ('DRAFT', 'RECRUITING', 'SELECTING', 'RUNNING', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_trials_unit CHECK (price_unit IN ('PER_RECRUIT', 'PER_PERSON')),
    CONSTRAINT ck_trials_counts CHECK (recruit_count BETWEEN 1 AND 1000 AND reviewer_share BETWEEN 0 AND 100),
    CONSTRAINT ck_trials_hours CHECK (reviewer_silver_hours BETWEEN 0 AND 168 AND reviewer_gold_hours BETWEEN reviewer_silver_hours AND 168),
    CONSTRAINT ck_trials_period CHECK (apply_end_at > apply_start_at),
    CONSTRAINT ck_trials_days CHECK (review_days BETWEEN 1 AND 90 AND ((form = 'LEND') = (return_days IS NOT NULL)) AND (return_days IS NULL OR return_days BETWEEN 1 AND 90)),
    CONSTRAINT ck_trials_selected CHECK ((selected_count IS NULL) = (amount IS NULL) AND (amount IS NULL OR amount >= 0))
);
CREATE INDEX ix_trials_status ON trials (status, apply_start_at DESC, id DESC);
CREATE INDEX ix_trials_advertiser ON trials (advertiser_id, created_at DESC);

-- ── 4) 신청 ─────────────────────────────────────────────────────────────────────────────────
-- status: APPLIED 신청 · SELECTED 뽑힘 · NOT_SELECTED 안 뽑힘 · CANCELLED 회원이 취소. quota: 신청 때 리뷰어 칭호가 있으면 REVIEWER(리뷰어 몫), 아니면 GENERAL.
-- reviewer_tier: 신청 때의 리뷰어 단계(BRONZE·SILVER·GOLD, 단계 전·칭호 없음 NULL) — 신청자 줄 표시. recipient_enc·phone_enc·address1_enc·address2_enc·memo_enc: 고른 배송지의
-- 암호문 사본(용도 trial.address.*) — 파기하면 NULL 과 address_purged_at. consents: 동의한 항목 코드 목록 JSON(PROVIDE_CONTACT · UNRETURNED_CONTACT · PRODUCT_CHARGE · DAMAGE_CHARGE).
-- recommended_at: 광고주(또는 운영자)가 후보로 추천한 때 — 최종 선정은 운영자.
CREATE TABLE trial_applications (
    id                  bigint        PRIMARY KEY,
    trial_id            bigint        NOT NULL REFERENCES trials (id),
    user_id             bigint        NOT NULL,
    status              varchar(14)   NOT NULL DEFAULT 'APPLIED',
    quota               varchar(8)    NOT NULL,
    reviewer_tier       varchar(8),
    recipient_enc       varchar(500),
    phone_enc           varchar(500),
    postcode            varchar(5),
    address1_enc        varchar(1000),
    address2_enc        varchar(1000),
    memo_enc            varchar(500),
    address_purged_at   timestamptz,
    consents            jsonb         NOT NULL DEFAULT '[]'::jsonb,
    recommended_at      timestamptz,
    created_at          timestamptz   NOT NULL,
    decided_at          timestamptz,
    cancelled_at        timestamptz,
    CONSTRAINT ck_trial_applications_status CHECK (status IN ('APPLIED', 'SELECTED', 'NOT_SELECTED', 'CANCELLED')),
    CONSTRAINT ck_trial_applications_quota CHECK (quota IN ('REVIEWER', 'GENERAL')),
    CONSTRAINT ck_trial_applications_tier CHECK (reviewer_tier IS NULL OR reviewer_tier IN ('BRONZE', 'SILVER', 'GOLD')),
    CONSTRAINT ck_trial_applications_consents CHECK (jsonb_typeof(consents) = 'array'),
    CONSTRAINT ck_trial_applications_address CHECK ((address_purged_at IS NULL) = (recipient_enc IS NOT NULL AND phone_enc IS NOT NULL AND address1_enc IS NOT NULL))
);
-- 회원·모집마다 살아 있는 신청은 하나(취소하면 다시 신청할 수 있다)
CREATE UNIQUE INDEX ux_trial_applications_live ON trial_applications (trial_id, user_id) WHERE status <> 'CANCELLED';
CREATE INDEX ix_trial_applications_trial ON trial_applications (trial_id, status, created_at);
CREATE INDEX ix_trial_applications_user ON trial_applications (user_id, created_at DESC);
CREATE INDEX ix_trial_applications_purge ON trial_applications (trial_id) WHERE address_purged_at IS NULL;

-- ── 5) 뽑힌 한 줄 ────────────────────────────────────────────────────────────────────────────
-- 송장(carrier·tracking_no·shipped_at·shipped_by_type OPERATOR|ADVERTISER·shipped_by) → 받음(received_at, received_by MEMBER|AUTO — 송장 뒤 7일 안 누르면 배치가 AUTO) →
-- 사용기(review_post_id 연결 글 · review_due_at 받은 날 + 모집 review_days) → 대여형 반납(return_due_at · returned_at · returned_by_type · returned_by) →
-- 약속 지킴(kept true = 기한 안 연결 사용기 + 대여형이면 반납까지 · false = 기한 넘김·미반납) · 미반납 확정(unreturned_at).
-- review_late_at: 사용기 기한 넘김 처리 때 · return_remind_first_at·second_at: 반납 독촉 알림 보낸 때 · contact_provided_at: 광고주에게 이름·연락처를 넘긴 때(미반납·파기 과실 — 감사 기록과 함께).
CREATE TABLE trial_participants (
    id                      bigint        PRIMARY KEY,
    trial_id                bigint        NOT NULL REFERENCES trials (id),
    application_id          bigint        NOT NULL REFERENCES trial_applications (id),
    user_id                 bigint        NOT NULL,
    quota                   varchar(8)    NOT NULL,
    carrier                 varchar(30),
    tracking_no             varchar(40),
    shipped_at              timestamptz,
    shipped_by_type         varchar(10),
    shipped_by              bigint,
    received_at             timestamptz,
    received_by             varchar(6),
    review_post_id          bigint,
    review_posted_at        timestamptz,
    review_due_at           timestamptz,
    return_due_at           timestamptz,
    returned_at             timestamptz,
    returned_by_type        varchar(10),
    returned_by             bigint,
    kept                    boolean,
    kept_at                 timestamptz,
    review_late_at          timestamptz,
    return_remind_first_at  timestamptz,
    return_remind_second_at timestamptz,
    unreturned_at           timestamptz,
    contact_provided_at     timestamptz,
    created_at              timestamptz   NOT NULL,
    CONSTRAINT ux_trial_participants_application UNIQUE (application_id),
    CONSTRAINT ck_trial_participants_quota CHECK (quota IN ('REVIEWER', 'GENERAL')),
    CONSTRAINT ck_trial_participants_shipped CHECK ((shipped_at IS NULL) = (tracking_no IS NULL) AND (shipped_at IS NULL) = (shipped_by_type IS NULL)
                                                    AND (shipped_by_type IS NULL OR shipped_by_type IN ('OPERATOR', 'ADVERTISER'))),
    CONSTRAINT ck_trial_participants_received CHECK ((received_at IS NULL) = (received_by IS NULL) AND (received_by IS NULL OR received_by IN ('MEMBER', 'AUTO'))),
    CONSTRAINT ck_trial_participants_returned CHECK ((returned_at IS NULL) = (returned_by_type IS NULL) AND (returned_by_type IS NULL OR returned_by_type IN ('OPERATOR', 'ADVERTISER'))),
    CONSTRAINT ck_trial_participants_kept CHECK ((kept IS NULL) = (kept_at IS NULL))
);
CREATE INDEX ix_trial_participants_trial ON trial_participants (trial_id, id);
CREATE INDEX ix_trial_participants_user ON trial_participants (user_id, created_at DESC);
-- 사용기 글 하나는 한 줄에만
CREATE UNIQUE INDEX ux_trial_participants_post ON trial_participants (review_post_id) WHERE review_post_id IS NOT NULL;

-- ── 6) 운영자 입고(운영자가 대신 보낼 때) — 모집마다 한 줄. 수량·때·광고주 반송 송장 ───────────────────────────────
CREATE TABLE trial_stock_receipts (
    id                   bigint        PRIMARY KEY,
    trial_id             bigint        NOT NULL REFERENCES trials (id),
    quantity             integer       NOT NULL,
    note                 varchar(300),
    received_at          timestamptz   NOT NULL,
    received_by          bigint        NOT NULL,
    return_carrier       varchar(30),
    return_tracking_no   varchar(40),
    return_shipped_at    timestamptz,
    return_shipped_by    bigint,
    keep_notified_at     timestamptz,
    CONSTRAINT ux_trial_stock_receipts_trial UNIQUE (trial_id),
    CONSTRAINT ck_trial_stock_receipts_quantity CHECK (quantity BETWEEN 1 AND 10000),
    CONSTRAINT ck_trial_stock_receipts_return CHECK ((return_shipped_at IS NULL) = (return_tracking_no IS NULL))
);

-- ── 7) 사진(미디어 저장소 이미지 키) — 입고 STOCK · 발송 전 PRE_SHIP · 반납 RETURN · 파손 DAMAGE ───────────────────────
-- 먼저 올리고(attached_at NULL) 처리(입고·발송·반납·파손 신고)가 붙인다. 붙지 않은 사진은 하루 배치가 하루 뒤 지운다.
-- participant_id: 발송 전·반납·파손 사진의 대상 줄 · damage_report_id: 파손 신고. uploaded_by_type OPERATOR|ADVERTISER(광고주 회원 ID).
CREATE TABLE trial_photos (
    id                bigint        PRIMARY KEY,
    trial_id          bigint        NOT NULL REFERENCES trials (id),
    kind              varchar(10)   NOT NULL,
    participant_id    bigint        REFERENCES trial_participants (id),
    damage_report_id  bigint,
    image_key         varchar(200)  NOT NULL,
    uploaded_by_type  varchar(10)   NOT NULL,
    uploaded_by       bigint        NOT NULL,
    attached_at       timestamptz,
    created_at        timestamptz   NOT NULL,
    CONSTRAINT ck_trial_photos_kind CHECK (kind IN ('STOCK', 'PRE_SHIP', 'RETURN', 'DAMAGE')),
    CONSTRAINT ck_trial_photos_uploader CHECK (uploaded_by_type IN ('OPERATOR', 'ADVERTISER'))
);
CREATE INDEX ix_trial_photos_trial ON trial_photos (trial_id, kind, id);
CREATE INDEX ix_trial_photos_participant ON trial_photos (participant_id, kind) WHERE participant_id IS NOT NULL;
CREATE INDEX ix_trial_photos_loose ON trial_photos (created_at) WHERE attached_at IS NULL;

-- ── 8) 파손 신고 — 운영자·광고주가 사진을 붙여 신고 → 회원 7일 안 이의 → 운영자 판정 ─────────────────────────────────
-- status: REPORTED 이의 기다림 · OBJECTED 회원이 이의 · AWAITING 이의 기한이 지나 운영자 확인할 일 · DECIDED 판정.
-- verdict: NORMAL_WEAR 정상 마모(청구·불이익 없음) · COURIER 택배 파손(보낸 쪽이 택배사에) · MEMBER_FAULT 회원 과실(광고주 수리비 한도 청구 · 신청 90일 금지) ·
--          INTENTIONAL 고의·숨김 반납(영구 금지 + 리뷰어 칭호 회수). 과실·고의일 때만 광고주에게 회원 이름·연락처를 넘긴다(contact_provided_at).
CREATE TABLE trial_damage_reports (
    id                   bigint        PRIMARY KEY,
    trial_id             bigint        NOT NULL REFERENCES trials (id),
    participant_id       bigint        NOT NULL REFERENCES trial_participants (id),
    reporter_type        varchar(10)   NOT NULL,
    reporter_id          bigint        NOT NULL,
    description          varchar(1000) NOT NULL,
    status               varchar(10)   NOT NULL DEFAULT 'REPORTED',
    objection_due_at     timestamptz   NOT NULL,
    objection            varchar(1000),
    objected_at          timestamptz,
    verdict              varchar(14),
    decision_note        varchar(1000),
    decided_by           bigint,
    decided_at           timestamptz,
    contact_provided_at  timestamptz,
    created_at           timestamptz   NOT NULL,
    CONSTRAINT ck_trial_damage_reports_reporter CHECK (reporter_type IN ('OPERATOR', 'ADVERTISER')),
    CONSTRAINT ck_trial_damage_reports_status CHECK (status IN ('REPORTED', 'OBJECTED', 'AWAITING', 'DECIDED')),
    CONSTRAINT ck_trial_damage_reports_verdict CHECK (verdict IS NULL OR verdict IN ('NORMAL_WEAR', 'COURIER', 'MEMBER_FAULT', 'INTENTIONAL')),
    CONSTRAINT ck_trial_damage_reports_decided CHECK ((status = 'DECIDED') = (verdict IS NOT NULL AND decided_at IS NOT NULL AND decided_by IS NOT NULL)),
    CONSTRAINT ck_trial_damage_reports_objected CHECK ((objected_at IS NULL) = (objection IS NULL))
);
CREATE INDEX ix_trial_damage_reports_participant ON trial_damage_reports (participant_id, created_at DESC);
CREATE INDEX ix_trial_damage_reports_queue ON trial_damage_reports (status, created_at) WHERE status <> 'DECIDED';

-- ── 9) 구매 인증 — 주문 내역·영수증 사진(비공개, 암호문 bytea) · 확인 = 그 게시판 매니저 또는 운영자 ──────────────────────
-- status: PENDING 확인 대기 · APPROVED 승인(「구매 인증」 칩 + 보너스 — 원장 PURCHASE_PROOF, 글마다 한 번) · REJECTED 거절(사유 — 다시 올릴 수 있다).
-- content·content_type: 다시 인코딩한 JPEG 의 암호문(용도 trial.purchase-proof) — 확인 뒤 proof_photo_days 가 지나면 파기(NULL·purged_at).
CREATE TABLE purchase_proofs (
    id                  bigint        PRIMARY KEY,
    post_id             bigint        NOT NULL,
    board_id            bigint        NOT NULL,
    user_id             bigint        NOT NULL,
    content             bytea,
    content_type        varchar(20),
    status              varchar(10)   NOT NULL DEFAULT 'PENDING',
    reject_reason       varchar(300),
    reviewed_by         bigint,
    reviewed_by_member  bigint,
    reviewed_at         timestamptz,
    purged_at           timestamptz,
    created_at          timestamptz   NOT NULL,
    CONSTRAINT ck_purchase_proofs_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_purchase_proofs_reviewed CHECK ((status = 'PENDING') = (reviewed_at IS NULL)),
    CONSTRAINT ck_purchase_proofs_rejected CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL),
    CONSTRAINT ck_purchase_proofs_content CHECK ((purged_at IS NULL) = (content IS NOT NULL))
);
-- 글마다 살아 있는 인증(대기·승인)은 하나 — 거절되면 다시 올릴 수 있다
CREATE UNIQUE INDEX ux_purchase_proofs_live ON purchase_proofs (post_id) WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX ix_purchase_proofs_queue ON purchase_proofs (status, created_at, id);
CREATE INDEX ix_purchase_proofs_board ON purchase_proofs (board_id, status, created_at);
CREATE INDEX ix_purchase_proofs_purge ON purchase_proofs (reviewed_at) WHERE purged_at IS NULL AND status <> 'PENDING';

-- ── 10) 협찬 숨김 신고 확인(신고 사유 SPONSOR_HIDDEN — 글마다 한 줄) ────────────────────────────────────────────────
-- 신고가 들어오면 PENDING 으로 만들고(이미 끝난 줄이면 다시 PENDING), 운영자가 CONFIRMED(글을 SPONSORED 로 바꾸고 표시를 붙인다) 또는 DISMISSED.
CREATE TABLE trial_sponsor_reviews (
    post_id            bigint        PRIMARY KEY,
    status             varchar(10)   NOT NULL DEFAULT 'PENDING',
    report_count       integer       NOT NULL DEFAULT 1,
    first_reported_at  timestamptz   NOT NULL,
    last_reported_at   timestamptz   NOT NULL,
    decided_by         bigint,
    decided_at         timestamptz,
    note               varchar(300),
    CONSTRAINT ck_trial_sponsor_reviews_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED')),
    CONSTRAINT ck_trial_sponsor_reviews_decided CHECK ((status = 'PENDING') = (decided_at IS NULL))
);
CREATE INDEX ix_trial_sponsor_reviews_queue ON trial_sponsor_reviews (status, last_reported_at DESC);

-- ── 11) 게시판 칸: 구입 경로 받기 OFF|OPTIONAL|REQUIRED · 체험단 사용기 받기 (덮어쓰기 칸 — 게시판 NULL = 기본값) ────────────────
ALTER TABLE boards ADD COLUMN purchase_source_mode varchar(10);
ALTER TABLE boards ADD COLUMN trial_reviews_enabled boolean;
ALTER TABLE boards ADD CONSTRAINT ck_boards_purchase_source_mode CHECK (purchase_source_mode IS NULL OR purchase_source_mode IN ('OFF', 'OPTIONAL', 'REQUIRED'));
ALTER TABLE board_defaults ADD COLUMN purchase_source_mode varchar(10) NOT NULL DEFAULT 'OFF';
ALTER TABLE board_defaults ADD COLUMN trial_reviews_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_purchase_source_mode CHECK (purchase_source_mode IN ('OFF', 'OPTIONAL', 'REQUIRED'));
-- 사용기 게시판(slug review)은 꼭 골라야 함 + 체험단 사용기 받기(계약 「구입 경로」). 설치 마법사가 만드는 게시판은 설치 프리셋이 같은 값을 넣는다.
UPDATE boards SET purchase_source_mode = 'REQUIRED', trial_reviews_enabled = true WHERE slug = 'review' AND kind = 'GENERAL';

-- 공통 말머리 TRIAL(「체험단」, 스텝 전용 아님) — 체험단 사용기에 서버가 강제로 단다(행이 없으면 서버가 만든다).
ALTER TABLE board_prefixes DROP CONSTRAINT ck_board_prefixes_common;
ALTER TABLE board_prefixes ADD CONSTRAINT ck_board_prefixes_common CHECK (common_key IS NULL OR common_key IN ('QUESTION', 'REVIEW', 'CHAT', 'NOTICE', 'TRIAL'));

-- ── 12) 글: 구입 경로 · 연결된 체험단 줄 ─────────────────────────────────────────────────────────────────
ALTER TABLE posts ADD COLUMN purchase_source varchar(10);
ALTER TABLE posts ADD COLUMN trial_participant_id bigint;
ALTER TABLE posts ADD CONSTRAINT ck_posts_purchase_source CHECK (purchase_source IS NULL OR purchase_source IN ('TRIAL', 'SELF', 'SPONSORED', 'BORROWED'));
ALTER TABLE posts ADD CONSTRAINT ck_posts_trial_link CHECK ((trial_participant_id IS NULL) OR purchase_source = 'TRIAL');
-- 글 표는 파티션 표라 유일 색인을 둘 수 없다 — 「한 줄에 글 하나」는 trial_participants.review_post_id 유일 색인과 줄 잠금(앱)이 막는다. 찾기 색인만 둔다.
CREATE INDEX ix_posts_trial_participant ON posts (trial_participant_id) WHERE trial_participant_id IS NOT NULL;

-- ── 13) 기능 제한 종류 TRIAL_BAN(체험단 신청 금지 — 영구는 until NULL) ─────────────────────────────────────────────
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다
ALTER TABLE member_restrictions DROP CONSTRAINT ck_member_restrictions_type;
ALTER TABLE member_restrictions ADD CONSTRAINT ck_member_restrictions_type CHECK (type IN ('POINT_FREEZE', 'GROUP_BUY_BAN', 'CHARGE_BAN', 'TRIAL_BAN'));

-- ── 14) 신고 사유 SPONSOR_HIDDEN(협찬 숨김 — 구입 경로를 받는 게시판 글에만, 앱이 막는다). V122 목록 ∪ SPONSOR_HIDDEN ──────────────────
-- ⚠ 합칠 때 합집합 확인
ALTER TABLE reports DROP CONSTRAINT ck_reports_reason;
ALTER TABLE reports ADD CONSTRAINT ck_reports_reason
    CHECK (reason IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION', 'OTHER',
                      'DEAL_FALSE_PRICE', 'DEAL_HIDDEN_AD', 'SPONSOR_HIDDEN'));
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_reason_code;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_reason_code
    CHECK (reason_code IS NULL OR reason_code IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION',
                                                  'OTHER', 'DEAL_FALSE_PRICE', 'DEAL_HIDDEN_AD', 'SPONSOR_HIDDEN'));

-- ── 15) 원장 사유 PURCHASE_PROOF(EARN — 구매 인증 승인 보너스, 대상 글, 원장 유일로 글마다 한 번, 하루 총 한도 밖). V238 목록 ∪ PURCHASE_PROOF ───────
-- 종류 짝(ck_point_ledger_reason_kind)은 ELSE(EARN) 갈래라 그대로 둔다.
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason CHECK (reason IN (
    'ATTENDANCE', 'ATTENDANCE_STREAK', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE', 'POST_RESTORE', 'TICKET_PURCHASE', 'ADMIN_ADJUST',
    'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP',
    'CHARGE', 'CHARGE_BONUS', 'CHARGE_BONUS_REVOKE', 'CHARGE_REFUND', 'CHARGE_REFUND_RESTORE', 'CHARGE_BONUS_RESTORE',
    'POINT_SHOP_PURCHASE', 'POINT_SHOP_REFUND', 'GB_POINT_USE', 'GB_POINT_RESTORE',
    'REVOKE_DEBT_OFFSET', 'EXPIRE', 'WITHDRAW_FORFEIT',
    'EMOTICON_CREATOR_REWARD',
    'EVENT_REWARD', 'EVENT_REWARD_REVOKE',
    'ROULETTE_SPIN', 'LOTTO_DRAW', 'ROULETTE_WIN',
    'POST_FEATURED', 'POST_FEATURED_REVOKE',
    'PURCHASE_PROOF'));

-- ── 16) 회원 알림 종류: V240 목록 ∪ 체험단 9종 · 구매 인증 2종 ─────────────────────────────────────────────────
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
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
                    'BIZ_APPLICATION',
                    'DEAL_FLAGGED', 'DEAL_AUTO_ENDED', 'DEAL_MANAGER_ENDED', 'DEAL_EXPIRED', 'DEAL_REOPENED_FIX_ENDS',
                    'SANCTION_ISSUED', 'SANCTION_LIFTED', 'APPEAL_ANSWERED',
                    'BOARD_SANCTION_ISSUED',
                    'EMOTICON_PACK',
                    'EMOTICON_PURCHASE',
                    'AVATAR_UPLOAD_REVIEWED',
                    'REWARD_EVENT_GRANTED', 'REWARD_EVENT_REVOKED',
                    'POST_STATUS_CHANGED',
                    'TITLE_GRANTED',
                    'TRIAL_SELECTED', 'TRIAL_NOT_SELECTED', 'TRIAL_SHIPPED', 'TRIAL_REVIEW_DUE', 'TRIAL_RETURN_DUE', 'TRIAL_UNRETURNED',
                    'TRIAL_DAMAGE_REPORTED', 'TRIAL_DAMAGE_DECIDED',
                    'PURCHASE_PROOF_APPROVED', 'PURCHASE_PROOF_REJECTED'));

-- ── 17) 권한(계약 「권한」 — 운영자): trial.manage · trial.settings · trial.review ─────────────────────────────────────
-- 권한 행은 admin 의 PermissionCatalogSynchronizer 가 맞추지만 이 파일이 먼저 돌 수 있어 여기서도 넣는다(있으면 그대로). 선례 V240.
-- 광고를 처리하는(ad.manage) 운영자 역할에 trial.manage·trial.review, 광고 설정(ad.settings) 역할에 trial.settings 를 함께 준다.
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at)
SELECT c.code, p.axis, p.type_id, p.category, true, now()
  FROM permissions p
  CROSS JOIN (VALUES ('trial.manage', 'ad.manage'), ('trial.review', 'ad.manage'), ('trial.settings', 'ad.settings')) AS c (code, base)
 WHERE p.code = c.base
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | (200 + row_number() OVER (ORDER BY rp.role_id, c.code))::bigint,
       rp.role_id, c.code, now()
  FROM role_permissions rp
  JOIN roles r ON r.role_id = rp.role_id AND r.axis = 'OPERATOR'
  JOIN (VALUES ('trial.manage', 'ad.manage'), ('trial.review', 'ad.manage'), ('trial.settings', 'ad.settings')) AS c (code, base) ON c.base = rp.permission_code
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_code = c.code);

-- ── 18) 리뷰어 칭호 조건에 「구매 인증 1편 이상」(V240 이 미뤄 둔 조건 — 체험단 계약). 운영자가 조건을 바꿨으면(이미 있으면) 건드리지 않는다 ───────────────────
UPDATE titles SET conditions = conditions || '[{"type":"PURCHASE_PROOFS","value":1}]'::jsonb, updated_at = now()
 WHERE title_key = 'reviewer' AND NOT (conditions @> '[{"type":"PURCHASE_PROOFS"}]'::jsonb);

COMMENT ON TABLE trials IS '체험단 모집 — V241 trial-group-contract';
COMMENT ON TABLE trial_applications IS '체험단 신청(배송지 암호문 사본) — V241';
COMMENT ON TABLE trial_participants IS '체험단 뽑힌 줄(발송·받음·사용기·반납) — V241';
COMMENT ON TABLE trial_stock_receipts IS '체험단 운영자 입고·광고주 반송 — V241';
COMMENT ON TABLE trial_photos IS '체험단 입고·발송 전·반납·파손 사진 — V241';
COMMENT ON TABLE trial_damage_reports IS '체험단 대여형 파손 신고·이의·판정 — V241';
COMMENT ON TABLE purchase_proofs IS '사용기 구매 인증(비공개 사진 암호문) — V241';
COMMENT ON TABLE trial_sponsor_reviews IS '협찬 숨김 신고 확인 — V241';
COMMENT ON TABLE trial_settings IS '체험단 설정(행 하나) — V241';
