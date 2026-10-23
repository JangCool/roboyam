package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.BatchRequestExecutor;
import com.community.batch.core.BatchRequestQueue.ClaimedRequest;
import com.community.batch.core.WorkerHeartbeats;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 실행 요청 집기 워커(「언제 돌릴지」 — 관리자 서버 쪽). {@code roboyam.batch.request-poll-millis}(기본 2초)마다 대기 요청을 집어({@code FOR UPDATE SKIP LOCKED} — 서버 여러 대가
 * 같은 요청을 두 번 집지 않는다) 실행 스레드에 넘긴다. 실행·결과 기록은 core 의 {@link BatchRequestExecutor} 가 한다 — 배치를 별도 서버로 뗄 때 이 클래스만 옮기면 된다(15-2).
 *
 * <ul>
 *   <li>{@code roboyam.batch.enabled=false} 인 서버는 집지 않는다(화면의 요청은 배치를 켠 다른 서버가 집는다). 서버가 뜬 뒤({@link #start}) 부터 집는다.</li>
 *   <li>동시에 {@code roboyam.batch.pool-size} 개까지 실행한다 — 빈 자리가 있을 때만 집는다(긴 배치가 다른 배치의 요청을 막지 않게, 그리고 못 돌릴 요청을 미리 집어 두지 않게).</li>
 *   <li>한 바퀴마다 워커 기록({@code worker_heartbeats}, 키 {@value #WORKER_KEY})을 남긴다 — 일이 없으면 간격마다 한 번만.</li>
 * </ul>
 */
@Component
public class BatchRequestPoller {

    private static final Logger log = LoggerFactory.getLogger(BatchRequestPoller.class);

    public static final String WORKER_KEY = "batch-request-poller";

    private final BatchProperties properties;
    private final BatchRequestExecutor executor;
    private final WorkerHeartbeats heartbeats;
    private final Semaphore slots;
    private final ThreadPoolTaskExecutor threads;
    private volatile boolean started;

    public BatchRequestPoller(BatchProperties properties, BatchRequestExecutor executor, WorkerHeartbeats heartbeats) {
        this.properties = properties;
        this.executor = executor;
        this.heartbeats = heartbeats;
        int size = Math.max(1, properties.poolSize());
        this.slots = new Semaphore(size);
        this.threads = new ThreadPoolTaskExecutor();
        threads.setCorePoolSize(size);
        threads.setMaxPoolSize(size);
        threads.setQueueCapacity(size);
        threads.setThreadNamePrefix("batch-request-");
        threads.setWaitForTasksToCompleteOnShutdown(true);
        threads.setAwaitTerminationSeconds(25);
    }

    /** 서버가 뜬 뒤 한 번 — 이때부터 집는다(카탈로그 맞추기·죽은 실행 닫기가 먼저 끝나게). 배치를 끈 서버는 아무것도 하지 않는다 */
    public synchronized void start() {
        if (started || !properties.enabled()) {
            return;
        }
        threads.initialize();
        started = true;
        log.info("배치 실행 요청 집기를 시작했다 poll={}ms threads={}", properties.requestPollMillis(), properties.poolSize());
    }

    public boolean isStarted() {
        return started;
    }

    /**
     * 한 바퀴 — 빈 자리만큼 대기 요청을 집어 실행 스레드에 넘긴다.
     *
     * @return 이번 바퀴에 집은 요청 수(배치를 끈 서버·시작 전이면 0 이고 아무것도 읽지 않는다)
     */
    @Scheduled(initialDelayString = "${roboyam.batch.request-poll-millis:2000}", fixedDelayString = "${roboyam.batch.request-poll-millis:2000}")
    public int poll() {
        if (!properties.enabled() || !started) {
            return 0;
        }
        int claimed = 0;
        try {
            while (slots.tryAcquire()) {
                Optional<ClaimedRequest> request;
                try {
                    request = executor.claimNext();
                } catch (RuntimeException e) {
                    slots.release();
                    throw e;
                }
                if (request.isEmpty()) {
                    slots.release();
                    break;
                }
                claimed++;
                submit(request.get());
            }
            heartbeats.success(WORKER_KEY, claimed);
        } catch (RuntimeException e) {
            // 데이터베이스가 잠시 끊긴 경우 등 — 다음 바퀴에 다시 한다. 이미 집은 요청은 실행 스레드가 끝까지 맡는다
            log.error("배치 실행 요청을 집지 못했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(WORKER_KEY, e);
        }
        return claimed;
    }

    private void submit(ClaimedRequest request) {
        try {
            threads.execute(() -> {
                try {
                    executor.execute(request);
                } finally {
                    slots.release();
                }
            });
        } catch (RuntimeException e) {
            slots.release();
            log.error("배치 요청을 실행 스레드에 넘기지 못했다 — 남은 요청 정리가 닫는다 requestId={}", request.id(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (started) {
            threads.shutdown();
        }
    }
}
