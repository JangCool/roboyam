package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.service.GroupBuyRefundWorker;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 상태 워커(모집 시작·마감·연장·판정·완료)와 불발·중단 환불 워커 — commerce.md 10-2·10-7, 18절 P16b·P16c·P16d·P25, 계약 결정 9·25·37·45·50·61·78·81,
 * G12·G20·G23·G24·G27·G28.
 */
class GroupBuyStateWorkerTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private String policy() {
        return kit.policy(3_000, null, 3_000, "ONE_WAY");
    }

    /** 10분 뒤 마감하는 공구 */
    private long endingSoon(GroupBuyTestKit.Spec spec) {
        return spec.period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(10))).open();
    }

    @Test
    void 예정_공구는_시작_시각에_워커가_모집을_시작하고_그때의_배송비를_복사한다() {
        String policy = policy();
        long gb = kit.spec(policy).period(kit.now().plus(Duration.ofMinutes(30)), kit.now().plus(Duration.ofDays(1))).open();
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.DRAFT);
        assertThat(kit.group(gb).publishedAt()).isNotNull();
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).as("아직 시작 전").isEqualTo(GroupBuyStatus.DRAFT);
        kit.advance(Duration.ofMinutes(31));
        kit.runStateWorker();
        var g = kit.group(gb);
        assertThat(g.status()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(g.shippingFee()).isEqualTo(3_000);
        assertThat(g.initialMaxQty()).isEqualTo(g.maxQty());
        assertThat(g.revisionNo()).isEqualTo(1);
    }

    @Test
    void P16b_DEMAND_마감_3분_전_선점_만료로_풀리면_마감에_30분_연장하고_연장_중_다_팔리면_즉시_종료() {
        long gb = endingSoon(kit.spec(policy()).qty(0, 1));
        JoinResult abandoned = kit.order(kit.newMember(), gb, 1);
        kit.advance(Duration.ofMinutes(6)); // 마감 4분 전 — 선점 만료(풀림)
        kit.holds.expireOverdue(1000);
        assertThat(kit.participation(abandoned.participationId()).status()).isEqualTo(ParticipationStatus.EXPIRED);
        kit.advance(Duration.ofMinutes(4).plusSeconds(1)); // 마감 지남
        assertThat(kit.stateWorker.closeOrExtend(gb)).isTrue();
        var g = kit.group(gb);
        assertThat(g.status()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(g.extendedUntil()).isEqualTo(g.endAt().plus(Duration.ofMinutes(30)));

        kit.paidOrder(kit.newMember(), gb, 1);
        assertThat(kit.group(gb).status()).as("연장 중 다 팔리면 그 자리에서").isEqualTo(GroupBuyStatus.CLOSING);
        assertThat(kit.group(gb).closeReason()).isEqualTo("SOLD_OUT");
        kit.assertInvariants(gb);
    }

    @Test
    void P16c_DEMAND_마감에_수량이_남아도_최근_선점_만료가_없으면_연장하지_않는다() {
        long gb = endingSoon(kit.spec(policy()));
        kit.paidOrder(kit.newMember(), gb, 1);
        kit.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertThat(kit.stateWorker.closeOrExtend(gb)).isTrue();
        var g = kit.group(gb);
        assertThat(g.status()).isEqualTo(GroupBuyStatus.CLOSING);
        assertThat(g.extendedUntil()).isNull();
        assertThat(g.closeReason()).isEqualTo("TIME_UP");
        assertThat(g.closingAt()).isEqualTo(g.endAt());
    }

    @Test
    void P16d_ALWAYS_는_수량이_남으면_30분_연장하고_끝나면_CLOSING() {
        long gb = endingSoon(kit.spec(policy()).extension("ALWAYS"));
        kit.advance(Duration.ofMinutes(10).plusSeconds(1));
        kit.stateWorker.closeOrExtend(gb);
        var g = kit.group(gb);
        assertThat(g.extendedUntil()).isEqualTo(g.endAt().plus(Duration.ofMinutes(30)));
        kit.advance(Duration.ofMinutes(30));
        assertThat(kit.stateWorker.closeOrExtend(gb)).isTrue();
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSING);
        assertThat(kit.group(gb).closingAt()).as("연장 끝이 집계 시작").isEqualTo(g.extendedUntil());
    }

    @Test
    void DEMAND_마감_시각에_결제_중인_선점이_남아_있으면_연장한다() {
        long gb = endingSoon(kit.spec(policy()));
        kit.advance(Duration.ofMinutes(8));
        kit.order(kit.newMember(), gb, 1);
        kit.advance(Duration.ofMinutes(2).plusSeconds(1));
        kit.stateWorker.closeOrExtend(gb);
        assertThat(kit.group(gb).extendedUntil()).isNotNull();
    }

    @Test
    void G12_마감_4분_전_추가_입고로_수량이_늘면_DEMAND_연장() {
        long gb = endingSoon(kit.spec(policy()).qty(0, 2));
        kit.paidOrder(kit.newMember(), gb, 1);
        kit.advance(Duration.ofMinutes(6));
        kit.admin.addStock(gb, null, 3, "입고", kit.operator, "ip");
        kit.advance(Duration.ofMinutes(4).plusSeconds(1));
        kit.stateWorker.closeOrExtend(gb);
        assertThat(kit.group(gb).extendedUntil()).isNotNull();
    }

    @Test
    void G28_마감_3분_전_본인_취소로_풀리면_DEMAND_연장_수량_풀림_이벤트() {
        long gb = endingSoon(kit.spec(policy()));
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 2);
        kit.advance(Duration.ofMinutes(7));
        kit.cancels.cancelByMember(member, paid.participationId(), lines(kit.mainSku(gb), 1));
        assertThat(kit.eventsOf(com.community.groupbuy.dto.GroupBuyEvents.QtyReleased.class))
                .anySatisfy(e -> assertThat(e.kind().name()).isEqualTo("CANCEL"));
        kit.advance(Duration.ofMinutes(3).plusSeconds(1));
        kit.stateWorker.closeOrExtend(gb);
        assertThat(kit.group(gb).extendedUntil()).isNotNull();
        kit.assertInvariants(gb);
    }

    @Test
    void 판정_성사면_JOINED_가_CONFIRMED_로_일괄_불발이면_환불_워커가_전액_환불() {
        long success = endingSoon(kit.spec(policy()).qty(2, 10));
        long failure = endingSoon(kit.spec(policy()).qty(5, 10));
        JoinResult s1 = kit.paidOrder(kit.newMember(), success, 2);
        JoinResult f1 = kit.paidOrder(kit.newMember(), failure, 2);
        kit.advance(Duration.ofMinutes(10).plusSeconds(1));
        kit.runStateWorker();

        assertThat(kit.group(success).status()).isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
        assertThat(kit.participation(s1.participationId()).status()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(kit.group(failure).status()).isEqualTo(GroupBuyStatus.CLOSED_FAILED);
        assertThat(kit.participation(f1.participationId()).status()).as("상태 워커는 환불하지 않는다(결정 61)").isEqualTo(ParticipationStatus.JOINED);

        kit.runRefundWorker();
        var refunded = kit.participation(f1.participationId());
        assertThat(refunded.status()).isEqualTo(ParticipationStatus.REFUNDED);
        assertThat(refunded.refundedAmount()).isEqualTo(refunded.cashAmount());
        assertThat(kit.payment(f1.checkout().paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(kit.group(failure).currentQty()).as("CANCEL 이동").isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE participation_id = ? AND cause = 'FAILED' AND status = 'COMPLETED'", refunded.id()))
                .as("onCanceled 가 환불 기록을 완료로").isEqualTo(1);
        kit.assertInvariants(success, failure);
    }

    @Test
    void P25_성사_뒤_취소로_확정이_최소_아래로_내려가도_성사는_뒤집지_않는다() {
        long gb = endingSoon(kit.spec(policy()).qty(2, 10));
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 2);
        kit.advance(Duration.ofMinutes(10).plusSeconds(1));
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
        // 승인된 발송 전 취소(2b-3)를 흉내 — 수량은 수량 서비스로만
        kit.tx.executeWithoutResult(s -> {
            var item = kit.items(paid.participationId()).get(0);
            kit.jdbc.update("UPDATE group_buy_participation_items SET canceled_qty = 1 WHERE id = ?", item.id());
            kit.spring.getBean(com.community.groupbuy.service.GroupBuyQtyService.class).cancel(gb, java.util.Map.of(item, 1), false, kit.operator, "APPROVED", kit.now());
        });
        assertThat(kit.group(gb).currentQty()).isEqualTo(1);
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
        kit.assertInvariants(gb);
    }

    @Test
    void 결정45_G20_집계_중에도_성사형_본인_즉시_취소가_되고_그_때문에_불발이_될_수_있다() {
        long gb = endingSoon(kit.spec(policy()).qty(3, 10));
        long member = kit.newMember();
        JoinResult a = kit.paidOrder(member, gb, 2);
        kit.paidOrder(kit.newMember(), gb, 1);
        kit.advance(Duration.ofMinutes(10).plusSeconds(1));
        kit.stateWorker.closeOrExtend(gb);
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSING);
        kit.cancels.cancelByMember(member, a.participationId(), null);
        assertThat(kit.participation(a.participationId()).status()).isEqualTo(ParticipationStatus.CANCELED);
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).as("판정 시점의 수량(1 < 3)").isEqualTo(GroupBuyStatus.CLOSED_FAILED);
        kit.runRefundWorker();
        kit.assertInvariants(gb);
    }

    @Test
    void G23_판정과_마지막_결제_확정이_겹쳐도_성사_수량이면_성사() throws Exception {
        for (int round = 0; round < 4; round++) {
            long gb = endingSoon(kit.spec(policy()).qty(2, 10));
            kit.paidOrder(kit.newMember(), gb, 1);
            JoinResult last = kit.order(kit.newMember(), gb, 1);
            kit.fakePg.pay(last.checkout().paymentId(), last.amount(), "KRW", kit.now());
            kit.advance(Duration.ofMinutes(10).plusSeconds(1));
            kit.stateWorker.closeOrExtend(gb); // DEMAND — 결제 중 선점이 있어 연장될 수 있다 → 집계 중으로 고정
            kit.jdbc.update("UPDATE group_buys SET status = 'CLOSING', close_reason = 'TIME_UP', closing_at = ? WHERE id = ? AND status = 'RECRUITING'",
                    java.sql.Timestamp.from(kit.now()), gb);
            List<Callable<Object>> tasks = new ArrayList<>();
            tasks.add(() -> kit.confirm.confirm(last.checkout().paymentId(), ConfirmedVia.WEBHOOK));
            tasks.add(() -> kit.stateWorker.judge(gb));
            GroupBuyTestKit.runTogether(tasks);
            kit.stateWorker.judge(gb);
            assertThat(kit.group(gb).status()).as("확정 2 = 최소 2").isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
            assertThat(kit.participation(last.participationId()).status()).isIn(ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED);
            kit.assertInvariants(gb);
        }
    }

    @Test
    void G27_두_옵션의_마지막_하나가_동시에_확정돼도_워커가_1분_안에_CLOSING() throws Exception {
        long gb = kit.spec(policy()).qty(0, 10).option("A", 0, 1).option("B", 0, 1).open();
        JoinResult a = kit.order(kit.newMember(), gb, lines(kit.skuId(gb, "A"), 1));
        JoinResult b = kit.order(kit.newMember(), gb, lines(kit.skuId(gb, "B"), 1));
        kit.fakePg.pay(a.checkout().paymentId(), a.amount(), "KRW", kit.now());
        kit.fakePg.pay(b.checkout().paymentId(), b.amount(), "KRW", kit.now());
        GroupBuyTestKit.runTogether(List.of(() -> kit.confirm.confirm(a.checkout().paymentId(), ConfirmedVia.WEBHOOK),
                () -> kit.confirm.confirm(b.checkout().paymentId(), ConfirmedVia.WEBHOOK)));
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).isIn(GroupBuyStatus.CLOSING, GroupBuyStatus.CLOSED_SUCCESS);
        assertThat(kit.group(gb).closeReason()).isEqualTo("SOLD_OUT");
        kit.assertInvariants(gb);
    }

    @Test
    void 즉시_발송_성사_공구는_진행_중_주문이_모두_끝나면_COMPLETED() {
        long gb = kit.spec(policy()).qty(0, 2).open();
        JoinResult a = kit.paidOrder(kit.newMember(), gb, 1);
        JoinResult b = kit.paidOrder(kit.newMember(), gb, 1);
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'DELIVERED' WHERE id = ?", a.participationId());
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).as("하나가 아직 발송 전").isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'DELIVERED' WHERE id = ?", b.participationId());
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.COMPLETED);
    }

    @Test
    void G24_불발_300건을_환불_워커가_바퀴당_50건씩_이어서_끝내고_중복_환불이_없다() {
        long gb = endingSoon(kit.spec(policy()).qty(400, 1000));
        List<JoinResult> paid = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            paid.add(kit.paidOrder(kit.newMember(), gb, 1));
        }
        kit.advance(Duration.ofMinutes(10).plusSeconds(1));
        Instant before = Instant.now();
        kit.runStateWorker();
        assertThat(Duration.between(before, Instant.now())).as("상태 워커는 환불을 하지 않아 짧다").isLessThan(Duration.ofSeconds(20));
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSED_FAILED);

        int first = kit.refundWorker.runOnce();
        assertThat(first).isLessThanOrEqualTo(GroupBuyRefundWorker.BATCH_LIMIT);
        // 서버가 멈췄다 다시 뜬 것처럼 — 다음 바퀴들이 남은 것을 이어 받는다
        kit.runRefundWorker();
        kit.runRefundWorker();
        for (JoinResult o : paid) {
            assertThat(kit.participation(o.participationId()).status()).isEqualTo(ParticipationStatus.REFUNDED);
        }
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE group_buy_id = ?", gb)).isEqualTo(300);
        assertThat(kit.count("SELECT count(*) FROM payment_cancels c JOIN group_buy_participations p ON c.payment_order_id = p.payment_order_id "
                + "WHERE p.group_buy_id = ?", gb)).as("주문마다 취소 하나").isEqualTo(300);
        kit.cancelWorker.runOnce();
        kit.assertInvariants(gb);
    }
}
