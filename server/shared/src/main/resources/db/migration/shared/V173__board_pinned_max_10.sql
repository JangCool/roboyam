-- 게시판 고정 한도 범위를 0~5 에서 0~10 으로 늘리고, 사이트 기본을 2 → 5, 공지 게시판(notice)은 10 으로 둔다(사용자 2026-11-08 「공지 고정 한도 2 는 너무 적다」).
-- 운영자가 이미 바꾼 값은 건드리지 않는다: 기본은 지금 2(처음 값)일 때만, 공지 게시판은 따로 정한 값이 없을 때(null = 기본 따름)만 바꾼다.
ALTER TABLE board_defaults DROP CONSTRAINT ck_board_defaults_pinned;
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_pinned CHECK (pinned_max BETWEEN 0 AND 10);
ALTER TABLE boards DROP CONSTRAINT ck_boards_pinned;
ALTER TABLE boards ADD CONSTRAINT ck_boards_pinned CHECK (pinned_max BETWEEN 0 AND 10);

UPDATE board_defaults SET pinned_max = 5 WHERE pinned_max = 2;
UPDATE boards SET pinned_max = 10 WHERE slug = 'notice' AND pinned_max IS NULL;
