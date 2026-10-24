-- 게시글 투표(설문) — docs/features/post-poll-contract.md 「DB」·「서버 구현 결과」
--
-- · polls: 글 하나에 투표 1개(post_id 유일). 글 표는 파티션 표라 post_id 외래 키가 없다(글이 지워지면 공개 쪽은 글을 읽지 못해 투표도 보이지 않는다).
--   multiple: 복수 선택 · change_allowed: 「다시 투표 허용」(기본 끔 — 끄면 한 번 투표하면 바꿀 수 없다, 켜면 마감 전까지 다른 선택으로 다시 투표) ·
--   closes_at: 마감 시각(NULL = 마감 없음, 지나면 그 자리에서 마감 — 배치 없음).
-- · poll_options: 선택지 2~6개(position 0부터 차례). 투표를 고치면(첫 표 전에만) 선택지를 모두 지우고 다시 넣는다.
-- · poll_votes: 고른 선택지(복수 선택이면 한 사람이 여러 줄). 득표·참여 수는 이 표에서 센다(사본 칸 없음 — 투표 하나의 줄 수가 작다).
--   익명: 누가 무엇을 골랐는지는 어떤 API 에도 내보내지 않는다(관리자 포함). 다시 투표 = 그 사람의 줄을 지우고 새로 넣는다(취소는 없다).
--   첫 표가 들어온 투표는 고치거나 뺄 수 없다(409 POLL_LOCKED) — 고치는 쪽 polls 행 FOR UPDATE, 투표하는 쪽 FOR SHARE 로 어긋나지 않게 한다.
CREATE TABLE polls (
    id             bigint       PRIMARY KEY,
    post_id        bigint       NOT NULL,
    question       varchar(100) NOT NULL,
    multiple       boolean      NOT NULL DEFAULT false,
    change_allowed boolean      NOT NULL DEFAULT false,
    closes_at      timestamptz,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT uq_polls_post UNIQUE (post_id)
);

CREATE TABLE poll_options (
    id       bigint      PRIMARY KEY,
    poll_id  bigint      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    position smallint    NOT NULL,
    label    varchar(50) NOT NULL,
    CONSTRAINT uq_poll_options_position UNIQUE (poll_id, position)
);

CREATE TABLE poll_votes (
    poll_id    bigint      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    option_id  bigint      NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (poll_id, user_id, option_id)
);
-- 선택지별 득표(결과)
CREATE INDEX ix_poll_votes_option ON poll_votes (poll_id, option_id);

-- 게시판 정책 칸 「글쓴이가 투표를 붙일 수 있음」(V79 debate_enabled 와 같은 구조 — boards 는 NULL = 기본값 따름, board_defaults 는 전체 기본값, 기본 끔)
ALTER TABLE boards ADD COLUMN poll_enabled boolean;
ALTER TABLE board_defaults ADD COLUMN poll_enabled boolean NOT NULL DEFAULT false;

-- 글 조치 기록에 POLL_DELETED(운영자가 글의 투표를 지움 — 관리자 DELETE /api/admin/posts/{id}/poll)를 더한다(V80 CHECK 를 다시 만든다)
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE post_moderation_events DROP CONSTRAINT ck_post_moderation_events_type;
ALTER TABLE post_moderation_events ADD CONSTRAINT ck_post_moderation_events_type CHECK (type IN ('HIDDEN', 'UNHIDDEN', 'DELETED', 'RESTORED', 'MOVED', 'NOTICE',
                                                                                             'PINNED', 'UNPINNED', 'REPORT_HIDDEN', 'POLL_DELETED'));
