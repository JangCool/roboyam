-- 소모임 1단계(기본) — docs/features/group-contract.md 「전체 개발 계획」 1단계(community docs/api-groups.md 1~7절을 로보얌에 맞춘 것).
-- 소모임 · 멤버(가입 신청 포함) · 소모임 설정(개설 규칙 · 매니저 · 공개 범위 · 운영 한도 네 구획) · 설정 변경 이력, 회원 알림 종류, 회원 권한 group.create.
-- 개설 신청은 소모임 행 자체다(status PENDING → ACTIVE | REJECTED(반려 사유) | DELETED(신청 취소)). 운영 기록(비활성·다시 열기)은 소모임 행의 칸 + 감사 기록.
-- 이후 단계가 붙을 자리: 매니저(3단계)는 group_members.role = 'MANAGER' + manager_perms + groups.manager_limit 이 이미 있다. 공개 범위(5단계)는 groups.visibility* 가 있다.
-- 글(2단계 — posts.group_id)·일정(4단계 — group_events)·공개 요청(5단계)은 그 단계가 표를 더한다.
-- 번호는 develop 병합(2026-10-26, 커머스 1-3 의 V89 다음) 때 V90~V93 으로 다시 매겼다.

-- ── 소모임 ──────────────────────────────────────────────────────────────────
-- id: Snowflake(앱 발급). name: 2~20자(앞뒤 공백을 다듬고 안쪽 공백을 하나로 — 앱). name_key: 공백을 모두 빼고 소문자(같은 이름 막기 — 앱이 만든다).
-- board_id: 글·말머리·투자 고지를 담는 소모임 게시판(boards.kind = 'GROUP' — 앱이 검사). 개설 뒤 바뀌지 않는다. 분류는 이 게시판이 할당된 분류(group_category_boards)로 정해지는 파생값이다.
--   게시판을 지우려면 그 게시판의 소모임이 없어야 한다(관리자 게시판 지우기가 먼저 409 로 막는다 — FK 는 마지막 방어선).
-- status: PENDING(개설 신청 중 — 신청자만 본다) · ACTIVE(열림) · REJECTED(반려 — reject_reason) · INACTIVE(운영 조치로 비활성) · DELETED(해산·신청 취소 — deleted_at).
-- leader_id: 모임장(한 명). creator_id: 개설을 신청한 회원. member_count: 모임장 포함 지금 멤버 수 — 소모임 행을 잠그고 같은 트랜잭션에서 바꾼다.
-- recruiting: 모임장이 모집을 마감하면 false. manager_limit: 이 소모임의 매니저 인원 상한(개설 때 매니저 규칙의 기본 인원을 복사 — 3단계에서 모임장이 바꾼다).
-- visibility: PUBLIC · PRIVATE(기본) — 공개 범위 기능(5단계)이 꺼져 있으면 개설은 늘 PRIVATE. visibility_changed_*: 마지막 변경(개설 때 정한 값이면 NULL).
-- stock_agreed: 투자 고지 게시판에서 「공부·산업 해설 목적만」 추가 동의를 받았다. kakao_*·discord_*: 채팅 링크(https 의 허용 주소만 — 앱).
-- reviewed_by/at: 개설을 승인·반려한 운영자(자유 개설이면 NULL). last_activity_at: 최근 활동(지금은 개설·가입 — 2단계에서 글).
-- deactivated_*·deactivate_reason·deactivate_case_id: 마지막으로 비활성이 된 때의 기록(다시 열어도 남는다, 신고 처리로 비활성이 되면 그 사건 — 지금은 신고 대상에 소모임이 없어 늘 NULL).
-- reactivated_*·reactivate_memo: 마지막 다시 열기(운영자만 보는 메모). 비활성이 다시 되면 비운다.
CREATE TABLE groups (
    id                     bigint        PRIMARY KEY,
    name                   varchar(20)   NOT NULL,
    name_key               varchar(40)   NOT NULL,
    description            varchar(500)  NOT NULL,
    board_id               bigint        NOT NULL REFERENCES boards (id),
    join_type              varchar(10)   NOT NULL,
    capacity               smallint      NOT NULL,
    offline                boolean       NOT NULL DEFAULT false,
    recruiting             boolean       NOT NULL DEFAULT true,
    visibility             varchar(10)   NOT NULL DEFAULT 'PRIVATE',
    visibility_changed_at  timestamptz,
    visibility_changed_by  bigint        REFERENCES users (id) ON DELETE SET NULL,
    status                 varchar(10)   NOT NULL,
    leader_id              bigint        NOT NULL REFERENCES users (id),
    creator_id             bigint        REFERENCES users (id) ON DELETE SET NULL,
    member_count           integer       NOT NULL DEFAULT 0,
    manager_limit          smallint      NOT NULL DEFAULT 5,
    stock_agreed           boolean       NOT NULL DEFAULT false,
    reject_reason          varchar(200),
    reviewed_by            bigint        REFERENCES operators (id) ON DELETE SET NULL,
    reviewed_at            timestamptz,
    kakao_url              varchar(300),
    kakao_label            varchar(30),
    discord_url            varchar(300),
    discord_label          varchar(30),
    requested_at           timestamptz   NOT NULL,
    opened_at              timestamptz,
    last_activity_at       timestamptz,
    deleted_at             timestamptz,
    deactivated_at         timestamptz,
    deactivated_by         bigint        REFERENCES operators (id) ON DELETE SET NULL,
    deactivate_reason      varchar(200),
    deactivate_case_id     bigint,
    reactivated_at         timestamptz,
    reactivated_by         bigint        REFERENCES operators (id) ON DELETE SET NULL,
    reactivate_memo        varchar(200),
    CONSTRAINT ck_groups_name CHECK (char_length(name) BETWEEN 2 AND 20 AND name = btrim(name)),
    CONSTRAINT ck_groups_name_key CHECK (char_length(name_key) BETWEEN 1 AND 40),
    CONSTRAINT ck_groups_description CHECK (char_length(description) BETWEEN 1 AND 500),
    CONSTRAINT ck_groups_join_type CHECK (join_type IN ('OPEN', 'APPROVAL')),
    CONSTRAINT ck_groups_capacity CHECK (capacity BETWEEN 5 AND 200),
    CONSTRAINT ck_groups_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_groups_status CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'INACTIVE', 'DELETED')),
    CONSTRAINT ck_groups_member_count CHECK (member_count >= 0),
    CONSTRAINT ck_groups_manager_limit CHECK (manager_limit >= 1),
    CONSTRAINT ck_groups_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);
-- 같은 이름은 열림·신청 중·비활성끼리 하나(비활성을 다시 열 때 겹치지 않게 — community 는 열림·신청 중만)
CREATE UNIQUE INDEX ux_groups_name_key_live ON groups (name_key) WHERE status IN ('PENDING', 'ACTIVE', 'INACTIVE');
-- 공개 목록(열림)·관리자 목록(열림·비활성, 최신순)
CREATE INDEX ix_groups_status_opened ON groups (status, opened_at DESC, id DESC);
-- 분류·게시판별 소모임 수(분류 groupCount, 개설 시 그 게시판)
CREATE INDEX ix_groups_board ON groups (board_id, status);
-- 내가 모임장인 소모임 수(1인 상한) · 내 개설 신청 · 하루 개설 한도
CREATE INDEX ix_groups_leader ON groups (leader_id, status);
CREATE INDEX ix_groups_creator ON groups (creator_id, requested_at DESC);
-- 이름·소개 검색(열림 한정) — 질의는 lower(칸) LIKE '%단어%'(V68 과 같은 방식)
CREATE INDEX ix_groups_name_trgm ON groups USING gin (lower(name) gin_trgm_ops) WHERE status = 'ACTIVE';
CREATE INDEX ix_groups_description_trgm ON groups USING gin (lower(description) gin_trgm_ops) WHERE status = 'ACTIVE';

-- ── 멤버 · 가입 신청 ───────────────────────────────────────────────────────────
-- 회원 × 소모임 한 행(기본 키). status: ACTIVE(멤버) · APPLIED(가입 신청 대기) · REJECTED(신청 거절 — reject_reason, decided_at 부터 재신청 대기) · LEFT(탈퇴) · KICKED(강퇴).
-- role: LEADER(모임장 한 명) · MANAGER(매니저 — 3단계) · MEMBER. manager_perms: 맡은 일 비트(APPLICATIONS 1 · KICK 2 · RECRUITING 4 · EVENTS 8 · UNPUBLISH 16 — 코드가 정한다),
--   매니저만 1개 이상이고 그 밖의 역할은 0. 나가기·강퇴·다시 가입하면 MEMBER · 0 으로 돌아간다(앱).
-- kicked_before: 한 번이라도 강퇴되면 true — 그 뒤로는 자유 가입 모임이어도 신청으로만 들어온다. decided_by: 신청을 승인·거절한 회원(모임장·매니저).
-- last_seen_at: 글 탭을 본 시각(2단계 「새 글 수」).
CREATE TABLE group_members (
    group_id         bigint        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id          bigint        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role             varchar(10)   NOT NULL DEFAULT 'MEMBER',
    manager_perms    smallint      NOT NULL DEFAULT 0,
    role_changed_at  timestamptz,
    role_changed_by  bigint        REFERENCES users (id) ON DELETE SET NULL,
    status           varchar(10)   NOT NULL,
    message          varchar(100),
    applied_at       timestamptz,
    joined_at        timestamptz,
    decided_at       timestamptz,
    decided_by       bigint        REFERENCES users (id) ON DELETE SET NULL,
    reject_reason    varchar(100),
    left_at          timestamptz,
    kicked_at        timestamptz,
    kicked_before    boolean       NOT NULL DEFAULT false,
    last_seen_at     timestamptz,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT ck_group_members_role CHECK (role IN ('LEADER', 'MANAGER', 'MEMBER')),
    CONSTRAINT ck_group_members_status CHECK (status IN ('ACTIVE', 'APPLIED', 'REJECTED', 'LEFT', 'KICKED')),
    CONSTRAINT ck_group_members_manager_perms CHECK (manager_perms BETWEEN 0 AND 31 AND (role = 'MANAGER') = (manager_perms > 0))
);
-- 멤버 탭·신규 가입자·가입 신청 목록
CREATE INDEX ix_group_members_group ON group_members (group_id, status, joined_at, user_id);
-- 내 소모임 · 하루 가입 한도
CREATE INDEX ix_group_members_user ON group_members (user_id, status);
-- 매니저 목록·인원(3단계)
CREATE INDEX ix_group_members_managers ON group_members (group_id) WHERE role = 'MANAGER' AND status = 'ACTIVE';

-- ── 소모임 설정(네 구획) ──────────────────────────────────────────────────────
-- 단일 행(id = 1). 칸이 NULL 이면 그 칸은 서버 설정 파일(roboyam.groups.*)의 값이다 — 구획의 *_updated_at 이 NULL 이면 「저장된 값 없음 → 기본값으로 도는 중」(fromDefaults).
-- 저장은 구획마다 따로(그 구획 칸 모두). 서버는 캐시하지 않는다(저장하면 다음 요청부터 모든 서버에 적용). 범위 CHECK 는 화면의 limits 와 같다.
-- min_grade: 개설 최소 등급(등급 키) — 등급을 지우면 NULL(= 기본값)로 돌아간다.
CREATE TABLE group_settings (
    id                     smallint      PRIMARY KEY DEFAULT 1,
    -- 개설 규칙
    creation_mode          varchar(10),
    min_grade              varchar(19)   REFERENCES grades (grade_key) ON DELETE SET NULL,
    max_per_owner          smallint,
    creation_updated_by    bigint        REFERENCES operators (id) ON DELETE SET NULL,
    creation_updated_at    timestamptz,
    -- 매니저(3단계가 읽는다)
    managers_enabled       boolean,
    manager_limit_default  smallint,
    manager_limit_max      smallint,
    manager_updated_by     bigint        REFERENCES operators (id) ON DELETE SET NULL,
    manager_updated_at     timestamptz,
    -- 공개 범위(5단계가 읽는다)
    visibility_enabled     boolean,
    visibility_cooldown_days smallint,
    visibility_retry_hours smallint,
    visibility_reject_limit smallint,
    visibility_daily_limit smallint,
    visibility_text_max    smallint,
    visibility_notice_sent_at timestamptz,
    visibility_updated_by  bigint        REFERENCES operators (id) ON DELETE SET NULL,
    visibility_updated_at  timestamptz,
    -- 운영 한도
    reapply_days           smallint,
    capacity_min           smallint,
    capacity_max           smallint,
    trending_size          smallint,
    trending_active_days   smallint,
    event_max_upcoming     smallint,
    event_max_days_ahead   smallint,
    event_max_length_days  smallint,
    policy_updated_by      bigint        REFERENCES operators (id) ON DELETE SET NULL,
    policy_updated_at      timestamptz,
    CONSTRAINT ck_group_settings_one CHECK (id = 1),
    CONSTRAINT ck_group_settings_creation CHECK ((creation_mode IS NULL OR creation_mode IN ('APPROVAL', 'OPEN'))
        AND (max_per_owner IS NULL OR max_per_owner BETWEEN 1 AND 10)),
    CONSTRAINT ck_group_settings_manager CHECK ((manager_limit_default IS NULL OR manager_limit_default BETWEEN 1 AND 10)
        AND (manager_limit_max IS NULL OR manager_limit_max BETWEEN 1 AND 10)
        AND (manager_limit_default IS NULL OR manager_limit_max IS NULL OR manager_limit_default <= manager_limit_max)),
    CONSTRAINT ck_group_settings_visibility CHECK ((visibility_cooldown_days IS NULL OR visibility_cooldown_days BETWEEN 0 AND 365)
        AND (visibility_retry_hours IS NULL OR visibility_retry_hours BETWEEN 0 AND 720)
        AND (visibility_reject_limit IS NULL OR visibility_reject_limit BETWEEN 1 AND 3)
        AND (visibility_daily_limit IS NULL OR visibility_daily_limit BETWEEN 1 AND 100)
        AND (visibility_text_max IS NULL OR visibility_text_max BETWEEN 10 AND 100)),
    CONSTRAINT ck_group_settings_policy CHECK ((reapply_days IS NULL OR reapply_days BETWEEN 0 AND 90)
        AND (capacity_min IS NULL OR capacity_min BETWEEN 5 AND 200)
        AND (capacity_max IS NULL OR capacity_max BETWEEN 5 AND 200)
        AND (capacity_min IS NULL OR capacity_max IS NULL OR capacity_min <= capacity_max)
        AND (trending_size IS NULL OR trending_size BETWEEN 1 AND 20)
        AND (trending_active_days IS NULL OR trending_active_days BETWEEN 1 AND 90)
        AND (event_max_upcoming IS NULL OR event_max_upcoming BETWEEN 1 AND 100)
        AND (event_max_days_ahead IS NULL OR event_max_days_ahead BETWEEN 1 AND 730)
        AND (event_max_length_days IS NULL OR event_max_length_days BETWEEN 1 AND 30))
);
-- 행은 하나 넣어 둔다(모든 칸 NULL = 기본값) — 저장은 늘 UPDATE 다
INSERT INTO group_settings (id) VALUES (1);

-- ── 설정 변경 이력 ───────────────────────────────────────────────────────────
-- 값이 실제로 바뀐 저장마다 한 줄(메일 설정 이력 V54 와 같은 꼴 + 구획). 고치거나 지우지 않는다.
-- section: CREATION · MANAGER · VISIBILITY · POLICY. action: CREATE(그 구획을 처음 저장 — 기본값에서) · UPDATE.
-- changes = [{field, before, after}] — 값은 코드 글자(APPROVAL·true·7·등급 키)이고, 조회가 요청 언어의 표시 글자로 바꾼다.
-- operator_id: 저장한 운영자(지워져도 남는다 — FK 없음, 감사 기록과 같다).
CREATE TABLE group_setting_history (
    id           bigint       PRIMARY KEY,
    section      varchar(12)  NOT NULL,
    action       varchar(10)  NOT NULL,
    operator_id  bigint,
    changed_at   timestamptz  NOT NULL,
    changes      jsonb        NOT NULL,
    CONSTRAINT ck_group_setting_history_section CHECK (section IN ('CREATION', 'MANAGER', 'VISIBILITY', 'POLICY')),
    CONSTRAINT ck_group_setting_history_action CHECK (action IN ('CREATE', 'UPDATE')),
    CONSTRAINT ck_group_setting_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);
CREATE INDEX ix_group_setting_history_changed ON group_setting_history (changed_at DESC, id DESC);
CREATE INDEX ix_group_setting_history_section ON group_setting_history (section, changed_at DESC, id DESC);

-- ── 회원 알림 종류: 소모임(개설 승인·반려 · 가입 신청 · 가입 승인·거절 · 강퇴 · 해산 · 모임장 넘김 · 비활성 · 다시 열림) ──
-- 알림 설정 행(member_event_settings)은 넣지 않는다 — 줄이 없으면 코드의 처음 값(MemberEvent)을 쓴다(V87 과 같다). 종류 목록은 V87 의 목록 ∪ 소모임 10종.
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED'));

-- ── 회원 권한 group.create(소모임 개설 신청) ──────────────────────────────────
-- 기능이 생겨 featureReady = true(서버가 뜰 때 코드 값으로 다시 맞춘다). 지금 글쓰기(post.write)를 가진 회원 역할에 같이 준다 —
-- 이 마이그레이션 전에는 개설 기능이 없었으므로 「글을 쓸 수 있는 회원은 개설을 신청할 수 있다」로 시작한다(사업자 회원은 글쓰기가 없어 받지 않는다).
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at)
VALUES ('group.create', 'MEMBER', 'memberSite', 'USE', true, now())
ON CONFLICT (code) DO UPDATE SET feature_ready = true, synced_at = now();
-- id 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V53·V54 와 같은 마이그레이션용 노드) + 순번
INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | (row_number() OVER (ORDER BY rp.role_id))::bigint,
       rp.role_id, 'group.create', now()
  FROM role_permissions rp
 WHERE rp.permission_code = 'post.write'
   AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_code = 'group.create');
