-- 회원 제재·이의제기(docs/features/member-sanction-contract.md — 결정 1~4 · 정책 1~8). 사이트 제재만(게시판 한정 제재는 따로).
-- · member_sanctions: 운영자가 준 사이트 제재 한 건. 단계 경고(1·2회째) → 정지 3일 → 7일 → 30일 → 영구 정지(kind). 다음 단계는 서버가 유효한(VOIDED 아닌) 제재로 센다.
--   sanction_no: 운영자가 부르는 짧은 번호(DB 시퀀스 — 앱은 넣지 않는다). warning_no: 경고일 때 유효한 경고 중 몇 번째(1·2) — 경고가 무효(인용)가 되면 남은 경고를 다시 매긴다.
--   status: ACTIVE(유효 — 정지·영구 정지는 진행 중) · ENDED(기간 끝 · 영구 정지로 대체, ended_at) · LIFTED(운영자 해제 — 정지·영구만, 횟수에 남음) · VOIDED(이의제기 인용 — 횟수에서 빠짐).
--   LIFTED·ENDED 뒤에도 이의제기가 인용되면 VOIDED 가 된다(lifted_at·ended_at 은 남는다). 진행 중인 정지·영구 정지는 회원당 하나(부분 유일 색인).
--   acknowledged_at: 회원이 경고 안내를 「확인했어요」 한 시각(정지·영구 정지는 쓰지 않는다 — 늘 확인한 것으로 본다).
--   report_case_id: 신고 처리에서 줬거나(resolve 의 sanction) 부여 때 이은 신고 사건.
-- · sanction_appeals: 제재 하나에 이의제기 하나(유일 — 기각 뒤 재접수 없음). 접수는 제재 시작 후 30일 안(앱이 검사, 결정 3). appeal_no: 짧은 접수번호(DB 시퀀스).
-- · 정지 만료는 DB 배치 sanction-expire(R__batch_39, pg_cron 1분) + 회원 쪽 읽기 전 마감(앱). 회원 상태(users.status SUSPENDED · BANNED)는 앱·배치가 회원 행을 잠그고 바꾼다.
-- · report_cases.action 에 SANCTION_ONLY(「제재만」 — 대상은 그대로, 작성자만 제재). 13자라 칸을 varchar(16) 으로 넓힌다(색인 없는 칸 — 표를 다시 쓰지 않는다).
-- · 회원 알림 종류 SANCTION_ISSUED · SANCTION_LIFTED · APPEAL_ANSWERED.
-- 감사 행위(SANCTION_ISSUE · SANCTION_LIFT · APPEAL_ACCEPT · APPEAL_REJECT)는 DB CHECK 가 없다(audit_logs.action 은 자유 글자).

-- ── 제재 ────────────────────────────────────────────────────────────────────
CREATE SEQUENCE member_sanction_no_seq;
CREATE TABLE member_sanctions (
    id              bigint       PRIMARY KEY,
    sanction_no     bigint       NOT NULL DEFAULT nextval('member_sanction_no_seq'),
    user_id         bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind            varchar(12)  NOT NULL,
    status          varchar(8)   NOT NULL DEFAULT 'ACTIVE',
    warning_no      smallint,
    skipped         boolean      NOT NULL DEFAULT false,
    reason          varchar(500) NOT NULL,
    starts_at       timestamptz  NOT NULL,
    ends_at         timestamptz,
    issued_by       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    report_case_id  bigint       REFERENCES report_cases (id) ON DELETE SET NULL,
    acknowledged_at timestamptz,
    lifted_by       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    lifted_at       timestamptz,
    lift_reason     varchar(500),
    voided_at       timestamptz,
    ended_at        timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_member_sanctions_no UNIQUE (sanction_no),
    CONSTRAINT ck_member_sanctions_kind CHECK (kind IN ('WARNING', 'SUSPEND_3', 'SUSPEND_7', 'SUSPEND_30', 'BAN')),
    CONSTRAINT ck_member_sanctions_status CHECK (status IN ('ACTIVE', 'ENDED', 'LIFTED', 'VOIDED')),
    -- 기간 정지만 끝이 있다
    CONSTRAINT ck_member_sanctions_ends CHECK ((kind IN ('SUSPEND_3', 'SUSPEND_7', 'SUSPEND_30')) = (ends_at IS NOT NULL) AND (ends_at IS NULL OR ends_at > starts_at)),
    CONSTRAINT ck_member_sanctions_warning_no CHECK (warning_no IS NULL OR (kind = 'WARNING' AND warning_no IN (1, 2))),
    -- 경고는 끝나거나 풀리지 않는다(유효 · 무효뿐)
    CONSTRAINT ck_member_sanctions_warning_status CHECK (kind <> 'WARNING' OR status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_member_sanctions_lifted CHECK ((status <> 'LIFTED' OR lifted_at IS NOT NULL) AND (lifted_at IS NULL OR (kind <> 'WARNING' AND lift_reason IS NOT NULL))),
    CONSTRAINT ck_member_sanctions_ended CHECK (status <> 'ENDED' OR ended_at IS NOT NULL),
    CONSTRAINT ck_member_sanctions_voided CHECK ((status = 'VOIDED') = (voided_at IS NOT NULL)),
    CONSTRAINT ck_member_sanctions_reason CHECK (char_length(btrim(reason)) BETWEEN 1 AND 500)
);
ALTER SEQUENCE member_sanction_no_seq OWNED BY member_sanctions.sanction_no;
-- 회원 하나의 제재(현황·이력·다음 단계 계산·내 제재) — 회원 범위 쿼리
CREATE INDEX ix_member_sanctions_user ON member_sanctions (user_id, created_at DESC, id DESC);
-- 진행 중인 정지·영구 정지는 회원당 하나 — 동시 부여는 회원 행 잠금이 먼저 막고, 이 색인이 마지막으로 막는다
CREATE UNIQUE INDEX ux_member_sanctions_running ON member_sanctions (user_id) WHERE status = 'ACTIVE' AND kind <> 'WARNING';
-- 만료 크론(R__batch_39) — 끝난 기간 정지를 고른다
CREATE INDEX ix_member_sanctions_expire ON member_sanctions (ends_at) WHERE status = 'ACTIVE' AND ends_at IS NOT NULL;
-- 관리자 목록(전체 · 최신/오래된 순) · 범위 「진행 중」(목록·건수·대시보드) · 범위 「끝남」
CREATE INDEX ix_member_sanctions_created ON member_sanctions (created_at DESC, id DESC);
CREATE INDEX ix_member_sanctions_running_list ON member_sanctions (created_at DESC, id DESC) WHERE status = 'ACTIVE' AND kind <> 'WARNING';
CREATE INDEX ix_member_sanctions_closed_list ON member_sanctions (created_at DESC, id DESC) WHERE status <> 'ACTIVE';
-- 신고 사건에서 준 제재(사건이 지워질 때 SET NULL · 사건 → 제재)
CREATE INDEX ix_member_sanctions_report_case ON member_sanctions (report_case_id) WHERE report_case_id IS NOT NULL;

-- ── 이의제기 ──────────────────────────────────────────────────────────────────
CREATE SEQUENCE sanction_appeal_no_seq;
CREATE TABLE sanction_appeals (
    id          bigint       PRIMARY KEY,
    appeal_no   bigint       NOT NULL DEFAULT nextval('sanction_appeal_no_seq'),
    sanction_id bigint       NOT NULL REFERENCES member_sanctions (id) ON DELETE CASCADE,
    user_id     bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status      varchar(8)   NOT NULL DEFAULT 'PENDING',
    reason      varchar(500) NOT NULL,
    answer      varchar(500),
    answered_by bigint       REFERENCES operators (id) ON DELETE SET NULL,
    answered_at timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_sanction_appeals_no UNIQUE (appeal_no),
    -- 제재 하나에 이의제기 하나(기각 뒤에도 다시 접수하지 못한다 — 정책 6)
    CONSTRAINT ux_sanction_appeals_sanction UNIQUE (sanction_id),
    CONSTRAINT ck_sanction_appeals_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_sanction_appeals_answered CHECK ((status = 'PENDING') = (answered_at IS NULL) AND (status = 'PENDING' OR answer IS NOT NULL)),
    CONSTRAINT ck_sanction_appeals_reason CHECK (char_length(btrim(reason)) BETWEEN 10 AND 500),
    CONSTRAINT ck_sanction_appeals_answer CHECK (answer IS NULL OR char_length(btrim(answer)) BETWEEN 1 AND 500)
);
ALTER SEQUENCE sanction_appeal_no_seq OWNED BY sanction_appeals.appeal_no;
-- 대기 오래된 순(목록 기본 · 메뉴 요약 · 대시보드 오래된 3건)
CREATE INDEX ix_sanction_appeals_pending ON sanction_appeals (created_at, id) WHERE status = 'PENDING';
-- 상태 거르기 목록 · 상태별 건수
CREATE INDEX ix_sanction_appeals_status ON sanction_appeals (status, created_at, id);
CREATE INDEX ix_sanction_appeals_created ON sanction_appeals (created_at, id);
CREATE INDEX ix_sanction_appeals_user ON sanction_appeals (user_id);

-- ── 신고 사건 조치: + SANCTION_ONLY(「제재만」 — 결정 4). V122 목록 ∪ SANCTION_ONLY ──
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다
ALTER TABLE report_cases ALTER COLUMN action TYPE varchar(16);
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_action;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_action
    CHECK (action IS NULL OR action IN ('HIDE', 'DELETE', 'KEEP_HIDDEN', 'DEACTIVATE', 'DEAL_END', 'SANCTION_ONLY', 'DISMISS'));

-- ── 회원 알림 종류: V122 목록 ∪ SANCTION_ISSUED · SANCTION_LIFTED · APPEAL_ANSWERED(정책 8 — 끌 수 없는 계정 알림) ──
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
                    'SANCTION_ISSUED', 'SANCTION_LIFTED', 'APPEAL_ANSWERED'));
