package com.community.batch.service;

import com.community.batch.catalog.BatchCatalog;
import com.community.batch.entity.BatchJobEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchTriggerType;
import com.community.batch.job.BatchResult;
import com.community.batch.repository.master.BatchJobRepository;
import com.community.batch.repository.master.BatchRunRepositoryQDSL;
import com.community.batch.service.BatchLockService.BatchLock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 배치 한 번을 <b>잠금 → 실행 로그 → 일 → 결과 기록</b> 순서로 돌린다. 예약 실행({@link #runScheduled})과 수동 실행({@link #runManual})이 같은 길을 쓴다.
 *
 * <ul>
 *   <li><b>한 곳에서만</b>: 배치마다 DB 어드바이저리 잠금을 잡는다. 이미 누가 쥐고 있으면(다른 서버든 이 서버든) 돌지 않는다({@link Outcome#ALREADY_RUNNING}). 예약 실행은 추가로
 *       예약 시각(슬롯)마다 로그 한 줄만 들어가게 유일 색인이 막는다 — 두 서버가 같은 시각에 깨어나도 한 번만 돈다({@link Outcome#NOT_DUE}).</li>
 *   <li><b>예약 실행이 꺼진 배치를 만나면</b> 아무것도 남기지 않고 돌아선다({@link Outcome#NOT_DUE}) — 다른 서버가 끈 직후의 마지막 예약일 수 있다. 수동 실행은 꺼져 있어도 돈다.</li>
 *   <li><b>결과</b>: 일이 정상으로 끝나면 처리 건수가 0 일 때 SKIP(건너뜀), 아니면 OK. 예외를 던지면 FAIL 이고 사유는 민감값을 가린 한 줄로 남기며(전체 스택트레이스는 서버 로그에만) 다음 예약은 그대로 돈다.</li>
 *   <li><b>죽은 실행</b>: 잠금을 쥔 쪽은 지금 그 배치를 돌리는 서버가 없다는 뜻이라, 남아 있는 「실행 중」 줄은 서버가 죽어 끝맺지 못한 것이다 — 시작하기 전에 실패로 닫는다.</li>
 * </ul>
 * 이 클래스는 트랜잭션을 열지 않는다 — 일은 {@code BatchJob} 이 부르는 서비스가 제 트랜잭션으로 하고, 로그는 {@link BatchRunRecorder} 가 따로 쓴다.
 */
@Service
public class BatchRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    /** 실행 시도의 결과 */
    public enum Outcome {
        /** 돌았다(성공·건너뜀·실패 모두 — 상태는 {@link RunReport#status()}) */
        FINISHED,
        /** 같은 배치가 이미 돌고 있어 돌지 않았다 */
        ALREADY_RUNNING,
        /** 돌 차례가 아니라 아무것도 하지 않았다 — 꺼졌거나 다른 서버가 같은 예약 시각을 먼저 맡았다 */
        NOT_DUE
    }

    /** 실행 시도의 보고. 돌았을 때만 {@code runId}·{@code status} 가 있다 */
    public record RunReport(Outcome outcome, Long runId, BatchRunStatus status) {

        static RunReport of(Outcome outcome) {
            return new RunReport(outcome, null, null);
        }
    }

    private final BatchLockService locks;
    private final BatchRunRecorder recorder;
    private final BatchRegistry registry;
    private final BatchJobRepository jobs;
    private final BatchRunRepositoryQDSL runQueries;

    public BatchRunner(BatchLockService locks, BatchRunRecorder recorder, BatchRegistry registry, BatchJobRepository jobs, BatchRunRepositoryQDSL runQueries) {
        this.locks = locks;
        this.recorder = recorder;
        this.registry = registry;
        this.jobs = jobs;
        this.runQueries = runQueries;
    }

    /** 예약 시각({@code slot})이 된 배치를 돌린다. 꺼져 있거나 다른 서버가 먼저 맡았으면 아무것도 남기지 않는다 */
    public RunReport runScheduled(BatchCatalog definition, Instant slot) {
        return execute(definition, BatchTriggerType.SCHEDULED, slot, null);
    }

    /** 운영자가 「지금 실행」을 눌렀다. 꺼져 있어도 돈다. 이미 돌고 있으면 {@link Outcome#ALREADY_RUNNING} */
    public RunReport runManual(BatchCatalog definition, long operatorId) {
        return execute(definition, BatchTriggerType.MANUAL, null, operatorId);
    }

    /**
     * 「실행 중」으로 남은 줄이 있는 배치를 찾아, 지금 아무도 돌리지 않으면(잠금을 잡을 수 있으면) 실패로 닫는다. 서버가 뜰 때와 주기적으로 부른다.
     * 다른 서버가 지금 정말로 돌리고 있는 배치는 잠금을 잡을 수 없으므로 건드리지 않는다.
     */
    public void sweepInterrupted() {
        for (String jobKey : runQueries.runningJobKeys()) {
            Optional<BatchLock> lock = locks.tryAcquire(jobKey);
            if (lock.isEmpty()) {
                continue;
            }
            try (BatchLock held = lock.get()) {
                long closed = recorder.failInterrupted(jobKey);
                if (closed > 0) {
                    log.warn("끝맺지 못한 배치 실행 {}건을 실패로 닫았다 jobKey={}", closed, jobKey);
                }
            }
        }
    }

    private RunReport execute(BatchCatalog definition, BatchTriggerType trigger, Instant slot, Long operatorId) {
        String jobKey = definition.key();
        Optional<BatchLock> lock = locks.tryAcquire(jobKey);
        if (lock.isEmpty()) {
            log.debug("배치가 이미 돌고 있어 건너뛴다 jobKey={} trigger={}", jobKey, trigger);
            return RunReport.of(Outcome.ALREADY_RUNNING);
        }
        try (BatchLock held = lock.get()) {
            if (trigger == BatchTriggerType.SCHEDULED && !jobs.findById(jobKey).map(BatchJobEntity::isEnabled).orElse(false)) {
                log.debug("꺼진 배치의 예약을 건너뛴다 jobKey={}", jobKey);
                return RunReport.of(Outcome.NOT_DUE);
            }
            recorder.failInterrupted(jobKey);

            BatchRunRecorder.Started started;
            try {
                started = recorder.start(jobKey, trigger, slot, operatorId);
            } catch (DataIntegrityViolationException e) {
                // 같은 예약 시각의 줄이 이미 있거나(다른 서버가 먼저 맡았다) 실행 중 줄이 남아 있다 — 두 번 돌지 않는다
                log.info("이미 맡은 실행이라 건너뛴다 jobKey={} trigger={} slot={}", jobKey, trigger, slot);
                return RunReport.of(trigger == BatchTriggerType.SCHEDULED ? Outcome.NOT_DUE : Outcome.ALREADY_RUNNING);
            }
            return runAndRecord(definition, started);
        }
    }

    private RunReport runAndRecord(BatchCatalog definition, BatchRunRecorder.Started started) {
        String jobKey = definition.key();
        BatchResult result = null;
        Throwable failure = null;
        try {
            result = registry.jobOf(definition).execute();
        } catch (Throwable error) { // 일이 무엇을 던져도 실행 로그를 닫는다(Error 는 아래에서 다시 던진다)
            failure = error;
            log.error("배치 실행 중 오류 jobKey={}", jobKey, error);
        }

        BatchRunStatus status;
        try {
            if (failure != null) {
                status = BatchRunStatus.FAIL;
                recorder.finish(started, jobKey, status, null, null, BatchErrorText.of(failure));
            } else {
                status = result.affected() == 0 ? BatchRunStatus.SKIP : BatchRunStatus.OK;
                recorder.finish(started, jobKey, status, result.affected(), result.message(), null);
            }
        } catch (RuntimeException e) {
            // 결과를 못 적었다 — 줄은 「실행 중」으로 남고 다음 실행 전에 닫힌다
            log.error("배치 실행 결과를 기록하지 못했다 jobKey={}", jobKey, e);
            throw e;
        }
        log.info("배치 끝 jobKey={} status={} affected={} millis={}", jobKey, status, result == null ? null : result.affected(),
                Duration.between(started.startedAt(), Instant.now()).toMillis());
        if (failure instanceof Error error) {
            throw error;
        }
        return new RunReport(Outcome.FINISHED, started.runId(), status);
    }
}
