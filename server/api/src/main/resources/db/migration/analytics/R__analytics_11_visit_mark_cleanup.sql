-- DB 배치 `analytics-visit-mark-cleanup` — 방문자 「그 날 셌다」 표시(analytics_visit_marks — analytics V6 부터 한국 날짜 하루 파티션) 조각 유지.
-- docs/features/site-analytics-contract.md 「주기 작업」·「3단계 결정 ⑥」(판정 표는 날짜 파티션 + DROP — 큰 DELETE 를 두지 않는다).
-- analytics 스트림의 반복 마이그레이션 — 공통 함수는 메인 DB(shared R__batch_00)에 있다(R__analytics_10 머리말). 키는 1단계(날마다 줄 DELETE)의 것을 이어 쓴다.
-- 규칙:
--   ① 조각 만들기 — 어제·오늘·내일·모레(한국 날짜) 조각이 없으면 만든다(자정에 조각이 없어 1분 몰아쓰기가 실패하지 않게 미리). 쓰는 쪽(api VisitFlusher)은 조각이 없다는
--      오류(23514)를 받으면 analytics_ensure_visit_mark_partitions 를 그 묶음의 날로 한 번 부르고 다시 쓴다. 여러 서버·배치가 동시에 불러도 잠금 아래에서 한 번만 만든다.
--   ② 지난 조각 지우기 — 판정은 한국 날짜 하루 단위라 오늘·어제 조각만 있으면 된다(자정 직전 신호를 자정 직후에 몰아 쓰면 어제 조각으로 판정한다). 그보다 앞선 날의 조각을
--      통째로 DROP 한다. 부모 표 잠금을 오래 기다리지 않는다(lock_timeout 5초 — 쓰기가 몰리면 다음 날 다시).
--   설정과 상관없이 돈다(끈 뒤에도 남은 표시는 지워야 한다). 집계(analytics_visit_hourly)는 지우지 않는다. 삭제 요청 크론(R__analytics_13)은 부모 표에 지우므로 그대로다.
-- 기본 실행: 매일 00:35 (한국시간).
-- 결과: 처리 건수 = 만든 조각 + 지운 조각, 0 이면 SKIP. 단계마다 COMMIT. 실패하면 그때까지의 건수와 사유를 남기고 FAIL.
-- 호출: CALL batch_analytics_visit_mark_cleanup();

-- 어제 ~ 모레(한국 날짜) 조각 + p_days(주면 — 쓰는 쪽 묶음의 날들) 조각을 만든다. 돌려주는 값 = 새로 만든 조각 수. 조각 이름 analytics_visit_marks_YYYYMMDD
CREATE OR REPLACE FUNCTION analytics_ensure_visit_mark_partitions(p_days date[] DEFAULT NULL) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_today   date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_days    date[];
    v_day     date;
    v_part    text;
    v_created int := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('analytics_visit_mark_partitions'));
    v_days := ARRAY[v_today - 1, v_today, v_today + 1, v_today + 2] || coalesce(p_days, '{}'::date[]);
    FOR v_day IN SELECT DISTINCT d FROM unnest(v_days) AS t(d) WHERE d IS NOT NULL ORDER BY 1 LOOP
        v_part := 'analytics_visit_marks_' || to_char(v_day, 'YYYYMMDD');
        IF to_regclass(v_part) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF analytics_visit_marks FOR VALUES FROM (%L) TO (%L)', v_part, v_day, v_day + 1);
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END
$fn$;

-- 날이 p_keep_from 앞인 조각을 지운다. 돌려주는 값 = 지운 조각 수. 이름(analytics_visit_marks_YYYYMMDD)으로 그 조각의 날을 읽는다
CREATE OR REPLACE FUNCTION analytics_drop_old_visit_mark_partitions(p_keep_from date) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_part    text;
    v_dropped int := 0;
BEGIN
    FOR v_part IN SELECT c.relname
                    FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                   WHERE i.inhparent = 'analytics_visit_marks'::regclass AND c.relname ~ '^analytics_visit_marks_[0-9]{8}$'
                   ORDER BY c.relname LOOP
        IF to_date(substr(v_part, length('analytics_visit_marks_') + 1), 'YYYYMMDD') < p_keep_from THEN
            EXECUTE format('DROP TABLE %I', v_part);
            v_dropped := v_dropped + 1;
        END IF;
    END LOOP;
    RETURN v_dropped;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_analytics_visit_mark_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'analytics-visit-mark-cleanup';
    v_created bigint := 0;
    v_dropped bigint := 0;
    v_err     text;
    v_state   text;
    v_msg     text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    -- ① 조각 만들기
    BEGIN
        v_created := analytics_ensure_visit_mark_partitions(NULL);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
        v_err := batch_error_text(v_state, v_msg);
    END;
    COMMIT;

    -- ② 어제보다 앞선 조각 통째로 지우기
    IF v_err IS NULL THEN
        BEGIN
            SET LOCAL lock_timeout = '5s';
            v_dropped := analytics_drop_old_visit_mark_partitions((now() AT TIME ZONE 'Asia/Seoul')::date - 1);
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_created + v_dropped, v_err,
        'batch.result.analyticsVisitMarkPartitions', batch_args(v_created::text, v_dropped::text),
        'batch.result.analyticsVisitMarkPartitions.none', NULL);
    COMMIT;
END
$proc$;

-- 마이그레이션 때 조각을 만들어 둔다(V6 가 만든 뒤 날이 바뀌었을 수 있다 — 빈 조각이라 비용이 없다).
-- chunk_size · max_per_run 은 3단계부터 쓰지 않는다(줄 DELETE 가 없다) — 이미 있는 값은 그대로 둔다
SELECT analytics_ensure_visit_mark_partitions(NULL);
SELECT batch_define('analytics-visit-mark-cleanup', 'batch_analytics_visit_mark_cleanup', 0, 35);
