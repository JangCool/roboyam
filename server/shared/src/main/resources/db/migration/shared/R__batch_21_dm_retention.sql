-- DB 배치 `dm-retention` — 쪽지 1년 보관 정리. docs/features/member-social-contract.md 「쪽지」(1년(365일) 지난 쪽지 자동 삭제)
-- 규칙 (차례대로):
--   1) 마지막 쪽지(dm_threads.last_message_at)가 retention_days 일(기본 365일)보다 오래된 대화를 통째로 지운다 — 쪽지가 하나도 안 남는 대화다(쪽지·두 사람의 쪽지함 줄은 함께 지워진다).
--   2) 아직 남는 대화의 쪽지 중 보낸 지(created_at) retention_days 일이 지난 것을 지운다(대화의 마지막 쪽지는 더 최근이라 남는다).
--   「신고된 대화는 처리가 끝날 때까지 보관」은 신고 대상에 쪽지가 생길 때 더한다(지금 로보얌 신고 대상에 쪽지가 없다) — 그때 두 단계 모두 그 대화를 건너뛰게 한다.
-- 기본 실행: 매일 04:20 (한국시간으로 적고 batch_daily_cron 이 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다).
-- 설정값(batch_params): retention_days(기본 365 — 앱의 rules.retentionDays 와 같은 값) · chunk_size(기본 5000) · max_per_run(기본 100000).
-- 결과: 처리 건수 = 지운 대화 수 + 지운 쪽지 수(1단계에서 대화와 함께 지워진 쪽지는 세지 않는다), 0 이면 SKIP. 덩어리마다 COMMIT.
-- 호출: CALL batch_dm_retention();  (「지금 실행」은 CALL batch_dm_retention('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_dm_retention(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'dm-retention';
    v_days    bigint := batch_param(c_job, 'retention_days', 365, 30, 3650);
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_cutoff  timestamptz;
    v_phase   int := 1;
    v_n       bigint;
    v_threads bigint := 0;
    v_msgs    bigint := 0;
    v_total   bigint := 0;
    v_err     text;
    v_state   text;
    v_msg     text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_cutoff := now() - make_interval(days => v_days::int);
    LOOP
        BEGIN
            IF v_phase = 1 THEN
                -- 마지막 쪽지가 기준보다 오래된 대화 — 그 사이 새 쪽지가 오면(보내기가 행을 잠그고 last_message_at 을 올린다) 다시 판정돼 건너뛴다
                DELETE FROM dm_threads
                 WHERE id IN (SELECT id FROM dm_threads
                               WHERE last_message_at < v_cutoff
                               ORDER BY last_message_at LIMIT least(v_chunk, v_max - v_total))
                   AND last_message_at < v_cutoff;
            ELSE
                DELETE FROM dm_messages
                 WHERE id IN (SELECT id FROM dm_messages
                               WHERE created_at < v_cutoff
                               ORDER BY created_at LIMIT least(v_chunk, v_max - v_total));
            END IF;
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        IF v_phase = 1 THEN
            v_threads := v_threads + v_n;
        ELSE
            v_msgs := v_msgs + v_n;
        END IF;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_total >= v_max;
        IF v_n = 0 THEN
            EXIT WHEN v_phase = 2;
            v_phase := 2;
        END IF;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.dmRetention' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_threads::text, v_msgs::text, v_days::text) END,
        'batch.result.dmRetention.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('dm-retention', 'retention_days', 365);
SELECT batch_param_seed('dm-retention', 'chunk_size', 5000);
SELECT batch_param_seed('dm-retention', 'max_per_run', 100000);
SELECT batch_define('dm-retention', 'batch_dm_retention', 4, 20);
