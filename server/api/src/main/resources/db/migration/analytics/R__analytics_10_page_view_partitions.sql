-- DB 배치 `analytics-page-view-partitions` — 방문 원본(analytics_page_views, analytics V1) 월 조각 유지 + 보관 기간 지난 달 조각 지우기.
-- docs/features/site-analytics-contract.md 「주기 작업」·「3단계 결정 ①」(원본 보관 = 달 단위, 기본 2개월).
-- analytics 스트림의 반복 마이그레이션이다. 공통 함수(batch_run_begin · batch_define …, shared R__batch_00)와 설정 표(stats_collection_settings, shared V133·V189)는 메인 DB 에 있다 —
-- 지금 분석 DB = 메인 DB 라 그대로 쓴다(분석 DB 를 따로 두게 되면 이 파일·설정 읽기를 그 DB 에 맞게 옮겨야 한다 — 계약 「미확정」).
-- 규칙:
--   ① 조각 만들기 — 설정 「방문 수집」(enabled)과 「원본 기록」(raw_events)이 모두 켜져 있을 때만: 이번 달·다음 달(한국 달 경계)의 조각이 없으면 만든다.
--      켤 때 바로 쓸 수 있게 관리자 저장(원본 기록을 켬)과 이 파일 끝(마이그레이션 때)도 같은 함수를 부르고, 쓰는 쪽(api)은 조각이 없다는 오류를 받으면 한 번 만들고 다시 쓴다.
--   ② 지우기 — 설정과 상관없이 돈다(끈 뒤에도 남은 원본은 보관 기간대로 지워야 한다 — 빈 표면 바로 끝난다). 보관 = stats_collection_settings.raw_retention_months
--      (V189 — 2 · 3 · 6 · 12개월, 이번 달 포함, 그 밖이면 2): 「이번 달부터 n번째 앞 달」보다 오래된 달 조각을 통째로 지운다(DROP). 줄 DELETE 는 없다 — 일 단위(예: 60일)는
--      31일 달이 이어지면 지난달 첫날을 놓쳐 버렸다. 2개월이면 이번 달 + 지난달(31~62일, 지난달은 늘 통째로 있다). 옛 칸 raw_retention_days 는 읽지 않는다.
-- 기본 실행: 매일 00:25 (한국시간 — batch_daily_cron 이 환산).
-- 결과: 처리 건수 = 만든 조각 + 지운 조각, 0 이면 SKIP. 단계마다 COMMIT. 실패하면 그때까지의 건수와 사유를 남기고 FAIL.
-- 호출: CALL batch_analytics_page_view_partitions();

-- 이번 달 ~ p_months_ahead 개월 뒤의 조각(한국 달 경계)을 만든다. 돌려주는 값 = 새로 만든 조각 수. 여러 서버·배치가 동시에 불러도 잠금 아래에서 한 번만 만든다
CREATE OR REPLACE FUNCTION analytics_ensure_page_view_partitions(p_months_ahead int DEFAULT 1) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_month   date;
    v_part    text;
    v_created int := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('analytics_page_view_partitions'));
    FOR i IN 0..greatest(p_months_ahead, 0) LOOP
        v_month := (date_trunc('month', now() AT TIME ZONE 'Asia/Seoul') + make_interval(months => i))::date;
        v_part  := format('analytics_page_views_%s', to_char(v_month, 'YYYY_MM'));
        IF to_regclass(v_part) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF analytics_page_views FOR VALUES FROM (%L) TO (%L)', v_part,
                           v_month::timestamp AT TIME ZONE 'Asia/Seoul', (v_month + interval '1 month')::timestamp AT TIME ZONE 'Asia/Seoul');
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END
$fn$;

-- 끝이 p_cutoff 앞(이하)인 조각을 지운다. 돌려주는 값 = 지운 조각 수. 이름(analytics_page_views_YYYY_MM)으로 그 조각의 달을 읽는다
CREATE OR REPLACE FUNCTION analytics_drop_expired_page_view_partitions(p_cutoff timestamptz) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_part    text;
    v_month   date;
    v_dropped int := 0;
BEGIN
    FOR v_part IN SELECT c.relname
                    FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                   WHERE i.inhparent = 'analytics_page_views'::regclass AND c.relname ~ '^analytics_page_views_[0-9]{4}_[0-9]{2}$'
                   ORDER BY c.relname LOOP
        v_month := to_date(substr(v_part, length('analytics_page_views_') + 1), 'YYYY_MM');
        IF ((v_month + interval '1 month')::timestamp AT TIME ZONE 'Asia/Seoul') <= p_cutoff THEN
            EXECUTE format('DROP TABLE %I', v_part);
            v_dropped := v_dropped + 1;
        END IF;
    END LOOP;
    RETURN v_dropped;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_analytics_page_view_partitions(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'analytics-page-view-partitions';
    v_on      boolean;
    v_months  int;
    v_cutoff  timestamptz;
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

    SELECT coalesce(bool_and(s.enabled AND s.raw_events), false),
           coalesce(max(CASE WHEN s.raw_retention_months IN (2, 3, 6, 12) THEN s.raw_retention_months END), 2)
      INTO v_on, v_months
      FROM stats_collection_settings s WHERE s.id = 1;
    -- 남길 첫 달 = 이번 달부터 (n - 1)달 앞(한국 달 경계) — 그 달의 시작보다 끝이 이른(이하) 조각을 지운다
    v_cutoff := (date_trunc('month', now() AT TIME ZONE 'Asia/Seoul') - make_interval(months => v_months - 1)) AT TIME ZONE 'Asia/Seoul';

    -- ① 조각 만들기(켜져 있을 때만)
    IF v_on THEN
        BEGIN
            v_created := analytics_ensure_page_view_partitions(1);
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
    END IF;

    -- ② 기한 앞 조각 통째로 지우기 — 부모 표 잠금을 오래 기다리지 않는다(쓰기가 몰리면 다음 날 다시)
    IF v_err IS NULL THEN
        BEGIN
            SET LOCAL lock_timeout = '5s';
            v_dropped := analytics_drop_expired_page_view_partitions(v_cutoff);
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
    END IF;

    -- (3단계부터 기한에 걸친 조각의 줄 DELETE 는 없다 — 보관이 달 단위라 조각이 통째로 기한 앞이 된다. 지난달은 늘 통째로 있다)

    PERFORM batch_run_close(p_run_id, c_job, v_created + v_dropped, v_err,
        'batch.result.analyticsRawMonths', batch_args(v_created::text, v_dropped::text, v_months::text),
        'batch.result.analyticsRawMonths.none', batch_args(v_months::text));
    COMMIT;
END
$proc$;

-- 마이그레이션 때 이번 달·다음 달 조각을 만들어 둔다(수집을 켜자마자 쓸 수 있게 — 빈 조각이라 비용이 없다)
SELECT analytics_ensure_page_view_partitions(1);
-- chunk_size · max_per_run 은 3단계부터 쓰지 않는다(줄 DELETE 가 없다) — 이미 있는 값은 그대로 둔다(화면의 설정값 목록에 남아도 무해)
SELECT batch_define('analytics-page-view-partitions', 'batch_analytics_page_view_partitions', 0, 25);
