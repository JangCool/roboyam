package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.mail.outbox.service.MailOutboxWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 커머스 메일 대기열 발송 워커의 「언제 돌릴지」(관리자 서버 쪽, docs/domain/commerce.md 15-4) — {@code roboyam.mail-outbox.poll-millis}(기본 30초)마다 한 바퀴.
 * 한 바퀴의 일(집기·보내기·재시도)은 shared 의 {@link MailOutboxWorker} 가 한다 — 배치를 별도 서버로 뗄 때 이 클래스만 옮기면 된다(15-2).
 *
 * <ul>
 *   <li>{@code roboyam.batch.enabled=false} 인 서버는 돌지 않는다(결정 17 — 관리자 서버가 꺼지면 메일이 늦어질 뿐 사라지지 않는다).</li>
 *   <li>바퀴마다 워커 기록({@code worker_heartbeats}, 키 {@value MailOutboxWorker#WORKER_KEY}) — 일이 없으면 간격마다 한 번만, 실패하면 가린 오류 한 줄.</li>
 * </ul>
 */
@Component
public class MailOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxScheduler.class);

    private final BatchProperties properties;
    private final MailOutboxWorker worker;
    private final WorkerHeartbeats heartbeats;

    public MailOutboxScheduler(BatchProperties properties, MailOutboxWorker worker, WorkerHeartbeats heartbeats) {
        this.properties = properties;
        this.worker = worker;
        this.heartbeats = heartbeats;
    }

    /** @return 이번 바퀴에 다룬 줄 수(배치를 끈 서버면 아무것도 읽지 않고 -1) */
    @Scheduled(initialDelayString = "${roboyam.mail-outbox.poll-millis:30000}", fixedDelayString = "${roboyam.mail-outbox.poll-millis:30000}")
    public int tick() {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            int handled = worker.runOnce();
            heartbeats.success(MailOutboxWorker.WORKER_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("메일 대기열 워커 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(MailOutboxWorker.WORKER_KEY, e);
            return 0;
        }
    }
}
