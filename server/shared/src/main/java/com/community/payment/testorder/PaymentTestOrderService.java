package com.community.payment.testorder;

import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.response.PaymentCheckoutView;
import com.community.payment.dto.response.PaymentResult;
import com.community.payment.exception.FakePgAlreadyDoneException;
import com.community.payment.exception.PaymentGatewayNotFakeException;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PgPaymentStatus;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.service.PaymentOrderService;
import com.community.payment.testorder.PaymentTestViews.CancelView;
import com.community.payment.testorder.PaymentTestViews.CreatedView;
import com.community.payment.testorder.PaymentTestViews.FakeOutcome;
import com.community.payment.testorder.PaymentTestViews.StatusView;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 시험 주문(결정 7 — {@code /api/dev/payments}, {@code roboyam.payment.test-orders} 인 서버만). 실제 도메인이 하듯 「도메인 주문 + 결제 주문」을 한 트랜잭션에서 만든다 —
 * 시험 주문의 도메인 주문 번호는 새 Snowflake ID 이고 판정은 처리기 메모리에 둔다. 가짜 게이트웨이일 때는 가짜 결제창의 결과(결제함·실패, 늦은 결제)를 가짜 PG 에 쓴다.
 * 모두 그 회원의 시험 결제만 다룬다(남의 결제는 404). 요청·응답 모양은 2a-1 화면 계약이 정본이다.
 */
@Service
@ConditionalOnProperty(prefix = "roboyam.payment", name = "test-orders", havingValue = "true")
public class PaymentTestOrderService {

    /** 시험 주문 금액 범위(원) */
    public static final long MIN_AMOUNT = 100;
    public static final long MAX_AMOUNT = 1_000_000;
    /** 시험 주문의 유효 시간 범위(분) — 결제 모듈의 범위(1~60분)와 같다 */
    public static final int MIN_VALID_MINUTES = 1;
    public static final int MAX_VALID_MINUTES = 60;
    /** 늦은 결제 흉내의 결제 시각 = 유효 시각 + 이 시간 */
    public static final Duration LATE_BY = Duration.ofMinutes(1);
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
     * 시험 주문을 만든다 — 결제 주문의 유효 시각은 결제 모듈이 정하는 그대로(만든 시각 + {@code validMinutes}분, {@link PaymentOrderService#create}).
     *
     * @param decision 처리기 판정(null 이면 ACCEPT)
     * @throws ValidationException 금액·유효 시간이 범위 밖({@code fields.amount}·{@code fields.validMinutes} — 둘 다 틀리면 둘 다)
     * @throws com.community.payment.exception.PaymentNotConfiguredException 포트원 결제 설정이 비었다(503)
     */
    @Transactional
    public CreatedView create(long userId, Long amount, Integer validMinutes, PaymentTestOrderHandler.Decision decision) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (amount == null || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            fields.put("amount", new MessageRef("payment.test.amount.range", MIN_AMOUNT, MAX_AMOUNT));
        }
        if (validMinutes == null || validMinutes < MIN_VALID_MINUTES || validMinutes > MAX_VALID_MINUTES) {
            fields.put("validMinutes", new MessageRef("payment.test.validMinutes.range", MIN_VALID_MINUTES, MAX_VALID_MINUTES));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long orderRefId = ids.nextId();
        handler.decide(orderRefId, decision);
        PaymentCheckout checkout = orders.create(new PaymentOrderRequest(userId, PaymentOrderType.PAYMENT_TEST, orderRefId, ORDER_NAME, amount,
                Duration.ofMinutes(validMinutes)));
        return new CreatedView(PaymentOrderType.PAYMENT_TEST.name(), Long.toString(orderRefId), checkout.expiresAt(), PaymentCheckoutView.of(checkout));
    }

    /**
     * 가짜 결제창의 결과를 가짜 PG 에 쓴다. 화면은 결제했으면 이어서 완료 API 를 부른다.
     *
     * @param amount 결제했다고 할 금액(null 이면 주문 금액, PAID 일 때 1원 이상)
     * @param late   true 면 결제 시각 = 그 결제 주문의 유효 시각 + 1분(늦은 결제 시험)
     * 시험 결제뿐 아니라 그 회원의 모든 결제 주문(공구·반품비 등)을 받는다 — 가짜 게이트웨이에서는 업무 결제창도 이 길로 결과를 남긴다.
     *
     * @throws PaymentOrderNotFoundException  그 회원의 결제 주문이 아니다(404)
     * @throws PaymentGatewayNotFakeException 가짜 게이트웨이가 아니다(409)
     * @throws FakePgAlreadyDoneException     그 결제의 결과가 가짜 PG 에 이미 있다(409)
     * @throws ValidationException            금액이 1원 미만({@code fields.amount})
     */
    public void fakeCheckout(long userId, String paymentId, FakeOutcome outcome, Long amount, boolean late) {
        OrderRow order = orderStore.findByPaymentId(paymentId).filter(o -> o.userId() == userId).orElseThrow(PaymentOrderNotFoundException::new);
        if (!(gateway instanceof FakePaymentGateway fake)) {
            throw new PaymentGatewayNotFakeException();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean written;
        if (outcome == FakeOutcome.PAID) {
            if (amount != null && amount < 1) {
                throw new ValidationException(Map.of("amount", new MessageRef("payment.test.fakeAmount.min", 1)));
            }
            long paid = amount == null ? order.amount() : amount;
            Instant paidAt = late ? order.expiresAt().plus(LATE_BY) : now;
            written = fake.store().finishCheckoutOnce(paymentId, PgPaymentStatus.PAID, paid, order.currency(), paidAt, now);
        } else {
            written = fake.store().finishCheckoutOnce(paymentId, PgPaymentStatus.FAILED, order.amount(), order.currency(), null, now);
        }
        if (!written) {
            throw new FakePgAlreadyDoneException();
        }
    }

    /** 그 시험 결제의 지금 결과({@link PaymentResult}) + 처리기 현금·취소 줄 전부 */
    public StatusView status(long userId, String paymentId) {
        OrderRow order = mine(userId, paymentId);
        List<CancelRow> cancels = cancelStore.findByOrder(order.id());
        return new StatusView(PaymentResult.of(order, cancels), handler.cashOf(order.orderRefId()), cancels.stream()
                .map(c -> new CancelView(Long.toString(c.id()), c.kind(), c.amount(), c.status(), c.pgStatus(), c.attempts())).toList());
    }

    private OrderRow mine(long userId, String paymentId) {
        return orderStore.findByPaymentId(paymentId).filter(o -> o.userId() == userId && o.orderType() == PaymentOrderType.PAYMENT_TEST)
                .orElseThrow(PaymentOrderNotFoundException::new);
    }
}
