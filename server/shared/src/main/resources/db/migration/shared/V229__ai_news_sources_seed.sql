-- AI 기자 뉴스 기본 출처(ai-news-reporter-contract 「소재 수집」 — 설치 시 적게: 국내 4 · 해외 3). 2026-11-16 구현 때 curl 로 실제로 열리는지(200 + RSS/Atom) 확인한 주소만 넣었다.
--   국내: 연합뉴스 산업(연합뉴스 IT·과학 전용 RSS 주소는 404 — 산업 RSS 로 대신) · 전자신문 AI·SW · ZDNet Korea(feedburner) · 블로터 전체기사
--   해외: The Verge · TechCrunch · Electrek
--   유튜브 채널은 기본 없음(관리자가 채널 ID 로 더한다). 구글 뉴스 검색어(KEYWORD)도 기본 없음. 주소가 바뀌면 관리자가 「뉴스 출처」에서 고친다.
--   이미 출처가 있으면(다시 돌리거나 손으로 넣은 DB) 넣지 않는다.
DO
$$
DECLARE
    v_base bigint := ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1022::bigint << 12);
    v_now  timestamptz := now();
    v_row  record;
BEGIN
    IF EXISTS (SELECT 1 FROM ai_news_sources) THEN
        RETURN;
    END IF;
    FOR v_row IN
        SELECT *
          FROM (VALUES (1, '연합뉴스 산업', 'https://www.yna.co.kr/rss/industry.xml'),
                       (2, '전자신문 AI·SW', 'https://rss.etnews.com/04.xml'),
                       (3, 'ZDNet Korea', 'https://feeds.feedburner.com/zdkorea'),
                       (4, '블로터', 'https://www.bloter.net/rss/allArticle.xml'),
                       (5, 'The Verge', 'https://www.theverge.com/rss/index.xml'),
                       (6, 'TechCrunch', 'https://techcrunch.com/feed/'),
                       (7, 'Electrek', 'https://electrek.co/feed/')) AS s (ord, name, address)
         ORDER BY ord
    LOOP
        INSERT INTO ai_news_sources (id, kind, name, address, enabled, sort_order, created_at, created_by, updated_at, updated_by)
        VALUES (v_base | v_row.ord, 'RSS', v_row.name, v_row.address, true, v_row.ord, v_now, NULL, v_now, NULL);
    END LOOP;
END
$$;
