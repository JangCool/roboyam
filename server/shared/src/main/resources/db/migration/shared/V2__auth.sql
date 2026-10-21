-- 가입·인증 (개발 1단계). 근거: docs/api-auth.md, docs/data-model.md
-- ID 는 시간순 bigint(Snowflake)를 애플리케이션이 발급한다 — DEFAULT 가 없다.

-- ── 회원 ─────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                bigint       PRIMARY KEY,
    email             varchar(254),                       -- 소문자로 정규화해 저장. 소셜 가입자가 안 주면 NULL. 탈퇴하면 NULL
    password_hash     varchar(100),                       -- 소셜로만 가입했으면 NULL
    role              varchar(16)  NOT NULL DEFAULT 'USER',
    status            varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    email_verified_at timestamptz,
    terms_version     varchar(16)  NOT NULL,
    terms_agreed_at   timestamptz  NOT NULL,
    age_confirmed_at  timestamptz  NOT NULL,              -- 만 14세 이상 확인
    last_login_at     timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    CONSTRAINT ck_users_role   CHECK (role   IN ('USER', 'STAFF', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'WITHDRAWN'))
);
CREATE UNIQUE INDEX ux_users_email ON users (email) WHERE email IS NOT NULL;

-- ── 프로필 (닉네임·등급) ─────────────────────────────────────────────────────
CREATE TABLE profiles (
    user_id             bigint       PRIMARY KEY REFERENCES users (id),
    nickname            varchar(12)  NOT NULL,
    bio                 varchar(100),
    profile_image       varchar(255),
    nickname_image      varchar(255),
    grade               smallint     NOT NULL DEFAULT 1,  -- 1 새싹 · 2 일반 · 3 단골 · 4 베테랑
    nickname_changed_at timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_profiles_grade CHECK (grade BETWEEN 1 AND 4)
);
-- 닉네임 중복 불가(대소문자 무시)
CREATE UNIQUE INDEX ux_profiles_nickname ON profiles (lower(nickname));

-- ── 소셜 계정 연결 ───────────────────────────────────────────────────────────
CREATE TABLE user_identities (
    id               bigint       PRIMARY KEY,
    user_id          bigint       NOT NULL REFERENCES users (id),
    provider         varchar(16)  NOT NULL,               -- GOOGLE · KAKAO · NAVER
    provider_user_id varchar(128) NOT NULL,
    email            varchar(254),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_user_identities_provider UNIQUE (provider, provider_user_id)
);
CREATE INDEX ix_user_identities_user ON user_identities (user_id);

-- ── 이메일 인증·비밀번호 재설정 토큰 ─────────────────────────────────────────
-- 토큰 원문은 저장하지 않는다(메일에만 있다). SHA-256 해시만 둔다.
CREATE TABLE auth_tokens (
    id          bigint      PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    purpose     varchar(20) NOT NULL,                     -- VERIFY_EMAIL · RESET_PASSWORD
    token_hash  char(64)    NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_tokens_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
CREATE UNIQUE INDEX ux_auth_tokens_hash ON auth_tokens (token_hash);
CREATE INDEX ix_auth_tokens_user_purpose ON auth_tokens (user_id, purpose, created_at DESC);

-- ── 로그인 시도 제한 ─────────────────────────────────────────────────────────
-- 계정이 있든 없든 이메일 단위로 센다(계정 존재를 알리지 않기 위해). 이메일은 해시로만 둔다.
CREATE TABLE login_attempts (
    email_key    char(64)    PRIMARY KEY,
    fail_count   int         NOT NULL DEFAULT 0,
    locked_until timestamptz,
    updated_at   timestamptz NOT NULL DEFAULT now()
);

-- ── 등급 규칙 (설정값 — 승급 판정은 6단계 이후) ─────────────────────────────
CREATE TABLE grade_rules (
    grade        smallint    PRIMARY KEY,
    label        varchar(8)  NOT NULL,
    min_days     int         NOT NULL,                    -- 가입 후 일수
    min_activity int         NOT NULL,                    -- 글·댓글 합
    min_promoted int         NOT NULL                     -- 출하 완료글 수
);
INSERT INTO grade_rules (grade, label, min_days, min_activity, min_promoted) VALUES
    (1, '새싹',   0,   0, 0),
    (2, '일반',   7,  10, 0),
    (3, '단골',  30,  50, 0),
    (4, '베테랑', 90, 200, 1);

-- ── Spring Session JDBC (PostgreSQL) ────────────────────────────────────────
-- 앱 서버 2대가 세션을 DB 로 공유한다. 스키마는 여기서 만들고 Spring 은 만들지 않는다
-- (spring.session.jdbc.initialize-schema=never).
CREATE TABLE spring_session (
    primary_id            char(36)     NOT NULL,
    session_id            char(36)     NOT NULL,
    creation_time         bigint       NOT NULL,
    last_access_time      bigint       NOT NULL,
    max_inactive_interval int          NOT NULL,
    expiry_time           bigint       NOT NULL,
    principal_name        varchar(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
