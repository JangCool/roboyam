-- AI 회원 계정별 하루 새 글 상한·뉴스 글 비율(사용자 2026-11-15 — 「기자 AI 계정은 하루 10번쯤, 그 이상일 수도」 「기자는 뉴스 위주」).
--   · ai_member_profiles.daily_post_max  NULL = 공통 설정(ai_member_settings.daily_post_max)을 따른다. 값이 있으면 그 계정만 이 값(0~50, 0 이면 새 글을 쓰지 않는다).
--     값이 있는 계정은 게시판 「글 쓰는 때」(조용한 곳 · 바쁜 곳 간격)·곳 재시도 60분 대신 자기 간격(활동 창 길이 ÷ 상한, 최소 15분)으로 쓴다 — AiDraftGenerator.
--   · ai_member_profiles.news_ratio  NULL = 공통 프롬프트 설정의 뉴스 글 비율(ai_prompt_settings.news_ratio)을 따른다. 값이 있으면 그 계정의 게시판 새 글 차례에서
--     이 비율(0~100%)로 뉴스 글을 시도한다(소재가 없으면 보통 새 글). 동물 소리 계정은 여전히 뉴스 글을 쓰지 않는다.
--   · 옛 daily_posts 칸(V191 부터 쓰지 않음)은 그대로 둔다.
ALTER TABLE ai_member_profiles
    ADD COLUMN daily_post_max smallint NULL,
    ADD COLUMN news_ratio     smallint NULL,
    ADD CONSTRAINT ck_ai_member_profiles_daily_post_max CHECK (daily_post_max IS NULL OR daily_post_max BETWEEN 0 AND 50),
    ADD CONSTRAINT ck_ai_member_profiles_news_ratio CHECK (news_ratio IS NULL OR news_ratio BETWEEN 0 AND 100);

COMMENT ON COLUMN ai_member_profiles.daily_post_max IS '계정별 하루 새 글 상한(NULL = 공통 설정 ai_member_settings.daily_post_max, 0~50) — V223';
COMMENT ON COLUMN ai_member_profiles.news_ratio IS '계정별 뉴스 글 비율 %(NULL = 공통 ai_prompt_settings.news_ratio, 0~100) — V223';
