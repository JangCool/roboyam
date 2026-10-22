-- 게시판 관리 — docs/features/board-contract.md (관리자 화면 확정 2026-10-22)
--
-- · boards: 게시판 정보(파티션 없음). id 는 Snowflake(앱 발급). slug 는 만든 뒤 바꾸지 않는다. 이름은 대소문자 무시 유일.
--   정책 칸은 NULL 허용 — NULL = 기본값(board_defaults)을 따른다. 유효값 = 게시판 값 ?? 기본값(앱이 계산한다).
-- · board_defaults: 모든 게시판의 기본 설정(행 하나, id = 1). 하위 화면 4개(신고·보안·알림·개인정보)는 화면마다 자기 행 하나짜리 표를 둔다
--   (화면마다 마지막 저장 시각·저장한 운영자가 따로 보인다).
-- · board_prefixes: 게시판별 말머리. 공통 말머리 4개(QUESTION·REVIEW·CHAT·NOTICE)는 게시판마다 common_key 가 있는 행으로 두고 켜고 끄기만 한다.
--   고유 말머리는 common_key 가 NULL 인 행이다(사용 중 최대 6개·이름 중복 검사는 앱이 한다).
-- · board_staff: 게시판 매니저·스텝 지정(docs/domain/operator-permissions.md 「게시판 매니저 · 스텝」). 회원이 지워지면 함께 지워진다.
-- · posts: id RANGE 월 파티션(Snowflake 시각 = ID 범위). 조각은 board_ensure_post_partitions(R__batch_17)가 지난달~6개월 뒤까지 만든다 —
--   조각 없는 달의 글은 저장이 실패한다(기본 조각을 두지 않는다). 글 조각은 떼거나 지우지 않는다.
-- · comments: post_id HASH 64. 답글 1단계(parent_id, reply_to_id).
-- · 글·댓글 표는 이번에 만들기만 한다(쓰기 API 는 후속). 파티션 표를 가리키는 외래 키는 두지 않는다. 비회원 글·댓글은 작성자 이름·비밀번호 해시를 갖고
--   작성 IP 는 회원 글에도 남긴다. 개인정보 보유기간 배치(R__batch_18)가 IP 지우기·비회원 정보 익명화/삭제·삭제된 원문 파기를 한다.

-- ── 게시판 기본 설정(행 하나) ─────────────────────────────────────────────────
-- 글쓰기·댓글 최소 자격: 등급 키 · STAFF(운영진·그 게시판 매니저/스텝만), 댓글은 OFF 도. 기본 설정은 비회원(GUEST)을 받지 않는다(게시판별로만 고른다).
-- 등급 키가 등급 표에 있는지는 앱이 저장할 때 본다(등급은 지워질 수 있어 외래 키를 두지 않는다).
CREATE TABLE board_defaults (
    id                            integer      PRIMARY KEY,
    date_display                  varchar(16)  NOT NULL,
    write_min                     varchar(19)  NOT NULL,
    comment_min                   varchar(19)  NOT NULL,
    image_upload_allowed          boolean      NOT NULL,
    post_gap_seconds              integer      NOT NULL,
    post_daily_limit              integer      NOT NULL,
    comment_gap_seconds           integer      NOT NULL,
    comment_daily_limit           integer      NOT NULL,
    list_size                     integer      NOT NULL,
    pinned_max                    integer      NOT NULL,
    indexable                     boolean      NOT NULL,
    searchable                    boolean      NOT NULL,
    in_home                       boolean      NOT NULL,
    attachment_purge_enabled      boolean      NOT NULL,
    attachment_retention_days     integer      NOT NULL,
    point_mode                    varchar(8)   NOT NULL,
    comment_protect_min           integer      NOT NULL,
    comment_protect_edit          boolean      NOT NULL,
    comment_protect_delete        varchar(8)   NOT NULL,
    comment_protect_delay_minutes integer      NOT NULL,
    comment_guest_delay_minutes   integer      NOT NULL,
    best_comment_min_likes        integer      NOT NULL,
    manager_max                   integer      NOT NULL,
    staff_max                     integer      NOT NULL,
    staff_post_daily_limit        integer      NOT NULL,
    staff_comment_daily_limit     integer      NOT NULL,
    staff_sanction_daily_limit    integer      NOT NULL,
    updated_at                    timestamptz,                         -- 저장한 적 없으면 NULL
    updated_by                    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_board_defaults_single_row CHECK (id = 1),
    CONSTRAINT ck_board_defaults_date_display CHECK (date_display IN ('STANDARD', 'RELATIVE')),
    CONSTRAINT ck_board_defaults_write_min CHECK (write_min ~ '^[A-Z][A-Z0-9_]{0,18}$' AND write_min NOT IN ('GUEST', 'OFF')),
    CONSTRAINT ck_board_defaults_comment_min CHECK (comment_min ~ '^[A-Z][A-Z0-9_]{0,18}$' AND comment_min <> 'GUEST'),
    CONSTRAINT ck_board_defaults_post_gap CHECK (post_gap_seconds BETWEEN 0 AND 3600),
    CONSTRAINT ck_board_defaults_post_daily CHECK (post_daily_limit BETWEEN 1 AND 1000),
    CONSTRAINT ck_board_defaults_comment_gap CHECK (comment_gap_seconds BETWEEN 0 AND 3600),
    CONSTRAINT ck_board_defaults_comment_daily CHECK (comment_daily_limit BETWEEN 1 AND 1000),
    CONSTRAINT ck_board_defaults_list_size CHECK (list_size BETWEEN 10 AND 100),
    CONSTRAINT ck_board_defaults_pinned CHECK (pinned_max BETWEEN 0 AND 5),
    CONSTRAINT ck_board_defaults_attachment_days CHECK (attachment_retention_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_board_defaults_point_mode CHECK (point_mode IN ('ON', 'OFF')),
    CONSTRAINT ck_board_defaults_protect_min CHECK (comment_protect_min BETWEEN 0 AND 100),
    CONSTRAINT ck_board_defaults_protect_delete CHECK (comment_protect_delete IN ('BLOCK', 'DELAY', 'ALLOW')),
    CONSTRAINT ck_board_defaults_protect_delay CHECK (comment_protect_delay_minutes IN (5, 10, 30)),
    CONSTRAINT ck_board_defaults_guest_delay CHECK (comment_guest_delay_minutes IN (0, 10, 30, 60, 180)),
    CONSTRAINT ck_board_defaults_best_likes CHECK (best_comment_min_likes BETWEEN 1 AND 100),
    CONSTRAINT ck_board_defaults_manager_max CHECK (manager_max BETWEEN 1 AND 10),
    CONSTRAINT ck_board_defaults_staff_max CHECK (staff_max BETWEEN 1 AND 10),
    CONSTRAINT ck_board_defaults_staff_post CHECK (staff_post_daily_limit BETWEEN 1 AND 50),
    CONSTRAINT ck_board_defaults_staff_comment CHECK (staff_comment_daily_limit BETWEEN 1 AND 100),
    CONSTRAINT ck_board_defaults_staff_sanction CHECK (staff_sanction_daily_limit BETWEEN 1 AND 20)
);
-- 기본값: 관리자 화면 목업(board-mock.ts)과 같다. 최소 자격은 첫 등급(서열 1, 보통 SEEDLING).
INSERT INTO board_defaults (id, date_display, write_min, comment_min, image_upload_allowed, post_gap_seconds, post_daily_limit, comment_gap_seconds,
                            comment_daily_limit, list_size, pinned_max, indexable, searchable, in_home, attachment_purge_enabled, attachment_retention_days,
                            point_mode, comment_protect_min, comment_protect_edit, comment_protect_delete, comment_protect_delay_minutes,
                            comment_guest_delay_minutes, best_comment_min_likes, manager_max, staff_max, staff_post_daily_limit, staff_comment_daily_limit,
                            staff_sanction_daily_limit)
SELECT 1, 'STANDARD', g.grade_key, g.grade_key, true, 60, 30, 10, 200, 20, 2, true, true, true, false, 30,
       'ON', 0, false, 'DELAY', 10, 0, 5, 3, 10, 10, 20, 5
  FROM (SELECT coalesce((SELECT grade_key FROM grades ORDER BY level LIMIT 1), 'SEEDLING') AS grade_key) g;

-- ── 기본 설정 하위 화면: 신고 정책(행 하나) ───────────────────────────────────
CREATE TABLE board_report_settings (
    id                           integer      PRIMARY KEY,
    report_enabled               boolean      NOT NULL DEFAULT true,
    auto_hide_threshold          integer      NOT NULL DEFAULT 5,
    auto_hide_target             varchar(8)   NOT NULL DEFAULT 'BOTH',
    auto_hide_min_grade          varchar(19)  NOT NULL,
    auto_hide_exempt_staff_posts boolean      NOT NULL DEFAULT true,
    rights_temp_measure_days     integer      NOT NULL DEFAULT 30,
    daily_report_limit           integer      NOT NULL DEFAULT 10,
    report_cooldown_seconds      integer      NOT NULL DEFAULT 60,
    rejection_limit_count        integer      NOT NULL DEFAULT 5,
    rejection_limit_days         integer      NOT NULL DEFAULT 30,
    rejection_block_days         integer      NOT NULL DEFAULT 7,
    notify_operator_scope        varchar(16)  NOT NULL DEFAULT 'PER_CASE',
    notify_author_on_action      boolean      NOT NULL DEFAULT true,
    notify_reporter_on_result    boolean      NOT NULL DEFAULT true,
    updated_at                   timestamptz,
    updated_by                   bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_board_report_single_row CHECK (id = 1),
    CONSTRAINT ck_board_report_threshold CHECK (auto_hide_threshold BETWEEN 0 AND 100),
    CONSTRAINT ck_board_report_target CHECK (auto_hide_target IN ('POST', 'COMMENT', 'BOTH')),
    CONSTRAINT ck_board_report_min_grade CHECK (auto_hide_min_grade ~ '^[A-Z][A-Z0-9_]{0,18}$'),
    CONSTRAINT ck_board_report_rights_days CHECK (rights_temp_measure_days BETWEEN 1 AND 30),
    CONSTRAINT ck_board_report_daily CHECK (daily_report_limit BETWEEN 0 AND 100),
    CONSTRAINT ck_board_report_cooldown CHECK (report_cooldown_seconds BETWEEN 0 AND 3600),
    CONSTRAINT ck_board_report_rejection_count CHECK (rejection_limit_count BETWEEN 0 AND 50),
    CONSTRAINT ck_board_report_rejection_days CHECK (rejection_limit_days BETWEEN 1 AND 365),
    CONSTRAINT ck_board_report_rejection_block CHECK (rejection_block_days BETWEEN 1 AND 90),
    CONSTRAINT ck_board_report_scope CHECK (notify_operator_scope IN ('PER_CASE', 'PER_REPORT'))
);
-- 자동 숨김 셈의 최소 등급: 둘째 등급(보통 REGULAR), 없으면 첫 등급
INSERT INTO board_report_settings (id, auto_hide_min_grade)
SELECT 1, coalesce((SELECT grade_key FROM grades ORDER BY level OFFSET 1 LIMIT 1), (SELECT grade_key FROM grades ORDER BY level LIMIT 1), 'SEEDLING');

-- ── 기본 설정 하위 화면: 보안(행 하나) ───────────────────────────────────────
-- blocked_words: 금지어 JSON 배열(최대 500개, 단어당 1~30자, 대소문자 무시 중복 없음 — 앱이 검사·정리한다). 이번에는 저장만 하고 적용 지점은 후속.
CREATE TABLE board_security_settings (
    id                            integer      PRIMARY KEY,
    view_count_dedup_hours        integer      NOT NULL DEFAULT 24,
    blocked_words_enabled         boolean      NOT NULL DEFAULT true,
    blocked_words_action          varchar(8)   NOT NULL DEFAULT 'BLOCK',
    blocked_words                 jsonb        NOT NULL DEFAULT '[]'::jsonb,
    new_member_link_limit         integer      NOT NULL DEFAULT 2,
    new_member_image_limit        integer      NOT NULL DEFAULT 3,
    duplicate_post_window_minutes integer      NOT NULL DEFAULT 10,
    external_link_nofollow        boolean      NOT NULL DEFAULT true,
    external_link_warn            boolean      NOT NULL DEFAULT false,
    guest_captcha_required        boolean      NOT NULL DEFAULT true,
    guest_name_min                integer      NOT NULL DEFAULT 2,
    guest_name_max                integer      NOT NULL DEFAULT 20,
    guest_password_min            integer      NOT NULL DEFAULT 4,
    guest_link_limit              integer      NOT NULL DEFAULT 0,
    guest_image_upload_allowed    boolean      NOT NULL DEFAULT false,
    updated_at                    timestamptz,
    updated_by                    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_board_security_single_row CHECK (id = 1),
    CONSTRAINT ck_board_security_dedup CHECK (view_count_dedup_hours BETWEEN 1 AND 168),
    CONSTRAINT ck_board_security_action CHECK (blocked_words_action IN ('BLOCK', 'MASK')),
    CONSTRAINT ck_board_security_words CHECK (jsonb_typeof(blocked_words) = 'array' AND jsonb_array_length(blocked_words) <= 500),
    CONSTRAINT ck_board_security_new_links CHECK (new_member_link_limit BETWEEN 0 AND 20),
    CONSTRAINT ck_board_security_new_images CHECK (new_member_image_limit BETWEEN 0 AND 20),
    CONSTRAINT ck_board_security_duplicate CHECK (duplicate_post_window_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_board_security_guest_name CHECK (guest_name_min BETWEEN 1 AND 20 AND guest_name_max BETWEEN 1 AND 20 AND guest_name_min <= guest_name_max),
    CONSTRAINT ck_board_security_guest_password CHECK (guest_password_min BETWEEN 4 AND 20),
    CONSTRAINT ck_board_security_guest_links CHECK (guest_link_limit BETWEEN 0 AND 20)
);
-- 기본 금지어: 욕설·비하 계열만(관리자 화면 목업과 같다)
INSERT INTO board_security_settings (id, blocked_words)
VALUES (1, '["씨발","시발","씨바","개새끼","새끼","병신","븅신","미친놈","미친년","지랄","염병","엿먹어","닥쳐","꺼져","쓰레기같은","틀딱","급식충","맘충","한남충","김치녀"]'::jsonb);

-- ── 기본 설정 하위 화면: 알림(행 하나) ───────────────────────────────────────
-- member_defaults: 회원 알림 종류별 기본값 JSON 배열 [{kind, site, email, memberCanTurnOff}]. 운영 처리·스텝 처리(OPERATOR_ACTION·STAFF_ACTION)는 앱이 site=true·끌 수 없음으로 고정한다.
CREATE TABLE board_notification_settings (
    id                                 integer      PRIMARY KEY,
    member_defaults                    jsonb        NOT NULL,
    group_window_minutes               integer      NOT NULL DEFAULT 10,
    email_quiet_hours_enabled          boolean      NOT NULL DEFAULT false,
    email_quiet_start                  varchar(5)   NOT NULL DEFAULT '22:00',
    email_quiet_end                    varchar(5)   NOT NULL DEFAULT '08:00',
    member_notification_retention_days integer      NOT NULL DEFAULT 90,
    updated_at                         timestamptz,
    updated_by                         bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_board_notification_single_row CHECK (id = 1),
    CONSTRAINT ck_board_notification_defaults CHECK (jsonb_typeof(member_defaults) = 'array'),
    CONSTRAINT ck_board_notification_group CHECK (group_window_minutes BETWEEN 0 AND 60),
    CONSTRAINT ck_board_notification_quiet CHECK (email_quiet_start ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$' AND email_quiet_end ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    CONSTRAINT ck_board_notification_retention CHECK (member_notification_retention_days BETWEEN 7 AND 365)
);
INSERT INTO board_notification_settings (id, member_defaults)
VALUES (1, '[{"kind":"COMMENT_ON_POST","site":true,"email":false,"memberCanTurnOff":true},
             {"kind":"REPLY_ON_COMMENT","site":true,"email":false,"memberCanTurnOff":true},
             {"kind":"MENTION","site":true,"email":false,"memberCanTurnOff":true},
             {"kind":"POST_POPULAR","site":true,"email":false,"memberCanTurnOff":true},
             {"kind":"OPERATOR_ACTION","site":true,"email":true,"memberCanTurnOff":false},
             {"kind":"STAFF_ACTION","site":true,"email":false,"memberCanTurnOff":false},
             {"kind":"REPORT_RESULT","site":true,"email":false,"memberCanTurnOff":true}]'::jsonb);

-- ── 기본 설정 하위 화면: 개인정보 보유기간(행 하나) ───────────────────────────
-- retention_enabled: 전체 스위치(파기는 되돌릴 수 없어 기본 꺼짐) — 꺼져 있으면 배치·지금 실행 모두 아무것도 하지 않는다.
-- guest_retention_days 0 = 비회원 글을 처리하지 않는다. last_run_at: 마지막 배치(또는 지금 실행) 시각.
CREATE TABLE board_privacy_settings (
    id                             integer      PRIMARY KEY,
    retention_enabled              boolean      NOT NULL DEFAULT false,
    ip_retention_days              integer      NOT NULL DEFAULT 365,
    guest_retention_days           integer      NOT NULL DEFAULT 365,
    guest_mode                     varchar(10)  NOT NULL DEFAULT 'ANONYMIZE',
    deleted_content_retention_days integer      NOT NULL DEFAULT 30,
    report_record_retention_days   integer      NOT NULL DEFAULT 365,
    staff_action_retention_days    integer      NOT NULL DEFAULT 365,
    last_run_at                    timestamptz,
    updated_at                     timestamptz,
    updated_by                     bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_board_privacy_single_row CHECK (id = 1),
    CONSTRAINT ck_board_privacy_ip CHECK (ip_retention_days BETWEEN 90 AND 3650),
    CONSTRAINT ck_board_privacy_guest CHECK (guest_retention_days BETWEEN 0 AND 3650),
    CONSTRAINT ck_board_privacy_guest_mode CHECK (guest_mode IN ('ANONYMIZE', 'DELETE')),
    CONSTRAINT ck_board_privacy_deleted CHECK (deleted_content_retention_days BETWEEN 1 AND 365),
    CONSTRAINT ck_board_privacy_report CHECK (report_record_retention_days BETWEEN 90 AND 1825),
    CONSTRAINT ck_board_privacy_staff CHECK (staff_action_retention_days BETWEEN 90 AND 1825)
);
INSERT INTO board_privacy_settings (id) VALUES (1);

-- ── 게시판 ──────────────────────────────────────────────────────────────────
-- kind: GENERAL(일반) · DEAL(핫딜) · PROMO(직접홍보) · GROUP(소모임) — 만든 뒤 바꾸지 않는다. 관리자 화면은 일반만 만든다.
-- layout: LIST · CARD · GALLERY. 카드형은 보기 전환(view_toggle)을 켤 수 없다. color: 표시색 12색 이름(NULL = 자동).
-- investment_notice: 켜기만 된다(앱이 끄기를 409 로 막는다). write_lock: 읽기 전용 잠금 POST · POST_COMMENT, NULL = 잠금 없음(기본값이 없는 칸).
-- intro_text·write_guide_text: 안내문 평문(보이는 글자 500자, 에디터·HTML 은 후속). sort_order: 1부터, 앱이 다시 매긴다.
-- 정책 칸(write_min … staff_sanction_daily_limit)은 NULL = 기본값 따름. 범위는 board_defaults 와 같다. GUEST 는 게시판별로만 고른다.
CREATE TABLE boards (
    id                            bigint       PRIMARY KEY,
    slug                          varchar(40)  NOT NULL,
    name                          varchar(40)  NOT NULL,
    name_en                       varchar(40),
    description                   varchar(200) NOT NULL DEFAULT '',
    kind                          varchar(8)   NOT NULL DEFAULT 'GENERAL',
    layout                        varchar(8)   NOT NULL DEFAULT 'LIST',
    view_toggle                   boolean      NOT NULL DEFAULT true,
    color                         varchar(8),
    allow_adult                   boolean      NOT NULL DEFAULT false,
    investment_notice             boolean      NOT NULL DEFAULT false,
    active                        boolean      NOT NULL DEFAULT true,
    sort_order                    integer      NOT NULL,
    write_lock                    varchar(16),
    intro_text                    text,
    write_guide_text              text,
    write_min                     varchar(19),
    comment_min                   varchar(19),
    image_upload_allowed          boolean,
    list_size                     integer,
    pinned_max                    integer,
    indexable                     boolean,
    searchable                    boolean,
    in_home                       boolean,
    point_mode                    varchar(8),
    comment_protect_min           integer,
    comment_protect_edit          boolean,
    comment_protect_delete        varchar(8),
    comment_protect_delay_minutes integer,
    comment_guest_delay_minutes   integer,
    manager_max                   integer,
    staff_post_daily_limit        integer,
    staff_comment_daily_limit     integer,
    staff_sanction_daily_limit    integer,
    created_at                    timestamptz  NOT NULL DEFAULT now(),
    created_by                    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at                    timestamptz  NOT NULL DEFAULT now(),
    updated_by                    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_boards_slug CHECK (slug ~ '^[a-z][a-z0-9-]{1,39}$' AND slug NOT IN ('defaults', 'order', 'new')),
    CONSTRAINT ck_boards_name CHECK (char_length(name) BETWEEN 1 AND 40 AND name = btrim(name)),
    CONSTRAINT ck_boards_name_en CHECK (name_en IS NULL OR (char_length(name_en) BETWEEN 1 AND 40 AND name_en = btrim(name_en))),
    CONSTRAINT ck_boards_kind CHECK (kind IN ('GENERAL', 'DEAL', 'PROMO', 'GROUP')),
    CONSTRAINT ck_boards_layout CHECK (layout IN ('LIST', 'CARD', 'GALLERY')),
    CONSTRAINT ck_boards_view_toggle CHECK (layout <> 'CARD' OR NOT view_toggle),
    CONSTRAINT ck_boards_color CHECK (color IS NULL OR color IN ('BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN')),
    CONSTRAINT ck_boards_write_lock CHECK (write_lock IS NULL OR write_lock IN ('POST', 'POST_COMMENT')),
    CONSTRAINT ck_boards_intro CHECK (intro_text IS NULL OR char_length(intro_text) <= 500),
    CONSTRAINT ck_boards_write_guide CHECK (write_guide_text IS NULL OR char_length(write_guide_text) <= 500),
    CONSTRAINT ck_boards_write_min CHECK (write_min ~ '^[A-Z][A-Z0-9_]{0,18}$' AND write_min <> 'OFF'),
    CONSTRAINT ck_boards_comment_min CHECK (comment_min ~ '^[A-Z][A-Z0-9_]{0,18}$'),
    CONSTRAINT ck_boards_guest_kind CHECK (kind IN ('GENERAL', 'GROUP') OR (write_min IS DISTINCT FROM 'GUEST' AND comment_min IS DISTINCT FROM 'GUEST')),
    CONSTRAINT ck_boards_list_size CHECK (list_size BETWEEN 10 AND 100),
    CONSTRAINT ck_boards_pinned CHECK (pinned_max BETWEEN 0 AND 5),
    CONSTRAINT ck_boards_point_mode CHECK (point_mode IN ('ON', 'OFF')),
    CONSTRAINT ck_boards_protect_min CHECK (comment_protect_min BETWEEN 0 AND 100),
    CONSTRAINT ck_boards_protect_delete CHECK (comment_protect_delete IN ('BLOCK', 'DELAY', 'ALLOW')),
    CONSTRAINT ck_boards_protect_delay CHECK (comment_protect_delay_minutes IN (5, 10, 30)),
    CONSTRAINT ck_boards_guest_delay CHECK (comment_guest_delay_minutes IN (0, 10, 30, 60, 180)),
    CONSTRAINT ck_boards_manager_max CHECK (manager_max BETWEEN 1 AND 10),
    CONSTRAINT ck_boards_staff_post CHECK (staff_post_daily_limit BETWEEN 1 AND 50),
    CONSTRAINT ck_boards_staff_comment CHECK (staff_comment_daily_limit BETWEEN 1 AND 100),
    CONSTRAINT ck_boards_staff_sanction CHECK (staff_sanction_daily_limit BETWEEN 1 AND 20)
);
CREATE UNIQUE INDEX ux_boards_slug ON boards (slug);
CREATE UNIQUE INDEX ux_boards_name ON boards (lower(name));
CREATE INDEX ix_boards_sort ON boards (sort_order, id);

-- ── 말머리 ──────────────────────────────────────────────────────────────────
-- common_key: 공통 말머리(QUESTION·REVIEW·CHAT·NOTICE)면 그 종류, 고유 말머리면 NULL. 공통은 게시판마다 4행이고 이름·운영진 전용은 고정이다.
-- 고유 말머리의 이름 중복(공통 이름 포함, 대소문자 무시)·사용 중 최대 6개는 앱이 저장 한 번 단위로 검사한다(저장 중 이름을 맞바꿀 수 있게 DB 유일 색인은 두지 않는다).
CREATE TABLE board_prefixes (
    id          bigint       PRIMARY KEY,
    board_id    bigint       NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    common_key  varchar(10),
    name        varchar(20)  NOT NULL,
    staff_only  boolean      NOT NULL DEFAULT false,
    color       varchar(8),
    active      boolean      NOT NULL DEFAULT true,
    sort_order  integer      NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_board_prefixes_common CHECK (common_key IS NULL OR common_key IN ('QUESTION', 'REVIEW', 'CHAT', 'NOTICE')),
    CONSTRAINT ck_board_prefixes_name CHECK (char_length(name) BETWEEN 1 AND 20 AND name = btrim(name)),
    CONSTRAINT ck_board_prefixes_color CHECK (color IS NULL OR color IN ('BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN'))
);
CREATE UNIQUE INDEX ux_board_prefixes_common ON board_prefixes (board_id, common_key) WHERE common_key IS NOT NULL;
CREATE INDEX ix_board_prefixes_board ON board_prefixes (board_id, sort_order, id);

-- ── 게시판 매니저·스텝 ───────────────────────────────────────────────────────
-- role: MANAGER · STAFF. 한 회원은 한 게시판에 한 역할만. 한 회원이 맡는 게시판 수(최대 5)·게시판당 인원은 앱이 행 잠금 아래에서 센다.
-- appointed_by_operator: 지정한 운영자(매니저가 스텝을 지정하는 공개 사이트 기능은 후속 — appointed_by_user).
CREATE TABLE board_staff (
    board_id              bigint       NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    user_id               bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                  varchar(8)   NOT NULL,
    appointed_at          timestamptz  NOT NULL DEFAULT now(),
    appointed_by_operator bigint       REFERENCES operators (id) ON DELETE SET NULL,
    appointed_by_user     bigint       REFERENCES users (id) ON DELETE SET NULL,
    PRIMARY KEY (board_id, user_id),
    CONSTRAINT ck_board_staff_role CHECK (role IN ('MANAGER', 'STAFF'))
);
CREATE INDEX ix_board_staff_user ON board_staff (user_id);

-- ── 글 (id 월 범위 파티션) ───────────────────────────────────────────────────
-- 작성자: 회원 글은 author_id, 비회원 글은 guest_name + guest_password_hash(해시만). author_ip 는 회원·비회원 모두(개인정보 배치가 보유기간 뒤 지운다).
-- status: PUBLISHED · DRAFT(임시저장). hidden_at: 숨김. deleted_at: 삭제(원문은 보유기간까지 남는다). purged_at: 원문(제목·본문)을 파기한 시각.
-- guest_processed_at: 비회원 정보 보유기간 처리(익명화·삭제)를 마친 시각.
CREATE TABLE posts (
    id                  bigint       NOT NULL,
    board_id            bigint       NOT NULL,
    prefix_id           bigint,
    author_id           bigint,
    guest_name          varchar(20),
    guest_password_hash varchar(100),
    author_ip           varchar(45),
    title               varchar(200) NOT NULL,
    content             text         NOT NULL,
    status              varchar(10)  NOT NULL DEFAULT 'PUBLISHED',
    hidden_at           timestamptz,
    deleted_at          timestamptz,
    purged_at           timestamptz,
    guest_processed_at  timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ck_posts_author CHECK (author_id IS NOT NULL OR guest_name IS NOT NULL),
    CONSTRAINT ck_posts_status CHECK (status IN ('PUBLISHED', 'DRAFT'))
) PARTITION BY RANGE (id);
CREATE INDEX ix_posts_board ON posts (board_id, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_posts_board_prefix ON posts (board_id, prefix_id);
CREATE INDEX ix_posts_ip_created ON posts (created_at) WHERE author_ip IS NOT NULL;
CREATE INDEX ix_posts_guest_created ON posts (created_at) WHERE author_id IS NULL AND guest_processed_at IS NULL;
CREATE INDEX ix_posts_deleted ON posts (deleted_at) WHERE deleted_at IS NOT NULL AND purged_at IS NULL;

-- ── 댓글 (post_id 해시 파티션 64개) ─────────────────────────────────────────
CREATE TABLE comments (
    id                  bigint       NOT NULL,
    post_id             bigint       NOT NULL,
    board_id            bigint       NOT NULL,
    parent_id           bigint,                       -- 원댓글 id(답글일 때만). 답글은 1단계만
    reply_to_id         bigint,                       -- 실제로 답한 댓글 id
    author_id           bigint,
    guest_name          varchar(20),
    guest_password_hash varchar(100),
    author_ip           varchar(45),
    content             text         NOT NULL,
    hidden_at           timestamptz,
    deleted_at          timestamptz,
    purged_at           timestamptz,
    guest_processed_at  timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, id),
    CONSTRAINT ck_comments_author CHECK (author_id IS NOT NULL OR guest_name IS NOT NULL)
) PARTITION BY HASH (post_id);

DO
$$
BEGIN
    FOR i IN 0..63 LOOP
        EXECUTE format('CREATE TABLE comments_p%s PARTITION OF comments FOR VALUES WITH (MODULUS 64, REMAINDER %s)', i, i);
    END LOOP;
END
$$;
CREATE INDEX ix_comments_top ON comments (post_id, id) WHERE parent_id IS NULL;
CREATE INDEX ix_comments_replies ON comments (post_id, parent_id, id) WHERE parent_id IS NOT NULL;
CREATE INDEX ix_comments_ip_created ON comments (created_at) WHERE author_ip IS NOT NULL;
CREATE INDEX ix_comments_guest_created ON comments (created_at) WHERE author_id IS NULL AND guest_processed_at IS NULL;
CREATE INDEX ix_comments_deleted ON comments (deleted_at) WHERE deleted_at IS NOT NULL AND purged_at IS NULL;

-- ── 설치 프리셋 적용 표시 ────────────────────────────────────────────────────
-- 설치 때 고른 board_preset 으로 기본 게시판을 만든 시각. 이 칸이 생기기 전에 설치된 곳은 NULL 이고, 공개 서버(api)가 뜰 때
-- 게시판이 하나도 없으면 한 번 적용한 뒤 채운다(게시판이 이미 있으면 적용하지 않고 채운다) — 운영자가 게시판을 모두 지워도 다시 만들지 않는다.
ALTER TABLE installation ADD COLUMN board_preset_applied_at timestamptz;
