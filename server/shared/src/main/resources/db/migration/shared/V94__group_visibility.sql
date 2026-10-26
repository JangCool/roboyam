-- 소모임 5단계(공개 범위·공개 요청) — docs/features/group-contract.md 「전체 개발 계획」 5단계(community docs/api-groups.md 11절 · community V84__group_visibility.sql 을 로보얌에 맞춘 것).
-- 공개 범위 칸(groups.visibility · visibility_changed_*)·설정(group_settings 공개 범위 구획 · visibility_notice_sent_at)은 V90 이 이미 만들었다 — community 의
-- group_visibility_rollout(안내 알림 선점)·group_visibility_settings(켜기·숫자 다섯)는 group_settings 한 행이 맡는다(이 파일은 만들지 않는다).
--
--   · posts.is_public            : 뜻이 「비멤버에게 보이는가(유효 노출)」로 넓어진다(V91 은 「공개로 전환됐는가」). 읽는 쪽(글 탭·글 수·글 상세 판정·해산)은 바뀌지 않는다.
--                                  저장 규칙(쓰는 길 GroupPostVisibilityService 가 지킨다): is_public = (소모임 PUBLIC AND members_only_at IS NULL) OR public_granted_at IS NOT NULL
--   · posts.public_granted_at/by : 비공개 소모임에서 승인·직접 공개(공개 범위 기능이 꺼져 있으면 작성자의 공개 전환)된 시각·처리자. NULL 이면 개별 공개가 아니다.
--                                  비멤버에게는 이 시각 이전의 댓글을 가린다(hiddenBy MEMBERS_ONLY — 읽을 때 계산).
--   · posts.members_only_at/by   : 공개 소모임에서 모임장·매니저가 글 하나를 멤버 전용으로 돌린 시각·처리자. 비공개 소모임에서는 잠자는 값이다.
--     public_granted_at 과 members_only_at 은 서로 배타다(하나를 쓰면 다른 하나를 지운다). 처리자는 회원 ID(글 표는 파티션이라 FK 없음 — board_id 와 같다).
--   · group_post_publish_requests : 공개 요청 — 글당 한 행(PK post_id). 거절된 뒤 다시 요청하면 같은 행을 PENDING 으로 되살린다(거절 횟수는 이어진다).
--   · group_visibility_changes    : 공개 범위 변경 이력(방향 · 변경한 모임장 · 시각 · 바뀐 글·댓글 수 · 멤버 전용으로 남은 글 수).
-- 번호: 소모임 1~4단계가 V90~V93(develop 병합 때 다시 매김) — 합칠 때 develop 의 마지막 번호를 다시 본다.

-- ── 글의 공개 기록 ──────────────────────────────────────────────────────────────
-- 칸 추가는 파티션 부모에만 하면 모든 조각에 같이 붙는다(V91 과 같다).
ALTER TABLE posts
    ADD COLUMN public_granted_at timestamptz,
    ADD COLUMN public_granted_by bigint,
    ADD COLUMN members_only_at   timestamptz,
    ADD COLUMN members_only_by   bigint;

-- 일반 글은 공개 기록이 없고, 두 기록은 함께 있을 수 없다(새 칸이라 기존 행은 모두 맞는다 — 큰 표를 다시 훑지 않게 NOT VALID, V91 과 같다)
ALTER TABLE posts ADD CONSTRAINT ck_posts_group_visibility
    CHECK ((group_id IS NOT NULL OR (public_granted_at IS NULL AND members_only_at IS NULL)) AND (public_granted_at IS NULL OR members_only_at IS NULL)) NOT VALID;

-- 백필: 이미 공개로 전환된 소모임 글(소모임은 모두 비공개 소모임 — 공개 범위 기능이 지금까지 꺼져 있었다). 공개 시각을 모르므로 글 작성 시각을 쓴다 —
-- 그 글의 지금 댓글이 전부 공개로 남는다(지금까지와 같다). 다시 돌려도 같은 결과(public_granted_at IS NULL 인 것만).
UPDATE posts SET public_granted_at = created_at, public_granted_by = author_id
 WHERE group_id IS NOT NULL AND is_public AND public_granted_at IS NULL;

-- 공개 범위 변경의 미리보기·일괄 갱신(소모임별 멤버 전용 글 · 개별 공개 글) — 글 탭 색인(ix_posts_group)으로 소모임 글을 고른 뒤 거른다(별도 색인 없음)

-- ── 공개 요청 ───────────────────────────────────────────────────────────────────
-- status: PENDING(처리 대기) · APPROVED(승인 — 글이 공개됐다) · REJECTED(거절 — reject_reason, reject_count + 1) · CANCELED(닫힘 — cancel_reason).
-- cancel_reason: AUTHOR(작성자가 거둠) · AUTHOR_LEFT(작성자가 소모임을 스스로 나감 — 다시 가입해도 되살리지 않는다) · GROUP_PUBLIC(소모임이 공개 소모임이 됨)
--   · UNPUBLISHED(승인된 글을 되돌림 — 거절 횟수를 지운다). 예약 값 POST_GONE 은 쓰지 않는다(지워졌거나 가려진 글의 요청은 읽을 때 거른다).
-- note·reject_reason: 관리자 설정 textMax(10~100자) 이하 — 칸은 100자. reject_count: 거절 한도(rejectLimit 1~3)의 바탕.
-- requested_at: 마지막으로 보낸(다시 보낸) 시각 — 하루 요청 수 한도도 이 시각으로 센다. decided_*: 승인·거절한 사람(모임장 · 「글 공개 처리·되돌리기」 매니저).
CREATE TABLE group_post_publish_requests (
    post_id        bigint       PRIMARY KEY,
    group_id       bigint       NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    requester_id   bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status         varchar(10)  NOT NULL,
    note           varchar(100),
    requested_at   timestamptz  NOT NULL,
    decided_by     bigint       REFERENCES users (id) ON DELETE SET NULL,
    decided_at     timestamptz,
    reject_reason  varchar(100),
    reject_count   smallint     NOT NULL DEFAULT 0,
    cancel_reason  varchar(20),
    CONSTRAINT ck_gppr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED')),
    CONSTRAINT ck_gppr_reject_count CHECK (reject_count BETWEEN 0 AND 3),
    CONSTRAINT ck_gppr_cancel_reason CHECK (cancel_reason IS NULL OR cancel_reason IN ('AUTHOR', 'AUTHOR_LEFT', 'GROUP_PUBLIC', 'UNPUBLISHED', 'POST_GONE')),
    CONSTRAINT ck_gppr_canceled CHECK ((status = 'CANCELED') = (cancel_reason IS NOT NULL))
);
-- 처리 대기 목록·대기 수(소모임별, 오래된 요청이 위)
CREATE INDEX ix_gppr_group_pending ON group_post_publish_requests (group_id, requested_at, post_id) WHERE status = 'PENDING';
-- 하루 요청 수 한도(요청자별 최근 24시간) · 나가기의 대기 요청 닫기
CREATE INDEX ix_gppr_requester ON group_post_publish_requests (requester_id, requested_at);

-- ── 공개 범위 변경 이력 ─────────────────────────────────────────────────────────
-- 값이 실제로 바뀐 변경마다 한 줄(같은 값으로 바꾸는 요청은 남기지 않는다). 고치거나 지우지 않는다.
-- post_count·comment_count: 그 방향으로 바뀐 글·댓글 수(PUBLIC: 멤버 전용 기록이 없는 글 · PRIVATE: 그때 비멤버에게 보이던 글). members_only_count: 멤버 전용으로 남은(잠든) 글 수.
CREATE TABLE group_visibility_changes (
    id                 bigint       PRIMARY KEY,
    group_id           bigint       NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    from_value         varchar(10)  NOT NULL,
    to_value           varchar(10)  NOT NULL,
    changed_by         bigint       REFERENCES users (id) ON DELETE SET NULL,
    changed_at         timestamptz  NOT NULL,
    post_count         integer      NOT NULL,
    comment_count      integer      NOT NULL,
    members_only_count integer      NOT NULL DEFAULT 0,
    CONSTRAINT ck_gvc_values CHECK (from_value IN ('PUBLIC', 'PRIVATE') AND to_value IN ('PUBLIC', 'PRIVATE') AND from_value <> to_value)
);
CREATE INDEX ix_gvc_group ON group_visibility_changes (group_id, changed_at DESC);

-- ── 회원 알림 종류: 공개 범위·공개 요청 7종(api-groups.md 11-8 — 모두 끌 수 없다, 이름은 40자 이내) ──
-- GROUP_PUBLISH_REQUESTED(공개 요청 → 모임장 + 「글 공개 처리·되돌리기」 매니저) · GROUP_PUBLISH_APPROVED · GROUP_PUBLISH_REJECTED(→ 작성자)
-- · GROUP_POST_PUBLISHED(남이 직접 공개 → 작성자) · GROUP_VISIBILITY_CHANGED(→ 멤버 전원, 바꾼 모임장 제외) · GROUP_POST_MEMBERS_ONLY · GROUP_POST_RELEASED(→ 작성자).
-- 알림 설정 행은 넣지 않는다(V90 와 같다). 종류 목록은 V93 의 목록 ∪ 위 7종.
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
                    'GROUP_POST_MEMBERS_ONLY', 'GROUP_POST_RELEASED'));
