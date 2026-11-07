package com.community.batch.core;

import com.community.batch.entity.BatchRunFailureEntity;
import com.community.batch.entity.QBatchRunFailureEntity;
import com.community.batch.repository.master.BatchRunFailureRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 건별 실패({@code batch_run_failures}) 기록 — <b>실패한 건만</b> 남긴다(성공 건은 단계 건수로만 센다). 실패 줄은 덩어리 일과 <b>다른 트랜잭션</b>으로 넣는다 — 그 건의 일이 롤백돼도
 * 실패했다는 기록은 남아야 한다. 문구는 부르는 쪽이 {@link BatchErrorText} 로 가려 넘기고 여기서 한 번 더 가린다(500자). JPA·QueryDSL 로 쓴다 — 트랜잭션 틀은 {@link BatchTransactions}.
 */
@Component
public class BatchFailureRecorder {

    /** 아직 해결하지 않은 실패 한 줄 */
    public record OpenFailure(long id, String stepName, String targetType, String targetId, String errorCode, int attempts) {
    }

    private static final QBatchRunFailureEntity F = QBatchRunFailureEntity.batchRunFailureEntity;

    private final JPAQueryFactory query;
    private final BatchRunFailureRepository failures;
    private final TransactionTemplate requiresNew;
    private final TransactionTemplate required;
    private final SnowflakeIdGenerator ids;

    public BatchFailureRecorder(@Qualifier(JpaMasterConfig.QUERY_FACTORY) JPAQueryFactory query, BatchRunFailureRepository failures, PlatformTransactionManager transactions,
                                SnowflakeIdGenerator ids) {
        this.query = query;
        this.failures = failures;
        this.requiresNew = BatchTransactions.requiresNew(transactions);
        this.required = BatchTransactions.required(transactions);
        this.ids = ids;
    }

    /** 실패 한 줄을 넣는다(따로 확정). 돌려주는 값은 줄 ID */
    public long record(long runId, String step, String targetType, String targetId, String errorCode, String message, int attempts) {
        long id = ids.nextId();
        String safeTarget = targetId == null ? "" : (targetId.length() <= 100 ? targetId : targetId.substring(0, 100));
        String safeType = targetType == null || targetType.isBlank() ? "UNKNOWN" : (targetType.length() <= 40 ? targetType : targetType.substring(0, 40));
        requiresNew.executeWithoutResult(status -> {
            failures.persist(BatchRunFailureEntity.record(id, runId, step, safeType, safeTarget, errorCode, BatchErrorText.mask(message == null ? "" : message), attempts,
                    BatchTransactions.now()));
            failures.flush();
        });
        return id;
    }

    /** 그 실행·단계의 미해결 실패(오래된 것부터) */
    public List<OpenFailure> unresolved(long runId, String step) {
        return query.select(F.id, F.stepName, F.targetType, F.targetId, F.errorCode, F.attempts).from(F).where(F.runId.eq(runId), F.stepName.eq(step), F.resolved.isFalse())
                .orderBy(F.id.asc()).fetch().stream()
                .map(t -> new OpenFailure(t.get(F.id), t.get(F.stepName), t.get(F.targetType), t.get(F.targetId), t.get(F.errorCode), t.get(F.attempts))).toList();
    }

    /** 그 실행의 단계별 미해결 실패 수(없는 단계는 빠진다) */
    public Map<String, Long> unresolvedCountByStep(long runId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        query.select(F.stepName, F.count()).from(F).where(F.runId.eq(runId), F.resolved.isFalse()).groupBy(F.stepName).fetch()
                .forEach(t -> counts.put(t.get(F.stepName), t.get(F.count())));
        return counts;
    }

    /** 해결 표시 — <b>부르는 쪽 트랜잭션에 참여한다</b>(그 건을 다시 처리한 트랜잭션과 함께 확정되게) */
    public void resolve(long failureId, long byRunId) {
        required.executeWithoutResult(status -> query.update(F).set(F.resolved, true).set(F.resolvedByRunId, byRunId).set(F.resolvedAt, BatchTransactions.now())
                .where(F.id.eq(failureId), F.resolved.isFalse()).execute());
    }

    /** 다시 해도 실패 — 원래 줄은 미해결인 채 시도 횟수만 +1(따로 확정, 확정 규칙 4) */
    public void bumpAttempts(long failureId) {
        requiresNew.executeWithoutResult(status -> query.update(F).set(F.attempts, F.attempts.add(1)).where(F.id.eq(failureId), F.resolved.isFalse()).execute());
    }
}
