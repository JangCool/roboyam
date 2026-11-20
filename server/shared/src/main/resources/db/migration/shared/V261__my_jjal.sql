-- 내 짤(docs/features/my-jjal-contract.md) — 회원이 움짤·짤을 올리거나 글·댓글의 짤을 「내 짤에 담기」로 모아 글·댓글에 붙이는 나만 쓰는 보관함.
--
-- · jjal_files: 짤 파일 한 줄 = 원본 바이트 SHA-256 하나(유일). 같은 해시를 다시 올리면 새로 저장하지 않고 이 줄을 가리킨다. 저장소 키는 무작위(해시를 키·주소에 쓰지 않는다),
--   공개 ID(토큰 :jjal-{공개ID}: 의 ID)도 무작위 영문·숫자. 신고로 내리면 status BLOCKED(까닭 blocked_reason — 운영자 STAFF · 자동 숨김 REPORTED·ILLEGAL·RIGHTS,
--   권리침해 임시조치는 blocked_until 이 지나면 다시 보인다). 내린 짤의 해시는 다시 올리기를 거절한다. uploaded_by = 처음 올린 회원(어디에도 내보내지 않는다).
--   저장 위치: 보이는 짤은 공개 접두사 jjal/…, 내려간 짤은 서빙하지 않는 접두사 private/jjal/…(무작위 새 키)로 옮기고, 다시 보이면 또 새 무작위 키로 jjal/… 에 둔다
--   (옛 주소는 살아나지 않는다). 운영자가 불법촬영물로 내린 짤은 파일을 지우고(storage_key NULL, purged_at) 해시 행만 남긴다 — 되돌림 없음.
-- · jjal_box_items: 보관함 줄((회원, 파일) 유일) — 출처 UPLOAD(올림)·COLLECT(담음), 순서 값 sort_key(작을수록 앞 — 새 줄은 지금 가장 작은 값 − 1 = 맨 앞).
-- · jjal_member_states: 회원별 처음 안내 확인 기록(시각 · 안내 판). 올리기·담기·순서는 회원 단위 어드바이저리 잠금 아래에서 판정한다(서버 여러 대).
-- · jjal_upload_events: 올리기 속도 기록(1분 10장 · 하루 50장 — 지운 보관함 줄과 무관하게 센다). 정리 배치가 2일 지난 줄을 지운다.
-- · jjal_uses: 짤을 쓰는 글·댓글(글 본문이면 comment_id NULL) — 저장 때 본문의 짤 토큰으로 다시 쓴다. 파일 정리(보관함 줄도, 살아 있는 쓰임도 없을 때)와 담기 검사가 본다.
--   파티션 표(posts·comments)를 가리키는 외래 키는 두지 않는다.
-- · 게시판 정책 칸 post_jjal_allowed(본문 짤 허용)·comment_jjal_allowed(댓글 짤 허용) — boards NULL = 기본값 따름, board_defaults NOT NULL 기본 true.
-- · 신고 대상 종류 JJAL(파일 단위): target_id = jjal_files.id, 글·게시판이 없다(post_id·board_id NULL), 작성자 없음(target_author_id NULL).
--   조치 JJAL_BLOCK(짤 내림). 기존 목록과 합집합(⚠ 합칠 때 다른 세션이 같은 CHECK 를 다시 만들었는지 확인).

-- ── 짤 파일 ─────────────────────────────────────────────────────────────────
CREATE TABLE jjal_files (
    id              bigint       PRIMARY KEY,
    public_id       varchar(32)  NOT NULL,
    sha256          char(64)     NOT NULL,
    storage_key     varchar(200),
    content_type    varchar(40)  NOT NULL,
    size_bytes      integer      NOT NULL,
    width           integer      NOT NULL,
    height          integer      NOT NULL,
    animated        boolean      NOT NULL,
    uploaded_by     bigint       REFERENCES users (id) ON DELETE SET NULL,
    status          varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    blocked_reason  varchar(10),
    blocked_until   timestamptz,
    blocked_at      timestamptz,
    purged_at       timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_jjal_files_public_id UNIQUE (public_id),
    CONSTRAINT ux_jjal_files_sha256 UNIQUE (sha256),
    CONSTRAINT ck_jjal_files_public_id CHECK (public_id ~ '^[A-Za-z0-9]{1,32}$'),
    CONSTRAINT ck_jjal_files_status CHECK (status IN ('ACTIVE', 'BLOCKED')),
    CONSTRAINT ck_jjal_files_blocked CHECK ((status = 'BLOCKED') = (blocked_reason IS NOT NULL AND blocked_at IS NOT NULL)),
    CONSTRAINT ck_jjal_files_blocked_reason CHECK (blocked_reason IS NULL OR blocked_reason IN ('REPORTED', 'ILLEGAL', 'RIGHTS', 'STAFF')),
    CONSTRAINT ck_jjal_files_blocked_until CHECK (blocked_until IS NULL OR status = 'BLOCKED'),
    CONSTRAINT ck_jjal_files_size CHECK (size_bytes > 0 AND width > 0 AND height > 0),
    -- 불법촬영물로 확정해 지운 짤(purged_at)만 내려간 채 저장소 키가 없다(파일을 지우고 해시 행만 남긴다)
    CONSTRAINT ck_jjal_files_purged CHECK (purged_at IS NULL OR (status = 'BLOCKED' AND blocked_reason = 'ILLEGAL' AND blocked_until IS NULL)),
    CONSTRAINT ck_jjal_files_storage_key CHECK (storage_key IS NOT NULL OR purged_at IS NOT NULL)
);
-- 정리 배치(오래된 것부터)
CREATE INDEX ix_jjal_files_created ON jjal_files (created_at);
-- 저장 위치 맞추기 배치(jjal-storage-sync) — 내려갔거나 비공개 위치에 있는 파일만
CREATE INDEX ix_jjal_files_storage_sync ON jjal_files (id) WHERE status = 'BLOCKED' OR storage_key LIKE 'private/%';
COMMENT ON TABLE jjal_files IS '내 짤 파일 — 원본 SHA-256 유일, 저장소 키·공개 ID 무작위(V261, my-jjal-contract)';

-- ── 보관함 줄 ───────────────────────────────────────────────────────────────
CREATE TABLE jjal_box_items (
    id          bigint       PRIMARY KEY,
    user_id     bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_id     bigint       NOT NULL REFERENCES jjal_files (id),
    source      varchar(8)   NOT NULL,
    sort_key    bigint       NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_jjal_box_items_user_file UNIQUE (user_id, file_id),
    CONSTRAINT ck_jjal_box_items_source CHECK (source IN ('UPLOAD', 'COLLECT'))
);
-- 내 보관함(회원이 정한 순서)
CREATE INDEX ix_jjal_box_items_user_sort ON jjal_box_items (user_id, sort_key, id);
-- 파일을 가리키는 줄이 남았는지(정리 배치)
CREATE INDEX ix_jjal_box_items_file ON jjal_box_items (file_id);
COMMENT ON TABLE jjal_box_items IS '내 짤 보관함 줄 — (회원, 파일) 유일, 출처 UPLOAD|COLLECT, 순서 값(V261)';

-- ── 처음 안내 확인 ──────────────────────────────────────────────────────────
CREATE TABLE jjal_member_states (
    user_id                bigint       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    intro_version          smallint,
    intro_acknowledged_at  timestamptz,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_jjal_member_states_intro CHECK ((intro_version IS NULL) = (intro_acknowledged_at IS NULL))
);
COMMENT ON TABLE jjal_member_states IS '내 짤 처음 안내 확인 기록(시각·안내 판) — 계정에 남겨 PC·휴대폰에서 두 번 나오지 않게(V261)';

-- ── 올리기 속도 기록 ────────────────────────────────────────────────────────
CREATE TABLE jjal_upload_events (
    id          bigint       PRIMARY KEY,
    user_id     bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_jjal_upload_events_user ON jjal_upload_events (user_id, created_at);
CREATE INDEX ix_jjal_upload_events_created ON jjal_upload_events (created_at);
COMMENT ON TABLE jjal_upload_events IS '내 짤 올리기 속도 기록(1분·하루 한도) — 정리 배치가 2일 지난 줄을 지운다(V261)';

-- ── 짤을 쓰는 글·댓글 ───────────────────────────────────────────────────────
CREATE TABLE jjal_uses (
    id          bigint  PRIMARY KEY,
    file_id     bigint  NOT NULL REFERENCES jjal_files (id),
    post_id     bigint  NOT NULL,
    comment_id  bigint,
    CONSTRAINT ux_jjal_uses_target UNIQUE NULLS NOT DISTINCT (post_id, comment_id, file_id)
);
CREATE INDEX ix_jjal_uses_file ON jjal_uses (file_id);
COMMENT ON TABLE jjal_uses IS '짤을 쓰는 글(comment_id NULL)·댓글 — 저장 때 본문 토큰으로 다시 쓴다(V261)';

-- ── 게시판 정책 칸 ──────────────────────────────────────────────────────────
ALTER TABLE boards ADD COLUMN post_jjal_allowed boolean;
ALTER TABLE boards ADD COLUMN comment_jjal_allowed boolean;
ALTER TABLE board_defaults ADD COLUMN post_jjal_allowed boolean NOT NULL DEFAULT true;
ALTER TABLE board_defaults ADD COLUMN comment_jjal_allowed boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN boards.post_jjal_allowed IS '본문 짤 허용(NULL = 사이트 기본) — V261';
COMMENT ON COLUMN boards.comment_jjal_allowed IS '댓글 짤 허용(NULL = 사이트 기본) — V261';

-- ── 신고 대상 종류 JJAL ─────────────────────────────────────────────────────
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다
ALTER TABLE reports DROP CONSTRAINT ck_reports_target_type;
ALTER TABLE reports ADD CONSTRAINT ck_reports_target_type CHECK (target_type IN ('POST', 'COMMENT', 'GROUP', 'JJAL'));
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_target_type;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_target_type CHECK (target_type IN ('POST', 'COMMENT', 'GROUP', 'JJAL'));

-- 글: 소모임·짤 신고만 비운다(V95 의 「GROUP 이면 NULL」을 넓힌다)
ALTER TABLE reports DROP CONSTRAINT ck_reports_post;
ALTER TABLE reports ADD CONSTRAINT ck_reports_post CHECK ((target_type IN ('GROUP', 'JJAL')) = (post_id IS NULL));
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_post;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_post CHECK ((target_type IN ('GROUP', 'JJAL')) = (post_id IS NULL));

-- 게시판: 짤 신고만 비운다(짤은 여러 게시판에 쓰인다 — 파일 단위)
ALTER TABLE reports ALTER COLUMN board_id DROP NOT NULL;
ALTER TABLE reports ADD CONSTRAINT ck_reports_board CHECK ((target_type = 'JJAL') = (board_id IS NULL));
ALTER TABLE report_cases ALTER COLUMN board_id DROP NOT NULL;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_board CHECK ((target_type = 'JJAL') = (board_id IS NULL));

-- 조치: V243 목록 ∪ JJAL_BLOCK
-- ⚠ 합칠 때 합집합 확인
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_action;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_action
    CHECK (action IS NULL OR action IN ('HIDE', 'DELETE', 'KEEP_HIDDEN', 'DEACTIVATE', 'DEAL_END', 'SANCTION_ONLY', 'SPONSOR_LABEL', 'JJAL_BLOCK', 'DISMISS'));
