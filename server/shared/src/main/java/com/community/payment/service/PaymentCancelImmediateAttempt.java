package com.community.payment.service;

import com.community.payment.dto.PaymentCancelRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 취소를 넣은 트랜잭션이 <b>커밋된 뒤</b> 그 자리에서 한 번 시도한다(D10 「즉시 시도」, 계약 「확정 흐름」 4). 실패해도 업무 응답을 깨지 않는다 — 줄은 PENDING 으로 남고 1분 워커가 이어 받는다.
 * 실행기는 늘 새 트랜잭션을 연다(커밋 뒤 단계에서는 끝난 트랜잭션에 참여하면 저장되지 않는다).
 */
@Component
public class PaymentCancelImmediateAttempt {

    private static final Logger log = LoggerFactory.getLogger(PaymentCancelImmediateAttempt.class);

    private final PaymentCancelExecutor executor;

    public PaymentCancelImmediateAttempt(PaymentCancelExecutor executor) {
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(PaymentCancelRequested event) {
        try {
            executor.attemptNow(event.cancelId());
        } catch (RuntimeException e) {
            log.warn("취소 즉시 시도가 실패했다 — 워커가 이어 받는다 cancelId={} 원인={}", event.cancelId(), e.getClass().getSimpleName());
        }
    }
}
