-- 소모임 신고 — 신고 대상 종류에 소모임(GROUP)을 더한다(community docs/api-report.md 1절 「대상과 사유」·5절 resolve DEACTIVATE, api-groups.md 6-2).
--
-- · reports·report_cases.target_type 에 GROUP. target_id = 소모임 ID, board_id = 그 소모임의 게시판(groups.board_id), target_author_id = 신고 당시 모임장.
--   글이 없으므로 post_id 는 소모임 신고에서만 비운다(NOT NULL 을 풀고 「GROUP 이면 NULL, 아니면 값」 CHECK). 기존 행은 모두 글·댓글이라 그대로 맞는다.
-- · 신고 사유에 소모임 사유 셋: PROMOTION(홍보·영업 목적) · OFFLINE_RISK(오프라인 모임 위험) · STOCK_RECOMMENDATION(종목 추천·유료 모집 — 투자 고지 게시판의 소모임만, 앱이 검사).
--   소모임 신고의 사유는 이 셋 + OTHER(설명 필수)이고 글·댓글 사유와 섞어 쓰지 못하게 앱이 막는다. 조치 사유 코드(report_cases.reason_code)도 같은 체계.
-- · 조치(report_cases.action)에 DEACTIVATE(소모임 비활성화 — 관리자 소모임 관리의 비활성화와 같은 서비스, groups.deactivate_case_id 에 사건 ID).
-- · groups.deactivate_case_id 가 report_cases 를 가리킨다(사건은 지우지 않는다 — 혹시 지워지면 기록만 비운다).
-- · 소모임 신고에는 자동 숨김이 없다(community 와 같다 — 운영자가 처리하기 전까지 소모임은 그대로 열려 있다).

-- ── 대상 종류 ───────────────────────────────────────────────────────────────
ALTER TABLE reports DROP CONSTRAINT ck_reports_target_type;
ALTER TABLE reports ADD CONSTRAINT ck_reports_target_type CHECK (target_type IN ('POST', 'COMMENT', 'GROUP'));
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_target_type;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_target_type CHECK (target_type IN ('POST', 'COMMENT', 'GROUP'));

-- ── 글 ID — 소모임 신고만 비운다 ────────────────────────────────────────────
ALTER TABLE reports ALTER COLUMN post_id DROP NOT NULL;
ALTER TABLE reports ADD CONSTRAINT ck_reports_post CHECK ((target_type = 'GROUP') = (post_id IS NULL));
ALTER TABLE report_cases ALTER COLUMN post_id DROP NOT NULL;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_post CHECK ((target_type = 'GROUP') = (post_id IS NULL));

-- ── 사유 ───────────────────────────────────────────────────────────────────
ALTER TABLE reports DROP CONSTRAINT ck_reports_reason;
ALTER TABLE reports ADD CONSTRAINT ck_reports_reason
    CHECK (reason IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION', 'OTHER'));
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_reason_code;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_reason_code
    CHECK (reason_code IS NULL OR reason_code IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION',
                                                  'OTHER'));

-- ── 조치 ───────────────────────────────────────────────────────────────────
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_action;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_action CHECK (action IS NULL OR action IN ('HIDE', 'DELETE', 'KEEP_HIDDEN', 'DEACTIVATE', 'DISMISS'));

-- ── 소모임 비활성 기록의 사건 ──────────────────────────────────────────────
ALTER TABLE groups ADD CONSTRAINT fk_groups_deactivate_case FOREIGN KEY (deactivate_case_id) REFERENCES report_cases (id) ON DELETE SET NULL;
