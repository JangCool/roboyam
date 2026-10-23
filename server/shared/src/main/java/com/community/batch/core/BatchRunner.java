package com.community.batch.core;

import com.community.batch.core.BatchLockService.BatchLock;
import com.community.batch.core.BatchRunRecorder.SourceRun;
import com.community.batch.core.BatchRunRecorder.SourceStep;
import com.community.batch.core.BatchRunRecorder.StepRow;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunMode;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchStepStatus;
import com.community.batch.entity.BatchTriggerType;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Java 배치 한 번을 <b>잠금 → 실행 로그·단계 → 일 → 결과 기록</b> 순서로 돌린다. 예약 실행({@link #runScheduled})과 요청 실행({@link #runRequested} — 지금 실행·
 * 파라미터 지정 실행·실패 지점부터 다시·실패 건만 다시)이 같은 길을 쓴다.
 *
 * <ul>
 *   <li><b>한 곳에서만</b>: 배치마다 DB 어드바이저리 잠금을 잡는다. 이미 누가 쥐고 있으면(다른 서버든 이 서버든) 돌지 않는다({@link Outcome#ALREADY_RUNNING}). 예약 실행은 추가로
 *       예약 시각(슬롯)마다 로그 한 줄만 들어가게 유일 색인이 막는다 — 두 서버가 같은 시각에 깨어나도 한 번만 돈다({@link Outcome#NOT_DUE}).</li>
 *   <li><b>예약 실행이 꺼진 배치를 만나면</b> 아무것도 남기지 않고 돌아선다({@link Outcome#NOT_DUE}). 요청 실행은 꺼져 있어도 돈다.</li>
 *   <li><b>단계</b>: 시작할 때 작업의 단계를 모두 {@code batch_run_steps} 에 넣는다 — 고르지 않은 단계(고를 수 있는 단계만 해당)·이어 받은 실행에서 원래 실행이 끝낸 단계·
 *       다시 할 실패 건이 없는 단계는 SKIP. 고를 수 없는 단계는 늘 돈다(확정 규칙 2).</li>
 *   <li><b>실패 지점부터 다시</b>: 원래 실행이 실패로 끝났고 체크포인트가 있고 작업이 지원하고, 아직 이어 받은 실행이 없을 때만(확정 규칙 3 — 유일 색인으로도 막는다).
 *       원래 실행의 파라미터를 이어 쓰고, 끝난 단계는 SKIP, 실패한 단계는 그 체크포인트 다음부터. 아니면 {@link Outcome#REJECTED}.</li>
 *   <li><b>결과</b>: 건수는 단계가 센 값이다 — 읽음·처리·건너뜀 = 주 단계(주 단계가 돌지 않았으면 마지막으로 돈 단계, 「실패 건만 다시」는 돈 단계의 합), 실패 = 단계 합(확정 규칙 1).
 *       처리 건수는 {@code affected_count} 로도 남는다. 처리·실패가 모두 0 이면 SKIP, 아니면 OK. 일이 예외를 던지면 FAIL 이고 사유는 민감값을 가린 한 줄(전체 스택트레이스는 서버 로그에만),
 *       실패 한도를 넘으면 FAIL({@value BatchFailureLimitException#MESSAGE_KEY}).</li>
 *   <li><b>죽은 실행</b>: 잠금을 쥔 쪽은 지금 그 배치를 돌리는 서버가 없다는 뜻이라, 남아 있는 「실행 중」 줄은 서버가 죽어 끝맺지 못한 것이다 — 시작하기 전에 실패로 닫는다.</li>
 * </ul>
 * 이 클래스는 트랜잭션을 열지 않는다 — 일은 작업이(덩어리 처리 도우미가) 제 트랜잭션으로 하고, 로그는 {@link BatchRunRecorder} 가 따로 쓴다.
 */
@Service
public class BatchRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    /** 「실패 지점부터 다시」를 할 수 없다 */
    public static final String NOT_RESUMABLE_KEY = "batch.request.rejected.notResumable";

    /** 「실패 건만 다시」에서 다시 할 미해결 실패 건이 없다 */
    public static final String NO_FAILURES_KEY = "batch.request.rejected.noFailures";

    /** 실행 시도의 결과 */
    public enum Outcome {
        /** 돌았다(성공·건너뜀·실패 모두 — 상태는 {@link RunReport#status()}) */
        FINISHED,
        /** 같은 배치가 이미 돌고 있어 돌지 않았다 */
        ALREADY_RUNNING,
        /** 돌 차례가 아니라 아무것도 하지 않았다 — 꺼졌거나 다른 서버가 같은 예약 시각을 먼저 맡았다 */
        NOT_DUE,
        /** 요청을 받아들일 수 없어 돌지 않았다(이어 받을 수 없음·다시 할 실패 없음) — 사유는 {@link RunReport#rejectKey()} */
        REJECTED
    }

    /** 실행 시도의 보고. 돌았을 때만 {@code runId}·{@code status} 가 있다 */
    public record RunReport(Outcome outcome, Long runId, BatchRunStatus status, String rejectKey) {

        static RunReport of(Outcome outcome) {
            return new RunReport(outcome, null, null, null);
        }

        static RunReport rejected(String key) {
            return new RunReport(Outcome.REJECTED, null, null, key);
        }
    }

    /**
     * 요청 실행 하나.
     *
     * @param parameters  NOW 의 파라미터(이미 검사한 값). RESUME·RETRY_FAILED 는 원래 실행의 값을 쓰므로 무시한다
     * @param steps       NOW 에서 고른 단계(모두면 null)
     * @param sourceRunId RESUME·RETRY_FAILED 의 원래 실행
     * @param requestId   이 실행을 만든 요청(없으면 null)
     */
    public record RunRequest(BatchDefinition definition, BatchRequestKind kind, Long operatorId, BatchParameters parameters, List<String> steps, Long sourceRunId,
                             Long requestId) {
    }

    private final BatchLockService locks;
    private final BatchRunRecorder recorder;
    private final BatchFailureRecorder failures;
    private final BatchRegistry registry;
    private final TransactionTemplate requiresNew;
    private final TransactionTemplate readOnly;

    public BatchRunner(BatchLockService locks, BatchRunRecorder recorder, BatchFailureRecorder failures, BatchRegistry registry, PlatformTransactionManager transactions) {
        this.locks = locks;
        this.recorder = recorder;
        this.failures = failures;
        this.registry = registry;
        this.requiresNew = BatchJdbc.requiresNew(transactions);
        this.readOnly = BatchJdbc.readOnly(transactions);
    }

    /** 예약 시각({@code slot})이 된 배치를 돌린다. 꺼져 있거나 다른 서버가 먼저 맡았으면 아무것도 남기지 않는다 */
    public RunReport runScheduled(BatchDefinition definition, Instant slot) {
        return execute(definition, BatchTriggerType.SCHEDULED, slot, new RunRequest(definition, BatchRequestKind.NOW, null, BatchParameters.empty(), null, null, null));
    }

    /** 요청 표에서 집은 실행 — 꺼져 있어도 돈다. 이미 돌고 있으면 {@link Outcome#ALREADY_RUNNING} */
    public RunReport runRequested(RunRequest request) {
        return execute(request.definition(), BatchTriggerType.MANUAL, null, request);
    }

    /**
     * 「실행 중」으로 남은 줄이 있는 배치를 찾아, 지금 아무도 돌리지 않으면(잠금을 잡을 수 있으면) 실패로 닫는다. 서버가 뜰 때와 주기적으로 부른다.
     * 다른 서버가 지금 정말로 돌리고 있는 배치는 잠금을 잡을 수 없으므로 건드리지 않는다.
     */
    public void sweepInterrupted() {
        for (String jobKey : recorder.runningJobKeys()) {
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

    private RunReport execute(BatchDefinition definition, BatchTriggerType trigger, Instant slot, RunRequest request) {
        String jobKey = definition.key();
        BatchJob job = registry.jobOf(definition);
        if (job == null) {
            throw new IllegalArgumentException("Java 배치 구현이 없다: " + jobKey);
        }
        Optional<BatchLock> lock = locks.tryAcquire(jobKey);
        if (lock.isEmpty()) {
            log.debug("배치가 이미 돌고 있어 건너뛴다 jobKey={} trigger={}", jobKey, trigger);
            return RunReport.of(Outcome.ALREADY_RUNNING);
        }
        try (BatchLock held = lock.get()) {
            if (trigger == BatchTriggerType.SCHEDULED && !recorder.scheduleEnabled(jobKey)) {
                log.debug("꺼진 배치의 예약을 건너뛴다 jobKey={}", jobKey);
                return RunReport.of(Outcome.NOT_DUE);
            }
            recorder.failInterrupted(jobKey);

            Plan plan = plan(job, request);
            if (plan.rejectKey() != null) {
                log.info("배치 요청을 받아들이지 않았다 jobKey={} kind={} reason={}", jobKey, request.kind(), plan.rejectKey());
                return RunReport.rejected(plan.rejectKey());
            }

            BatchRunRecorder.Started started;
            try {
                started = recorder.start(new BatchRunRecorder.StartCommand(jobKey, trigger, slot, request.operatorId(), plan.mode(),
                        BatchMessageCodec.encodeParams(plan.parameters().asMap()), request.sourceRunId(), request.requestId(), plan.rows()));
            } catch (DataIntegrityViolationException e) {
                if (plan.mode() == BatchRunMode.RESUME && recorder.hasResume(request.sourceRunId())) {
                    return RunReport.rejected(NOT_RESUMABLE_KEY); // 그 사이 다른 곳이 먼저 이어 받았다(유일 색인)
                }
                // 같은 예약 시각의 줄이 이미 있거나(다른 서버가 먼저 맡았다) 실행 중 줄이 남아 있다 — 두 번 돌지 않는다
                log.info("이미 맡은 실행이라 건너뛴다 jobKey={} trigger={} slot={}", jobKey, trigger, slot);
                return RunReport.of(trigger == BatchTriggerType.SCHEDULED ? Outcome.NOT_DUE : Outcome.ALREADY_RUNNING);
            }
            return runAndRecord(job, plan, started, request);
        }
    }

    /** 이번 실행의 방식·파라미터·단계 계획. 받아들일 수 없으면 {@code rejectKey} */
    private record Plan(BatchRunMode mode, BatchParameters parameters, List<StepRow> rows, Map<String, String> startCheckpoints, Set<String> retrySteps,
                        String rejectKey) {

        static Plan rejected(String key) {
            return new Plan(null, null, null, null, null, key);
        }
    }

    private Plan plan(BatchJob job, RunRequest request) {
        List<BatchStepDefinition> definitions = job.steps();
        List<StepRow> rows = new ArrayList<>();
        Map<String, String> checkpoints = new java.util.HashMap<>();
        Set<String> retry = new HashSet<>();
        BatchRunMode mode = request.kind().runMode();
        switch (mode) {
            case NORMAL -> {
                Set<String> chosen = request.steps() == null ? null : new HashSet<>(request.steps());
                for (int i = 0; i < definitions.size(); i++) {
                    BatchStepDefinition step = definitions.get(i);
                    boolean skip = chosen != null && step.selectable() && !chosen.contains(step.name());
                    rows.add(new StepRow(step.name(), i + 1, skip ? BatchStepStatus.SKIP : BatchStepStatus.PENDING));
                }
                return new Plan(mode, request.parameters() == null ? BatchParameters.empty() : request.parameters(), rows, checkpoints, retry, null);
            }
            case RESUME -> {
                Optional<SourceRun> found = request.sourceRunId() == null ? Optional.empty() : recorder.sourceRun(request.sourceRunId());
                if (found.isEmpty()) {
                    return Plan.rejected(NOT_RESUMABLE_KEY);
                }
                SourceRun source = found.get();
                if (!source.jobKey().equals(job.definition().key()) || source.status() != BatchRunStatus.FAIL || source.checkpoint() == null || !job.resumable()
                        || recorder.hasResume(source.id())) {
                    return Plan.rejected(NOT_RESUMABLE_KEY);
                }
                for (int i = 0; i < definitions.size(); i++) {
                    BatchStepDefinition step = definitions.get(i);
                    SourceStep before = source.steps().get(step.name());
                    boolean done = before != null && (before.status() == BatchStepStatus.OK || before.status() == BatchStepStatus.SKIP);
                    rows.add(new StepRow(step.name(), i + 1, done ? BatchStepStatus.SKIP : BatchStepStatus.PENDING));
                    if (before != null && before.status() == BatchStepStatus.FAIL && before.checkpoint() != null) {
                        checkpoints.put(step.name(), before.checkpoint());
                    }
                }
                return new Plan(mode, BatchParameters.of(BatchMessageCodec.decodeParams(source.paramsJson())), rows, checkpoints, retry, null);
            }
            case RETRY_FAILED -> {
                Optional<SourceRun> found = request.sourceRunId() == null ? Optional.empty() : recorder.sourceRun(request.sourceRunId());
                if (found.isEmpty() || !found.get().jobKey().equals(job.definition().key())) {
                    return Plan.rejected(NO_FAILURES_KEY);
                }
                Map<String, Long> open = failures.unresolvedCountByStep(found.get().id());
                if (open.isEmpty()) {
                    return Plan.rejected(NO_FAILURES_KEY);
                }
                for (int i = 0; i < definitions.size(); i++) {
                    BatchStepDefinition step = definitions.get(i);
                    boolean has = open.getOrDefault(step.name(), 0L) > 0;
                    rows.add(new StepRow(step.name(), i + 1, has ? BatchStepStatus.PENDING : BatchStepStatus.SKIP));
                    if (has) {
                        retry.add(step.name());
                    }
                }
                return new Plan(mode, BatchParameters.of(BatchMessageCodec.decodeParams(found.get().paramsJson())), rows, checkpoints, retry, null);
            }
            default -> throw new IllegalStateException("모르는 실행 방식: " + mode);
        }
    }

    private RunReport runAndRecord(BatchJob job, Plan plan, BatchRunRecorder.Started started, RunRequest request) {
        String jobKey = job.definition().key();
        BatchContext context = new BatchContext(started.runId(), job.definition(), plan.mode(), plan.parameters(), request.sourceRunId(), recorder.settingsOf(jobKey),
                recorder, failures, requiresNew, readOnly);
        for (StepRow row : plan.rows()) {
            BatchStep.Plan stepPlan = row.status() == BatchStepStatus.SKIP ? BatchStep.Plan.SKIP
                    : plan.retrySteps().contains(row.name()) ? BatchStep.Plan.RETRY : BatchStep.Plan.RUN;
            context.plan(row.name(), row.order(), stepPlan, plan.startCheckpoints().get(row.name()));
        }

        BatchResult result = null;
        Throwable failure = null;
        try {
            result = job.run(context);
            if (result == null) {
                throw new IllegalStateException("배치가 결과 요약을 돌려주지 않았다: " + jobKey);
            }
        } catch (Throwable error) { // 일이 무엇을 던져도 실행 로그를 닫는다(Error 는 아래에서 다시 던진다)
            failure = error;
            if (error instanceof BatchFailureLimitException) {
                log.warn("배치 실패 건수가 한도를 넘어 멈췄다 jobKey={} limit={}", jobKey, context.failureLimit());
            } else {
                log.error("배치 실행 중 오류 jobKey={}", jobKey, error);
            }
        }

        BatchRunRecorder.Finish finish;
        try {
            context.complete(failure == null);
            finish = summarize(job, plan.mode(), context, result, failure);
            recorder.finish(started, jobKey, finish);
        } catch (RuntimeException e) {
            // 결과를 못 적었다 — 줄은 「실행 중」으로 남고 다음 실행 전에 닫힌다
            log.error("배치 실행 결과를 기록하지 못했다 jobKey={}", jobKey, e);
            throw e;
        }
        log.info("배치 끝 jobKey={} mode={} status={} processed={} failed={} millis={}", jobKey, plan.mode(), finish.status(), finish.processed(), finish.failed(),
                Duration.between(started.startedAt(), Instant.now()).toMillis());
        if (failure instanceof Error error) {
            throw error;
        }
        return new RunReport(Outcome.FINISHED, started.runId(), finish.status(), null);
    }

    /** 실행 줄의 건수·상태·요약(확정 규칙 1) */
    private static BatchRunRecorder.Finish summarize(BatchJob job, BatchRunMode mode, BatchContext context, BatchResult result, Throwable failure) {
        List<BatchStep> ran = context.steps().stream().filter(step -> step.status() == BatchStepStatus.OK || step.status() == BatchStepStatus.FAIL).toList();
        BatchStep failedStep = ran.stream().filter(step -> step.status() == BatchStepStatus.FAIL).findFirst().orElse(null);
        BatchStep main = ran.stream().filter(step -> step.name().equals(job.mainStep())).findFirst().orElse(ran.isEmpty() ? null : ran.get(ran.size() - 1));
        long failed = context.steps().stream().mapToLong(BatchStep::failedCount).sum();
        long read;
        long processed;
        long skipped;
        if (mode == BatchRunMode.RETRY_FAILED) {
            read = ran.stream().mapToLong(BatchStep::readCount).sum();
            processed = ran.stream().mapToLong(BatchStep::processedCount).sum();
            skipped = ran.stream().mapToLong(BatchStep::skippedCount).sum();
        } else {
            read = main == null ? 0 : main.readCount();
            processed = main == null ? 0 : main.processedCount();
            skipped = main == null ? 0 : main.skippedCount();
        }
        BatchStep checkpointStep = failedStep != null ? failedStep : main;
        String checkpoint = checkpointStep == null ? null : checkpointStep.checkpoint();
        if (failure instanceof BatchFailureLimitException limit) {
            return new BatchRunRecorder.Finish(BatchRunStatus.FAIL, processed, read, failed, skipped, checkpoint,
                    new MessageRef(BatchFailureLimitException.MESSAGE_KEY, String.valueOf(limit.limit())), null);
        }
        if (failure != null) {
            return new BatchRunRecorder.Finish(BatchRunStatus.FAIL, processed, read, failed, skipped, checkpoint, null, BatchErrorText.of(failure));
        }
        BatchRunStatus status = processed == 0 && failed == 0 ? BatchRunStatus.SKIP : BatchRunStatus.OK;
        return new BatchRunRecorder.Finish(status, processed, read, failed, skipped, checkpoint, result.message(), null);
    }
}
