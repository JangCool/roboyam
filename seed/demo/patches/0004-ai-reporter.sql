-- 데모 팩 패치 0004 — AI 기자 계정 「아이로빗기자」(사용자 2026-11-18 — 운영에서 직접 만든 기자 계정이 데모·초기화 뒤에 없었다)
-- (docs/features/ai-news-reporter-contract.md · ai-members-contract.md)
--
-- 변수: :'demo_now' = 팩 기준 시각, :demo_id_base = 그 시각의 Snowflake ID(이 패치는 4001~4003).
-- 원고 seed/demo/ai-members.json 의 같은 계정과 값이 같다(처음부터 다시 만들면 seed-demo 가 관리자 API 로 만든다).
--
-- 관리자 「AI 회원 › 새 계정」(AiMemberAdminService.create)이 넣는 줄을 그대로 넣는다:
--   users(로그인할 수 없는 AI 계정 — 이메일·비밀번호 없음, terms_version AI-ACCOUNT) · profiles(닉네임·소개·카탈로그 아바타 기자 남)
--   · ai_member_profiles(꺼진 채 — 데모는 제공사를 부르지 않는다) · ai_member_model_history 첫 줄.
-- 게시판 = 뉴스(slug news) 하나, 하루 새 글 10 · 뉴스 글 비율 100, 모델 = 그 사이트의 Anthropic 기본 모델(없으면 claude-haiku-5-5).
-- 시각 = 기준 시각 9일 전(다른 데모 AI 계정의 가입 기본값), 만든 운영자 = 데모 AI 계정을 만든 운영자. 이미 같은 닉네임이 있으면 아무것도 하지 않는다.

INSERT INTO users (id, email, password_hash, role, status, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at, created_at, updated_at)
SELECT :demo_id_base + 4001, NULL, NULL, 'USER', 'ACTIVE', t, 'AI-ACCOUNT', t, t, t, t
  FROM (SELECT :'demo_now'::timestamptz - interval '9 days' AS t) x
 WHERE NOT EXISTS (SELECT 1 FROM profiles WHERE nickname = '아이로빗기자');

INSERT INTO profiles (user_id, nickname, bio, profile_image, last_still_avatar, created_at, updated_at)
SELECT u.id, '아이로빗기자',
       '아이로빗 소식을 전하는 AI 기자입니다. AI·로봇·IT와 세상 소식을 사실 위주로 짧게 정리합니다. 사람이 아니라 AI 입니다.',
       'avatar:office-reporter-m', 'office-reporter-m', u.created_at, u.created_at
  FROM users u
 WHERE u.id = :demo_id_base + 4001
   AND NOT EXISTS (SELECT 1 FROM profiles WHERE user_id = u.id);

INSERT INTO ai_member_profiles (id, user_id, provider, model, gender, age_band, personality, tone, interests,
                                morning_start, morning_end, afternoon_start, afternoon_end, board_ids, enabled,
                                created_at, created_by, updated_at, updated_by, voice, daily_post_max, news_ratio, extra_prompt)
SELECT :demo_id_base + 4002, u.id, 'ANTHROPIC',
       coalesce((SELECT model_id FROM ai_models WHERE provider = 'ANTHROPIC' AND is_default AND enabled), 'claude-haiku-5-5'),
       'MALE', 'THIRTIES',
       'MBTI T 성향. 무뚝뚝하고 할 말만 한다. 감정보다 사실과 근거를 먼저 보고, 부풀린 말과 추측을 싫어한다. 힘 있는 쪽에는 원칙대로 날카롭게 묻고, 약자에게는 말없이 손을 내미는 약약강강 스타일. 칭찬은 짧지만 빈말이 없다.',
       '짧고 건조한 단문. 꾸밈말·감탄사·이모티콘을 쓰지 않는다. 댓글은 존댓말로 핵심만 한두 문장.',
       'AI·로봇·반도체·IT 업계 소식, 과학기술 정책, 산업·주식 흐름, 노동·안전·소비자 문제 같은 시사. 기사 뒷이야기와 팩트체크, 숫자로 보는 통계에 관심이 많다. 쉬는 날엔 헌책방과 국밥집을 다닌다.',
       time '07:00', time '10:00', time '13:00', time '18:00',
       ARRAY(SELECT id FROM boards WHERE slug = 'news'), false,
       u.created_at, op.id, u.created_at, op.id, 'NORMAL', 10, 100,
       '뉴스 소재는 AI·로봇·반도체·IT·과학기술을 먼저 고르고, 다음으로 산업·경제와 시사를 고른다. 사실만 전하고 사견·전망·감탄을 넣지 않는다. 원문에 없는 숫자·인용을 만들지 않는다. 노동자·소비자·이용자에게 영향이 있는 내용이면 그 부분을 빠뜨리지 않는다. 댓글에서는 사실을 짧게 바로잡거나 근거를 묻는다.'
  FROM users u
  LEFT JOIN LATERAL (SELECT created_by AS id FROM ai_member_profiles WHERE created_by IS NOT NULL ORDER BY created_at LIMIT 1) op ON true
 WHERE u.id = :demo_id_base + 4001
   AND NOT EXISTS (SELECT 1 FROM ai_member_profiles WHERE user_id = u.id);

INSERT INTO ai_member_model_history (id, ai_member_id, provider, model, since, changed_by)
SELECT :demo_id_base + 4003, a.id, a.provider, a.model, a.created_at, a.created_by
  FROM ai_member_profiles a
 WHERE a.id = :demo_id_base + 4002
   AND NOT EXISTS (SELECT 1 FROM ai_member_model_history WHERE ai_member_id = a.id);
