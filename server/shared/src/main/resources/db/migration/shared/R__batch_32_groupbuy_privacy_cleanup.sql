-- DB 배치 `groupbuy-privacy-cleanup` — 공구 개인정보 보관 정리. docs/domain/commerce.md 10-9, docs/features/commerce-groupbuy-contract.md 결정 34·「마이그레이션 계획」(2b-4).
-- 번호: shared R__batch_31(결제 웹훅 기록) 다음.
-- 규칙(거래 기록 — 주문 금액·상태·날짜·환불 기록 — 은 지우지 않는다. 열린 요청이 있는 주문은 미룬다):
--   ① 배송지: 배송완료 뒤 delivered_days(기본 180일), 취소·환불 뒤 canceled_days(기본 180일), 결제하지 못한 주문(EXPIRED)은 만료 뒤 공구 설정 expired_address_retention_days(기본 30일)
--      → 배송지 암호문·우편번호를 지우고 address_masked_at 을 적는다.
--   ② 수정 이력(표시·광고 기록 — 10-2 「6개월」): 다음 판이 생긴 지 revision_days(기본 180일)가 지난 판을 지운다(지금 판·주문이 가리키는 판 번호는 남는다 — 공구마다 마지막 판은 지우지 않는다).
--   ③ 요청(소비자 불만·분쟁 처리 기록 3년): 끝난 지 request_days(기본 1095일)가 지난 요청의 회원 사유 글(memo)과 회원이 올린 사진 줄을 지운다(운영자 거절 근거·금액은 청약철회 기록으로 남는다).
--   ④ 공구 문의(2b-6, 소비자 문의 기록 3년): 보관 만료일(group_buy_questions.retain_until)이 지난 질문을 지운다(답변·답변 이력 함께).
-- 기본 실행: 매일 04:35 (한국시간 — 메일 대기열 정리 04:40 앞). 설정값(batch_params): delivered_days(180) · canceled_days(180) · revision_days(180) · request_days(1095) ·
--   chunk_size(1000) · max_per_run(100000).
-- 결과: 처리 건수 = 지운(가린) 행 수 합, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_groupbuy_privacy_cleanup();  (「지금 실행」은 CALL batch_groupbuy_privacy_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_groupbuy_privacy_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job       CONSTANT text := 'groupbuy-privacy-cleanup';
    v_delivered bigint := batch_param(c_job, 'delivered_days', 180, 30, 3650);
    v_canceled  bigint := batch_param(c_job, 'canceled_days', 180, 30, 3650);
    v_revision  bigint := batch_param(c_job, 'revision_days', 180, 180, 3650);
    v_request   bigint := batch_param(c_job, 'request_days', 1095, 1095, 3650);
    v_chunk     bigint := batch_param(c_job, 'chunk_size', 1000, 100, 100000);
    v_max       bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_expired   bigint;
    v_n         bigint;
    v_total     bigint := 0;
    v_err       text;
    v_state     text;
    v_msg       text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT;

    SELECT expired_address_retention_days INTO v_expired FROM group_buy_settings WHERE id = 1;
    v_expired := COALESCE(v_expired, 30);

    -- ① 배송지
    LOOP
        BEGIN
            UPDATE group_buy_participations
               SET recipient_enc = NULL, phone_enc = NULL, postcode = NULL, address1_enc = NULL, address2_enc = NULL, memo_enc = NULL,
                   address_masked_at = now(), updated_at = now()
             WHERE id IN (SELECT p.id FROM group_buy_participations p
                           WHERE p.address_masked_at IS NULL
                             AND ((p.status = 'DELIVERED' AND p.delivered_at < now() - make_interval(days => v_delivered::int))
                               OR (p.status IN ('CANCELED', 'REFUNDED') AND COALESCE(p.refunded_at, p.canceled_at, p.updated_at) < now() - make_interval(days => v_canceled::int))
                               OR (p.status = 'EXPIRED' AND COALESCE(p.expired_at, p.updated_at) < now() - make_interval(days => v_expired::int)))
                             AND NOT EXISTS (SELECT 1 FROM group_buy_requests r WHERE r.participation_id = p.id
                                              AND r.status IN ('AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED'))
                           ORDER BY p.id LIMIT least(v_chunk, v_max - v_total));
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

    -- ② 수정 이력 — 다음 판이 생긴 지 기간이 지난 판(공구마다 마지막 판은 남는다)
    IF v_err IS NULL AND v_total < v_max THEN
        LOOP
            BEGIN
                DELETE FROM group_buy_revisions
                 WHERE id IN (SELECT r.id FROM group_buy_revisions r
                               WHERE EXISTS (SELECT 1 FROM group_buy_revisions n
                                              WHERE n.group_buy_id = r.group_buy_id AND n.revision_no > r.revision_no
                                                AND n.created_at < now() - make_interval(days => v_revision::int))
                               ORDER BY r.id LIMIT least(v_chunk, v_max - v_total));
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

    -- ③ 끝난 요청의 회원 사유 글·회원 사진
    IF v_err IS NULL AND v_total < v_max THEN
        BEGIN
            DELETE FROM group_buy_request_photos ph
             USING group_buy_requests r
             WHERE ph.request_id = r.id AND ph.owner_kind = 'MEMBER'
               AND r.status IN ('COMPLETED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED', 'EXPIRED')
               AND r.updated_at < now() - make_interval(days => v_request::int);
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_total := v_total + v_n;
            UPDATE group_buy_requests SET memo = NULL
             WHERE memo IS NOT NULL AND status IN ('COMPLETED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED', 'EXPIRED')
               AND updated_at < now() - make_interval(days => v_request::int);
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_total := v_total + v_n;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
        END;
        COMMIT;
    END IF;

    -- ④ 공구 문의(2b-6 — 소비자 문의 기록 3년): 보관 만료일(마지막 답변일, 답변이 없으면 작성일 + 3년)이 지난 질문(답변·답변 이력은 함께 지워진다)
    IF v_err IS NULL AND v_total < v_max THEN
        LOOP
            BEGIN
                DELETE FROM group_buy_questions
                 WHERE id IN (SELECT id FROM group_buy_questions WHERE retain_until < (now() AT TIME ZONE 'Asia/Seoul')::date
                               ORDER BY id LIMIT least(v_chunk, v_max - v_total));
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
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.groupbuyPrivacyCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text) END,
        'batch.result.groupbuyPrivacyCleanup.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('groupbuy-privacy-cleanup', 'delivered_days', 180);
SELECT batch_param_seed('groupbuy-privacy-cleanup', 'canceled_days', 180);
SELECT batch_param_seed('groupbuy-privacy-cleanup', 'revision_days', 180);
SELECT batch_param_seed('groupbuy-privacy-cleanup', 'request_days', 1095);
SELECT batch_param_seed('groupbuy-privacy-cleanup', 'chunk_size', 1000);
SELECT batch_param_seed('groupbuy-privacy-cleanup', 'max_per_run', 100000);
SELECT batch_define('groupbuy-privacy-cleanup', 'batch_groupbuy_privacy_cleanup', 4, 35);
