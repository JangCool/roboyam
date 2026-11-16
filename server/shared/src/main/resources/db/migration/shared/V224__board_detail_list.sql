-- 글 상세 아래 게시판 목록(docs/features/post-detail-board-list-contract.md) — 게시판마다의 값, 사이트 기본(board_defaults) 칸은 두지 않는다.
--   · OFF     글 상세 아래 목록을 보이지 않는다
--   · LIST    게시판 목록과 같은 모양(기본 — 휴대폰도 모바일 게시판 목록 모양)
--   · COMPACT 휴대폰은 촘촘한 한 줄(제목·댓글 수·날짜), PC 는 게시판 목록 그대로
-- 소모임 게시판도 같은 칸을 쓴다. 상수 기본값이 있는 칸 더하기는 표를 다시 쓰지 않고, 표가 작아(게시판 수) CHECK 는 바로 건다.
ALTER TABLE boards
    ADD COLUMN detail_list varchar(8) NOT NULL DEFAULT 'LIST',
    ADD CONSTRAINT ck_boards_detail_list CHECK (detail_list IN ('OFF', 'LIST', 'COMPACT'));

COMMENT ON COLUMN boards.detail_list IS '글 상세 아래 게시판 목록 OFF · LIST(기본) · COMPACT — V224';
