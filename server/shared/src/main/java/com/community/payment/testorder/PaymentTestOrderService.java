package com.community.payment.testorder;

import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PgPaymentStatus;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.service.PaymentOrderService;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.testorder.PaymentTestViews.CancelView;
import com.community.payment.testorder.PaymentTestViews.CreatedView;
import com.community.payment.testorder.PaymentTestViews.FakeResult;
import com.community.payment.testorder.PaymentTestViews.StatusView;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 시험 주문(결정 7 — {@code /api/dev/payments}, {@code roboyam.payment.test-orders} 인 서버만). 실제 도메인이 하듯 「도메인 주문 + 결제 주문」을 한 트랜잭션에서 만든다 —
 * 시험 주문의 도메인 주문 번호는 새 Snowflake ID 이고 판정은 처리기 메모리에 둔다. 가짜 게이트웨이일 때는 가짜 결제창의 결과(결제함·실패)를 가짜 PG 에 쓴다.
 * 모두 그 회원의 시험 결제만 다룬다(남의 결제는 404).
 */
@Service
@ConditionalOnProperty(prefix = "roboyam.payment", name = "test-orders", havingValue = "true")
public class PaymentTestOrderService {

    /** 시험 주문 금액 범위(원) */
    public static final long MIN_AMOUNT = 100;
    public static final long MAX_AMOUNT = 1_000_000;
    /** 시험 주문의 유효 시간 */
    public static final Duration VALID_FOR = Duration.ofMinutes(10);
    /** 시험 주문의 주문명 */
    public static final String ORDER_NAME = "결제 시험 주문";

    private final PaymentOrderService orders;
    private final PaymentOrderStore orderStore;
    private final PaymentCancelStore cancelStore;
    private final PaymentTestOrderHandler handler;
    private final PaymentGateway gateway;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public PaymentTestOrderService(PaymentOrderService orders, PaymentOrderStore orderStore, PaymentCancelStore cancelStore, PaymentTestOrderHandler handler,
                                   PaymentGateway gateway, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.orderStore = orderStore;
        this.cancelStore = cancelStore;
        this.handler = handler;
        this.gateway = gateway;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 시험 주문을 만든다.
     *
     * @throws ValidationException 금액이 범위 밖({@code fields.amount})
     */
    @Transactional
    public CreatedView create(long userId, long amount, PaymentTestOrderHandler.Decision decision) {
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new ValidationException(Map.of("amount", new MessageRef("payment.test.amount.range", MIN_AMOUNT, MAX_AMOUNT)));
        }
        long orderRefId = ids.nextId();
        handler.decide(orderRefId, decision);
        PaymentCheckout checkout = orders.create(new PaymentOrderRequest(userId, PaymentOrderType.PAYMENT_TEST, orderRefId, ORDER_NAME, amount, VALID_FOR));
        return new CreatedView(Long.toString(orderRefId), checkout.paymentId(), checkout.orderName(), checkout.amount(), checkout.currency(), checkout.expiresAt(),
                checkout.provider(), checkout.storeId(), checkout.channelKey());
    }

    /**
     * 가짜 결제창의 결과를 가짜 PG 에 쓴다(가짜 게이트웨이일 때만 — 포트원이면 404). 화면은 이어서 완료 API 를 부른다.
     *
     * @param amount 결제했다고 할 금액(null 이면 주문 금액)
     */
    public void fakePay(long userId, String paymentId, FakeResult result, Long amount) {
        if (!(gateway instanceof FakePaymentGateway fake)) {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
        OrderRow order = mine(userId, paymentId);
        long paid = amount == null ? order.amount() : amount;
        if (result == FakeResult.PAID) {
            fake.store().pay(paymentId, paid, "KRW", clock.instant().truncatedTo(ChronoUnit.MICROS));
        } else {
            fake.store().setStatus(paymentId, PgPaymentStatus.FAILED, paid, clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
    }

    /** 그 시험 결제의 지금 상태 */
    public StatusView status(long userId, String paymentId) {
        OrderRow order = mine(userId, paymentId);
        return new StatusView(order.paymentId(), Long.toString(order.orderRefId()), order.status(), order.anomaly(), order.amount(), order.paidAmount(),
                order.canceledAmount(), handler.cashOf(order.orderRefId()), cancelStore.findByOrder(order.id()).stream()
                .map(c -> new CancelView(Long.toString(c.id()), c.kind(), c.amount(), c.status(), c.pgStatus(), c.attempts())).toList());
    }

    private OrderRow mine(long userId, String paymentId) {
        return orderStore.findByPaymentId(paymentId).filter(o -> o.userId() == userId && o.orderType() == PaymentOrderType.PAYMENT_TEST)
                .orElseThrow(PaymentOrderNotFoundException::new);
    }
}
