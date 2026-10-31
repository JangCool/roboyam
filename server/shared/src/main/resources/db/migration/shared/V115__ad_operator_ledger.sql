-- 광고 일정표 백엔드(docs/features/ad-contract.md · docs/plans/biz-ad-hotdeal/04-ia-review.md §2·S9·S10) — 장부는 V112 신청 건(biz_applications) 그대로 쓰고,
--   · 계정 없는 광고주(ad_advertisers — 연락처는 DataCipher 암호문 + 가린 값, 사업자 신원과 1:1 연결 가능) · 광고주 연락처 평문 열람 기록(ad_advertiser_views)
--   · 신청 건에 운영자 등록 칸(광고주 · 등록자 · 비중 · 계약 금액 제안 · 운영 메모)과 긴급 내림 칸(내린 운영자 · 광고주 메일 결과)
--   · 운영자 등록 때 미리 올린 계약서 사본(건에 붙기 전에는 application_id 가 비어 있다)
--   · 환불 종류 DOWNTIME(긴급 내림을 되돌릴 때 중단 기간만큼 일할 환불 — F5)
-- 를 더한다. 운영자 등록 건은 SUBMITTED 로 들어가 같은 광고 심사 큐를 거친다(상태·전이 CHECK 는 바꿀 것 없음 — ck_ba_source_operator 그대로).

-- ── 계정 없는 광고주 — 이름은 평문(검색), 담당자·연락처·이메일은 암호문(용도 ad.advertiser.*) + 목록용 가린 값. 지우지 않는다 ──
CREATE TABLE ad_advertisers (
    id                    bigint        PRIMARY KEY,
    name                  varchar(60)   NOT NULL,
    -- 사업자 신원과 1:1 연결(선택) — 연결하면 사업자 신원의 상호·등록번호 끝 4자리가 함께 보인다
    business_profile_id   bigint        REFERENCES business_profiles (id),
    contact_name_enc      text,
    contact_phone_enc     text,
    email_enc             text,
    contact_name_masked   varchar(60),
    contact_phone_masked  varchar(30),
    email_masked          varchar(120),
    memo                  varchar(300),
    created_by            bigint        NOT NULL,
    created_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL,
    CONSTRAINT ux_ad_advertisers_profile UNIQUE (business_profile_id),
    CONSTRAINT ck_aa_name CHECK (length(btrim(name)) BETWEEN 1 AND 60),
    -- 가린 값은 암호문이 있을 때만(평문을 가린 값 칸에 넣지 않는다 — 서비스가 같이 만든다)
    CONSTRAINT ck_aa_masked CHECK ((contact_name_enc IS NULL) = (contact_name_masked IS NULL) AND (contact_phone_enc IS NULL) = (contact_phone_masked IS NULL)
                                   AND (email_enc IS NULL) = (email_masked IS NULL))
);
-- 같은 이름의 광고주는 하나(대소문자·앞뒤 공백 무시)
CREATE UNIQUE INDEX ux_ad_advertisers_name ON ad_advertisers (lower(btrim(name)));

-- ── 광고주 연락처 평문 열람 기록(사업자 business_sensitive_views 와 같은 모양) — 사유 5~200자 필수 ──
CREATE TABLE ad_advertiser_views (
    id             bigint        PRIMARY KEY,
    advertiser_id  bigint        NOT NULL REFERENCES ad_advertisers (id),
    operator_id    bigint        NOT NULL,
    reason         varchar(200)  NOT NULL,
    ip             varchar(45),
    created_at     timestamptz   NOT NULL,
    CONSTRAINT ck_aav_reason CHECK (length(btrim(reason)) BETWEEN 5 AND 200)
);
CREATE INDEX ix_aav_advertiser ON ad_advertiser_views (advertiser_id, created_at DESC);

-- ── 신청 건 — 운영자 등록 칸 · 긴급 내림 칸 ──
ALTER TABLE biz_applications
    -- 운영자 등록 건의 광고주(계정 없는 광고주) — 사업자를 고른 등록은 business_profile_id 만, 연결된 광고주를 고르면 둘 다
    ADD COLUMN advertiser_id     bigint        REFERENCES ad_advertisers (id),
    -- 등록한 운영자(운영자 등록 건만 — 자기 승인 판정 · 04 §2)
    ADD COLUMN created_by        bigint,
    -- 같은 자리에 여럿이 돌 때의 비중(1~10, 기본 5 — 사업자 신청 건도 5)
    ADD COLUMN weight            smallint      NOT NULL DEFAULT 5,
    -- 운영자 등록 때 적은 계약 금액 제안(공급가) — 승인 때 approved_* 로 확정한다
    ADD COLUMN proposed_supply   bigint,
    ADD COLUMN operator_memo     varchar(300),
    -- 긴급 내림한 운영자 · 광고주 메일 결과(SENT 보냄 · FAILED 실패 · NO_ADDRESS 주소 없음, 보내지 않았으면 null)
    ADD COLUMN taken_down_by     bigint,
    ADD COLUMN takedown_mail     varchar(10);
ALTER TABLE biz_applications
    ADD CONSTRAINT ck_ba_weight CHECK (weight BETWEEN 1 AND 10),
    ADD CONSTRAINT ck_ba_proposed CHECK (proposed_supply IS NULL OR proposed_supply >= 1),
    ADD CONSTRAINT ck_ba_operator_created CHECK (source <> 'OPERATOR' OR created_by IS NOT NULL),
    ADD CONSTRAINT ck_ba_takedown_mail CHECK (takedown_mail IS NULL OR takedown_mail IN ('SENT', 'FAILED', 'NO_ADDRESS'));
CREATE INDEX ix_ba_advertiser ON biz_applications (advertiser_id) WHERE advertiser_id IS NOT NULL;

-- ── 계약서 사본 — 운영자 등록 서랍에서 건이 생기기 전에 올린 사본은 application_id 가 비어 있다(등록 때 그 건에 붙인다). 붙지 않은 사본 정리는 후속 ──
ALTER TABLE biz_contract_files ALTER COLUMN application_id DROP NOT NULL;
CREATE INDEX ix_bcf_draft ON biz_contract_files (uploaded_by, uploaded_at) WHERE application_id IS NULL;

-- ── 환불 종류: DOWNTIME(긴급 내림 되돌리기의 일할 환불 — 바로 승인, used_days = 전체 일수 − 중단 일수). V112 목록 ∪ DOWNTIME ──
ALTER TABLE biz_refunds DROP CONSTRAINT ck_br_type;
ALTER TABLE biz_refunds ADD CONSTRAINT ck_br_type CHECK (type IN ('FULL_AUTO', 'PRORATED', 'SUSPEND_PRORATED', 'DOWNTIME'));
