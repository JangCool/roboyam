package com.community.rewardevent.service;

import com.community.rewardevent.repository.master.RewardEventStore;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 나눠 처리 중인 보상 지급 실행을 이어 하는 짧은 주기 워커의 한 바퀴(docs/domain/commerce.md 15-4 — 「언제 돌릴지」는 관리자 서버의 {@code RewardRunScheduler}).
 * 실행마다 덩어리를 {@value #CHUNKS_PER_TICK}개까지 처리한다. 덩어리마다 실행 행을 {@code SKIP LOCKED} 로 잡으므로 서버 여러 대·요청 안 처리와 겹쳐도 한 실행은 한 곳에서만 돈다.
 */
@Component
public class RewardRunWorker {

    public static final String WORKER_KEY = "reward-event-runs";
    static final int RUNS_PER_TICK = 5;
    static final int CHUNKS_PER_TICK = 20;

    private final RewardEventStore events;
    private final RewardGrantService grants;
    private final TransactionTemplate tx;

    public RewardRunWorker(RewardEventStore events, RewardGrantService grants, PlatformTransactionManager transactions) {
        this.events = events;
        this.grants = grants;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setReadOnly(true);
    }

    /** @return 이번 바퀴에 처리한 덩어리 수 */
    public int runOnce() {
        List<Long> ids = tx.execute(status -> events.runningIds(RUNS_PER_TICK));
        int done = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            done += grants.drain(id, CHUNKS_PER_TICK);
        }
        return done;
    }
}
