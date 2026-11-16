-- 글 출처 항목(2026-11-16 — ai-news-reporter-contract 「본문 꼬리」) — 뉴스 글의 「출처: 이름 — 주소」를 본문이 아니라 따로 둔다. 출처가 여러 곳일 수 있다.
--
-- 1) 게시판 정책 칸 「출처 칸 쓰기」(V206 author_thumbnail_enabled 와 같은 구조 — boards 는 NULL = 사이트 기본, board_defaults 는 사이트 기본, 기본 끔).
--    켠 게시판만 글쓰기·고치기의 sources 를 받는다(꺼져 있으면 무시 — 새 글은 빈 값). 나중에 꺼도 이미 단 출처는 그대로 보인다.
ALTER TABLE boards ADD COLUMN sources_enabled boolean;
ALTER TABLE board_defaults ADD COLUMN sources_enabled boolean NOT NULL DEFAULT false;

-- 2) posts.sources: 글의 출처 목록 JSON 배열 [{"name":"전자신문","url":"https://…"}] — NULL = 없음. 규칙(서버 PostSources): 최대 5개 · 이름 0~40자(비면 화면이 도메인) ·
--    주소 필수 · http(s) 만 · 500자 · 같은 주소는 하나. posts 는 파티션 표 — 부모에 더하면 모든 파티션에 생긴다(상수 기본값 없는 NULL 칸이라 표를 다시 쓰지 않는다).
ALTER TABLE posts ADD COLUMN sources jsonb;

-- 3) ai_member_drafts.sources: AI 기자 뉴스 글 생성 줄의 출처(같은 모양) — 검수 대기함에서 승인·바로 게시할 때 글의 sources 로 싣는다.
ALTER TABLE ai_member_drafts ADD COLUMN sources jsonb;

COMMENT ON COLUMN boards.sources_enabled IS '출처 칸 쓰기(NULL = 사이트 기본) — V232';
COMMENT ON COLUMN posts.sources IS '글 출처 목록 [{name,url}] — V232';
COMMENT ON COLUMN ai_member_drafts.sources IS 'AI 뉴스 글 출처 목록 [{name,url}] — V232';
