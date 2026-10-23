package com.community.batch.core;

import com.community.batch.core.BatchFailureRecorder.OpenFailure;
import com.community.batch.entity.BatchStepStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 실행 중인 작업의 단계 하나 — {@link BatchContext#step} 으로 들어간다. 건수(읽음·처리·실패·건너뜀)와 체크포인트를 세고 {@code batch_run_steps} 에 남긴다.
 *
 * <p><b>덩어리 처리 도우미</b>({@link #chunks}, 15-3 성능 규칙):
 * <ol>
 *   <li>정렬 키 다음부터 덩어리(작업별 {@code chunk_size}, 기본 500)를 읽고, 덩어리째 처리하고, <b>같은 트랜잭션에서</b> 단계 건수·체크포인트를 한 번 고친다 —
 *       성공 건은 건별로 기록하지 않는다(10만 건 / 500 = 이력 갱신 200번).</li>
 *   <li>덩어리가 실패하면 그 트랜잭션을 되돌리고 <b>그 덩어리만</b> 한 건씩(건마다 트랜잭션) 다시 처리해 실패한 건만 {@code batch_run_failures} 에 남긴다(별도 트랜잭션,
 *       문구는 가린다). 다른 덩어리는 한 건씩 돌지 않는다.</li>
 *   <li>한 실행의 실패 수가 작업별 {@code failure_limit}(기본 100)을 넘으면 {@link BatchFailureLimitException} 으로 실행을 멈춘다(FAIL).</li>
 *   <li>「실패 지점부터 다시」(RESUME)는 원래 실행에서 실패한 단계의 체크포인트 다음부터 읽는다. 「실패 건만 다시」(RETRY_FAILED)는 원래 실행의 미해결 실패 건만
 *       {@link ChunkSource#byIds} 로 읽어 한 건씩 처리한다 — 성공하면 원래 줄을 해결 표시하고, 또 실패하면 원래 줄은 시도 +1(미해결) · 새 실행에 새 줄(시도 = 원래 + 1).</li>
 * </ol>
 * 한 단계에서 {@link #chunks} 는 한 번만 부른다. 덩어리 처리가 아닌 일은 {@link #record} 로 건수를 남긴다.
 */
public final class BatchStep {

    private static final Logger log = LoggerFactory.getLogger(BatchStep.class);

    /** 이번 실행에서 이 단계를 어떻게 하나 */
    enum Plan {
        /** 돈다(처음부터 또는 {@code startCheckpoint} 다음부터) */
        RUN,
        /** 건너뛴다 — 고르지 않았거나, 이어 받은 실행에서 원래 실행이 끝낸 단계거나, 다시 할 실패 건이 없다 */
        SKIP,
        /** 원래 실행의 미해결 실패 건만 */
        RETRY
    }

    private final BatchContext context;
    private final String name;
    private final int order;
    private final Plan plan;
    private final String startCheckpoint;

    private BatchStepStatus status;
    private boolean chunked;
    private long read;
    private long processed;
    private long failed;
    private long skipped;
    private String checkpoint;

    BatchStep(BatchContext context, String name, int order, Plan plan, String startCheckpoint) {
        this.context = context;
        this.name = name;
        this.order = order;
        this.plan = plan;
        this.startCheckpoint = startCheckpoint;
        this.status = plan == Plan.SKIP ? BatchStepStatus.SKIP : BatchStepStatus.PENDING;
    }

    public String name() {
        return name;
    }

    /** 이번 실행에서 이 단계가 도는가(건너뛰는 단계에서는 {@link #chunks}·{@link #record} 가 아무것도 하지 않는다) */
    public boolean active() {
        return plan != Plan.SKIP;
    }

    /** 「실패 건만 다시」로 도는가 */
    public boolean retrying() {
        return plan == Plan.RETRY;
    }

    public long readCount() {
        return read;
    }

    public long processedCount() {
        return processed;
    }

    public long failedCount() {
        return failed;
    }

    public long skippedCount() {
        return skipped;
    }

    /** 마지막으로 끝낸 덩어리의 정렬 키 — 없으면 null */
    public String checkpoint() {
        return checkpoint;
    }

    BatchStepStatus status() {
        return status;
    }

    int order() {
        return order;
    }

    Plan plan() {
        return plan;
    }

    /** 덩어리 처리 — 위 설명대로. 실행이 멈춰야 하는 오류(읽기 실패·실패 한도)는 그대로 던진다 */
    public <T> void chunks(ChunkSource<T> source, ChunkProcessor<T> processor) {
        if (!active()) {
            return;
        }
        ensureRunning();
        if (chunked) {
            throw new IllegalStateException("한 단계에서 덩어리 처리는 한 번만 한다: " + name);
        }
        chunked = true;
        if (plan == Plan.RETRY) {
            retryFailures(source, processor);
            return;
        }
        int size = context.chunkSize();
        String after = startCheckpoint;
        checkpoint = startCheckpoint;
        while (true) {
            String from = after;
            List<T> items = context.readOnly().execute(status -> source.next(from, size));
            if (items == null || items.isEmpty()) {
                return;
            }
            String last = limitKey(source.keyOf(items.get(items.size() - 1)));
            Tally tally;
            try {
                tally = context.requiresNew().execute(status -> {
                    long done = processChecked(processor, items);
                    Tally whole = new Tally(items.size(), done, 0, items.size() - done);
                    context.recorder().addStepProgress(context.runId(), name, whole.read, whole.processed, 0, whole.skipped, last);
                    return whole;
                });
            } catch (RuntimeException chunkFailure) {
                log.info("배치 덩어리가 실패해 한 건씩 다시 처리한다 jobKey={} step={} size={} 원인={}", context.jobKey(), name, items.size(),
                        chunkFailure.getClass().getSimpleName());
                tally = itemByItem(source, processor, items, last);
            }
            add(tally);
            checkpoint = last;
            if (items.size() < size) {
                return;
            }
            after = last;
        }
    }

    /** 덩어리 처리가 아닌 일의 건수를 남긴다(따로 확정) */
    public void record(long readCount, long processedCount, long skippedCount) {
        if (!active()) {
            return;
        }
        ensureRunning();
        context.requiresNew().executeWithoutResult(status -> context.recorder().addStepProgress(context.runId(), name, readCount, processedCount, 0, skippedCount, null));
        add(new Tally(readCount, processedCount, 0, skippedCount));
    }

    /** 실패한 덩어리를 한 건씩 — 성공한 건은 건마다 확정, 실패한 건만 기록. 건수·체크포인트는 끝에 한 번 */
    private <T> Tally itemByItem(ChunkSource<T> source, ChunkProcessor<T> processor, List<T> items, String last) {
        Tally tally = new Tally(0, 0, 0, 0);
        for (T item : items) {
            tally.read++;
            try {
                long done = context.requiresNew().execute(status -> processChecked(processor, List.of(item)));
                tally.processed += done;
                tally.skipped += 1 - done;
            } catch (RuntimeException itemFailure) {
                Throwable cause = unwrap(itemFailure);
                tally.failed++;
                context.failures().record(context.runId(), name, source.targetType(), source.targetIdOf(item), BatchErrorText.codeOf(cause), BatchErrorText.messageOf(cause), 1);
                stopIfOverLimit(tally);
            }
        }
        saveTally(tally, last);
        return tally;
    }

    /** 「실패 건만 다시」 — 원래 실행의 이 단계 미해결 실패 건만, 덩어리 크기씩 묶어 읽고 한 건씩 처리 */
    private <T> void retryFailures(ChunkSource<T> source, ChunkProcessor<T> processor) {
        List<OpenFailure> open = context.failures().unresolved(context.sourceRunId(), name);
        int size = context.chunkSize();
        for (int from = 0; from < open.size(); from += size) {
            List<OpenFailure> group = open.subList(from, Math.min(open.size(), from + size));
            List<T> found = context.readOnly().execute(status -> source.byIds(group.stream().map(OpenFailure::targetId).toList()));
            Map<String, T> byId = new HashMap<>();
            if (found != null) {
                found.forEach(item -> byId.put(source.targetIdOf(item), item));
            }
            Tally tally = new Tally(0, 0, 0, 0);
            for (OpenFailure failure : group) {
                tally.read++;
                T item = byId.get(failure.targetId());
                if (item == null) {
                    // 대상이 없어졌다 — 다시 할 일이 없으므로 건너뜀으로 세고 해결 표시한다
                    tally.skipped++;
                    context.requiresNew().executeWithoutResult(status -> context.failures().resolve(failure.id(), context.runId()));
                    continue;
                }
                try {
                    long done = context.requiresNew().execute(status -> {
                        long count = processChecked(processor, List.of(item));
                        context.failures().resolve(failure.id(), context.runId());
                        return count;
                    });
                    tally.processed += done;
                    tally.skipped += 1 - done;
                } catch (RuntimeException itemFailure) {
                    Throwable cause = unwrap(itemFailure);
                    tally.failed++;
                    context.failures().bumpAttempts(failure.id());
                    context.failures().record(context.runId(), name, failure.targetType(), failure.targetId(), BatchErrorText.codeOf(cause), BatchErrorText.messageOf(cause),
                            failure.attempts() + 1);
                    stopIfOverLimit(tally);
                }
            }
            saveTally(tally, null);
            add(tally);
        }
    }

    /** 실패 한도를 넘었으면 그때까지 센 건수를 남기고(체크포인트는 그대로) 멈춘다 */
    private void stopIfOverLimit(Tally tally) {
        if (context.addFailure() > context.failureLimit()) {
            saveTally(tally, null);
            add(tally);
            throw new BatchFailureLimitException(context.failureLimit());
        }
    }

    private void saveTally(Tally tally, String checkpointKey) {
        context.requiresNew().executeWithoutResult(status -> context.recorder().addStepProgress(context.runId(), name, tally.read, tally.processed, tally.failed,
                tally.skipped, checkpointKey));
    }

    private void add(Tally tally) {
        read += tally.read;
        processed += tally.processed;
        failed += tally.failed;
        skipped += tally.skipped;
    }

    private void ensureRunning() {
        if (status == BatchStepStatus.PENDING) {
            context.enter(this);
        }
        if (status != BatchStepStatus.RUNNING) {
            throw new IllegalStateException("이미 끝난 단계다: " + name);
        }
    }

    void markRunning() {
        status = BatchStepStatus.RUNNING;
        context.recorder().stepRunning(context.runId(), name);
    }

    void finish(BatchStepStatus result) {
        if (status == BatchStepStatus.RUNNING || status == BatchStepStatus.PENDING) {
            status = result;
            context.recorder().finishStep(context.runId(), name, result);
        }
    }

    private static <T> long processChecked(ChunkProcessor<T> processor, List<T> items) {
        long done;
        try {
            done = processor.process(items);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new CheckedFailure(e);
        }
        if (done < 0 || done > items.size()) {
            throw new IllegalStateException("처리 건수가 덩어리 크기를 벗어났다: " + done + "/" + items.size());
        }
        return done;
    }

    private static Throwable unwrap(RuntimeException e) {
        return e instanceof CheckedFailure checked ? checked.getCause() : e;
    }

    private static String limitKey(String key) {
        if (key == null) {
            return null;
        }
        return key.length() <= 200 ? key : key.substring(0, 200);
    }

    /** 처리 쪽이 던진 검사 예외를 트랜잭션 틀 밖으로 나르는 껍데기 */
    private static final class CheckedFailure extends RuntimeException {
        CheckedFailure(Exception cause) {
            super(cause);
        }
    }

    /** 덩어리 하나(또는 묶음 하나)의 건수 */
    private static final class Tally {
        long read;
        long processed;
        long failed;
        long skipped;

        Tally(long read, long processed, long failed, long skipped) {
            this.read = read;
            this.processed = processed;
            this.failed = failed;
            this.skipped = skipped;
        }
    }
}
