-- AI 기자 뉴스 — 수집 줄이기(docs/features/ai-news-reporter-contract.md 「소재 수집」, 사용자 2026-11-16 「너무 많이 모은다」).
--   · ai_member_settings.news_collect_keywords  관심 키워드(쉼표로 이음 — 낱말에는 쉼표가 없다, 낱말당 1~30자 · 최대 60개 → 최대 1,859자).
--       RSS·YOUTUBE 출처 항목은 제목 또는 요약에 이 낱말이 하나라도 들어 있을 때만 넣는다(대소문자 무시). KEYWORD 출처는 이미 검색이라 거르지 않는다.
--       비어 있으면(빈 글자) 거르지 않는다. 관리자 AI 회원 설정 「뉴스 출처」의 「관심 키워드」가 고친다.
--   · 출처당 한 번에 읽는 개수 30 → 10 은 코드(AiNewsFeeds.ITEMS_PER_SOURCE)만 바뀐다.
--   · 이미 쌓인 ai_news_items 중 RSS·YOUTUBE 출처 항목이면서 기본 키워드에 안 맞고 아직 안 쓴 것(used_at IS NULL)은 지운다(같은 기준 — ILIKE ANY).
ALTER TABLE ai_member_settings
    ADD COLUMN news_collect_keywords varchar(2000) NOT NULL
        DEFAULT 'AI,인공지능,생성형,챗GPT,OpenAI,로봇,휴머노이드,테슬라,Tesla,스페이스X,SpaceX,머스크,반도체,엔비디아,Nvidia,HBM,삼성전자,SK하이닉스,TSMC,자율주행,전기차,EV,주식,증시,코스피,나스닥,유튜버,유튜브';

COMMENT ON COLUMN ai_member_settings.news_collect_keywords IS 'AI 기자 뉴스 관심 키워드(쉼표로 이음, 빈 글자 = 거르지 않음) — RSS·YOUTUBE 수집 항목은 제목·요약에 하나라도 들어간 것만 넣는다 — V230';

DELETE
FROM ai_news_items i
    USING ai_news_sources s
WHERE s.id = i.source_id
  AND s.kind IN ('RSS', 'YOUTUBE')
  AND i.used_at IS NULL
  AND NOT (
    i.title ILIKE ANY (SELECT '%' || k || '%' FROM unnest(string_to_array((SELECT news_collect_keywords FROM ai_member_settings WHERE id = 1), ',')) AS k WHERE k <> '')
        OR coalesce(i.summary, '') ILIKE ANY (SELECT '%' || k || '%' FROM unnest(string_to_array((SELECT news_collect_keywords FROM ai_member_settings WHERE id = 1), ',')) AS k WHERE k <> '')
    );
