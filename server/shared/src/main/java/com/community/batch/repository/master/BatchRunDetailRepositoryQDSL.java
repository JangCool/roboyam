package com.community.batch.repository.master;

import com.community.batch.dto.BatchRunRequestRow;
import com.community.batch.entity.BatchRequestStatus;
import com.community.batch.entity.BatchRunFailureEntity;
import com.community.batch.entity.BatchRunMode;
import com.community.batch.entity.BatchRunStepEntity;
import com.community.batch.entity.QBatchRunEntity;
import com.community.batch.entity.QBatchRunFailureEntity;
import com.community.batch.entity.QBatchRunRequestEntity;
import com.community.batch.entity.QBatchRunStepEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 배치 실행 상세 화면이 읽는 것 — 단계, 건별 실패(쪽), 실행 요청, 이어 받은 실행(V61 표들). 쓰기는 배치 core 의 JDBC 기록기가 하고 여기는 읽기만 한다.
 * <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class BatchRunDetailRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QBatchRunEntity RUN = QBatchRunEntity.batchRunEntity;
    private static final QBatchRunStepEntity STEP = QBatchRunStepEntity.batchRunStepEntity;
    private static final QBatchRunFailureEntity FAILURE = QBatchRunFailureEntity.batchRunFailureEntity;
    private static final QBatchRunRequestEntity REQUEST = QBatchRunRequestEntity.batchRunRequestEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    private static final List<BatchRequestStatus> OPEN = List.of(BatchRequestStatus.PENDING, BatchRequestStatus.RUNNING);

    public BatchRunDetailRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 실행의 단계(차례대로) */
    public List<BatchRunStepEntity> steps(long runId) {
        return query.selectFrom(STEP).where(STEP.runId.eq(runId)).orderBy(STEP.stepOrder.asc()).fetch();
    }

    /** 실행의 건별 실패 한 쪽 — 일어난 시각 최근순(같으면 id 최근순). {@code resolved} 가 null 이면 전체 */
    public List<BatchRunFailureEntity> failures(long runId, Boolean resolved, int page, int size) {
        return query.selectFrom(FAILURE).where(FAILURE.runId.eq(runId), resolvedIs(resolved))
                .orderBy(FAILURE.occurredAt.desc(), FAILURE.id.desc())
                .offset((long) page * size).limit(size).fetch();
    }

    public long countFailures(long runId, Boolean resolved) {
        Long total = query.select(FAILURE.count()).from(FAILURE).where(FAILURE.runId.eq(runId), resolvedIs(resolved)).fetchOne();
        return total == null ? 0 : total;
    }

    /** 이 실행을 다시 돌린 실행들(이어 받기·실패 건만 다시) — 시작 순 */
    public List<Long> resumedBy(long runId) {
        return query.select(RUN.id).from(RUN).where(RUN.resumedFromRunId.eq(runId)).orderBy(RUN.startedAt.asc(), RUN.id.asc()).fetch();
    }

    /** 이 실행을 이어 받은(RESUME) 실행이 있나 — 한 번만 이어 받는다(확정 규칙 3) */
    public boolean hasResume(long runId) {
        return query.selectOne().from(RUN).where(RUN.resumedFromRunId.eq(runId), RUN.runMode.eq(BatchRunMode.RESUME)).fetchFirst() != null;
    }

    /** 요청 한 줄과 요청한 운영자 이름 */
    public Optional<BatchRunRequestRow> request(long requestId) {
        Tuple row = query.select(REQUEST, OPERATOR.operatorName).from(REQUEST).leftJoin(OPERATOR).on(OPERATOR.id.eq(REQUEST.operatorId))
                .where(REQUEST.id.eq(requestId)).fetchOne();
        return row == null ? Optional.empty() : Optional.of(new BatchRunRequestRow(row.get(REQUEST), row.get(OPERATOR.operatorName)));
    }

    /** 배치마다 끝나지 않은(PENDING·RUNNING) 요청 — 같은 배치에 하나뿐이다 */
    public Map<String, BatchRunRequestRow> openByJobKey(Collection<String> jobKeys) {
        Map<String, BatchRunRequestRow> open = new HashMap<>();
        if (jobKeys.isEmpty()) {
            return open;
        }
        query.select(REQUEST, OPERATOR.operatorName).from(REQUEST).leftJoin(OPERATOR).on(OPERATOR.id.eq(REQUEST.operatorId))
                .where(REQUEST.jobKey.in(jobKeys), REQUEST.status.in(OPEN)).fetch()
                .forEach(row -> open.put(row.get(REQUEST).getJobKey(), new BatchRunRequestRow(row.get(REQUEST), row.get(OPERATOR.operatorName))));
        return open;
    }

    /** 이 실행을 원래 실행으로 삼은 끝나지 않은 요청(다시 실행 요청) */
    public Optional<BatchRunRequestRow> openBySourceRun(long runId) {
        Tuple row = query.select(REQUEST, OPERATOR.operatorName).from(REQUEST).leftJoin(OPERATOR).on(OPERATOR.id.eq(REQUEST.operatorId))
                .where(REQUEST.sourceRunId.eq(runId), REQUEST.status.in(OPEN)).orderBy(REQUEST.requestedAt.desc()).fetchFirst();
        return row == null ? Optional.empty() : Optional.of(new BatchRunRequestRow(row.get(REQUEST), row.get(OPERATOR.operatorName)));
    }

    /** 아직 해결하지 않은 실패 수 */
    public long countUnresolved(long runId) {
        return countFailures(runId, false);
    }

    private static BooleanExpression resolvedIs(Boolean resolved) {
        return resolved == null ? null : FAILURE.resolved.eq(resolved);
    }
}
