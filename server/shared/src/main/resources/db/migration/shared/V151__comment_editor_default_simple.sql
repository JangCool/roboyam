-- 댓글 에디터 전체 기본값을 간단 에디터(SIMPLE)로 — docs/features/board-public-contract.md 「에디터」 (사용자 결정 2026-11-05)
--
-- · 댓글도 기본으로 서식 에디터를 쓴다. 평문(Textarea)에서는 고른 로보티콘이 입력칸에 `:emo-ID:` 글자로 보여서, 에디터(그림으로 넣는다)를 기본으로 한다.
-- · board_defaults.comment_editor 를 SIMPLE 로 바꾸고 칸 기본값도 SIMPLE 로. 게시판별 칸(boards.comment_editor)은 NULL = 기본값 따름이라 그대로 두면 함께 바뀐다.
--   게시판에서 따로 NONE 으로 정한 곳은 운영자 선택이므로 건드리지 않는다. 이미 쓴 평문 댓글(TEXT)은 그대로 보인다(에디터 게시판도 TEXT 를 받는다).

UPDATE board_defaults SET comment_editor = 'SIMPLE' WHERE comment_editor = 'NONE';
ALTER TABLE board_defaults ALTER COLUMN comment_editor SET DEFAULT 'SIMPLE';
