package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.payment.service.PaymentCancelWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PG 취소 재시도 워커의 「언제 돌릴지」(관리자 서버 쪽, docs/domain/commerce.md 15-4·15-5 「PG 취소 재시도 — 1분」, docs/features/commerce-payment-contract.md) —
 * {@code roboyam.payment.cancel-poll-millis}(기본 1분)마다 한 바퀴. 한 바퀴의 일(되살리기·집기·보내기·재시도)은 shared 의 {@link PaymentCancelWorker} 가 한다 —
 * 배치를 별도 서버로 뗄 때 이 클래스만 옮기면 된다(15-2). {@code MailOutboxScheduler} 와 같은 모양이다.
 *
 * <ul>
 *   <li>{@code roboyam.batch.enabled=false} 인 서버는 돌지 않는다(관리자 서버가 꺼지면 재시도가 늦어질 뿐 대기열에 남는다 — 즉시 시도는 확정한 서버가 한다).</li>
 *   <li>바퀴마다 워커 기록({@code worker_heartbeats}, 키 {@value PaymentCancelWorker#WORKER_KEY}) — 일이 없으면 간격마다 한 번만, 실패하면 가린 오류 한 줄.</li>
 * </ul>
 */
@Component
public class PaymentCancelScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCancelScheduler.class);

    private final BatchProperties properties;
    private final PaymentCancelWorker worker;
    private final WorkerHeartbeats heartbeats;

    public PaymentCancelScheduler(BatchProperties properties, PaymentCancelWorker worker, WorkerHeartbeats heartbeats) {
        this.properties = properties;
        this.worker = worker;
        this.heartbeats = heartbeats;
    }

    /** @return 이번 바퀴에 다룬 취소 수(배치를 끈 서버면 아무것도 읽지 않고 -1) */
    @Scheduled(initialDelayString = "${roboyam.payment.cancel-poll-millis:60000}", fixedDelayString = "${roboyam.payment.cancel-poll-millis:60000}")
    public int tick() {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            int handled = worker.runOnce();
            heartbeats.success(PaymentCancelWorker.WORKER_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("결제 취소 워커 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(PaymentCancelWorker.WORKER_KEY, e);
            return 0;
        }
    }
}
