package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.service.GroupBuyRequestService;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 거래 기록 개인정보 파기 DB 배치 {@code commerce-record-privacy-purge}(R__batch_47, V263 ③ — commerce.md 17-3 「거래 기록 개인 칸」, 2026-11-24 사용자 결정).
 * 끝난 공구 주문의 마지막 거래 시각 + 5년이 지나면 받는 분·연락처·주소·배송 메모·현금영수증 번호·운영 메모만 지우고 {@code privacy_purged_at} 을 찍는다.
 * 주문 번호·금액·상태·날짜·주문 줄·배송·요청 기록은 남는다. 마지막 거래(요청 갱신 등)가 5년 안이거나, 열린 요청·보내지 않은 계좌 송금이 있으면 미룬다.
 *
 * <p>규칙 → 시험: ① 5년 지난 배송완료·취소 주문 개인 칸 파기 + 연결 고리(송장 번호·회수 송장·거절 메모·PG 영수증 주소) 비움, 거래 칸 남김 → {@link #마지막_거래_5년이_지난_끝난_주문은_개인_칸만_지우고_거래_기록은_남긴다}
 * ② 5년 안 · 마지막 거래(요청)가 5년 안 · 열린 요청 · 보내지 않은 송금은 그대로 → {@link #기간_5년이_안_됐거나_마지막_거래가_5년_안이거나_열린_일이_있으면_그대로}
 * ③ 다시 돌려도 다시 대상이 되지 않음 → ①의 끝 · ④ 카탈로그·기본값·번들 → {@link #카탈로그는_DB_배치이고_매일_05시_10분_보관_5년_번들_두_언어}
 */
class CommerceRecordPrivacyPurgeBatchTest {

    private static final String JOB = "commerce-record-privacy-purge";
    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private void run() {
        kit.jdbc.execute("CALL batch_commerce_record_privacy_purge('MANUAL', NULL, NULL)");
    }

    /** 그 주문과 딸린 기록(배송·요청·환불·송금)의 모든 시각을 {@code years} 년 앞으로 민다 — 순서는 그대로 */
    private void age(long participationId, int years) {
        String back = " - make_interval(years => " + years + ")";
        kit.jdbc.update("UPDATE group_buy_participations SET created_at = created_at" + back + ", updated_at = updated_at" + back + ", joined_at = joined_at" + back
                + ", confirmed_at = confirmed_at" + back + ", canceled_at = canceled_at" + back + ", refunded_at = refunded_at" + back + ", expired_at = expired_at" + back
                + ", ordered_at = ordered_at" + back + ", shipped_at = shipped_at" + back + ", delivered_at = delivered_at" + back + ", reserved_until = reserved_until" + back
                + " WHERE id = ?", participationId);
        kit.jdbc.update("UPDATE group_buy_shipments SET shipped_at = shipped_at" + back + ", delivered_at = delivered_at" + back + ", escrow_logistics_at = escrow_logistics_at"
                + back + ", escrow_completed_at = escrow_completed_at" + back + ", created_at = created_at" + back + ", updated_at = updated_at" + back
                + " WHERE participation_id = ?", participationId);
        kit.jdbc.update("UPDATE group_buy_requests SET created_at = created_at" + back + ", updated_at = updated_at" + back + ", decided_at = decided_at" + back
                + ", completed_at = completed_at" + back + ", received_at = received_at" + back + ", closes_at = closes_at" + back + " WHERE participation_id = ?", participationId);
        kit.jdbc.update("UPDATE group_buy_refunds SET created_at = created_at" + back + ", completed_at = completed_at" + back + " WHERE participation_id = ?", participationId);
        kit.jdbc.update("UPDATE commerce_bank_transfers SET created_at = created_at" + back + ", sent_at = sent_at" + back + " WHERE participation_id = ?", participationId);
    }

    /** 현금영수증 번호 · 운영 메모 — 주문 화면에서 받는 칸(이 시험에서는 바로 넣는다) */
    private void receiptAndMemo(long participationId) {
        kit.jdbc.update("UPDATE group_buy_participations SET cash_receipt_type = 'PERSONAL', cash_receipt_no_enc = 'd1:receipt', admin_memo = '홍길동님 010-1234-5678 부재 시 경비실' "
                + "WHERE id = ?", participationId);
    }

    private Map<String, Object> row(long participationId) {
        return kit.jdbc.queryForMap("SELECT order_no, status, amount, cash_amount, delivered_at, canceled_at, recipient_enc, phone_enc, postcode, address1_enc, address2_enc, memo_enc, "
                + "cash_receipt_type, cash_receipt_no_enc, admin_memo, address_masked_at, privacy_purged_at FROM group_buy_participations WHERE id = ?", participationId);
    }

    private void assertPurged(long participationId, String orderNo, long amount, String status) {
        Map<String, Object> r = row(participationId);
        for (String c : new String[] {"recipient_enc", "phone_enc", "postcode", "address1_enc", "address2_enc", "memo_enc", "cash_receipt_no_enc", "admin_memo"}) {
            assertThat(r.get(c)).as(c + " 파기 " + participationId).isNull();
        }
        assertThat(r.get("privacy_purged_at")).as("파기 표시").isNotNull();
        assertThat(r.get("address_masked_at")).as("배송지 가림 표시").isNotNull();
        // 거래 기록은 남는다 — 주문 번호·금액·상태·날짜·현금영수증 종류·주문 줄·배송 기록
        assertThat(r.get("order_no")).isEqualTo(orderNo);
        assertThat(((Number) r.get("amount")).longValue()).isEqualTo(amount);
        assertThat(r.get("status")).isEqualTo(status);
        assertThat(r.get("cash_receipt_type")).isEqualTo("PERSONAL");
        assertThat(kit.count("SELECT count(*) FROM group_buy_participation_items WHERE participation_id = ?", participationId)).as("주문 줄(상품)").isPositive();
    }

    private void assertKept(long participationId, String why) {
        Map<String, Object> r = row(participationId);
        assertThat(r.get("recipient_enc")).as(why + " — 받는 분").isNotNull();
        assertThat(r.get("phone_enc")).as(why + " — 연락처").isNotNull();
        assertThat(r.get("cash_receipt_no_enc")).as(why + " — 현금영수증 번호").isNotNull();
        assertThat(r.get("admin_memo")).as(why + " — 운영 메모").isNotNull();
        assertThat(r.get("privacy_purged_at")).as(why + " — 파기 표시 없음").isNull();
        assertThat(kit.count("SELECT count(*) FROM group_buy_shipments WHERE participation_id = ? AND tracking_no IS NOT NULL", participationId)).as(why + " — 송장 번호")
                .isEqualTo(1);
    }

    @Test
    void 카탈로그는_DB_배치이고_매일_05시_10분_보관_5년_번들_두_언어() throws Exception {
        assertThat(BatchCatalog.ofKey(JOB)).isEqualTo(BatchCatalog.COMMERCE_RECORD_PRIVACY_PURGE);
        assertThat(BatchCatalog.COMMERCE_RECORD_PRIVACY_PURGE.isJava()).isFalse();
        assertThat(BatchCatalog.COMMERCE_RECORD_PRIVACY_PURGE.procedureName()).isEqualTo("batch_commerce_record_privacy_purge");
        Map<String, Object> defined = kit.jdbc.queryForMap("SELECT kst_hour, kst_minute, cadence FROM batch_db_jobs WHERE job_key = ?", JOB);
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(5);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(10);
        assertThat(defined.get("cadence")).isEqualTo("DAILY");
        assertThat(kit.jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'retention_years'", Long.class, JOB)).isEqualTo(5L);
        for (String lang : new String[] {"ko", "en"}) {
            Properties bundle = new Properties();
            try (var in = new java.io.InputStreamReader(CommerceRecordPrivacyPurgeBatchTest.class.getResourceAsStream("/messages/messages_" + lang + ".properties"),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                bundle.load(in);
            }
            assertThat(bundle).containsKeys("batch.job." + JOB + ".name", "batch.job." + JOB + ".desc", "batch.result.commerceRecordPrivacyPurge",
                    "batch.result.commerceRecordPrivacyPurge.none");
        }
    }

    @Test
    void 마지막_거래_5년이_지난_끝난_주문은_개인_칸만_지우고_거래_기록은_남긴다() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(25_000).open();
        long deliveredUser = kit.newMember();
        JoinResult delivered = kit.delivered(deliveredUser, gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        // 끝난 요청 하나(운영자 거절 메모 · 회수 송장 번호가 남은 모양) + PG 영수증 주소 — 받는 분을 알아볼 수 있는 연결 고리
        var closedRequest = kit.requests.create(deliveredUser, delivered.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN,
                CollectionMethod.CUSTOMER_SEND, Resolution.REFUND, List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, RequestReason.DEFECT)), "불량이에요",
                List.of(), null));
        kit.requests.withdraw(deliveredUser, closedRequest.requestId());
        kit.jdbc.update("UPDATE group_buy_requests SET reject_note = '홍길동님 통화 — 사용 흔적', pickup_carrier_code = 'CJ', pickup_tracking_no = '123456789012' WHERE id = ?",
                closedRequest.requestId());
        kit.jdbc.update("UPDATE payment_orders SET receipt_url = 'https://receipt.example/pay' WHERE id = (SELECT payment_order_id FROM group_buy_participations WHERE id = ?)",
                delivered.participationId());
        long canceledUser = kit.newMember();
        JoinResult canceled = kit.paidOrder(canceledUser, gb, 1);
        kit.cancels.cancelByMember(canceledUser, canceled.participationId(), List.of());
        kit.settleCancels();
        for (JoinResult o : List.of(delivered, canceled)) {
            receiptAndMemo(o.participationId());
            age(o.participationId(), 6);
        }
        Map<String, Object> deliveredBefore = row(delivered.participationId());
        Map<String, Object> canceledBefore = row(canceled.participationId());
        String deliveredStatus = (String) deliveredBefore.get("status");
        String canceledStatus = (String) canceledBefore.get("status");
        assertThat(deliveredStatus).isEqualTo("DELIVERED");
        assertThat(canceledStatus).isIn("CANCELED", "REFUNDED");

        run();

        assertPurged(delivered.participationId(), (String) deliveredBefore.get("order_no"), ((Number) deliveredBefore.get("amount")).longValue(), deliveredStatus);
        assertPurged(canceled.participationId(), (String) canceledBefore.get("order_no"), ((Number) canceledBefore.get("amount")).longValue(), canceledStatus);
        assertThat(row(delivered.participationId()).get("delivered_at")).as("배송완료 시각(날짜)은 남는다").isEqualTo(deliveredBefore.get("delivered_at"));
        assertThat(kit.count("SELECT count(*) FROM group_buy_shipments WHERE participation_id = ?", delivered.participationId())).as("배송(재화 공급) 기록").isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM group_buy_shipments WHERE participation_id = ? AND tracking_no IS NULL AND carrier_code IS NOT NULL AND shipped_at IS NOT NULL",
                delivered.participationId())).as("송장 번호만 지우고 택배사·발송 시각은 남는다").isEqualTo(1);
        Map<String, Object> req = kit.jdbc.queryForMap("SELECT status, reject_note, pickup_tracking_no, pickup_carrier_code FROM group_buy_requests WHERE id = ?",
                closedRequest.requestId());
        assertThat(req.get("reject_note")).as("거절 메모").isNull();
        assertThat(req.get("pickup_tracking_no")).as("회수 송장 번호").isNull();
        assertThat(req.get("status")).as("요청(청약철회) 기록은 남는다").isEqualTo("WITHDRAWN");
        Map<String, Object> pay = kit.jdbc.queryForMap("SELECT receipt_url, paid_amount, pg_tx_id FROM payment_orders WHERE id = (SELECT payment_order_id FROM group_buy_participations "
                + "WHERE id = ?)", delivered.participationId());
        assertThat(pay.get("receipt_url")).as("PG 영수증 주소").isNull();
        assertThat(pay.get("paid_amount")).as("결제 금액은 남는다").isNotNull();
        assertThat(kit.count("SELECT count(*) FROM payment_orders WHERE id = (SELECT payment_order_id FROM group_buy_participations WHERE id = ?)", delivered.participationId()))
                .as("결제 기록").isEqualTo(1);
        assertThat(kit.jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, JOB))
                .isEqualTo("batch.result.commerceRecordPrivacyPurge");

        // 다시 돌려도 파기 표시는 처음 그대로다(다시 대상이 되지 않는다)
        Object firstMark = row(delivered.participationId()).get("privacy_purged_at");
        run();
        assertThat(row(delivered.participationId()).get("privacy_purged_at")).isEqualTo(firstMark);
    }

    @Test
    void 기간_5년이_안_됐거나_마지막_거래가_5년_안이거나_열린_일이_있으면_그대로() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(25_000).open();

        // 4년 전 배송완료 — 아직 기간 안
        JoinResult recent = kit.delivered(kit.newMember(), gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        receiptAndMemo(recent.participationId());
        age(recent.participationId(), 4);

        // 6년 전 배송완료지만 반품 요청(철회)이 4년 전에 끝났다 — 마지막 거래 시각은 요청
        long lateUser = kit.newMember();
        JoinResult lateRequest = kit.delivered(lateUser, gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        var withdrawn = kit.requests.create(lateUser, lateRequest.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CollectionMethod.CUSTOMER_SEND,
                Resolution.REFUND, List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, RequestReason.DEFECT)), "포장이 찢겨 왔어요", List.of(), null));
        kit.requests.withdraw(lateUser, withdrawn.requestId());
        receiptAndMemo(lateRequest.participationId());
        age(lateRequest.participationId(), 6);
        kit.jdbc.update("UPDATE group_buy_requests SET updated_at = now() - interval '4 years' WHERE id = ?", withdrawn.requestId());

        // 6년 전 배송완료 + 열린 요청(접수) — 끝날 때까지 미룬다
        long openUser = kit.newMember();
        JoinResult openRequest = kit.delivered(openUser, gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        kit.requests.create(openUser, openRequest.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CollectionMethod.CUSTOMER_SEND,
                Resolution.REFUND, List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, RequestReason.DEFECT)), "불량이에요", List.of(), null));
        receiptAndMemo(openRequest.participationId());
        age(openRequest.participationId(), 6);

        // 6년 전 배송완료 + 아직 보내지 않은 계좌 송금(계좌 받기 전) — 돈을 돌려줘야 하므로 미룬다
        JoinResult unsent = kit.delivered(kit.newMember(), gb, GroupBuyTestKit.lines(kit.mainSku(gb), 1));
        long userOfUnsent = ((Number) kit.jdbc.queryForObject("SELECT user_id FROM group_buy_participations WHERE id = ?", Long.class, unsent.participationId())).longValue();
        kit.jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, participation_id, user_id, amount, status, reason, created_at) "
                + "VALUES (?, 'GROUP_BUY_COMPENSATION', ?, ?, ?, 1000, 'AWAITING_ACCOUNT', '보상 송금 시험', now())", kit.ids.nextId(), kit.ids.nextId(), unsent.participationId(),
                userOfUnsent);
        receiptAndMemo(unsent.participationId());
        age(unsent.participationId(), 6);

        run();

        assertKept(recent.participationId(), "4년 전 배송완료");
        assertKept(lateRequest.participationId(), "마지막 거래(요청) 4년 전");
        assertKept(openRequest.participationId(), "열린 요청");
        assertKept(unsent.participationId(), "보내지 않은 계좌 송금");
        assertThat(kit.jdbc.queryForObject("SELECT status FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, JOB))
                .isIn("OK", "SKIP");
    }
}
