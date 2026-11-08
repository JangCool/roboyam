-- 전체 공지(사용자 확정 2026-11-08) — 공지 게시판(slug notice) 고정글을 다른 게시판 글 목록 맨 위에도 건다. docs/features/notice-admin-contract.md 「전체 공지」.
-- 공지 글 하나에 한 줄(post_id PK). 글 표는 파티션 표라 외래 키가 없다(post_deals 와 같은 방식).
-- pinned_at = 전체 공지로 걸 때의 글 고정 시각(posts.pinned_at 사본). 공개 쪽은 글의 고정 시각이 이 값과 같을 때만 보인다 —
--   공지 관리 밖(게시글 관리 고정 해제·이동·삭제 등)에서 고정이 풀리거나 다시 고정되면 이 줄은 저절로 힘을 잃는다(다시 걸려면 공지 관리에서 저장).
-- excluded_board_ids = 뺄 게시판(일반·소모임 게시판 ID). exclude_group_boards = 소모임 게시판 전체를 뺀다(앞으로 만들 소모임 게시판도).
-- expires_at = 다른 게시판에서 내려갈 시각(NULL = 풀 때까지). 지나면 읽을 때 거른다(배치 없음) — 공지 게시판의 고정은 그대로.
-- 동시에 최대 3개(살아 있고 기간 안인 것)는 앱이 검사한다(공지 게시판 행 잠금 아래).
CREATE TABLE notice_global_pins (
    post_id              bigint      PRIMARY KEY,
    pinned_at            timestamptz NOT NULL,
    excluded_board_ids   bigint[]    NOT NULL DEFAULT '{}',
    exclude_group_boards boolean     NOT NULL DEFAULT false,
    expires_at           timestamptz,
    created_at           timestamptz NOT NULL,
    created_by           bigint,
    updated_at           timestamptz NOT NULL,
    updated_by           bigint
);
