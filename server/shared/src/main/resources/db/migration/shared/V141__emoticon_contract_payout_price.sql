-- 로보티콘 S2(상품화 계약 · 작가 정산 정보 · 유료 희망 신청 · 승인 가격 · 가격 바꾸기 · 설정의 유료 칸) — docs/features/emoticon-contract.md 「서버 구현 단계」 S2 · 「S2 구현 결과」.
-- ⚠ 합칠 때 번호 재확인(가번호 V141 — S1 은 V140, develop 의 V139 는 배송지). develop 에 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 무엇이 바뀌나
--   ① 약관 종류 ∪ ROBOTIKON_CONTRACT(로보티콘 상품화 계약 — 판·시행일·이력은 약관 관리 그대로). 종류 칸 varchar(10) → varchar(20)(이름이 18자).
--      가입 동의 버전 문자열(T{n}.P{n})·공개 약관 요약(가입·로그인 링크)에는 넣지 않는다(서버 TermsKind.signup()).
--   ② 본인인증 결과에 이름 암호문(identity_verifications.name_enc — DataCipher 용도 identity.name). 작가 정산 계좌의 예금주 = 인증 이름 대조에만 쓴다.
--      commerce.md 12-1 「이름은 저장하지 않는다(필요해지면 암호문)」의 「필요해지면」 — 이 칸 앞에 인증한 회원은 NULL(정산 정보를 등록하지 못한다 — 계약 S2 결과).
--   ③ 설정(emoticon_settings)의 유료 칸: 유료 판매 켜기(기본 꺼짐) · 수수료율 30% · 정산일 10일 · 최소 지급액 10,000원 · 셀프 환불 기간 7일 · 가격 단계 {1500,2500,3500}.
--      범위는 admin-web UtilEmoticon.SETTING_RANGES 와 같다. 가격 단계 = 1~10개 · 100원 단위 100~100,000원 · 겹침 없음 · 오름차순.
--   ④ 팩 가격 CHECK 넓히기: 0(무료) 또는 작가 팩의 100원 단위 100~100,000원. 「설정의 가격 단계 중 하나 · 유료 판매 켜짐」은 표를 건너는 규칙이라 트리거가 가격을 바꾸는 순간에 본다
--      (운영 팩은 늘 0). 설정 쪽 트리거는 상점에 나온 팩(판매 중·판매 중단)이 쓰는 단계를 빼지 못하게 한다.
--   ⑤ 심사 승인 가격 CHECK 넓히기(승인한 건만 가격, 0 이상).
--   ⑥ 가격 전환 기록(emoticon_price_events — 추가만): 운영자 가격 바꾸기(사유 5~200자) · 새 판 승인으로 바뀐 가격(심사 건을 가리키고 사유 없음).
--   ⑦ 상품화 계약 동의 기록(emoticon_contract_consents — 추가만): (작가, 판) 하나에 한 줄, 동의 시각 · 그때 낸 팩.
--   ⑧ 작가 정산 정보(emoticon_payout_accounts — 작가마다 한 줄): 개인·사업자 · 사업자 번호 · 은행 · 계좌번호 · 예금주(암호문 + 가린 값). 원문은 화면에 내지 않고
--      운영자 원문 보기(권한 emoticon.payout.pii + 사유 + 감사 EMOTICON_PAYOUT_REVEAL)만 푼다.
-- 새 표는 users 에 외래 키를 걸지 않는다(V140 과 같다). 암호문 칸은 DataKeyColumns 에 올렸다(키 교체 배치).

-- ── ① 약관 종류 ─────────────────────────────────────────────────────────────
ALTER TABLE terms_versions ALTER COLUMN kind TYPE varchar(20);
ALTER TABLE terms_versions DROP CONSTRAINT ck_terms_versions_kind;
ALTER TABLE terms_versions ADD CONSTRAINT ck_terms_versions_kind CHECK (kind IN ('TERMS', 'PRIVACY', 'ROBOTIKON_CONTRACT'));
ALTER TABLE terms_drafts ALTER COLUMN kind TYPE varchar(20);
ALTER TABLE terms_drafts DROP CONSTRAINT ck_terms_drafts_kind;
ALTER TABLE terms_drafts ADD CONSTRAINT ck_terms_drafts_kind CHECK (kind IN ('TERMS', 'PRIVACY', 'ROBOTIKON_CONTRACT'));

-- ── ② 본인인증 이름(암호문) ─────────────────────────────────────────────────
ALTER TABLE identity_verifications ADD COLUMN name_enc text;
ALTER TABLE identity_verifications ADD CONSTRAINT ck_identity_verifications_name_enc CHECK (name_enc IS NULL OR name_enc ~ '^d[0-9]+:');

-- ── ③ 설정의 유료 칸 ────────────────────────────────────────────────────────
-- 가격 단계 검사 — 1차원 · 1~10개 · 빈 칸 없음 · 100원 단위 100~100,000원 · 겹침 없음 · 오름차순. 빈 배열은 array_ndims 가 NULL 이라 coalesce 로 거짓(CHECK 는 NULL 을 통과시킨다)
CREATE OR REPLACE FUNCTION emoticon_price_tiers_valid(tiers integer[]) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT coalesce(tiers IS NOT NULL AND array_ndims(tiers) = 1 AND cardinality(tiers) BETWEEN 1 AND 10 AND array_position(tiers, NULL) IS NULL
       AND NOT EXISTS (SELECT 1 FROM unnest(tiers) AS t(v) WHERE v < 100 OR v > 100000 OR v % 100 <> 0)
       AND tiers = (SELECT array_agg(DISTINCT v ORDER BY v) FROM unnest(tiers) AS t(v)), false)
$$;

ALTER TABLE emoticon_settings
    ADD COLUMN paid_sales_enabled boolean   NOT NULL DEFAULT false,
    ADD COLUMN fee_rate_percent   integer   NOT NULL DEFAULT 30,
    ADD COLUMN settlement_day     integer   NOT NULL DEFAULT 10,
    ADD COLUMN payout_minimum     integer   NOT NULL DEFAULT 10000,
    ADD COLUMN refund_days        integer   NOT NULL DEFAULT 7,
    ADD COLUMN price_tiers        integer[] NOT NULL DEFAULT '{1500,2500,3500}';
ALTER TABLE emoticon_settings
    ADD CONSTRAINT ck_emoticon_settings_fee CHECK (fee_rate_percent BETWEEN 0 AND 90),
    ADD CONSTRAINT ck_emoticon_settings_settlement_day CHECK (settlement_day BETWEEN 1 AND 28),
    ADD CONSTRAINT ck_emoticon_settings_payout_minimum CHECK (payout_minimum BETWEEN 0 AND 1000000),
    ADD CONSTRAINT ck_emoticon_settings_refund_days CHECK (refund_days BETWEEN 1 AND 30),
    ADD CONSTRAINT ck_emoticon_settings_price_tiers CHECK (emoticon_price_tiers_valid(price_tiers));

-- ── ④ 팩 가격 ───────────────────────────────────────────────────────────────
ALTER TABLE emoticon_packs DROP CONSTRAINT ck_emoticon_packs_price;
-- 운영 팩은 늘 무료(계약 「목업 2차」), 작가 팩의 유료 가격은 100원 단위 100~100,000원(가격 단계의 범위)
ALTER TABLE emoticon_packs ADD CONSTRAINT ck_emoticon_packs_price CHECK (price = 0 OR (source = 'CREATOR' AND price BETWEEN 100 AND 100000 AND price % 100 = 0));

-- 가격을 바꾸는 순간 — 유료 가격이면 설정의 가격 단계 중 하나이고 유료 판매가 켜져 있어야 한다(무료로 바꾸기는 늘 된다).
-- 서비스는 설정 행을 FOR SHARE 로 잠그고 같은 검사를 먼저 해 400 fields.price 로 답한다 — 이 트리거는 마지막 그물이다
CREATE OR REPLACE FUNCTION emoticon_pack_price_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_tiers   integer[];
    v_enabled boolean;
BEGIN
    IF NEW.price > 0 AND (TG_OP = 'INSERT' OR NEW.price IS DISTINCT FROM OLD.price) THEN
        SELECT price_tiers, paid_sales_enabled INTO v_tiers, v_enabled FROM emoticon_settings WHERE id = 1;
        IF NOT coalesce(v_enabled, false) OR NOT (NEW.price = ANY (v_tiers)) THEN
            RAISE EXCEPTION 'emoticon pack price % is not an open price tier', NEW.price;
        END IF;
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_emoticon_packs_price BEFORE INSERT OR UPDATE OF price ON emoticon_packs FOR EACH ROW EXECUTE FUNCTION emoticon_pack_price_guard();

-- 가격 단계를 바꾸는 순간 — 상점에 나온 팩(판매 중 · 판매 중단)이 쓰는 단계는 뺄 수 없다(계약 「목업 3차」)
CREATE OR REPLACE FUNCTION emoticon_settings_tiers_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.price_tiers IS DISTINCT FROM OLD.price_tiers AND EXISTS (
            SELECT 1 FROM emoticon_packs p WHERE p.status IN ('APPROVED', 'SUSPENDED') AND p.price > 0 AND NOT (p.price = ANY (NEW.price_tiers))) THEN
        RAISE EXCEPTION 'emoticon price tier in use cannot be removed';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_emoticon_settings_tiers BEFORE UPDATE OF price_tiers ON emoticon_settings FOR EACH ROW EXECUTE FUNCTION emoticon_settings_tiers_guard();

-- ── ⑤ 심사 승인 가격 ────────────────────────────────────────────────────────
ALTER TABLE emoticon_reviews DROP CONSTRAINT ck_emoticon_reviews_price;
ALTER TABLE emoticon_reviews ADD CONSTRAINT ck_emoticon_reviews_price CHECK ((status = 'APPROVED') = (approved_price IS NOT NULL)
    AND (approved_price IS NULL OR approved_price = 0 OR (approved_price BETWEEN 100 AND 100000 AND approved_price % 100 = 0)));

-- ── ⑥ 가격 전환 기록(추가만) ────────────────────────────────────────────────
CREATE TABLE emoticon_price_events (
    id          bigint        PRIMARY KEY,
    pack_id     bigint        NOT NULL REFERENCES emoticon_packs (id),
    from_price  integer       NOT NULL,
    to_price    integer       NOT NULL,
    reason      varchar(200),
    review_id   bigint        REFERENCES emoticon_reviews (id),
    changed_at  timestamptz   NOT NULL,
    changed_by  bigint        REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_emoticon_price_events_prices CHECK (from_price >= 0 AND to_price >= 0 AND from_price <> to_price),
    -- 운영자 가격 바꾸기는 사유 5~200자, 새 판 승인으로 바뀐 가격은 심사 건을 가리키고 사유가 없다
    CONSTRAINT ck_emoticon_price_events_reason CHECK ((review_id IS NULL) = (reason IS NOT NULL)
        AND (reason IS NULL OR char_length(btrim(reason)) BETWEEN 5 AND 200))
);
CREATE INDEX ix_emoticon_price_events_pack ON emoticon_price_events (pack_id, changed_at);

-- ⑦ 상품화 계약 동의 기록(추가만) — 판(약관 ROBOTIKON_CONTRACT 의 게시본 버전)마다 작가 한 줄
CREATE TABLE emoticon_contract_consents (
    id         bigint        PRIMARY KEY,
    user_id    bigint        NOT NULL,
    version    integer       NOT NULL,
    agreed_at  timestamptz   NOT NULL,
    pack_id    bigint        REFERENCES emoticon_packs (id),
    CONSTRAINT ux_emoticon_contract_consents_member_version UNIQUE (user_id, version),
    CONSTRAINT ck_emoticon_contract_consents_version CHECK (version >= 1)
);
CREATE INDEX ix_emoticon_contract_consents_version ON emoticon_contract_consents (version);

-- 추가만 하는 두 표 — 고치기·지우기를 막는다(운영자가 지워져 처리자 칸이 비는 것만 받는다)
CREATE OR REPLACE FUNCTION emoticon_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
    END IF;
    IF TG_TABLE_NAME = 'emoticon_price_events' THEN
        IF (NEW.id, NEW.pack_id, NEW.from_price, NEW.to_price, NEW.reason, NEW.review_id, NEW.changed_at)
                IS DISTINCT FROM (OLD.id, OLD.pack_id, OLD.from_price, OLD.to_price, OLD.reason, OLD.review_id, OLD.changed_at)
                OR (NEW.changed_by IS NOT NULL AND NEW.changed_by IS DISTINCT FROM OLD.changed_by) THEN
            RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END
$$;
CREATE TRIGGER tr_emoticon_price_events_append_only BEFORE UPDATE OR DELETE ON emoticon_price_events FOR EACH ROW EXECUTE FUNCTION emoticon_append_only();
CREATE TRIGGER tr_emoticon_contract_consents_append_only BEFORE UPDATE OR DELETE ON emoticon_contract_consents FOR EACH ROW EXECUTE FUNCTION emoticon_append_only();

-- ── ⑧ 작가 정산 정보 ────────────────────────────────────────────────────────
CREATE TABLE emoticon_payout_accounts (
    user_id                bigint        PRIMARY KEY,
    payee_type             varchar(10)   NOT NULL,
    business_number_enc    text,
    business_number_masked varchar(12),
    bank_code              varchar(10)   NOT NULL,
    account_number_enc     text          NOT NULL,
    account_last4          varchar(4)    NOT NULL,
    holder_name_enc        text          NOT NULL,
    holder_masked          varchar(30)   NOT NULL,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    CONSTRAINT ck_emoticon_payout_accounts_type CHECK (payee_type IN ('INDIVIDUAL', 'BUSINESS')),
    -- 사업자만 사업자 번호가 있다(가린 값 123-45-***** 와 함께)
    CONSTRAINT ck_emoticon_payout_accounts_business CHECK ((payee_type = 'BUSINESS') = (business_number_enc IS NOT NULL)
        AND (business_number_enc IS NULL) = (business_number_masked IS NULL)),
    CONSTRAINT ck_emoticon_payout_accounts_bank CHECK (bank_code IN ('KB', 'SHINHAN', 'WOORI', 'HANA', 'NH', 'IBK', 'KAKAO', 'TOSS', 'KBANK', 'SC', 'POST')),
    CONSTRAINT ck_emoticon_payout_accounts_last4 CHECK (account_last4 ~ '^[0-9]{4}$'),
    CONSTRAINT ck_emoticon_payout_accounts_cipher CHECK (account_number_enc ~ '^d[0-9]+:' AND holder_name_enc ~ '^d[0-9]+:'
        AND (business_number_enc IS NULL OR business_number_enc ~ '^d[0-9]+:'))
);
