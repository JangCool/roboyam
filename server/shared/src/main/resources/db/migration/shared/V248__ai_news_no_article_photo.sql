-- AI 기자 뉴스 글에 원문 기사 사진을 넣지 않는다(사용자 2026-11-18 「기사 사진은 퍼가지 않도록 해」 — 기사 사진은 별도 저작물).
-- V247 이 만든 생성 줄의 원문 대표 이미지 주소 칸을 지운다. 원문 본문 발췌(요약 자료)·기사체 지시는 그대로다. 글 썸네일은 사이트 기본 뉴스 썸네일.
ALTER TABLE ai_member_drafts DROP CONSTRAINT IF EXISTS ck_ai_member_drafts_news_image_url;
ALTER TABLE ai_member_drafts DROP COLUMN IF EXISTS news_image_url;
