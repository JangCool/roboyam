package com.community.batch.core;

import com.community.batch.entity.BatchRunMode;
import com.community.batch.entity.BatchStepStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실행 한 번의 문맥 — 작업({@link BatchJob#run})이 받는다. 실행 번호, 실행 방식(보통·실패 지점부터·실패 건만), 파라미터, 이번에 돌 단계와 단계별 시작 체크포인트,
 * 「실패 건만 다시」의 원래 실행을 들고 있고, {@link #step} 으로 단계에 들어가며 덩어리 처리 도우미({@link BatchStep#chunks})를 준다.
 *
 * <p>단계는 작업이 정한 차례대로 들어간다 — 다음 단계에 들어가면 앞 단계는 「끝남」(OK)이 된다. 이번 실행에서 건너뛰는 단계(고르지 않음 · 이미 끝냄 · 다시 할 실패 없음)도
 * {@link #step} 으로 받을 수 있지만 {@link BatchStep#active()} 가 false 이고 아무 일도 하지 않는다. 고를 수 없는 단계(selectable=false)는 단계를 골라 실행해도 늘 돈다(확정 규칙 2).
 */
public final class BatchContext {

    private final long runId;
    private final BatchDefinition definition;
    private final BatchRunMode mode;
    private final BatchParameters parameters;
    private final Long sourceRunId;
    private final BatchRunRecorder.Settings settings;
    private final BatchRunRecorder recorder;
    private final BatchFailureRecorder failures;
    private final TransactionTemplate requiresNew;
    private final TransactionTemplate readOnly;
    private final Map<String, BatchStep> steps = new LinkedHashMap<>();
    private BatchStep current;
    private long failureCount;

    BatchContext(long runId, BatchDefinition definition, BatchRunMode mode, BatchParameters parameters, Long sourceRunId, BatchRunRecorder.Settings settings,
                 BatchRunRecorder recorder, BatchFailureRecorder failures, TransactionTemplate requiresNew, TransactionTemplate readOnly) {
        this.runId = runId;
        this.definition = definition;
        this.mode = mode;
        this.parameters = parameters;
        this.sourceRunId = sourceRunId;
        this.settings = settings;
        this.recorder = recorder;
        this.failures = failures;
        this.requiresNew = requiresNew;
        this.readOnly = readOnly;
    }

    void plan(String name, int order, BatchStep.Plan plan, String startCheckpoint) {
        steps.put(name, new BatchStep(this, name, order, plan, startCheckpoint));
    }

    /** 이 실행의 로그 번호({@code batch_runs.id}) */
    public long runId() {
        return runId;
    }

    public String jobKey() {
        return definition.key();
    }

    /** 보통 · 실패 지점부터 다시 · 실패 건만 다시 */
    public BatchRunMode mode() {
        return mode;
    }

    /** 실행 파라미터 — 다시 실행은 원래 실행의 값 */
    public BatchParameters parameters() {
        return parameters;
    }

    /** 덩어리 크기(작업별 {@code chunk_size}, 기본 {@value BatchRunRecorder#DEFAULT_CHUNK_SIZE}) */
    public int chunkSize() {
        return settings.chunkSize();
    }

    /** 실패 한도(작업별 {@code failure_limit}, 기본 {@value BatchRunRecorder#DEFAULT_FAILURE_LIMIT}) */
    public int failureLimit() {
        return settings.failureLimit();
    }

    /** 단계에 들어간다(앞 단계는 끝난다). 작업이 정의하지 않은 이름이면 {@link IllegalArgumentException} */
    public BatchStep step(String name) {
        BatchStep step = steps.get(name);
        if (step == null) {
            throw new IllegalArgumentException("작업이 정의하지 않은 단계다: " + definition.key() + "." + name);
        }
        if (step.active() && step.status() == BatchStepStatus.PENDING) {
            enter(step);
        }
        return step;
    }

    /** 이 단계가 이번 실행에서 도는가 */
    public boolean shouldRun(String name) {
        BatchStep step = steps.get(name);
        return step != null && step.active();
    }

    void enter(BatchStep step) {
        if (current != null && step.order() < current.order()) {
            throw new IllegalStateException("단계는 정의한 차례대로 들어간다: " + step.name());
        }
        if (current != null && current != step) {
            current.finish(BatchStepStatus.OK);
        }
        current = step;
        step.markRunning();
    }

    /** 실행이 끝났다 — 성공이면 지금 단계는 OK, 들어가지 않은 단계는 SKIP. 실패면 지금 단계만 FAIL(닿지 못한 단계는 PENDING 으로 남아 이어 받기가 돌린다) */
    void complete(boolean success) {
        if (current != null) {
            current.finish(success ? BatchStepStatus.OK : BatchStepStatus.FAIL);
        }
        if (success) {
            steps.values().stream().filter(step -> step.status() == BatchStepStatus.PENDING).forEach(step -> step.finish(BatchStepStatus.SKIP));
        }
    }

    List<BatchStep> steps() {
        return Collections.unmodifiableList(new ArrayList<>(steps.values()));
    }

    Long sourceRunId() {
        return sourceRunId;
    }

    /** 이 실행의 실패 수를 하나 늘리고 돌려준다 */
    long addFailure() {
        return ++failureCount;
    }

    BatchRunRecorder recorder() {
        return recorder;
    }

    BatchFailureRecorder failures() {
        return failures;
    }

    TransactionTemplate requiresNew() {
        return requiresNew;
    }

    TransactionTemplate readOnly() {
        return readOnly;
    }
}
