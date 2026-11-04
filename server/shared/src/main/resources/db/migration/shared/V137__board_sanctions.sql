-- 게시판 제재(docs/features/board-sanction-contract.md — 결정 1~11 · 규칙). 게시판 매니저·스텝이 맡은 게시판에서만 주는 경고·쓰기 제한(1·3·7일).
-- 사이트 제재(member_sanctions, V136)와 단계·횟수를 따로 센다. 이의제기는 사이트 제재 이의제기 표(sanction_appeals)에 합친다(결정 4).
-- · board_sanctions: 한 건. kind WARNING · RESTRICT_1 · RESTRICT_3 · RESTRICT_7(처리자가 자유롭게 고른다 — 권장 단계는 앱이 계산할 뿐, 결정 2).
--   status: ACTIVE(경고는 늘, 쓰기 제한은 끝 전) · ENDED(기간 끝, ended_at) · LIFTED(매니저·운영자 해제 — 진행 중 쓰기 제한만, 권장 계산엔 남음) ·
--   VOIDED(이의 인용·운영자 무효·반복 검토 무효 — 권장·반복 검토 수에서 빠짐). 진행 중 판정은 읽을 때 ends_at > 지금(크론은 상태만 정리 — R__batch_40).
--   처리자: issued_by(회원) + issued_role(MANAGER · STAFF, 줄 때의 자리). 처리자 신원은 공개 응답·알림·오류 문구에 싣지 않는다(결정 7 — 앱 규칙).
--   계기: post_id · comment_id(글·댓글은 파티션 표라 FK 없음) + source_title(줄 때의 글 제목 스냅숏).
--   해제: lifted_by_type MANAGER(lifted_by_user) · OPERATOR(lifted_by_operator) + lift_reason(1~200자, 결정 11).
--   무효: void_source APPEAL(이의 인용) · OPERATOR(운영자 무효) · REVIEW(반복 검토 닫기 VOIDED) + voided_by(운영자) · void_reason(운영자 무효 1~200자, 그 밖은 비거나 메모).
--   (board_id, user_id) 에 진행 중 쓰기 제한은 하나(부분 유일 색인 — 회원 행 잠금이 먼저 막고 이 색인이 마지막으로 막는다).
-- · board_staff_usage: 처리자 1명 · 게시판 1개 · 한국 날짜 하루의 사용량 — 제재(경고+쓰기 제한 합산). 한도 차감은 INSERT … ON CONFLICT … DO UPDATE … WHERE used < limit
--   한 문장(원자적 증가). 해제·무효로 돌려받지 않는다. posts · comments 칸은 다음 작업(스텝의 글 숨기기·삭제 · 댓글 삭제)의 자리(지금은 0).
-- · board_staff_actions: 매니저·스텝의 처리 기록(감사 기록 대신 — 회원의 처리). kind 는 지금 SANCTION(결정 1 — 글·댓글을 나중에 같은 표에 담는다), action ISSUE · LIFT.
-- · board_sanction_reviews: 반복 검토(결정 3). 같은 회원 · 같은 게시판 · 최근 7일 · VOIDED 아닌 제재 3건 이상이면 앱이 부여 트랜잭션 안에서 연다.
--   (user_id, board_id) 에 열린 건 하나(부분 유일 색인). counted_after = 열 때 그 (회원, 게시판)의 마지막으로 닫힌 검토의 closed_at(그 이전 제재는 다시 세지 않는다).
--   닫기(운영자): result KEPT · SITE_SANCTIONED(site_sanction_id) · VOIDED(voided_sanction_ids) + memo 0~200자. 자동으로 닫지 않는다(결정 9).
--   닫을 때의 7일 건수·게시판 수를 closed_count · closed_board_count 로 남긴다(목록의 「닫은 때 기준」).
-- · sanction_appeals: + board_sanction_id(게시판 제재 이의제기), sanction_id NULL 허용, 둘 중 하나만(CHECK), 게시판 제재당 하나(유일).
-- · 회원 알림 종류 + BOARD_SANCTION_ISSUED(끌 수 없는 계정 알림 — 결정 6). 이의 답변은 APPEAL_ANSWERED 를 같이 쓴다.
-- 감사 행위(BOARD_SANCTION_LIFT · BOARD_SANCTION_VOID · BOARD_SANCTION_REVIEW_CLOSE)는 DB CHECK 가 없다(audit_logs.action 은 자유 글자).

-- ── 게시판 제재 ───────────────────────────────────────────────────────────────
CREATE SEQUENCE board_sanction_no_seq;
CREATE TABLE board_sanctions (
    id                 bigint       PRIMARY KEY,
    sanction_no        bigint       NOT NULL DEFAULT nextval('board_sanction_no_seq'),
    board_id           bigint       NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    user_id            bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind               varchar(12)  NOT NULL,
    status             varchar(8)   NOT NULL DEFAULT 'ACTIVE',
    reason_code        varchar(12)  NOT NULL,
    reason_text        varchar(200),
    issued_by          bigint       REFERENCES users (id) ON DELETE SET NULL,
    issued_role        varchar(8)   NOT NULL,
    post_id            bigint,
    comment_id         bigint,
    source_title       varchar(200),
    starts_at          timestamptz  NOT NULL,
    ends_at            timestamptz,
    acknowledged_at    timestamptz,
    lifted_at          timestamptz,
    lifted_by_type     varchar(8),
    lifted_by_user     bigint       REFERENCES users (id) ON DELETE SET NULL,
    lifted_by_operator bigint       REFERENCES operators (id) ON DELETE SET NULL,
    lift_reason        varchar(200),
    voided_at          timestamptz,
    void_source        varchar(8),
    voided_by          bigint       REFERENCES operators (id) ON DELETE SET NULL,
    void_reason        varchar(200),
    ended_at           timestamptz,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_board_sanctions_no UNIQUE (sanction_no),
    CONSTRAINT ck_board_sanctions_kind CHECK (kind IN ('WARNING', 'RESTRICT_1', 'RESTRICT_3', 'RESTRICT_7')),
    CONSTRAINT ck_board_sanctions_status CHECK (status IN ('ACTIVE', 'ENDED', 'LIFTED', 'VOIDED')),
    CONSTRAINT ck_board_sanctions_reason_code CHECK (reason_code IN ('SPAM', 'ABUSE', 'INVESTMENT', 'OFF_TOPIC', 'OTHER')),
    CONSTRAINT ck_board_sanctions_reason_text CHECK (reason_text IS NULL OR char_length(btrim(reason_text)) BETWEEN 1 AND 200),
    CONSTRAINT ck_board_sanctions_role CHECK (issued_role IN ('MANAGER', 'STAFF')),
    -- 계기 댓글은 글과 함께
    CONSTRAINT ck_board_sanctions_source CHECK (comment_id IS NULL OR post_id IS NOT NULL),
    -- 쓰기 제한만 끝이 있다
    CONSTRAINT ck_board_sanctions_ends CHECK ((kind <> 'WARNING') = (ends_at IS NOT NULL) AND (ends_at IS NULL OR ends_at > starts_at)),
    -- 경고는 끝나거나 풀리지 않는다(유효 · 무효뿐)
    CONSTRAINT ck_board_sanctions_warning_status CHECK (kind <> 'WARNING' OR status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_board_sanctions_lifted CHECK ((status <> 'LIFTED' OR lifted_at IS NOT NULL)
        AND (lifted_at IS NULL OR (kind <> 'WARNING' AND lift_reason IS NOT NULL AND lifted_by_type IN ('MANAGER', 'OPERATOR')))),
    CONSTRAINT ck_board_sanctions_lift_reason CHECK (lift_reason IS NULL OR char_length(btrim(lift_reason)) BETWEEN 1 AND 200),
    CONSTRAINT ck_board_sanctions_ended CHECK (status <> 'ENDED' OR ended_at IS NOT NULL),
    CONSTRAINT ck_board_sanctions_voided CHECK ((status = 'VOIDED') = (voided_at IS NOT NULL) AND (voided_at IS NULL OR void_source IN ('APPEAL', 'OPERATOR', 'REVIEW'))),
    CONSTRAINT ck_board_sanctions_void_reason CHECK (void_reason IS NULL OR char_length(btrim(void_reason)) BETWEEN 1 AND 200)
);
ALTER SEQUENCE board_sanction_no_seq OWNED BY board_sanctions.sanction_no;
-- 진행 중인 쓰기 제한은 (회원, 게시판)마다 하나 — 회원 앞이라 「이 회원의 진행 중 쓰기 제한 전부」(글·댓글 쓰기 판정, 요청당 한 번)도 이 색인을 탄다
CREATE UNIQUE INDEX ux_board_sanctions_running ON board_sanctions (user_id, board_id) WHERE status = 'ACTIVE' AND kind <> 'WARNING';
-- 회원 하나의 게시판 제재(내 제재 · 현황 · 권장 단계 · 반복 검토 판정 · 회원 상세) — 회원 범위
CREATE INDEX ix_board_sanctions_user ON board_sanctions (user_id, board_id, created_at DESC, id DESC);
-- 만료 크론(R__batch_40) — 끝난 쓰기 제한을 고른다
CREATE INDEX ix_board_sanctions_expire ON board_sanctions (ends_at) WHERE status = 'ACTIVE' AND ends_at IS NOT NULL;
-- 게시판 하나의 제재(매니저의 제재 내역 · 관리자 게시판 거르기) · 스텝 본인 것(내가 준 것만)
CREATE INDEX ix_board_sanctions_board ON board_sanctions (board_id, created_at DESC, id DESC);
CREATE INDEX ix_board_sanctions_issuer ON board_sanctions (board_id, issued_by, created_at DESC, id DESC);
-- 관리자 목록(전체 · 최신/오래된 순) · 범위 「진행 중」(진행 중 쓰기 제한만 — 결정 11) · 범위 「끝남」
CREATE INDEX ix_board_sanctions_created ON board_sanctions (created_at DESC, id DESC);
CREATE INDEX ix_board_sanctions_running_list ON board_sanctions (created_at DESC, id DESC) WHERE status = 'ACTIVE' AND kind <> 'WARNING';
CREATE INDEX ix_board_sanctions_closed_list ON board_sanctions (created_at DESC, id DESC) WHERE status <> 'ACTIVE';

-- ── 매니저·스텝 하루 사용량(한국 날짜) ────────────────────────────────────────
CREATE TABLE board_staff_usage (
    board_id   bigint      NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    usage_date date        NOT NULL,
    sanctions  integer     NOT NULL DEFAULT 0,
    posts      integer     NOT NULL DEFAULT 0,
    comments   integer     NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (board_id, user_id, usage_date),
    CONSTRAINT ck_board_staff_usage_counts CHECK (sanctions >= 0 AND posts >= 0 AND comments >= 0)
);
-- 지난 날 정리(R__batch_40 규칙 2)
CREATE INDEX ix_board_staff_usage_date ON board_staff_usage (usage_date);

-- ── 매니저·스텝 처리 기록 ─────────────────────────────────────────────────────
CREATE TABLE board_staff_actions (
    id                bigint       PRIMARY KEY,
    board_id          bigint       NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    actor_id          bigint       REFERENCES users (id) ON DELETE SET NULL,
    actor_role        varchar(8)   NOT NULL,
    kind              varchar(12)  NOT NULL,
    action            varchar(12)  NOT NULL,
    target_user_id    bigint       REFERENCES users (id) ON DELETE SET NULL,
    board_sanction_id bigint       REFERENCES board_sanctions (id) ON DELETE SET NULL,
    post_id           bigint,
    comment_id        bigint,
    reason            varchar(200),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_board_staff_actions_role CHECK (actor_role IN ('MANAGER', 'STAFF')),
    CONSTRAINT ck_board_staff_actions_kind CHECK (kind IN ('SANCTION')),
    CONSTRAINT ck_board_staff_actions_action CHECK (action IN ('ISSUE', 'LIFT'))
);
CREATE INDEX ix_board_staff_actions_board ON board_staff_actions (board_id, created_at DESC, id DESC);
CREATE INDEX ix_board_staff_actions_actor ON board_staff_actions (actor_id, created_at DESC) WHERE actor_id IS NOT NULL;
CREATE INDEX ix_board_staff_actions_sanction ON board_staff_actions (board_sanction_id) WHERE board_sanction_id IS NOT NULL;

-- ── 반복 검토 ─────────────────────────────────────────────────────────────────
CREATE SEQUENCE board_sanction_review_no_seq;
CREATE TABLE board_sanction_reviews (
    id                  bigint       PRIMARY KEY,
    review_no           bigint       NOT NULL DEFAULT nextval('board_sanction_review_no_seq'),
    board_id            bigint       NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    user_id             bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status              varchar(8)   NOT NULL DEFAULT 'OPEN',
    opened_at           timestamptz  NOT NULL,
    opened_count        integer      NOT NULL,
    counted_after       timestamptz,
    closed_at           timestamptz,
    closed_by           bigint       REFERENCES operators (id) ON DELETE SET NULL,
    result              varchar(16),
    memo                varchar(200),
    site_sanction_id    bigint       REFERENCES member_sanctions (id) ON DELETE SET NULL,
    voided_sanction_ids bigint[]     NOT NULL DEFAULT '{}',
    closed_count        integer,
    closed_board_count  integer,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_board_sanction_reviews_no UNIQUE (review_no),
    CONSTRAINT ck_board_sanction_reviews_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_board_sanction_reviews_result CHECK (result IS NULL OR result IN ('KEPT', 'SITE_SANCTIONED', 'VOIDED')),
    CONSTRAINT ck_board_sanction_reviews_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL) AND (status = 'CLOSED') = (result IS NOT NULL)),
    CONSTRAINT ck_board_sanction_reviews_memo CHECK (memo IS NULL OR char_length(btrim(memo)) BETWEEN 1 AND 200)
);
ALTER SEQUENCE board_sanction_review_no_seq OWNED BY board_sanction_reviews.review_no;
-- (회원, 게시판)마다 열린 건 하나 — 동시 부여는 회원 행 잠금이 먼저 줄 세우고, 이 색인 + ON CONFLICT DO NOTHING 이 마지막으로 막는다
CREATE UNIQUE INDEX ux_board_sanction_reviews_open ON board_sanction_reviews (user_id, board_id) WHERE status = 'OPEN';
-- 마지막으로 닫힌 검토(다시 세지 않는 경계) · 회원 상세의 열린 건 수
CREATE INDEX ix_board_sanction_reviews_user ON board_sanction_reviews (user_id, board_id, closed_at DESC);
-- 목록: 열린 건 오래된 순 · 닫힌 건 최근 닫은 순 · 메뉴 배지(열린 건 수)
CREATE INDEX ix_board_sanction_reviews_open_list ON board_sanction_reviews (opened_at, id) WHERE status = 'OPEN';
CREATE INDEX ix_board_sanction_reviews_closed_list ON board_sanction_reviews (closed_at DESC, id DESC) WHERE status = 'CLOSED';

-- ── 이의제기: 게시판 제재도 같은 표(결정 4) ─────────────────────────────────────
ALTER TABLE sanction_appeals ALTER COLUMN sanction_id DROP NOT NULL;
ALTER TABLE sanction_appeals ADD COLUMN board_sanction_id bigint REFERENCES board_sanctions (id) ON DELETE CASCADE;
-- 사이트 제재 · 게시판 제재 중 하나만
ALTER TABLE sanction_appeals ADD CONSTRAINT ck_sanction_appeals_target CHECK ((sanction_id IS NULL) <> (board_sanction_id IS NULL));
-- 게시판 제재 하나에 이의제기 하나(기각 뒤에도 다시 접수하지 못한다 — 사이트 제재와 같다). NULL 은 서로 겹치지 않는다
ALTER TABLE sanction_appeals ADD CONSTRAINT ux_sanction_appeals_board_sanction UNIQUE (board_sanction_id);

-- ── 회원 알림 종류: V136 목록 ∪ BOARD_SANCTION_ISSUED(결정 6 — 끌 수 없는 계정 알림) ──
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
                    'BOARD_SANCTION_ISSUED'));
