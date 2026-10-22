-- DB 배치 `board-post-partitions` — 글(posts) 월 조각 유지. docs/features/board-contract.md 「데이터 구조」「배치」
-- 규칙: posts 는 id(Snowflake) 범위로 달마다 조각을 나눈다. 지난달~6개월 뒤(UTC 달 경계)까지의 조각이 없으면 만든다 — 조각 없는 달의 글은 저장이 실패한다.
--       조각은 떼거나 지우지 않는다. 서버 여러 대·배치가 동시에 불러도 안전하게 어드바이저리 트랜잭션 잠금 아래에서 만들고, 이미 있는 조각은 건너뛴다.
-- 부르는 곳: 이 파일 끝(마이그레이션 때 한 번) · 앱이 뜰 때(api·admin 모두 — BoardPartitionBootstrap) · 이 배치(매일 00:20 KST).
-- Snowflake 비트 배치: (UTC ms - 1767225600000) << 22 — SnowflakeIdGenerator.EPOCH_MS · NODE_BITS(10) + SEQUENCE_BITS(12) 와 반드시 같아야 한다.
-- 결과: 처리 건수 = 새로 만든 조각 수, 0 이면 SKIP.
-- 호출: CALL batch_board_post_partitions();  (「지금 실행」은 CALL batch_board_post_partitions('MANUAL', 운영자ID, NULL))

-- 시각 → 그 시각의 가장 작은 Snowflake ID
CREATE OR REPLACE FUNCTION board_snowflake_floor(ts timestamptz) RETURNS bigint
LANGUAGE sql IMMUTABLE AS
$$ SELECT ((floor(extract(epoch FROM ts) * 1000)::bigint - 1767225600000) << 22) $$;

-- 지난달 ~ p_months_ahead 개월 뒤의 월 조각을 만든다. 돌려주는 값 = 새로 만든 조각 수
CREATE OR REPLACE FUNCTION board_ensure_post_partitions(p_months_ahead int DEFAULT 6) RETURNS int
LANGUAGE plpgsql AS
$fn$
DECLARE
    v_month   date;
    v_lo      bigint;
    v_hi      bigint;
    v_part    text;
    v_created int := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('board_post_partitions'));
    FOR i IN -1..greatest(p_months_ahead, 0) LOOP
        v_month := (date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => i))::date;
        v_lo    := board_snowflake_floor(v_month::timestamp AT TIME ZONE 'UTC');
        v_hi    := board_snowflake_floor((v_month + interval '1 month')::timestamp AT TIME ZONE 'UTC');
        v_part  := format('posts_%s', to_char(v_month, 'YYYY_MM'));
        IF to_regclass(v_part) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF posts FOR VALUES FROM (%s) TO (%s)', v_part, v_lo, v_hi);
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_board_post_partitions(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'board-post-partitions';
    v_ahead  bigint := batch_param(c_job, 'months_ahead', 6, 1, 24);
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

    BEGIN
        v_total := board_ensure_post_partitions(v_ahead::int);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
        v_err := batch_error_text(v_state, v_msg);
    END;
    COMMIT;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        'batch.result.boardPostPartitions', batch_args(v_total::text, v_ahead::text),
        'batch.result.boardPostPartitions.none', batch_args(v_ahead::text));
    COMMIT;
END
$proc$;

SELECT board_ensure_post_partitions(6);
SELECT batch_param_seed('board-post-partitions', 'months_ahead', 6);
SELECT batch_define('board-post-partitions', 'batch_board_post_partitions', 0, 20);
