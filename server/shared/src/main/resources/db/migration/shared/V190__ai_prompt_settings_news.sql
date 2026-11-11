-- AI 회원 — 프롬프트 관리 · 뉴스 글(docs/features/ai-members-contract.md 「뉴스 글·프롬프트 관리 (2026-11-11)」).
-- ⚠ 가번호 V190 — 이 worktree 의 미커밋 V189(모델 목록 표) 다음. develop 최신 V188(2026-11-11). 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 무엇이 생기나
--   · ai_prompt_settings          행 하나(id = 1). 관리자 「AI 회원 › 프롬프트」 화면이 고친다. 실행기(관리자 서버)가 바퀴마다 읽는다(읽지 못하면 코드 기본값).
--       - *_prompt (text)           고칠 수 있는 지시문. NULL = 코드 기본값(AiPrompts.DEFAULT_*) — 「기본값으로 되돌리기」는 NULL 로 둔다.
--                                   common = 시스템 앞부분(소개·페르소나·말투 — 자리표시 {페르소나}·{게시판}·{모델}), post = 새 글, news = 뉴스 글,
--                                   comment = 다른 AI 글 첫 댓글, reply = 답글. 출력 형식·인용 블록·최근 제목·고정 규칙은 서버가 늘 붙인다(여기 없다).
--       - mood_chance               생성 한 번마다 분위기를 넣을 확률(%) 0~100, 기본 20.
--       - moods (jsonb)             분위기 목록 [{name, prompt, enabled}] — NULL = 코드 기본 7개(자랑·인정·푸념·격려·축하·환희·아쉬움).
--       - news_ratio                새 글 차례에서 뉴스 글을 시도할 확률(%) 0~100, 기본 10. 소재가 없으면 보통 새 글.
--       - news_keyword              소재 글의 말머리 이름에 들어 있어야 할 말(기본 「뉴스」).
--       - news_days                 최근 며칠 안의 글만 소재로(1~30, 기본 3).
--       - paragraph_auto            문단 자동 나누기(모델이 빈 줄 없이 길게 쓴 글만, 기본 켬).
--       - paragraph_sentences       자동 나누기 때 한 문단 문장 수(1~10, 기본 3).
--   · ai_member_drafts.source_post_id  뉴스 글의 소재 글(사이트 회원이 쓴 「뉴스」 말머리 글). 같은 소재를 다시 쓰지 않게 실패 줄 말고는 모두 센다.
--                                      소재 글이 지워져도 줄은 남는다(FK 없음 — 기록용).
-- 외부 뉴스·검색 API 는 쓰지 않는다(사용자 지시 2026-11-11 — 사이트 안 글만 소재).

CREATE TABLE ai_prompt_settings (
    id                  integer      PRIMARY KEY,
    common_prompt       text,
    post_prompt         text,
    news_prompt         text,
    comment_prompt      text,
    reply_prompt        text,
    mood_chance         smallint     NOT NULL DEFAULT 20,
    moods               jsonb,
    news_ratio          smallint     NOT NULL DEFAULT 10,
    news_keyword        varchar(20)  NOT NULL DEFAULT '뉴스',
    news_days           smallint     NOT NULL DEFAULT 3,
    paragraph_auto      boolean      NOT NULL DEFAULT true,
    paragraph_sentences smallint     NOT NULL DEFAULT 3,
    updated_at          timestamptz,
    updated_by          bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_prompt_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_ai_prompt_settings_mood_chance CHECK (mood_chance BETWEEN 0 AND 100),
    CONSTRAINT ck_ai_prompt_settings_moods CHECK (moods IS NULL OR jsonb_typeof(moods) = 'array'),
    CONSTRAINT ck_ai_prompt_settings_news_ratio CHECK (news_ratio BETWEEN 0 AND 100),
    CONSTRAINT ck_ai_prompt_settings_news_keyword CHECK (length(btrim(news_keyword)) BETWEEN 1 AND 20),
    CONSTRAINT ck_ai_prompt_settings_news_days CHECK (news_days BETWEEN 1 AND 30),
    CONSTRAINT ck_ai_prompt_settings_sentences CHECK (paragraph_sentences BETWEEN 1 AND 10),
    CONSTRAINT ck_ai_prompt_settings_lengths CHECK (
        (common_prompt IS NULL OR length(common_prompt) <= 4000)
        AND (post_prompt IS NULL OR length(post_prompt) <= 2000)
        AND (news_prompt IS NULL OR length(news_prompt) <= 2000)
        AND (comment_prompt IS NULL OR length(comment_prompt) <= 2000)
        AND (reply_prompt IS NULL OR length(reply_prompt) <= 2000))
);
INSERT INTO ai_prompt_settings (id) VALUES (1);

ALTER TABLE ai_member_drafts ADD COLUMN source_post_id bigint;
-- 이미 쓴 소재 거르기(NOT EXISTS) — 뉴스 글 줄만
CREATE INDEX ix_ai_member_drafts_source ON ai_member_drafts (source_post_id) WHERE source_post_id IS NOT NULL;
