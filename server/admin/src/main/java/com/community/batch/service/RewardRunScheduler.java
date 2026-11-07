package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.rewardevent.service.RewardRunWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보상 이벤트 지급 실행(일괄 대량 · 실패만 다시) 이어 하기 워커의 「언제 돌릴지」(관리자 서버 쪽, docs/domain/commerce.md 15-4) — {@code roboyam.reward-event.run-poll-millis}(기본 5초)마다
 * 한 바퀴. 한 바퀴의 일은 shared 의 {@link RewardRunWorker} 가 한다(실행 행 {@code SKIP LOCKED} — 서버 여러 대여도 한 실행은 한 곳만).
 * {@code roboyam.batch.enabled=false} 인 서버는 돌지 않는다. 바퀴마다 워커 기록({@code worker_heartbeats}, 키 {@value RewardRunWorker#WORKER_KEY}).
 */
@Component
public class RewardRunScheduler {

    private static final Logger log = LoggerFactory.getLogger(RewardRunScheduler.class);

    private final BatchProperties properties;
    private final RewardRunWorker worker;
    private final WorkerHeartbeats heartbeats;

    public RewardRunScheduler(BatchProperties properties, RewardRunWorker worker, WorkerHeartbeats heartbeats) {
        this.properties = properties;
        this.worker = worker;
        this.heartbeats = heartbeats;
    }

    /** @return 이번 바퀴에 처리한 덩어리 수(배치를 끈 서버면 -1) */
    @Scheduled(initialDelayString = "${roboyam.reward-event.run-poll-millis:5000}", fixedDelayString = "${roboyam.reward-event.run-poll-millis:5000}")
    public int tick() {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            int handled = worker.runOnce();
            heartbeats.success(RewardRunWorker.WORKER_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("보상 지급 실행 워커 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(RewardRunWorker.WORKER_KEY, e);
            return 0;
        }
    }
}
