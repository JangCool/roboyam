-- 하이라이트 토론의 중립 입장 + 하이라이트별 「중립도 받기」 + 댓글 비추천 + 의견 고정 요청 + 게시판 정책 칸 4개 — docs/features/popular-highlight-contract.md 「결정」·「서버 구현 결과」, board-contract.md
--
-- · 입장 값이 PRO · CON · NEUTRAL 셋이 된다. NEUTRAL 이 7자라 두 표의 stance 칸을 varchar(3) → varchar(7) 로 넓힌다
--   (varchar 길이 늘리기는 표를 다시 쓰지 않고 색인 ix_comments_opinion 도 그대로 쓴다). CHECK 는 V76 처럼 DROP 뒤 다시 만든다.
-- · comments 는 파티션 표 — 부모에서 바꾸면 조각에도 함께 적용된다.

ALTER TABLE comments DROP CONSTRAINT ck_comments_stance;
ALTER TABLE comments ALTER COLUMN stance TYPE varchar(7);
ALTER TABLE comments ADD CONSTRAINT ck_comments_stance CHECK (stance IS NULL OR stance IN ('PRO', 'CON', 'NEUTRAL'));

ALTER TABLE post_stances DROP CONSTRAINT ck_post_stances_stance;
ALTER TABLE post_stances ALTER COLUMN stance TYPE varchar(7);
ALTER TABLE post_stances ADD CONSTRAINT ck_post_stances_stance CHECK (stance IN ('PRO', 'CON', 'NEUTRAL'));

-- 글별 입장 수(투표 수 — 공개 글 상세·댓글 목록 debate.votes, 관리자 하이라이트 commentCounts.votes)를 세는 색인. PK(post_id, user_id) 앞부분으로도 읽지만
-- stance 로 묶어 세므로 표만 훑지 않게 둔다.
CREATE INDEX ix_post_stances_post_stance ON post_stances (post_id, stance);

-- 하이라이트마다 「중립도 받기」(기본 켬 — 기존 하이라이트도 켬). 끄면 새로 중립을 고를 수 없다(PUT …/stance NEUTRAL → 400 fields.stance).
-- 이미 중립으로 고른 입장·쓴 의견은 그대로 남고 수에도 그대로 든다.
ALTER TABLE highlights ADD COLUMN neutral_enabled boolean NOT NULL DEFAULT true;

-- ── 댓글 비추천 + 게시판 정책 칸 3개(사용자 결정 2026-10-24 — popular-highlight-contract.md 「결정」, board-contract.md 정책 칸) ──────────────
-- · 모든 댓글(일반·의견·답글)에 비추천. 추천과 비추천은 한 회원이 한 댓글에 둘 중 하나만(서비스가 같은 트랜잭션에서 다른 쪽을 푼다). 포인트·알림에는 쓰지 않는다.
-- · comments.dislike_count: 비추천 수(보이는 수는 게시판 정책 commentDislikeEnabled 가 켜졌을 때만 응답에 싣는다 — 꺼도 쌓인 수는 그대로 둔다).
--   상수 기본값 칸 추가라 표를 다시 쓰지 않는다(파티션 부모 → 조각에 함께).
ALTER TABLE comments ADD COLUMN dislike_count integer NOT NULL DEFAULT 0;

-- 비추천 기록 — comment_likes(V46)와 같은 모양(댓글 ID 해시 16 조각, 한 회원이 한 댓글에 한 번). 파티션 표(comments)를 가리키는 외래 키는 두지 않는다.
CREATE TABLE comment_dislikes (
    comment_id bigint      NOT NULL,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id    bigint      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
) PARTITION BY HASH (comment_id);

DO
$$
BEGIN
    FOR i IN 0..15 LOOP
        EXECUTE format('CREATE TABLE comment_dislikes_p%s PARTITION OF comment_dislikes FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
    END LOOP;
END
$$;
-- 글 하나에서 내가 비추천한 댓글(댓글 목록의 disliked)·게시판 내용 지우기
CREATE INDEX ix_comment_dislikes_post_user ON comment_dislikes (post_id, user_id);

-- 게시판 정책 칸(V77 staff_hidden_notice 와 같은 구조 — boards 는 NULL = 기본값 따름, board_defaults 는 전체 기본값)
-- · comment_dislike_enabled: 댓글 비추천 켬(기본 끔 — 끄면 추천만 보이고 비추천 요청은 409)
-- · comment_like_label · comment_dislike_label: 추천·비추천 단추 이름(1~10자). board_defaults 의 NULL = 화면 기본 이름(「추천」·「비추천」)
ALTER TABLE boards ADD COLUMN comment_dislike_enabled boolean;
ALTER TABLE boards ADD COLUMN comment_like_label varchar(10);
ALTER TABLE boards ADD COLUMN comment_dislike_label varchar(10);

ALTER TABLE board_defaults ADD COLUMN comment_dislike_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE board_defaults ADD COLUMN comment_like_label varchar(10);
ALTER TABLE board_defaults ADD COLUMN comment_dislike_label varchar(10);

-- ── 의견 고정 요청(사용자 결정 2026-10-24 — popular-highlight-contract.md 「결정」) ─────────────────────────────────────────────
-- 의견(입장 있는 원댓글)을 쓴 회원이 자기 의견을 맨 위에 고정해 달라고 요청한다. 누가 확인하는지는 게시판 정책 pin_request_reviewers.
--   status: PENDING(대기) · APPROVED(승인 — debate_pins 에 고정됨) · REJECTED(거절 — reject_reason 0~100자). 요청한 회원이 취소하면 행을 지운다.
--   decided_by_kind: MEMBER(글쓴이·게시판 매니저 — decided_by = users.id) · OPERATOR(운영자 — decided_by = operators.id). 의견 하나에 대기 요청은 하나(부분 유일 색인).
--   글·댓글은 파티션 표라 외래 키를 두지 않는다.
CREATE TABLE debate_pin_requests (
    id              bigint       PRIMARY KEY,
    comment_id      bigint       NOT NULL,
    post_id         bigint       NOT NULL,
    requester_id    bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status          varchar(10)  NOT NULL,
    reject_reason   varchar(100),
    created_at      timestamptz  NOT NULL,
    decided_at      timestamptz,
    decided_by_kind varchar(10),
    decided_by      bigint,
    CONSTRAINT ck_debate_pin_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_debate_pin_requests_decider CHECK (decided_by_kind IS NULL OR decided_by_kind IN ('MEMBER', 'OPERATOR'))
);
CREATE UNIQUE INDEX ux_debate_pin_requests_pending ON debate_pin_requests (comment_id) WHERE status = 'PENDING';
CREATE INDEX ix_debate_pin_requests_post ON debate_pin_requests (post_id, status, id);

-- 게시판 정책 칸 「고정 요청을 확인하는 사람」 — POST_AUTHOR · BOARD_MANAGER · OPERATOR 를 쉼표로 이은 값(빈 글자 = 고정 요청 끔).
-- boards 는 NULL = 기본값 따름, board_defaults 기본 'OPERATOR'.
ALTER TABLE boards ADD COLUMN pin_request_reviewers varchar(64);
ALTER TABLE board_defaults ADD COLUMN pin_request_reviewers varchar(64) NOT NULL DEFAULT 'OPERATOR';

-- 회원 알림 종류에 OPINION_PIN_REQUESTED(내가 확인할 고정 요청이 왔다)·OPINION_PIN_REVIEWED(내 고정 요청이 승인·거절됐다)를 더한다(MemberNotificationType 과 같은 값).
-- ⚠ 합칠 때 합집합 확인 — 다른 세션(커머스 등)도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED'));
