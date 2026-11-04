-- DB 배치 `analytics-forget-requests` — 방문 기록 삭제 요청(analytics_forget_requests, analytics V4) 처리. docs/features/site-analytics-contract.md 「2단계 — 정보주체 요청」.
-- analytics 스트림의 반복 마이그레이션 — 공통 함수는 메인 DB(shared R__batch_00)에 있다(R__analytics_10 머리말).
-- 규칙:
--   ① 대기 요청을 한 번에 모은다 — 받은 지 delay_minutes(2분) 넘은 것만: 서버가 1분마다 몰아 쓰므로, 요청 직전의 신호가 아직 메모리에 있다가 처리 뒤에 써지는 일이 없게.
--      한 번에 requests_per_run(500) 개까지(남은 것은 다음 5분).
--   ② 그 해시들로 한 번씩 지운다: 원본(analytics_page_views)은 월 조각마다 한 문장(조각 하나를 한 번 훑는다), 「그 날 셌다」 표시(analytics_visit_marks)·지금 접속(analytics_visit_active)은 한 문장씩.
--      성능: 원본에 visitor_hash 인덱스를 두지 않는다 — 두면 1분 몰아쓰기의 원본 넣기마다 인덱스 쓰기가 늘어난다(원본은 가장 큰 표). 대신 대기 요청이 있을 때만
--      visitor_hash = ANY(모은 해시) 한 번으로 조각을 훑는다(요청이 몇 개든 조각마다 한 번). 판정 표시는 기본 키 (day, visitor_hash) 의 뒤 칸이라 PostgreSQL 18 의
--      B-tree 건너뛰기 검색이 날(오늘·어제 — 2일 보관)마다 내려간다. 지금 접속은 기본 키(visitor_hash).
--      합산 표(analytics_visit_hourly · *_daily)는 사람을 가리키지 않아 지우지 않는다. 판정 표시를 지웠으므로 그 방문자가 오늘 다시 오면 방문자 1 로 다시 셀 수 있다(허용).
--   ③ 지운 줄 수를 요청별로 더해 두고(RETURNING 을 해시로 묶어) 끝나면 DONE · processed_at 을 적고 해시를 비운다. 중간에 실패하면 요청은 대기로 남아 다음 실행이 이어서 지운다
--      (이미 지운 줄은 다시 세지 않는다 — 누적 값이 맞다).
-- 기본 실행: 5분마다(*/5). 설정값(batch_params): requests_per_run(500, 1~10000) · delay_minutes(2, 1~60).
-- 결과: 처리 건수 = 처리한 요청 수, 0 이면 SKIP. 단계마다 COMMIT. 실패하면 사유를 남기고 FAIL(요청은 대기 그대로).
-- 호출: CALL batch_analytics_forget_requests();

CREATE OR REPLACE PROCEDURE batch_analytics_forget_requests(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'analytics-forget-requests';
    v_limit  bigint := batch_param(c_job, 'requests_per_run', 500, 1, 10000);
    v_delay  bigint := batch_param(c_job, 'delay_minutes', 2, 1, 60);
    v_ids    bigint[];
    v_hashes text[];
    v_parts  regclass[];
    v_part   regclass;
    v_done   bigint := 0;
    v_rows   bigint := 0;
    v_err    text;
    v_state  text;
    v_msg    text;
    -- 지운 줄을 해시로 묶어 그 요청의 deleted_rows 에 더한다($1 = 해시들, $2 = 요청 id 들)
    c_count  CONSTANT text := 'WITH d AS (DELETE FROM %s WHERE visitor_hash = ANY($1) RETURNING visitor_hash), '
                           || 'c AS (SELECT visitor_hash, count(*) AS n FROM d GROUP BY visitor_hash) '
                           || 'UPDATE analytics_forget_requests r SET deleted_rows = coalesce(r.deleted_rows, 0) + c.n '
                           || 'FROM c WHERE r.id = ANY($2) AND r.visitor_hash = c.visitor_hash';
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    -- ① 대기 요청 모으기(오래된 차례)
    SELECT array_agg(q.id ORDER BY q.received_at, q.id), array_agg(q.visitor_hash::text ORDER BY q.received_at, q.id)
      INTO v_ids, v_hashes
      FROM (SELECT id, received_at, visitor_hash FROM analytics_forget_requests
             WHERE status = 'PENDING' AND received_at < now() - make_interval(mins => v_delay::int)
             ORDER BY received_at, id
             LIMIT v_limit) q;

    IF v_ids IS NOT NULL THEN
        -- ② 원본 — 월 조각마다 한 문장
        SELECT coalesce(array_agg(i.inhrelid::regclass ORDER BY i.inhrelid), '{}')
          INTO v_parts
          FROM pg_inherits i WHERE i.inhparent = 'analytics_page_views'::regclass;
        FOREACH v_part IN ARRAY v_parts LOOP
            BEGIN
                EXECUTE format(c_count, v_part) USING v_hashes, v_ids;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
            END;
            COMMIT;
            EXIT WHEN v_err IS NOT NULL;
        END LOOP;

        -- ② 「그 날 셌다」 표시 · 지금 접속
        IF v_err IS NULL THEN
            BEGIN
                EXECUTE format(c_count, 'analytics_visit_marks') USING v_hashes, v_ids;
                EXECUTE format(c_count, 'analytics_visit_active') USING v_hashes, v_ids;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
            END;
            COMMIT;
        END IF;

        -- ③ 끝맺기 — 해시를 비운다
        IF v_err IS NULL THEN
            BEGIN
                SELECT coalesce(sum(deleted_rows), 0) INTO v_rows FROM analytics_forget_requests WHERE id = ANY(v_ids) AND status = 'PENDING';
                UPDATE analytics_forget_requests
                   SET status = 'DONE', processed_at = now(), deleted_rows = coalesce(deleted_rows, 0), visitor_hash = NULL
                 WHERE id = ANY(v_ids) AND status = 'PENDING';
                GET DIAGNOSTICS v_done = ROW_COUNT;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                v_done := 0;
                v_rows := 0;
            END;
            COMMIT;
        END IF;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_done, v_err,
        'batch.result.analyticsForgetRequests', batch_args(v_done::text, v_rows::text),
        'batch.result.analyticsForgetRequests.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('analytics-forget-requests', 'requests_per_run', 500);
SELECT batch_param_seed('analytics-forget-requests', 'delay_minutes', 2);
SELECT batch_define_every_minutes('analytics-forget-requests', 'batch_analytics_forget_requests', 5);
