-- DB 배치 `biz-payment-record-purge` — 광고·사업자 핫딜 신청 건의 계약서 사본 내용과 입금 기록의 개인 칸 파기(2026-11-24 사용자 결정 — 개인정보처리방침 1-3·2절,
-- 국세기본법 장부·증빙 5년 · 전자상거래법 대금 결제 기록 5년). 번호: shared R__batch_45 다음. 칸 모양은 V263 ①②.
-- 규칙: 「게재가 끝난 날」 + retention_years(기본 5)가 지난 신청 건의
--       · 계약서 사본(biz_contract_files) 내용(암호문)을 비우고 erased_at 을 찍는다 — 줄(이름·형식·크기·해시·올린 시각)은 남긴다(종이 접수의 스캔본 참조 · 사본 수 CHECK).
--       · 입금 기록(biz_deposits)의 입금자명·불일치 사유를 비우고 erased_at 을 찍는다 — 입금액·입금일·확인 운영자·시각은 남긴다(금액 합계·대사·통계가 깨지지 않게).
--       「게재가 끝난 날」 = 예약·내림(SCHEDULED·TAKEN_DOWN)은 게재 끝 시각(end_at — 내림을 되돌릴 수 있어 내린 시각보다 늦은 쪽), 반려·취소·만료는 닫힌 시각(closed_at, 없으면 updated_at),
--       그리고 그 건의 환불(요청·결정·완료·송금 시각)과 입금 확인 시각 중 가장 늦은 것. 심사·결제 대기 건과 열린 환불(심사 대기 · 수동 송금 대기)이 있는 건은 미룬다.
--       · 그 건의 수동 환불 송금 메모(biz_refunds.transfer_note)도 비운다(2026-11-24 메인 세션 결정).
--       · 건에 붙지 않은 계약서 사본(초안 — application_id NULL)은 올린 지 같은 기간이 지나면 내용을 비운다.
--       신청 건 줄 · 금액 · 상태 · 환불 금액·결정 기록 · 처리 기록은 지우지 않는다. 종이로 받은 계약서(biz_doc_intakes CONTRACT)는 종이라 배치가 없앨 수 없다 —
--       운영자 몫(파기 = 「폐기함」 처리, business-contract · ad-contract 「보존」).
-- 기본 실행: 매일 05:05 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_years(5, 5~10) · chunk_size(500) · max_per_run(100000).
-- 결과: 처리 건수 = 비운 계약서 사본 줄 + 입금 기록 줄 + 송금 메모 줄 + 초안 사본 줄, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_biz_payment_record_purge();  (「지금 실행」은 CALL batch_biz_payment_record_purge('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_biz_payment_record_purge(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'biz-payment-record-purge';
    v_years  bigint := batch_param(c_job, 'retention_years', 5, 5, 10);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 500, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_cut    timestamptz;
    v_last   bigint := 0;
    v_ids    bigint[];
    v_n      bigint;
    v_round  bigint;
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

    v_cut := now() - make_interval(years => v_years::int);
    LOOP
        BEGIN
            -- 다음 덩어리 — 비울 사본·입금 기록이 남은 끝난 신청 건(id 차례 키셋)
            SELECT array_agg(id ORDER BY id) INTO v_ids
              FROM (SELECT a.id FROM biz_applications a
                     WHERE a.id > v_last
                       AND a.status IN ('SCHEDULED', 'TAKEN_DOWN', 'REJECTED', 'CANCELED', 'EXPIRED')
                       AND (EXISTS (SELECT 1 FROM biz_contract_files f WHERE f.application_id = a.id AND f.content IS NOT NULL)
                            OR EXISTS (SELECT 1 FROM biz_deposits d WHERE d.application_id = a.id AND (d.depositor_name IS NOT NULL OR d.mismatch_reason IS NOT NULL))
                            OR EXISTS (SELECT 1 FROM biz_refunds r WHERE r.application_id = a.id AND r.transfer_note IS NOT NULL))
                       AND greatest(CASE WHEN a.status IN ('SCHEDULED', 'TAKEN_DOWN') THEN a.end_at ELSE coalesce(a.closed_at, a.updated_at) END,
                                    (SELECT max(greatest(r.requested_at, r.decided_at, r.completed_at, r.transferred_at)) FROM biz_refunds r WHERE r.application_id = a.id),
                                    (SELECT d.confirmed_at FROM biz_deposits d WHERE d.application_id = a.id)) <= v_cut
                       AND NOT EXISTS (SELECT 1 FROM biz_refunds r WHERE r.application_id = a.id AND (r.status = 'REQUESTED' OR r.transfer_status = 'PENDING'))
                     ORDER BY a.id LIMIT v_chunk) s;
            EXIT WHEN v_ids IS NULL;
            v_last := v_ids[cardinality(v_ids)];
            v_round := 0;

            UPDATE biz_contract_files SET content = NULL, erased_at = coalesce(erased_at, now())
             WHERE application_id = ANY (v_ids) AND content IS NOT NULL;
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            UPDATE biz_deposits SET depositor_name = NULL, mismatch_reason = NULL, erased_at = coalesce(erased_at, now())
             WHERE application_id = ANY (v_ids) AND (depositor_name IS NOT NULL OR mismatch_reason IS NOT NULL);
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            -- 수동 환불 송금 메모(입금 기록과 같은 성격 — 계좌·이름이 들 수 있다)
            UPDATE biz_refunds SET transfer_note = NULL WHERE application_id = ANY (v_ids) AND transfer_note IS NOT NULL;
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_round;
        COMMIT;
        EXIT WHEN v_total >= v_max;
    END LOOP;

    -- 건에 붙지 않은 계약서 사본(운영자 등록 서랍에서 미리 올리고 건을 만들지 않은 초안, application_id NULL — V115) — 올린 지 같은 기간이 지나면 내용을 비운다
    IF v_err IS NULL AND v_total < v_max THEN
        LOOP
            BEGIN
                UPDATE biz_contract_files SET content = NULL, erased_at = coalesce(erased_at, now())
                 WHERE id IN (SELECT f.id FROM biz_contract_files f
                               WHERE f.application_id IS NULL AND f.content IS NOT NULL AND f.uploaded_at <= v_cut
                               ORDER BY f.id LIMIT least(v_chunk, v_max - v_total));
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
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.bizPaymentRecordPurge' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_years::text) END,
        'batch.result.bizPaymentRecordPurge.none', batch_args(v_years::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('biz-payment-record-purge', 'retention_years', 5);
SELECT batch_param_seed('biz-payment-record-purge', 'chunk_size', 500);
SELECT batch_param_seed('biz-payment-record-purge', 'max_per_run', 100000);
SELECT batch_define('biz-payment-record-purge', 'batch_biz_payment_record_purge', 5, 5);
