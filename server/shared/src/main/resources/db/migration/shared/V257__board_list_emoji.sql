-- 게시판 목록 이모지(docs/features/mobile-home-list-contract.md 「게시판 목록 이모지」, 사용자 확정 2026-11-19)
--
-- 사진 없는 글의 썸네일 자리(모바일 홈 목록형 · 최신글·하이라이트·갤러리 줄)에 게시판 이모지를 보인다. 비우면(NULL) 화면이 게시판 이름 첫 글자를 쓴다.
-- 관리자 게시판 기본 정보의 「목록 이모지」(선택, 이모지 한 개 — 최대 16자) — 공개 글 줄의 게시판 참조 board.emoji 로 나간다.
ALTER TABLE boards ADD COLUMN list_emoji varchar(16);

COMMENT ON COLUMN boards.list_emoji IS '목록 이모지(이모지 한 개, NULL = 게시판 이름 첫 글자) — 사진 없는 글의 썸네일 자리';

-- 지금 있는 게시판 slug 에 기본 이모지 — 그 slug 가 있는 행만 바뀐다(없는 slug 는 건너뛴다)
UPDATE boards b
   SET list_emoji = v.emoji
  FROM (VALUES ('ai', '🤖'),
               ('stock', '📈'),
               ('game', '🎮'),
               ('sports', '⚽'),
               ('video', '🎬'),
               ('free', '💬'),
               ('notice', '📢'),
               ('humor', '😂'),
               ('hotdeal', '🛍️'),
               ('issue', '🌏'),
               ('life', '🏠'),
               ('entertainment', '📺'),
               ('car', '🚗'),
               ('pet', '🐶'),
               ('love', '💕'),
               ('job', '💼'),
               ('toon', '📚'),
               ('tip', '💡'),
               ('review', '⭐'),
               ('market', '🏷️'),
               ('bugs', '🐞'),
               ('requests', '🙋'),
               ('anonymous', '🎭'),
               ('news', '📰'),
               ('qa-test', '🧪')) AS v(slug, emoji)
 WHERE b.slug = v.slug;
