-- DB 배치 `analytics-visit-mark-cleanup` — 방문자 「그 날 셌다」 표시(analytics_visit_marks, analytics V1) 정리. docs/features/site-analytics-contract.md 「주기 작업」.
-- analytics 스트림의 반복 마이그레이션 — 공통 함수는 메인 DB(shared R__batch_00)에 있다(R__analytics_10 머리말).
-- 규칙: 판정은 한국 날짜 하루 단위라 오늘·어제 줄만 있으면 된다(자정 직전 신호를 자정 직후에 몰아 쓰면 어제 줄로 판정한다). 그보다 앞선 날의 줄을 지운다(2일 보관).
--       설정과 상관없이 돈다(끈 뒤에도 남은 표시는 지워야 한다 — 빈 표면 바로 SKIP). 집계(analytics_visit_hourly)는 지우지 않는다.
-- 기본 실행: 매일 00:35 (한국시간). 설정값(batch_params): chunk_size(5000) · max_per_run(1000000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT.
-- 호출: CALL batch_analytics_visit_mark_cleanup();

CREATE OR REPLACE PROCEDURE batch_analytics_visit_mark_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'analytics-visit-mark-cleanup';
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 1000000, 1000, 100000000);
    v_cutoff date;
    v_n      bigint;
    v_total  bigint := 0;
    v_err    text;
    v_state  text;
    v_msg    text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_cutoff := (now() AT TIME ZONE 'Asia/Seoul')::date - 1; -- 남길 첫 날 = 어제
    LOOP
        BEGIN
            -- 기본 키(day, visitor_hash) 앞부분 범위
            DELETE FROM analytics_visit_marks
             WHERE (day, visitor_hash) IN (SELECT day, visitor_hash FROM analytics_visit_marks
                                            WHERE day < v_cutoff
                                            LIMIT least(v_chunk, v_max - v_total));
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_n = 0 OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.analyticsVisitMarkCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text) END,
        'batch.result.analyticsVisitMarkCleanup.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('analytics-visit-mark-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('analytics-visit-mark-cleanup', 'max_per_run', 1000000);
SELECT batch_define('analytics-visit-mark-cleanup', 'batch_analytics_visit_mark_cleanup', 0, 35);
