package com.community.payment.service;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PgPayment;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>확정 코드 하나</b>(commerce.md 8-2 의 4, 계약 「확정 흐름」) — 완료 API·웹훅·READY 정리·관리자 다시 확인(2a-3)이 모두 이것을 부른다.
 *
 * <ol>
 *   <li>포트원 단건 조회는 <b>트랜잭션 밖</b>에서 한다(DB 잠금을 쥔 채 HTTP 를 기다리지 않는다). 본문(웹훅)을 믿지 않고 조회 결과만 믿는다. PAID 가 아니면 그대로(NOT_PAID).</li>
 *   <li>새 트랜잭션 하나: 결제 주문 행을 잠그고 READY·EXPIRED 일 때만 PAID 로(이긴 쪽만 — 진 쪽은 같은 결과를 돌려받는다). 그 전에 <b>늦은 결제</b>(결정 4 — 우리 상태가 EXPIRED 면 무조건,
 *       READY 면 포트원 {@code paidAt > expires_at} 일 때만) → <b>금액·통화</b>(포트원 총 결제금액 = 청구 금액, KRW) 차례로 판정해 이상이면 같은 UPDATE 에 이상을 싣는다
 *       (받아들인 결제 하나 색인에 부딪히지 않게 — 결정 3). 이상이면 처리기를 부르지 않고 전액 자동 취소를 넣는다(금액 불일치는 운영자 알림).</li>
 *   <li>이상이 없으면 처리기 {@code onPaid} — 받아들이면 끝, 거절하면 {@code DOMAIN_REJECTED} + 전액 자동 취소. <b>처리기 예외는 전체를 되돌린다</b>(READY 그대로, 취소 없음 —
 *       다음 웹훅·READY 정리가 다시 시도).</li>
 *   <li>자동 취소는 커밋 뒤 그 자리에서 한 번 시도된다({@code PaymentCancelImmediateAttempt}).</li>
 * </ol>
 */
@Service
public class PaymentConfirmService {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmService.class);

    /** 확정 결과 */
    public enum Outcome {
        /** 받아들였다(이번에 확정) */
        ACCEPTED,
        /** 이상 결제로 자동 취소를 넣었다(이번에 확정 — 늦은 결제·금액 불일치·도메인 거절) */
        REJECTED,
        /** 포트원이 아직 결제 완료가 아니다 — 결제 주문은 그대로 */
        NOT_PAID,
        /** 이미 다른 길이 확정했다(또는 끝난 결제) — 지금 상태를 돌려준다 */
        ALREADY_DONE,
        /** 그런 결제 주문이 없다 */
        NOT_FOUND
    }

    /** 확정 결과와 그 뒤의 결제 주문 */
    public record ConfirmResult(Outcome outcome, OrderRow order) {
    }

    private final PaymentOrderStore orders;
    private final PaymentEventStore events;
    private final PaymentCancelService cancels;
    private final PaymentOrderHandlers handlers;
    private final PaymentGateway gateway;
    private final PaymentAnomalyService anomalies;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate separate;
    private final Clock clock;

    public PaymentConfirmService(PaymentOrderStore orders, PaymentEventStore events, PaymentCancelService cancels, PaymentOrderHandlers handlers,
                                 PaymentGateway gateway, PaymentAnomalyService anomalies, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                                 ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.events = events;
        this.cancels = cancels;
        this.handlers = handlers;
        this.gateway = gateway;
        this.anomalies = anomalies;
        this.ids = ids;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 그 결제를 확정한다. 트랜잭션 밖에서 부른다(부르는 쪽 트랜잭션이 있어도 확정은 새 트랜잭션으로 따로 확정된다).
     *
     * @throws com.community.payment.exception.PaymentGatewayUnavailableException 포트원 연결 실패(503) — 아무것도 바꾸지 않았다
     * @throws com.community.payment.exception.PaymentNotConfiguredException      결제 설정이 비었다(503)
     * @throws RuntimeException 처리기 예외 — 확정 전체가 되돌려졌다
     */
    public ConfirmResult confirm(String paymentId, ConfirmedVia via) {
        Optional<OrderRow> found = orders.findByPaymentId(paymentId);
        if (found.isEmpty()) {
            return new ConfirmResult(Outcome.NOT_FOUND, null);
        }
        OrderRow order = found.get();
        if (order.status() != PaymentStatus.READY && order.status() != PaymentStatus.EXPIRED) {
            return new ConfirmResult(Outcome.ALREADY_DONE, order);
        }
        PgPayment pg = gateway.getPayment(paymentId);
        if (!pg.paid()) {
            log.info("결제가 아직 완료되지 않았다 paymentOrderId={} pgStatus={} via={}", order.id(), pg.status(), via);
            return new ConfirmResult(Outcome.NOT_PAID, order);
        }
        return separate.execute(status -> settle(order.id(), pg, via));
    }

    /**
     * READY 를 EXPIRED 로 닫는다(READY 정리 — 포트원이 결제 완료가 아니라고 했을 때). 새 트랜잭션.
     *
     * @return 닫았으면 true(이미 다른 길이 바꿨으면 false)
     */
    public boolean expireUnpaid(long paymentOrderId, String pgStatus) {
        Boolean expired = separate.execute(status -> {
            Instant now = now();
            if (!orders.expireReady(paymentOrderId, now)) {
                return false;
            }
            events.insert(ids.nextId(), paymentOrderId, PaymentEventStore.Kind.EXPIRED, Map.of("reason", "READY_CLEANUP", "pgStatus", pgStatus), now);
            return true;
        });
        return Boolean.TRUE.equals(expired);
    }

    private ConfirmResult settle(long orderId, PgPayment pg, ConfirmedVia via) {
        OrderRow locked = orders.lockById(orderId).orElseThrow();
        if (locked.status() != PaymentStatus.READY && locked.status() != PaymentStatus.EXPIRED) {
            return new ConfirmResult(Outcome.ALREADY_DONE, locked); // 다른 길이 먼저 확정했다
        }
        Instant now = now();
        Instant paidAt = pg.paidAt() == null ? now : pg.paidAt().truncatedTo(ChronoUnit.MICROS);
        PaymentAnomaly anomaly = judge(locked, pg);
        orders.markPaid(orderId, new PaymentOrderStore.PaidFields(pg.totalAmount(), paidAt, pg.payMethod(), pg.pgProvider(), pg.pgTxId(), pg.receiptUrl()), anomaly, via,
                now);
        Map<String, Object> paid = new LinkedHashMap<>();
        paid.put("via", via.name());
        paid.put("paidAmount", pg.totalAmount());
        paid.put("previousStatus", locked.status().name());
        events.insert(ids.nextId(), orderId, PaymentEventStore.Kind.PAID, paid, now);
        if (anomaly != null) {
            reject(locked, anomaly, pg.totalAmount(), Map.of("anomaly", anomaly.name(), "expectedAmount", locked.amount(), "pgAmount", pg.totalAmount(),
                    "pgCurrency", String.valueOf(pg.currency())), now);
            if (anomaly == PaymentAnomaly.AMOUNT_MISMATCH) {
                anomalies.notifyOperators(locked, PaymentAnomalyService.Reason.AMOUNT_MISMATCH);
            }
            return new ConfirmResult(Outcome.REJECTED, orders.findById(orderId).orElseThrow());
        }
        PaymentDecision decision = handlers.require(locked.orderType()).onPaid(new PaidPayment(orderId, locked.paymentId(), locked.userId(), locked.orderType(),
                locked.orderRefId(), pg.totalAmount(), paidAt, via));
        if (decision.accepted()) {
            return new ConfirmResult(Outcome.ACCEPTED, orders.findById(orderId).orElseThrow());
        }
        orders.markAnomaly(orderId, PaymentAnomaly.DOMAIN_REJECTED, now);
        reject(locked, PaymentAnomaly.DOMAIN_REJECTED, pg.totalAmount(), Map.of("anomaly", PaymentAnomaly.DOMAIN_REJECTED.name(), "reason", decision.reason()), now);
        return new ConfirmResult(Outcome.REJECTED, orders.findById(orderId).orElseThrow());
    }

    /** 늦은 결제(결정 4) → 금액·통화 차례로 판정. 이상이 없으면 null */
    static PaymentAnomaly judge(OrderRow order, PgPayment pg) {
        boolean late = order.status() == PaymentStatus.EXPIRED || (pg.paidAt() != null && pg.paidAt().isAfter(order.expiresAt()));
        if (late) {
            return PaymentAnomaly.LATE_PAYMENT;
        }
        if (pg.totalAmount() != order.amount() || !"KRW".equals(pg.currency())) {
            return PaymentAnomaly.AMOUNT_MISMATCH;
        }
        return null;
    }

    /** 이상 이력 + 전액 자동 취소(요청 키 {@code AUTO_REJECT:<결제 주문 ID>}) */
    private void reject(OrderRow order, PaymentAnomaly anomaly, long amount, Map<String, Object> detail, Instant now) {
        events.insert(ids.nextId(), order.id(), PaymentEventStore.Kind.ANOMALY, detail, now);
        cancels.request(new PaymentCancelOrder(order.id(), amount, CancelKind.AUTO_REJECT, "자동 취소 — " + anomaly.name(), "AUTO_REJECT:" + order.id(), null));
        log.warn("이상 결제 — 전액 자동 취소를 넣었다 paymentOrderId={} anomaly={}", order.id(), anomaly);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
