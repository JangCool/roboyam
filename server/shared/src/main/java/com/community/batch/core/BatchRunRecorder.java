package com.community.batch.core;

import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunMode;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchRunStepEntity;
import com.community.batch.entity.BatchStepStatus;
import com.community.batch.entity.BatchTriggerType;
import com.community.batch.entity.QBatchJobEntity;
import com.community.batch.entity.QBatchRunEntity;
import com.community.batch.entity.QBatchRunRequestEntity;
import com.community.batch.entity.QBatchRunStepEntity;
import com.community.batch.entity.BatchRequestStatus;
import com.community.batch.repository.master.BatchRunRepository;
import com.community.batch.repository.master.BatchRunStepRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.community.zconfiguration.message.MessageRef;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배치 실행 로그({@code batch_runs})·단계({@code batch_run_steps})와 목록의 「마지막 실행 요약」({@code batch_jobs.last_*})을 쓴다. 배치 일 자체와는 <b>별도 트랜잭션</b>이다 —
 * 시작 줄은 배치가 도는 동안에도 다른 곳에서 보이고(실행 중 표시), 배치가 실패해 롤백돼도 「실패했다」는 남는다. 예외는 덩어리 진행({@link #addStepProgress})으로,
 * 덩어리 저장 확정과 <b>같은 트랜잭션</b>에 참여한다(15-3 성능 규칙 — 성공 건은 건별로 기록하지 않고 덩어리마다 단계 건수·체크포인트를 한 번 고친다).
 * JPA·QueryDSL 로 쓴다(넣기는 넣고 바로 내보낸다 — 실행 중·예약 시각·이어 받기 유일 색인 위반이 {@code DataIntegrityViolationException} 으로 그 자리에서 난다, 닫기는 조건부 UPDATE).
 * Native SQL 로 남긴 것: 작업별 설정값 DB 함수 {@code batch_param()}. 트랜잭션 틀은 {@link BatchTransactions}.
 */
@Component
public class BatchRunRecorder {

    /** 서버가 죽어 끝맺지 못한 실행을 닫을 때 쓰는 요약 문구 키 */
    public static final String INTERRUPTED_KEY = "batch.error.interrupted";

    /** 덩어리 크기 기본값·범위 — 작업별 설정 {@code batch_params(job_key, 'chunk_size')} */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 실패 한도 기본값 — 작업별 설정 {@code batch_params(job_key, 'failure_limit')}. 넘으면 실행을 FAIL 로 멈춘다 */
    public static final int DEFAULT_FAILURE_LIMIT = 100;

    /** 시작한 실행 — 끝낼 때 이 값으로 줄을 찾는다 */
    public record Started(long runId, Instant startedAt) {
    }

    /** 시작할 때 넣는 단계 한 줄 — 상태는 {@code PENDING} 이나 {@code SKIP}(고르지 않음·이미 끝남·다시 할 실패 없음) */
    public record StepRow(String name, int order, BatchStepStatus status) {
    }

    /** 실행 시작 */
    public record StartCommand(String jobKey, BatchTriggerType trigger, Instant scheduledAt, Long operatorId, BatchRunMode mode, String paramsJson,
                               Long resumedFromRunId, Long requestId, List<StepRow> steps) {
    }

    /**
     * 실행 끝 — {@code status} 는 OK · SKIP · FAIL. 실패는 {@code error}(이미 가린 한 줄) 또는 {@code message}(문구 키 — 예: 실패 한도)를, 그 밖은 {@code message} 요약을 싣는다.
     * 건수는 처리({@code affected_count}) · 읽음 · 실패 · 건너뜀.
     */
    public record Finish(BatchRunStatus status, Long processed, Long read, Long failed, Long skipped, String checkpoint, MessageRef message, String error) {
    }

    /** 다시 돌릴 원래 실행 */
    public record SourceRun(long id, String jobKey, BatchRunStatus status, BatchRunMode mode, String paramsJson, String checkpoint, Map<String, SourceStep> steps) {
    }

    public record SourceStep(String name, BatchStepStatus status, String checkpoint) {
    }

    /** 작업별 설정값 */
    public record Settings(int chunkSize, int failureLimit) {
    }

    private static final QBatchRunEntity RUN = QBatchRunEntity.batchRunEntity;
    private static final QBatchRunStepEntity STEP = QBatchRunStepEntity.batchRunStepEntity;
    private static final QBatchJobEntity JOB = QBatchJobEntity.batchJobEntity;
    private static final QBatchRunRequestEntity REQUEST = QBatchRunRequestEntity.batchRunRequestEntity;
    /** 하위 질의용 별칭 */
    private static final QBatchRunEntity RUNNING = new QBatchRunEntity("runningRun");

    private final JPAQueryFactory query;
    private final BatchRunRepository runs;
    private final BatchRunStepRepository steps;
    private final EntityManager entityManager;
    private final TransactionTemplate requiresNew;
    private final TransactionTemplate required;
    private final SnowflakeIdGenerator ids;
    private final int nodeId;

    @Autowired
    public BatchRunRecorder(@Qualifier(JpaMasterConfig.QUERY_FACTORY) JPAQueryFactory query, BatchRunRepository runs, BatchRunStepRepository steps,
                            EntityManager entityManager, PlatformTransactionManager transactions, SnowflakeIdGenerator ids, @Value("${roboyam.node-id}") int nodeId) {
        this.query = query;
        this.runs = runs;
        this.steps = steps;
        this.entityManager = entityManager;
        this.requiresNew = BatchTransactions.requiresNew(transactions);
        this.required = BatchTransactions.required(transactions);
        this.ids = ids;
        this.nodeId = nodeId;
    }

    /**
     * 실행 중 줄과 단계 줄을 넣고, 요청에서 왔으면 요청에 실행 번호를 잇는다. 같은 배치가 이미 실행 중이거나(유일 색인) 같은 예약 시각의 줄이 이미 있거나,
     * 같은 실행을 두 번 이어 받으려 하면(RESUME 유일 색인) {@code DataIntegrityViolationException} 이 난다 — 부르는 쪽이 「건너뜀」·「거절」로 다룬다.
     */
    public Started start(StartCommand command) {
        return requiresNew.execute(status -> {
            Instant startedAt = BatchTransactions.now();
            long runId = ids.nextId();
            runs.persist(BatchRunEntity.start(runId, command.jobKey(), command.trigger(), command.scheduledAt(), startedAt, command.operatorId(), nodeId, command.mode(),
                    command.paramsJson(), command.resumedFromRunId(), command.requestId()));
            runs.flush(); // 유일 색인 위반은 여기서(저장소가 DataIntegrityViolationException 으로 바꾼다)
            for (StepRow step : command.steps()) {
                steps.persist(BatchRunStepEntity.plan(runId, step.name(), step.order(), step.status(), startedAt));
            }
            steps.flush();
            if (command.requestId() != null) {
                query.update(REQUEST).set(REQUEST.runId, runId).where(REQUEST.id.eq(command.requestId()), REQUEST.status.eq(BatchRequestStatus.RUNNING)).execute();
            }
            updateLastRun(command.jobKey(), startedAt, BatchRunStatus.RUNNING, null, null, null);
            return new Started(runId, startedAt);
        });
    }

    /** 실행을 끝낸다 */
    public void finish(Started started, String jobKey, Finish finish) {
        requiresNew.executeWithoutResult(status -> {
            String messageKey = finish.message() == null ? null : finish.message().key();
            String messageArgs = finish.message() == null ? null : BatchMessageCodec.encodeArgs(finish.message());
            long updated = query.update(RUN).set(RUN.status, finish.status()).set(RUN.finishedAt, BatchTransactions.now()).set(RUN.affectedCount, finish.processed())
                    .set(RUN.readCount, finish.read()).set(RUN.failedCount, finish.failed()).set(RUN.skippedCount, finish.skipped())
                    .set(RUN.checkpoint, finish.checkpoint()).set(RUN.messageKey, messageKey).set(RUN.messageArgs, messageArgs).set(RUN.errorMessage, finish.error())
                    .where(RUN.id.eq(started.runId()), RUN.status.eq(BatchRunStatus.RUNNING)).execute();
            if (updated == 0) {
                throw new IllegalStateException("실행 로그 줄이 없거나 이미 닫혔다: " + started.runId());
            }
            updateLastRun(jobKey, started.startedAt(), finish.status(), finish.processed(), finish.message(), finish.error());
        });
    }

    /** 단계에 들어간다 — {@code RUNNING} + 시작 시각 */
    public void stepRunning(long runId, String step) {
        requiresNew.executeWithoutResult(status -> query.update(STEP).set(STEP.status, BatchStepStatus.RUNNING).set(STEP.startedAt, BatchTransactions.now())
                .where(STEP.runId.eq(runId), STEP.stepName.eq(step), STEP.status.eq(BatchStepStatus.PENDING)).execute());
    }

    /** 단계를 끝낸다 — OK · FAIL · SKIP. 이미 끝난 단계는 건드리지 않는다 */
    public void finishStep(long runId, String step, BatchStepStatus result) {
        requiresNew.executeWithoutResult(status -> {
            Instant now = BatchTransactions.now();
            query.update(STEP).set(STEP.status, result).set(STEP.finishedAt, now).set(STEP.startedAt, STEP.startedAt.coalesce(now))
                    .where(STEP.runId.eq(runId), STEP.stepName.eq(step), STEP.status.in(BatchStepStatus.PENDING, BatchStepStatus.RUNNING)).execute();
        });
    }

    /**
     * 덩어리 하나만큼 단계 건수를 더하고 체크포인트를 옮긴다(null 이면 그대로). <b>부르는 쪽 트랜잭션에 참여한다</b> — 덩어리 처리와 같은 트랜잭션에서 불러 함께 확정·롤백되게 한다.
     */
    public void addStepProgress(long runId, String step, long read, long processed, long failed, long skipped, String checkpoint) {
        required.executeWithoutResult(status -> {
            JPAUpdateClause update = query.update(STEP).set(STEP.readCount, STEP.readCount.add(read)).set(STEP.processedCount, STEP.processedCount.add(processed))
                    .set(STEP.failedCount, STEP.failedCount.add(failed)).set(STEP.skippedCount, STEP.skippedCount.add(skipped));
            if (checkpoint != null) {
                update.set(STEP.checkpoint, checkpoint);
            }
            update.where(STEP.runId.eq(runId), STEP.stepName.eq(step)).execute();
        });
    }

    /**
     * 작업별 설정값(덩어리 크기·실패 한도) — {@code batch_params} 를 {@code batch_param()} 으로 읽어 범위 밖이면 기본값.
     *
     * <p>Native SQL 인 이유: DB 함수 {@code batch_param()} — DB 배치 프로시저와 같은 범위 검사를 한 곳에 둔다.
     */
    public Settings settingsOf(String jobKey) {
        Object[] row = (Object[]) entityManager.createNativeQuery("SELECT batch_param(?1, 'chunk_size', ?2, 1, 10000), batch_param(?1, 'failure_limit', ?3, 0, 100000)")
                .setParameter(1, jobKey).setParameter(2, (long) DEFAULT_CHUNK_SIZE).setParameter(3, (long) DEFAULT_FAILURE_LIMIT).getSingleResult();
        return new Settings(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
    }

    /** 예약 실행이 켜져 있나({@code batch_jobs.enabled}). 설정 행이 없으면 꺼진 것으로 본다 */
    public boolean scheduleEnabled(String jobKey) {
        return Boolean.TRUE.equals(query.select(JOB.enabled).from(JOB).where(JOB.jobKey.eq(jobKey)).fetchOne());
    }

    /** 다시 돌릴 원래 실행과 그 단계 */
    public Optional<SourceRun> sourceRun(long runId) {
        Tuple t = query.select(RUN.id, RUN.jobKey, RUN.status, RUN.runMode, RUN.params, RUN.checkpoint).from(RUN).where(RUN.id.eq(runId)).fetchOne();
        if (t == null) {
            return Optional.empty();
        }
        SourceRun run = new SourceRun(t.get(RUN.id), t.get(RUN.jobKey), t.get(RUN.status), t.get(RUN.runMode), t.get(RUN.params), t.get(RUN.checkpoint),
                new LinkedHashMap<>());
        query.select(STEP.stepName, STEP.status, STEP.checkpoint).from(STEP).where(STEP.runId.eq(runId)).orderBy(STEP.stepOrder.asc()).fetch()
                .forEach(s -> run.steps().put(s.get(STEP.stepName), new SourceStep(s.get(STEP.stepName), s.get(STEP.status), s.get(STEP.checkpoint))));
        return Optional.of(run);
    }

    /** 이 실행을 이미 이어 받은(RESUME) 실행이 있나 — 한 실행은 한 번만 이어 받는다(확정 규칙 3) */
    public boolean hasResume(long runId) {
        return query.selectOne().from(RUN).where(RUN.resumedFromRunId.eq(runId), RUN.runMode.eq(BatchRunMode.RESUME)).fetchFirst() != null;
    }

    /** DB 배치가 만든 실행 줄에 그 실행을 만든 요청을 잇는다 */
    public void linkRequest(long runId, long requestId) {
        requiresNew.executeWithoutResult(status -> query.update(RUN).set(RUN.requestId, requestId).where(RUN.id.eq(runId), RUN.requestId.isNull()).execute());
    }

    /**
     * 그 배치의 「실행 중」으로 남은 줄(과 그 실행의 실행 중 단계)을 실패로 닫는다(서버가 죽어 끝맺지 못한 실행). <b>그 배치의 잠금을 쥔 쪽만</b> 부른다 —
     * 잠금을 쥐었다는 것은 지금 그 배치를 돌리는 서버가 없다는 뜻이다.
     *
     * @return 닫은 줄 수
     */
    public long failInterrupted(String jobKey) {
        Long closed = requiresNew.execute(status -> {
            Instant now = BatchTransactions.now();
            query.update(STEP).set(STEP.status, BatchStepStatus.FAIL).set(STEP.finishedAt, now)
                    .where(STEP.status.eq(BatchStepStatus.RUNNING),
                            STEP.runId.in(JPAExpressions.select(RUNNING.id).from(RUNNING).where(RUNNING.jobKey.eq(jobKey), RUNNING.status.eq(BatchRunStatus.RUNNING))))
                    .execute();
            long count = query.update(RUN).set(RUN.status, BatchRunStatus.FAIL).set(RUN.finishedAt, now).set(RUN.messageKey, INTERRUPTED_KEY).setNull(RUN.messageArgs)
                    .where(RUN.jobKey.eq(jobKey), RUN.status.eq(BatchRunStatus.RUNNING)).execute();
            if (count > 0) {
                query.update(JOB).set(JOB.lastStatus, BatchRunStatus.FAIL.name()).set(JOB.lastMessageKey, INTERRUPTED_KEY).setNull(JOB.lastMessageArgs)
                        .where(JOB.jobKey.eq(jobKey), JOB.lastStatus.eq(BatchRunStatus.RUNNING.name())).execute();
            }
            return count;
        });
        return closed == null ? 0 : closed;
    }

    /** 실행 중으로 남아 있는 로그가 있는 배치 키 */
    public Set<String> runningJobKeys() {
        return new HashSet<>(query.selectDistinct(RUN.jobKey).from(RUN).where(RUN.status.eq(BatchRunStatus.RUNNING)).fetch());
    }

    /** 그 배치가 지금 실행 중인가(실행 중 줄이 있나) */
    public boolean isRunning(String jobKey) {
        return query.selectOne().from(RUN).where(RUN.jobKey.eq(jobKey), RUN.status.eq(BatchRunStatus.RUNNING)).fetchFirst() != null;
    }

    /** 목록의 마지막 실행 요약(Java 배치만 행이 있다 — 없으면 아무것도 바뀌지 않는다) */
    private void updateLastRun(String jobKey, Instant at, BatchRunStatus status, Long affected, MessageRef message, String error) {
        query.update(JOB).set(JOB.lastRunAt, at).set(JOB.lastStatus, status.name()).set(JOB.lastAffectedCount, affected)
                .set(JOB.lastMessageKey, message == null ? null : message.key()).set(JOB.lastMessageArgs, message == null ? null : BatchMessageCodec.encodeArgs(message))
                .set(JOB.lastError, error).where(JOB.jobKey.eq(jobKey)).execute();
    }
}
