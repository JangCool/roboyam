-- 익명 게시판(봉인 열람) — docs/features/anonymous-board-contract.md(화면 확정 2026-11-10 · 정책 1~11).
-- ⚠ 가번호 V184 — develop 최신 V183(2026-11-10). 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- · boards.author_mode: 작성자 표시 NICKNAME(기본) · ANONYMOUS. 게시판마다의 값(board_defaults 에서 물려받지 않는다 — secret_mode(V166)와 같다).
--   일반 게시판(kind = 'GENERAL')만 ANONYMOUS 를 고를 수 있다(앱 — 관리자 저장이 막고, 그 밖 종류는 읽을 때도 NICKNAME 으로 본다).
-- · posts.anonymous · comments.anonymous: 쓸 때의 게시판 설정(정책 2) — 게시판을 나중에 되돌려도 이미 쓴 글·댓글의 값은 그대로다. author_id 는 그대로 남는다(봉인).
--   댓글은 「글이 익명이거나 지금 게시판이 익명이면」 익명으로 쓴다(앱 — 익명 글의 글쓴이가 닉네임 댓글로 드러나지 않게).
-- · post_anonymous_numbers: 한 글에서 처음 댓글을 다는 회원 차례대로 익명1, 익명2 …(정책 3). 글·회원 하나에 번호 하나, 글 안에서 번호는 겹치지 않는다.
--   번호는 댓글 저장 트랜잭션 안에서 글 단위 잠금(pg_advisory_xact_lock — PostCounterQueries#lockWriter) 아래 최대값 + 1 로 매긴다. 글쓴이는 번호를 받지 않는다.
--   글 표는 id 월 범위 파티션이라 post_id 에 FK 를 걸지 않는다(board_sanctions.post_id 와 같다). 회원이 지워지면 줄도 지운다(번호는 다시 쓰지 않는다 — 최대값 + 1).
-- · member_sanctions.sealed · board_sanctions.sealed: 「이 글/댓글 작성자」로 건 제재(정책 9) — 제재 목록·결과·감사에서 대상이 봉인(닉네임·회원 ID 없음)으로 보인다.
-- 파티션 표에 상수 기본값이 있는 칸을 더하는 것은 「빠른 기본값」이라 표를 다시 쓰지 않는다(V56 · V166 과 같다).

-- ── 게시판 설정 ──────────────────────────────────────────────────────────────
ALTER TABLE boards ADD COLUMN author_mode varchar(10) NOT NULL DEFAULT 'NICKNAME';
ALTER TABLE boards ADD CONSTRAINT ck_boards_author_mode CHECK (author_mode IN ('NICKNAME', 'ANONYMOUS'));

-- ── 글 · 댓글 ────────────────────────────────────────────────────────────────
ALTER TABLE posts ADD COLUMN anonymous boolean NOT NULL DEFAULT false;
ALTER TABLE comments ADD COLUMN anonymous boolean NOT NULL DEFAULT false;

-- ── 글 안 익명 번호 ──────────────────────────────────────────────────────────
CREATE TABLE post_anonymous_numbers (
    post_id    bigint      NOT NULL,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    number     integer     NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_post_anonymous_numbers PRIMARY KEY (post_id, user_id),
    CONSTRAINT ux_post_anonymous_numbers_number UNIQUE (post_id, number),
    CONSTRAINT ck_post_anonymous_numbers_number CHECK (number >= 1)
);

-- ── 「이 글 작성자」 제재(봉인) ──────────────────────────────────────────────
ALTER TABLE member_sanctions ADD COLUMN sealed boolean NOT NULL DEFAULT false;
ALTER TABLE board_sanctions ADD COLUMN sealed boolean NOT NULL DEFAULT false;
