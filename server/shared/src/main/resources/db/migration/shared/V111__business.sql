-- 사업자 신원(docs/features/business-contract.md, docs/plans/biz-ad-hotdeal/00-overview.md ★ B1~B3·H2·F7·F8).
--   · 신원 1줄(business_profiles — 회원당 하나, 반려 뒤 재신청은 같은 줄) + 서비스별 이용 권한(business_services — HOTDEAL·AD)
--   · 국세청 상태조회 스냅샷(business_nts_checks) · 사업자등록증 사본(business_documents — 공개 /media 폴더가 아니라 DB 에 암호문으로)
--   · 평문·등록증 열람 기록(business_sensitive_views — 운영자·IP·사유) · 처리 기록(business_history — 관리자 상세의 「처리 기록」)
--   · 회원 알림 종류 BUSINESS(덧붙임 kind 로 접수·승인·반려·정지·해제·재심사를 가른다 — 끌 수 없는 계정 알림)
-- 개인정보 칸(등록번호·대표자·연락처·이메일·주소)은 DataCipher 암호문, 같은 번호 비교는 BlindIndex(HMAC) 해시, 끝 4자리만 평문이다.
-- 신청 건(광고·핫딜) 표는 신청 건 백엔드가 만든다 — 여기 표를 참조하는 쪽이 그쪽이다.

CREATE TABLE business_profiles (
    id                       bigint        PRIMARY KEY,
    user_id                  bigint        NOT NULL REFERENCES users (id),
    status                   varchar(10)   NOT NULL,
    -- 승인 뒤 상호·번호를 바꿔 다시 심사 중(F7) — status = PENDING 일 때만. 이미 예약·진행 중인 신청 건은 그대로, 새 신청만 막힌다
    re_review                boolean       NOT NULL DEFAULT false,
    company_name             varchar(60)   NOT NULL,
    biz_no_enc               text,
    biz_no_hash              char(64),
    biz_no_last4             char(4)       NOT NULL,
    owner_name_enc           text,
    phone_enc                text,
    email_enc                text,
    postcode                 varchar(6),
    address1_enc             text,
    address2_enc             text,
    certificate_id           bigint,
    terms_version            varchar(20)   NOT NULL,
    privacy_version          varchar(20)   NOT NULL,
    agreed_at                timestamptz   NOT NULL,
    submitted_at             timestamptz   NOT NULL,
    reviewed_at              timestamptz,
    reviewed_by              bigint        REFERENCES operators (id) ON DELETE SET NULL,
    first_approved_at        timestamptz,
    -- 국세청 조회 불가인데 승인한 사유(B1 — 감사에도 남는다)
    nts_override_reason      varchar(200),
    reject_code              varchar(20),
    reject_text              varchar(200),
    suspend_code             varchar(20),
    suspend_text             varchar(200),
    -- 정지 때 고른 진행 건 처리(H2) — 처리는 신청 건 백엔드의 훅이 한다
    suspend_pending_action   varchar(20),
    suspend_scheduled_action varchar(20),
    suspend_running_action   varchar(20),
    suspended_at             timestamptz,
    suspended_by             bigint        REFERENCES operators (id) ON DELETE SET NULL,
    withdrawn_at             timestamptz,
    -- 보존 기간이 지나 암호문·등록증을 지운 시각(반려·탈퇴 90일 — 파기 배치는 후속)
    erased_at                timestamptz,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    CONSTRAINT ux_business_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_bp_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_bp_re_review CHECK (NOT re_review OR status = 'PENDING'),
    CONSTRAINT ck_bp_last4 CHECK (biz_no_last4 ~ '^[0-9]{4}$'),
    CONSTRAINT ck_bp_company CHECK (length(btrim(company_name)) BETWEEN 1 AND 60),
    CONSTRAINT ck_bp_pii CHECK (erased_at IS NOT NULL OR (biz_no_enc IS NOT NULL AND biz_no_hash IS NOT NULL AND owner_name_enc IS NOT NULL AND phone_enc IS NOT NULL
                                                          AND email_enc IS NOT NULL AND address1_enc IS NOT NULL)),
    CONSTRAINT ck_bp_erased CHECK (erased_at IS NULL OR status IN ('REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_bp_reject CHECK (status <> 'REJECTED' OR (reject_code IS NOT NULL AND reject_text IS NOT NULL)),
    CONSTRAINT ck_bp_reject_code CHECK (reject_code IS NULL OR reject_code IN ('DOC_UNREADABLE', 'INFO_MISMATCH', 'NTS_CLOSED', 'DUPLICATE', 'OTHER')),
    CONSTRAINT ck_bp_suspend CHECK (status <> 'SUSPENDED' OR (suspend_code IS NOT NULL AND suspend_text IS NOT NULL AND suspended_at IS NOT NULL
                                                             AND suspend_pending_action IS NOT NULL AND suspend_scheduled_action IS NOT NULL
                                                             AND suspend_running_action IS NOT NULL)),
    CONSTRAINT ck_bp_suspend_code CHECK (suspend_code IS NULL OR suspend_code IN ('POLICY_VIOLATION', 'FRAUD', 'NTS_CLOSED', 'OTHER')),
    CONSTRAINT ck_bp_suspend_actions CHECK ((suspend_pending_action IS NULL OR suspend_pending_action IN ('CANCEL', 'KEEP'))
                                            AND (suspend_scheduled_action IS NULL OR suspend_scheduled_action IN ('REFUND_FULL', 'HOLD'))
                                            AND (suspend_running_action IS NULL OR suspend_running_action IN ('TAKEDOWN_PRORATE', 'TAKEDOWN_ONLY')))
);
-- 등록번호 중복은 살아 있는 줄(심사 중·승인·정지) 사이에서만(409 BUSINESS_DUPLICATE) — 서비스가 먼저 보고, 동시 신청은 이 색인이 막는다
CREATE UNIQUE INDEX ux_business_profiles_biz_no_live ON business_profiles (biz_no_hash) WHERE status IN ('PENDING', 'APPROVED', 'SUSPENDED');
CREATE INDEX ix_business_profiles_status ON business_profiles (status, submitted_at DESC);

-- ── 서비스별 이용 권한 — 줄은 신청 때 HOTDEAL·AD 둘을 만든다(직접홍보는 광고 한 줄 광고로 합침 — 00 D2) ──────────────
CREATE TABLE business_services (
    id          bigint        PRIMARY KEY,
    profile_id  bigint        NOT NULL REFERENCES business_profiles (id),
    service     varchar(10)   NOT NULL,
    status      varchar(10)   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    updated_by  bigint        REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ux_business_services UNIQUE (profile_id, service),
    CONSTRAINT ck_bs_service CHECK (service IN ('HOTDEAL', 'AD')),
    CONSTRAINT ck_bs_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'OFF'))
);

-- ── 국세청 상태조회 스냅샷(B1) — 줄을 고치지 않고 조회마다 한 줄. 승인 판정은 가장 최근 줄 ─────────────────────────────
CREATE TABLE business_nts_checks (
    id          bigint        PRIMARY KEY,
    profile_id  bigint        NOT NULL REFERENCES business_profiles (id),
    status      varchar(12)   NOT NULL,
    tax_type    varchar(60),
    closed_on   date,
    message     varchar(200),
    checked_at  timestamptz   NOT NULL,
    -- 다시 조회한 운영자(신청·수정 때 서버가 한 조회는 null)
    checked_by  bigint        REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_bnc_status CHECK (status IN ('CONTINUING', 'PAUSED', 'CLOSED', 'NOT_FOUND', 'UNAVAILABLE'))
);
CREATE INDEX ix_bnc_profile ON business_nts_checks (profile_id, checked_at DESC);

-- ── 사업자등록증 사본(B2) — 올리면 먼저 회원 것으로 한 줄(profile_id null), 신청·수정이 붙인다. 내용은 DataCipher 로 잠근 바이트 ────────
CREATE TABLE business_documents (
    id            bigint        PRIMARY KEY,
    user_id       bigint        NOT NULL REFERENCES users (id),
    profile_id    bigint        REFERENCES business_profiles (id),
    kind          varchar(12)   NOT NULL,
    file_name     varchar(120)  NOT NULL,
    content_type  varchar(40)   NOT NULL,
    size_bytes    integer       NOT NULL,
    content       bytea,
    sha256        char(64)      NOT NULL,
    created_at    timestamptz   NOT NULL,
    attached_at   timestamptz,
    erased_at     timestamptz,
    CONSTRAINT ck_bd_kind CHECK (kind IN ('CERTIFICATE')),
    CONSTRAINT ck_bd_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_bd_size CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_bd_content CHECK (erased_at IS NOT NULL OR content IS NOT NULL),
    CONSTRAINT ck_bd_attached CHECK (attached_at IS NULL OR profile_id IS NOT NULL)
);
CREATE INDEX ix_bd_user ON business_documents (user_id, created_at DESC);
CREATE INDEX ix_bd_profile ON business_documents (profile_id) WHERE profile_id IS NOT NULL;
ALTER TABLE business_profiles ADD CONSTRAINT fk_bp_certificate FOREIGN KEY (certificate_id) REFERENCES business_documents (id);

-- ── 평문·등록증 열람 기록(B3 — commerce_address_views 모양 + IP). 평문 열람은 사유 5~200자 필수 ─────────────────────────
CREATE TABLE business_sensitive_views (
    id           bigint        PRIMARY KEY,
    profile_id   bigint        NOT NULL REFERENCES business_profiles (id),
    kind         varchar(12)   NOT NULL,
    operator_id  bigint        NOT NULL,
    reason       varchar(200),
    ip           varchar(45),
    created_at   timestamptz   NOT NULL,
    CONSTRAINT ck_bsv_kind CHECK (kind IN ('REVEAL', 'CERTIFICATE')),
    CONSTRAINT ck_bsv_reason CHECK (kind <> 'REVEAL' OR (reason IS NOT NULL AND length(btrim(reason)) BETWEEN 5 AND 200))
);
CREATE INDEX ix_bsv_profile ON business_sensitive_views (profile_id, kind, created_at DESC);

-- ── 처리 기록 — 신청·수정·조회·승인·반려·정지·해제·열람·탈퇴. note 는 표시용(사유 코드·짧은 글 — 평문 개인정보를 싣지 않는다) ────────
CREATE TABLE business_history (
    id          bigint        PRIMARY KEY,
    profile_id  bigint        NOT NULL REFERENCES business_profiles (id),
    action      varchar(16)   NOT NULL,
    actor_type  varchar(10)   NOT NULL,
    actor_id    bigint,
    note        varchar(300),
    created_at  timestamptz   NOT NULL,
    CONSTRAINT ck_bh_action CHECK (action IN ('APPLIED', 'UPDATED', 'NTS_CHECKED', 'APPROVED', 'REJECTED', 'SUSPENDED', 'UNSUSPENDED', 'REVEALED', 'CERT_VIEWED',
                                              'WITHDRAWN')),
    CONSTRAINT ck_bh_actor CHECK (actor_type IN ('MEMBER', 'OPERATOR', 'SYSTEM'))
);
CREATE INDEX ix_bh_profile ON business_history (profile_id, created_at DESC);

-- ── 회원 알림 종류: 사업자 BUSINESS 한 종류(덧붙임 kind — SUBMITTED·APPROVED·REJECTED·SUSPENDED·UNSUSPENDED·RE_REVIEW) ──
-- 종류 목록은 V104 의 목록 ∪ BUSINESS. ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
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
                    'BUSINESS'));
