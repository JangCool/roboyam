-- 찬반 토론을 하이라이트에서 게시글 기능으로 옮긴다 — docs/features/post-debate-contract.md 「DB」·「서버 구현 결과」
--
-- · post_debates: 행이 있으면 그 글의 토론이 열려 있다(하이라이트·노출과 무관). 닫기 = 행을 지운다 — 이미 고른 입장(post_stances)·쓴 의견(comments.stance)·
--   고정(debate_pins)·고정 요청(debate_pin_requests)은 지우지 않는다(다시 열면 그대로 돌아온다).
--   neutral_enabled: 「중립도 받기」(기본 켬) · stance_change_allowed: 「입장 바꾸기 허용」(기본 켬 — 끄면 이미 고른 입장을 다른 값으로 바꾸면 409 STANCE_CHANGE_LOCKED).
--   opened_by_kind: 누가 열었나 — MEMBER(글쓴이 — 글쓴이가 바꾸고 닫을 수 있다) · MANAGER(그 게시판 매니저 — 공개 API 는 OPERATOR 로 보인다) · OPERATOR(운영자).
--     MANAGER·OPERATOR 가 연 토론은 글쓴이에게 읽기 전용이다. opened_by 는 MEMBER·MANAGER 면 회원 ID, OPERATOR 면 운영자 ID(외래 키 없음 — 지워진 사람도 기록은 남긴다).
--   updated_by_kind·updated_by: 마지막으로 설정을 바꾼 사람(같은 값).
-- · 글 표는 파티션 표라 post_id 외래 키가 없다(글이 지워지면 공개 쪽은 글을 읽지 못해 토론도 보이지 않고, 관리 목록에는 글 상태로 보인다).
CREATE TABLE post_debates (
    post_id               bigint      PRIMARY KEY,
    neutral_enabled       boolean     NOT NULL DEFAULT true,
    stance_change_allowed boolean     NOT NULL DEFAULT true,
    opened_by_kind        varchar(10) NOT NULL,
    opened_by             bigint,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    updated_by_kind       varchar(10),
    updated_by            bigint,
    CONSTRAINT ck_post_debates_opened_by_kind CHECK (opened_by_kind IN ('MEMBER', 'MANAGER', 'OPERATOR')),
    CONSTRAINT ck_post_debates_updated_by_kind CHECK (updated_by_kind IS NULL OR updated_by_kind IN ('MEMBER', 'MANAGER', 'OPERATOR'))
);
-- 관리자 「토론 관리」 목록(최근 연 순)
CREATE INDEX ix_post_debates_created ON post_debates (created_at DESC, post_id DESC);

-- 지금까지의 하이라이트 토론(debate 켬)을 글의 토론으로 옮긴다 — 「중립도 받기」는 그대로, 「입장 바꾸기 허용」은 켬(V79 이전에는 늘 바꿀 수 있었다), 연 사람 = 하이라이트를 만든 운영자.
-- 노출이 끝났거나 꺼진 하이라이트의 토론도 옮긴다(이제 토론은 노출과 무관하게 열려 있다).
INSERT INTO post_debates (post_id, neutral_enabled, stance_change_allowed, opened_by_kind, opened_by, created_at, updated_at, updated_by_kind, updated_by)
SELECT post_id, neutral_enabled, true, 'OPERATOR', created_by, created_at, updated_at, 'OPERATOR', updated_by
  FROM highlights
 WHERE debate;

-- 하이라이트는 토론을 갖지 않는다(V75 debate · V78 neutral_enabled)
ALTER TABLE highlights DROP COLUMN debate;
ALTER TABLE highlights DROP COLUMN neutral_enabled;

-- 게시판 정책 칸 「글쓴이가 찬반 토론을 열 수 있음」(V77·V78 정책 칸과 같은 구조 — boards 는 NULL = 기본값 따름, board_defaults 는 전체 기본값, 기본 끔)
ALTER TABLE boards ADD COLUMN debate_enabled boolean;
ALTER TABLE board_defaults ADD COLUMN debate_enabled boolean NOT NULL DEFAULT false;
