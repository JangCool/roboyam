-- 관리자 「신고 관리」 — docs/features/report-admin-contract.md 「DB」·「서버 구현 결과」
--
-- · report_cases: 같은 대상(글·댓글)의 신고를 한 줄로 묶은 사건. 대상마다 열린(PENDING·IN_REVIEW) 사건은 하나(부분 유일 색인).
--   닫힌 사건(RESOLVED·DISMISSED) 뒤에 들어온 신고는 새 사건으로 0부터 센다(자동 숨김 셈·운영자 알림 PER_CASE 도 새로).
--   case_no: 운영자가 부르는 짧은 번호 — DB 시퀀스가 매긴다(앱은 넣지 않는다). post_id: 대상 글(댓글 사건이면 그 댓글의 글).
--   action: HIDE(운영자 숨김) · DELETE · KEEP_HIDDEN(자동 숨김 확정) · DISMISS(무시). reason_code: 조치 사유 코드(신고 사유와 같은 체계), note: 운영자가 적은 글(1~500자).
--   false_report: 「허위 신고로 표시」한 무시 — 그 사건의 신고들이 REJECTED 가 되어 신고 남용 차단(반려 제한)이 센다. 파티션 표(posts·comments)를 가리키는 외래 키는 두지 않는다.
-- · reports.case_id 추가. 기존 처리 대기(PENDING) 신고는 대상별 사건으로 묶어 옮긴다(이미 처리된 옛 신고는 사건 없이 둔다).
--   사건 ID 는 그 묶음의 가장 작은 신고 ID 를 쓴다(신고 ID 는 Snowflake 라 사건 표 안에서도 겹치지 않는다).
-- · reports.status 에 DISMISSED(그냥 무시)를 더한다: 조치로 닫히면 ACCEPTED, 허위 표시 무시면 REJECTED, 그냥 무시면 DISMISSED, 다시 열면 PENDING.
-- · report_events: 사건 기록(쌓기만 한다). type REPORTED · AUTO_HIDDEN · REVIEW_STARTED · RESOLVED · DISMISSED · REOPENED.
--   actor_kind OPERATOR(actor_id = operators.id) · SYSTEM · MEMBER(신고 — actor_id 는 비운다: 신고자는 보유기간 배치가 지우는 reports 에만 남기고, 기록에는 detail.reportId 만).
-- · 회원 알림 종류에 COMMENT_REMOVED(운영자가 내 댓글을 지움)·REPORT_RESULT(내 신고의 처리 결과)를 더한다.
--   회원 이벤트 REPORT_RESULT 는 이제 실제로 발행된다 — V59 가 「준비 중」이라 꺼 둔 값을 코드의 처음 값(사용·앱 알림 켬, 이메일 끔)으로 돌린다
--   (준비 중 이벤트는 화면에서 바꿀 수 없었으므로 운영자가 고른 값이 아니다).
-- · 운영자 권한 카탈로그에서 쓰이지 않는 bannedword.read·bannedword.settings 를 뺀다(금지어는 게시판 기본 설정 › 보안 = board.settings).
--   역할에 저장된 행과 권한 행을 지운다(서버 기동 때 카탈로그 동기화도 같은 일을 한다 — 여기서는 먼저 확실히).
-- · 보유기간 배치(R__batch_18)는 열린 사건의 신고를 지우지 않는다 — 반복 마이그레이션 쪽에서 고친다.

-- ── 사건 ────────────────────────────────────────────────────────────────────
CREATE SEQUENCE report_case_no_seq;

CREATE TABLE report_cases (
    id                bigint       PRIMARY KEY,
    case_no           bigint       NOT NULL DEFAULT nextval('report_case_no_seq'),
    target_type       varchar(8)   NOT NULL,
    target_id         bigint       NOT NULL,
    post_id           bigint       NOT NULL,
    board_id          bigint       NOT NULL,
    target_author_id  bigint,
    status            varchar(10)  NOT NULL DEFAULT 'PENDING',
    report_count      integer      NOT NULL DEFAULT 0,
    first_reported_at timestamptz  NOT NULL,
    last_reported_at  timestamptz  NOT NULL,
    reviewer_id       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    review_started_at timestamptz,
    closed_at         timestamptz,
    closed_by         bigint       REFERENCES operators (id) ON DELETE SET NULL,
    action            varchar(12),
    reason_code       varchar(20),
    note              varchar(500),
    false_report      boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_report_cases_case_no UNIQUE (case_no),
    CONSTRAINT ck_report_cases_target_type CHECK (target_type IN ('POST', 'COMMENT')),
    CONSTRAINT ck_report_cases_status CHECK (status IN ('PENDING', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_report_cases_action CHECK (action IS NULL OR action IN ('HIDE', 'DELETE', 'KEEP_HIDDEN', 'DISMISS')),
    CONSTRAINT ck_report_cases_reason_code CHECK (reason_code IS NULL OR reason_code IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'OTHER')),
    CONSTRAINT ck_report_cases_closed CHECK ((status IN ('RESOLVED', 'DISMISSED')) = (closed_at IS NOT NULL)),
    CONSTRAINT ck_report_cases_count CHECK (report_count >= 0)
);
ALTER SEQUENCE report_case_no_seq OWNED BY report_cases.case_no;
-- 대상마다 열린 사건 하나
CREATE UNIQUE INDEX ux_report_cases_open_target ON report_cases (target_type, target_id) WHERE status IN ('PENDING', 'IN_REVIEW');
-- 목록(상태 거르기 + 마지막 신고 순)·메뉴 요약
CREATE INDEX ix_report_cases_status_last ON report_cases (status, last_reported_at DESC);
CREATE INDEX ix_report_cases_last ON report_cases (last_reported_at DESC);
-- 대상의 사건들(다시 열기 확인)·글과 그 댓글의 사건(게시글 관리 「신고」에서 넘어옴)
CREATE INDEX ix_report_cases_target ON report_cases (target_type, target_id);
CREATE INDEX ix_report_cases_post ON report_cases (post_id);
-- 작성자 카드의 최근 닫힌 사건 수
CREATE INDEX ix_report_cases_author_closed ON report_cases (target_author_id, closed_at) WHERE target_author_id IS NOT NULL AND closed_at IS NOT NULL;

-- ── 신고 → 사건 ─────────────────────────────────────────────────────────────
ALTER TABLE reports ADD COLUMN case_id bigint REFERENCES report_cases (id) ON DELETE SET NULL;
CREATE INDEX ix_reports_case ON reports (case_id);

ALTER TABLE reports DROP CONSTRAINT ck_reports_status;
ALTER TABLE reports ADD CONSTRAINT ck_reports_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'DISMISSED'));

-- 기존 처리 대기 신고를 대상별 사건으로(번호는 첫 신고 차례로). 글·게시판·작성자는 가장 최근 신고의 값
INSERT INTO report_cases (id, target_type, target_id, post_id, board_id, target_author_id, status, report_count, first_reported_at, last_reported_at,
                          created_at, updated_at)
SELECT g.id, g.target_type, g.target_id, g.post_id, g.board_id, g.target_author_id, 'PENDING', g.report_count, g.first_at, g.last_at, now(), now()
FROM (SELECT min(r.id)                                                AS id,
             r.target_type,
             r.target_id,
             (array_agg(r.post_id ORDER BY r.created_at DESC))[1]          AS post_id,
             (array_agg(r.board_id ORDER BY r.created_at DESC))[1]         AS board_id,
             (array_agg(r.target_author_id ORDER BY r.created_at DESC))[1] AS target_author_id,
             count(*)::integer                                        AS report_count,
             min(r.created_at)                                        AS first_at,
             max(r.created_at)                                        AS last_at
      FROM reports r
      WHERE r.status = 'PENDING'
      GROUP BY r.target_type, r.target_id) g
ORDER BY g.first_at, g.id;

UPDATE reports r
SET case_id = c.id
FROM report_cases c
WHERE r.status = 'PENDING'
  AND c.target_type = r.target_type
  AND c.target_id = r.target_id;

-- ── 사건 기록 ────────────────────────────────────────────────────────────────
CREATE TABLE report_events (
    id         bigint       PRIMARY KEY,
    case_id    bigint       NOT NULL REFERENCES report_cases (id) ON DELETE CASCADE,
    type       varchar(16)  NOT NULL,
    actor_kind varchar(10)  NOT NULL,
    actor_id   bigint,
    detail     jsonb,
    created_at timestamptz  NOT NULL,
    CONSTRAINT ck_report_events_type CHECK (type IN ('REPORTED', 'AUTO_HIDDEN', 'REVIEW_STARTED', 'RESOLVED', 'DISMISSED', 'REOPENED')),
    CONSTRAINT ck_report_events_actor CHECK (actor_kind IN ('OPERATOR', 'SYSTEM', 'MEMBER') AND (actor_kind <> 'OPERATOR' OR actor_id IS NOT NULL))
);
CREATE INDEX ix_report_events_case ON report_events (case_id, id);

-- 옮긴 신고마다 REPORTED 한 줄(기록 ID 는 신고 ID — 겹치지 않는다)
INSERT INTO report_events (id, case_id, type, actor_kind, actor_id, detail, created_at)
SELECT r.id, r.case_id, 'REPORTED', 'MEMBER', NULL, jsonb_build_object('reportId', r.id::text, 'reason', r.reason), r.created_at
FROM reports r
WHERE r.case_id IS NOT NULL;

-- ── 회원 알림 종류 COMMENT_REMOVED · REPORT_RESULT ─────────────────────────
-- ⚠ 합칠 때 합집합 확인 — V80 의 목록 + COMMENT_REMOVED · REPORT_RESULT. 다른 세션(커머스 등)도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT'));

-- 회원 이벤트 REPORT_RESULT — 준비 중(V59 꺼 둠) → 실제 이벤트의 처음 값
UPDATE member_event_settings
SET enabled = true,
    in_app = true,
    email = false
WHERE code = 'REPORT_RESULT';

-- ── 쓰이지 않는 운영자 권한 ─────────────────────────────────────────────────
DELETE FROM role_permissions WHERE permission_code IN ('bannedword.read', 'bannedword.settings');
DELETE FROM permissions WHERE code IN ('bannedword.read', 'bannedword.settings');
