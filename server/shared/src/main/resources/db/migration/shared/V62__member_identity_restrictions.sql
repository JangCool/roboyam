-- 커머스 0단계 0-4 — 본인인증 · 기능 제한 · 탈퇴 대비 · 포트원 설정 (docs/features/commerce-foundation-contract.md 「0-4」, docs/domain/commerce.md 12-1 · 12-3 · 17-2 · 17-3)
-- 가번호: 0-4 와 0-5 중 develop 에 먼저 합치는 쪽이 V62, 나중 쪽은 합칠 때 다음 번호로 파일 이름을 바꾼다(계약 「번호」).
-- 새 커머스 표는 다른 모듈 표에 외래 키를 걸지 않는다(commerce.md 3절 — 회원은 user_id 로만 참조, 무결성은 앱이 지킨다).
-- 해시 칸은 BlindIndex(HMAC-SHA256 16진 64자), 암호문 칸은 DataCipher(`d<버전>:`) · SecretEncryptor(`v1:`)의 결과만 들어온다. 원문(CI·DI·이메일·비밀키)은 어느 표에도 없다.

-- ── 본인인증 결과 (회원당 한 줄) ─────────────────────────────────────────────
-- di_hash · ci_hash: 용도 identity.di · identity.ci 의 해시. 둘 다 저장하고 각각 유일 — 본인인증 PG 를 바꿔도 1인 1계정이 깨지지 않게(세션 정리 결정 2).
--   업체가 하나만 주면 다른 칸은 NULL(유일 제약은 NULL 끼리 겹쳐도 된다), 둘 다 없으면 저장하지 않는다(CHECK).
-- birth_date: 만 19세 판정용(화면에는 성인 여부와 19세 생일만 나간다). 이름·휴대폰 번호·성별·통신사는 저장하지 않는다.
-- provider_tx_id: 업체의 인증 건 번호 — 유일(같은 인증 결과로 두 번 묶지 못한다).
-- 탈퇴하면 탈퇴 기능이 이 줄의 해시를 identity_withdrawals 로 옮기고 줄을 지운다(IdentityWithdrawalRecorder#recordIdentity).
CREATE TABLE identity_verifications (
    user_id         bigint       PRIMARY KEY,
    di_hash         varchar(64),
    ci_hash         varchar(64),
    birth_date      date         NOT NULL,
    provider        varchar(16)  NOT NULL,
    provider_tx_id  varchar(100) NOT NULL,
    verified_at     timestamptz  NOT NULL,
    CONSTRAINT ux_identity_verifications_di UNIQUE (di_hash),
    CONSTRAINT ux_identity_verifications_ci UNIQUE (ci_hash),
    CONSTRAINT ux_identity_verifications_tx UNIQUE (provider_tx_id),
    CONSTRAINT ck_identity_verifications_hash CHECK (di_hash IS NOT NULL OR ci_hash IS NOT NULL),
    CONSTRAINT ck_identity_verifications_di_format CHECK (di_hash IS NULL OR di_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_identity_verifications_ci_format CHECK (ci_hash IS NULL OR ci_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_identity_verifications_provider CHECK (provider IN ('PORTONE', 'FAKE'))
);

-- ── 본인인증 시도 (서버가 먼저 발급한 인증 번호) ───────────────────────────────
-- id: 서버가 발급한 인증 번호(포트원 identityVerificationId 로 그대로 쓴다). 발급 30분 안에 확정해야 한다.
-- status: PENDING(발급) → VERIFIED(확정) | FAILED(failure_code = 거절한 오류 코드 — 업체 오류·시간 지남·다른 계정·재인증 제한·이미 인증).
-- 본인인증 성공·실패는 회원 행동이라 감사 기록이 아니라 이 표에 남는다. 정리 배치(member-privacy-retention)가 30일(설정값) 뒤 지운다.
CREATE TABLE identity_verification_attempts (
    id            varchar(64)  PRIMARY KEY,
    user_id       bigint       NOT NULL,
    provider      varchar(16)  NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'PENDING',
    failure_code  varchar(40),
    created_at    timestamptz  NOT NULL,
    completed_at  timestamptz,
    CONSTRAINT ck_identity_verification_attempts_provider CHECK (provider IN ('PORTONE', 'FAKE')),
    CONSTRAINT ck_identity_verification_attempts_status CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED')),
    CONSTRAINT ck_identity_verification_attempts_completed CHECK ((status = 'PENDING') = (completed_at IS NULL)),
    CONSTRAINT ck_identity_verification_attempts_failure CHECK ((status = 'FAILED') = (failure_code IS NOT NULL))
);
-- 정리 배치(만든 지 오래된 것) · 회원별 최근 시도
CREATE INDEX ix_identity_verification_attempts_created ON identity_verification_attempts (created_at);
CREATE INDEX ix_identity_verification_attempts_user ON identity_verification_attempts (user_id, created_at DESC);

-- ── 기능 제한 (계정 정지와 따로 특정 기능만 막는다 — commerce.md 12-3) ────────────
-- type: POINT_FREEZE(포인트 적립·사용·충전) · GROUP_BUY_BAN(새 공구 참여) · CHARGE_BAN(새 충전).
-- reason: 회원에게 공개하는 사유(운영자 부여는 필수, 자동 부여는 NULL — 화면·알림은 auto_rule 의 번들 문구를 쓴다). admin_memo: 운영자만 보는 메모.
-- 진행 중 = lifted_at IS NULL ∧ starts_at ≤ 지금 ∧ (until IS NULL ∨ until > 지금). 같은 종류가 겹쳐도 막지 않는다 — 늦게 끝나는 쪽까지 막힌다(화면 확정 결정 1).
-- source: AUTO(auto_rule 필수, created_by 없음) · OPERATOR(created_by 필수). 해제는 운영자만 — lifted_at · lifted_by · lift_reason 이 함께 채워진다.
CREATE TABLE member_restrictions (
    id           bigint        PRIMARY KEY,
    user_id      bigint        NOT NULL,
    type         varchar(20)   NOT NULL,
    reason       varchar(200),
    admin_memo   varchar(1000),
    starts_at    timestamptz   NOT NULL,
    until        timestamptz,
    source       varchar(10)   NOT NULL,
    auto_rule    varchar(40),
    created_by   bigint,
    created_at   timestamptz   NOT NULL,
    lifted_by    bigint,
    lifted_at    timestamptz,
    lift_reason  varchar(200),
    CONSTRAINT ck_member_restrictions_type CHECK (type IN ('POINT_FREEZE', 'GROUP_BUY_BAN', 'CHARGE_BAN')),
    CONSTRAINT ck_member_restrictions_source CHECK (source IN ('AUTO', 'OPERATOR')),
    CONSTRAINT ck_member_restrictions_period CHECK (until IS NULL OR until > starts_at),
    CONSTRAINT ck_member_restrictions_auto CHECK ((source = 'AUTO') = (auto_rule IS NOT NULL)),
    CONSTRAINT ck_member_restrictions_operator CHECK ((source = 'OPERATOR') = (created_by IS NOT NULL)),
    CONSTRAINT ck_member_restrictions_reason CHECK (source = 'AUTO' OR reason IS NOT NULL),
    CONSTRAINT ck_member_restrictions_lift CHECK (
        (lifted_at IS NULL AND lifted_by IS NULL AND lift_reason IS NULL)
        OR (lifted_at IS NOT NULL AND lifted_by IS NOT NULL AND lift_reason IS NOT NULL))
);
-- 진행 중 조회(막힌 기능 판정 · 포인트 적립 건너뜀 · 탈퇴 전 검증) — 해제되지 않은 줄만
CREATE INDEX ix_member_restrictions_open ON member_restrictions (user_id, type, until) WHERE lifted_at IS NULL;
-- 회원 상세의 목록(최근 것부터)
CREATE INDEX ix_member_restrictions_user ON member_restrictions (user_id, created_at DESC);

-- ── 재가입·재인증 제한 해시 (탈퇴 후 30일 — commerce.md 12-1 · 17-2) ─────────────
-- kind: EMAIL · SOCIAL(소셜 계정 식별값) · IDENTITY_DI · IDENTITY_CI. hash 는 BlindIndex 해시만 — 누구의 것인지 잇는 칸(user_id)이 없다.
-- expires_at = 탈퇴 시각 + 설정 일수(기본 30) — 탈퇴 때 박는다. 지나면 정리 배치(member-privacy-retention)가 지운다.
CREATE TABLE identity_withdrawals (
    id            bigint       PRIMARY KEY,
    kind          varchar(16)  NOT NULL,
    hash          varchar(64)  NOT NULL,
    withdrawn_at  timestamptz  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    CONSTRAINT ck_identity_withdrawals_kind CHECK (kind IN ('EMAIL', 'SOCIAL', 'IDENTITY_DI', 'IDENTITY_CI')),
    CONSTRAINT ck_identity_withdrawals_hash CHECK (hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_identity_withdrawals_period CHECK (expires_at > withdrawn_at)
);
-- 가입·인증 때 「같은 값이 아직 막혀 있나」 · 정리 배치
CREATE INDEX ix_identity_withdrawals_lookup ON identity_withdrawals (kind, hash, expires_at);
CREATE INDEX ix_identity_withdrawals_expires ON identity_withdrawals (expires_at);

-- ── 탈퇴 회원 연락용 이메일 (분리 보관 — commerce.md 17-2 · 17-3) ─────────────────
-- 결제 이력이 있는 회원이 탈퇴할 때 이메일 하나만 DataCipher(용도 member.withdrawn-contact.email)로 암호화해 둔다. 일반 회원 조회 쿼리는 이 표를 읽지 않는다.
-- 열람은 전용 권한 + 사유 + 감사(WITHDRAWN_CONTACT_REVEAL)를 같은 트랜잭션에 남긴 뒤에만(WithdrawnContactStore#reveal).
-- purge_after = 마지막 거래일 + 5년. 지나면 정리 배치가 지운다. 데이터 키 교체 배치는 이 표의 email_cipher 를 새 키로 다시 암호화한다.
CREATE TABLE withdrawn_member_contacts (
    user_id       bigint       PRIMARY KEY,
    email_cipher  text         NOT NULL,
    withdrawn_at  timestamptz  NOT NULL,
    purge_after   timestamptz  NOT NULL,
    CONSTRAINT ck_withdrawn_member_contacts_cipher CHECK (email_cipher ~ '^d[0-9]{1,3}:')
);
CREATE INDEX ix_withdrawn_member_contacts_purge ON withdrawn_member_contacts (purge_after);

-- ── 포트원 설정 (관리자 화면의 DB 설정 — 세션 정리 결정 9) ───────────────────────
-- 칸마다 한 줄. 0단계는 본인인증 칸(storeId · apiSecret · identityChannelKey)만, 2a 에서 결제 칸이 같은 표에 더해진다(그래서 키 값 CHECK 를 두지 않는다).
-- value_cipher: SecretEncryptor(ROBOYAM_CRYPTO_KEY) 암호문. 지우면 NULL 이 되고 바꾼 시각·사람은 남는다(화면의 「마지막 변경」). 값은 응답·감사·로그에 싣지 않는다.
CREATE TABLE portone_settings (
    key           varchar(40)  PRIMARY KEY,
    value_cipher  text,
    updated_at    timestamptz  NOT NULL,
    updated_by    bigint,
    CONSTRAINT ck_portone_settings_cipher CHECK (value_cipher IS NULL OR value_cipher LIKE 'v1:%')
);

-- ── 회원 알림 종류: 기능 제한이 걸렸다 (FEATURE_RESTRICTED) ───────────────────────
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'FEATURE_RESTRICTED'));
