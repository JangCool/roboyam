-- DB 배치 `commerce-record-privacy-purge` — 전자상거래 거래 기록(공동구매 주문)의 법정 보관 기간이 지나면 개인정보만 지운다(2026-11-24 사용자 결정 — 개인정보처리방침 2절 「거래 기록」,
-- docs/domain/commerce.md 17-3 「거래 기록 개인 칸」). 번호: shared R__batch_46 다음. 표시 칸은 V263 ③.
-- 규칙: 끝난 주문(배송완료·취소·환불·만료)의 「마지막 거래 시각」 + retention_years(기본 5 — 계약·청약철회·대금결제·재화공급 기록 5년)이 지나면
--       남은 개인 칸 — 받는 분 이름·전화번호·우편번호·주소·배송 메모 암호문, 현금영수증 번호 암호문, 운영 메모(자유 글이라 이름·연락처가 들 수 있다) — 를 비우고
--       privacy_purged_at 을 찍는다(배송지를 아직 가리지 않았으면 address_masked_at 도). 같은 때 받는 분을 알아볼 수 있는 연결 고리 — 송장 번호(group_buy_shipments.tracking_no, V265)·회수 송장 번호·
--       운영자 거절 메모(group_buy_requests.reject_note)·PG 영수증 주소(payment_orders.receipt_url — 주문 결제·반품 배송비 결제) — 도 비운다(2026-11-24 메인 세션 결정).
--       주문 번호·금액·상품(주문 줄)·상태·날짜·환불·요청·배송 기록(택배사·시각)과 회원 번호·PG 거래 번호는 남긴다(정산·대사·통계).
--       「마지막 거래 시각」 = 주문의 만든·결제·발주·발송·배송완료·취소·환불·만료 시각, 그 주문의 요청(갱신 시각)·환불(만든·완료 시각)·배송(발송·완료·구매확정 시각)·계좌 송금(보낸 시각) 중 가장 늦은 것
--       — 17-3 이 연락용 이메일에 쓰는 「마지막 거래 시각 + 5년」과 같은 기준(세법 보존 기준일은 익명 거래 기록에만 쓴다, 2026-11-04 정정).
--       미루는 것: 열린 요청(부족분 결제 대기·접수·승인·회수) · 끝나지 않은 환불(진행·지연·수동) · 보내지 않은 계좌 송금(계좌 받기 전·계좌 있음 — 돈을 돌려줘야 한다).
-- 이 배치가 맡지 않는 것(이미 다른 배치가 지운다): 배송지 180일·만료 주문 30일(R__batch_32 ①) · 요청 사유 글·회원 사진 3년, 상품 문의 3년(소비자 불만·분쟁 기록 — R__batch_32 ③④) ·
--   송금을 마친 환불 계좌(보낸 그 자리에서 지운다) · 탈퇴 회원 연락용 이메일(마지막 거래 + 5년 — R__batch_28) · 로보티콘 유료 구매(emoticon_purchases)는 회원 번호·금액·팩 사본뿐이라 지울 개인 칸이 없다.
-- 기본 실행: 매일 05:10 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_years(5, 5~10) · chunk_size(1000) · max_per_run(100000).
-- 결과: 처리 건수 = 개인 칸을 지운 주문 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_commerce_record_privacy_purge();  (「지금 실행」은 CALL batch_commerce_record_privacy_purge('MANUAL', 운영자ID, NULL))
-- 시험: shared CommerceRecordPrivacyPurgeBatchTest(커머스 규칙 — commerce.md 18절).

CREATE OR REPLACE PROCEDURE batch_commerce_record_privacy_purge(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'commerce-record-privacy-purge';
    v_years  bigint := batch_param(c_job, 'retention_years', 5, 5, 10);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 1000, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_cut    timestamptz;
    v_last_at timestamptz := '-infinity';
    v_last   bigint := 0;
    v_ids    bigint[];
    v_done   bigint[];
    v_pays   bigint[];
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

    v_cut := now() - make_interval(years => v_years::int);
    LOOP
        BEGIN
            -- 다음 덩어리 — 끝났고 아직 파기하지 않은, 만든 지 기간이 지난 주문(ix_gbp_privacy_purge — 만든 시각·id 차례 키셋.
            -- 마지막 거래 시각 ≥ 만든 시각이라 만든 지 기간이 안 된 주문은 볼 필요가 없다)
            SELECT array_agg(id ORDER BY created_at, id), (array_agg(created_at ORDER BY created_at DESC, id DESC))[1], (array_agg(id ORDER BY created_at DESC, id DESC))[1]
              INTO v_ids, v_last_at, v_last
              FROM (SELECT p.id, p.created_at FROM group_buy_participations p
                     WHERE p.privacy_purged_at IS NULL AND p.status IN ('DELIVERED', 'CANCELED', 'REFUNDED', 'EXPIRED')
                       AND p.created_at <= v_cut AND (p.created_at, p.id) > (v_last_at, v_last)
                     ORDER BY p.created_at, p.id LIMIT least(v_chunk, v_max - v_total)) s;
            EXIT WHEN v_ids IS NULL;

            WITH done AS (
                UPDATE group_buy_participations p
                   SET recipient_enc = NULL, phone_enc = NULL, postcode = NULL, address1_enc = NULL, address2_enc = NULL, memo_enc = NULL,
                       cash_receipt_no_enc = NULL, admin_memo = NULL,
                       address_masked_at = coalesce(p.address_masked_at, now()), privacy_purged_at = now(), updated_at = now()
                 WHERE p.id = ANY (v_ids)
                   AND p.privacy_purged_at IS NULL AND p.status IN ('DELIVERED', 'CANCELED', 'REFUNDED', 'EXPIRED')
                   AND greatest(p.created_at, p.joined_at, p.confirmed_at, p.ordered_at, p.shipped_at, p.delivered_at, p.canceled_at, p.refunded_at, p.expired_at,
                                (SELECT max(r.updated_at) FROM group_buy_requests r WHERE r.participation_id = p.id),
                                (SELECT max(greatest(f.created_at, f.completed_at)) FROM group_buy_refunds f WHERE f.participation_id = p.id),
                                (SELECT max(greatest(s.shipped_at, s.delivered_at, s.escrow_completed_at)) FROM group_buy_shipments s WHERE s.participation_id = p.id),
                                (SELECT max(t.sent_at) FROM commerce_bank_transfers t WHERE t.participation_id = p.id)) <= v_cut
                   AND NOT EXISTS (SELECT 1 FROM group_buy_requests r WHERE r.participation_id = p.id AND r.status IN ('AWAITING_FEE', 'REQUESTED', 'APPROVED', 'RECEIVED'))
                   AND NOT EXISTS (SELECT 1 FROM group_buy_refunds f WHERE f.participation_id = p.id AND f.status <> 'COMPLETED')
                   AND NOT EXISTS (SELECT 1 FROM commerce_bank_transfers t WHERE t.participation_id = p.id AND t.status <> 'SENT')
                RETURNING p.id, p.payment_order_id)
            SELECT array_agg(id), array_agg(payment_order_id) FILTER (WHERE payment_order_id IS NOT NULL) INTO v_done, v_pays FROM done;
            v_n := coalesce(cardinality(v_done), 0);

            IF v_done IS NOT NULL THEN
                -- 받는 분을 알아볼 수 있는 연결 고리(2026-11-24 메인 세션 결정): 송장 번호(발송·교환) · 회수 송장 번호 · 운영자 거절 메모 · PG 영수증 주소(주문 결제 · 반품 배송비 결제)
                UPDATE group_buy_shipments SET tracking_no = NULL, updated_at = now() WHERE participation_id = ANY (v_done) AND tracking_no IS NOT NULL;
                UPDATE group_buy_requests SET reject_note = NULL, pickup_tracking_no = NULL
                 WHERE participation_id = ANY (v_done) AND (reject_note IS NOT NULL OR pickup_tracking_no IS NOT NULL);
                UPDATE payment_orders SET receipt_url = NULL, updated_at = now()
                 WHERE receipt_url IS NOT NULL
                   AND (id = ANY (coalesce(v_pays, '{}'))
                        OR id IN (SELECT r.fee_payment_order_id FROM group_buy_requests r WHERE r.participation_id = ANY (v_done) AND r.fee_payment_order_id IS NOT NULL));
            END IF;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.commerceRecordPrivacyPurge' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_years::text) END,
        'batch.result.commerceRecordPrivacyPurge.none', batch_args(v_years::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('commerce-record-privacy-purge', 'retention_years', 5);
SELECT batch_param_seed('commerce-record-privacy-purge', 'chunk_size', 1000);
SELECT batch_param_seed('commerce-record-privacy-purge', 'max_per_run', 100000);
SELECT batch_define('commerce-record-privacy-purge', 'batch_commerce_record_privacy_purge', 5, 10);
