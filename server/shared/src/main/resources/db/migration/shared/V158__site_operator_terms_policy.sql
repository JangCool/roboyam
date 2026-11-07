-- 운영 주체 단계 · 약관 운영정책 · 게시본의 운영 주체 사본 · 불리한 변경 — docs/features/terms-operator-contract.md
-- 번호: 가번호 V159 → V158 로 당김(2026-11-07 develop 병합 — V158 을 예약했던 profile-privacy 는 아직 파일이 없어 그 뒤 번호를 쓴다, Flyway 순서 검사 엄격).
--
-- ① site_operator(1행): 사이트를 운영하는 사람·사업자 — 단계 INDIVIDUAL(개인, 사업자등록 전) · SOLE_PROPRIETOR(개인사업자) · CORPORATION(법인).
--    사업자 정보의 정본이다(세션 정리 결정 — 커머스 사업자 정보의 겹치는 칸은 여기서 읽는다). 개인 단계는 사업자 칸이 모두 NULL(CHECK).
--    길이는 관리자 화면 상한과 같다(이름 30 · 이메일 254 · 전화 20 · 주소 200 · 그 밖 50). 필수 여부·전화 글자는 서비스(SiteOperatorService)가 본다.
-- ② site_operator_history: 단계 전환 이력(바꾼 운영자는 FK 없이 — 감사 기록처럼 운영자가 지워져도 남는다).
-- ③ 처음 값: 커머스 사업자 정보(commerce_business_info)에 상호가 있으면 그 값을 옮기고 단계를 SOLE_PROPRIETOR 로(local 시드가 시험용 사업자 정보로 판매를 켜 둔다),
--    없으면 INDIVIDUAL + 빈 칸. 옮긴 뒤 커머스 표의 겹치는 칸(상호·대표자·등록번호·통신판매업 신고번호·신고 기관·주소·전화·이메일·보호책임자·호스팅)을 지운다 —
--    커머스 고유 칸(운영시간 business_hours · 배송·환불 안내 shipping_refund_policy)만 남는다.
-- ④ 약관 종류 ∪ OPERATION_POLICY(운영정책 — 가입 동의 대상 아님). 게시본에 unfavorable(회원에게 불리한 변경)과 operator_snapshot(게시 당시 운영 주체, JSON) 칸.

-- ── ① 운영 주체 ─────────────────────────────────────────────────────────────
CREATE TABLE site_operator (
    id                     integer       PRIMARY KEY,
    stage                  varchar(20)   NOT NULL DEFAULT 'INDIVIDUAL',
    operator_name          varchar(30)   NOT NULL DEFAULT '',
    contact_email          varchar(254)  NOT NULL DEFAULT '',
    phone                  varchar(20),
    privacy_officer_same   boolean       NOT NULL DEFAULT true,
    privacy_officer_name   varchar(30),
    privacy_officer_email  varchar(254),
    hosting_provider       varchar(50),
    business_name          varchar(50),
    representative         varchar(30),
    registration_number    varchar(12),
    address                varchar(200),
    mail_order_number      varchar(50),
    mail_order_authority   varchar(50),
    corporate_number       varchar(20),
    updated_at             timestamptz,
    updated_by             bigint        REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_site_operator_single_row CHECK (id = 1),
    CONSTRAINT ck_site_operator_stage CHECK (stage IN ('INDIVIDUAL', 'SOLE_PROPRIETOR', 'CORPORATION')),
    CONSTRAINT ck_site_operator_registration CHECK (registration_number IS NULL OR registration_number ~ '^[0-9]{3}-[0-9]{2}-[0-9]{5}$'),
    CONSTRAINT ck_site_operator_individual CHECK (stage <> 'INDIVIDUAL' OR (business_name IS NULL AND representative IS NULL AND registration_number IS NULL
        AND address IS NULL AND mail_order_number IS NULL AND mail_order_authority IS NULL AND corporate_number IS NULL)),
    CONSTRAINT ck_site_operator_corporate CHECK (stage = 'CORPORATION' OR corporate_number IS NULL),
    CONSTRAINT ck_site_operator_officer CHECK (NOT privacy_officer_same OR (privacy_officer_name IS NULL AND privacy_officer_email IS NULL))
);

CREATE TABLE site_operator_history (
    id          bigint       PRIMARY KEY,                 -- Snowflake(앱 발급)
    changed_at  timestamptz  NOT NULL,
    from_stage  varchar(20)  NOT NULL,
    to_stage    varchar(20)  NOT NULL,
    changed_by  bigint,                                   -- 운영자(operators.id) — FK 없음
    CONSTRAINT ck_site_operator_history_from CHECK (from_stage IN ('INDIVIDUAL', 'SOLE_PROPRIETOR', 'CORPORATION')),
    CONSTRAINT ck_site_operator_history_to CHECK (to_stage IN ('INDIVIDUAL', 'SOLE_PROPRIETOR', 'CORPORATION')),
    CONSTRAINT ck_site_operator_history_changed CHECK (from_stage <> to_stage)
);
CREATE INDEX ix_site_operator_history_changed ON site_operator_history (changed_at DESC);

-- ── ③ 처음 값 — 커머스 사업자 정보에서 옮긴다 ────────────────────────────────
-- 보호책임자: 비었거나 대표자와 같으면 「운영자와 같음」. 「이름(이메일)」 꼴이면 둘로 나누고, 그 밖의 글자면 이름으로(이메일은 연락 이메일).
INSERT INTO site_operator (id, stage, operator_name, contact_email, phone, privacy_officer_same, privacy_officer_name, privacy_officer_email, hosting_provider,
                           business_name, representative, registration_number, address, mail_order_number, mail_order_authority, updated_at, updated_by)
SELECT 1,
       CASE WHEN b.filled THEN 'SOLE_PROPRIETOR' ELSE 'INDIVIDUAL' END,
       CASE WHEN b.filled THEN left(b.representative, 30) ELSE '' END,
       left(b.email, 254),
       NULLIF(left(b.phone, 20), ''),
       b.officer_same,
       CASE WHEN b.officer_same THEN NULL WHEN b.officer_split IS NOT NULL THEN left(b.officer_split[1], 30) ELSE left(b.privacy_officer, 30) END,
       CASE WHEN b.officer_same THEN NULL WHEN b.officer_split IS NOT NULL THEN left(b.officer_split[2], 254) ELSE NULLIF(left(b.email, 254), '') END,
       NULLIF(left(b.hosting_provider, 50), ''),
       CASE WHEN b.filled THEN left(b.company_name, 50) END,
       CASE WHEN b.filled THEN NULLIF(left(b.representative, 30), '') END,
       CASE WHEN b.filled THEN NULLIF(b.business_no, '') END,
       CASE WHEN b.filled THEN NULLIF(left(b.address, 200), '') END,
       CASE WHEN b.filled THEN NULLIF(b.mail_order_no, '') END,
       CASE WHEN b.filled THEN NULLIF(b.mail_order_authority, '') END,
       b.updated_at, b.updated_by
  FROM (SELECT c.*, btrim(c.company_name) <> '' AS filled,
               (btrim(c.privacy_officer) = '' OR btrim(c.privacy_officer) = btrim(c.representative)) AS officer_same,
               regexp_match(btrim(c.privacy_officer), '^(.+?)\s*\(([^()@\s]+@[^()\s]+)\)$') AS officer_split
          FROM commerce_business_info c WHERE c.id = 1) b;
-- 커머스 행이 없었다면(있을 수 없지만) 빈 처음 값
INSERT INTO site_operator (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM site_operator WHERE id = 1);

-- 옮겨서 사업자 단계가 됐으면 전환 이력 한 줄(처리자 없음). ID 는 앱 Snowflake 와 같은 모양(노드 1023 — 마이그레이션 몫)
INSERT INTO site_operator_history (id, changed_at, from_stage, to_stage, changed_by)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12), now(), 'INDIVIDUAL', 'SOLE_PROPRIETOR', NULL
  FROM site_operator WHERE id = 1 AND stage = 'SOLE_PROPRIETOR';

ALTER TABLE commerce_business_info
    DROP COLUMN company_name,
    DROP COLUMN representative,
    DROP COLUMN business_no,
    DROP COLUMN mail_order_no,
    DROP COLUMN mail_order_authority,
    DROP COLUMN address,
    DROP COLUMN phone,
    DROP COLUMN email,
    DROP COLUMN privacy_officer,
    DROP COLUMN hosting_provider;

-- ── ④ 약관 — 운영정책 · 불리한 변경 · 게시 당시 운영 주체 ─────────────────────
ALTER TABLE terms_versions DROP CONSTRAINT ck_terms_versions_kind;
ALTER TABLE terms_versions ADD CONSTRAINT ck_terms_versions_kind CHECK (kind IN ('TERMS', 'PRIVACY', 'ROBOTIKON_CONTRACT', 'OPERATION_POLICY'));
ALTER TABLE terms_drafts DROP CONSTRAINT ck_terms_drafts_kind;
ALTER TABLE terms_drafts ADD CONSTRAINT ck_terms_drafts_kind CHECK (kind IN ('TERMS', 'PRIVACY', 'ROBOTIKON_CONTRACT', 'OPERATION_POLICY'));

-- unfavorable: 회원에게 불리한 변경으로 게시했는가(시행일 30일 뒤 이후 + 가입 회원에게 메일). 지난 게시본은 false.
-- operator_snapshot: 게시할 때 자리표시를 바꾼 운영 주체 값(TermsOperatorSnapshot JSON). 이 칸 앞에 게시한 판은 NULL.
ALTER TABLE terms_versions ADD COLUMN unfavorable boolean NOT NULL DEFAULT false;
ALTER TABLE terms_versions ADD COLUMN operator_snapshot jsonb;
