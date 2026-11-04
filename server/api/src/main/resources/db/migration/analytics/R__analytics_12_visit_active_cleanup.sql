-- DB 배치 `analytics-visit-active-cleanup` — 「지금 접속」 표(analytics_visit_active, analytics V1) 정리. docs/features/site-analytics-contract.md 「주기 작업」.
-- analytics 스트림의 반복 마이그레이션 — 공통 함수는 메인 DB(shared R__batch_00)에 있다(R__analytics_10 머리말).
-- 규칙: 「지금 접속」은 last_seen 5분 안만 세므로 10분 넘게 신호가 없는 방문자 줄을 지운다(표를 방문자 수만큼 키우지 않게). ix_analytics_visit_active_seen(last_seen) 범위.
--       설정과 상관없이 돈다(빈 표면 바로 SKIP).
-- 기본 실행: 10분마다(*/10). 설정값(batch_params): keep_minutes(10, 6~120) · chunk_size(5000) · max_per_run(1000000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT.
-- 호출: CALL batch_analytics_visit_active_cleanup();

CREATE OR REPLACE PROCEDURE batch_analytics_visit_active_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'analytics-visit-active-cleanup';
    v_keep   bigint := batch_param(c_job, 'keep_minutes', 10, 6, 120);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 1000000, 1000, 100000000);
    v_cutoff timestamptz;
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

    v_cutoff := now() - make_interval(mins => v_keep::int);
    LOOP
        BEGIN
            -- 지우는 사이에 새 신호로 last_seen 이 늘어난 줄은 남는다(조건을 다시 본다)
            DELETE FROM analytics_visit_active
             WHERE visitor_hash IN (SELECT visitor_hash FROM analytics_visit_active
                                     WHERE last_seen < v_cutoff
                                     LIMIT least(v_chunk, v_max - v_total))
               AND last_seen < v_cutoff;
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.analyticsVisitActiveCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_keep::text) END,
        'batch.result.analyticsVisitActiveCleanup.none', batch_args(v_keep::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('analytics-visit-active-cleanup', 'keep_minutes', 10);
SELECT batch_param_seed('analytics-visit-active-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('analytics-visit-active-cleanup', 'max_per_run', 1000000);
SELECT batch_define_every_minutes('analytics-visit-active-cleanup', 'batch_analytics_visit_active_cleanup', 10);
