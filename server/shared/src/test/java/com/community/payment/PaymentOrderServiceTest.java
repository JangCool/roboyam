package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * 결제 주문 만들기(commerce.md 8-1·8-2 의 1, 계약 「패키지」 {@code PaymentOrderService.create}) — 서버가 정하는 결제 ID({@code pay_} + 32자)·금액·유효 시각,
 * 도메인 트랜잭션 안에서만({@code MANDATORY}), 재결제마다 새 번호·이전 READY 는 EXPIRED, 처리기·설정이 없으면 만들지 않는다.
 */
class PaymentOrderServiceTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    private static PaymentOrderRequest request(long userId, long ref, long amount, Duration validFor) {
        return new PaymentOrderRequest(userId, PaymentOrderType.PAYMENT_TEST, ref, "시험 결제", amount, validFor);
    }

    @Test
    void 결제_ID_는_pay_와_무작위_32자이고_금액_유효_시각은_서버가_정한다() {
        long member = kit.newMember();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            PaymentTestKit.Created created = kit.create(member, 12_300, Decision.ACCEPT);
            assertThat(created.paymentId()).matches("pay_[A-Za-z0-9]{32}").hasSize(36);
            assertThat(seen.add(created.paymentId())).as("겹치지 않는다").isTrue();
        }
        PaymentTestKit.Created created = kit.create(member, 12_300, Decision.ACCEPT);
        OrderRow row = kit.order(created.paymentId());
        assertThat(row.status()).isEqualTo(PaymentStatus.READY);
        assertThat(row.amount()).isEqualTo(12_300);
        assertThat(row.currency()).isEqualTo("KRW");
        assertThat(row.expiresAt()).isEqualTo(kit.now().plus(Duration.ofMinutes(5)));
        assertThat(row.userId()).isEqualTo(member);
        assertThat(row.canceledAmount()).isZero();
        assertThat(row.paidAmount()).isNull();
        PaymentCheckout checkout = created.checkout();
        assertThat(checkout.amount()).isEqualTo(12_300);
        assertThat(checkout.expiresAt()).isEqualTo(row.expiresAt());
        assertThat(checkout.provider()).isEqualTo(PaymentGatewayKind.FAKE);
        assertThat(kit.events(row.id(), "CREATED")).isEqualTo(1);
    }

    @Test
    void 도메인_트랜잭션_밖에서_부르면_던진다_MANDATORY() {
        assertThatThrownBy(() -> kit.orders.create(request(kit.newMember(), kit.ids.nextId(), 1000, Duration.ofMinutes(5))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 도메인_트랜잭션이_되돌려지면_결제_주문도_없다() {
        long ref = kit.ids.nextId();
        assertThatThrownBy(() -> kit.tx.executeWithoutResult(status -> {
            kit.orders.create(request(kit.newMember(), ref, 1000, Duration.ofMinutes(5)));
            throw new IllegalStateException("도메인 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(kit.count("SELECT count(*) FROM payment_orders WHERE order_ref_id = ?", ref)).isZero();
    }

    @Test
    void 재결제마다_새_결제_ID_이고_같은_주문의_이전_READY_는_EXPIRED_로_닫는다() {
        long member = kit.newMember();
        long ref = kit.ids.nextId();
        PaymentTestKit.Created first = kit.createFor(member, ref, 5000, Decision.ACCEPT);
        PaymentTestKit.Created second = kit.createFor(member, ref, 5000, Decision.ACCEPT);
        assertThat(second.paymentId()).isNotEqualTo(first.paymentId());
        assertThat(kit.order(first.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.order(second.paymentId()).status()).isEqualTo(PaymentStatus.READY);
        assertThat(kit.events(first.orderId(), "EXPIRED")).isEqualTo(1);
        // 다른 주문의 READY 는 그대로
        PaymentTestKit.Created other = kit.create(member, 5000, Decision.ACCEPT);
        kit.createFor(member, ref, 5000, Decision.ACCEPT);
        assertThat(kit.order(other.paymentId()).status()).isEqualTo(PaymentStatus.READY);
        assertThat(kit.order(second.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void 금액_주문명_유효_시간이_범위_밖이면_만들지_않는다() {
        long member = kit.newMember();
        for (PaymentOrderRequest bad : new PaymentOrderRequest[] {
                request(member, kit.ids.nextId(), 0, Duration.ofMinutes(5)),
                request(member, kit.ids.nextId(), -10, Duration.ofMinutes(5)),
                request(member, kit.ids.nextId(), 1000, Duration.ofSeconds(59)),
                request(member, kit.ids.nextId(), 1000, Duration.ofMinutes(61)),
                new PaymentOrderRequest(member, PaymentOrderType.PAYMENT_TEST, kit.ids.nextId(), " ", 1000, Duration.ofMinutes(5)),
                new PaymentOrderRequest(member, PaymentOrderType.PAYMENT_TEST, kit.ids.nextId(), "가".repeat(101), 1000, Duration.ofMinutes(5))}) {
            assertThatThrownBy(() -> kit.tx.executeWithoutResult(status -> kit.orders.create(bad))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 처리기가_없는_종류는_만들지_않는다() {
        for (PaymentOrderType type : new PaymentOrderType[] {PaymentOrderType.POINT_CHARGE, PaymentOrderType.GROUP_BUY}) {
            long ref = kit.ids.nextId();
            assertThatThrownBy(() -> kit.tx.executeWithoutResult(status -> kit.orders.create(new PaymentOrderRequest(kit.newMember(), type, ref, "x", 1000,
                    Duration.ofMinutes(5))))).as(type.name()).isInstanceOf(IllegalStateException.class);
            assertThat(kit.count("SELECT count(*) FROM payment_orders WHERE order_ref_id = ?", ref)).isZero();
        }
    }

    @Test
    void 결제_설정이_비면_503_이고_아무것도_만들지_않는다() {
        kit.gateway.notConfigured = true;
        long ref = kit.ids.nextId();
        assertThatThrownBy(() -> kit.tx.executeWithoutResult(status -> kit.orders.create(request(kit.newMember(), ref, 1000, Duration.ofMinutes(5)))))
                .isInstanceOf(PaymentNotConfiguredException.class)
                .satisfies(e -> assertThat(((PaymentNotConfiguredException) e).getStatus().value()).isEqualTo(503));
        assertThat(kit.count("SELECT count(*) FROM payment_orders WHERE order_ref_id = ?", ref)).isZero();
    }
}
