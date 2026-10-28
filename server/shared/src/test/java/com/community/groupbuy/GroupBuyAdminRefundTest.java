package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static com.community.groupbuy.entity.CollectionMethod.CUSTOMER_SEND;
import static com.community.groupbuy.entity.RequestReason.DEFECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.ReturnDisposition;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.service.GroupBuyAdminRefundService.Result;
import com.community.groupbuy.service.GroupBuyRequestService;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ManualCancelMethod;
import com.community.zconfiguration.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 운영 환불·고액 문턱·계좌 송금 전환·수동 처리 — 계약 결정 16·32·38·67·70·71·74 · G18·G21(두 번 환불)·G33·G34·G35.
 */
class GroupBuyAdminRefundTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    @AfterEach
    void restoreSettings() {
        kit.jdbc.update("UPDATE group_buy_settings SET large_refund_threshold = 100000 WHERE id = 1");
    }

    private static String code(Throwable e) {
        return ((GroupBuyException) e).getErrorCode();
    }

    @Test
    void G34_서버가_계산한_고액_반품은_일반_권한으로_한_번에_운영자_금액은_누계가_문턱부터_고액_권한() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).price(1_200_000).open();
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(kit.mainSku(gb), 1));
        var r = kit.requests.create(member, order.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CUSTOMER_SEND, null,
                List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, DEFECT)), null, null, null));
        kit.requests.approve(r.requestId(), null, kit.operator, "ip");
        kit.requests.receive(r.requestId(), kit.operator);
        kit.requests.complete(r.requestId(), List.of(new GroupBuyRequestService.DispositionInput(kit.mainSku(gb), ReturnDisposition.DISCARD, 1)), 0, null, false, kit.operator,
                "ip");
        var payment = kit.payment(order.checkout().paymentId());
        assertThat(kit.paymentCancels.findByOrder(payment.id())).singleElement().satisfies(c -> assertThat(c.amount()).isEqualTo(1_200_000));

        // 운영 환불 3만 + 8만 — 두 번째(누계 11만)부터 고액 권한, 없으면 승인 대기
        long other = kit.newMember();
        long gb2 = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).price(200_000).open();
        JoinResult o2 = kit.delivered(other, gb2, lines(kit.mainSku(gb2), 1));
        Result first = kit.adminRefunds.refund(o2.participationId(), null, 30_000L, false, "가격 조정 보상", false, kit.operator, "ip");
        assertThat(first.state()).isEqualTo("QUEUED");
        Result second = kit.adminRefunds.refund(o2.participationId(), null, 80_000L, false, "가격 조정 보상", false, kit.operator, "ip");
        assertThat(second.state()).isEqualTo("AWAITING_APPROVAL");
        assertThat(kit.participation(o2.participationId()).refundedAmount()).isEqualTo(30_000);
        Result approved = kit.adminRefunds.approvePending(second.pendingId(), kit.operator, "ip");
        assertThat(approved.state()).isEqualTo("QUEUED");
        assertThat(kit.participation(o2.participationId()).refundedAmount()).isEqualTo(110_000);
        kit.assertInvariants(gb, gb2);

        // 문턱 0 = 끔 — 모두 일반 권한
        kit.jdbc.update("UPDATE group_buy_settings SET large_refund_threshold = 0 WHERE id = 1");
        assertThat(kit.adminRefunds.refund(o2.participationId(), null, 50_000L, false, "가격 조정 보상", false, kit.operator, "ip").state()).isEqualTo("QUEUED");
    }

    @Test
    void 결정32_배송_중은_거절_발송_전은_취소와_같고_배송완료는_금액만_남은_결제액을_다_쓰면_REFUNDED() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long sku = kit.mainSku(gb);
        JoinResult pre = kit.paidOrder(kit.newMember(), gb, lines(sku, 3));
        Result r = kit.adminRefunds.refund(pre.participationId(), lines(sku, 2), null, false, "재고 부족으로 일부 취소", true, kit.operator, "ip");
        assertThat(r.amount()).as("발송 전 일부 — 재부과 3,000").isEqualTo(47_000);
        assertThat(kit.items(pre.participationId()).getFirst().canceledQty()).isEqualTo(2);
        Result fault = kit.adminRefunds.refund(pre.participationId(), null, null, true, "사업자 귀책 전체 취소", true, kit.operator, "ip");
        assertThat(fault.amount()).isEqualTo(28_000);
        assertThat(kit.participation(pre.participationId()).status()).isEqualTo(ParticipationStatus.CANCELED);

        JoinResult inTransit = kit.paidOrder(kit.newMember(), gb, lines(sku, 1));
        kit.ship(inTransit.participationId());
        assertThatThrownBy(() -> kit.adminRefunds.refund(inTransit.participationId(), null, 1_000L, false, "배송 중 환불 시도", true, kit.operator, "ip"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(GroupBuyException.REFUND_SHIPPED));

        JoinResult done = kit.delivered(kit.newMember(), gb, lines(sku, 2));
        kit.adminRefunds.refund(done.participationId(), null, 10_001L, false, "가격 조정 보상", true, kit.operator, "ip");
        assertThat(kit.jdbc.queryForObject("SELECT lines::text FROM group_buy_refunds WHERE participation_id = ? AND cause = 'ADMIN'", String.class, done.participationId()))
                .contains("amountOnly");
        assertThat(kit.group(gb).currentQty()).as("금액만 — 수량 이동 없음(취소 3 · 배송 중 1 · 배송완료 2)").isEqualTo(3);
        assertThatThrownBy(() -> kit.adminRefunds.refund(done.participationId(), null, 50_000L, false, "남은 결제액 초과", true, kit.operator, "ip"))
                .isInstanceOf(ValidationException.class);
        kit.adminRefunds.refund(done.participationId(), null, 39_999L, false, "남은 금액 전부", true, kit.operator, "ip");
        assertThat(kit.participation(done.participationId()).status()).isEqualTo(ParticipationStatus.REFUNDED);
        kit.assertInvariants(gb);
    }

    @Test
    void G33_결정71_하자_부분_반품_보상_뒤_운영_환불은_보상을_뺀_남은_결제액까지_반품은_앞선_운영_환불을_뺀다() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(20_000).open();
        long sku = kit.mainSku(gb);
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(sku, 2)); // 43,000
        kit.adminRefunds.refund(order.participationId(), null, 10_000L, false, "가격 조정 보상", true, kit.operator, "ip");
        var r = kit.requests.create(member, order.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CUSTOMER_SEND, null,
                List.of(new GroupBuyRequestService.LineInput(sku, 1, DEFECT)), null, null, null));
        assertThat(r.previewAmount()).as("20,000 − 앞선 운영 환불 줄 몫(10,000 전부가 한 줄 — 단품 하나)").isEqualTo(10_000);
        kit.requests.approve(r.requestId(), null, kit.operator, "ip");
        kit.requests.receive(r.requestId(), kit.operator);
        kit.requests.complete(r.requestId(), List.of(new GroupBuyRequestService.DispositionInput(sku, ReturnDisposition.DISCARD, 1)), 4_000, null, true, kit.operator, "ip");
        var p = kit.participation(order.participationId());
        assertThat(p.remainingCash()).isEqualTo(43_000 - 10_000 - 10_000 - 4_000);
        assertThatThrownBy(() -> kit.adminRefunds.refund(p.id(), null, 20_000L, false, "남은 결제액 초과", true, kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThat(kit.adminRefunds.refund(p.id(), null, 19_000L, false, "남은 결제액 전부", true, kit.operator, "ip").state()).isEqualTo("QUEUED");
        kit.assertInvariants(gb);
    }

    @Test
    void G35_G21_PG_가_거절해_멈춘_환불은_같은_금액을_다시_넣지_못하고_계좌_송금으로_전환() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        JoinResult order = kit.delivered(kit.newMember(), gb, lines(kit.mainSku(gb), 1));
        kit.pgCancelDown = true;
        Result r = kit.adminRefunds.refund(order.participationId(), null, 10_000L, false, "일부 보상 환불", true, kit.operator, "ip");
        var cancel = kit.paymentCancels.findByOrder(kit.payment(order.checkout().paymentId()).id()).getFirst();
        kit.jdbc.update("UPDATE payment_cancels SET status = 'GAVE_UP', completed_at = now(), locked_by = NULL, locked_at = NULL WHERE id = ?", cancel.id());
        assertThatThrownBy(() -> kit.adminRefunds.refund(order.participationId(), null, 28_000L, false, "같은 금액 다시", true, kit.operator, "ip"))
                .as("결정 38 — 누계가 이미 올랐다(남은 결제액 18,000)").isInstanceOf(ValidationException.class);
        long transferId = kit.bankTransfers.convertRefund(r.refundId(), "PG 취소 기간 지남", kit.operator, "ip");
        assertThat(kit.paymentCancels.findById(cancel.id()).orElseThrow().status()).isEqualTo(CancelStatus.MANUAL);
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE id = ? AND method = 'BANK_TRANSFER' AND status = 'IN_PROGRESS'", r.refundId())).isEqualTo(1);
        kit.assertInvariants(gb);
        kit.bankTransfers.registerAccount(transferId, "시험은행", "110-222-333333", "홍길동");
        kit.bankTransfers.markSent(transferId, kit.operator, "ip");
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE id = ? AND status = 'COMPLETED'", r.refundId())).isEqualTo(1);
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.action()).isEqualTo(com.community.audit.dto.AuditActions.GROUPBUY_BANK_TRANSFER));
        kit.assertInvariants(gb);
    }

    @Test
    void G35_G18_콘솔_취소로_수동_처리하면_취소_누계까지_올리고_환불_기록은_MANUAL_이벤트() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        JoinResult order = kit.delivered(kit.newMember(), gb, lines(kit.mainSku(gb), 1));
        kit.pgCancelDown = true;
        Result r = kit.adminRefunds.refund(order.participationId(), null, 5_000L, false, "부분 보상 환불", true, kit.operator, "ip");
        var payment = kit.payment(order.checkout().paymentId());
        var cancel = kit.paymentCancels.findByOrder(payment.id()).getFirst();
        kit.jdbc.update("UPDATE payment_cancels SET status = 'GAVE_UP', completed_at = now(), locked_by = NULL, locked_at = NULL WHERE id = ?", cancel.id());
        kit.tx.executeWithoutResult(s -> kit.manualCancels.resolve(cancel.id(), ManualCancelMethod.CONSOLE_CANCEL, "console-cancel-1", "콘솔에서 취소함", kit.operator));
        assertThat(kit.payment(order.checkout().paymentId()).canceledAmount()).isEqualTo(5_000);
        assertThat(kit.paymentCancels.succeededAmount(payment.id())).as("취소 누계 = SUCCEEDED + 콘솔 수동").isEqualTo(5_000);
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE id = ? AND status = 'MANUAL' AND completed_at IS NOT NULL", r.refundId())).isEqualTo(1);
        assertThat(kit.eventsOf(GroupBuyEvents.RefundResolvedManually.class)).anySatisfy(e -> assertThat(e.refundId()).isEqualTo(r.refundId()));
        assertThat(kit.count("SELECT count(*) FROM payment_cancels WHERE id = ? AND manual_method = 'CONSOLE_CANCEL' AND pg_cancel_id = 'console-cancel-1'", cancel.id()))
                .isEqualTo(1);
        kit.assertInvariants(gb);
    }
}
