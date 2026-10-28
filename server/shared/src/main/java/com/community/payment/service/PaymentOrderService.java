package com.community.payment.service;

import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.gateway.PaymentCheckoutInfo;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.community.payment.dto.PaymentOrderType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 주문 만들기(commerce.md 8-2 의 1) — 도메인이 자기 주문(충전 건·참여 건)과 <b>같은 트랜잭션</b>에서 부른다({@code MANDATORY}).
 *
 * <ul>
 *   <li>결제 ID·금액·유효 시각은 서버가 정한다 — 결제 ID = {@code pay_} + 무작위 32자(영숫자, {@link SecureRandom}), 금액은 도메인이 계산한 값, 유효 시각 = 지금 + 유효 시간(1~60분).</li>
 *   <li><b>재결제마다 새 결제 ID</b> — 같은 주문의 열린 READY 는 EXPIRED 로 닫는다(commerce.md 8-1, 포트원은 실패한 ID 의 재사용을 보장하지 않는다). 닫은 시도에 늦게 결제가 오면 늦은 결제로 자동 취소.</li>
 *   <li>그 종류의 처리기가 없으면 만들지 않는다(설정 오류). 결제 설정이 비었으면 503 {@code PAYMENT_NOT_CONFIGURED} — 앱은 뜨고 결제 시작만 막힌다(commerce.md 8-5).</li>
 * </ul>
 */
@Service
public class PaymentOrderService {

    static final Duration MIN_VALID = Duration.ofMinutes(1);
    static final Duration MAX_VALID = Duration.ofMinutes(60);
    static final int ORDER_NAME_MAX = 100;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentOrderStore orders;
    private final PaymentEventStore events;
    private final PaymentOrderHandlers handlers;
    private final PaymentGateway gateway;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public PaymentOrderService(PaymentOrderStore orders, PaymentEventStore events, PaymentOrderHandlers handlers, PaymentGateway gateway, SnowflakeIdGenerator ids,
                               ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.events = events;
        this.handlers = handlers;
        this.gateway = gateway;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 결제 주문을 만든다.
     *
     * @throws IllegalArgumentException 금액·주문명·유효 시간이 범위 밖(도메인 코드의 결함)
     * @throws IllegalStateException    그 종류의 처리기가 없다
     * @throws com.community.payment.exception.PaymentNotConfiguredException 결제 설정이 비었다(503)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentCheckout create(PaymentOrderRequest request) {
        validate(request);
        handlers.require(request.orderType());
        PaymentCheckoutInfo checkout = gateway.checkoutInfo();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Long> closed = orders.expireOpenReady(request.orderType(), request.orderRefId(), now);
        for (long previous : closed) {
            events.insert(ids.nextId(), previous, PaymentEventStore.Kind.EXPIRED, Map.of("reason", "RETRY_PAYMENT"), now);
        }
        long id = ids.nextId();
        String paymentId = newPaymentId();
        Instant expiresAt = now.plus(request.validFor());
        orders.insert(new PaymentOrderStore.NewOrder(id, paymentId, request.userId(), request.orderType(), request.orderRefId(), request.orderName().strip(),
                request.amount(), expiresAt, now));
        events.insert(ids.nextId(), id, PaymentEventStore.Kind.CREATED, Map.of("amount", request.amount(), "expiresAt", expiresAt.toString()), now);
        return new PaymentCheckout(id, paymentId, request.orderName().strip(), request.amount(), "KRW", expiresAt, checkout.provider(), checkout.storeId(),
                checkout.channelKey());
    }

    /**
     * 그 도메인 주문의 열린 READY 를 모두 EXPIRED 로 닫는다 — 도메인이 결제 중 주문을 닫을 때(선점 만료·중단·거절) <b>결제 행을 먼저</b> 잠그는 길이다
     * (공구 계약 결정 37 — 결제 확정이 결제 행 → 도메인 주문 차례라 같은 차례가 된다). 닫은 뒤 결제가 오면 늦은 결제로 자동 취소된다. 닫은 결제 주문 ID(없으면 빈 목록 — 0 행이어도 부르는 쪽은 계속한다).
     *
     * @param reason 이력에 남길 까닭(대문자·밑줄)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> expireOpen(PaymentOrderType orderType, long orderRefId, String reason) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Long> closed = orders.expireOpenReady(orderType, orderRefId, now);
        for (long id : closed) {
            events.insert(ids.nextId(), id, PaymentEventStore.Kind.EXPIRED, Map.of("reason", reason), now);
        }
        return closed;
    }

    /**
     * 그 도메인 주문의 아직 유효한 READY 결제창 정보(없으면 빈 값) — 「결제 다시 열기」를 두 번 눌러도 앞 결제를 늦은 결제로 만들지 않게 같은 결제를 돌려준다(공구 계약 결정 63).
     *
     * @param minRemaining 남은 유효 시간이 이보다 짧으면 없는 것으로 본다
     */
    public Optional<PaymentCheckout> openCheckout(PaymentOrderType orderType, long orderRefId, Duration minRemaining) {
        Instant now = clock.instant();
        Optional<PaymentOrderStore.OrderRow> open = orders.findOpenReady(orderType, orderRefId);
        if (open.isEmpty() || open.get().expiresAt().isBefore(now.plus(minRemaining))) {
            return Optional.empty();
        }
        PaymentOrderStore.OrderRow row = open.get();
        PaymentCheckoutInfo checkout = gateway.checkoutInfo();
        return Optional.of(new PaymentCheckout(row.id(), row.paymentId(), row.orderName(), row.amount(), row.currency(), row.expiresAt(), checkout.provider(),
                checkout.storeId(), checkout.channelKey()));
    }

    /** 결제 ID — {@code pay_} + 무작위 영숫자 32자(36자) */
    static String newPaymentId() {
        StringBuilder out = new StringBuilder("pay_");
        for (int i = 0; i < 32; i++) {
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    private static void validate(PaymentOrderRequest request) {
        if (request.orderType() == null) {
            throw new IllegalArgumentException("주문 종류가 비었다");
        }
        if (request.amount() < 1) {
            throw new IllegalArgumentException("결제 금액은 1원 이상이다");
        }
        if (request.orderName() == null || request.orderName().isBlank() || request.orderName().strip().length() > ORDER_NAME_MAX) {
            throw new IllegalArgumentException("주문명은 1~" + ORDER_NAME_MAX + "자다");
        }
        if (request.validFor() == null || request.validFor().compareTo(MIN_VALID) < 0 || request.validFor().compareTo(MAX_VALID) > 0) {
            throw new IllegalArgumentException("유효 시간은 1~60분이다");
        }
    }
}
