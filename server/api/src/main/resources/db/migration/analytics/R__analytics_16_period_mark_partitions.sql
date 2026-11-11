-- DB 배치 `analytics-period-mark-partitions` — 주·월 순방문자 표시(analytics V8 — 방문자·회원 × 주·달, 기간 범위 파티션) 조각 유지.
-- docs/features/site-analytics-contract.md 「3단계」 결정 ④(주·월 순방문자) · 결정 ⑥(판정 표는 날짜·기간 파티션 + DROP — 큰 DELETE 를 두지 않는다).
-- analytics 스트림의 반복 마이그레이션 — 공통 함수는 메인 DB(shared R__batch_00)에 있다(R__analytics_10 머리말).
-- 규칙:
--   ① 조각 만들기 — 이번 주·다음 주(월요일 시작, 한국 날짜) 주 조각과 이번 달·다음 달 달 조각을 네 표 모두에 없으면 만든다(기간이 바뀌는 자정에 조각이 없어 1분 몰아쓰기가
--      실패하지 않게 미리). 쓰는 쪽(api VisitFlusher)은 조각이 없다는 오류(23514)를 받으면 analytics_ensure_period_mark_partitions 를 그 묶음의 날로 한 번 부르고 다시 쓴다.
--      여러 서버·배치가 동시에 불러도 잠금 아래에서 한 번만 만든다.
--   ② 지난 조각 지우기 — 기간이 끝나고 정리 여유(c_grace_days, 3일)가 지난 조각을 통째로 DROP 한다(끝난 기간은 숫자 표 analytics_unique_counts 만 남긴다 — 표시는 사람을
--      가리키므로 오래 두지 않는다). 여유를 두는 까닭: 서버가 최대 1분 늦게 몰아 쓰고, 몰아쓰기가 실패하면 다음 1분에 지난 기간 줄을 다시 쓴다. 부모 표 잠금을 오래 기다리지 않는다
--      (lock_timeout 5초 — 쓰기가 몰리면 다음 날 다시).
--   설정과 상관없이 돈다(끈 뒤에도 남은 표시는 지워야 한다). 숫자 표는 지우지 않는다(기한 없음).
-- 기본 실행: 매일 00:55 (한국시간 — batch_daily_cron 이 환산). 주·달이 바뀌는 날(과 그 며칠 뒤)만 일이 있고 나머지 날은 SKIP.
-- 결과: 처리 건수 = 만든 조각 + 지운 조각, 0 이면 SKIP. 단계마다 COMMIT. 실패하면 그때까지의 건수와 사유를 남기고 FAIL.
-- 호출: CALL batch_analytics_period_mark_partitions();  (「지금 실행」은 CALL batch_analytics_period_mark_partitions('MANUAL', 운영자ID, NULL))

-- 이번 주·다음 주 + 이번 달·다음 달(한국 날짜) 조각 + p_days(주면 — 쓰는 쪽 묶음의 날들)가 든 기간의 조각을 네 표에 만든다. 돌려주는 값 = 새로 만든 조각 수.
-- 조각 이름: 주 = <표>_YYYYMMDD(그 주 월요일), 달 = <표>_YYYYMM
CREATE OR REPLACE FUNCTION analytics_ensure_period_mark_partitions(p_days date[] DEFAULT NULL) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_today   date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_start   date;
    v_table   text;
    v_part    text;
    v_created int := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('analytics_period_mark_partitions'));
    -- 주 조각(월요일 시작 — date_trunc('week') 는 ISO 주라 월요일)
    FOR v_start IN SELECT DISTINCT date_trunc('week', d)::date
                     FROM unnest(ARRAY[v_today, v_today + 7] || coalesce(p_days, '{}'::date[])) AS t(d)
                    WHERE d IS NOT NULL ORDER BY 1 LOOP
        FOREACH v_table IN ARRAY ARRAY['analytics_visitor_week_marks', 'analytics_member_week_marks'] LOOP
            v_part := v_table || '_' || to_char(v_start, 'YYYYMMDD');
            IF to_regclass(v_part) IS NULL THEN
                EXECUTE format('CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)', v_part, v_table, v_start, v_start + 7);
                v_created := v_created + 1;
            END IF;
        END LOOP;
    END LOOP;
    -- 달 조각
    FOR v_start IN SELECT DISTINCT date_trunc('month', d)::date
                     FROM unnest(ARRAY[v_today, (date_trunc('month', v_today) + interval '1 month')::date] || coalesce(p_days, '{}'::date[])) AS t(d)
                    WHERE d IS NOT NULL ORDER BY 1 LOOP
        FOREACH v_table IN ARRAY ARRAY['analytics_visitor_month_marks', 'analytics_member_month_marks'] LOOP
            v_part := v_table || '_' || to_char(v_start, 'YYYYMM');
            IF to_regclass(v_part) IS NULL THEN
                EXECUTE format('CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)', v_part, v_table, v_start, (v_start + interval '1 month')::date);
                v_created := v_created + 1;
            END IF;
        END LOOP;
    END LOOP;
    RETURN v_created;
END
$fn$;

-- 기간의 끝(다음 기간 첫날) + p_grace_days 가 p_today 와 같거나 앞인 조각을 지운다. 돌려주는 값 = 지운 조각 수. 이름으로 그 조각의 기간을 읽는다
CREATE OR REPLACE FUNCTION analytics_drop_old_period_mark_partitions(p_today date, p_grace_days int) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_table   text;
    v_week    boolean;
    v_part    text;
    v_start   date;
    v_end     date;
    v_dropped int := 0;
BEGIN
    FOREACH v_table IN ARRAY ARRAY['analytics_visitor_week_marks', 'analytics_member_week_marks', 'analytics_visitor_month_marks', 'analytics_member_month_marks'] LOOP
        v_week := v_table IN ('analytics_visitor_week_marks', 'analytics_member_week_marks');
        FOR v_part IN SELECT c.relname
                        FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                       WHERE i.inhparent = v_table::regclass
                         AND c.relname ~ ('^' || v_table || CASE WHEN v_week THEN '_[0-9]{8}$' ELSE '_[0-9]{6}$' END)
                       ORDER BY c.relname LOOP
            IF v_week THEN
                v_start := to_date(substr(v_part, length(v_table) + 2), 'YYYYMMDD');
                v_end := v_start + 7;
            ELSE
                v_start := to_date(substr(v_part, length(v_table) + 2) || '01', 'YYYYMMDD');
                v_end := (v_start + interval '1 month')::date;
            END IF;
            IF v_end + p_grace_days <= p_today THEN
                EXECUTE format('DROP TABLE %I', v_part);
                v_dropped := v_dropped + 1;
            END IF;
        END LOOP;
    END LOOP;
    RETURN v_dropped;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_analytics_period_mark_partitions(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job        CONSTANT text := 'analytics-period-mark-partitions';
    c_grace_days CONSTANT int := 3;
    v_created    bigint := 0;
    v_dropped    bigint := 0;
    v_err        text;
    v_state      text;
    v_msg        text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    -- ① 조각 만들기
    BEGIN
        v_created := analytics_ensure_period_mark_partitions(NULL);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
        v_err := batch_error_text(v_state, v_msg);
    END;
    COMMIT;

    -- ② 끝나고 정리 여유가 지난 기간의 조각 통째로 지우기
    IF v_err IS NULL THEN
        BEGIN
            SET LOCAL lock_timeout = '5s';
            v_dropped := analytics_drop_old_period_mark_partitions((now() AT TIME ZONE 'Asia/Seoul')::date, c_grace_days);
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_created + v_dropped, v_err,
        'batch.result.analyticsPeriodMarkPartitions', batch_args(v_created::text, v_dropped::text),
        'batch.result.analyticsPeriodMarkPartitions.none', NULL);
    COMMIT;
END
$proc$;

-- 마이그레이션 때 조각을 만들어 둔다(V8 은 부모 표만 만든다 — 빈 조각이라 비용이 없다)
SELECT analytics_ensure_period_mark_partitions(NULL);
SELECT batch_define('analytics-period-mark-partitions', 'batch_analytics_period_mark_partitions', 0, 55);
