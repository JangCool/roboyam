-- 회원 차단·차단 단어·이용자 메모(공개 사이트 개인 설정) — docs/features/member-social-contract.md 「차단·메모」. 상한은 앱 상수(차단 500명 · 단어 50개 2~20자 · 메모 500개 20자).
--
-- · user_blocks: 내가(user_id) 차단한 회원(target_id). 상대에게 알리지 않는다. 쪽지·받는 사람 찾기는 어느 쪽이 차단했든 막는다(target_id 색인).
--   ID 는 Snowflake(만든 시각 순)라 「차단한 시각이 최근인 것이 위」 = id 내림차순이고 목록 커서도 id 다.
-- · blocked_words: 내 차단 단어 — 글 제목·댓글 본문에 들어 있으면(대소문자 무시·부분 일치) 화면이 접는다. 같은 단어는 한 번만(대소문자 무시 — 앱이 먼저 보고 색인이 최종 방어).
-- · user_memos: 내가 상대 회원에게 남긴 메모 — 나에게만 보인다. 「처음 남긴 것이 최근인 순서」 = id 내림차순(고쳐도 자리가 바뀌지 않는다).
-- 회원 행이 지워지면(users 행 삭제) 같이 지운다. 탈퇴한 회원은 목록·개수에서 앱이 뺀다(행은 남는다).
CREATE TABLE user_blocks (
    id         bigint      PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_id  bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    CONSTRAINT ux_user_blocks_pair UNIQUE (user_id, target_id),
    CONSTRAINT ck_user_blocks_self CHECK (user_id <> target_id)
);
CREATE INDEX ix_user_blocks_user ON user_blocks (user_id, id DESC);
CREATE INDEX ix_user_blocks_target ON user_blocks (target_id);

CREATE TABLE blocked_words (
    id         bigint      PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    word       varchar(20) NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX ix_blocked_words_user ON blocked_words (user_id, id DESC);
CREATE UNIQUE INDEX ux_blocked_words_user_word ON blocked_words (user_id, lower(word));

CREATE TABLE user_memos (
    id         bigint      PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_id  bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    memo       varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ux_user_memos_pair UNIQUE (user_id, target_id),
    CONSTRAINT ck_user_memos_self CHECK (user_id <> target_id)
);
CREATE INDEX ix_user_memos_user ON user_memos (user_id, id DESC);
CREATE INDEX ix_user_memos_target ON user_memos (target_id);
