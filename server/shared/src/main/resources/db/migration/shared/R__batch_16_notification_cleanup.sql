-- DB 배치 `notification-cleanup` — 관리자 알림(admin_notifications) 정리. docs/features/notification-contract.md 「보관·정리」
-- 규칙 (위에서 아래로 차례대로, 앞 단계가 지운 뒤의 상태로 다음 단계를 센다):
--   1) 읽은 알림: 읽은 지(read_at) read_retention_days 일(기본 30일)이 지난 것을 지운다.
--   2) 안 읽은 알림: 만든 지(created_at) unread_retention_days 일(기본 90일)이 지난 것을 지운다.
--   3) 운영자당 최대 per_operator_cap 건(기본 500건): 넘는 몫을 지운다. 지우는 차례는 「읽은 것 중 오래된 것부터」이고, 읽은 것을 다 지우고도 넘으면 안 읽은 것 중 오래된 것부터다
--      (남기는 차례 = 안 읽은 것 최근순, 그다음 읽은 것 최근순).
-- 기본 실행: 매일 03:50 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 5칸 식(분 시 일 월 요일).
-- 설정값(batch_params): read_retention_days(기본 30) · unread_retention_days(기본 90) · per_operator_cap(기본 500) · chunk_size(기본 5000) · max_per_run(기본 100000).
-- 결과: 처리 건수 = 지운 행 수(세 단계 합), 0 이면 SKIP. 덩어리마다 COMMIT. 한 번 실행 상한에 닿으면 나머지는 다음 실행이 이어서 지운다. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_notification_cleanup();  (「지금 실행」은 CALL batch_notification_cleanup('MANUAL', 운영자ID, NULL))
-- 이름 규칙: 프로시저 이름 = 'batch_' + 배치 키의 하이픈을 밑줄로 바꾼 것.

CREATE OR REPLACE PROCEDURE batch_notification_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job          CONSTANT text := 'notification-cleanup';
    v_read_days    bigint := batch_param(c_job, 'read_retention_days', 30, 1, 3650);
    v_unread_days  bigint := batch_param(c_job, 'unread_retention_days', 90, 1, 3650);
    v_cap          bigint := batch_param(c_job, 'per_operator_cap', 500, 10, 100000);
    v_chunk        bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max          bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_read_cutoff   timestamptz;
    v_unread_cutoff timestamptz;
    v_phase        int;
    v_n            bigint;
    v_read         bigint := 0;
    v_unread       bigint := 0;
    v_capped       bigint := 0;
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

    v_read_cutoff := now() - make_interval(days => v_read_days::int);
    v_unread_cutoff := now() - make_interval(days => v_unread_days::int);

    FOR v_phase IN 1..3 LOOP
        LOOP
            BEGIN
                IF v_phase = 1 THEN
                    DELETE FROM admin_notifications
                     WHERE id IN (SELECT id FROM admin_notifications
                                   WHERE read_at IS NOT NULL AND read_at < v_read_cutoff
                                   ORDER BY read_at LIMIT least(v_chunk, v_max - v_total));
                ELSIF v_phase = 2 THEN
                    DELETE FROM admin_notifications
                     WHERE id IN (SELECT id FROM admin_notifications
                                   WHERE read_at IS NULL AND created_at < v_unread_cutoff
                                   ORDER BY created_at LIMIT least(v_chunk, v_max - v_total));
                ELSE
                    -- 한도를 넘는 운영자만 골라(색인 operator_id) 남기는 차례로 줄을 세우고 넘는 줄을 지운다
                    DELETE FROM admin_notifications
                     WHERE id IN (SELECT ranked.id
                                    FROM (SELECT n.id,
                                                 row_number() OVER (PARTITION BY n.operator_id ORDER BY (n.read_at IS NULL) DESC, n.id DESC) AS rn
                                            FROM admin_notifications n
                                           WHERE n.operator_id IN (SELECT operator_id FROM admin_notifications GROUP BY operator_id HAVING count(*) > v_cap)) ranked
                                   WHERE ranked.rn > v_cap
                                   LIMIT least(v_chunk, v_max - v_total));
                END IF;
                GET DIAGNOSTICS v_n = ROW_COUNT;
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                EXIT;
            END;
            v_total := v_total + v_n;
            IF v_phase = 1 THEN
                v_read := v_read + v_n;
            ELSIF v_phase = 2 THEN
                v_unread := v_unread + v_n;
            ELSE
                v_capped := v_capped + v_n;
            END IF;
            COMMIT;
            EXIT WHEN v_n = 0 OR v_total >= v_max;
        END LOOP;
        EXIT WHEN v_err IS NOT NULL OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.notificationCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_read::text, v_unread::text, v_capped::text) END,
        'batch.result.notificationCleanup.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('notification-cleanup', 'read_retention_days', 30);
SELECT batch_param_seed('notification-cleanup', 'unread_retention_days', 90);
SELECT batch_param_seed('notification-cleanup', 'per_operator_cap', 500);
SELECT batch_param_seed('notification-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('notification-cleanup', 'max_per_run', 100000);
SELECT batch_define('notification-cleanup', 'batch_notification_cleanup', 3, 50);
