package com.community.notification.service;

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
 * <p>스프링의 {@code @Scheduled} 기본 스케줄러(단일 스레드)에서 돈다 — 이 서버의 다른 {@code @Scheduled} 작업이 없다. 오류는 삼키고 다음 주기에 다시 한다.
 */
@Component
public class BatchRunWatcher {

    private static final Logger log = LoggerFactory.getLogger(BatchRunWatcher.class);

    static final int CHUNK = 50;

    /** 한 주기에 이어서 처리하는 최대 묶음 수 — 한 주기가 끝없이 길어지지 않게 */
    private static final int MAX_CHUNKS_PER_RUN = 20;

    private final BatchRunNotifier notifier;
    private final NotificationProperties properties;

    public BatchRunWatcher(BatchRunNotifier notifier, NotificationProperties properties) {
        this.notifier = notifier;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${roboyam.notification.batch-watch-seconds:30}", fixedDelayString = "${roboyam.notification.batch-watch-seconds:30}",
            timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void watch() {
        if (!properties.watcherEnabled()) {
            return;
        }
        try {
            for (int i = 0; i < MAX_CHUNKS_PER_RUN; i++) {
                if (notifier.notifyPending(CHUNK) < CHUNK) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            // 데이터베이스가 아직 준비되지 않았거나 잠시 끊긴 경우 — 다음 주기에 다시 한다
            log.error("배치 알림 감시에 실패했다 원인={}", e.getClass().getSimpleName());
        }
    }
}
