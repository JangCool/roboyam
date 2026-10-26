package com.community.payment.service;

import com.community.batch.core.BatchErrorText;
import com.community.payment.config.PaymentProperties;
import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.dto.PgCancelStatus;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PgCancelResult;
import com.community.payment.gateway.PgCancellation;
import com.community.payment.gateway.PgPayment;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 취소 한 건 실행(계약 「확정 흐름」 5, commerce.md 8-3) — 커밋 뒤 즉시 시도({@link #attemptNow})와 1분 워커({@code PaymentCancelWorker})가 같은 길을 쓴다.
 *
 * <ol>
 *   <li><b>집기</b>: {@code PENDING → SENDING}(+{@code locked_by}) 조건부 UPDATE 로 한쪽만 집는다(즉시 시도와 워커가 부딪쳐도 한 번).</li>
 *   <li><b>재시도 전 조회</b>(두 번째 시도부터): 포트원 결제를 조회해 우리 취소(취소 내역 아이디 또는 사유의 {@code rc:<취소 ID>} 표시)가 이미 SUCCEEDED 면 보내지 않고 성공으로 맞춘다,
 *       REQUESTED(접수만)면 보내지 않고 기다린다 — 시간 초과·「접수만」 뒤의 이중 취소를 막는다.</li>
 *   <li><b>보내기</b>: 새 트랜잭션에서 결제 주문 행을 잠그고 포트원 취소에 {@code amount}·{@code currentCancellableAmount}(결제 − 취소됨)·{@code reason} 을 보낸다.</li>
 *   <li><b>성공</b>: 취소 줄을 SUCCEEDED 로(이긴 쪽만) → 결제 주문의 취소 누계를 조건부로 늘리고({@code canceled_amount + 금액 ≤ paid_amount}) 상태를 부분/전액 취소로 → 처리기 {@code onCanceled}(한 번).</li>
 *   <li><b>실패·접수만·연결 실패</b>: 시도 한도(즉시 1 + 재시도 5)까지 간격 1·5·30·120·720분으로 다시, 다 쓰면 GAVE_UP + 결제 이상 운영자 알림(P17 — 도메인 확정은 없다).</li>
 * </ol>
 * 오류는 가린 한 줄({@link BatchErrorText})로 남기고 로그에는 예외 클래스 이름만 남긴다.
 */
@Service
public class PaymentCancelExecutor {

    private static final Logger log = LoggerFactory.getLogger(PaymentCancelExecutor.class);

    private final PaymentOrderStore orders;
    private final PaymentCancelStore cancels;
    private final PaymentEventStore events;
    private final PaymentOrderHandlers handlers;
    private final PaymentGateway gateway;
    private final PaymentAnomalyService anomalies;
    private final PaymentProperties properties;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate separate;
    private final int nodeId;
    private final Clock clock;

    public PaymentCancelExecutor(PaymentOrderStore orders, PaymentCancelStore cancels, PaymentEventStore events, PaymentOrderHandlers handlers, PaymentGateway gateway,
                                 PaymentAnomalyService anomalies, PaymentProperties properties, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                                 @Value("${roboyam.node-id}") int nodeId, ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.cancels = cancels;
        this.events = events;
        this.handlers = handlers;
        this.gateway = gateway;
        this.anomalies = anomalies;
        this.properties = properties;
        this.ids = ids;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.nodeId = nodeId;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 이 서버의 번호(집은 줄의 {@code locked_by}) */
    public int nodeId() {
        return nodeId;
    }

    /**
     * 커밋 뒤 즉시 시도 — 그 줄이 아직 PENDING 이고 차례가 됐으면 집어 한 번 실행한다.
     *
     * @return 집어서 실행했으면 true(다른 쪽이 먼저 집었으면 false)
     */
    public boolean attemptNow(long cancelId) {
        // 커밋 뒤 단계에서는 끝난 트랜잭션의 연결이 아직 묶여 있다 — 새 트랜잭션으로 집어 확정해야 다른 연결(보내기)이 SENDING 을 본다
        Optional<CancelRow> claimed = separate.execute(status -> cancels.claimOne(cancelId, nodeId, now()));
        claimed.ifPresent(this::execute);
        return claimed.isPresent();
    }

    /** 이미 집은(SENDING) 줄 하나를 실행한다 — 던지지 않는다(실패는 재시도·GAVE_UP 으로 남긴다) */
    public void execute(CancelRow claimed) {
        try {
            OrderRow order = orders.findById(claimed.paymentOrderId()).orElseThrow(() -> new IllegalStateException("결제 주문이 없다"));
            if (claimed.attempts() > 1) {
                Optional<PgCancellation> ours = ours(gateway.getPayment(order.paymentId()), claimed);
                if (ours.isPresent() && ours.get().status() == PgCancelStatus.SUCCEEDED) {
                    separate.executeWithoutResult(status -> finish(claimed, nodeId, ours.get().id()));
                    log.info("재시도 전 조회 — 포트원에서 이미 취소됐다 cancelId={}", claimed.id());
                    return;
                }
                if (ours.isPresent() && ours.get().status() == PgCancelStatus.REQUESTED) {
                    separate.executeWithoutResult(status -> retryOrGiveUp(claimed, order, PgCancelStatus.REQUESTED, ours.get().id(), "PG_REQUESTED"));
                    return;
                }
            }
            separate.executeWithoutResult(status -> send(claimed));
        } catch (PaymentGatewayUnavailableException | PaymentNotConfiguredException e) {
            log.warn("포트원 취소를 보내지 못했다 cancelId={} attempts={} 원인={}", claimed.id(), claimed.attempts(), e.getErrorCode());
            failSeparately(claimed, e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("취소 실행이 실패했다 cancelId={} attempts={} 원인={}", claimed.id(), claimed.attempts(), e.getClass().getSimpleName());
            failSeparately(claimed, BatchErrorText.of(e));
        }
    }

    /**
     * 포트원이 알려 준 성공으로 끝낸다(웹훅이 취소 목록을 맞출 때) — 끝나지 않은 줄(PENDING·SENDING·GAVE_UP)만. 새 트랜잭션.
     *
     * @return 이번에 끝냈으면 true
     */
    public boolean settleFromProvider(CancelRow row, String pgCancelId) {
        Boolean done = separate.execute(status -> finish(row, null, pgCancelId));
        return Boolean.TRUE.equals(done);
    }

    /** 결제 주문 행을 잠그고 보낸다 → 성공이면 끝내고, 아니면 재시도·GAVE_UP(같은 트랜잭션) */
    private void send(CancelRow claimed) {
        OrderRow locked = orders.lockById(claimed.paymentOrderId()).orElseThrow();
        PgCancelResult result = gateway.cancel(locked.paymentId(), claimed.amount(), locked.cancellable(), providerReason(claimed));
        if (result.succeeded()) {
            finish(claimed, nodeId, result.cancellationId());
            return;
        }
        String error = result.errorType() == null ? "PG_" + result.status().name() : result.errorType();
        log.warn("포트원 취소가 끝나지 않았다 cancelId={} attempts={} pgStatus={} type={}", claimed.id(), claimed.attempts(), result.status(), error);
        retryOrGiveUp(claimed, locked, result.status(), result.cancellationId(), error);
    }

    /**
     * 성공으로 끝낸다 — 결제 주문 행을 먼저 잠그고(잠그는 차례를 맞춰 교착을 막는다) 취소 줄을 SUCCEEDED 로(이긴 쪽만) → 취소 누계·상태 → 이력 → {@code onCanceled}.
     *
     * @param holder 집은 서버(워커·즉시 시도) — null 이면 끝나지 않은 줄 어느 것이든
     * @return 이번에 끝냈으면 true
     */
    private boolean finish(CancelRow row, Integer holder, String pgCancelId) {
        OrderRow locked = orders.lockById(row.paymentOrderId()).orElseThrow();
        Instant now = now();
        if (!cancels.markSucceeded(row.id(), holder, pgCancelId, now)) {
            return false;
        }
        if (!orders.addCanceled(locked.id(), row.amount(), now)) {
            throw new IllegalStateException("취소 누계가 결제 금액을 넘는다 — 취소 대기열과 결제 주문이 어긋났다");
        }
        OrderRow after = orders.findById(locked.id()).orElseThrow();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cancelId", Long.toString(row.id()));
        detail.put("amount", row.amount());
        detail.put("canceledTotal", after.canceledAmount());
        events.insert(ids.nextId(), locked.id(), PaymentEventStore.Kind.CANCEL_SUCCEEDED, detail, now);
        handlers.require(after.orderType()).onCanceled(new CanceledPayment(after.id(), after.paymentId(), after.userId(), after.orderType(), after.orderRefId(), row.id(),
                row.kind(), row.amount(), after.canceledAmount(), after.status() == PaymentStatus.CANCELED, after.anomaly(), now));
        log.info("취소를 끝냈다 cancelId={} paymentOrderId={} amount={}", row.id(), locked.id(), row.amount());
        return true;
    }

    private void retryOrGiveUp(CancelRow claimed, OrderRow order, PgCancelStatus pgStatus, String pgCancelId, String error) {
        Instant now = now();
        if (claimed.attempts() >= properties.cancelMaxAttempts()) {
            if (cancels.markGaveUp(claimed.id(), nodeId, pgStatus, pgCancelId, error, now)) {
                events.insert(ids.nextId(), order.id(), PaymentEventStore.Kind.CANCEL_GAVE_UP,
                        Map.of("cancelId", Long.toString(claimed.id()), "attempts", claimed.attempts(), "error", error), now);
                anomalies.notifyOperators(order, PaymentAnomalyService.Reason.CANCEL_GAVE_UP);
                log.error("취소를 시도 한도({})까지 끝내지 못해 그만뒀다 cancelId={} paymentOrderId={}", properties.cancelMaxAttempts(), claimed.id(), order.id());
            }
            return;
        }
        Instant next = now.plus(properties.backoffAfter(claimed.attempts()));
        if (cancels.markRetry(claimed.id(), nodeId, pgStatus, pgCancelId, error, next)) {
            events.insert(ids.nextId(), order.id(), PaymentEventStore.Kind.CANCEL_RETRY,
                    Map.of("cancelId", Long.toString(claimed.id()), "attempts", claimed.attempts(), "error", error, "nextAttemptAt", next.toString()), now);
        }
    }

    private void failSeparately(CancelRow claimed, String error) {
        try {
            separate.executeWithoutResult(status -> retryOrGiveUp(claimed, orders.findById(claimed.paymentOrderId()).orElseThrow(), null, null, error));
        } catch (RuntimeException e) {
            // 기록까지 실패했다 — 줄은 SENDING 으로 남고 워커가 오래 머문 줄로 되살린다
            log.error("취소 재시도 기록을 남기지 못했다 cancelId={} 원인={}", claimed.id(), e.getClass().getSimpleName());
        }
    }

    /** 포트원에 보낼 사유 — 우리 취소 표시 {@code rc:<취소 ID>} 를 붙인다(취소 내역 아이디를 받지 못했을 때 재시도 전 조회·웹훅이 우리 취소를 알아본다) */
    static String providerReason(CancelRow row) {
        return "roboyam " + row.kind().name() + " rc:" + row.id();
    }

    /** 포트원 취소 목록에서 이 취소 — 취소 내역 아이디가 같거나 사유에 우리 표시가 있는 것(성공한 것을 먼저) */
    static Optional<PgCancellation> ours(PgPayment pg, CancelRow row) {
        String tag = "rc:" + row.id();
        List<PgCancellation> matched = pg.cancellations().stream()
                .filter(c -> (row.pgCancelId() != null && row.pgCancelId().equals(c.id())) || (c.reason() != null && endsWithTag(c.reason(), tag)))
                .toList();
        return matched.stream().filter(c -> c.status() == PgCancelStatus.SUCCEEDED).findFirst()
                .or(() -> matched.stream().filter(c -> c.status() == PgCancelStatus.REQUESTED).findFirst());
    }

    /** 사유의 우리 취소 표시에서 취소 ID — 없으면 null */
    public static Long cancelIdOf(String reason) {
        if (reason == null) {
            return null;
        }
        int at = reason.lastIndexOf("rc:");
        if (at < 0) {
            return null;
        }
        try {
            return Long.parseLong(reason.substring(at + 3).strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean endsWithTag(String reason, String tag) {
        return reason.strip().endsWith(tag);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
