-- AI 기자 유튜브 영상 글(2026-11-24 — ai-news-reporter-contract 「유튜브 영상 글」): 유튜브 채널 출처(YOUTUBE)의 수집 항목을 소재로 쓴 글은 영상·짤 게시판에
-- 영상 플레이어(본문의 YouTube 표식 <div data-youtube-id="11자">)를 붙여 올린다. 생성 줄에는 본문을 평문 그대로 두고(검수 대기함이 글자로 보여 준다) 영상 ID 만 따로 둔다 —
-- 등록할 때 플레이어 + 본문 문단으로 HTML 글을 만든다. 수집 항목은 7일 뒤 지워지므로 링크 대신 ID 를 생성 줄에 남긴다. NULL = 영상 없음(지금까지의 모든 줄).
ALTER TABLE ai_member_drafts ADD COLUMN youtube_video_id varchar(11);
ALTER TABLE ai_member_drafts ADD CONSTRAINT ck_ai_member_drafts_youtube_video_id CHECK (youtube_video_id IS NULL OR youtube_video_id ~ '^[A-Za-z0-9_-]{11}$');

COMMENT ON COLUMN ai_member_drafts.youtube_video_id IS 'AI 기자 유튜브 영상 글의 영상 ID(11자) — 등록 때 본문 위에 YouTube 표식으로 붙인다, NULL = 영상 없음 — V268';
