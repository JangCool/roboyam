-- DB 배치 `analytics-log-cleanup` — 방문 수집 진단 기록과 처리 끝난 삭제 요청 정리(analytics V4). docs/features/site-analytics-contract.md 「2단계」.
-- analytics 스트림의 반복 마이그레이션 — 공통 함수는 메인 DB(shared R__batch_00)에 있다(R__analytics_10 머리말).
-- 규칙(설정과 상관없이 돈다 — 빈 표면 바로 SKIP):
--   ① 수집 진단(diag_keep_days, 기본 7일): 기록 실패 줄(analytics_collector_failures — 기본 키 (failed_at, id) 앞 칸 범위) · 서버별 시간 칸(analytics_collector_hourly — (hour, instance))
--      · 그동안 한 번도 기록하지 않은 서버의 「살아 있음」 줄(analytics_collector_beats — ix_analytics_collector_beats_seen). 관리자 화면은 최근 24시간만 읽는다.
--   ② 처리 끝난 삭제 요청(request_keep_days, 기본 365일): status = DONE 이고 받은 지 그만큼 지난 줄(ix_analytics_forget_requests_received). 대기 줄은 지우지 않는다.
--      처리 끝난 줄에는 해시가 없다(번호 · 받은 시각 · 지운 줄 수 · 처리 시각만).
-- 기본 실행: 매일 00:45 (한국시간). 설정값(batch_params): diag_keep_days(7, 1~90) · request_keep_days(365, 30~3650) · chunk_size(5000) · max_per_run(1000000).
-- 결과: 처리 건수 = 지운 진단 줄 + 지운 요청 줄, 0 이면 SKIP. 덩어리마다 COMMIT.
-- 호출: CALL batch_analytics_log_cleanup();

CREATE OR REPLACE PROCEDURE batch_analytics_log_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job       CONSTANT text := 'analytics-log-cleanup';
    v_diag_days bigint := batch_param(c_job, 'diag_keep_days', 7, 1, 90);
    v_req_days  bigint := batch_param(c_job, 'request_keep_days', 365, 30, 3650);
    v_chunk     bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max       bigint := batch_param(c_job, 'max_per_run', 1000000, 1000, 100000000);
    v_diag_cut  timestamptz;
    v_req_cut   timestamptz;
    v_n         bigint;
    v_diag      bigint := 0;
    v_req       bigint := 0;
    v_step      int := 1;
    v_err       text;
    v_state     text;
    v_msg       text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_diag_cut := now() - make_interval(days => v_diag_days::int);
    v_req_cut := now() - make_interval(days => v_req_days::int);

    -- 단계 1 실패 줄 · 2 서버별 시간 칸 · 3 살아 있음 · 4 처리 끝난 요청 — 덩어리마다 COMMIT, 단계가 비면 다음 단계
    WHILE v_step <= 4 AND v_diag + v_req < v_max LOOP
        BEGIN
            IF v_step = 1 THEN
                DELETE FROM analytics_collector_failures
                 WHERE (failed_at, id) IN (SELECT failed_at, id FROM analytics_collector_failures WHERE failed_at < v_diag_cut
                                     ORDER BY failed_at LIMIT least(v_chunk, v_max - v_diag - v_req));
            ELSIF v_step = 2 THEN
                DELETE FROM analytics_collector_hourly
                 WHERE (hour, instance) IN (SELECT hour, instance FROM analytics_collector_hourly WHERE hour < v_diag_cut
                                             ORDER BY hour LIMIT least(v_chunk, v_max - v_diag - v_req));
            ELSIF v_step = 3 THEN
                -- 지우는 사이 다시 기록한 서버는 남는다(조건을 다시 본다)
                DELETE FROM analytics_collector_beats
                 WHERE instance IN (SELECT instance FROM analytics_collector_beats WHERE last_write_at < v_diag_cut
                                     LIMIT least(v_chunk, v_max - v_diag - v_req))
                   AND last_write_at < v_diag_cut;
            ELSE
                DELETE FROM analytics_forget_requests
                 WHERE id IN (SELECT id FROM analytics_forget_requests WHERE received_at < v_req_cut AND status = 'DONE'
                               ORDER BY received_at, id LIMIT least(v_chunk, v_max - v_diag - v_req))
                   AND status = 'DONE';
            END IF;
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        IF v_step = 4 THEN
            v_req := v_req + v_n;
        ELSE
            v_diag := v_diag + v_n;
        END IF;
        COMMIT;
        IF v_n = 0 THEN
            v_step := v_step + 1;
        END IF;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_diag + v_req, v_err,
        CASE WHEN v_diag + v_req >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.analyticsLogCleanup' END,
        CASE WHEN v_diag + v_req >= v_max THEN batch_args((v_diag + v_req)::text, v_max::text) ELSE batch_args(v_diag::text, v_req::text) END,
        'batch.result.analyticsLogCleanup.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('analytics-log-cleanup', 'diag_keep_days', 7);
SELECT batch_param_seed('analytics-log-cleanup', 'request_keep_days', 365);
SELECT batch_param_seed('analytics-log-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('analytics-log-cleanup', 'max_per_run', 1000000);
SELECT batch_define('analytics-log-cleanup', 'batch_analytics_log_cleanup', 0, 45);
