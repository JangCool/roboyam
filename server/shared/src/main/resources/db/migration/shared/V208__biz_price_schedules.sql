-- 요금표 가격 기간표 — docs/features/ad-price-rules-contract.md(화면 확정 2026-11-13).
-- 관리자가 상품(biz_price_rules 한 줄)마다 적용 시각을 정해 값 넷(단가·동시 한도·최대 일수·시작일 선택 범위)을 예약한다. 신청 받기 켬/끔은 예약 없이 바로 바뀐다.
--
-- ── 가격 기간표 — 한 줄 = 「이 시각부터 이 값」. 한 상품에 여러 줄, 같은 상품에 같은 적용 시각은 하나만 ──
--   * 값 넷을 통째로 가진다(바꾸지 않는 칸도 그 시각 직전 값으로 채워 둔다). 칸 범위는 biz_price_rules 의 CHECK 와 같다.
--     건당(POST) 상품의 capacity 는 null — unit 은 다른 표라 CHECK 로 묶지 못해 서버가 검사하고 읽을 때도 null 로 본다.
--   * 지금 값 = 「적용 시각 ≤ 지금인 가장 늦은 줄, 없으면 biz_price_rules 값(설치 기본값)」(BizPriceRuleStore). 배치 없이 적용 시각부터 정확하다.
--   * 적용 시각이 지난 줄은 지우지 않는다 — 「언제부터 얼마였나」 기록이자 상품 카드의 「최근 변경」 원천. 고치기·취소(지우기)는 아직 적용 전인 줄만(서버).
--   * 운영자가 지워져도 줄은 남는다(created_by·updated_by 에 FK 없음 — 감사 기록과 같다).
CREATE TABLE biz_price_schedules (
    id            bigint      PRIMARY KEY,                                   -- Snowflake(앱 발급)
    product       varchar(20) NOT NULL REFERENCES biz_price_rules (product),
    effective_at  timestamptz NOT NULL,
    unit_price    bigint      NOT NULL,
    capacity      integer,
    max_days      integer     NOT NULL,
    max_lead_days integer     NOT NULL,
    created_by    bigint      NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_by    bigint,
    updated_at    timestamptz,
    CONSTRAINT uq_bps_product_effective UNIQUE (product, effective_at),       -- 「상품마다 그 시각 이전 가장 늦은 줄」 찾기도 이 색인
    CONSTRAINT ck_bps_price CHECK (unit_price >= 0),
    CONSTRAINT ck_bps_capacity CHECK (capacity IS NULL OR capacity >= 1),
    CONSTRAINT ck_bps_days CHECK (max_days >= 1 AND max_lead_days >= 1)
);

-- 아직 적용 전인 줄(관리자 화면 「다가오는 예약」 · 사업자 화면 예고)
CREATE INDEX ix_bps_effective ON biz_price_schedules (effective_at);

-- ── 변경 이력 — 광고 노출 자리 설정 이력(V120 ad_placement_history)과 같은 짜임. 관리자가 한 일만 남는다 ──
--   * action: CREATE(예약 만들기) · UPDATE(예약 고치기 · 받기 켬/끔) · DELETE(예약 취소). 여러 상품 한꺼번에·모두 취소는 한 줄.
--   * source: ADMIN(관리자 화면 /ads/placements?tab=prices) 하나 — 설정 이력 서랍의 다른 이력과 모양을 맞추려고 둔다.
--   * product: 지금 값(biz_price_rules)이 바로 바뀐 줄의 상품(받기 켬/끔) — 상품 카드의 「최근 변경」에 함께 보인다. 예약 줄(만들기·고치기·취소)은 null(여러 상품일 수 있다).
--     예약이 적용되는 순간은 이력 줄이 아니라 기간표(biz_price_schedules) 자체로 보인다.
--   * changes = [{field, before, after}] — field 는 schedules.<상품>.effectiveAt(순간 ISO) · schedules.<상품>.<칸>(직전 값 → 예약 값) · rules.<상품>.accepting.
--     칸 = unitPrice · capacity · maxDays · maxLeadDays. 값은 모두 글자(숫자 글자, 한도 없음 null, 받기 'true'/'false').
--   * 보관: 무기한(V120 과 같다).
CREATE TABLE biz_price_rule_history (
    id          bigint      PRIMARY KEY,                     -- Snowflake(앱 발급)
    action      varchar(10) NOT NULL,
    source      varchar(10) NOT NULL,
    operator_id bigint      NOT NULL,
    product     varchar(20),
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_bprh_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_bprh_source CHECK (source = 'ADMIN'),
    CONSTRAINT ck_bprh_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);

CREATE INDEX ix_bprh_changed ON biz_price_rule_history (changed_at DESC, id DESC);
CREATE INDEX ix_bprh_product ON biz_price_rule_history (product, changed_at DESC, id DESC) WHERE product IS NOT NULL;
