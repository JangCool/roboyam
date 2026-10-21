-- 운영자 · 역할 · 권한 · 초대 · 감사 기록 — docs/features/roles-contract.md 1단계, docs/domain/operator-permissions.md
--
-- 운영자(관리자 앱 계정)는 회원(users)과 별도 테이블이다. 역할은 운영자 역할(관리자 앱 권한)과 회원 역할(공개 사이트 회원의 능력) 두 축이 한 표(roles)에 있다.
-- ID 는 앱이 발급하는 Snowflake bigint 다(DEFAULT 없음). 역할은 사람이 정한 아이디(ROLE_...)가 키다 — 만든 뒤에는 바꾸지 않는다.
-- 권한 목록의 원천은 코드(enum)이고 permissions 표는 서버가 뜰 때 코드와 맞춘다(role_permissions 가 참조할 수 있게 하는 사본).

-- ── 운영자 ───────────────────────────────────────────────────────────────────
-- 상태 4개(승인·잠금·만료·사용)와 계정 기간. 로그인은 승인됨 · 잠기지 않음 · 만료 표시 없음 · 사용 중 · 기간 안일 때만 된다.
-- 루트(최고 관리자)는 설치 마법사가 만드는 계정 하나(is_root)이고 어떤 변경도 거절된다 — 모든 권한을 가진다(역할이 아니라 계정의 속성).
CREATE TABLE operators (
    id               bigint       PRIMARY KEY,
    email            varchar(254) NOT NULL,                      -- 소문자로 정규화해 저장. 로그인 아이디
    operator_name    varchar(20)  NOT NULL,
    password_hash    varchar(100) NOT NULL,
    is_root          boolean      NOT NULL DEFAULT false,
    approved         boolean      NOT NULL DEFAULT false,        -- 승인됨 (false = 승인 대기)
    locked           boolean      NOT NULL DEFAULT false,        -- 잠김 (운영자가 잠근 것. 로그인 실패 10분 잠금은 login_attempts)
    expired          boolean      NOT NULL DEFAULT false,        -- 만료 표시
    enabled          boolean      NOT NULL DEFAULT true,         -- 사용 중 (false = 사용 중지)
    account_start_at timestamptz,                                -- 이 시각부터 (비면 제한 없음)
    account_end_at   timestamptz,                                -- 이 시각까지 (비면 제한 없음)
    last_login_at    timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_operators_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_operators_period CHECK (account_start_at IS NULL OR account_end_at IS NULL OR account_start_at <= account_end_at),
    -- 루트는 늘 쓸 수 있는 상태이고 기간 제한이 없다
    CONSTRAINT ck_operators_root_state CHECK (NOT is_root OR (approved AND NOT locked AND NOT expired AND enabled
                                                              AND account_start_at IS NULL AND account_end_at IS NULL))
);
CREATE UNIQUE INDEX ux_operators_email ON operators (email);
-- 루트는 하나뿐이다: is_root 가 true 인 행만 색인하고 유일하게 한다
CREATE UNIQUE INDEX ux_operators_single_root ON operators (is_root) WHERE is_root;
CREATE INDEX ix_operators_created ON operators (created_at DESC);

-- ── 권한 (코드 enum 과 맞춘 사본) ────────────────────────────────────────────
CREATE TABLE permissions (
    code          varchar(60) PRIMARY KEY,                       -- 영역.행위 (예: role.manage)
    axis          varchar(8)  NOT NULL,                          -- OPERATOR · MEMBER
    type_id       varchar(30) NOT NULL,                          -- 운영자: 세부 유형 id(operatorRole …) / 회원: memberSite · memberBoard
    category      varchar(12) NOT NULL,                          -- 운영자: READ · MANAGE · CONFIG · SENSITIVE / 회원: USE · BOARD
    feature_ready boolean     NOT NULL,                          -- 그 일을 하는 화면(기능)이 있는가 — 표시용
    synced_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_permissions_axis CHECK (axis IN ('OPERATOR', 'MEMBER'))
);

-- ── 역할 ─────────────────────────────────────────────────────────────────────
-- 역할 유형은 고정 분류라 표가 없다(코드가 원천). 운영자 역할 유형은 op_<세부 유형 id>, 회원 역할 유형은 4종.
CREATE TABLE roles (
    role_id      varchar(30)  PRIMARY KEY,                       -- ROLE_ + 대문자·숫자·밑줄
    role_type_id varchar(40)  NOT NULL,
    axis         varchar(8)   NOT NULL,
    role_name    varchar(50)  NOT NULL,
    description  varchar(200),
    scope        varchar(8)   NOT NULL DEFAULT 'SITE',           -- SITE 사이트 전체 · BOARD 게시판 하나(매니저·스텝)
    grant_mode   varchar(10)  NOT NULL DEFAULT 'MANUAL',         -- AUTO 자동 · APPOINTED 지정 · MANUAL 수동
    builtin      boolean      NOT NULL DEFAULT false,            -- 제품이 넣은 역할. 지울 수 없다
    created_by   bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_roles_id_format CHECK (role_id ~ '^ROLE_[A-Z0-9_]+$'),
    CONSTRAINT ck_roles_axis CHECK (axis IN ('OPERATOR', 'MEMBER')),
    CONSTRAINT ck_roles_scope CHECK (scope IN ('SITE', 'BOARD')),
    CONSTRAINT ck_roles_grant_mode CHECK (grant_mode IN ('AUTO', 'APPOINTED', 'MANUAL')),
    -- 운영자 역할은 op_ 유형 + 사이트 범위 + 수동 부여, 회원 역할은 회원 유형 4종
    CONSTRAINT ck_roles_type CHECK (
        (axis = 'OPERATOR' AND role_type_id LIKE 'op\_%' AND scope = 'SITE' AND grant_mode = 'MANUAL')
        OR (axis = 'MEMBER' AND role_type_id IN ('memberGrade', 'memberKind', 'memberBoard', 'memberCustom')))
);
CREATE INDEX ix_roles_type ON roles (role_type_id);

-- 역할이 가진 권한 — 통째로 바꾼다. (역할, 권한) 쌍은 유일하다.
CREATE TABLE role_permissions (
    id              bigint      PRIMARY KEY,
    role_id         varchar(30) NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    permission_code varchar(60) NOT NULL REFERENCES permissions (code),
    granted_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_role_permissions UNIQUE (role_id, permission_code)
);
CREATE INDEX ix_role_permissions_code ON role_permissions (permission_code);

-- 운영자가 받은 역할 — 여럿을 받고 권한은 합집합이다. 쓰는 운영자가 있는 역할은 지울 수 없다(RESTRICT, 앱이 먼저 막는다).
CREATE TABLE operator_roles (
    id          bigint      PRIMARY KEY,
    operator_id bigint      NOT NULL REFERENCES operators (id) ON DELETE CASCADE,
    role_id     varchar(30) NOT NULL REFERENCES roles (role_id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ux_operator_roles UNIQUE (operator_id, role_id)
);
CREATE INDEX ix_operator_roles_role ON operator_roles (role_id);

-- 회원이 가진 회원 역할 — 등급 1 + 회원 유형 1 + 게시판 지정 0~N + 사용자 정의 0~N. 저장 자리만 만든다(부여 API 는 후속 단계).
-- board_id 는 게시판 단위 역할(매니저·스텝)이 가리키는 게시판 — 게시판 표가 생길 때 참조를 건다.
CREATE TABLE user_roles (
    id          bigint      PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    role_id     varchar(30) NOT NULL REFERENCES roles (role_id),
    board_id    bigint,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by bigint      REFERENCES operators (id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX ux_user_roles ON user_roles (user_id, role_id, COALESCE(board_id, 0));
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- ── 운영자 초대 ──────────────────────────────────────────────────────────────
-- 1회용 초대 링크. 토큰 원문은 저장하지 않는다(메일에만 있다) — SHA-256 해시만 둔다. 유효기간은 기본 72시간(가정).
-- 수락하지 않은 초대는 같은 이메일에 하나뿐이다. 취소하면 행을 지운다.
CREATE TABLE operator_invitations (
    id          bigint       PRIMARY KEY,
    email       varchar(254) NOT NULL,
    token_hash  char(64)     NOT NULL,
    invited_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    expires_at  timestamptz  NOT NULL,
    consumed_at timestamptz,                                     -- 수락한 시각 (수락은 후속 단계)
    CONSTRAINT ck_operator_invitations_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_operator_invitations_expiry CHECK (expires_at > created_at)
);
CREATE UNIQUE INDEX ux_operator_invitations_token ON operator_invitations (token_hash);
CREATE UNIQUE INDEX ux_operator_invitations_pending_email ON operator_invitations (email) WHERE consumed_at IS NULL;

-- 초대할 때 정한 역할 — 수락한 운영자가 받는다. 역할을 지우면 초대에서도 빠진다.
CREATE TABLE operator_invitation_roles (
    id            bigint      PRIMARY KEY,
    invitation_id bigint      NOT NULL REFERENCES operator_invitations (id) ON DELETE CASCADE,
    role_id       varchar(30) NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    CONSTRAINT ux_operator_invitation_roles UNIQUE (invitation_id, role_id)
);

-- ── 감사 기록 ────────────────────────────────────────────────────────────────
-- 역할·권한·운영자 상태 변경, 로그인 성공/실패, 권한 거부. 쓰기만 한다. 비밀번호·토큰은 남기지 않는다. 보관 1년(가정, 정리 배치는 후속).
-- actor_id 에 외래키를 걸지 않는다 — 반려로 운영자 행이 지워져도 기록은 남는다.
CREATE TABLE audit_logs (
    id          bigint       PRIMARY KEY,
    occurred_at timestamptz  NOT NULL DEFAULT now(),
    actor_type  varchar(10)  NOT NULL,                           -- OPERATOR · SYSTEM · ANONYMOUS
    actor_id    bigint,
    action      varchar(40)  NOT NULL,
    result      varchar(8)   NOT NULL,                           -- SUCCESS · FAILURE · DENIED
    target_type varchar(30),
    target_id   varchar(64),
    ip          varchar(64),
    request_id  varchar(64),
    detail      text,                                            -- 짧은 JSON (granted/revoked 등)
    CONSTRAINT ck_audit_logs_actor_type CHECK (actor_type IN ('OPERATOR', 'SYSTEM', 'ANONYMOUS')),
    CONSTRAINT ck_audit_logs_result CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED'))
);
CREATE INDEX ix_audit_logs_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_id, occurred_at DESC) WHERE actor_id IS NOT NULL;
CREATE INDEX ix_audit_logs_action ON audit_logs (action, occurred_at DESC);
