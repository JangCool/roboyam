-- 사업자 서류 접수 기록(docs/features/biz-doc-intake-contract.md) — 계약서 사본·사업자등록증 사본을 인쇄본(우편·직접 전달)·종이 팩스로 받은 기록.
--   · 종이 접수 한 줄(biz_doc_intakes) — 서류 종류별로 신청 건(CONTRACT) 또는 사업자 신원(CERTIFICATE) 하나만 가리킨다. 처리(계속 보관·폐기 예정·폐기함) · 폐기 · 잘못 기록.
--     줄을 지우지 않는다(잘못 기록은 줄을 그어 남기고 개수에 세지 않는다). 메모·보관 위치는 평문이다(개인정보를 적지 말라는 안내는 화면 몫 — 데이터 키 교체 대상 아님).
--   · 파일 사본이 들어온 길(channel — UPLOAD 기본 · FAX 전자 팩스): 계약서 파일(biz_contract_files) · 등록증 파일(business_documents).
--     관리자가 올린 등록증 파일은 올린 운영자(uploaded_by)를 남긴다(회원이 올린 줄은 null — 회원 하루 올리기 한도는 회원 줄만 센다).
--   · 사업자 신원이 등록증을 내는 길(certificate_delivery — UPLOAD 기본 · MAIL_FAX 우편·팩스로 보냄).
--   · 신청 건의 종이 근거 수(paper_contract_count — 잘못 기록 아닌 계약서 종이 접수, 폐기한 줄 포함) + 수동 확정 CHECK 를 「파일 + 종이 ≥ 1」로.
--   · 사업자 처리 기록 행위 ∪ PAPER_RECEIVED·PAPER_HANDLING·PAPER_DISPOSED·PAPER_VOIDED·CERT_UPLOADED.
--     (신청 건 처리 기록 biz_application_events.action 은 CHECK 가 없다 — 코드 BizEventAction 에 같은 이름을 더한다)

-- ── 종이 접수 기록 ─────────────────────────────────────────────────────────
CREATE TABLE biz_doc_intakes (
    id                     bigint        PRIMARY KEY,
    doc                    varchar(12)   NOT NULL,
    application_id         bigint        REFERENCES biz_applications (id),
    profile_id             bigint        REFERENCES business_profiles (id),
    channel                varchar(6)    NOT NULL,
    received_on            date          NOT NULL,
    pages                  integer,
    memo                   varchar(200),
    handling               varchar(16)   NOT NULL,
    storage_location       varchar(100),
    -- 접수한 운영자(운영자 줄은 지우지 않는다 — 다른 운영 기록처럼 FK 없이 번호만)
    received_by            bigint,
    recorded_at            timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    disposed_on            date,
    disposed_at            timestamptz,
    disposed_by            bigint,
    dispose_reason         varchar(200),
    -- 폐기 때 고른 스캔본 — 계약서는 그 건의 계약서 파일(필수), 등록증은 그 사업자의 등록증 파일(선택)
    scan_contract_file_id  bigint        REFERENCES biz_contract_files (id),
    scan_document_id       bigint        REFERENCES business_documents (id),
    voided_at              timestamptz,
    voided_by              bigint,
    void_reason            varchar(200),
    CONSTRAINT ck_bdi_doc CHECK (doc IN ('CONTRACT', 'CERTIFICATE')),
    CONSTRAINT ck_bdi_target CHECK ((doc = 'CONTRACT' AND application_id IS NOT NULL AND profile_id IS NULL)
                                    OR (doc = 'CERTIFICATE' AND profile_id IS NOT NULL AND application_id IS NULL)),
    CONSTRAINT ck_bdi_channel CHECK (channel IN ('MAIL', 'VISIT', 'FAX')),
    CONSTRAINT ck_bdi_pages CHECK (pages IS NULL OR pages BETWEEN 1 AND 200),
    CONSTRAINT ck_bdi_handling CHECK (handling IN ('KEEP', 'DISPOSE_PENDING', 'DISPOSED')),
    -- 보관 위치는 계속 보관일 때만
    CONSTRAINT ck_bdi_storage CHECK (storage_location IS NULL OR handling = 'KEEP'),
    -- 폐기함 ⇔ 폐기일·시각·폐기자·사유가 있다. 폐기일은 접수일 이후
    CONSTRAINT ck_bdi_disposed CHECK ((handling = 'DISPOSED') = (disposed_on IS NOT NULL)
                                      AND (handling <> 'DISPOSED' OR (disposed_at IS NOT NULL AND disposed_by IS NOT NULL AND dispose_reason IS NOT NULL
                                                                      AND length(btrim(dispose_reason)) BETWEEN 1 AND 200 AND disposed_on >= received_on))
                                      AND (handling = 'DISPOSED' OR (disposed_at IS NULL AND disposed_by IS NULL AND dispose_reason IS NULL))),
    -- 스캔본은 폐기한 줄만 · 서류 종류에 맞는 칸만 · 계약서 폐기는 스캔본 필수
    CONSTRAINT ck_bdi_scan CHECK ((scan_contract_file_id IS NULL OR (doc = 'CONTRACT' AND handling = 'DISPOSED'))
                                  AND (scan_document_id IS NULL OR (doc = 'CERTIFICATE' AND handling = 'DISPOSED'))
                                  AND (doc <> 'CONTRACT' OR handling <> 'DISPOSED' OR scan_contract_file_id IS NOT NULL)),
    -- 잘못 기록 ⇔ 시각·운영자·사유(5~200자). 폐기한 줄은 잘못 기록할 수 없다(폐기 기록은 근거)
    CONSTRAINT ck_bdi_void CHECK ((voided_at IS NULL) = (void_reason IS NULL) AND (voided_at IS NULL) = (voided_by IS NULL)
                                  AND (void_reason IS NULL OR length(btrim(void_reason)) BETWEEN 5 AND 200)
                                  AND (voided_at IS NULL OR handling <> 'DISPOSED'))
);
CREATE INDEX ix_bdi_application ON biz_doc_intakes (application_id, recorded_at DESC) WHERE application_id IS NOT NULL;
CREATE INDEX ix_bdi_profile ON biz_doc_intakes (profile_id, recorded_at DESC) WHERE profile_id IS NOT NULL;

-- ── 파일 사본이 들어온 길 ─────────────────────────────────────────────────────
ALTER TABLE biz_contract_files
    ADD COLUMN channel varchar(6) NOT NULL DEFAULT 'UPLOAD',
    ADD CONSTRAINT ck_bcf_channel CHECK (channel IN ('UPLOAD', 'FAX'));

ALTER TABLE business_documents
    ADD COLUMN channel varchar(6) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN uploaded_by bigint,
    ADD CONSTRAINT ck_bd_channel CHECK (channel IN ('UPLOAD', 'FAX')),
    -- 운영자가 올린 줄은 올리면서 바로 그 신원에 붙는다
    ADD CONSTRAINT ck_bd_operator CHECK (uploaded_by IS NULL OR (profile_id IS NOT NULL AND attached_at IS NOT NULL));

-- ── 사업자 신원이 등록증을 내는 길 ───────────────────────────────────────────
ALTER TABLE business_profiles
    ADD COLUMN certificate_delivery varchar(8) NOT NULL DEFAULT 'UPLOAD',
    ADD CONSTRAINT ck_bp_certificate_delivery CHECK (certificate_delivery IN ('UPLOAD', 'MAIL_FAX'));

-- ── 신청 건 종이 근거 수 + 수동 확정 CHECK(V112 — 파일만 세던 것을 「파일 + 종이 ≥ 1」로) ─────────────────
ALTER TABLE biz_applications
    ADD COLUMN paper_contract_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ba_paper_count CHECK (paper_contract_count >= 0);
ALTER TABLE biz_applications DROP CONSTRAINT ck_ba_manual_confirmed;
ALTER TABLE biz_applications
    ADD CONSTRAINT ck_ba_manual_confirmed CHECK (status NOT IN ('SCHEDULED', 'TAKEN_DOWN') OR payment_method <> 'MANUAL'
                                                 OR (active_contract_count + paper_contract_count >= 1 AND deposit_confirmed_at IS NOT NULL));

-- ── 사업자 처리 기록 행위(V130 목록 ∪ 종이 접수·관리자 등록증 올리기) ─────────────────────────────
ALTER TABLE business_history DROP CONSTRAINT ck_bh_action;
ALTER TABLE business_history
    ADD CONSTRAINT ck_bh_action CHECK (action IN ('APPLIED', 'UPDATED', 'NTS_CHECKED', 'APPROVED', 'REJECTED', 'SUSPENDED', 'UNSUSPENDED', 'REVEALED', 'CERT_VIEWED',
                                                  'WITHDRAWN', 'SERVICE_SUSPENDED', 'SERVICE_RESUMED',
                                                  'PAPER_RECEIVED', 'PAPER_HANDLING', 'PAPER_DISPOSED', 'PAPER_VOIDED', 'CERT_UPLOADED'));
