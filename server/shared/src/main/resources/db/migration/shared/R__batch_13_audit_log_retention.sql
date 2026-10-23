-- DB 배치 `audit-log-retention` — 감사 로그(audit_logs) 보관 기간 정리. docs/domain/commerce.md 13-4, docs/features/commerce-foundation-contract.md 「0-5」
-- 규칙: 감사 줄은 남길 때 보관 분류(retention_class)가 붙는다(Java AuditRetention). 분류마다 발생(occurred_at)한 지 그 분류의 보관 일수가 지난 줄을 지운다.
--   GENERAL        일반 운영           retention_days                (기본 365 · 1~3650)
--   PERSONAL_DATA  개인정보 열람·내려받기 personal_data_retention_days  (기본 730 · 하한 365 ~3650)
--   MONEY          돈 관련             money_retention_days          (기본 1825 · 하한 1825 ~3650)
--   값은 관리자 화면(감사 로그 › 보관 기간, 최고 관리자만)이 batch_params 에 쓴다. 하한보다 짧거나 범위 밖이면 batch_param 이 무시하고 기본값을 쓴다 —
--   DB 를 직접 고쳐도 하한보다 일찍 지워지지 않는다.
--   Java 쪽 같은 범위: AuditRetentionClass(server/shared …/audit/dto) — 한쪽을 고치면 다른 쪽도 고친다.
-- 기본 실행: 매일 03:30 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 5칸 식(분 시 일 월 요일).
-- 설정값(batch_params): 위 셋 · chunk_size(기본 5000) · max_per_run(기본 100000 — 세 분류 합).
-- 결과: 처리 건수 = 지운 행 수(세 분류 합), 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_audit_log_retention();  (「지금 실행」은 CALL batch_audit_log_retention('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_audit_log_retention(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'audit-log-retention';
    v_general bigint := batch_param(c_job, 'retention_days', 365, 1, 3650);
    v_pii     bigint := batch_param(c_job, 'personal_data_retention_days', 730, 365, 3650);
    v_money   bigint := batch_param(c_job, 'money_retention_days', 1825, 1825, 3650);
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_class   text;
    v_days    bigint;
    v_cutoff  timestamptz;
    v_n       bigint;
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

    FOREACH v_class IN ARRAY ARRAY['GENERAL', 'PERSONAL_DATA', 'MONEY'] LOOP
        EXIT WHEN v_err IS NOT NULL OR v_total >= v_max;
        v_days := CASE v_class WHEN 'GENERAL' THEN v_general WHEN 'PERSONAL_DATA' THEN v_pii ELSE v_money END;
        v_cutoff := now() - make_interval(days => v_days::int);
        LOOP
            BEGIN
                DELETE FROM audit_logs
                 WHERE id IN (SELECT id FROM audit_logs
                               WHERE retention_class = v_class AND occurred_at < v_cutoff
                               ORDER BY occurred_at LIMIT least(v_chunk, v_max - v_total));
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
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.auditLogRetention.classes' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text)
             ELSE batch_args(v_total::text, v_general::text, v_pii::text, v_money::text) END,
        'batch.result.auditLogRetention.classes.none', batch_args(v_general::text, v_pii::text, v_money::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('audit-log-retention', 'retention_days', 365);
SELECT batch_param_seed('audit-log-retention', 'personal_data_retention_days', 730);
SELECT batch_param_seed('audit-log-retention', 'money_retention_days', 1825);
SELECT batch_param_seed('audit-log-retention', 'chunk_size', 5000);
SELECT batch_param_seed('audit-log-retention', 'max_per_run', 100000);
SELECT batch_define('audit-log-retention', 'batch_audit_log_retention', 3, 30);
