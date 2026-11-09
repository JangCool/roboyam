-- DB 배치 `cache-log-cleanup` — 캐시 관리 기록 정리(shared V175 — docs/features/cache-admin-contract.md 「서버 구현 결과」 남은 것).
-- 번호: shared R__batch_41 다음.
-- 규칙(한 덩어리에서 위에서 아래로):
--   ① 갱신 기록(cache_refresh_logs) — 요청한 지 refresh_log_days(기본 90, 7~3650)가 지난 줄.
--   ② 설정 변경 이력(cache_setting_changes — TTL · 캐시 켬/끔 · 전체 켬/끔) — 바꾼 지 setting_change_days(기본 365, 30~3650)가 지난 줄.
--      같은 변경은 감사 기록(audit_logs — CACHE_TTL_CHANGE 등)에도 남고, 그 보관은 audit-log-retention 이 맡는다.
--   ③ 서버별 상태 보고(cache_node_states) — 마지막 보고가 node_days(기본 7, 1~365)보다 오래된 줄(사라진 서버). 살아 있는 서버의 동기화(CacheSync)도 1시간 지난 줄을 지우지만,
--      서버가 모두 멈췄거나 보고가 끊긴 채로 남은 줄은 이 배치가 지운다.
--   캐시 설정(cache_settings · cache_global_settings)은 지우지 않는다.
-- 기본 실행: 매일 03:25 (한국시간 — batch_daily_cron 이 환산, 운영자 초대 정리 03:20 과 감사 기록 보관 03:30 사이).
-- 설정값(batch_params): refresh_log_days(90) · setting_change_days(365) · node_days(7) · chunk_size(5000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 행 수 합, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_cache_log_cleanup();  (「지금 실행」은 CALL batch_cache_log_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_cache_log_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job          CONSTANT text := 'cache-log-cleanup';
    v_log_days     bigint := batch_param(c_job, 'refresh_log_days', 90, 7, 3650);
    v_change_days  bigint := batch_param(c_job, 'setting_change_days', 365, 30, 3650);
    v_node_days    bigint := batch_param(c_job, 'node_days', 7, 1, 365);
    v_chunk        bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max          bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_now          timestamptz;
    v_n            bigint;
    v_round        bigint;
    v_logs         bigint := 0;
    v_changes      bigint := 0;
    v_nodes        bigint := 0;
    v_total        bigint := 0;
    v_err          text;
    v_state        text;
    v_msg          text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_now := now();
    LOOP
        BEGIN
            v_round := 0;

            -- ① 갱신 기록
            DELETE FROM cache_refresh_logs
             WHERE id IN (SELECT id FROM cache_refresh_logs
                           WHERE requested_at < v_now - make_interval(days => v_log_days::int)
                           LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;
            v_logs := v_logs + v_n;

            -- ② 설정 변경 이력
            DELETE FROM cache_setting_changes
             WHERE id IN (SELECT id FROM cache_setting_changes
                           WHERE changed_at < v_now - make_interval(days => v_change_days::int)
                           LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;
            v_changes := v_changes + v_n;

            -- ③ 보고가 끊긴 서버의 상태 줄(cache_node_states_reported_idx)
            DELETE FROM cache_node_states
             WHERE (node_id, cache_key) IN (SELECT node_id, cache_key FROM cache_node_states
                                             WHERE reported_at < v_now - make_interval(days => v_node_days::int)
                                             LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;
            v_nodes := v_nodes + v_n;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_round;
        COMMIT;
        EXIT WHEN v_round = 0 OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.cacheLogCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text)
             ELSE batch_args(v_total::text, v_logs::text, v_changes::text, v_nodes::text, v_log_days::text, v_change_days::text, v_node_days::text) END,
        'batch.result.cacheLogCleanup.none', batch_args(v_log_days::text, v_change_days::text, v_node_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('cache-log-cleanup', 'refresh_log_days', 90);
SELECT batch_param_seed('cache-log-cleanup', 'setting_change_days', 365);
SELECT batch_param_seed('cache-log-cleanup', 'node_days', 7);
SELECT batch_param_seed('cache-log-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('cache-log-cleanup', 'max_per_run', 100000);
SELECT batch_define('cache-log-cleanup', 'batch_cache_log_cleanup', 3, 25);
