-- DB 배치 `analytics-monthly-rollup` — 방문 집계 월별 합치기 + 13개월 정리. docs/features/site-analytics-contract.md 「3단계」 결정 ③ · 「검토 보강」.
-- analytics 스트림의 반복 마이그레이션 — 공통 함수는 메인 DB(shared R__batch_00)에 있다(R__analytics_10 머리말). 월별 표는 analytics V7.
-- 규칙:
--   ① 합치기 — 끝난 달 m 의 일별 8개 표(page · flow · referrer · campaign · env · search · search_filtered · bot)와 시간별 방문(analytics_visit_hourly — 한국 달 경계)을
--      같은 열쇠의 월별 표에 <덮어써> 합친다(그 달 줄을 지우고 다시 넣기 — 몇 번 돌려도 같은 값). 합친 달은 analytics_monthly_rollups 에 적는다.
--      · 지난달: 매달 1일·2일(한국 날짜)에 합친다 — 서버가 최대 1분 늦게 몰아 쓰므로 1일 자정 직후에는 지난달 마지막 몇 분이 빠질 수 있어 2일에 한 번 더(덮어쓰기라 같은 결과).
--      · 따라잡기: 합친 기록이 없는 끝난 달(크론이 1·2일 모두 못 돈 달, 처음 배포 때의 지난 달들)은 그날 합친다.
--      · 월별 방문의 방문자는 「일별 합」(visitors_daily_sum) — 월 순방문자가 아니다(결정 ④ 의 숫자 표에서만 읽는다).
--   ② 13개월 정리 — 「이번 달 + 지난 12개월」(달력 단위 — 작년 같은 날과 늘 비교 가능)보다 오래된 일별·시간별 줄을 덩어리로 지운다. ① 이 모두 성공했을 때만 —
--      그 앞의 달은 모두 합친 기록이 있다(①이 데이터가 있는 가장 오래된 달부터 본다). 지우다 멈춘 달은 합친 기록이 있어 다시 합치지 않는다(남은 줄로 월별을 덮어쓰지 않게).
--   설정과 상관없이 돈다(수집을 꺼도 쌓인 집계는 합치고 기한대로 지운다). 진단 표(analytics_collector_*)는 R__analytics_14 대로.
-- 기본 실행: 매일 00:30 (한국시간 — batch_daily_cron 이 환산). 월 1·2일 식이 아니라 매일인 까닭: 한국 00:30 은 UTC 로 전날 15:30 이라 「1·2일」 식을 pg_cron 시간대로 옮기면
--   날짜가 밀린다(db-batch.md 「시각 환산」) — 매일 돌고 할 일이 없는 날은 바로 SKIP 한다(월 하루 이틀만 일한다).
-- 설정값(batch_params): chunk_size(5000) · max_per_run(1000000).
-- 결과: 처리 건수 = 합친 달 수 + 지운 줄 수, 0 이면 SKIP. 달마다·덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유를 남기고 FAIL.
-- 호출: CALL batch_analytics_monthly_rollup();  (「지금 실행」은 CALL batch_analytics_monthly_rollup('MANUAL', 운영자ID, NULL))

-- 한 달(그 달 1일)을 월별 표에 덮어써 합친다. 돌려주는 값 = 쓴 월별 줄 수
CREATE OR REPLACE FUNCTION analytics_rollup_month(p_month date) RETURNS bigint
LANGUAGE plpgsql AS $fn$
DECLARE
    v_next date := (p_month + interval '1 month')::date;
    v_from timestamptz := p_month::timestamp AT TIME ZONE 'Asia/Seoul';
    v_to   timestamptz := v_next::timestamp AT TIME ZONE 'Asia/Seoul';
    v_n    bigint;
    v_rows bigint := 0;
BEGIN
    DELETE FROM analytics_visit_monthly WHERE month = p_month;
    INSERT INTO analytics_visit_monthly (month, visitors_daily_sum, new_visitors, page_views)
    SELECT p_month, sum(visitors), sum(new_visitors), sum(page_views) FROM analytics_visit_hourly WHERE hour >= v_from AND hour < v_to HAVING count(*) > 0;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_page_monthly WHERE month = p_month;
    INSERT INTO analytics_page_monthly (month, path, kind, views)
    SELECT p_month, path, kind, sum(views) FROM analytics_page_daily WHERE day >= p_month AND day < v_next GROUP BY path, kind;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_flow_monthly WHERE month = p_month;
    INSERT INTO analytics_flow_monthly (month, from_path, to_path, moves)
    SELECT p_month, from_path, to_path, sum(moves) FROM analytics_flow_daily WHERE day >= p_month AND day < v_next GROUP BY from_path, to_path;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_referrer_monthly WHERE month = p_month;
    INSERT INTO analytics_referrer_monthly (month, referrer_group, referrer_domain, visits)
    SELECT p_month, referrer_group, referrer_domain, sum(visits) FROM analytics_referrer_daily WHERE day >= p_month AND day < v_next GROUP BY referrer_group, referrer_domain;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_campaign_monthly WHERE month = p_month;
    INSERT INTO analytics_campaign_monthly (month, utm_source, utm_medium, utm_campaign, visits)
    SELECT p_month, utm_source, utm_medium, utm_campaign, sum(visits) FROM analytics_campaign_daily WHERE day >= p_month AND day < v_next
     GROUP BY utm_source, utm_medium, utm_campaign;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_env_monthly WHERE month = p_month;
    INSERT INTO analytics_env_monthly (month, device, browser, os, screen, visits)
    SELECT p_month, device, browser, os, screen, sum(visits) FROM analytics_env_daily WHERE day >= p_month AND day < v_next GROUP BY device, browser, os, screen;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_search_monthly WHERE month = p_month;
    INSERT INTO analytics_search_monthly (month, term, searches, no_result)
    SELECT p_month, term, sum(searches), sum(no_result) FROM analytics_search_daily WHERE day >= p_month AND day < v_next GROUP BY term;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_search_filtered_monthly WHERE month = p_month;
    INSERT INTO analytics_search_filtered_monthly (month, searches)
    SELECT p_month, sum(searches) FROM analytics_search_filtered_daily WHERE day >= p_month AND day < v_next HAVING count(*) > 0;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    DELETE FROM analytics_bot_monthly WHERE month = p_month;
    INSERT INTO analytics_bot_monthly (month, kind, requests)
    SELECT p_month, kind, sum(requests) FROM analytics_bot_daily WHERE day >= p_month AND day < v_next GROUP BY kind;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    v_rows := v_rows + v_n;

    INSERT INTO analytics_monthly_rollups (month, rolled_at) VALUES (p_month, now())
    ON CONFLICT (month) DO UPDATE SET rolled_at = EXCLUDED.rolled_at;
    RETURN v_rows;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_analytics_monthly_rollup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'analytics-monthly-rollup';
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 1000000, 1000, 100000000);
    v_today   date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_this    date := date_trunc('month', v_today)::date;
    v_last    date := (v_this - interval '1 month')::date;
    v_keep    date := (v_this - interval '12 months')::date; -- 남길 첫 날 = 이번 달 + 지난 12개월
    v_oldest  date;
    v_month   date;
    v_rolled  bigint := 0;
    v_deleted bigint := 0;
    v_n       bigint;
    v_table   text;
    v_err     text;
    v_state   text;
    v_msg     text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    -- 데이터가 있는 가장 오래된 달 — 표마다 기본 키 맨 앞(day · hour)의 앞 끝 한 칸
    SELECT date_trunc('month', min(d))::date INTO v_oldest
      FROM (SELECT min(day) AS d FROM analytics_page_daily
            UNION ALL SELECT min(day) FROM analytics_flow_daily
            UNION ALL SELECT min(day) FROM analytics_referrer_daily
            UNION ALL SELECT min(day) FROM analytics_campaign_daily
            UNION ALL SELECT min(day) FROM analytics_env_daily
            UNION ALL SELECT min(day) FROM analytics_search_daily
            UNION ALL SELECT min(day) FROM analytics_search_filtered_daily
            UNION ALL SELECT min(day) FROM analytics_bot_daily
            UNION ALL SELECT (min(hour) AT TIME ZONE 'Asia/Seoul')::date FROM analytics_visit_hourly) x;

    -- ① 합치기 — 오래된 달부터 지난달까지, 합친 기록이 없는 달 + (1·2일이면) 지난달
    IF v_oldest IS NOT NULL THEN
        v_month := v_oldest;
        WHILE v_month <= v_last LOOP
            IF NOT EXISTS (SELECT 1 FROM analytics_monthly_rollups r WHERE r.month = v_month)
               OR (v_month = v_last AND extract(day FROM v_today) <= 2) THEN
                BEGIN
                    PERFORM analytics_rollup_month(v_month);
                    v_rolled := v_rolled + 1;
                EXCEPTION WHEN OTHERS THEN
                    GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                    v_err := batch_error_text(v_state, v_msg);
                END;
                COMMIT;
                EXIT WHEN v_err IS NOT NULL;
            END IF;
            v_month := (v_month + interval '1 month')::date;
        END LOOP;
    END IF;

    -- ② 13개월보다 오래된 일별 줄 — 표마다 덩어리로(그 앞의 달은 ① 에서 모두 합쳤다)
    IF v_err IS NULL AND v_oldest IS NOT NULL AND v_oldest < v_keep THEN
        FOREACH v_table IN ARRAY ARRAY['analytics_page_daily', 'analytics_flow_daily', 'analytics_referrer_daily', 'analytics_campaign_daily', 'analytics_env_daily',
                                       'analytics_search_daily', 'analytics_search_filtered_daily', 'analytics_bot_daily'] LOOP
            WHILE v_err IS NULL AND v_deleted < v_max LOOP
                BEGIN
                    EXECUTE format('DELETE FROM %I WHERE ctid = ANY(ARRAY(SELECT ctid FROM %I WHERE day < $1 LIMIT $2))', v_table, v_table)
                        USING v_keep, least(v_chunk, v_max - v_deleted);
                    GET DIAGNOSTICS v_n = ROW_COUNT;
                EXCEPTION WHEN OTHERS THEN
                    GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                    v_err := batch_error_text(v_state, v_msg);
                    EXIT;
                END;
                v_deleted := v_deleted + v_n;
                COMMIT;
                EXIT WHEN v_n = 0;
            END LOOP;
            EXIT WHEN v_err IS NOT NULL OR v_deleted >= v_max;
        END LOOP;
        -- 시간별 방문(한국 달 경계)
        WHILE v_err IS NULL AND v_deleted < v_max LOOP
            BEGIN
                DELETE FROM analytics_visit_hourly
                 WHERE hour IN (SELECT hour FROM analytics_visit_hourly
                                 WHERE hour < (v_keep::timestamp AT TIME ZONE 'Asia/Seoul')
                                 ORDER BY hour
                                 LIMIT least(v_chunk, v_max - v_deleted));
                GET DIAGNOSTICS v_n = ROW_COUNT;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                EXIT;
            END;
            v_deleted := v_deleted + v_n;
            COMMIT;
            EXIT WHEN v_n = 0;
        END LOOP;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_rolled + v_deleted, v_err,
        CASE WHEN v_deleted >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.analyticsMonthlyRollup' END,
        CASE WHEN v_deleted >= v_max THEN batch_args(v_deleted::text, v_max::text) ELSE batch_args(v_rolled::text, v_deleted::text) END,
        'batch.result.analyticsMonthlyRollup.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('analytics-monthly-rollup', 'chunk_size', 5000);
SELECT batch_param_seed('analytics-monthly-rollup', 'max_per_run', 1000000);
SELECT batch_define('analytics-monthly-rollup', 'batch_analytics_monthly_rollup', 0, 30);
