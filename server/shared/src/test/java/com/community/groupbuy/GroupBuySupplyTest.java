package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.ExpireCause;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.SupplyChoice;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.handler.GroupBuyPaymentHandler;
import com.community.groupbuy.service.FulfillmentService;
import com.community.groupbuy.service.GroupBuyBusinessDays;
import com.community.groupbuy.service.GroupBuySupplyService;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.service.PaymentConfirmService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 단품 공급 불가(계약 결정 53 · G38)·영업일(결정 8 · P35 · G21 휴일).
 */
class GroupBuySupplyTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    @Test
    void G38_그_단품만_산_주문은_배송비_포함_전액_섞인_주문은_그_줄만_재부과_없이_답이_없으면_나머지만() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).price(25_000).option("A", 0, 10).option("B", 0, 10).open();
        long a = kit.skuId(gb, "A");
        long b = kit.skuId(gb, "B");
        long onlyA = kit.newMember();
        long mixed = kit.newMember();
        long mixedCancel = kit.newMember();
        JoinResult o1 = kit.paidOrder(onlyA, gb, lines(a, 1)); // 28,000
        JoinResult o2 = kit.paidOrder(mixed, gb, lines(a, 1, b, 1)); // 50,000 무료배송
        JoinResult o3 = kit.paidOrder(mixedCancel, gb, lines(a, 1, b, 1));

        GroupBuySupplyService.Result result = kit.supply.markFailure(gb, a, "공급처 단종 통보", kit.operator, "ip");
        assertThat(result.refundedOrders()).isEqualTo(1);
        assertThat(result.askedOrders()).isEqualTo(2);
        assertThat(kit.participation(o1.participationId()).status()).isEqualTo(ParticipationStatus.CANCELED);
        assertThat(kit.participation(o1.participationId()).refundedAmount()).as("배송비 포함 전액").isEqualTo(28_000);
        assertThat(kit.participation(o2.participationId()).refundedAmount()).as("그 줄만 — 무료배송 기준 아래여도 재부과 없음").isEqualTo(25_000);
        assertThat(kit.participation(o2.participationId()).reshippingFeeCharged()).isZero();
        assertThat(kit.sku(gb, a).onSale()).isFalse();
        assertThat(kit.sku(gb, a).maxQty()).as("확정 + 선점으로 줄임").isEqualTo(1);
        assertThat(kit.eventsOf(GroupBuyEvents.SupplyChoiceAsked.class)).hasSize(2);
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_SUPPLY_FAILURE));
        assertThatThrownBy(() -> kit.supply.markFailure(gb, a, "공급처 단종 통보", kit.operator, "ip")).isInstanceOf(GroupBuyException.class);

        // 기한 전에는 발송하지 않는다
        var ship = kit.fulfillment.ship(List.of(new FulfillmentService.ShipLine(o2.participationId(), "CJ", "123456789")), kit.operator, "ip");
        assertThat(ship.results().getFirst().code()).isEqualTo(GroupBuyException.ORDER_NOT_SHIPPABLE);

        kit.supply.choose(mixedCancel, o3.participationId(), SupplyChoice.CANCEL_ALL);
        assertThat(kit.participation(o3.participationId()).status()).isEqualTo(ParticipationStatus.CANCELED);
        assertThat(kit.participation(o3.participationId()).refundedAmount()).isEqualTo(50_000);
        assertThatThrownBy(() -> kit.supply.choose(mixedCancel, o3.participationId(), SupplyChoice.KEEP_REST))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SUPPLY_CHOICE_CLOSED));

        kit.advance(Duration.ofDays(8));
        kit.fulfillmentWorker.runOnce();
        assertThat(kit.count("SELECT count(*) FROM group_buy_supply_choices WHERE participation_id = ? AND choice = 'KEEP_REST' AND defaulted", o2.participationId()))
                .isEqualTo(1);
        kit.ship(o2.participationId());
        kit.assertInvariants(gb);
    }

    @Test
    void G38_공급_불가_뒤_그_단품을_품은_결제_중_주문이_결제되면_거절_자동_취소_다른_단품만_산_주문은_받는다() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).price(25_000).option("A", 0, 10).option("B", 0, 10).open();
        long a = kit.skuId(gb, "A");
        long b = kit.skuId(gb, "B");
        long withA = kit.newMember();
        long onlyB = kit.newMember();
        JoinResult pendingA = kit.order(withA, gb, lines(a, 1, b, 1)); // 결제 중
        JoinResult pendingB = kit.order(onlyB, gb, lines(b, 1));

        GroupBuySupplyService.Result result = kit.supply.markFailure(gb, a, "공급처 단종 통보", kit.operator, "ip");
        assertThat(result.refundedOrders() + result.askedOrders()).as("결제 중 주문은 건드리지 않는다(결제 행 먼저 — 결제 확정이 판정)").isZero();

        var late = kit.pay(pendingA);
        assertThat(late.outcome()).isEqualTo(PaymentConfirmService.Outcome.REJECTED);
        var payment = kit.payment(pendingA.checkout().paymentId());
        assertThat(payment.anomaly()).isEqualTo(PaymentAnomaly.DOMAIN_REJECTED);
        assertThat(payment.status()).as("커밋 뒤 즉시 취소").isEqualTo(PaymentStatus.CANCELED);
        var p = kit.participation(pendingA.participationId());
        assertThat(p.status()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(p.expireCause()).isEqualTo(ExpireCause.REJECTED_CLOSED);
        assertThat(kit.sku(gb, a).currentQty()).as("확정하지 않았다").isZero();
        assertThat(kit.sku(gb, a).reservedQty()).as("선점 해제").isZero();
        assertThat(kit.eventsOf(GroupBuyEvents.OrderRejected.class)).anySatisfy(e -> assertThat(e.reason()).isEqualTo(GroupBuyPaymentHandler.REJECT_SUPPLY_FAILED));

        assertThat(kit.pay(pendingB).outcome()).as("공급 불가가 아닌 단품만 — 받는다").isEqualTo(PaymentConfirmService.Outcome.ACCEPTED);
        kit.assertInvariants(gb);
    }

    @Test
    void G21_P35_영업일은_주말과_휴일을_건너뛰고_2영업일_경고_3영업일_심각() {
        // 2026-10-30 금 → 다음 영업일 11-02 월(10-31·11-01 주말), 11-03 을 휴일로
        List<LocalDate> holidays = List.of(LocalDate.of(2026, 11, 3));
        LocalDate friday = LocalDate.of(2026, 10, 30);
        assertThat(GroupBuyBusinessDays.plus(friday, 1, holidays)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(GroupBuyBusinessDays.plus(friday, 2, holidays)).as("휴일 건너뜀").isEqualTo(LocalDate.of(2026, 11, 4));
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        var start = friday.atTime(15, 0).atZone(seoul).toInstant();
        assertThat(GroupBuyBusinessDays.deadlineLevel(start, LocalDate.of(2026, 11, 2).atTime(18, 0).atZone(seoul).toInstant(), holidays))
                .isEqualTo(GroupBuyBusinessDays.Level.NONE);
        assertThat(GroupBuyBusinessDays.deadlineLevel(start, LocalDate.of(2026, 11, 4).atTime(9, 0).atZone(seoul).toInstant(), holidays))
                .isEqualTo(GroupBuyBusinessDays.Level.WARNING);
        assertThat(GroupBuyBusinessDays.deadlineLevel(start, LocalDate.of(2026, 11, 5).atTime(9, 0).atZone(seoul).toInstant(), holidays))
                .isEqualTo(GroupBuyBusinessDays.Level.CRITICAL);
        assertThat(GroupBuyBusinessDays.endOfBusinessDays(start, 3, holidays)).isEqualTo(LocalDate.of(2026, 11, 6).atStartOfDay(seoul).toInstant());
    }

    @Test
    void P35_요청_처리_기한_단계는_취소는_요청한_날_반품은_돌려받은_날부터() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long member = kit.newMember();
        JoinResult order = kit.paidOrder(member, gb, 1);
        kit.fulfillment.prepare(List.of(order.participationId()), kit.operator, "ip");
        var r = kit.requests.create(member, order.participationId(), new com.community.groupbuy.service.GroupBuyRequestService.RequestInput(
                com.community.groupbuy.entity.RequestType.CANCEL, null, null, List.of(new com.community.groupbuy.service.GroupBuyRequestService.LineInput(kit.mainSku(gb), 1,
                null)), null, null, null));
        assertThat(kit.requests.deadlineLevel(r.requestId())).isEqualTo(GroupBuyBusinessDays.Level.NONE);
        kit.advance(Duration.ofDays(7)); // 주말이 끼어도 영업일 5
        assertThat(kit.requests.deadlineLevel(r.requestId())).isEqualTo(GroupBuyBusinessDays.Level.CRITICAL);
    }
}
