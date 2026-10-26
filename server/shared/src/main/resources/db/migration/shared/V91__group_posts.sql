-- 소모임 2단계(소모임 글) — docs/features/group-contract.md 「전체 개발 계획」 2단계(community docs/api-groups.md 8절을 로보얌에 맞춘 것).
-- 소모임 글은 posts 표의 글이다(게시판별 표를 나누지 않는다). 글의 그릇은 소모임 게시판(groups.board_id, boards.kind = 'GROUP')이고 이 칸들이 소모임과 공개 여부를 말한다.
-- 번호는 develop 병합(2026-10-26, 커머스 1-3 의 V89 다음) 때 V90~V93 으로 다시 매겼다.
--
-- · group_id: 소모임 글이면 그 소모임(앱이 쓸 때 정한다 — 바뀌지 않는다). 해산해도 공개로 남는 글은 연결을 유지한다(화면이 「해산한 소모임」으로 표기 — community V80 과 같다).
--   글 표는 id 월 범위 파티션이라 외래 키를 걸지 않는다(board_id 와 같다). 소모임 행은 지우지 않고 status 로만 닫는다.
-- · is_public: 공개로 전환됐는가(원본 칸 이름). 쓰자마자는 false(멤버만 본다). 작성자가 켜고, 작성자·모임장·되돌리기를 맡은 매니저가 끈다. 일반 글은 늘 false.
-- · group_left_at: 작성자가 소모임을 스스로 나가 가려진 시각(로보얌 — community 는 hidden_at + hidden_reason = 'GROUP_LEFT').
--   운영자 숨김(hidden_*)과 칸을 나눠 서로를 덮어쓰지 않는다: 다시 가입하면 이 칸만 비우고(운영자가 숨긴 글은 숨긴 채), 운영자의 숨김 풀기는 이 칸을 건드리지 않는다.
--   가려진 글은 어느 목록에도 안 나오고 주소는 404 다. 공개 여부(is_public)는 그대로 둔다.
-- 칸 추가는 파티션 부모에만 하면 모든 조각에 같이 붙는다(V46 과 같은 방식). 기존 행은 모두 일반 글이다(소모임 게시판 글쓰기는 지금까지 막혀 있었다).

ALTER TABLE posts
    ADD COLUMN group_id      bigint,
    ADD COLUMN is_public     boolean     NOT NULL DEFAULT false,
    ADD COLUMN group_left_at timestamptz;

-- 일반 글은 공개 전환·나가서 가림이 없다(새 칸이라 기존 행은 모두 맞는다 — 큰 표를 다시 훑지 않게 NOT VALID, V56 과 같다)
ALTER TABLE posts ADD CONSTRAINT ck_posts_group_flags CHECK (group_id IS NOT NULL OR (NOT is_public AND group_left_at IS NULL)) NOT VALID;

-- 글 탭(최신이 위, 커서) · 소모임별 글 수 · 새 글 수 · 나가기·다시 가입·해산의 일괄 처리(소모임 + 작성자)
CREATE INDEX ix_posts_group ON posts (group_id, id DESC) WHERE group_id IS NOT NULL;
CREATE INDEX ix_posts_group_author ON posts (group_id, author_id) WHERE group_id IS NOT NULL;

-- ── 회원 알림 종류: 소모임 글 비공개 되돌림(모임장·되돌리기를 맡은 매니저가 내 공개 글을 비공개로 돌렸다) ──
-- 알림 설정 행은 넣지 않는다(V90 와 같다). 종류 목록은 V90 의 목록 ∪ GROUP_POST_UNPUBLISHED.
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED',
                    'GROUP_POST_UNPUBLISHED'));
