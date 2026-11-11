-- DB 배치 `deal-click-partitions` — 핫딜 구매 링크 클릭 판정 표(deal_click_daily — V189 부터 한국 날짜 하루 범위 파티션) 조각 유지.
-- docs/features/site-analytics-contract.md 「3단계」 결정 ⑤⑥ · hotdeal-contract.md(클릭 수는 post_deals.click_count — 판정 표는 「하루 같은 방문자·같은 글 1번」 거르기뿐).
-- 번호: shared R__batch_42 다음.
-- 규칙:
--   ① 조각 만들기 — 어제·오늘·내일·모레(한국 날짜) 조각이 없으면 만든다(자정에 조각이 없어 클릭 기록이 실패하지 않게 미리). 쓰는 쪽(DealQueries#recordClick)은 조각이 없다는
--      오류(23514)를 받으면 같은 함수를 그 날로 한 번 부르고 다시 쓴다. 여러 서버·배치가 동시에 불러도 어드바이저리 트랜잭션 잠금 아래에서 한 번만 만든다.
--   ② 지난 조각 지우기 — 판정은 한국 날짜 하루 단위라 오늘·어제 조각만 있으면 된다(자정 직전 클릭을 자정 직후에 다시 눌러도 어제 조각이 판정한다). 그보다 앞선 날의 조각을
--      통째로 DROP 한다(큰 DELETE 를 두지 않는다 — 결정 ⑥). 부모 표 잠금을 오래 기다리지 않는다(lock_timeout 5초 — 클릭이 몰리면 다음 날 다시).
--   설정과 상관없이 돈다(통계 수집 스위치와 무관한 핫딜 기능이다).
-- 기본 실행: 매일 00:50 (한국시간 — batch_daily_cron 이 환산).
-- 결과: 처리 건수 = 만든 조각 + 지운 조각, 0 이면 SKIP. 단계마다 COMMIT. 실패하면 그때까지의 건수와 사유를 남기고 FAIL.
-- 호출: CALL batch_deal_click_partitions();  (「지금 실행」은 CALL batch_deal_click_partitions('MANUAL', 운영자ID, NULL))

-- 어제 ~ 모레(한국 날짜) 조각 + p_day(주면 — 쓰는 쪽이 받은 날) 조각을 만든다. 돌려주는 값 = 새로 만든 조각 수. 조각 이름 deal_click_daily_YYYYMMDD
CREATE OR REPLACE FUNCTION deal_ensure_click_partitions(p_day date DEFAULT NULL) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_today   date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_days    date[];
    v_day     date;
    v_part    text;
    v_created int := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('deal_click_partitions'));
    v_days := ARRAY[v_today - 1, v_today, v_today + 1, v_today + 2];
    IF p_day IS NOT NULL AND NOT p_day = ANY(v_days) THEN
        v_days := v_days || p_day;
    END IF;
    FOREACH v_day IN ARRAY v_days LOOP
        v_part := 'deal_click_daily_' || to_char(v_day, 'YYYYMMDD');
        IF to_regclass(v_part) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF deal_click_daily FOR VALUES FROM (%L) TO (%L)', v_part, v_day, v_day + 1);
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END
$fn$;

-- 날이 p_keep_from 앞인 조각을 지운다. 돌려주는 값 = 지운 조각 수. 이름(deal_click_daily_YYYYMMDD)으로 그 조각의 날을 읽는다
CREATE OR REPLACE FUNCTION deal_drop_old_click_partitions(p_keep_from date) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_part    text;
    v_dropped int := 0;
BEGIN
    FOR v_part IN SELECT c.relname
                    FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                   WHERE i.inhparent = 'deal_click_daily'::regclass AND c.relname ~ '^deal_click_daily_[0-9]{8}$'
                   ORDER BY c.relname LOOP
        IF to_date(substr(v_part, length('deal_click_daily_') + 1), 'YYYYMMDD') < p_keep_from THEN
            EXECUTE format('DROP TABLE %I', v_part);
            v_dropped := v_dropped + 1;
        END IF;
    END LOOP;
    RETURN v_dropped;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_deal_click_partitions(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'deal-click-partitions';
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
        v_created := deal_ensure_click_partitions(NULL);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
        v_err := batch_error_text(v_state, v_msg);
    END;
    COMMIT;

    -- ② 어제보다 앞선 조각 통째로 지우기
    IF v_err IS NULL THEN
        BEGIN
            SET LOCAL lock_timeout = '5s';
            v_dropped := deal_drop_old_click_partitions((now() AT TIME ZONE 'Asia/Seoul')::date - 1);
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_created + v_dropped, v_err,
        'batch.result.dealClickPartitions', batch_args(v_created::text, v_dropped::text),
        'batch.result.dealClickPartitions.none', NULL);
    COMMIT;
END
$proc$;

-- 마이그레이션 때 조각을 만들어 둔다(V189 가 만든 뒤 날이 바뀌었을 수 있다 — 빈 조각이라 비용이 없다)
SELECT deal_ensure_click_partitions(NULL);
SELECT batch_define('deal-click-partitions', 'batch_deal_click_partitions', 0, 50);
