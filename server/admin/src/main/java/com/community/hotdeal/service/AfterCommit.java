package com.community.hotdeal.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 커밋된 뒤에만 사본을 버린다(롤백되면 그대로 — 포인트 정책 저장과 같은 방식). 트랜잭션 밖이면 바로 */
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
