-- 커머스 2b-2 공구 핵심 — docs/features/commerce-groupbuy-contract.md 「마이그레이션 계획」 V101·「최종 검토로 더하는 열·표」, docs/domain/commerce.md 10절.
-- 번호: develop 의 shared 마지막 V100(2a 보강) 다음(2026-10-28 확인 — 합칠 때 다시 확인).
--
-- 무엇이 생기나
--   · 배송비 정책(shipping_policies): 정책 이름(code)별 기간 줄. 같은 code 의 기간은 겹치지 않는다(배제 제약). 적용이 시작된 줄은 금액·방식·시작을 고치거나 지우지 못한다
--     (트리거 — 끝 시각만 미래로 정할 수 있다: 새 줄을 더할 때 지금 줄을 닫는 길). 공구는 모집 시작 때 그 시각에 유효한 줄의 값을 복사해 끝까지 쓴다(10-2).
--   · 카테고리(group_buy_categories — 2단)·상품(group_buy_products — 설명·이미지·고시 양식·옵션 축/값·상품 단품, 결정 89).
--   · 공구(group_buys — 상품 하나 + 판매 조건)·공구 단품(MAIN·ADDON — 상품 단품을 가리키고 공급가·수량 열)·수정 이력(표시·광고 6개월 — 10-2)·공구 상태 이력.
--   · 주문(group_buy_participations — 한 번의 결제·배송지·배송비) + 주문 줄(group_buy_participation_items — 단품·수량·단가, 결정 23·88).
--   · 수량 이동(group_buy_qty_movements): 수량을 바꾸는 모든 길이 한 줄씩(RESERVE·RELEASE·CONFIRM·CANCEL·RETURN·RESTOCK·ADD·REDUCE·EXCHANGE_OUT), 공구(MAIN 만)·단품 양쪽의 이후 값.
--   · 주문 상태 이력(participation_status_history — 선점 반복 이탈은 여기서 HOLD_TIMEOUT 만 센다, 결정 28).
--   · 환불 기록(group_buy_refunds — 결정 36·75): 계약은 V102 로 적었지만 2b-2 의 불발·중단 환불·본인 취소가 쓰므로 여기서 만든다(요청 ID 는 V102 의 요청 표를 가리킨다 — FK 없이).
--   · 관심 신청(group_buy_watches)·발송 지연 공지(group_buy_delay_notices)·주문 번호 일련(group_buy_order_counters)·공구 설정(group_buy_settings, 1행).
-- 다른 모듈 표(users·payment_orders)에 외래키를 걸지 않는다(commerce.md 3절). 금액 bigint(원), 수량 integer, 시각 timestamptz, ID Snowflake.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ── 공구 설정(1행 — 돈·시각에 닿는 값은 그 트랜잭션 안에서 지금 값을 읽는다, 결정 19) ─────────────────
CREATE TABLE group_buy_settings (
    id                             integer     PRIMARY KEY,
    hold_minutes                   integer     NOT NULL DEFAULT 5,
    demand_max_days                integer     NOT NULL DEFAULT 14,
    first_come_max_days            integer     NOT NULL DEFAULT 30,
    extension_minutes              integer     NOT NULL DEFAULT 30,
    extension_window_minutes       integer     NOT NULL DEFAULT 5,
    judge_grace_minutes            integer     NOT NULL DEFAULT 2,
    ship_within_business_days      integer     NOT NULL DEFAULT 3,
    compensation_cap               bigint      NOT NULL DEFAULT 10000,
    large_refund_threshold         bigint      NOT NULL DEFAULT 100000,
    address_reveal_minutes         integer     NOT NULL DEFAULT 10,
    expired_address_retention_days integer     NOT NULL DEFAULT 30,
    supply_choice_business_days    integer     NOT NULL DEFAULT 3,
    holidays                       date[]      NOT NULL DEFAULT '{}',
    updated_at                     timestamptz,
    updated_by                     bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_group_buy_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_group_buy_settings_hold CHECK (hold_minutes BETWEEN 3 AND 30),
    CONSTRAINT ck_group_buy_settings_days CHECK (demand_max_days BETWEEN 1 AND 90 AND first_come_max_days BETWEEN 1 AND 180),
    CONSTRAINT ck_group_buy_settings_extension CHECK (extension_minutes BETWEEN 0 AND 120 AND extension_window_minutes BETWEEN 1 AND 30),
    CONSTRAINT ck_group_buy_settings_grace CHECK (judge_grace_minutes BETWEEN 0 AND 30),
    CONSTRAINT ck_group_buy_settings_ship_days CHECK (ship_within_business_days BETWEEN 1 AND 30 AND supply_choice_business_days BETWEEN 1 AND 30),
    CONSTRAINT ck_group_buy_settings_money CHECK (compensation_cap >= 0 AND large_refund_threshold >= 0),
    CONSTRAINT ck_group_buy_settings_privacy CHECK (address_reveal_minutes BETWEEN 1 AND 60 AND expired_address_retention_days BETWEEN 1 AND 365)
);
INSERT INTO group_buy_settings (id) VALUES (1);

-- ── 배송비 정책 ───────────────────────────────────────────────────────────────
-- free_shipping_over: null = 무료배송 없음. return_fee_mode: ONE_WAY 편도(기본) · ROUND_TRIP 왕복(10-8). effective_to: null = 계속.
CREATE TABLE shipping_policies (
    id                 bigint       PRIMARY KEY,
    code               varchar(30)  NOT NULL,
    name               varchar(60)  NOT NULL,
    base_fee           bigint       NOT NULL,
    free_shipping_over bigint,
    return_fee         bigint       NOT NULL,
    return_fee_mode    varchar(10)  NOT NULL DEFAULT 'ONE_WAY',
    effective_from     timestamptz  NOT NULL,
    effective_to       timestamptz,
    created_by         bigint,
    created_at         timestamptz  NOT NULL,
    CONSTRAINT ck_shipping_policies_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT ck_shipping_policies_fees CHECK (base_fee >= 0 AND return_fee >= 0 AND (free_shipping_over IS NULL OR free_shipping_over > 0)),
    CONSTRAINT ck_shipping_policies_mode CHECK (return_fee_mode IN ('ONE_WAY', 'ROUND_TRIP')),
    CONSTRAINT ck_shipping_policies_period CHECK (effective_to IS NULL OR effective_to > effective_from),
    -- 같은 정책 이름의 기간은 겹치지 않는다(반열린 구간 [시작, 끝))
    CONSTRAINT ex_shipping_policies_period EXCLUDE USING gist (code WITH =, tstzrange(effective_from, effective_to, '[)') WITH &&)
);
CREATE INDEX ix_shipping_policies_code ON shipping_policies (code, effective_from);

-- 적용이 시작된 줄은 고치거나 지우지 않는다 — 끝 시각만(비었거나 아직 오지 않았을 때) 지금 이후로 정할 수 있다(새 줄을 더하며 지금 줄을 닫는 길).
CREATE OR REPLACE FUNCTION shipping_policies_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.effective_from <= now() THEN
            RAISE EXCEPTION '적용이 시작된 배송비 정책 줄은 지울 수 없다' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.effective_from <= now() THEN
        IF NEW.id <> OLD.id OR NEW.code <> OLD.code OR NEW.name <> OLD.name OR NEW.base_fee <> OLD.base_fee
           OR NEW.free_shipping_over IS DISTINCT FROM OLD.free_shipping_over OR NEW.return_fee <> OLD.return_fee
           OR NEW.return_fee_mode <> OLD.return_fee_mode OR NEW.effective_from <> OLD.effective_from
           OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.created_at <> OLD.created_at THEN
            RAISE EXCEPTION '적용이 시작된 배송비 정책 줄은 고칠 수 없다 — 새 줄을 더한다' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        IF NEW.effective_to IS DISTINCT FROM OLD.effective_to THEN
            IF (OLD.effective_to IS NOT NULL AND OLD.effective_to <= now()) OR NEW.effective_to IS NULL OR NEW.effective_to < now() THEN
                RAISE EXCEPTION '적용이 시작된 줄의 끝은 지금 이후로만 정할 수 있다' USING ERRCODE = 'integrity_constraint_violation';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_shipping_policies_guard BEFORE UPDATE OR DELETE ON shipping_policies FOR EACH ROW EXECUTE FUNCTION shipping_policies_guard();

-- ── 카테고리(결정 89) — 2단(최상위·그 아래 하나, 2단 제한은 앱), 차례·숨김. 상품마다 하나 필수, 회원 목록 필터(최상위를 고르면 아래 단도) ──
CREATE TABLE group_buy_categories (
    id          bigint       PRIMARY KEY,
    parent_id   bigint       REFERENCES group_buy_categories (id),
    name        varchar(30)  NOT NULL,
    sort_order  integer      NOT NULL DEFAULT 0,
    hidden      boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT ck_group_buy_categories_name CHECK (length(btrim(name)) BETWEEN 1 AND 30),
    CONSTRAINT ck_group_buy_categories_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ux_group_buy_categories_name UNIQUE NULLS NOT DISTINCT (parent_id, name)
);

-- ── 상품(결정 89 — 같은 상품을 차수·조건을 바꿔 반복해서 판다) ─────────────────────────────────────────
-- 설명·이미지·고시 양식은 상품에 — 고치면 진행 중 공구에도 바로 보이고 그 공구 수정 이력에 사본(정보 정정).
-- 이름·정상가·청약철회 제한·옵션 축/값·상품 단품 구성은 그 상품을 쓰는 공구가 모집 중(공개된 예정·모집 중·집계 중)이면 잠금(새 단품은 다음 공구부터).
CREATE TABLE group_buy_products (
    id                     bigint        PRIMARY KEY,
    category_id            bigint        NOT NULL REFERENCES group_buy_categories (id),
    name                   varchar(100)  NOT NULL,
    summary                varchar(5000),
    spec                   varchar(5000),
    description            text,
    normal_price           bigint,
    normal_price_basis     varchar(20),
    normal_price_source    varchar(500),
    withdrawal_restriction varchar(1000),
    version                integer       NOT NULL DEFAULT 0,
    created_by             bigint,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    CONSTRAINT ck_group_buy_products_name CHECK (length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_group_buy_products_price CHECK (normal_price IS NULL OR normal_price > 0),
    CONSTRAINT ck_group_buy_products_price_basis CHECK (normal_price_basis IS NULL OR normal_price_basis IN ('OFFICIAL_STORE', 'PREVIOUS_ROUND', 'MARKET', 'PREVIOUS_SALE'))
);
CREATE INDEX ix_group_buy_products_category ON group_buy_products (category_id);

CREATE TABLE group_buy_product_images (
    id          bigint        PRIMARY KEY,
    product_id  bigint        NOT NULL REFERENCES group_buy_products (id) ON DELETE CASCADE,
    image_key   varchar(300)  NOT NULL,
    thumb_key   varchar(300),
    sort_order  integer       NOT NULL,
    created_at  timestamptz   NOT NULL
);
CREATE UNIQUE INDEX ux_group_buy_product_images_order ON group_buy_product_images (product_id, sort_order);

-- 옵션 종류(2b-1 사용자 결정 2026-10-28 — 관리자 「옵션 종류」 목록): 코드(영문 대문자 2~20, 만든 뒤 불변)·이름(유일 — 바꾸면 쓰는 상품의 옵션 이름도 따라간다)·차례·숨김.
-- 상품은 옵션을 이 코드로 고른다(숨긴 종류는 새로 고를 수 없다). 쓰는 상품이 있으면 지울 수 없다(FK).
CREATE TABLE group_buy_option_types (
    code        varchar(20)  PRIMARY KEY,
    name        varchar(30)  NOT NULL,
    sort_order  integer      NOT NULL DEFAULT 0,
    hidden      boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT ck_group_buy_option_types_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),
    CONSTRAINT ck_group_buy_option_types_name CHECK (length(btrim(name)) BETWEEN 1 AND 30),
    CONSTRAINT ux_group_buy_option_types_name UNIQUE (name)
);

-- 옵션 축(0~3개 — 상품이 고른 옵션 종류, 예 색상·사이즈 — 이름은 종류 것)·값(단품들의 값에서 서버가 만든다, 예 블랙·화이트) — 상품에 붙는다(결정 88·89).
-- 고칠 때 옵션 종류(차례)가 같으면 같은 값은 ID 를 유지한다.
CREATE TABLE group_buy_option_axes (
    id               bigint       PRIMARY KEY,
    product_id       bigint       NOT NULL REFERENCES group_buy_products (id) ON DELETE CASCADE,
    sort_order       integer      NOT NULL,
    option_type_code varchar(20)  NOT NULL REFERENCES group_buy_option_types (code)
);
CREATE UNIQUE INDEX ux_group_buy_option_axes_type ON group_buy_option_axes (product_id, option_type_code);
CREATE INDEX ix_group_buy_option_axes_type ON group_buy_option_axes (option_type_code);

CREATE TABLE group_buy_option_values (
    id          bigint       PRIMARY KEY,
    axis_id     bigint       NOT NULL REFERENCES group_buy_option_axes (id) ON DELETE CASCADE,
    product_id  bigint       NOT NULL,
    sort_order  integer      NOT NULL,
    value       varchar(30)  NOT NULL,
    CONSTRAINT ck_group_buy_option_values_value CHECK (length(btrim(value)) BETWEEN 1 AND 30)
);
CREATE UNIQUE INDEX ux_group_buy_option_values_value ON group_buy_option_values (axis_id, value);

-- 상품 단품(2b-1 사용자 결정 — 단품 목록이 정본): 운영자가 한 줄씩 등록한 파는 단위(이름 — 상품 안에서 유일, ID 는 서버 채번 — 운영자 입력 코드 없음). value_ids: 옵션 종류(축)마다 값(축 차례, 옵션이 없으면 빈 배열 — 서버가 값으로 축·값을 만든다).
-- default_supply_price: 기본 공급가(선택 — 공구 단품으로 복사해 고친다).
-- on_sale: 새 공구의 기본 판매 여부. 공구 단품이 가리키는 상품 단품은 지울 수 없다(FK).
CREATE TABLE group_buy_product_skus (
    id                   bigint        PRIMARY KEY,
    product_id           bigint        NOT NULL REFERENCES group_buy_products (id) ON DELETE CASCADE,
    value_ids            bigint[]      NOT NULL DEFAULT '{}',
    name                 varchar(100)  NOT NULL,
    default_supply_price bigint,
    on_sale              boolean       NOT NULL DEFAULT true,
    sort_order           integer       NOT NULL DEFAULT 0,
    CONSTRAINT ck_gbps_supply CHECK (default_supply_price IS NULL OR default_supply_price >= 0),
    CONSTRAINT ck_gbps_name CHECK (length(btrim(name)) BETWEEN 1 AND 100)
);
CREATE UNIQUE INDEX ux_gbps_product ON group_buy_product_skus (id, product_id);
CREATE UNIQUE INDEX ux_gbps_name ON group_buy_product_skus (product_id, name);
-- 같은 값 묶음 두 단품 금지(옵션이 있을 때 — 옵션이 없으면 이름으로 가린다)
CREATE UNIQUE INDEX ux_gbps_combination ON group_buy_product_skus (product_id, value_ids) WHERE cardinality(value_ids) > 0;

-- ── 공구 ─────────────────────────────────────────────────────────────────────
-- product_id: 파는 상품(결정 89 — 설명·이미지·고시·정상가·청약철회 제한은 상품에). 추가 차수 = 같은 상품으로 새 공구.
-- status: DRAFT → RECRUITING → CLOSING → CLOSED_SUCCESS → ORDERED → SHIPPING → COMPLETED / CLOSING → CLOSED_FAILED / (RECRUITING~SHIPPING) → CANCELED (10-2, 결정 49).
-- published_at: 운영자가 「모집 시작」을 누른 시각(공개 — 시작 시각 전이면 「예정」 공구). 시작 시각이 되면 상태 워커가 RECRUITING 으로 바꾼다.
-- min_qty 0 = 선착순. fulfillment: IMMEDIATE 즉시 발송(선착순만) · AFTER_CLOSE 마감 후 발주.
-- 배송비 사본(shipping_fee·free_shipping_over·return_shipping_fee·return_fee_mode)은 모집 시작 때 정책에서 복사하고 끝까지 바꾸지 않는다.
-- 수량: max_qty(지금 최대) · initial_max_qty(모집 시작 때 고정) · added_qty(ADD 합) · current_qty(판매 수량 합 — 결정 28) · reserved_qty(결제 중 선점 합).
--   수량 열은 조건부 UPDATE 로만 바꾸고 version 을 올리지 않는다(version 은 설명·이미지 편집의 낙관 잠금).
-- close_reason: SOLD_OUT 다 팔림 · TIME_UP 기간 끝 · EARLY_CLOSED 운영자 조기 마감 · CANCELED 중단(2b-1 사용자 결정 — 결정 79 고침) — CLOSING(또는 중단) 전이와 같은 문장에서(결정 79).
-- closing_at: CLOSING 에 들어간 시각(마감 시각, 연장됐으면 extended_until, 다 팔림·조기 마감이면 그 시각) — 판정 시각 = closing_at + 선점 시간 + 여유.
-- round_root_id: 1차 공구 ID(1차는 자기 자신) · round_no(유일 (round_root_id, round_no)) · round_limit_shared: 차수 사이 1인 한도 합산(결정 24, 기본 꺼짐).
-- revision_no: 마지막 수정 이력 번호(모집 시작 때 1). 주문은 결제 때의 번호를 남긴다.
CREATE TABLE group_buys (
    id                          bigint        PRIMARY KEY,
    product_id                  bigint        NOT NULL REFERENCES group_buy_products (id),
    title                       varchar(100)  NOT NULL,
    group_price                 bigint        NOT NULL,
    shipping_policy_code        varchar(30)   NOT NULL,
    shipping_fee                bigint,
    free_shipping_over          bigint,
    return_shipping_fee         bigint,
    return_fee_mode             varchar(10),
    min_qty                     integer       NOT NULL DEFAULT 0,
    max_qty                     integer       NOT NULL,
    initial_max_qty             integer,
    added_qty                   integer       NOT NULL DEFAULT 0,
    max_qty_per_user            integer,
    current_qty                 integer       NOT NULL DEFAULT 0,
    reserved_qty                integer       NOT NULL DEFAULT 0,
    start_at                    timestamptz   NOT NULL,
    end_at                      timestamptz   NOT NULL,
    extension_mode              varchar(10)   NOT NULL DEFAULT 'DEMAND',
    extended_until              timestamptz,
    closing_at                  timestamptz,
    close_reason                varchar(15),
    fulfillment                 varchar(12)   NOT NULL,
    ship_planned_at             date,
    ship_within_business_days   integer,
    return_policy               varchar(2000),
    status                      varchar(20)   NOT NULL DEFAULT 'DRAFT',
    published_at                timestamptz,
    host_type                   varchar(10)   NOT NULL DEFAULT 'OPERATOR',
    point_payment_allowed       boolean       NOT NULL DEFAULT false,
    allowed_point_kinds         varchar(20)   NOT NULL DEFAULT 'PAID',
    free_point_cap_per_participation bigint,
    round_root_id               bigint        NOT NULL,
    round_no                    integer       NOT NULL DEFAULT 1,
    round_limit_shared          boolean       NOT NULL DEFAULT false,
    revision_no                 integer       NOT NULL DEFAULT 0,
    version                     integer       NOT NULL DEFAULT 0,
    created_by                  bigint,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    CONSTRAINT ck_group_buys_status CHECK (status IN ('DRAFT', 'RECRUITING', 'CLOSING', 'CLOSED_SUCCESS', 'CLOSED_FAILED', 'ORDERED', 'SHIPPING', 'COMPLETED', 'CANCELED')),
    CONSTRAINT ck_group_buys_fulfillment CHECK (fulfillment IN ('IMMEDIATE', 'AFTER_CLOSE')),
    -- 성사형은 마감 후 발주만(성사 전에 보낼 수 없다 — D1)
    CONSTRAINT ck_group_buys_fulfillment_min CHECK (fulfillment = 'AFTER_CLOSE' OR min_qty = 0),
    CONSTRAINT ck_group_buys_extension CHECK (extension_mode IN ('DEMAND', 'ALWAYS')),
    CONSTRAINT ck_group_buys_close_reason CHECK (close_reason IS NULL OR close_reason IN ('SOLD_OUT', 'TIME_UP', 'EARLY_CLOSED', 'CANCELED')),
    CONSTRAINT ck_group_buys_price CHECK (group_price > 0),
    CONSTRAINT ck_group_buys_qty CHECK (min_qty >= 0 AND max_qty > 0 AND min_qty <= max_qty AND (max_qty_per_user IS NULL OR max_qty_per_user > 0)),
    CONSTRAINT ck_group_buys_counts CHECK (current_qty >= 0 AND reserved_qty >= 0 AND current_qty + reserved_qty <= max_qty AND added_qty >= 0),
    CONSTRAINT ck_group_buys_period CHECK (end_at > start_at AND (extended_until IS NULL OR extended_until > end_at)),
    -- 발송 시기 약정(전자상거래법 제15조 ① — 결정 51): 마감 후 발주는 발송 예정일, 즉시 발송은 결제 후 N영업일
    CONSTRAINT ck_group_buys_ship_plan CHECK ((fulfillment = 'AFTER_CLOSE' AND ship_planned_at IS NOT NULL) OR (fulfillment = 'IMMEDIATE' AND ship_within_business_days BETWEEN 1 AND 30)),
    -- 모집을 시작한 공구는 배송비 사본·처음 수량·수정 이력이 있다
    CONSTRAINT ck_group_buys_started_copy CHECK (status = 'DRAFT' OR (published_at IS NOT NULL AND shipping_fee IS NOT NULL AND return_shipping_fee IS NOT NULL
        AND return_fee_mode IS NOT NULL AND initial_max_qty IS NOT NULL AND revision_no >= 1)),
    CONSTRAINT ck_group_buys_shipping_copy CHECK ((shipping_fee IS NULL OR shipping_fee >= 0) AND (return_shipping_fee IS NULL OR return_shipping_fee >= 0)
        AND (free_shipping_over IS NULL OR free_shipping_over > 0) AND (return_fee_mode IS NULL OR return_fee_mode IN ('ONE_WAY', 'ROUND_TRIP'))),
    CONSTRAINT ck_group_buys_closing CHECK (status IN ('DRAFT', 'RECRUITING') OR closing_at IS NOT NULL OR status = 'CANCELED'),
    CONSTRAINT ck_group_buys_host CHECK (host_type = 'OPERATOR'),
    CONSTRAINT ck_group_buys_points CHECK (allowed_point_kinds IN ('PAID', 'PAID_AND_FREE') AND (free_point_cap_per_participation IS NULL OR free_point_cap_per_participation > 0)),
    CONSTRAINT ck_group_buys_round CHECK (round_no >= 1 AND (round_no > 1 OR round_root_id = id))
);
CREATE UNIQUE INDEX ux_group_buys_round ON group_buys (round_root_id, round_no);
CREATE INDEX ix_group_buys_status ON group_buys (status, start_at);
CREATE INDEX ix_group_buys_product ON group_buys (product_id);
-- 상태 워커: 모집 중 공구의 마감 시각, 집계 중 공구
CREATE INDEX ix_group_buys_recruiting_end ON group_buys (end_at) WHERE status = 'RECRUITING';
CREATE INDEX ix_group_buys_draft_start ON group_buys (start_at) WHERE status = 'DRAFT' AND published_at IS NOT NULL;

-- 공구 단품(group_buy_skus — 실제 판매 단위, 결정 88·89): product_sku_id 가 가리키는 상품 단품마다 이 공구의 추가금·한도·판매 여부·공급가·수량 열.
--   kind MAIN = 공구 상품의 단품(조합마다 하나) / ADDON = 다른 상품의 단품을 추가 구성상품으로 연결(단독 구매 불가 — 고시는 그 상품 것).
--   value_ids·name 은 상품 단품의 사본(MAIN 조합·이름, ADDON 은 빈 배열·「상품 이름」 또는 「상품 이름 (조합)」) — 공구 안에서 유일.
--   supply_price: 공급가 — 모집 시작 때 모든 단품에 있어야 한다(0 은 명시 입력, 빈 값은 409 GROUP_BUY_SUPPLY_PRICE_MISSING). 회원 응답에는 없다.
--   가격: MAIN = 공구가 + extra_price, ADDON = addon_price. max_qty null = 단품 한도 없음(MAIN 은 공구 최대만). 한도 없는 단품도 수량 열은 늘 갱신한다(결정 77).
--   공구 최대·성사 판정·1인 한도는 MAIN 수량만 센다 — 공구 행의 current·reserved = Σ MAIN 단품(결정 88). on_sale: 판매 여부(필요 없는 조합은 끈다).
CREATE TABLE group_buy_skus (
    id              bigint        PRIMARY KEY,
    group_buy_id    bigint        NOT NULL REFERENCES group_buys (id) ON DELETE CASCADE,
    kind            varchar(10)   NOT NULL,
    product_sku_id  bigint        NOT NULL REFERENCES group_buy_product_skus (id),
    value_ids       bigint[]      NOT NULL DEFAULT '{}',
    name            varchar(100)  NOT NULL,
    extra_price     bigint        NOT NULL DEFAULT 0,
    addon_price     bigint,
    max_qty         integer,
    initial_max_qty integer,
    current_qty     integer       NOT NULL DEFAULT 0,
    reserved_qty    integer       NOT NULL DEFAULT 0,
    on_sale         boolean       NOT NULL DEFAULT true,
    supply_price    bigint,
    sort_order      integer       NOT NULL DEFAULT 0,
    CONSTRAINT ck_group_buy_skus_kind CHECK (kind IN ('MAIN', 'ADDON')),
    CONSTRAINT ck_group_buy_skus_price CHECK ((kind = 'MAIN' AND extra_price >= 0 AND addon_price IS NULL)
        OR (kind = 'ADDON' AND extra_price = 0 AND addon_price > 0 AND cardinality(value_ids) = 0)),
    CONSTRAINT ck_group_buy_skus_counts CHECK (current_qty >= 0 AND reserved_qty >= 0 AND (max_qty IS NULL OR (max_qty > 0 AND current_qty + reserved_qty <= max_qty))),
    -- 한도 없는 단품은 처음 수량도 없다(한도 없는 단품에는 단품 ADD·REDUCE 가 없다 — 결정 80)
    CONSTRAINT ck_group_buy_skus_initial CHECK (max_qty IS NOT NULL OR initial_max_qty IS NULL),
    CONSTRAINT ck_group_buy_skus_name CHECK (length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_group_buy_skus_supply CHECK (supply_price IS NULL OR supply_price >= 0)
);
-- 주문 줄·고시 양식의 단품이 같은 공구 것인지 보는 복합 FK 의 대상(결정 66)
CREATE UNIQUE INDEX ux_group_buy_skus_group ON group_buy_skus (id, group_buy_id);
CREATE UNIQUE INDEX ux_group_buy_skus_name ON group_buy_skus (group_buy_id, name);
CREATE UNIQUE INDEX ux_group_buy_skus_product_sku ON group_buy_skus (group_buy_id, product_sku_id);
CREATE INDEX ix_group_buy_skus_product_sku ON group_buy_skus (product_sku_id);
-- 축 값 조합마다 MAIN 단품 하나
CREATE UNIQUE INDEX ux_group_buy_skus_combination ON group_buy_skus (group_buy_id, value_ids) WHERE kind = 'MAIN';

-- 수정 이력(표시·광고 기록 6개월 — 10-2, 시행령 제6조): 상품 내용(설명·이미지·고시·단품 이름 — 결정 89)·수량 사본(추가 입고도 표시 기록 — 계약 「마이그레이션 계획」)
CREATE TABLE group_buy_revisions (
    id             bigint        PRIMARY KEY,
    group_buy_id   bigint        NOT NULL REFERENCES group_buys (id) ON DELETE CASCADE,
    revision_no    integer       NOT NULL,
    title          varchar(100)  NOT NULL,
    product_name   varchar(100)  NOT NULL,
    summary        varchar(5000),
    spec           varchar(5000),
    description    text,
    images         jsonb         NOT NULL DEFAULT '[]',
    max_qty        integer       NOT NULL,
    added_qty      integer       NOT NULL,
    option_axes    jsonb         NOT NULL DEFAULT '[]',
    skus           jsonb         NOT NULL DEFAULT '[]',
    notices        jsonb         NOT NULL DEFAULT '[]',
    change_kind    varchar(20)   NOT NULL,
    edited_by      bigint,
    created_at     timestamptz   NOT NULL,
    CONSTRAINT ck_group_buy_revisions_kind CHECK (change_kind IN ('START', 'CONTENT', 'STOCK_ADD', 'STOCK_REDUCE', 'SHIP_DELAY', 'NOTICE'))
);
CREATE UNIQUE INDEX ux_group_buy_revisions_no ON group_buy_revisions (group_buy_id, revision_no);
CREATE INDEX ix_group_buy_revisions_created ON group_buy_revisions (created_at);

-- ── 상품정보제공고시 양식(결정 87 — 공정위 「전자상거래 등에서의 상품 등의 정보제공에 관한 고시」 Ⅲ-1 품목별 정보) ─────────────────
-- 양식(group_buy_notices): 상품마다 여러 장(결정 89 — 고시는 상품에 붙는다) — 차례·품목 코드(카탈로그 groupbuy/notice-catalog-<버전>.json 의 code)·제목·카탈로그 버전(만든 때 버전 고정).
-- 적용 단품(group_buy_notice_skus — 상품 단품): 비면 그 양식은 상품 전체 = 기본 상품(목록에 없는 상품 단품을 덮는다). 한 단품은 양식 하나에만(유일 product_sku_id).
-- 추가 구성상품은 다른 상품이라 그 상품의 양식이 따로 필요하다(결정 88).
-- 값(group_buy_notice_values): 항목마다 「값」 또는 「제공 불가 + 사유」(일반원칙 ③ — 빈 항목 금지는 모집 시작 때 검사, 제공 불가에 사유가 없는 줄은 DB 가 막는다).
--   custom = true 는 판매자 추가 항목(사용자 지시 2026-10-28): 항목 키 없이 이름(1~40자)·내용(1~1,000자 평문)·차례, 양식당 20개까지(앱), 모집 시작 완성 검사 대상 아님.
CREATE TABLE group_buy_notices (
    id              bigint       PRIMARY KEY,
    product_id      bigint       NOT NULL REFERENCES group_buy_products (id) ON DELETE CASCADE,
    sort_order      integer      NOT NULL,
    category_code   varchar(40)  NOT NULL,
    title           varchar(60)  NOT NULL,
    catalog_version varchar(20)  NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT ck_group_buy_notices_code CHECK (category_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_group_buy_notices_title CHECK (length(btrim(title)) BETWEEN 1 AND 60),
    CONSTRAINT ux_group_buy_notices_product UNIQUE (id, product_id)
);
CREATE UNIQUE INDEX ux_group_buy_notices_order ON group_buy_notices (product_id, sort_order);

CREATE TABLE group_buy_notice_skus (
    notice_id      bigint  NOT NULL,
    product_id     bigint  NOT NULL,
    product_sku_id bigint  NOT NULL,
    PRIMARY KEY (notice_id, product_sku_id),
    CONSTRAINT fk_gbns_notice FOREIGN KEY (notice_id, product_id) REFERENCES group_buy_notices (id, product_id) ON DELETE CASCADE,
    CONSTRAINT fk_gbns_sku FOREIGN KEY (product_sku_id, product_id) REFERENCES group_buy_product_skus (id, product_id) ON DELETE CASCADE
);
-- 한 단품은 양식 하나에만
CREATE UNIQUE INDEX ux_gbns_sku ON group_buy_notice_skus (product_sku_id);

CREATE TABLE group_buy_notice_values (
    id                 bigint         PRIMARY KEY,
    notice_id          bigint         NOT NULL REFERENCES group_buy_notices (id) ON DELETE CASCADE,
    custom             boolean        NOT NULL DEFAULT false,
    item_key           varchar(60),
    custom_name        varchar(40),
    sort_order         integer        NOT NULL DEFAULT 0,
    value              varchar(2000),
    unavailable        boolean        NOT NULL DEFAULT false,
    unavailable_reason varchar(1000),
    CONSTRAINT ck_gbnv_key CHECK (item_key IS NULL OR item_key ~ '^[a-z][a-z0-9_]{0,59}$'),
    -- 고시 항목은 키가, 판매자 추가 항목은 키 없이 이름·내용이 있다(추가 항목은 「제공 불가」가 없다)
    CONSTRAINT ck_gbnv_kind CHECK ((NOT custom AND item_key IS NOT NULL AND custom_name IS NULL)
        OR (custom AND item_key IS NULL AND length(btrim(custom_name)) BETWEEN 1 AND 40 AND value IS NOT NULL AND length(btrim(value)) BETWEEN 1 AND 1000
            AND NOT unavailable)),
    -- 제공 불가면 구체적 사유가 있어야 하고 값은 두지 않는다(일반원칙 ③)
    CONSTRAINT ck_gbnv_unavailable CHECK ((NOT unavailable AND unavailable_reason IS NULL)
        OR (unavailable AND value IS NULL AND unavailable_reason IS NOT NULL AND length(btrim(unavailable_reason)) > 0))
);
CREATE UNIQUE INDEX ux_gbnv_item ON group_buy_notice_values (notice_id, item_key) WHERE NOT custom;
CREATE UNIQUE INDEX ux_gbnv_custom_name ON group_buy_notice_values (notice_id, custom_name) WHERE custom;
CREATE UNIQUE INDEX ux_gbnv_custom_order ON group_buy_notice_values (notice_id, sort_order) WHERE custom;

CREATE TABLE group_buy_status_history (
    id           bigint       PRIMARY KEY,
    group_buy_id bigint       NOT NULL REFERENCES group_buys (id) ON DELETE CASCADE,
    from_status  varchar(20),
    to_status    varchar(20)  NOT NULL,
    reason       varchar(40),
    actor_id     bigint,
    created_at   timestamptz  NOT NULL
);
CREATE INDEX ix_group_buy_status_history ON group_buy_status_history (group_buy_id, created_at);

-- ── 주문(참여) ───────────────────────────────────────────────────────────────
-- status: PENDING_PAYMENT → JOINED → CONFIRMED → PREPARING → SHIPPED → DELIVERED / PENDING_PAYMENT → EXPIRED / → CANCELED(발송 전 전부 취소) / → REFUNDED(불발·중단·전부 반품, 결정 46).
-- order_no: G + KST yyyyMMdd + 그날 일련 5자리(결정 13). 금액: items_amount(줄 합) + shipping_fee = amount(≥1), point_amount(0 — 11절 대비) + cash_amount = amount.
-- refunded_amount: 상품·배송비 환불 누계(취소를 넣을 때 올린다 — 결정 38) · compensation_amount: PG 로 돌려준 고객 택배비 보상 누계(결정 39).
--   남은 결제액 = cash_amount − refunded_amount − compensation_amount ≥ 0.
-- reshipping_fee_charged: 지금까지 재부과한 배송비(결정 33·42) · shipped_shipping_fee: 발송 때 고정한 처음 배송비(결정 43, 2b-3).
-- reserved_until: 선점 끝 = 결제 주문 expires_at(결정 41). payment_order_id: 마지막 결제 시도.
-- 배송지는 암호문(DataCipher — 용도 groupbuy.address.*, postcode 만 평문). expire_cause: 만료 원인(EXPIRED 일 때만, 결정 28).
CREATE TABLE group_buy_participations (
    id                     bigint        PRIMARY KEY,
    order_no               varchar(20)   NOT NULL,
    group_buy_id           bigint        NOT NULL REFERENCES group_buys (id),
    user_id                bigint        NOT NULL,
    status                 varchar(20)   NOT NULL,
    items_amount           bigint        NOT NULL,
    shipping_fee           bigint        NOT NULL,
    amount                 bigint        NOT NULL,
    point_amount           bigint        NOT NULL DEFAULT 0,
    cash_amount            bigint        NOT NULL,
    refunded_amount        bigint        NOT NULL DEFAULT 0,
    compensation_amount    bigint        NOT NULL DEFAULT 0,
    point_refunded         bigint        NOT NULL DEFAULT 0,
    reshipping_fee_charged bigint        NOT NULL DEFAULT 0,
    shipped_shipping_fee   bigint,
    group_buy_revision     integer       NOT NULL,
    payment_order_id       bigint,
    reserved_until         timestamptz   NOT NULL,
    expire_cause           varchar(30),
    recipient_enc          varchar(500),
    phone_enc              varchar(500),
    postcode               varchar(10),
    address1_enc           varchar(1000),
    address2_enc           varchar(1000),
    memo_enc               varchar(1000),
    address_masked_at      timestamptz,
    cash_receipt_type      varchar(10),
    cash_receipt_no_enc    varchar(500),
    admin_memo             varchar(2000),
    joined_at              timestamptz,
    confirmed_at           timestamptz,
    canceled_at            timestamptz,
    refunded_at            timestamptz,
    expired_at             timestamptz,
    ordered_at             timestamptz,
    shipped_at             timestamptz,
    delivered_at           timestamptz,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    CONSTRAINT ck_gbp_status CHECK (status IN ('PENDING_PAYMENT', 'JOINED', 'CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELED', 'REFUNDED', 'EXPIRED')),
    CONSTRAINT ck_gbp_order_no CHECK (order_no ~ '^G[0-9]{8}[0-9]{5,}$'),
    CONSTRAINT ck_gbp_amounts CHECK (items_amount >= 0 AND shipping_fee >= 0 AND amount = items_amount + shipping_fee AND amount >= 1),
    CONSTRAINT ck_gbp_cash CHECK (point_amount >= 0 AND cash_amount = amount - point_amount AND cash_amount >= 0),
    -- 이중 환불 막기(community D 512) — 보상까지 합쳐 남은 결제액을 넘지 않는다(결정 38)
    CONSTRAINT ck_gbp_refunded CHECK (refunded_amount >= 0 AND compensation_amount >= 0 AND refunded_amount <= amount
        AND refunded_amount + compensation_amount <= cash_amount AND point_refunded >= 0 AND point_refunded <= point_amount),
    CONSTRAINT ck_gbp_reshipping CHECK (reshipping_fee_charged >= 0 AND (shipped_shipping_fee IS NULL OR shipped_shipping_fee >= 0)),
    CONSTRAINT ck_gbp_expire_cause CHECK ((status = 'EXPIRED') = (expire_cause IS NOT NULL) AND (expire_cause IS NULL OR expire_cause IN
        ('HOLD_TIMEOUT', 'GROUP_BUY_CANCELED', 'PAYMENT_FAILED_CLOSED', 'REJECTED_CLOSED', 'LATE_PAYMENT', 'REJECTED_ANOMALY'))),
    CONSTRAINT ck_gbp_receipt CHECK (cash_receipt_type IS NULL OR cash_receipt_type IN ('PERSONAL', 'BUSINESS')),
    -- 줄의 공구가 주문의 공구와 같은지 보는 복합 FK 의 대상
    CONSTRAINT ux_gbp_group UNIQUE (id, group_buy_id)
);
CREATE UNIQUE INDEX ux_gbp_order_no ON group_buy_participations (order_no);
-- 공구당 진행 중 주문은 1건(결정 23·28·66 — 어드바이저리 잠금 뒤 마지막 방어선 → GROUP_BUY_ALREADY_JOINED)
CREATE UNIQUE INDEX ux_gbp_in_progress ON group_buy_participations (group_buy_id, user_id)
    WHERE status IN ('PENDING_PAYMENT', 'JOINED', 'CONFIRMED', 'PREPARING', 'SHIPPED');
-- 선점 만료 워커
CREATE INDEX ix_gbp_pending ON group_buy_participations (reserved_until) WHERE status = 'PENDING_PAYMENT';
CREATE INDEX ix_gbp_group_status ON group_buy_participations (group_buy_id, status);
CREATE INDEX ix_gbp_user ON group_buy_participations (user_id, created_at);
CREATE INDEX ix_gbp_payment ON group_buy_participations (payment_order_id);

-- 주문 줄: 줄마다 단품 하나, 같은 단품 두 줄 금지(결정 66·88). 주문에는 MAIN 단품 줄이 하나 이상(추가 구성상품 단독 구매 불가 — 앱).
-- 판매 수량(재고 기준) = qty − canceled_qty − restocked_qty · 1인 한도 기준 = qty − canceled_qty − returned_qty(결정 28, MAIN 단품만).
CREATE TABLE group_buy_participation_items (
    id               bigint   PRIMARY KEY,
    participation_id bigint   NOT NULL,
    group_buy_id     bigint   NOT NULL,
    sku_id           bigint   NOT NULL,
    qty              integer  NOT NULL,
    unit_price       bigint   NOT NULL,
    item_amount      bigint   NOT NULL,
    canceled_qty     integer  NOT NULL DEFAULT 0,
    returned_qty     integer  NOT NULL DEFAULT 0,
    restocked_qty    integer  NOT NULL DEFAULT 0,
    CONSTRAINT ck_gbpi_qty CHECK (qty > 0 AND canceled_qty >= 0 AND returned_qty >= 0 AND canceled_qty + returned_qty <= qty),
    CONSTRAINT ck_gbpi_restocked CHECK (restocked_qty >= 0 AND restocked_qty <= returned_qty),
    CONSTRAINT ck_gbpi_amount CHECK (unit_price > 0 AND item_amount = unit_price * qty),
    CONSTRAINT ux_gbpi_sku UNIQUE (participation_id, sku_id),
    CONSTRAINT fk_gbpi_participation FOREIGN KEY (participation_id, group_buy_id) REFERENCES group_buy_participations (id, group_buy_id) ON DELETE CASCADE,
    CONSTRAINT fk_gbpi_sku FOREIGN KEY (sku_id, group_buy_id) REFERENCES group_buy_skus (id, group_buy_id)
);
CREATE INDEX ix_gbpi_group_sku ON group_buy_participation_items (group_buy_id, sku_id);

CREATE TABLE participation_status_history (
    id               bigint       PRIMARY KEY,
    participation_id bigint       NOT NULL REFERENCES group_buy_participations (id) ON DELETE CASCADE,
    group_buy_id     bigint       NOT NULL,
    user_id          bigint       NOT NULL,
    from_status      varchar(20),
    to_status        varchar(20)  NOT NULL,
    cause            varchar(40),
    actor_id         bigint,
    created_at       timestamptz  NOT NULL
);
CREATE INDEX ix_participation_status_history ON participation_status_history (participation_id, created_at);
-- 선점 반복 이탈: 최근 24시간 HOLD_TIMEOUT 만료만 센다(결정 28)
CREATE INDEX ix_participation_hold_timeout ON participation_status_history (user_id, created_at) WHERE to_status = 'EXPIRED' AND cause = 'HOLD_TIMEOUT';

-- ── 수량 이동 — 수량을 바꾸는 모든 길이 한 줄(이후 값은 조건부 UPDATE … RETURNING 으로, 결정 82) ─────────────────
-- kind: RESERVE·RELEASE·CONFIRM·CANCEL·RETURN(반품 기록, 수량 불변)·RESTOCK(다시 판매)·ADD(추가 입고)·REDUCE(줄이기)·EXCHANGE_OUT(교환 출고, 기록만).
-- 공구 이후 값(current·reserved·max_after)은 공구 행을 바꾼 이동(MAIN 단품·공구 단위)만, sku_* 은 단품 이동만(결정 88 — 추가 구성상품은 공구 행을 바꾸지 않는다).
-- max_after 는 공구 최대(ADD·REDUCE 불변식 — 결정 80).
CREATE TABLE group_buy_qty_movements (
    id                    bigint        PRIMARY KEY,
    group_buy_id          bigint        NOT NULL REFERENCES group_buys (id) ON DELETE CASCADE,
    sku_id                bigint,
    participation_id      bigint,
    participation_item_id bigint,
    kind                  varchar(15)   NOT NULL,
    qty                   integer       NOT NULL,
    current_after         integer,
    reserved_after        integer,
    max_after             integer,
    sku_current_after     integer,
    sku_reserved_after    integer,
    sku_max_after         integer,
    reason                varchar(200),
    actor_id              bigint,
    created_at            timestamptz   NOT NULL,
    CONSTRAINT ck_gbqm_kind CHECK (kind IN ('RESERVE', 'RELEASE', 'CONFIRM', 'CANCEL', 'RETURN', 'RESTOCK', 'ADD', 'REDUCE', 'EXCHANGE_OUT')),
    CONSTRAINT ck_gbqm_qty CHECK (qty > 0),
    CONSTRAINT ck_gbqm_group CHECK ((current_after IS NULL) = (reserved_after IS NULL) AND (current_after IS NULL) = (max_after IS NULL)),
    CONSTRAINT ck_gbqm_sku CHECK (sku_id IS NOT NULL OR (sku_current_after IS NULL AND sku_reserved_after IS NULL AND sku_max_after IS NULL)),
    CONSTRAINT ck_gbqm_side CHECK (current_after IS NOT NULL OR sku_current_after IS NOT NULL)
);
-- 연장 판단(마감 전 5분 안의 풀림 — 결정 81)·이동 내역
CREATE INDEX ix_gbqm_group_created ON group_buy_qty_movements (group_buy_id, created_at);
CREATE INDEX ix_gbqm_participation ON group_buy_qty_movements (participation_id) WHERE participation_id IS NOT NULL;

-- ── 환불 기록(결정 36·75) — 공구 쪽에서 돈을 돌려주는 모든 일을 한 줄씩 ─────────────────────────────
-- cause: REQUEST(요청 승인) · USER_CANCEL(본인 즉시 취소) · FAILED(불발) · CANCELED(중단) · ADMIN(운영 환불) · SUPPLY_FAILURE · DELAY(지연 취소) · RETURN_FEE_REFUND.
-- 금액 나눔: item_refund + shipping_refund − return_fee − reshipping_fee + reshipping_refund + compensation_pg = amount(PG 로 보낸 금액). compensation_bank 은 송금분(따로).
-- method: PG_CANCEL · BANK_TRANSFER · NONE(0원). status: IN_PROGRESS → COMPLETED / DELAYED(PG 포기) / MANUAL(운영자 처리).
-- request_key: 결제 취소 대기열 요청 키 GB_REFUND:{id}. lines: 요청 없는 환불의 줄 내역(주문 줄·수량·금액).
CREATE TABLE group_buy_refunds (
    id                bigint        PRIMARY KEY,
    participation_id  bigint        NOT NULL REFERENCES group_buy_participations (id),
    group_buy_id      bigint        NOT NULL,
    user_id           bigint        NOT NULL,
    cause             varchar(20)   NOT NULL,
    request_id        bigint,
    item_refund       bigint        NOT NULL DEFAULT 0,
    shipping_refund   bigint        NOT NULL DEFAULT 0,
    return_fee        bigint        NOT NULL DEFAULT 0,
    reshipping_fee    bigint        NOT NULL DEFAULT 0,
    reshipping_refund bigint        NOT NULL DEFAULT 0,
    compensation_pg   bigint        NOT NULL DEFAULT 0,
    compensation_bank bigint        NOT NULL DEFAULT 0,
    amount            bigint        NOT NULL,
    method            varchar(15)   NOT NULL,
    payment_order_id  bigint,
    payment_cancel_id bigint,
    request_key       varchar(60)   NOT NULL,
    status            varchar(15)   NOT NULL,
    lines             jsonb,
    actor_id          bigint,
    reason            varchar(200),
    created_at        timestamptz   NOT NULL,
    completed_at      timestamptz,
    CONSTRAINT ck_gbr_cause CHECK (cause IN ('REQUEST', 'USER_CANCEL', 'FAILED', 'CANCELED', 'ADMIN', 'SUPPLY_FAILURE', 'DELAY', 'RETURN_FEE_REFUND')),
    CONSTRAINT ck_gbr_method CHECK (method IN ('PG_CANCEL', 'BANK_TRANSFER', 'NONE')),
    CONSTRAINT ck_gbr_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'DELAYED', 'MANUAL')),
    CONSTRAINT ck_gbr_parts CHECK (item_refund >= 0 AND shipping_refund >= 0 AND return_fee >= 0 AND reshipping_fee >= 0 AND reshipping_refund >= 0
        AND compensation_pg >= 0 AND compensation_bank >= 0 AND amount >= 0),
    CONSTRAINT ck_gbr_sum CHECK (amount = item_refund + shipping_refund - return_fee - reshipping_fee + reshipping_refund + compensation_pg),
    CONSTRAINT ck_gbr_none CHECK ((method = 'NONE') = (amount = 0)),
    CONSTRAINT ck_gbr_done CHECK ((status = 'IN_PROGRESS') = (completed_at IS NULL))
);
CREATE UNIQUE INDEX ux_gbr_request_key ON group_buy_refunds (request_key);
CREATE UNIQUE INDEX ux_gbr_cancel ON group_buy_refunds (payment_cancel_id) WHERE payment_cancel_id IS NOT NULL;
CREATE INDEX ix_gbr_participation ON group_buy_refunds (participation_id, created_at);

-- ── 관심 신청(결정 17·84) — kind: CLOSING_SOON 마감 임박 · RESTOCK 수량 풀림 · NEXT_ROUND 다음 차수(1차 공구 기준). sku_id: 단품 품절 알림(선택, 결정 84·88) ──
CREATE TABLE group_buy_watches (
    id               bigint       PRIMARY KEY,
    group_buy_id     bigint       NOT NULL REFERENCES group_buys (id) ON DELETE CASCADE,
    user_id          bigint       NOT NULL,
    kind             varchar(15)  NOT NULL,
    sku_id           bigint,
    last_notified_at timestamptz,
    created_at       timestamptz  NOT NULL,
    CONSTRAINT ck_group_buy_watches_kind CHECK (kind IN ('CLOSING_SOON', 'RESTOCK', 'NEXT_ROUND')),
    CONSTRAINT ux_group_buy_watches UNIQUE NULLS NOT DISTINCT (group_buy_id, user_id, kind, sku_id)
);
CREATE INDEX ix_group_buy_watches_user ON group_buy_watches (user_id);

-- ── 발송 지연 공지(결정 52) ───────────────────────────────────────────────────
CREATE TABLE group_buy_delay_notices (
    id                  bigint        PRIMARY KEY,
    group_buy_id        bigint        NOT NULL REFERENCES group_buys (id) ON DELETE CASCADE,
    previous_planned_at date,
    new_planned_at      date          NOT NULL,
    reason              varchar(500)  NOT NULL,
    operator_id         bigint,
    created_at          timestamptz   NOT NULL
);
CREATE INDEX ix_group_buy_delay_notices ON group_buy_delay_notices (group_buy_id, created_at);

-- ── 주문 번호 일련(KST 날짜별) ───────────────────────────────────────────────
CREATE TABLE group_buy_order_counters (
    day       date     PRIMARY KEY,
    last_seq  integer  NOT NULL
);
