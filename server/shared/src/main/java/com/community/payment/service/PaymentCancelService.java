package com.community.payment.service;

import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.dto.PaymentCancelRequested;
import com.community.payment.exception.PaymentCancelExceedsException;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 취소 대기열에 넣기 — <b>모든 PG 취소는 이 대기열을 지난다</b>(commerce.md 8-1·8-3, D10). 도메인의 업무 트랜잭션(또는 확정 트랜잭션) 안에서 부른다({@code MANDATORY}).
 *
 * <ul>
 *   <li>결제 주문 행을 잠그고 남은 취소 가능 금액 = 결제 − 취소됨 − 아직 끝나지 않은 취소(PENDING·SENDING·GAVE_UP)를 넘는 취소는 거절한다(409 {@code PAYMENT_CANCEL_EXCEEDS}).</li>
 *   <li>같은 요청 키는 한 번만 — 다시 넣으면 처음 줄의 ID 를 돌려준다(자연 키 멱등, commerce.md 3절).</li>
 *   <li>커밋 뒤 그 자리에서 한 번 시도한다({@link PaymentCancelRequested} → {@code PaymentCancelImmediateAttempt}). 실패하면 1분 워커가 이어 받는다.</li>
 * </ul>
 */
@Service
public class PaymentCancelService {

    private final PaymentOrderStore orders;
    private final PaymentCancelStore cancels;
    private final PaymentEventStore events;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public PaymentCancelService(PaymentOrderStore orders, PaymentCancelStore cancels, PaymentEventStore events, SnowflakeIdGenerator ids,
                                ApplicationEventPublisher publisher, ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.cancels = cancels;
        this.events = events;
        this.ids = ids;
        this.publisher = publisher;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 취소를 넣는다.
     *
     * @return 취소 대기열 ID(같은 요청 키가 이미 있으면 그 줄)
     * @throws PaymentCancelExceedsException 남은 취소 가능 금액을 넘거나 결제되지 않은 주문
     * @throws IllegalArgumentException      금액·사유·요청 키가 범위 밖(도메인 코드의 결함)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long request(PaymentCancelOrder order) {
        if (order.amount() < 1 || order.kind() == null || order.reason() == null || order.reason().isBlank() || order.reason().strip().length() > 200
                || order.requestKey() == null || order.requestKey().isBlank() || order.requestKey().length() > 120) {
            throw new IllegalArgumentException("취소 금액·종류·사유(1~200자)·요청 키(1~120자)를 확인");
        }
        Optional<PaymentCancelStore.CancelRow> existing = cancels.findByRequestKey(order.requestKey());
        if (existing.isPresent()) {
            return existing.get().id();
        }
        OrderRow locked = orders.lockById(order.paymentOrderId()).orElseThrow(PaymentCancelExceedsException::new);
        if (!locked.status().paid()) {
            throw new PaymentCancelExceedsException();
        }
        long remaining = locked.cancellable() - cancels.openAmount(locked.id());
        if (order.amount() > remaining) {
            throw new PaymentCancelExceedsException();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long id = ids.nextId();
        if (!cancels.insert(id, locked.id(), order.amount(), order.reason().strip(), order.kind(), order.requestKey(), order.requestedBy(), now)) {
            return cancels.findByRequestKey(order.requestKey()).orElseThrow().id(); // 동시에 같은 키를 넣었다 — 먼저 넣은 줄
        }
        events.insert(ids.nextId(), locked.id(), PaymentEventStore.Kind.CANCEL_REQUESTED,
                Map.of("cancelId", Long.toString(id), "kind", order.kind().name(), "amount", order.amount()), now);
        publisher.publishEvent(new PaymentCancelRequested(id));
        return id;
    }
}
