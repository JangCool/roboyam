-- 하이라이트 토론(찬성·반대 의견) — docs/features/popular-highlight-contract.md 「서버 구현 결과」(community docs/api-debate.md 를 로보얌에 맞춘 것).
-- 노출 중인 하이라이트(V75)의 debate 가 켜진 글에서만 토론이 열린다. 의견 = 입장 배지가 붙은 최상위 댓글(답글에도 배지는 붙지만 의견 수로 세지 않는다).

-- 쓸 때의 입장 배지 — PRO(찬성) · CON(반대) · NULL(일반 댓글). 회원이 나중에 입장을 바꿔도 이미 쓴 댓글은 그대로다.
ALTER TABLE comments ADD COLUMN stance varchar(3);
ALTER TABLE comments ADD CONSTRAINT ck_comments_stance CHECK (stance IS NULL OR stance IN ('PRO', 'CON'));

-- 운영자가 숨긴 의견(관리자 「의견 숨기기」)은 신고 숨김과 같은 칸(hidden_at)을 쓰고 까닭을 STAFF 로 둔다 — 목록에는 신고 숨김처럼 자리(HIDDEN)만 남고
-- 글의 보이는 댓글 수에서 빠진다. 숨김 해제는 까닭이 STAFF 인 숨김만 푼다(신고 숨김은 풀지 않는다).
ALTER TABLE comments DROP CONSTRAINT ck_comments_hidden_reason;
ALTER TABLE comments ADD CONSTRAINT ck_comments_hidden_reason CHECK (hidden_reason IS NULL OR hidden_reason IN ('REPORTED', 'ILLEGAL', 'RIGHTS', 'STAFF'));

-- 글 하나의 의견만 읽는 작은 색인(관리자 의견 목록·구분별 수)
CREATE INDEX ix_comments_opinion ON comments (post_id, stance, id) WHERE parent_id IS NULL AND stance IS NOT NULL;

-- 회원의 글별 현재 입장 — 찬성·반대로 처음 의견을 쓸 때 생기고 PUT /api/posts/{id}/stance 로만 바뀐다. 댓글을 지워도 남는다.
-- 글 표는 파티션 표라 post_id 외래 키가 없다.
CREATE TABLE post_stances (
    post_id    bigint      NOT NULL,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    stance     varchar(3)  NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT ck_post_stances_stance CHECK (stance IN ('PRO', 'CON'))
);

-- 운영자가 맨 앞에 고정한 의견(관리자 의견 목록의 순서 · 나중에 홈 카드) — 댓글마다 한 줄
CREATE TABLE debate_pins (
    comment_id bigint      PRIMARY KEY,
    post_id    bigint      NOT NULL,
    pinned_by  bigint      REFERENCES operators (id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX ix_debate_pins_post ON debate_pins (post_id);
