package com.community.notification.service;

import com.community.batch.core.WorkerHeartbeats;
import com.community.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배치 실패·종료 알림 감시자 — {@code roboyam.notification.batch-watch-seconds}(기본 30초)마다 {@link BatchRunNotifier} 를 돌려 새로 끝난 배치 실행 줄을 알림으로 바꾼다.
 * 배치는 요청 밖에서(Java 스케줄러·pg_cron) 끝나고 DB 프로시저가 알림 수신자를 계산하게 하지 않기 위해 Java 감시자가 한다. 여러 서버가 동시에 돌려도 줄을
 * {@code FOR UPDATE SKIP LOCKED} 로 나눠 잡으므로 알림이 중복되지 않는다. 한 번에 {@value #CHUNK} 줄씩 처리하고 더 남았으면 이어서 처리한다.
 *
 * <p>스프링의 {@code @Scheduled} 스케줄러에서 돈다 — 관리자 서버는 배치 요청 집기 워커({@code BatchRequestPoller})도 같은 스케줄러를 쓰므로 스레드를 여럿 둔다
 * ({@code spring.task.scheduling.pool.size}, application.yml). 오류는 삼키고 다음 주기에 다시 한다. 한 바퀴마다 워커 기록({@code worker_heartbeats}, 키 {@value #WORKER_KEY})을
 * 남긴다(일이 없으면 간격마다 한 번만 — docs/domain/commerce.md 15-4).
 */
@Component
public class BatchRunWatcher {

    private static final Logger log = LoggerFactory.getLogger(BatchRunWatcher.class);

    static final int CHUNK = 50;

    public static final String WORKER_KEY = "batch-run-watcher";

    /** 한 주기에 이어서 처리하는 최대 묶음 수 — 한 주기가 끝없이 길어지지 않게 */
    private static final int MAX_CHUNKS_PER_RUN = 20;

    private final BatchRunNotifier notifier;
    private final NotificationProperties properties;
    private final WorkerHeartbeats heartbeats;

    public BatchRunWatcher(BatchRunNotifier notifier, NotificationProperties properties, WorkerHeartbeats heartbeats) {
        this.notifier = notifier;
        this.properties = properties;
        this.heartbeats = heartbeats;
    }

    @Scheduled(initialDelayString = "${roboyam.notification.batch-watch-seconds:30}", fixedDelayString = "${roboyam.notification.batch-watch-seconds:30}",
            timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void watch() {
        if (!properties.watcherEnabled()) {
            return;
        }
        long handled = 0;
        try {
            for (int i = 0; i < MAX_CHUNKS_PER_RUN; i++) {
                int done = notifier.notifyPending(CHUNK);
                handled += done;
                if (done < CHUNK) {
                    break;
                }
            }
            heartbeats.success(WORKER_KEY, handled);
        } catch (RuntimeException e) {
            // 데이터베이스가 아직 준비되지 않았거나 잠시 끊긴 경우 — 다음 주기에 다시 한다
            log.error("배치 알림 감시에 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(WORKER_KEY, e);
        }
    }
}
