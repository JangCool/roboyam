-- 관리자 2차 인증(TOTP) — docs/features/commerce-foundation-contract.md 「0-3」, docs/domain/commerce.md 13-3
--
-- admin 스트림에 두는 이유(세션 정리 결정 5): 관리자 앱(admin 서버)만 읽고 쓴다 — 공개 사이트 서버(api)는 운영자 2차 인증을 모른다.
-- 비밀값: OTP 비밀키는 ROBOYAM_CRYPTO_KEY(SecretEncryptor, 「v1:」 암호문)로 암호화해 둔다(결정 4). 복구 코드·신뢰 기기 쿠키는 해시만 둔다.
--
-- · admin_security_settings: 행 하나(id = 1). 설치 직후 꺼짐 · 유예 14일 · 기기 신뢰 1개월(30일) · OTP 실패 한도 5회. 바꾸는 사람은 최고 관리자(루트)뿐(앱이 판정).
--   mfa_enabled_at 은 마지막으로 켠 시각 — 유예의 시작(운영자마다 max(켠 시각, 계정을 만든 시각) + grace_days, 저장하지 않고 매번 계산한다).
-- · operator_mfa: 운영자당 한 행. secret_enc 가 있으면 등록됨. last_step 은 마지막으로 받아들인 TOTP 시간 칸(30초) — 그 칸 이하의 코드는 거절(재사용 방지).
--   pending_* 는 등록 중(확정 전) 비밀키 — 10분 안에 확정하지 않으면 버린다(R__batch_30_admin_mfa_cleanup). pending_reenroll 은 「다시 등록」인가.
-- · operator_mfa_recovery_codes: 복구 코드 10개의 SHA-256(운영자 ID ‖ 코드)만. (운영자, 해시) 유일, 쓰면 used_at — 한 번만.
-- · operator_trusted_devices: 「이 기기에서 다시 묻지 않기」 — 쿠키 값(256비트)의 SHA-256 만(전체 유일). 기간 = 설정 개월 × 30일.
-- 운영자가 지워지면 모두 연쇄 삭제한다.
CREATE TABLE admin_security_settings (
    id             integer     PRIMARY KEY,
    mfa_enabled    boolean     NOT NULL DEFAULT false,
    grace_days     integer     NOT NULL DEFAULT 14,
    trust_months   integer     NOT NULL DEFAULT 1,
    max_failures   integer     NOT NULL DEFAULT 5,
    mfa_enabled_at timestamptz,
    mfa_enabled_by bigint      REFERENCES operators (id) ON DELETE SET NULL,
    updated_at     timestamptz,
    updated_by     bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_admin_security_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_admin_security_settings_grace_days CHECK (grace_days BETWEEN 1 AND 90),
    CONSTRAINT ck_admin_security_settings_trust_months CHECK (trust_months BETWEEN 1 AND 12),
    CONSTRAINT ck_admin_security_settings_max_failures CHECK (max_failures BETWEEN 3 AND 10)
);
INSERT INTO admin_security_settings (id) VALUES (1);

CREATE TABLE operator_mfa (
    operator_id        bigint       PRIMARY KEY REFERENCES operators (id) ON DELETE CASCADE,
    secret_enc         varchar(200),
    enrolled_at        timestamptz,
    last_step          bigint,
    last_used_at       timestamptz,
    pending_secret_enc varchar(200),
    pending_started_at timestamptz,
    pending_reenroll   boolean      NOT NULL DEFAULT false,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_operator_mfa_enrolled CHECK ((secret_enc IS NULL) = (enrolled_at IS NULL)),
    CONSTRAINT ck_operator_mfa_pending CHECK ((pending_secret_enc IS NULL) = (pending_started_at IS NULL))
);
-- 정리 배치가 버려진 등록 중 비밀키를 훑는다
CREATE INDEX ix_operator_mfa_pending ON operator_mfa (pending_started_at) WHERE pending_started_at IS NOT NULL;

CREATE TABLE operator_mfa_recovery_codes (
    id          bigint      PRIMARY KEY,
    operator_id bigint      NOT NULL REFERENCES operators (id) ON DELETE CASCADE,
    code_hash   char(64)    NOT NULL,
    created_at  timestamptz NOT NULL,
    used_at     timestamptz,
    CONSTRAINT ux_operator_mfa_recovery_codes UNIQUE (operator_id, code_hash)
);

CREATE TABLE operator_trusted_devices (
    id           bigint      PRIMARY KEY,
    operator_id  bigint      NOT NULL REFERENCES operators (id) ON DELETE CASCADE,
    token_hash   char(64)    NOT NULL,
    agent        varchar(80),
    ip           varchar(64),
    trusted_at   timestamptz NOT NULL,
    expires_at   timestamptz NOT NULL,
    last_used_at timestamptz,
    CONSTRAINT ux_operator_trusted_devices_token UNIQUE (token_hash),
    CONSTRAINT ck_operator_trusted_devices_period CHECK (expires_at > trusted_at)
);
CREATE INDEX ix_operator_trusted_devices_operator ON operator_trusted_devices (operator_id, trusted_at DESC);
-- 정리 배치가 기간이 끝난 기기를 훑는다
CREATE INDEX ix_operator_trusted_devices_expires ON operator_trusted_devices (expires_at);

-- ── 서버 접근자의 OTP 초기화 명령 (installer/roboyam-mfa-reset.sh 가 psql 로 부른다) ──────────────────────────────
-- 루트가 폰·복구 코드를 모두 잃었을 때 쓴다(화면의 초기화는 루트가 다른 운영자에게만 — 본인은 409 MFA_RESET_SELF). 이메일은 대소문자를 무시하고 찾는다.
-- 그 운영자의 OTP(등록 중 비밀키 포함)·복구 코드·신뢰 기기를 지우고 감사 기록 MFA_RESET 을 SYSTEM 으로 남긴다(detail.via = SERVER_COMMAND, 비밀값 없음).
-- 반환: 'NOT_FOUND'(그 이메일의 운영자가 없음) · 'RESET'(지웠다 — 등록돼 있지 않았어도 남은 기록을 지우고 감사를 남긴다).
-- 감사 기록 ID 는 앱의 Snowflake 와 같은 모양(2026-01-01 기준 ms 41비트 | 서버 번호 10비트 | 순번 12비트)이고, 서버 번호는 이 명령 전용으로 1022 를 쓴다
-- (1023 은 마이그레이션 전용 — V30). 같은 ms 에 겹치면 순번을 바꿔 다시 넣는다.
CREATE OR REPLACE FUNCTION admin_mfa_reset(p_email text) RETURNS text
LANGUAGE plpgsql AS $fn$
DECLARE
    v_id       bigint;
    v_enrolled boolean := false;
    v_codes    int;
    v_devices  int;
    v_audit    bigint;
    v_try      int := 0;
BEGIN
    SELECT id INTO v_id FROM operators WHERE email = lower(btrim(coalesce(p_email, '')));
    IF v_id IS NULL THEN
        RETURN 'NOT_FOUND';
    END IF;

    SELECT secret_enc IS NOT NULL INTO v_enrolled FROM operator_mfa WHERE operator_id = v_id FOR UPDATE;
    DELETE FROM operator_mfa WHERE operator_id = v_id;
    DELETE FROM operator_mfa_recovery_codes WHERE operator_id = v_id;
    GET DIAGNOSTICS v_codes = ROW_COUNT;
    DELETE FROM operator_trusted_devices WHERE operator_id = v_id;
    GET DIAGNOSTICS v_devices = ROW_COUNT;

    LOOP
        v_audit := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22)
                   | (1022::bigint << 12) | floor(random() * 4096)::bigint;
        INSERT INTO audit_logs (id, occurred_at, actor_type, actor_id, action, result, target_type, target_id, ip, request_id, detail)
        VALUES (v_audit, now(), 'SYSTEM', NULL, 'MFA_RESET', 'SUCCESS', 'OPERATOR', v_id::text, NULL, NULL,
                jsonb_build_object('via', 'SERVER_COMMAND', 'wasEnrolled', coalesce(v_enrolled, false),
                                   'recoveryCodes', v_codes, 'trustedDevices', v_devices)::text)
        ON CONFLICT (id) DO NOTHING;
        EXIT WHEN FOUND;
        v_try := v_try + 1;
        IF v_try >= 20 THEN
            RAISE EXCEPTION '감사 기록 ID 를 만들지 못했다';
        END IF;
    END LOOP;
    RETURN 'RESET';
END
$fn$;
