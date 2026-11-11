-- AI 회원 — 동물 계정(글 방식) · 글 쓰는 때(docs/features/ai-members-contract.md 「동물 계정·글 쓰는 때 (2026-11-11)」).
-- ⚠ 가번호 V191 — develop 최신 V190(2026-11-11). 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다. 메뉴 변경 없음.
--
-- 무엇이 바뀌나
--   · ai_member_settings  「글 쓰는 때」 — 모든 AI 계정 공통(계정별 「하루 글 수」 대신). 실행기(관리자 서버 AiMemberScheduler)가 바퀴마다 읽는다.
--       - quiet_days          조용한 게시판 기준 일수(1~30, 기본 2) — 그 게시판(또는 AI 전용 소모임)에 이 일수 동안 새 글(사람·AI 모두, 삭제·숨김 제외)이 없으면 AI 가 한 건 쓴다.
--       - busy_interval_days  글이 잘 올라오는 게시판 간격(화면은 7 또는 30, 서버는 1~90, 기본 7) — 최근 quiet_days 안에 글이 있으면 그곳의 마지막 AI 회원 글이
--                             이 일수보다 오래됐을 때만 한 건 쓴다.
--       - daily_post_max      계정당 하루(한국 날짜) 최대 새 글 시도 수(0~5, 기본 1 — 0 이면 새 글을 쓰지 않는다. 실패한 시도도 센다).
--   · ai_member_profiles.voice  글 방식 NORMAL(보통) · DOG(강아지 소리) · CAT(고양이 소리), 기본 NORMAL. DOG·CAT 은 제목·본문을 그 동물 소리로만 쓰고 본문 끝 괄호에
--                               한두 문장 해석(존댓말)을 붙인다 — 뉴스 글·문단 자동 나누기·분위기는 쓰지 않는다. 본문은 200자로 자른다.
--   · ai_member_profiles.daily_posts  더는 쓰지 않는다(지우지 않음 — 옛 값 그대로, 새 계정은 2). 새 글 양은 위 「글 쓰는 때」가 정한다.
--   · ai_prompt_settings.dog_prompt · cat_prompt  강아지·고양이 소리 지시(NULL = 코드 기본값 AiPrompts.DEFAULT_DOG·DEFAULT_CAT, 2000자 이하).
--       DOG·CAT 계정의 새 글·첫 댓글·답글 지시를 대신한다(출력 형식 「제목: … 본문: …」/「본문만」은 서버가 늘 붙인다 — 파싱이 깨지지 않게).
-- 판정 질의는 게시판별 최근 글 시각을 한 번에 읽는다 — 글 표 ix_posts_board (board_id, id DESC) · ix_posts_group (group_id, id DESC) 와 id 하한(스노플레이크)으로 최근 범위만 본다.

ALTER TABLE ai_member_settings
    ADD COLUMN quiet_days         smallint NOT NULL DEFAULT 2,
    ADD COLUMN busy_interval_days smallint NOT NULL DEFAULT 7,
    ADD COLUMN daily_post_max     smallint NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_ai_member_settings_quiet_days CHECK (quiet_days BETWEEN 1 AND 30),
    ADD CONSTRAINT ck_ai_member_settings_busy_interval CHECK (busy_interval_days BETWEEN 1 AND 90),
    ADD CONSTRAINT ck_ai_member_settings_daily_post_max CHECK (daily_post_max BETWEEN 0 AND 5);

ALTER TABLE ai_member_profiles
    ADD COLUMN voice varchar(8) NOT NULL DEFAULT 'NORMAL',
    ADD CONSTRAINT ck_ai_member_profiles_voice CHECK (voice IN ('NORMAL', 'DOG', 'CAT'));

COMMENT ON COLUMN ai_member_profiles.daily_posts IS '쓰지 않음(V191 — 새 글 양은 ai_member_settings 의 quiet_days·busy_interval_days·daily_post_max). 지우지 않고 옛 값을 둔다.';

ALTER TABLE ai_prompt_settings
    ADD COLUMN dog_prompt text,
    ADD COLUMN cat_prompt text,
    ADD CONSTRAINT ck_ai_prompt_settings_animal_lengths CHECK (
        (dog_prompt IS NULL OR length(dog_prompt) <= 2000)
        AND (cat_prompt IS NULL OR length(cat_prompt) <= 2000));
