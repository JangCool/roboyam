package com.community.batch.service;

import com.community.batch.catalog.BatchCatalog;
import com.community.batch.config.BatchProperties;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.repository.master.BatchJobRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * 예약 실행. 사용 여부·cron 은 DB({@code batch_jobs})가 원천이라 <b>재기동 없이</b> 바뀐다 — 이 서버에서 바꾸면 곧바로({@link #reconcile}), 다른 서버에서 바꾸면
 * {@code roboyam.batch.refresh-seconds}(기본 30초) 안에 따라온다.
 *
 * <ul>
 *   <li><b>스스로 다음 시각을 계산한다</b>: 켜진 배치마다 다음 실행 시각(slot)을 cron 으로 계산해 그 시각에 한 번 예약하고, 끝나면 다음 시각을 다시 계산한다. slot 은 초 단위로 정해지므로
 *       서버가 여러 대여도 같은 값이 되고, 실행기가 slot 별로 한 번만 돌게 막는다({@link BatchRunner}).</li>
 *   <li><b>밀린 실행을 되살리지 않는다</b>: 서버가 내려가 있던 동안의 시각은 건너뛴다(다음 시각부터). 일이 다음 시각을 넘겨 길어져도 겹쳐 돌지 않는다(잠금).</li>
 *   <li>시간대는 {@code roboyam.batch.zone}(Asia/Seoul)이다.</li>
 *   <li>스프링의 {@code @Scheduled} 전용 스케줄러와 섞이지 않게 이 클래스가 자기 스케줄러를 쥔다(빈으로 노출하지 않는다).</li>
 * </ul>
 */
@Component
public class BatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduler.class);

    /** 예약해 둔 다음 실행 하나 */
    private static final class Slot {
        private final BatchCatalog definition;
        private final String cron;
        private final Instant at;
        private ScheduledFuture<?> future;

        private Slot(BatchCatalog definition, String cron, Instant at) {
            this.definition = definition;
            this.cron = cron;
            this.at = at;
        }
    }

    private final BatchProperties properties;
    private final BatchJobRepository jobs;
    private final BatchRunner runner;
    private final ThreadPoolTaskScheduler scheduler;
    private final Map<String, Slot> slots = new HashMap<>();
    private boolean started;

    public BatchScheduler(BatchProperties properties, BatchJobRepository jobs, BatchRunner runner) {
        this.properties = properties;
        this.jobs = jobs;
        this.runner = runner;
        this.scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.poolSize());
        scheduler.setThreadNamePrefix("batch-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(25);
        scheduler.setErrorHandler(error -> log.error("배치 스케줄러 오류", error));
    }

    /** 서버가 뜬 뒤 한 번 부른다 — 예약을 맞추고, 주기적으로 DB 를 다시 읽는다. 예약 실행을 끈 서버({@code scheduler-enabled=false})는 아무것도 하지 않는다 */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        if (!properties.schedulerEnabled()) {
            log.info("이 서버는 배치 예약 실행을 하지 않는다(roboyam.batch.scheduler-enabled=false) — 「지금 실행」만 된다");
            return;
        }
        scheduler.initialize();
        reconcile();
        scheduler.scheduleWithFixedDelay(this::poll, Instant.now().plusSeconds(properties.refreshSeconds()), Duration.ofSeconds(properties.refreshSeconds()));
        log.info("배치 스케줄러를 시작했다 zone={} refresh={}초 threads={}", properties.zone(), properties.refreshSeconds(), properties.poolSize());
    }

    /** 설정이 바뀐 직후 부른다 — DB 의 사용 여부·cron 에 예약을 다시 맞춘다 */
    public synchronized void reconcile() {
        if (!started || !properties.schedulerEnabled()) {
            return;
        }
        Map<String, BatchJobEntity> settings = new HashMap<>();
        jobs.findAll().forEach(job -> settings.put(job.getJobKey(), job));

        for (BatchCatalog definition : BatchCatalog.javaJobs()) { // DB 배치는 pg_cron 이 돌린다 — 여기서 예약하지 않는다
            BatchJobEntity setting = settings.get(definition.key());
            Slot current = slots.get(definition.key());
            boolean wanted = setting != null && setting.isEnabled();
            if (!wanted) {
                if (current != null) {
                    cancel(current);
                    slots.remove(definition.key());
                    log.info("배치 예약을 풀었다 jobKey={}", definition.key());
                }
            } else if (current == null || !current.cron.equals(setting.getCron())) {
                if (current != null) {
                    cancel(current);
                }
                scheduleNext(definition, setting.getCron(), ZonedDateTime.now(properties.zoneId()));
            }
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }

    /** 주기적으로: 다른 서버가 바꾼 설정을 따라오고, 죽은 서버가 남긴 「실행 중」 줄을 닫는다 */
    private void poll() {
        try {
            reconcile();
            runner.sweepInterrupted();
        } catch (RuntimeException e) {
            log.error("배치 설정을 다시 읽지 못했다", e);
        }
    }

    private void scheduleNext(BatchCatalog definition, String cron, ZonedDateTime from) {
        ZonedDateTime next;
        try {
            next = CronExpression.parse(cron).next(from);
        } catch (IllegalArgumentException e) {
            log.error("배치 cron 식을 해석하지 못해 예약하지 않는다 jobKey={}", definition.key()); // 저장 때 검증하므로 DB 를 직접 고친 경우뿐이다
            slots.remove(definition.key());
            return;
        }
        if (next == null) {
            log.warn("배치의 다음 실행 시각이 없어 예약하지 않는다 jobKey={}", definition.key());
            slots.remove(definition.key());
            return;
        }
        Slot slot = new Slot(definition, cron, next.toInstant());
        slot.future = scheduler.schedule(() -> fire(slot), slot.at);
        slots.put(definition.key(), slot);
        log.debug("배치 예약 jobKey={} next={}", definition.key(), next);
    }

    private void fire(Slot slot) {
        try {
            runner.runScheduled(slot.definition, slot.at);
        } catch (RuntimeException e) {
            log.error("배치 예약 실행 중 오류 jobKey={}", slot.definition.key(), e);
        } finally {
            synchronized (this) {
                // 그 사이 설정이 바뀌어 이 예약이 밀려났다면 새 예약이 이미 있다
                if (slots.get(slot.definition.key()) == slot) {
                    ZonedDateTime now = ZonedDateTime.now(properties.zoneId());
                    ZonedDateTime slotTime = slot.at.atZone(properties.zoneId());
                    scheduleNext(slot.definition, slot.cron, slotTime.isAfter(now) ? slotTime : now);
                }
            }
        }
    }

    private static void cancel(Slot slot) {
        if (slot.future != null) {
            slot.future.cancel(false);
        }
    }
}
