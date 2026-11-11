-- 방문 통계 3단계 — docs/features/site-analytics-contract.md 「3단계」 결정 ①(원본 보관 = 달 단위) · 결정 ⑤⑥(핫딜 클릭 판정 표 하루 파티션).
--
-- ① stats_collection_settings.raw_retention_months — 방문 원본(analytics_page_views) 보관 개월 수 2 · 3 · 6 · 12(이번 달 포함, 기본 2 = 이번 달 + 지난달).
--    크론(analytics R__analytics_10)이 「이번 달부터 n번째 앞 달보다 오래된 달 조각」을 통째로 지운다(줄 DELETE 없음).
--    롤링 배포(API 서버 2~3대를 차례로 띄운다) 동안 옛 서버가 옛 칸을 읽고 쓰므로 raw_retention_days 는 지우지 않고 칸을 더한다 — 새 코드는 옛 칸을 쓰지 않는다
--    (다음 버전에서 지운다). 기존 값 옮기기: 30 → 2 · 90 → 3 · 180 → 6 · 365 → 12.
--    이력(stats_collection_setting_history.changes)의 field 는 이제 rawRetentionMonths — 옛 줄의 rawRetentionDays(일 값)는 그대로 남는다(화면이 둘 다 읽는다).
ALTER TABLE stats_collection_settings ADD COLUMN IF NOT EXISTS raw_retention_months smallint NOT NULL DEFAULT 2;
ALTER TABLE stats_collection_settings DROP CONSTRAINT IF EXISTS ck_stats_collection_settings_retention_months;
ALTER TABLE stats_collection_settings ADD CONSTRAINT ck_stats_collection_settings_retention_months CHECK (raw_retention_months IN (2, 3, 6, 12));
UPDATE stats_collection_settings
   SET raw_retention_months = CASE raw_retention_days WHEN 30 THEN 2 WHEN 90 THEN 3 WHEN 180 THEN 6 WHEN 365 THEN 12 ELSE 2 END;

-- ② deal_click_daily(V122 — 구매 링크 클릭 「하루 같은 방문자·같은 글 1번」 판정) → 한국 날짜(day) 하루 범위 파티션.
--    클릭 수는 핫딜 행(post_deals.click_count)에 따로 있어 판정 표는 오늘·어제만 있으면 된다 — 지난 조각은 DB 배치(shared R__batch_43 `deal-click-partitions`)가
--    통째로 DROP 한다(큰 DELETE·vacuum 빈자리 없음). 조각은 그 배치가 오늘·내일·모레 것을 미리 만들고, 쓰는 쪽(DealQueries#recordClick)은 조각이 없다는 오류(23514)를 받으면
--    한 번 만들고 다시 쓴다(deal_ensure_click_partitions — R__batch_43).
--    옮기기: 같은 이름·같은 칸의 파티션 표를 새로 만들어 어제·오늘 줄만 옮기고 바꿔 끼운다(칸이 같아 롤링 배포 중 옛 서버의 넣기 문장도 그대로 맞는다).
--    기본 키 (post_id, day, visitor_hash) 가 파티션 열(day)을 담아 ON CONFLICT DO NOTHING 이 그대로 동작한다. 하루 조각이라 ix_deal_click_daily_day(day) 는 다시 만들지 않는다.
--    조각 이름: deal_click_daily_YYYYMMDD.
ALTER TABLE deal_click_daily RENAME TO deal_click_daily_old;
ALTER TABLE deal_click_daily_old RENAME CONSTRAINT deal_click_daily_pkey TO deal_click_daily_old_pkey;
DROP INDEX IF EXISTS ix_deal_click_daily_day;

CREATE TABLE deal_click_daily (
    post_id      bigint      NOT NULL,
    day          date        NOT NULL,
    visitor_hash varchar(64) NOT NULL,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (post_id, day, visitor_hash)
) PARTITION BY RANGE (day);

DO $$
DECLARE
    v_today date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_day   date;
BEGIN
    FOR i IN -1..2 LOOP -- 어제 · 오늘 · 내일 · 모레
        v_day := v_today + i;
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF deal_click_daily FOR VALUES FROM (%L) TO (%L)',
                       'deal_click_daily_' || to_char(v_day, 'YYYYMMDD'), v_day, v_day + 1);
    END LOOP;
    INSERT INTO deal_click_daily (post_id, day, visitor_hash, created_at)
    SELECT post_id, day, visitor_hash, created_at FROM deal_click_daily_old WHERE day >= v_today - 1 AND day <= v_today + 2
    ON CONFLICT DO NOTHING;
END
$$;

DROP TABLE deal_click_daily_old;
