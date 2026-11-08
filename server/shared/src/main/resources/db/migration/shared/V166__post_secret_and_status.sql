-- 운영 게시판(버그 신고 · 기능 요청)용 게시판 설정 두 가지 — 비밀글 · 처리 상태. 모든 일반 게시판(kind = 'GENERAL')에서 켤 수 있다(관리자 게시판 설정).
-- 게시판마다의 값이다(board_defaults 에서 물려받지 않는다 — ads_hidden(V117)·gadget_exclusions(V69)와 같다).
--
-- · boards.secret_mode: 비밀글 방식 OFF(없음) · OPTIONAL(글쓴이가 고름) · ALWAYS(모든 글 비밀). 비회원 글은 늘 비밀이 아니다(앱 규칙).
-- · posts.secret: 비밀글인가 — 쓸 때(와 OPTIONAL 게시판에서 글쓴이가 고칠 때) 정하고, 게시판 설정을 나중에 바꿔도 이미 쓴 글의 값은 그대로다(관리자 글 옮기기도).
--   읽을 수 있는 사람 = 글쓴이(회원) · 그 게시판 매니저·스텝 · 운영자 회원 계정 · 관리자 앱 운영자. 섞인 목록(최신글·통합 검색·인기글·회원 작성글·가젯·팁)에서는 빠진다
--   (PostRepositoryQDSL#feedVisible · PostSearchQueries · R__batch_24 popular_rank_candidates 가 같은 판정).
-- · boards.status_enabled: 처리 상태를 쓰는가. 켜진 게시판의 새 글은 RECEIVED 로 쓴다(앱). 값이 없는 글(켜기 전에 쓴 글)은 켜진 게시판에서 RECEIVED 로 보인다.
-- · posts.process_status: RECEIVED(접수) · CHECKING(확인 중) · IN_PROGRESS(진행 중) · DONE(완료) · ON_HOLD(보류) · REJECTED(반려) · DUPLICATE(중복), NULL = 아직 없음.
--   process_status_at / process_status_by: 마지막으로 바꾼 시각 · 바꾼 회원(공개 사이트의 매니저·스텝·운영자 회원 계정 — users.id). 관리자 앱 운영자가 바꾸면 by 는 NULL
--   (누가 바꿨는지는 감사 기록 POST_PROCESS_STATUS 에 남는다). 처음 접수(RECEIVED)는 둘 다 NULL. 글 표는 id 월 범위 파티션이라 외래 키를 걸지 않는다(board_id 와 같다).
-- 파티션 표에 상수 기본값이 있는 칸을 더하는 것은 「빠른 기본값」이라 표를 다시 쓰지 않는다(V56 과 같다). CHECK 는 NOT VALID 로 걸어 기존 행을 훑지 않는다
-- (새 칸이라 기존 행은 모두 맞고, 새로 쓰는 행은 검사된다).

-- ── 게시판 설정 ──────────────────────────────────────────────────────────────
ALTER TABLE boards ADD COLUMN secret_mode varchar(8) NOT NULL DEFAULT 'OFF';
ALTER TABLE boards ADD CONSTRAINT ck_boards_secret_mode CHECK (secret_mode IN ('OFF', 'OPTIONAL', 'ALWAYS'));
ALTER TABLE boards ADD COLUMN status_enabled boolean NOT NULL DEFAULT false;

-- ── 글 ───────────────────────────────────────────────────────────────────────
ALTER TABLE posts
    ADD COLUMN secret            boolean     NOT NULL DEFAULT false,
    ADD COLUMN process_status    varchar(16),
    ADD COLUMN process_status_at timestamptz,
    ADD COLUMN process_status_by bigint;

ALTER TABLE posts ADD CONSTRAINT ck_posts_process_status
    CHECK (process_status IS NULL OR process_status IN ('RECEIVED', 'CHECKING', 'IN_PROGRESS', 'DONE', 'ON_HOLD', 'REJECTED', 'DUPLICATE')) NOT VALID;

-- 게시판 목록의 상태 거르기(?status=) — 상태를 쓰는 게시판의 글만 값이 있으므로 부분 색인(파티션 부모에 만들면 모든 조각에 붙는다, V91 과 같다).
-- RECEIVED 거르기는 값 없는 글도 포함하므로 그 부분은 ix_posts_board(board_id, id DESC) 가 맡는다.
CREATE INDEX ix_posts_board_process_status ON posts (board_id, process_status, id DESC) WHERE process_status IS NOT NULL AND deleted_at IS NULL;

-- ── 회원 알림 종류: V163 목록 ∪ POST_STATUS_CHANGED(내 글의 처리 상태가 바뀜 — 글쓴이에게, 바꾼 사람이 본인이면 보내지 않는다) ──
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
                    'POST_STATUS_CHANGED'));
