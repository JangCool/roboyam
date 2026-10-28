package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.repository.master.FulfillmentStore.EscrowJobRow;
import com.community.groupbuy.repository.master.FulfillmentStore.ShipmentRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.payment.gateway.EscrowLogistics;
import com.community.payment.gateway.EscrowResult;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 에스크로 호출 대기열 워커(계약 결정 4·14·64) — 포트원 호출은 <b>트랜잭션 밖</b>, 결과는 발송 줄에 조건부로. 「이미 등록·완료」는 성공, 에스크로 결제가 아니면 건너뜀(SKIPPED).
 *
 * <ul>
 *   <li>LOGISTICS: 배송 정보 등록(이미 등록됐으면 수정 — 송장 정정·교환 재발송). COMPLETE: 구매 확정 — 배송완료 뒤 청약철회 기간(7일)이 열린 요청 없이 지나면 넣는다({@link #enqueueCompleteDue}).</li>
 *   <li>재시도 표: 1분 → 5분 → 30분 → 2시간 → 6시간, 여섯 번째 실패에서 GAVE_UP(이벤트 {@link GroupBuyEvents.EscrowGaveUp} — 운영자 알림은 2b-4).</li>
 *   <li>집을 때 다음 시도를 잠시 미뤄(임대) 서버 여럿이 같은 줄을 겹쳐 부르지 않는다.</li>
 * </ul>
 */
@Service
public class GroupBuyEscrowWorker {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyEscrowWorker.class);

    /** 재시도 간격(시도 n 번 실패 뒤) — 다 쓰면 GAVE_UP */
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6));
    /** 구매 확정까지 기다리는 날(청약철회 7일 — 결정 14) */
    static final Duration COMPLETE_AFTER = Duration.ofDays(7);
    static final int BATCH = 50;
    private static final Duration LEASE = Duration.ofMinutes(5);

    private final FulfillmentStore store;
    private final ParticipationStore participations;
    private final PaymentOrderStore paymentOrders;
    private final PaymentGateway gateway;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GroupBuyEscrowWorker(FulfillmentStore store, ParticipationStore participations, PaymentOrderStore paymentOrders, PaymentGateway gateway, SnowflakeIdGenerator ids,
                                ApplicationEventPublisher events, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.participations = participations;
        this.paymentOrders = paymentOrders;
        this.gateway = gateway;
        this.ids = ids;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 한 바퀴 — 구매 확정 대상을 넣고, 차례가 된 일을 처리한다. 처리한 수 */
    public int runOnce() {
        enqueueCompleteDue();
        int n = 0;
        for (long jobId : store.escrowDue(now(), BATCH)) {
            if (process(jobId)) {
                n++;
            }
        }
        return n;
    }

    /** 배송완료 뒤 7일이 지났고 열린 요청이 없는 처음 발송에 구매 확정을 넣는다 */
    public int enqueueCompleteDue() {
        Instant now = now();
        List<Long> due = store.escrowCompleteDue(now.minus(COMPLETE_AFTER), BATCH);
        due.forEach(shipmentId -> tx.executeWithoutResult(status -> store.enqueueEscrow(ids.nextId(), shipmentId, "COMPLETE", now)));
        return due.size();
    }

    /** 방금 커밋한 발송들의 일을 바로 한 번 시도한다(실패해도 대기열이 이어 받는다) */
    public void runFor(List<Long> shipmentIds) {
        for (long shipmentId : shipmentIds) {
            for (EscrowJobRow job : store.escrowJobs(shipmentId)) {
                if ("PENDING".equals(job.status())) {
                    process(job.id());
                }
            }
        }
    }

    /** 한 일 — 집고(짧은 트랜잭션) → 포트원(밖) → 결과 기록(짧은 트랜잭션). 이번에 끝냈으면 true */
    boolean process(long jobId) {
        Instant now = now();
        Optional<EscrowJobRow> claimed = tx.execute(status -> store.claimEscrow(jobId, now, now.plus(LEASE)));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        EscrowJobRow job = claimed.get();
        ShipmentRow shipment = store.shipment(job.shipmentId()).orElseThrow();
        String paymentId = participations.find(shipment.participationId()).map(ParticipationStore.ParticipationRow::paymentOrderId)
                .flatMap(paymentOrders::findById).map(PaymentOrderStore.OrderRow::paymentId).orElse(null);
        EscrowResult result;
        String error;
        try {
            if (paymentId == null) {
                result = EscrowResult.notEscrow();
            } else if ("LOGISTICS".equals(job.kind())) {
                result = gateway.registerEscrowLogistics(paymentId, new EscrowLogistics(shipment.carrierCode(), shipment.trackingNo(), shipment.shippedAt()),
                        shipment.escrowLogisticsAt() != null);
            } else {
                result = gateway.completeEscrow(paymentId);
            }
            error = result.errorType();
        } catch (RuntimeException e) {
            result = EscrowResult.failed(null);
            error = e.getClass().getSimpleName();
        }
        EscrowResult outcome = result;
        String lastError = error == null ? outcome.outcome().name() : error;
        tx.executeWithoutResult(status -> record(job, shipment, outcome, lastError));
        return outcome.outcome() != EscrowResult.Outcome.FAILED;
    }

    private void record(EscrowJobRow job, ShipmentRow shipment, EscrowResult result, String error) {
        Instant now = now();
        switch (result.outcome()) {
            case DONE -> {
                store.stampEscrow(shipment.id(), job.kind(), true, now);
                store.finishEscrow(job.id(), "DONE", null, now);
            }
            case NOT_ESCROW -> {
                store.stampEscrow(shipment.id(), job.kind(), false, now);
                store.finishEscrow(job.id(), "SKIPPED", null, now);
            }
            case FAILED -> {
                String masked = error.length() > 200 ? error.substring(0, 200) : error;
                if (job.attempts() > BACKOFF.size()) {
                    store.finishEscrow(job.id(), "GAVE_UP", masked, now);
                    log.error("에스크로 호출을 그만뒀다 jobId={} shipmentId={} kind={} error={}", job.id(), shipment.id(), job.kind(), masked);
                    events.publishEvent(new GroupBuyEvents.EscrowGaveUp(shipment.id(), shipment.participationId(), job.kind()));
                } else {
                    store.retryEscrow(job.id(), masked, now.plus(BACKOFF.get(job.attempts() - 1)));
                }
            }
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
