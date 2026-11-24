-- AI 기자 뉴스 기본 출처에 대형 IT 기업 공식 유튜브 채널 13곳을 더한다(ai-news-reporter-contract 「소재 수집」 — 사용자 결정 2026-11-24 「기본 출처로 넣어줘」).
--   V229 는 유튜브 채널을 기본으로 넣지 않았다. 2026-11-24 각 채널 페이지(@핸들)에서 채널 ID 를 확인하고 채널 RSS 가 200 + Atom 으로 열리는지, 최근 영상이 있는지 확인한 것만 넣는다.
--   광고·생활 영상이 많은 Apple · Google · Microsoft · Samsung · Meta 는 넣지 않았다. xAI · TSMC 는 공식 채널을 찾지 못했다.
--   이미 설치된 DB 와 새 설치 모두 이 파일로 들어간다. 같은 채널 ID 의 YOUTUBE 출처가 이미 있으면(관리자가 손으로 넣은 경우) 그 채널은 넣지 않는다.
--   순서(sort_order)는 지금 출처 맨 뒤에 이어 붙인다. 관심 키워드 거르기(V230)는 유튜브 항목에도 그대로 걸린다.
DO
$$
DECLARE
    v_base  bigint := ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1022::bigint << 12);
    v_now   timestamptz := now();
    v_order int := (SELECT coalesce(max(sort_order), 0) FROM ai_news_sources);
    v_row   record;
BEGIN
    FOR v_row IN
        SELECT *
          FROM (VALUES (1, 'Tesla 유튜브', 'UC5WjFrtBdufl6CZojX3D8dQ'),
                       (2, 'SpaceX 유튜브', 'UCtI0Hodo5o5dUb67FeUjDeA'),
                       (3, 'NVIDIA 유튜브', 'UCHuiy8bXnmK5nisYHUd1J5g'),
                       (4, 'AMD 유튜브', 'UCHQDjDDW8w2RieO-IuqYlyg'),
                       (5, 'Micron 유튜브', 'UCBqcI352Dc2ExKq1uSdwZvg'),
                       (6, 'Intel 유튜브', 'UCk7SjrXVXAj8m8BLgzh6dGA'),
                       (7, 'SK하이닉스 유튜브', 'UCltyil2FaQZZ4kUNinKkKmw'),
                       (8, '삼성반도체 뉴스룸 유튜브', 'UCWuCkZjfPwkHtjeVvB1LnVw'),
                       (9, 'OpenAI 유튜브', 'UCXZCJLdBC09xxGZ6gcdrc6A'),
                       (10, 'Anthropic 유튜브', 'UCrDwWp7EBBv4NwvScIpBDOA'),
                       (11, 'Boston Dynamics 유튜브', 'UC7vVhkEfw4nOGp8TyDk7RcQ'),
                       (12, 'Qualcomm 유튜브', 'UCH6eZr6vbZ6Bx53TyuSzxrg'),
                       (13, 'Arm 유튜브', 'UCvcBJFXTzCfU_sILnYVd4gg')) AS s (ord, name, channel_id)
         ORDER BY ord
    LOOP
        CONTINUE WHEN EXISTS (SELECT 1 FROM ai_news_sources WHERE kind = 'YOUTUBE' AND btrim(address) = v_row.channel_id);
        v_order := v_order + 1;
        INSERT INTO ai_news_sources (id, kind, name, address, enabled, sort_order, created_at, created_by, updated_at, updated_by)
        VALUES (v_base | v_row.ord, 'YOUTUBE', v_row.name, v_row.channel_id, true, v_order, v_now, NULL, v_now, NULL);
    END LOOP;
END
$$;
