package com.community.batch.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 설정 변경이 커밋된 뒤에 예약을 다시 맞춘다 — 커밋 전에 읽으면 바뀐 값을 못 본다 */
@Component
public class BatchSettingsChangeListener {

    private final BatchScheduler scheduler;

    public BatchSettingsChangeListener(BatchScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(BatchSettingsChanged event) {
        scheduler.reconcile();
    }
}
