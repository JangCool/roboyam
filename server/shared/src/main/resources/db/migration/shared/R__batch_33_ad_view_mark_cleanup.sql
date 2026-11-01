-- DB 배치 `ad-view-mark-cleanup` — 공개 광고 하루 1번 판정 표(ad_view_marks, V118) 정리. docs/features/ad-contract.md 「공개 노출 목업」 확정 결정(2026-11-01).
-- 번호: develop 의 반복 마이그레이션 shared R__batch_32 · admin R__batch_30 다음.
-- 규칙: 판정은 「한국 날짜 하루」 단위라 오늘 · 어제 줄만 있으면 된다. 한국 날짜가 retention_days 일(기본 2 — 오늘·어제를 남김, 2~30) 넘게 지난 줄을 지운다.
--       집계(ad_daily_stats)는 지우지 않는다. 판정 표에는 원본 IP·User-Agent·회원 ID 가 없다(날짜를 섞은 HMAC 64자뿐).
-- 기본 실행: 매일 04:15 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_days(2) · chunk_size(5000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 행 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 지운 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_ad_view_mark_cleanup();  (「지금 실행」은 CALL batch_ad_view_mark_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_ad_view_mark_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'ad-view-mark-cleanup';
    v_keep   bigint := batch_param(c_job, 'retention_days', 2, 2, 30);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_cutoff date;
    v_n      bigint;
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

    -- 남길 첫 날(한국 날짜) — 기본 2 면 어제
    v_cutoff := (now() AT TIME ZONE 'Asia/Seoul')::date - (v_keep::int - 1);
    LOOP
        BEGIN
            DELETE FROM ad_view_marks
             WHERE ctid = ANY (ARRAY(SELECT ctid FROM ad_view_marks
                                     WHERE day < v_cutoff
                                     LIMIT least(v_chunk, v_max - v_total)));
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

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.adViewMarkCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_keep::text) END,
        'batch.result.adViewMarkCleanup.none', batch_args(v_keep::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('ad-view-mark-cleanup', 'retention_days', 2);
SELECT batch_param_seed('ad-view-mark-cleanup', 'chunk_size', 5000);
SELECT batch_param_seed('ad-view-mark-cleanup', 'max_per_run', 100000);
SELECT batch_define('ad-view-mark-cleanup', 'batch_ad_view_mark_cleanup', 4, 15);
