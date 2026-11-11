-- DB 배치 `ad-view-mark-cleanup` — 공개 광고 하루 1번 판정 표(ad_view_marks — V118, V194 부터 한국 날짜 하루 범위 파티션) 조각 유지.
-- docs/features/ad-contract.md 「공개 노출 목업」 확정 결정(2026-11-01) · docs/features/site-analytics-contract.md 「3단계」 결정 ⑥(날짜 파티션 + DROP, 2026-11-11).
-- 번호: develop 의 반복 마이그레이션 shared R__batch_32 · admin R__batch_30 다음(배치 키·기록은 V118 때 그대로 — 배치 화면의 실행 기록이 이어진다).
-- 규칙:
--   ① 조각 만들기 — 오늘·내일·모레(한국 날짜) 조각이 없으면 만든다(자정에 조각이 없어 노출·클릭 기록이 실패하지 않게 미리). 쓰는 쪽(api PublicAdStore#count)은 조각이 없다는
--      오류(23514)를 받으면 같은 함수를 그 날로 한 번 부르고 다시 쓴다. 여러 서버·배치가 동시에 불러도 어드바이저리 트랜잭션 잠금 아래에서 한 번만 만든다.
--   ② 지난 조각 지우기 — 판정은 한국 날짜 하루 단위다. 한국 날짜가 retention_days 일(기본 2 — 오늘·어제를 남김, 2~30) 넘게 지난 조각을 통째로 DROP 한다
--      (줄 DELETE 를 두지 않는다 — 결정 ⑥). 부모 표 잠금을 오래 기다리지 않는다(lock_timeout 5초 — 노출이 몰리면 다음 날 다시).
--   집계(ad_daily_stats)는 지우지 않는다. 판정 표에는 원본 IP·User-Agent·회원 ID 가 없다(날짜를 섞은 HMAC 64자뿐).
-- 기본 실행: 매일 04:15 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_days(2) — 조각 단위로 쓴다.
-- 결과: 처리 건수 = 만든 조각 + 지운 조각, 0 이면 SKIP. 단계마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_ad_view_mark_cleanup();  (「지금 실행」은 CALL batch_ad_view_mark_cleanup('MANUAL', 운영자ID, NULL))

-- 오늘 ~ 모레(한국 날짜) 조각 + p_day(주면 — 쓰는 쪽이 받은 날) 조각을 만든다. 돌려주는 값 = 새로 만든 조각 수. 조각 이름 ad_view_marks_YYYYMMDD
CREATE OR REPLACE FUNCTION ad_ensure_view_mark_partitions(p_day date DEFAULT NULL) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_today   date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_days    date[];
    v_day     date;
    v_part    text;
    v_created int := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('ad_view_mark_partitions'));
    v_days := ARRAY[v_today, v_today + 1, v_today + 2];
    IF p_day IS NOT NULL AND NOT p_day = ANY(v_days) THEN
        v_days := v_days || p_day;
    END IF;
    FOREACH v_day IN ARRAY v_days LOOP
        v_part := 'ad_view_marks_' || to_char(v_day, 'YYYYMMDD');
        IF to_regclass(v_part) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF ad_view_marks FOR VALUES FROM (%L) TO (%L)', v_part, v_day, v_day + 1);
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END
$fn$;

-- 날이 p_keep_from 앞인 조각을 지운다. 돌려주는 값 = 지운 조각 수. 이름(ad_view_marks_YYYYMMDD)으로 그 조각의 날을 읽는다
CREATE OR REPLACE FUNCTION ad_drop_old_view_mark_partitions(p_keep_from date) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_part    text;
    v_dropped int := 0;
BEGIN
    FOR v_part IN SELECT c.relname
                    FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                   WHERE i.inhparent = 'ad_view_marks'::regclass AND c.relname ~ '^ad_view_marks_[0-9]{8}$'
                   ORDER BY c.relname LOOP
        IF to_date(substr(v_part, length('ad_view_marks_') + 1), 'YYYYMMDD') < p_keep_from THEN
            EXECUTE format('DROP TABLE %I', v_part);
            v_dropped := v_dropped + 1;
        END IF;
    END LOOP;
    RETURN v_dropped;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_ad_view_mark_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'ad-view-mark-cleanup';
    v_keep    bigint := batch_param(c_job, 'retention_days', 2, 2, 30);
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
        v_created := ad_ensure_view_mark_partitions(NULL);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
        v_err := batch_error_text(v_state, v_msg);
    END;
    COMMIT;

    -- ② 보관 일수보다 오래된 조각 통째로 지우기 — 남길 첫 날(한국 날짜)은 기본 2 면 어제
    IF v_err IS NULL THEN
        BEGIN
            SET LOCAL lock_timeout = '5s';
            v_dropped := ad_drop_old_view_mark_partitions((now() AT TIME ZONE 'Asia/Seoul')::date - (v_keep::int - 1));
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_created + v_dropped, v_err,
        'batch.result.adViewMarkPartitions', batch_args(v_created::text, v_dropped::text, v_keep::text),
        'batch.result.adViewMarkPartitions.none', batch_args(v_keep::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('ad-view-mark-cleanup', 'retention_days', 2);
-- 마이그레이션 때 조각을 만들어 둔다(V194 가 만든 뒤 날이 바뀌었을 수 있다 — 빈 조각이라 비용이 없다)
SELECT ad_ensure_view_mark_partitions(NULL);
SELECT batch_define('ad-view-mark-cleanup', 'batch_ad_view_mark_cleanup', 4, 15);
