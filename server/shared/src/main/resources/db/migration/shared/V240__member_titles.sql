-- 회원 칭호(docs/features/member-title-contract.md 「구현 결정」 2026-11-17)
--
-- 등급(경험치 사다리)과 따로 「무엇을 잘하는 사람인가」를 보여 주는 칭호. 한 회원이 여러 개를 갖고, 자동(AUTO)·신청 심사(APPLY)·운영자 수여(AWARD) 세 길로 받는다.
-- 칭호는 권한을 주지 않는다(등급 역할과 섞지 않는다). AI 회원(ai_member_profiles)·운영팀 계정(operator_member_accounts)은 늘 제외 — 앱이 판정한다.
-- users 외래키를 걸지 않는다(포인트·커머스 새 표 규칙 — 탈퇴 정리는 회원 쪽이 한다). 칭호 표 안쪽(titles)만 외래키로 잇는다.

-- ── 1) 칭호 정의 ─────────────────────────────────────────────────────────────
-- title_key: 불변 식별자(API 경로의 {key}). 기본 칭호는 고정 키, 운영자가 만든 칭호는 서버가 't' + 36진 ID 로 발급한다.
-- name: 표시 이름 — 공백을 뺀 6자까지(「출석 100일」이 기본 칭호라 공백은 세지 않는다, 사용자 계약 「최대 6자」의 해석). 등급 이름과 겹치면 앱이 400.
-- icon: 화면 도트 그림 키(계약 「아이콘」). chip: 칩 색 PURPLE(수여·심사)·GREEN(자동).
-- method: AUTO 자동 · APPLY 신청·심사 · AWARD 운영자 수여. reviewer: 신청 칭호를 심사하는 사람 — OPERATOR 운영자만 · OPERATOR_AND_BOARD_MANAGER 운영자 + 연결 게시판 매니저.
-- board_id: 연결 게시판(매니저 심사·조건의 기본 게시판). 설치 마법사가 게시판을 마이그레이션 뒤에 만들므로 기본 칭호는 board_slug(처음 값)로 찾는다 — 운영자가 고르면 board_id 를 쓰고 slug 를 비운다.
-- conditions: 조건 목록 JSON [{type, value, boardId?, boardSlug?, gradeKey?}] — type GRADE_AT_LEAST(gradeKey) · POSTS(value, 게시판) · POPULAR_DAYS(value) ·
--             ATTENDANCE_DAYS(value) · NO_REPORT_HIDDEN. 모두 채우면 받는다(자동은 배치가 주고, 신청은 신청할 수 있다).
-- tier_metric·tier_bronze·tier_silver·tier_gold: 단계(동·은·금) — 무엇을 세는지(POPULAR_DAYS·ATTENDANCE_DAYS·POSTS·TRIAL_KEPT)와 단계 문턱. 단계가 없으면 넷 다 NULL.
-- benefit_*: 혜택 표시(체험단 먼저 신청·매거진 첫 화면 후보·한정 아바타 테두리) — 기능 쪽이 칭호를 읽어 판단한다(칭호는 권한을 주지 않는다).
-- dormant_hide: 6개월 활동(글·댓글) 없으면 대표 칭호를 숨김(회수 아님 — 다시 쓰면 돌아온다).
-- builtin: 기본 칭호(키·받는 방법을 바꿀 수 없다). enabled: 꺼지면 어디에도 보이지 않고 자동 지급·신청도 멈춘다(가진 줄은 남는다).
CREATE TABLE titles (
    id                      bigint       PRIMARY KEY,
    title_key               varchar(32)  NOT NULL,
    name                    varchar(12)  NOT NULL,
    name_en                 varchar(40),
    icon                    varchar(16)  NOT NULL,
    chip                    varchar(8)   NOT NULL,
    method                  varchar(8)   NOT NULL,
    reviewer                varchar(32)  NOT NULL DEFAULT 'OPERATOR',
    board_id                bigint       REFERENCES boards (id) ON DELETE SET NULL,
    board_slug              varchar(50),
    conditions              jsonb        NOT NULL DEFAULT '[]'::jsonb,
    tier_metric             varchar(24),
    tier_bronze             integer,
    tier_silver             integer,
    tier_gold               integer,
    benefit_trial_priority  boolean      NOT NULL DEFAULT false,
    benefit_magazine        boolean      NOT NULL DEFAULT false,
    benefit_avatar_frame    boolean      NOT NULL DEFAULT false,
    dormant_hide            boolean      NOT NULL DEFAULT false,
    builtin                 boolean      NOT NULL DEFAULT false,
    enabled                 boolean      NOT NULL DEFAULT true,
    sort_order              integer      NOT NULL DEFAULT 0,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              bigint,
    CONSTRAINT ux_titles_key UNIQUE (title_key),
    CONSTRAINT ck_titles_key CHECK (title_key ~ '^[a-z0-9][a-z0-9-]{1,31}$'),
    CONSTRAINT ck_titles_name CHECK (name = btrim(name) AND char_length(replace(name, ' ', '')) BETWEEN 1 AND 6),
    CONSTRAINT ck_titles_icon CHECK (icon IN ('pen', 'trophy', 'wreath', 'mag', 'mic', 'popular', 'cal', 'medal', 'bulb', 'heart')),
    CONSTRAINT ck_titles_chip CHECK (chip IN ('PURPLE', 'GREEN')),
    CONSTRAINT ck_titles_method CHECK (method IN ('AUTO', 'APPLY', 'AWARD')),
    CONSTRAINT ck_titles_reviewer CHECK (reviewer IN ('OPERATOR', 'OPERATOR_AND_BOARD_MANAGER')),
    CONSTRAINT ck_titles_conditions CHECK (jsonb_typeof(conditions) = 'array'),
    CONSTRAINT ck_titles_tiers CHECK (
        (tier_metric IS NULL AND tier_bronze IS NULL AND tier_silver IS NULL AND tier_gold IS NULL)
        OR (tier_metric IN ('POPULAR_DAYS', 'ATTENDANCE_DAYS', 'POSTS', 'TRIAL_KEPT')
            AND tier_bronze BETWEEN 1 AND 100000 AND tier_silver > tier_bronze AND tier_gold > tier_silver AND tier_gold <= 100000))
);
-- 이름은 공백·대소문자를 무시하고 하나(앱이 먼저 409 로 막고 이 색인이 마지막으로 막는다)
CREATE UNIQUE INDEX ux_titles_name ON titles (lower(replace(name, ' ', '')));

-- ── 2) 회원이 가진 칭호(받은 기록) ───────────────────────────────────────────────
-- period: 이달의 작가 'YYYY-MM' · 올해의 작가 'YYYY' · 그 밖 ''(빈 값). 같은 칭호·같은 기간의 살아 있는 줄은 하나(부분 유일 색인).
-- tier: 단계 칭호의 지금 단계(BRONZE·SILVER·GOLD) — 오르면 같은 줄을 고치고 tier_changed_at·seen_at(다시 축하) 을 바꾼다.
-- source: AUTO(배치·명예 작가 즉시 지급) · AWARD(운영자 수여) · APPLICATION(신청 승인). granted_by(운영자)·granted_by_member(게시판 매니저 승인) 중 하나 또는 둘 다 NULL(자동).
-- seen_at: 축하 창을 닫은 때(NULL = 다음 접속 때 한 번 축하). 회수해도 줄은 남긴다(revoked_at·revoked_by·revoked_reason).
CREATE TABLE member_titles (
    id                  bigint        PRIMARY KEY,
    user_id             bigint        NOT NULL,
    title_id            bigint        NOT NULL REFERENCES titles (id) ON DELETE CASCADE,
    tier                varchar(8),
    period              varchar(7)    NOT NULL DEFAULT '',
    source              varchar(12)   NOT NULL,
    application_id      bigint,
    granted_at          timestamptz   NOT NULL,
    granted_by          bigint,
    granted_by_member   bigint,
    grant_reason        varchar(500),
    tier_changed_at     timestamptz,
    seen_at             timestamptz,
    revoked_at          timestamptz,
    revoked_by          bigint,
    revoked_reason      varchar(500),
    CONSTRAINT ck_member_titles_tier CHECK (tier IS NULL OR tier IN ('BRONZE', 'SILVER', 'GOLD')),
    CONSTRAINT ck_member_titles_period CHECK (period = '' OR period ~ '^[0-9]{4}(-(0[1-9]|1[0-2]))?$'),
    CONSTRAINT ck_member_titles_source CHECK (source IN ('AUTO', 'AWARD', 'APPLICATION')),
    CONSTRAINT ck_member_titles_revoked CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);
CREATE UNIQUE INDEX ux_member_titles_active ON member_titles (user_id, title_id, period) WHERE revoked_at IS NULL;
-- 작성자 줄·프로필·내 칭호(회원 묶음 IN) · 칭호별 보유자·명예의 전당 · 주고 뺀 기록(최신 순)
CREATE INDEX ix_member_titles_user ON member_titles (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_member_titles_title ON member_titles (title_id, period) WHERE revoked_at IS NULL;
CREATE INDEX ix_member_titles_log ON member_titles (title_id, granted_at DESC, id DESC);

-- ── 3) 칭호 신청 ─────────────────────────────────────────────────────────────
-- status: PENDING 대기 · APPROVED 승인 · REJECTED 거절(사유 필수, reapply_at = 다시 신청할 수 있는 날 — 기본 30일 뒤). 회원·칭호마다 대기는 하나.
-- reviewed_by(운영자)·reviewed_by_member(게시판 매니저) — 매니저는 자기 게시판에 연결된 칭호만, 자기 신청은 심사할 수 없다(앱).
CREATE TABLE title_applications (
    id                  bigint        PRIMARY KEY,
    user_id             bigint        NOT NULL,
    title_id            bigint        NOT NULL REFERENCES titles (id) ON DELETE CASCADE,
    status              varchar(10)   NOT NULL DEFAULT 'PENDING',
    reason              varchar(500),
    reapply_at          timestamptz,
    reviewed_by         bigint,
    reviewed_by_member  bigint,
    reviewed_at         timestamptz,
    created_at          timestamptz   NOT NULL,
    CONSTRAINT ck_title_applications_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_title_applications_reviewed CHECK ((status = 'PENDING') = (reviewed_at IS NULL)),
    CONSTRAINT ck_title_applications_rejected CHECK (status <> 'REJECTED' OR (reason IS NOT NULL AND reapply_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_title_applications_pending ON title_applications (user_id, title_id) WHERE status = 'PENDING';
CREATE INDEX ix_title_applications_user ON title_applications (user_id, created_at DESC);
CREATE INDEX ix_title_applications_queue ON title_applications (status, created_at DESC, id DESC);

-- ── 4) 작가 칭호 설정(행 하나) ─────────────────────────────────────────────────
-- monthly_max: 이달의 작가 한 달 최대 인원(1~20, 기본 3) · honorary_count: 명예 작가가 되는 이달의 작가 횟수(1~24, 기본 3) · reapply_days: 거절 뒤 다시 신청까지 일수(1~365, 기본 30).
CREATE TABLE title_settings (
    id              integer      PRIMARY KEY,
    monthly_max     integer      NOT NULL DEFAULT 3,
    honorary_count  integer      NOT NULL DEFAULT 3,
    reapply_days    integer      NOT NULL DEFAULT 30,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      bigint,
    CONSTRAINT ck_title_settings_one CHECK (id = 1),
    CONSTRAINT ck_title_settings_values CHECK (monthly_max BETWEEN 1 AND 20 AND honorary_count BETWEEN 1 AND 24 AND reapply_days BETWEEN 1 AND 365)
);
INSERT INTO title_settings (id) VALUES (1);

-- ── 5) 이달의 작가 후보(운영자가 넣은 글) ────────────────────────────────────────
-- 후보 = 그 달 post_features(살아 있는 선정)의 글쓴이 + 이 표. month 'YYYY-MM'(한국 시간), 글마다 그 달 한 번.
CREATE TABLE title_monthly_candidates (
    id          bigint       PRIMARY KEY,
    month       varchar(7)   NOT NULL,
    post_id     bigint       NOT NULL,
    user_id     bigint       NOT NULL,
    added_by    bigint       NOT NULL,
    added_at    timestamptz  NOT NULL,
    CONSTRAINT ux_title_monthly_candidates UNIQUE (month, post_id),
    CONSTRAINT ck_title_monthly_candidates_month CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);

-- ── 6) 회원 대표 칭호 ────────────────────────────────────────────────────────
-- representative_title_id NULL + hidden false = 기본(가진 작가 칭호 중 가장 높은 것) · hidden true = 「안 보이기」.
CREATE TABLE member_title_prefs (
    user_id                  bigint       PRIMARY KEY,
    representative_title_id  bigint       REFERENCES titles (id) ON DELETE SET NULL,
    hidden                   boolean      NOT NULL DEFAULT false,
    updated_at               timestamptz  NOT NULL
);

-- ── 7) 기본 칭호 7개(계약 「기본 칭호」 — 모든 설치·데모 공통) ───────────────────────
-- 리뷰어: 정식(REGULAR) 이상 · 사용기(slug review) 3편 — 「구매 인증 1편」은 구매 인증 기능(체험단 계약)이 생길 때 조건에 더한다. 단계 = 체험단 약속 지킴 3·10·30(체험단 기능 전에는 단계 없음).
-- 기자단: 정식 이상 · 뉴스(slug news) 10편 · 신고로 숨겨진 글 없음. 인기글: 하루 인기글 상위 진입 10·30·100일. 출석 100일: 출석 100일.
INSERT INTO titles (id, title_key, name, name_en, icon, chip, method, reviewer, board_id, board_slug, conditions, tier_metric, tier_bronze, tier_silver, tier_gold,
                    builtin, sort_order)
SELECT ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12) | s.n,
       s.title_key, s.name, s.name_en, s.icon, s.chip, s.method, s.reviewer,
       CASE WHEN s.board_slug IS NULL THEN NULL ELSE (SELECT b.id FROM boards b WHERE b.slug = s.board_slug) END,
       CASE WHEN s.board_slug IS NULL OR EXISTS (SELECT 1 FROM boards b WHERE b.slug = s.board_slug) THEN NULL ELSE s.board_slug END,
       s.conditions::jsonb, s.tier_metric, s.tier_bronze, s.tier_silver, s.tier_gold, true, s.n
  FROM (VALUES
        (1, 'monthly-writer',  '이달의 작가', 'Writer of the Month', 'pen',     'PURPLE', 'AWARD', 'OPERATOR', NULL, '[]', NULL, NULL, NULL, NULL),
        (2, 'yearly-writer',   '올해의 작가', 'Writer of the Year',  'trophy',  'PURPLE', 'AWARD', 'OPERATOR', NULL, '[]', NULL, NULL, NULL, NULL),
        (3, 'honorary-writer', '명예 작가',   'Honorary Writer',     'wreath',  'GREEN',  'AUTO',  'OPERATOR', NULL, '[]', NULL, NULL, NULL, NULL),
        (4, 'reviewer',        '리뷰어',      'Reviewer',            'mag',     'PURPLE', 'APPLY', 'OPERATOR_AND_BOARD_MANAGER', 'review',
            '[{"type":"GRADE_AT_LEAST","gradeKey":"REGULAR"},{"type":"POSTS","value":3,"boardSlug":"review"}]', 'TRIAL_KEPT', 3, 10, 30),
        (5, 'reporter',        '기자단',      'Reporter',            'mic',     'PURPLE', 'APPLY', 'OPERATOR', NULL,
            '[{"type":"GRADE_AT_LEAST","gradeKey":"REGULAR"},{"type":"POSTS","value":10,"boardSlug":"news"},{"type":"NO_REPORT_HIDDEN"}]', NULL, NULL, NULL, NULL),
        (6, 'popular',         '인기글',      'Popular Writer',      'popular', 'GREEN',  'AUTO',  'OPERATOR', NULL, '[]', 'POPULAR_DAYS', 10, 30, 100),
        (7, 'attendance-100',  '출석 100일',  '100-Day Attendance',  'cal',     'GREEN',  'AUTO',  'OPERATOR', NULL, '[{"type":"ATTENDANCE_DAYS","value":100}]', NULL, NULL, NULL, NULL)
       ) AS s (n, title_key, name, name_en, icon, chip, method, reviewer, board_slug, conditions, tier_metric, tier_bronze, tier_silver, tier_gold);

-- ── 8) 회원 알림 종류: V166 목록 ∪ TITLE_GRANTED(칭호를 받음 — 단계가 오른 것도) ───────────
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
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
                    'BUSINESS',
                    'BIZ_APPLICATION',
                    'DEAL_FLAGGED', 'DEAL_AUTO_ENDED', 'DEAL_MANAGER_ENDED', 'DEAL_EXPIRED', 'DEAL_REOPENED_FIX_ENDS',
                    'SANCTION_ISSUED', 'SANCTION_LIFTED', 'APPEAL_ANSWERED',
                    'BOARD_SANCTION_ISSUED',
                    'EMOTICON_PACK',
                    'EMOTICON_PURCHASE',
                    'AVATAR_UPLOAD_REVIEWED',
                    'REWARD_EVENT_GRANTED', 'REWARD_EVENT_REVOKED',
                    'POST_STATUS_CHANGED',
                    'TITLE_GRANTED'));

-- ── 9) 권한(계약 「권한」 — 운영자): title.read · title.manage · title.review ───────────────
-- 권한 행은 admin 의 PermissionCatalogSynchronizer 가 맞추지만 이 파일이 먼저 돌 수 있어 여기서도 넣는다(있으면 그대로). 선례 V239.
-- 등급을 보는(grade.read) 운영자 역할에 title.read, 등급을 바꾸는(grade.manage) 역할에 title.manage·title.review 를 함께 준다(가정 — 회원 칭호는 등급 화면 옆 회원 묶음).
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at)
SELECT c.code, p.axis, p.type_id, p.category, true, now()
  FROM permissions p
  CROSS JOIN (VALUES ('title.read', 'grade.read'), ('title.manage', 'grade.manage'), ('title.review', 'grade.manage')) AS c (code, base)
 WHERE p.code = c.base
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | (100 + row_number() OVER (ORDER BY rp.role_id, c.code))::bigint,
       rp.role_id, c.code, now()
  FROM role_permissions rp
  JOIN roles r ON r.role_id = rp.role_id AND r.axis = 'OPERATOR'
  JOIN (VALUES ('title.read', 'grade.read'), ('title.manage', 'grade.manage'), ('title.review', 'grade.manage')) AS c (code, base) ON c.base = rp.permission_code
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_code = c.code);

COMMENT ON TABLE titles IS '회원 칭호 정의 — V240 member-title-contract';
COMMENT ON TABLE member_titles IS '회원이 받은 칭호(회수해도 남는다) — V240';
COMMENT ON TABLE title_applications IS '칭호 신청·심사 — V240';
COMMENT ON TABLE title_settings IS '작가 칭호 설정(행 하나) — V240';
COMMENT ON TABLE title_monthly_candidates IS '이달의 작가 후보로 넣은 글 — V240';
COMMENT ON TABLE member_title_prefs IS '회원 대표 칭호 — V240';
