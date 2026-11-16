-- 게시판 옵션 세 묶음(2026-11-16)
--
-- 1) 글 상세 아래 [인기글]·[최신글] 탭(post-detail-board-list-contract 「2차」) — PC·모바일 각각 켜고 끈다. 정책 칸(V206 author_thumbnail_enabled 와 같은 구조):
--    boards 는 NULL = 사이트 기본(board_defaults) 따름, board_defaults 는 사이트 기본(PC 끔 · 모바일 켬).
ALTER TABLE boards
    ADD COLUMN detail_feed_tabs_pc boolean,
    ADD COLUMN detail_feed_tabs_mobile boolean;
ALTER TABLE board_defaults
    ADD COLUMN detail_feed_tabs_pc boolean NOT NULL DEFAULT false,
    ADD COLUMN detail_feed_tabs_mobile boolean NOT NULL DEFAULT true;

-- 2) 뉴스 게시판 글 규칙(ai-news-reporter-contract 「뉴스 게시판 글 규칙」 — 회원·AI 공통) — 정책 칸, 사이트 기본 끔.
--    prefix_required: 켠 게시판(켜진 말머리가 하나라도 있을 때)에 말머리 없이 쓰면 400 fields.prefix.
--    tag_required: 켠 게시판(「태그 쓰기」가 켜졌을 때)에 태그 0개로 쓰면 400 fields.tags.
ALTER TABLE boards
    ADD COLUMN prefix_required boolean,
    ADD COLUMN tag_required boolean;
ALTER TABLE board_defaults
    ADD COLUMN prefix_required boolean NOT NULL DEFAULT false,
    ADD COLUMN tag_required boolean NOT NULL DEFAULT false;

-- 3) 성인 전용 게시판(adult-board-contract) — 게시판마다의 값(사이트 기본 없음). 켜면 본인인증한 성인만 게시판·글에 닿고(그 밖은 없는 게시판과 같은 404),
--    메뉴·게시판 목록에서도 성인이 아니면 빠진다. 그 게시판 글은 모두 19금(posts.adult)으로 저장하고 19금 허용(allow_adult)도 켠 것으로 본다.
--    일반 게시판만 켤 수 있다(핫딜·직접홍보·소모임은 관리자 저장이 거절). 켜고 저장하면 그 게시판의 기존 글도 19금이 된다(관리자 저장 — 감사 adultMarkedPosts).
ALTER TABLE boards
    ADD COLUMN adult_only boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN boards.detail_feed_tabs_pc IS '글 상세 아래 인기글·최신글 탭 PC(NULL = 사이트 기본) — V226';
COMMENT ON COLUMN boards.detail_feed_tabs_mobile IS '글 상세 아래 인기글·최신글 탭 모바일(NULL = 사이트 기본) — V226';
COMMENT ON COLUMN boards.prefix_required IS '말머리 필수(NULL = 사이트 기본) — V226';
COMMENT ON COLUMN boards.tag_required IS '태그 필수(NULL = 사이트 기본) — V226';
COMMENT ON COLUMN boards.adult_only IS '성인 전용 게시판(본인인증 성인만) — V226';
