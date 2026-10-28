package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static com.community.groupbuy.entity.CollectionMethod.COMPANY_PICKUP;
import static com.community.groupbuy.entity.CollectionMethod.CUSTOMER_SEND;
import static com.community.groupbuy.entity.RequestReason.CHANGE_OF_MIND;
import static com.community.groupbuy.entity.RequestReason.DEFECT;
import static com.community.groupbuy.entity.ReturnDisposition.DISCARD;
import static com.community.groupbuy.entity.ReturnDisposition.RESELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QtyMovementKind;
import com.community.groupbuy.entity.RejectReason;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.entity.ReturnDisposition;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.service.GroupBuyRequestService.Created;
import com.community.groupbuy.service.GroupBuyRequestService.DispositionInput;
import com.community.groupbuy.service.GroupBuyRequestService.LineInput;
import com.community.groupbuy.service.GroupBuyRequestService.RequestInput;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.service.PaymentConfirmService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 취소·반품 요청(commerce.md 10-3·10-8 · 18절 P33·P34·P36~P42, 계약 결정 15·44·54·55·68·69·72·73·83 · G1·G6·G7·G14·G15·G30·G31·G36·G39·G40).
 */
class GroupBuyRequestTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private static RequestInput ret(CollectionMethod method, long skuId, int qty, RequestReason reason) {
        return new RequestInput(RequestType.RETURN, method, null, List.of(new LineInput(skuId, qty, reason)), null, null, null);
    }

    /** 승인 → 회수 확인 → 완료(줄마다 처리 방법) */
    private void approveAndComplete(long requestId, List<DispositionInput> dispositions, long compensation) {
        kit.requests.approve(requestId, null, kit.operator, "ip");
        kit.requests.receive(requestId, kit.operator);
        kit.requests.complete(requestId, dispositions, compensation, null, false, kit.operator, "ip");
    }

    private GroupBuyRequestStore.RequestRow request(long id) {
        return kit.spring.getBean(GroupBuyRequestStore.class).find(id).orElseThrow();
    }

    private long refundOf(long requestId, String cause) {
        Long n = kit.jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM group_buy_refunds WHERE request_id = ? AND cause = ?", Long.class, requestId, cause);
        return n == null ? 0 : n;
    }

    private static String code(Throwable e) {
        return ((GroupBuyException) e).getErrorCode();
    }

    @Test
    void P33_P34_반품_기한은_단순_변심_7일_하자_3개월() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(kit.mainSku(gb), 2));
        kit.advance(Duration.ofDays(40));
        assertThatThrownBy(() -> kit.requests.create(member, order.participationId(), ret(COMPANY_PICKUP, kit.mainSku(gb), 1, CHANGE_OF_MIND)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.RETURN_WINDOW_CLOSED));
        Created created = kit.requests.create(member, order.participationId(), ret(COMPANY_PICKUP, kit.mainSku(gb), 1, DEFECT));
        assertThat(created.status()).isEqualTo(RequestStatus.REQUESTED);
        assertThat(created.previewAmount()).as("하자 — 반품비 없이 전액").isEqualTo(25_000);
    }

    /** 업체 부담 반품 + 「하자를 안 날」 */
    private static RequestInput defectKnown(long skuId, RequestReason reason, LocalDate knownOn) {
        return new RequestInput(RequestType.RETURN, COMPANY_PICKUP, null, List.of(new LineInput(skuId, 1, reason)), null, null, null, knownOn);
    }

    @Test
    void P33_보강_3개월이_지난_하자는_안_날부터_30일_미래와_배송_전_날짜는_400_변심은_7일_운영자는_그_날짜를_보고_거절() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long sku = kit.mainSku(gb);
        long member = kit.newMember();
        long other = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(sku, 1));
        JoinResult o2 = kit.delivered(other, gb, lines(sku, 1));
        LocalDate deliveredOn = ServiceTime.dateOf(kit.now());
        kit.advance(Duration.ofDays(100)); // 배송완료 + 3개월이 지났다
        LocalDate today = ServiceTime.dateOf(kit.now());
        long oid = order.participationId();

        assertThatThrownBy(() -> kit.requests.create(member, oid, defectKnown(sku, DEFECT, null))).as("안 날을 적지 않으면 3개월로 끝")
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.RETURN_WINDOW_CLOSED));
        assertThatThrownBy(() -> kit.requests.create(member, oid, defectKnown(sku, DEFECT, today.minusDays(31)))).as("안 날부터 31일")
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.RETURN_WINDOW_CLOSED));
        assertThatThrownBy(() -> kit.requests.create(member, oid, defectKnown(sku, DEFECT, today.plusDays(1)))).as("미래 날짜").isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.requests.create(member, oid, defectKnown(sku, DEFECT, deliveredOn.minusDays(1)))).as("배송완료 전 날짜")
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.requests.create(member, oid, defectKnown(sku, CHANGE_OF_MIND, today.minusDays(1)))).as("단순 변심은 안 날과 관계없이 7일")
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.RETURN_WINDOW_CLOSED));

        Created ok = kit.requests.create(member, oid, defectKnown(sku, DEFECT, today.minusDays(30)));
        assertThat(ok.status()).isEqualTo(RequestStatus.REQUESTED);
        assertThat(ok.previewAmount()).as("하자 전체 반품 — 처음 배송비까지 전액").isEqualTo(28_000);
        assertThat(request(ok.requestId()).defectKnownOn()).as("운영자가 보는 날짜").isEqualTo(today.minusDays(30));

        Created disputed = kit.requests.create(other, o2.participationId(), defectKnown(sku, RequestReason.WRONG_ITEM, today));
        long photo = kit.requests.registerOperatorPhoto(kit.operator, "e/known.jpg");
        kit.requests.reject(disputed.requestId(), RejectReason.OUT_OF_WINDOW, "배송 당일 하자 문의 기록이 있어 안 날이 맞지 않아요", List.of(photo), null, null,
                kit.operator, "ip");
        assertThat(request(disputed.requestId()).status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(request(disputed.requestId()).rejectReasonCode()).isEqualTo("OUT_OF_WINDOW");
        assertThat(kit.audits).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_REQUEST_REJECT);
            assertThat(e.targetId()).isEqualTo(Long.toString(disputed.requestId()));
            assertThat(e.detail()).containsEntry("defectKnownOn", today.toString()).containsEntry("reasonCode", "OUT_OF_WINDOW");
        });
        assertThat(kit.participation(o2.participationId()).status()).isEqualTo(ParticipationStatus.DELIVERED);
    }

    @Test
    void G7_G6_25000x3_무료배송을_1개씩_세_번_반품하면_22000_19000_25000_그리고_REFUNDED() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(kit.mainSku(gb), 3));
        long sku = kit.mainSku(gb);
        long[] expected = {22_000, 19_000, 25_000};
        for (long amount : expected) {
            Created r = kit.requests.create(member, order.participationId(), ret(COMPANY_PICKUP, sku, 1, CHANGE_OF_MIND));
            assertThat(r.previewAmount()).isEqualTo(amount);
            approveAndComplete(r.requestId(), List.of(new DispositionInput(sku, DISCARD, 1)), 0);
            assertThat(refundOf(r.requestId(), "REQUEST")).isEqualTo(amount);
            kit.assertInvariants(gb);
        }
        var p = kit.participation(order.participationId());
        assertThat(p.status()).as("받은 뒤 전부 반품 — 반품비가 남아도 REFUNDED(결정 46)").isEqualTo(ParticipationStatus.REFUNDED);
        assertThat(p.refundedAmount()).isEqualTo(66_000);
        assertThat(kit.payment(order.checkout().paymentId()).canceledAmount()).isEqualTo(66_000);
        assertThat(kit.items(p.id()).getFirst().returnedQty()).isEqualTo(3);
        assertThat(kit.group(gb).currentQty()).as("폐기 반품은 판매 수량 그대로(결정 28)").isEqualTo(3);
        assertThat(kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'RETURN' AND reason = 'DISCARD'", p.id())).isEqualTo(3);
        // G30 — 그 뒤 운영 환불은 거절
        assertThatThrownBy(() -> kit.adminRefunds.refund(p.id(), null, 1_000L, false, "가격 조정 시도", true, kit.operator, "ip"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.ORDER_STATE));
    }

    @Test
    void G1_다시_판매는_모집_중_단순_변심만_하자는_거절_마감_뒤는_거절_폐기는_수량_그대로() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long sku = kit.mainSku(gb);
        long a = kit.newMember();
        long b = kit.newMember();
        long c = kit.newMember();
        JoinResult oa = kit.delivered(a, gb, lines(sku, 2));
        JoinResult ob = kit.delivered(b, gb, lines(sku, 1));
        JoinResult oc = kit.delivered(c, gb, lines(sku, 1));
        assertThat(kit.group(gb).currentQty()).isEqualTo(4);

        Created ra = kit.requests.create(a, oa.participationId(), ret(CUSTOMER_SEND, sku, 2, CHANGE_OF_MIND));
        approveAndComplete(ra.requestId(), List.of(new DispositionInput(sku, RESELL, 1), new DispositionInput(sku, DISCARD, 1)), 0);
        assertThat(kit.group(gb).currentQty()).as("다시 판매 1 — 확정 감소").isEqualTo(3);
        assertThat(kit.items(oa.participationId()).getFirst().restockedQty()).isEqualTo(1);
        assertThat(kit.eventsOf(GroupBuyEvents.QtyReleased.class)).anySatisfy(e -> assertThat(e.kind()).isEqualTo(QtyMovementKind.RESTOCK));
        kit.assertInvariants(gb);

        Created rb = kit.requests.create(b, ob.participationId(), ret(CUSTOMER_SEND, sku, 1, DEFECT));
        kit.requests.approve(rb.requestId(), null, kit.operator, "ip");
        kit.requests.receive(rb.requestId(), kit.operator);
        assertThatThrownBy(() -> kit.requests.complete(rb.requestId(), List.of(new DispositionInput(sku, RESELL, 1)), 0, null, false, kit.operator, "ip"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.NOT_RESELLABLE));

        Created rc = kit.requests.create(c, oc.participationId(), ret(CUSTOMER_SEND, sku, 1, CHANGE_OF_MIND));
        kit.requests.approve(rc.requestId(), null, kit.operator, "ip");
        kit.requests.receive(rc.requestId(), kit.operator);
        kit.admin.closeEarly(gb, "조기 마감 시험", kit.operator, "ip");
        assertThatThrownBy(() -> kit.requests.complete(rc.requestId(), List.of(new DispositionInput(sku, RESELL, 1)), 0, null, false, kit.operator, "ip"))
                .as("CLOSING 뒤").satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.NOT_RESELLABLE));
        int before = kit.group(gb).currentQty();
        kit.requests.complete(rc.requestId(), List.of(new DispositionInput(sku, ReturnDisposition.KEEP, 1)), 0, null, false, kit.operator, "ip");
        assertThat(kit.group(gb).currentQty()).isEqualTo(before);
        assertThatThrownBy(() -> kit.requests.complete(rb.requestId(), List.of(new DispositionInput(sku, DISCARD, 2)), 0, null, false, kit.operator, "ip"))
                .as("합 ≠ 줄 수량").isInstanceOf(ValidationException.class);
        kit.assertInvariants(gb);
    }

    @Test
    void G31_업체회수가_마이너스면_부족분을_결제해야_접수되고_고객발송은_2000_환불() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).price(5_000).supply(1_000L).option("본체", 42_000, null).option("부품", 0, null).open();
        long main = kit.skuId(gb, "본체");
        long part = kit.skuId(gb, "부품");

        // 업체회수 −1,000 → 추가 결제 뒤 접수, 완료 때 낸 1,000 이 메운다(주문 결제 환불 0 — NONE)
        long a = kit.newMember();
        JoinResult oa = kit.delivered(a, gb, lines(main, 1, part, 1));
        var preview = kit.requests.preview(a, oa.participationId(), RequestType.RETURN, List.of(new LineInput(part, 1, CHANGE_OF_MIND)));
        assertThat(preview.byMethod().get(COMPANY_PICKUP).total()).isEqualTo(-1_000);
        assertThat(preview.byMethod().get(CUSTOMER_SEND).total()).isEqualTo(2_000);
        Created ra = kit.requests.create(a, oa.participationId(), ret(COMPANY_PICKUP, part, 1, CHANGE_OF_MIND));
        assertThat(ra.status()).isEqualTo(RequestStatus.AWAITING_FEE);
        assertThat(ra.feePayment().amount()).isEqualTo(1_000);
        assertThatThrownBy(() -> kit.requests.approve(ra.requestId(), null, kit.operator, "ip")).as("접수 전")
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REQUEST_NOT_OPEN));
        assertThat(kit.payAt(ra.feePayment().paymentId(), 1_000, kit.now()).outcome()).isEqualTo(PaymentConfirmService.Outcome.ACCEPTED);
        assertThat(request(ra.requestId()).status()).isEqualTo(RequestStatus.REQUESTED);
        approveAndComplete(ra.requestId(), List.of(new DispositionInput(part, DISCARD, 1)), 0);
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE request_id = ? AND cause = 'REQUEST' AND amount = 0 AND method = 'NONE' AND fee_covered = 1000 "
                + "AND return_fee = 3000 AND reshipping_fee = 3000", ra.requestId())).isEqualTo(1);
        assertThat(kit.participation(oa.participationId()).reshippingFeeCharged()).as("받은 돈까지 재부과로 남긴다").isEqualTo(3_000);
        kit.assertInvariants(gb);

        // 고객발송 — 추가 결제 없이 2,000
        long b = kit.newMember();
        JoinResult ob = kit.delivered(b, gb, lines(main, 1, part, 1));
        Created rb = kit.requests.create(b, ob.participationId(), ret(CUSTOMER_SEND, part, 1, CHANGE_OF_MIND));
        assertThat(rb.feePayment()).isNull();
        approveAndComplete(rb.requestId(), List.of(new DispositionInput(part, DISCARD, 1)), 0);
        assertThat(refundOf(rb.requestId(), "REQUEST")).isEqualTo(2_000);

        // 결제하지 않으면 접수 안 됨 — 하루 뒤 닫힘, 결제창도 닫힘
        long c = kit.newMember();
        JoinResult oc = kit.delivered(c, gb, lines(main, 1, part, 1));
        Created rc = kit.requests.create(c, oc.participationId(), ret(COMPANY_PICKUP, part, 1, CHANGE_OF_MIND));
        // 접수 뒤 거절 — 낸 1,000 을 그 결제에서 돌려준다(RETURN_FEE_REFUND)
        long d = kit.newMember();
        JoinResult od = kit.delivered(d, gb, lines(main, 1, part, 1));
        Created rd = kit.requests.create(d, od.participationId(), ret(COMPANY_PICKUP, part, 1, CHANGE_OF_MIND));
        kit.payAt(rd.feePayment().paymentId(), 1_000, kit.now());
        kit.advance(Duration.ofHours(25));
        kit.fulfillmentWorker.runOnce();
        assertThat(request(rc.requestId()).status()).isEqualTo(RequestStatus.EXPIRED);
        assertThat(kit.payment(rc.feePayment().paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);

        long photo = kit.requests.registerOperatorPhoto(kit.operator, "evidence/1.jpg");
        kit.requests.reject(rd.requestId(), RejectReason.CUSTOMER_DAMAGE, "포장 훼손", List.of(photo), null, null, kit.operator, "ip");
        assertThat(refundOf(rd.requestId(), "RETURN_FEE_REFUND")).isEqualTo(1_000);
        assertThat(kit.payment(rd.feePayment().paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(request(rd.requestId()).feeRefundedAmount()).isEqualTo(1_000);
        kit.assertInvariants(gb);
    }

    @Test
    void G14_한_요청에_하자_줄과_변심_줄이면_반품비는_운영자_부담() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).price(20_000).option("A", 0, null).option("B", 0, null).open();
        long a = kit.skuId(gb, "A");
        long b = kit.skuId(gb, "B");
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(a, 1, b, 2));
        Created r = kit.requests.create(member, order.participationId(), new RequestInput(RequestType.RETURN, COMPANY_PICKUP, null,
                List.of(new LineInput(a, 1, DEFECT), new LineInput(b, 1, CHANGE_OF_MIND)), null, null, null));
        assertThat(r.previewAmount()).isEqualTo(40_000);
        approveAndComplete(r.requestId(), List.of(new DispositionInput(a, DISCARD, 1), new DispositionInput(b, DISCARD, 1)), 0);
        assertThat(kit.jdbc.queryForList("SELECT refund_amount FROM group_buy_request_items WHERE request_id = ? ORDER BY sku_id", Long.class, r.requestId()))
                .containsExactlyInAnyOrder(20_000L, 20_000L);
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE request_id = ? AND return_fee = 0", r.requestId())).isEqualTo(1);
        kit.assertInvariants(gb);
    }

    @Test
    void P41_G15_고객발송_하자_전체_반품_보상은_남은_결제액을_넘는_몫을_계좌로() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(39_000).open();
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(kit.mainSku(gb), 1));
        Created r = kit.requests.create(member, order.participationId(), ret(CUSTOMER_SEND, kit.mainSku(gb), 1, DEFECT));
        kit.requests.approve(r.requestId(), null, kit.operator, "ip");
        kit.requests.receive(r.requestId(), kit.operator);
        assertThatThrownBy(() -> kit.requests.complete(r.requestId(), List.of(new DispositionInput(kit.mainSku(gb), DISCARD, 1)), 10_001, null, true, kit.operator, "ip"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.COMPENSATION_OVER_CAP));
        kit.requests.complete(r.requestId(), List.of(new DispositionInput(kit.mainSku(gb), DISCARD, 1)), 4_000, null, false, kit.operator, "ip");
        assertThat(refundOf(r.requestId(), "REQUEST")).as("결제액 전액(처음 배송비 포함)").isEqualTo(42_000);
        var transfer = kit.jdbc.queryForMap("SELECT id, amount, status FROM commerce_bank_transfers WHERE participation_id = ?", order.participationId());
        assertThat(((Number) transfer.get("amount")).longValue()).as("보상 4,000 은 남은 결제액을 넘어 계좌로").isEqualTo(4_000);
        assertThat(transfer.get("status")).isEqualTo("AWAITING_ACCOUNT");
        kit.assertInvariants(gb);
        long transferId = ((Number) transfer.get("id")).longValue();
        assertThatThrownBy(() -> kit.bankTransfers.markSent(transferId, kit.operator, "ip")).as("계좌 받기 전").isInstanceOf(GroupBuyException.class);
        kit.bankTransfers.registerAccount(transferId, "시험은행", "123-456-7890", "홍길동");
        kit.bankTransfers.markSent(transferId, kit.operator, "ip");
        assertThat(kit.count("SELECT count(*) FROM commerce_bank_transfers WHERE id = ? AND status = 'SENT' AND account_no_enc IS NULL AND account_erased_at IS NOT NULL",
                transferId)).isEqualTo(1);
    }

    @Test
    void G15_보상이_남은_결제액_안이면_PG_취소에_함께_보상_누계에_따로() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(20_000).open();
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(kit.mainSku(gb), 2));
        Created r = kit.requests.create(member, order.participationId(), ret(CUSTOMER_SEND, kit.mainSku(gb), 1, DEFECT));
        approveAndComplete(r.requestId(), List.of(new DispositionInput(kit.mainSku(gb), DISCARD, 1)), 4_000);
        var p = kit.participation(order.participationId());
        assertThat(p.refundedAmount()).isEqualTo(20_000);
        assertThat(p.compensationAmount()).isEqualTo(4_000);
        assertThat(kit.payment(order.checkout().paymentId()).canceledAmount()).isEqualTo(24_000);
        assertThatThrownBy(() -> kit.requests.complete(r.requestId(), List.of(), 0, null, false, kit.operator, "ip")).isInstanceOf(GroupBuyException.class);
        kit.assertInvariants(gb);
        // 단순 변심에는 보상 없음
        Created again = kit.requests.create(member, order.participationId(), ret(CUSTOMER_SEND, kit.mainSku(gb), 1, CHANGE_OF_MIND));
        kit.requests.approve(again.requestId(), null, kit.operator, "ip");
        assertThatThrownBy(() -> kit.requests.complete(again.requestId(), List.of(new DispositionInput(kit.mainSku(gb), DISCARD, 1)), 1_000, null, false, kit.operator, "ip"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void G36_콘솔_직접_취소_대사_이슈가_열린_결제는_승인_거절() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(kit.mainSku(gb), 1));
        Created r = kit.requests.create(member, order.participationId(), ret(CUSTOMER_SEND, kit.mainSku(gb), 1, DEFECT));
        var payment = kit.payment(order.checkout().paymentId());
        kit.jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, payment_order_id, detected_day, status, created_at) VALUES (?, 'CANCEL_MISMATCH', ?, ?, "
                + "current_date, 'OPEN', now())", kit.ids.nextId(), payment.paymentId(), payment.id());
        assertThatThrownBy(() -> kit.requests.approve(r.requestId(), null, kit.operator, "ip"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.PAYMENT_UNRECONCILED));
        kit.jdbc.update("UPDATE reconciliation_issues SET status = 'RESOLVED', resolved_at = now() WHERE payment_order_id = ?", payment.id());
        kit.requests.approve(r.requestId(), null, kit.operator, "ip");
    }

    @Test
    void G40_반품_거절은_사유_코드와_근거_사진_가치_감소는_청약철회_제한_안내가_있을_때_단순_변심만() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long plain = kit.spec(policy).open();
        long restricted = kit.spec(policy).withdrawalRestriction("포장을 뜯어 사용하면 가치가 줄어 반품할 수 없어요").open();
        long m1 = kit.newMember();
        long m2 = kit.newMember();
        long m3 = kit.newMember();
        JoinResult o1 = kit.delivered(m1, plain, lines(kit.mainSku(plain), 1));
        JoinResult o2 = kit.delivered(m2, restricted, lines(kit.mainSku(restricted), 1));
        JoinResult o3 = kit.delivered(m3, restricted, lines(kit.mainSku(restricted), 1));
        Created r1 = kit.requests.create(m1, o1.participationId(), ret(CUSTOMER_SEND, kit.mainSku(plain), 1, CHANGE_OF_MIND));
        Created r2 = kit.requests.create(m2, o2.participationId(), ret(CUSTOMER_SEND, kit.mainSku(restricted), 1, CHANGE_OF_MIND));
        Created r3 = kit.requests.create(m3, o3.participationId(), ret(CUSTOMER_SEND, kit.mainSku(restricted), 1, DEFECT));
        long photo1 = kit.requests.registerOperatorPhoto(kit.operator, "e/1.jpg");
        assertThatThrownBy(() -> kit.requests.reject(r1.requestId(), RejectReason.CUSTOMER_DAMAGE, null, List.of(), null, null, kit.operator, "ip")).as("사진 없음")
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REJECT_REASON_NOT_ALLOWED));
        assertThatThrownBy(() -> kit.requests.reject(r1.requestId(), RejectReason.USED_VALUE_LOSS, null, List.of(photo1), null, null, kit.operator, "ip"))
                .as("⑥ 표시 없으면 제한 못 함").satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REJECT_REASON_NOT_ALLOWED));
        assertThatThrownBy(() -> kit.requests.reject(r1.requestId(), RejectReason.ALREADY_SHIPPED, null, List.of(photo1), null, null, kit.operator, "ip"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REJECT_REASON_NOT_ALLOWED));
        long photo3 = kit.requests.registerOperatorPhoto(kit.operator, "e/3.jpg");
        assertThatThrownBy(() -> kit.requests.reject(r3.requestId(), RejectReason.USED_VALUE_LOSS, null, List.of(photo3), null, null, kit.operator, "ip"))
                .as("하자 반품은 가치 감소로 거절 못 함").satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REJECT_REASON_NOT_ALLOWED));
        long photo2 = kit.requests.registerOperatorPhoto(kit.operator, "e/2.jpg");
        kit.requests.reject(r2.requestId(), RejectReason.USED_VALUE_LOSS, "개봉·사용 흔적", List.of(photo2), null, null, kit.operator, "ip");
        assertThat(request(r2.requestId()).status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(request(r2.requestId()).rejectReasonCode()).isEqualTo("USED_VALUE_LOSS");
        assertThat(kit.count("SELECT count(*) FROM group_buy_request_photos WHERE request_id = ? AND owner_kind = 'OPERATOR'", r2.requestId())).isEqualTo(1);
        assertThat(kit.participation(o2.participationId()).status()).as("거절은 주문 상태를 흔들지 않는다").isEqualTo(ParticipationStatus.DELIVERED);
    }

    @Test
    void G39_하자_교환은_같은_주문에_재발송_환불_0_교환_출고_기록_교환할_물건이_없으면_환불로() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long sku = kit.mainSku(gb);
        long a = kit.newMember();
        JoinResult oa = kit.delivered(a, gb, lines(sku, 1));
        assertThatThrownBy(() -> kit.requests.create(a, oa.participationId(), new RequestInput(RequestType.RETURN, CUSTOMER_SEND, Resolution.EXCHANGE,
                List.of(new LineInput(sku, 1, CHANGE_OF_MIND)), null, null, null))).satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.EXCHANGE_NOT_ALLOWED));
        Created r = kit.requests.create(a, oa.participationId(), new RequestInput(RequestType.RETURN, COMPANY_PICKUP, Resolution.EXCHANGE,
                List.of(new LineInput(sku, 1, DEFECT)), "켜지지 않아요", null, null));
        kit.requests.approve(r.requestId(), null, kit.operator, "ip");
        kit.requests.recordPickup(r.requestId(), "CJ", "777766665555", kit.operator);
        kit.requests.receive(r.requestId(), kit.operator);
        int current = kit.group(gb).currentQty();
        kit.requests.exchangeShip(r.requestId(), "CJ", "555544443333", List.of(new DispositionInput(sku, ReturnDisposition.RETURN_TO_SUPPLIER, 1)), 0, false, kit.operator, "ip");
        assertThat(request(r.requestId()).status()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(kit.count("SELECT count(*) FROM group_buy_shipments WHERE participation_id = ? AND kind = 'EXCHANGE'", oa.participationId())).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE request_id = ?", r.requestId())).as("환불 0").isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'EXCHANGE_OUT'", oa.participationId())).isEqualTo(1);
        assertThat(kit.group(gb).currentQty()).as("판매 수량 그대로").isEqualTo(current);
        assertThat(kit.items(oa.participationId()).getFirst().returnedQty()).isZero();
        assertThat(kit.fakeGateway.escrowCalls()).anySatisfy(c -> assertThat(c.logistics() != null && "555544443333".equals(c.logistics().trackingNo())).isTrue());
        kit.assertInvariants(gb);

        long b = kit.newMember();
        JoinResult ob = kit.delivered(b, gb, lines(sku, 1));
        Created rb = kit.requests.create(b, ob.participationId(), new RequestInput(RequestType.RETURN, CUSTOMER_SEND, Resolution.EXCHANGE,
                List.of(new LineInput(sku, 1, RequestReason.WRONG_ITEM)), null, null, null));
        kit.requests.approve(rb.requestId(), null, kit.operator, "ip");
        kit.requests.receive(rb.requestId(), kit.operator);
        kit.requests.convertToRefund(rb.requestId(), kit.operator);
        assertThat(kit.eventsOf(GroupBuyEvents.RequestChanged.class)).anySatisfy(e -> assertThat(e.reason()).isEqualTo("EXCHANGE_TO_REFUND"));
        kit.requests.complete(rb.requestId(), List.of(new DispositionInput(sku, DISCARD, 1)), 0, null, false, kit.operator, "ip");
        assertThat(refundOf(rb.requestId(), "REQUEST")).as("오배송 전체 — 결제액 전액").isEqualTo(28_000);
        kit.assertInvariants(gb);
    }

    @Test
    void 발송_전_취소_요청은_승인하면_그_줄만_취소_재부과_거절은_이미_발송함_송장으로만() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long sku = kit.mainSku(gb);
        long a = kit.newMember();
        JoinResult oa = kit.paidOrder(a, gb, lines(sku, 3));
        kit.fulfillment.prepare(List.of(oa.participationId()), kit.operator, "ip");
        assertThatThrownBy(() -> kit.cancels.cancelByMember(a, oa.participationId(), null)).as("준비중 뒤는 요청으로")
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.CANCEL_NOT_ALLOWED));
        Created r = kit.requests.create(a, oa.participationId(), new RequestInput(RequestType.CANCEL, null, null, List.of(new LineInput(sku, 2, null)), null, null, null));
        assertThat(r.previewAmount()).isEqualTo(47_000);
        assertThatThrownBy(() -> kit.requests.create(a, oa.participationId(), new RequestInput(RequestType.CANCEL, null, null, List.of(new LineInput(sku, 1, null)), null, null,
                null))).satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REQUEST_OPEN));
        kit.requests.approve(r.requestId(), null, kit.operator, "ip");
        var p = kit.participation(oa.participationId());
        assertThat(p.status()).isEqualTo(ParticipationStatus.PREPARING);
        assertThat(p.refundedAmount()).isEqualTo(47_000);
        assertThat(request(r.requestId()).status()).isEqualTo(RequestStatus.COMPLETED);
        kit.assertInvariants(gb);

        long b = kit.newMember();
        JoinResult ob = kit.paidOrder(b, gb, lines(sku, 1));
        kit.fulfillment.prepare(List.of(ob.participationId()), kit.operator, "ip");
        Created rb = kit.requests.create(b, ob.participationId(), new RequestInput(RequestType.CANCEL, null, null, List.of(new LineInput(sku, 1, null)), null, null, null));
        assertThatThrownBy(() -> kit.requests.reject(rb.requestId(), RejectReason.CUSTOMER_DAMAGE, null, List.of(), "CJ", "123456789", kit.operator, "ip"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REJECT_REASON_NOT_ALLOWED));
        kit.requests.reject(rb.requestId(), RejectReason.ALREADY_SHIPPED, null, List.of(), "CJ", "123456789", kit.operator, "ip");
        assertThat(kit.participation(ob.participationId()).status()).isEqualTo(ParticipationStatus.SHIPPED);
        assertThat(request(rb.requestId()).status()).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    void 결정72_승인_때_다시_계산한_값이_미리_보기와_다르면_두_값을_보이고_확인_뒤에만() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long sku = kit.mainSku(gb);
        long a = kit.newMember();
        JoinResult oa = kit.paidOrder(a, gb, lines(sku, 3));
        kit.fulfillment.prepare(List.of(oa.participationId()), kit.operator, "ip");
        Created r = kit.requests.create(a, oa.participationId(), new RequestInput(RequestType.CANCEL, null, null, List.of(new LineInput(sku, 1, null)), null, null, null));
        assertThat(r.previewAmount()).isEqualTo(25_000);
        kit.jdbc.update("UPDATE group_buy_requests SET preview_amount = 24000 WHERE id = ?", r.requestId()); // 미리 본 뒤 조건이 바뀐 경우를 만든다
        assertThatThrownBy(() -> kit.requests.approve(r.requestId(), null, kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.REFUND_AMOUNT_CHANGED);
        });
        kit.requests.approve(r.requestId(), 25_000L, kit.operator, "ip");
        assertThat(kit.participation(oa.participationId()).refundedAmount()).isEqualTo(25_000);
    }

    @Test
    void G20_반품은_처리_방법과_관계없이_1인_한도에서_빠진다() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).perUser(2).open();
        long sku = kit.mainSku(gb);
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(sku, 2));
        assertThatThrownBy(() -> kit.order(member, gb, lines(sku, 1))).satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.LIMIT_EXCEEDED));
        Created r = kit.requests.create(member, order.participationId(), ret(CUSTOMER_SEND, sku, 1, CHANGE_OF_MIND));
        approveAndComplete(r.requestId(), List.of(new DispositionInput(sku, DISCARD, 1)), 0);
        kit.paidOrder(member, gb, lines(sku, 1));
        kit.assertInvariants(gb);
    }

    @Test
    void 결정69_지연_공지_뒤_전액_취소는_열린_요청을_SUPERSEDED_로_닫는다() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long sku = kit.mainSku(gb);
        long member = kit.newMember();
        JoinResult order = kit.paidOrder(member, gb, lines(sku, 2));
        kit.fulfillment.prepare(List.of(order.participationId()), kit.operator, "ip");
        Created r = kit.requests.create(member, order.participationId(), new RequestInput(RequestType.CANCEL, null, null, List.of(new LineInput(sku, 1, null)), null, null, null));
        kit.admin.announceDelay(gb, java.time.LocalDate.now().plusDays(30), "공급처 입고 지연", kit.operator, "ip");
        kit.cancels.cancelByMember(member, order.participationId(), null);
        assertThat(kit.participation(order.participationId()).status()).isEqualTo(ParticipationStatus.CANCELED);
        assertThat(request(r.requestId()).status()).isEqualTo(RequestStatus.SUPERSEDED);
        kit.assertInvariants(gb);
    }

    @Test
    void 반품도_본_상품을_모두_빼며_추가상품만_남기면_409_철회하면_다시_요청할_수_있다() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).addon("충전기", 10_000, null).open();
        long main = kit.mainSku(gb);
        long addon = kit.skus(gb).stream().filter(s -> !s.main()).findFirst().orElseThrow().id();
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(main, 1, addon, 1));
        assertThatThrownBy(() -> kit.requests.create(member, order.participationId(), ret(CUSTOMER_SEND, main, 1, DEFECT)))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.ADDON_ONLY));
        Created r = kit.requests.create(member, order.participationId(), ret(CUSTOMER_SEND, addon, 1, DEFECT));
        kit.requests.withdraw(member, r.requestId());
        assertThat(request(r.requestId()).status()).isEqualTo(RequestStatus.WITHDRAWN);
        Created both = kit.requests.create(member, order.participationId(), new RequestInput(RequestType.RETURN, CUSTOMER_SEND, null,
                List.of(new LineInput(main, 1, DEFECT), new LineInput(addon, 1, DEFECT)), null, null, null));
        assertThat(both.previewAmount()).as("하자 전체 — 낸 배송비 3,000 까지").isEqualTo(38_000);
    }
}
