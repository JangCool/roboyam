-- 소모임 분류 — docs/features/group-contract.md 「관리자 화면」(community docs/api-groups.md 6-3 을 로보얌에 맞춘 것).
-- 분류(이름·순서) 1 : 소모임 게시판(boards.kind = 'GROUP') N. 소모임의 분류는 그 게시판이 할당된 분류로 정해지는 파생값이다(소모임 표는 소모임 서버 단계).
-- 관리자 /api/admin/group-categories 가 만들고 고치며, 게시판 만들기(POST /api/admin/boards, kind GROUP + groupCategoryId)가 분류 끝에 할당한다.
-- 번호 87 은 가번호다(85·86 은 커머스 1단계가 예약) — develop 에 합칠 때 마지막 번호를 다시 본다.

-- ── 분류 ────────────────────────────────────────────────────────────────────
-- id: Snowflake(앱 발급 — 다른 표와 같다). name: 앞뒤 공백을 다듬고 안쪽 공백을 하나로 줄인 이름(1~20자, < > 줄바꿈·제어 문자 없음 — 앱이 검사).
-- name_key: 같은 이름 막기 — 이름에서 공백을 모두 빼고 소문자로 바꾼 값(앱이 만든다). sort_order: 10 단위(작을수록 앞), 앱이 다시 매긴다.
-- updated_at: 이름이 바뀌거나 게시판이 더해지거나 빠질 때(다른 분류로 옮겨져 빠질 때도) 앱이 갱신한다 — 「다른 관리자가 먼저 고쳤어요」(409 GROUP_CATEGORY_STALE) 비교 기준.
CREATE TABLE group_categories (
    id          bigint       PRIMARY KEY,
    name        varchar(20)  NOT NULL,
    name_key    varchar(40)  NOT NULL,
    sort_order  integer      NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_group_categories_name_key UNIQUE (name_key),
    CONSTRAINT ck_group_categories_name CHECK (char_length(name) BETWEEN 1 AND 20 AND name = btrim(name)),
    CONSTRAINT ck_group_categories_name_key CHECK (char_length(name_key) BETWEEN 1 AND 40)
);
CREATE INDEX ix_group_categories_order ON group_categories (sort_order, id);

-- ── 분류에 할당한 게시판 ──────────────────────────────────────────────────────
-- 게시판 하나는 분류 최대 하나(board_id 가 기본 키). 행이 있으면 늘 분류가 있다(category_id NOT NULL) — 분류에서 빼는 일은 행을 지운다.
-- assigned_order: 분류 안의 할당 순서(1부터, 같으면 게시판 ID 순). 소모임 게시판(kind GROUP)만 넣는다(앱이 검사 — 종류는 만든 뒤 바뀌지 않는다).
-- 게시판을 지우면 할당도 지워지고, 분류를 지우면 그 분류의 게시판은 「분류 없음」이 된다(행이 지워진다).
CREATE TABLE group_category_boards (
    board_id        bigint   PRIMARY KEY REFERENCES boards (id) ON DELETE CASCADE,
    category_id     bigint   NOT NULL REFERENCES group_categories (id) ON DELETE CASCADE,
    assigned_order  integer  NOT NULL
);
CREATE INDEX ix_group_category_boards_category ON group_category_boards (category_id, assigned_order, board_id);
