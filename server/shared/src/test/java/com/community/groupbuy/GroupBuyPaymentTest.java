package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.ExpireCause;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.handler.GroupBuyPaymentHandler;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.service.PaymentConfirmService.Outcome;
import com.community.restriction.exception.MemberRestrictedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 결제 처리기·선점 만료·결제 다시 열기 — commerce.md 8-2·8-6·10-3·10-6·12-3, 18절 P3·P3b·P15·P16, 계약 결정 27·37·40·41·62·63·78·82, G9·G16·G18·G22·G25·G26.
 * 실제 PostgreSQL 위의 결제 모듈(가짜 PG)로 확정 코드를 그대로 부른다.
 */
class GroupBuyPaymentTest {

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

    @Test
    void 성사형은_결제되면_JOINED_와_수량_확정_즉시_발송은_CONFIRMED_까지_한_번에() {
        long demand = kit.spec(policy()).qty(5, 10).open();
        long immediate = kit.spec(policy()).open();
        JoinResult a = kit.order(kit.newMember(), demand, 2);
        JoinResult b = kit.order(kit.newMember(), immediate, 1);

        assertThat(kit.pay(a).outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(kit.pay(b).outcome()).isEqualTo(Outcome.ACCEPTED);

        var pa = kit.participation(a.participationId());
        assertThat(pa.status()).isEqualTo(ParticipationStatus.JOINED);
        assertThat(pa.groupBuyRevision()).as("결제 때의 수정 이력 번호").isEqualTo(1);
        assertThat(kit.participation(b.participationId()).status()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(kit.group(demand).currentQty()).isEqualTo(2);
        assertThat(kit.group(demand).reservedQty()).isZero();
        assertThat(kit.count("SELECT count(*) FROM participation_status_history WHERE participation_id = ? AND to_status = 'CONFIRMED'", b.participationId())).isEqualTo(1);
        assertThat(kit.eventsOf(GroupBuyEvents.OrderPaid.class)).hasSize(2);
        kit.assertInvariants(demand, immediate);
    }

    @Test
    void 결정27_마지막_하나가_확정되면_같은_트랜잭션에서_CLOSING_SOLD_OUT() {
        long gb = kit.spec(policy()).qty(0, 3).open();
        kit.paidOrder(kit.newMember(), gb, 2);
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.RECRUITING);
        kit.paidOrder(kit.newMember(), gb, 1);
        var g = kit.group(gb);
        assertThat(g.status()).isEqualTo(GroupBuyStatus.CLOSING);
        assertThat(g.closeReason()).isEqualTo("SOLD_OUT");
        assertThat(g.closingAt()).isEqualTo(kit.now());
        kit.assertInvariants(gb);
    }

    @Test
    void G16_옵션_한도_합이_공구_최대보다_작아도_옵션이_모두_다_팔리면_즉시_CLOSING() {
        long gb = kit.spec(policy()).qty(0, 100).option("빨강", 0, 2).option("파랑", 0, 1).open();
        long red = kit.skuId(gb, "빨강");
        long blue = kit.skuId(gb, "파랑");
        kit.paidOrder(kit.newMember(), gb, lines(red, 2));
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.RECRUITING);
        kit.paidOrder(kit.newMember(), gb, lines(blue, 1));
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSING);
        assertThat(kit.group(gb).closeReason()).isEqualTo("SOLD_OUT");
        kit.assertInvariants(gb);
    }

    @Test
    void 한도_없는_옵션이_있으면_옵션으로는_다_팔림이_아니다() {
        long gb = kit.spec(policy()).qty(0, 100).option("빨강", 0, 1).option("자유", 0, null).open();
        kit.paidOrder(kit.newMember(), gb, lines(kit.skuId(gb, "빨강"), 1));
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.RECRUITING);
    }

    @Test
    void P3_결제_중에_마감_시각이_지나도_선점_시간_안에_결제하면_받고_판정에_넣는다() {
        long gb = kit.spec(policy()).qty(2, 10).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(2))).open();
        JoinResult joined = kit.order(kit.newMember(), gb, 2);
        kit.advance(Duration.ofMinutes(3)); // 마감 지남, 선점 5분 안
        kit.stateWorker.closeOrExtend(gb);
        assertThat(kit.group(gb).status()).as("결제 중 선점이 남아 DEMAND 연장 — 연장 중에도 결제를 받는다").isIn(GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING);
        kit.jdbc.update("UPDATE group_buys SET status = 'CLOSING', close_reason = 'TIME_UP', closing_at = end_at WHERE id = ?", gb); // 집계 중으로 고정
        assertThat(kit.pay(joined).outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(kit.participation(joined.participationId()).status()).isEqualTo(ParticipationStatus.JOINED);
        assertThat(kit.stateWorker.judge(gb)).isTrue();
        assertThat(kit.group(gb).status()).as("확정 2 = 최소 2 — 성사").isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
        kit.assertInvariants(gb);
    }

    @Test
    void P3b_판정이_끝난_뒤_결제가_오면_예외_없이_거절_자동_취소_주문은_만료_선점은_한_번_해제_이탈로_세지_않는다() {
        long gb = kit.spec(policy()).qty(3, 10).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(1))).open();
        long member = kit.newMember();
        JoinResult joined = kit.order(member, gb, 1);
        kit.jdbc.update("UPDATE group_buys SET status = 'CLOSED_FAILED', close_reason = 'TIME_UP', closing_at = end_at WHERE id = ?", gb); // 판정 끝(불발)

        var result = kit.pay(joined);

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        var payment = kit.payment(joined.checkout().paymentId());
        assertThat(payment.anomaly()).isEqualTo(PaymentAnomaly.DOMAIN_REJECTED);
        assertThat(payment.status()).as("커밋 뒤 즉시 취소").isEqualTo(PaymentStatus.CANCELED);
        var p = kit.participation(joined.participationId());
        assertThat(p.status()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(p.expireCause()).isEqualTo(ExpireCause.REJECTED_CLOSED);
        assertThat(p.refundedAmount()).as("자동 취소는 주문 금액을 건드리지 않는다(결정 40)").isZero();
        assertThat(kit.group(gb).reservedQty()).isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'RELEASE'", p.id())).isEqualTo(1);
        assertThat(kit.participationStore.holdTimeouts(member, kit.now().minus(Duration.ofDays(1)))).isZero();
        assertThat(kit.eventsOf(GroupBuyEvents.AutoCancelCompleted.class)).as("자동 취소의 onCanceled 는 회원 안내만(G18)").hasSize(1);
        kit.assertInvariants(gb);
    }

    @Test
    void P15_선점이_만료된_직후_결제가_오면_수량이_남아도_살리지_않고_자동_취소() {
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult joined = kit.order(member, gb, 1);
        kit.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(kit.holds.expireOverdue(1000)).isGreaterThanOrEqualTo(1);
        assertThat(kit.participation(joined.participationId()).status()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(kit.payment(joined.checkout().paymentId()).status()).as("결제 행 먼저 닫음").isEqualTo(PaymentStatus.EXPIRED);

        var result = kit.payAt(joined.checkout().paymentId(), joined.amount(), kit.now().minusSeconds(2));

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(kit.payment(joined.checkout().paymentId()).anomaly()).isEqualTo(PaymentAnomaly.LATE_PAYMENT);
        assertThat(kit.participation(joined.participationId()).status()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(kit.participation(joined.participationId()).expireCause()).isEqualTo(ExpireCause.HOLD_TIMEOUT);
        assertThat(kit.group(gb).currentQty()).isZero();
        kit.assertInvariants(gb);
    }

    @Test
    void P16_결제창_실패_뒤_선점_시간_안에_다시_결제하면_같은_주문으로_성공하고_선점은_늘지_않는다() {
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult joined = kit.order(member, gb, 1);
        kit.advance(Duration.ofMinutes(2));

        PaymentCheckout retry = kit.join.reopen(member, joined.participationId(), true);

        assertThat(retry.paymentId()).isNotEqualTo(joined.checkout().paymentId());
        assertThat(retry.expiresAt()).as("선점을 늘리지 않는다(결정 41)").isEqualTo(joined.reservedUntil());
        assertThat(kit.payment(joined.checkout().paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.payAt(retry.paymentId(), retry.amount(), kit.now()).outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(kit.participation(joined.participationId()).status()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(kit.participation(joined.participationId()).paymentOrderId()).isEqualTo(retry.paymentOrderId());
        kit.assertInvariants(gb);
    }

    @Test
    void G26_남은_선점이_1분_미만이면_HOLD_EXPIRED_유효한_READY_가_있으면_같은_결제를_돌려준다() {
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult joined = kit.order(member, gb, 1);
        PaymentCheckout same = kit.join.reopen(member, joined.participationId(), false);
        assertThat(same.paymentId()).isEqualTo(joined.checkout().paymentId());
        assertThat(kit.payment(joined.checkout().paymentId()).status()).isEqualTo(PaymentStatus.READY);

        kit.advance(Duration.ofMinutes(4).plusSeconds(10)); // 남은 50초
        assertThatThrownBy(() -> kit.join.reopen(member, joined.participationId(), true))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.HOLD_EXPIRED));
        assertThatThrownBy(() -> kit.join.reopen(kit.newMember(), joined.participationId(), false))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ORDER_NOT_FOUND));
    }

    @Test
    void G25_onPaid_의_도메인_사정은_예외_없이_거절한다() {
        long gb = kit.spec(policy()).open();

        // ① 결제 중이 아닌 주문(그새 다른 길이 닫음 — 결제 행은 READY 그대로인 비정상)
        JoinResult closed = kit.order(kit.newMember(), gb, 1);
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'EXPIRED', expire_cause = 'GROUP_BUY_CANCELED' WHERE id = ?", closed.participationId());
        kit.jdbc.update("UPDATE group_buys SET reserved_qty = reserved_qty - 1 WHERE id = ?", gb);
        assertThat(kit.pay(closed).outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(kit.payment(closed.checkout().paymentId()).anomaly()).isEqualTo(PaymentAnomaly.DOMAIN_REJECTED);

        // ② 수량 불변식이 이미 깨짐(선점 0) — CONFIRM 0 행 → 거절 + 결제 이상 이벤트, 예외 없음
        JoinResult broken = kit.order(kit.newMember(), gb, 1);
        kit.jdbc.update("UPDATE group_buys SET reserved_qty = 0 WHERE id = ?", gb);
        assertThat(kit.pay(broken).outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(kit.participation(broken.participationId()).expireCause()).isEqualTo(ExpireCause.REJECTED_ANOMALY);
        assertThat(kit.eventsOf(GroupBuyEvents.OrderRejected.class)).anySatisfy(e -> {
            assertThat(e.reason()).isEqualTo(GroupBuyPaymentHandler.REJECT_QTY_ANOMALY);
            assertThat(e.anomaly()).isTrue();
        });
        kit.jdbc.update("UPDATE group_buys SET reserved_qty = 0, current_qty = 0 WHERE id = ?", gb); // 시험이 깬 값을 되돌린다

        // ③ DB 제약 충돌(주문 상태 이력 넣기에서 유일 충돌을 흉내) — 세이브포인트로 되돌리고 거절
        JoinResult conflict = kit.order(kit.newMember(), gb, 1);
        kit.jdbc.execute("CREATE OR REPLACE FUNCTION gb_test_conflict() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.participation_id = " + conflict.participationId() + " AND NEW.to_status = 'JOINED' THEN RAISE EXCEPTION 'dup' USING ERRCODE = 'unique_violation'; END IF; "
                + "RETURN NEW; END; $$");
        kit.jdbc.execute("CREATE TRIGGER trg_gb_test_conflict BEFORE INSERT ON participation_status_history FOR EACH ROW EXECUTE FUNCTION gb_test_conflict()");
        try {
            assertThat(kit.pay(conflict).outcome()).isEqualTo(Outcome.REJECTED);
        } finally {
            kit.jdbc.execute("DROP TRIGGER trg_gb_test_conflict ON participation_status_history");
            kit.jdbc.execute("DROP FUNCTION gb_test_conflict()");
        }
        var p = kit.participation(conflict.participationId());
        assertThat(p.status()).as("확정 쪽 변경은 되돌리고 거절 경로로 만료").isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(kit.group(gb).currentQty()).isZero();
        assertThat(kit.group(gb).reservedQty()).isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'CONFIRM'", p.id())).isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'RELEASE'", p.id())).isEqualTo(1);
    }

    @Test
    void 선점_반복_이탈_24시간_3번_만료면_공구_참여_제한이_걸리고_다른_원인은_세지_않는다() {
        long gb = kit.spec(policy()).qty(0, 1000).open();
        long member = kit.newMember();
        // 중단으로 닫힌 것은 세지 않는다
        JoinResult notCounted = kit.order(member, gb, 1);
        assertThat(kit.holds.close(notCounted.participationId(), ExpireCause.GROUP_BUY_CANCELED, false)).isTrue();
        for (int i = 0; i < 3; i++) {
            JoinResult joined = kit.order(member, gb, 1);
            kit.advance(Duration.ofMinutes(5).plusSeconds(1));
            assertThat(kit.holds.close(joined.participationId(), ExpireCause.HOLD_TIMEOUT, true)).isTrue();
            if (i < 2) {
                assertThat(kit.count("SELECT count(*) FROM member_restrictions WHERE user_id = ? AND type = 'GROUP_BUY_BAN'", member)).isZero();
            }
        }
        assertThat(kit.count("SELECT count(*) FROM member_restrictions WHERE user_id = ? AND type = 'GROUP_BUY_BAN' AND auto_rule = 'GROUP_BUY_HOLD_EXPIRED'", member))
                .isEqualTo(1);
        assertThatThrownBy(() -> kit.order(member, gb, 1)).isInstanceOf(MemberRestrictedException.class);
        assertThat(kit.eventsOf(GroupBuyEvents.HoldExpired.class)).hasSize(3);
        kit.assertInvariants(gb);
    }

    @Test
    void G22_웹훅_확정과_선점_만료_워커가_동시에_와도_교착_없이_하나만_이기고_RELEASE_는_한_번() throws Exception {
        long gb = kit.spec(policy()).qty(0, 1000).open();
        List<JoinResult> orders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            orders.add(kit.order(kit.newMember(), gb, 1));
        }
        java.time.Instant paidAt = kit.now().plusSeconds(10); // 선점 시간 안의 결제
        for (JoinResult o : orders) {
            kit.fakePg.pay(o.checkout().paymentId(), o.amount(), "KRW", paidAt);
        }
        kit.advance(Duration.ofMinutes(5).plusSeconds(1)); // 워커가 보기에는 선점 시간이 지났다
        List<Callable<Object>> tasks = new ArrayList<>();
        for (JoinResult o : orders) {
            tasks.add(() -> kit.confirm.confirm(o.checkout().paymentId(), ConfirmedVia.WEBHOOK));
            tasks.add(() -> kit.holds.close(o.participationId(), ExpireCause.HOLD_TIMEOUT, true));
        }
        List<Object> results = GroupBuyTestKit.runTogether(tasks);
        assertThat(results).noneMatch(r -> r instanceof Exception);
        for (JoinResult o : orders) {
            var p = kit.participation(o.participationId());
            var pay = kit.payment(o.checkout().paymentId());
            int releases = kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'RELEASE'", p.id());
            int confirms = kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'CONFIRM'", p.id());
            if (p.status() == ParticipationStatus.CONFIRMED) {
                assertThat(pay.anomaly()).isNull();
                assertThat(releases).isZero();
                assertThat(confirms).isEqualTo(1);
            } else {
                assertThat(p.status()).isEqualTo(ParticipationStatus.EXPIRED);
                assertThat(pay.anomaly()).as("닫힌 결제에 온 결제는 늦은 결제").isEqualTo(PaymentAnomaly.LATE_PAYMENT);
                assertThat(releases).isEqualTo(1);
                assertThat(confirms).isZero();
            }
        }
        kit.assertInvariants(gb);
    }

    @Test
    void G22_웹훅_확정과_결제_다시_열기가_동시에_와도_교착_없이_하나만_이긴다() throws Exception {
        long gb = kit.spec(policy()).qty(0, 1000).open();
        for (int round = 0; round < 4; round++) {
            long member = kit.newMember();
            JoinResult o = kit.order(member, gb, 1);
            kit.fakePg.pay(o.checkout().paymentId(), o.amount(), "KRW", kit.now());
            List<Object> results = GroupBuyTestKit.runTogether(List.of(
                    () -> kit.confirm.confirm(o.checkout().paymentId(), ConfirmedVia.WEBHOOK),
                    () -> kit.join.reopen(member, o.participationId(), true)));
            var p = kit.participation(o.participationId());
            var first = kit.payment(o.checkout().paymentId());
            if (p.status() == ParticipationStatus.CONFIRMED) {
                assertThat(first.anomaly()).isNull();
                assertThat(results.get(1)).as("다시 열기는 졌다").isInstanceOf(GroupBuyException.class);
                assertThat(kit.count("SELECT count(*) FROM payment_orders WHERE order_ref_id = ? AND status = 'READY'", p.id())).isZero();
            } else {
                assertThat(p.status()).isEqualTo(ParticipationStatus.PENDING_PAYMENT);
                assertThat(first.anomaly()).isEqualTo(PaymentAnomaly.LATE_PAYMENT);
            }
        }
        kit.assertInvariants(gb);
    }

    @Test
    void G9_결제창이_열린_동안_중단되면_주문은_만료_선점_해제_뒤에_온_결제는_늦은_결제로_자동_취소_이탈로_세지_않는다() {
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult joined = kit.order(member, gb, 2);
        kit.admin.cancel(gb, "공급처 사정", kit.operator, "ip");
        kit.runRefundWorker();

        var p = kit.participation(joined.participationId());
        assertThat(p.status()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(p.expireCause()).isEqualTo(ExpireCause.GROUP_BUY_CANCELED);
        assertThat(kit.payment(joined.checkout().paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.group(gb).reservedQty()).isZero();

        var late = kit.pay(joined);
        assertThat(late.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(kit.payment(joined.checkout().paymentId()).anomaly()).isEqualTo(PaymentAnomaly.LATE_PAYMENT);
        assertThat(kit.participationStore.holdTimeouts(member, kit.now().minus(Duration.ofDays(1)))).isZero();
        kit.assertInvariants(gb);
    }

    @Test
    void G17_결제_확정과_중단이_동시에_와도_교착_없고_확정이_이기면_환불_워커가_그_주문을_환불한다() throws Exception {
        for (int round = 0; round < 3; round++) {
            long gb = kit.spec(policy()).qty(0, 1000).open();
            List<JoinResult> orders = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                JoinResult o = kit.order(kit.newMember(), gb, 1);
                kit.fakePg.pay(o.checkout().paymentId(), o.amount(), "KRW", kit.now());
                orders.add(o);
            }
            List<Callable<Object>> tasks = new ArrayList<>();
            for (JoinResult o : orders) {
                tasks.add(() -> kit.confirm.confirm(o.checkout().paymentId(), ConfirmedVia.WEBHOOK));
            }
            tasks.add(() -> kit.admin.cancel(gb, "동시 중단", kit.operator, "ip"));
            List<Object> results = GroupBuyTestKit.runTogether(tasks);
            assertThat(results).noneMatch(r -> r instanceof RuntimeException && !(r instanceof GroupBuyException));
            kit.runRefundWorker();
            kit.cancelWorker.runOnce();
            for (JoinResult o : orders) {
                var p = kit.participation(o.participationId());
                var pay = kit.payment(o.checkout().paymentId());
                assertThat(p.status()).isIn(ParticipationStatus.REFUNDED, ParticipationStatus.EXPIRED);
                assertThat(pay.status()).as("어느 쪽이든 돈은 돌아간다").isEqualTo(PaymentStatus.CANCELED);
            }
            kit.assertInvariants(gb);
        }
    }
}
