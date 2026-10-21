package com.community.batch.repository.master;

import com.community.batch.dto.BatchRunCriteria;
import com.community.batch.dto.BatchRunRow;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.QBatchRunEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 배치 실행 로그의 조회(조건·페이징은 DB 에서)와 조건부 UPDATE/DELETE. 수동 실행한 운영자 이름은 왼쪽 조인으로 함께 읽는다.
 * <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class BatchRunRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QBatchRunEntity RUN = QBatchRunEntity.batchRunEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    public BatchRunRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 조건에 맞는 로그 한 쪽 — 시작 시각 최근순(같으면 id 최근순) */
    public List<BatchRunRow> search(BatchRunCriteria criteria) {
        List<Tuple> rows = query.select(RUN, OPERATOR.operatorName).from(RUN).leftJoin(OPERATOR).on(OPERATOR.id.eq(RUN.operatorId))
                .where(conditions(criteria))
                .orderBy(RUN.startedAt.desc(), RUN.id.desc())
                .offset((long) criteria.page() * criteria.size()).limit(criteria.size())
                .fetch();
        return rows.stream().map(row -> new BatchRunRow(row.get(RUN), row.get(OPERATOR.operatorName))).toList();
    }

    /** 조건에 맞는 로그 수 */
    public long count(BatchRunCriteria criteria) {
        JPAQuery<Long> count = query.select(RUN.count()).from(RUN).where(conditions(criteria));
        Long total = count.fetchOne();
        return total == null ? 0 : total;
    }

    /** 로그 한 줄 */
    public Optional<BatchRunRow> findRow(long id) {
        Tuple row = query.select(RUN, OPERATOR.operatorName).from(RUN).leftJoin(OPERATOR).on(OPERATOR.id.eq(RUN.operatorId)).where(RUN.id.eq(id)).fetchOne();
        return row == null ? Optional.empty() : Optional.of(new BatchRunRow(row.get(RUN), row.get(OPERATOR.operatorName)));
    }

    /** 실행 중으로 남아 있는 로그가 있는 배치 키 */
    public Set<String> runningJobKeys() {
        return Set.copyOf(query.select(RUN.jobKey).distinct().from(RUN).where(RUN.status.eq(BatchRunStatus.RUNNING)).fetch());
    }

    /**
     * 그 배치의 「실행 중」으로 남은 로그를 모두 실패로 닫는다. 배치 잠금을 쥔 쪽만 부른다 — 잠금을 쥐었다는 것은 지금 그 배치를 돌리는 서버가 없다는 뜻이라
     * 남은 「실행 중」은 서버가 죽어 끝맺지 못한 것이다.
     *
     * @return 닫은 줄 수
     */
    public long failRunning(String jobKey, Instant now, String messageKey) {
        return query.update(RUN)
                .set(RUN.status, BatchRunStatus.FAIL)
                .set(RUN.finishedAt, now)
                .set(RUN.messageKey, messageKey)
                .where(RUN.jobKey.eq(jobKey), RUN.status.eq(BatchRunStatus.RUNNING))
                .execute();
    }

    /** 끝난 지 오래된 로그를 지운다(실행 중인 로그는 지우지 않는다). 지운 줄 수 */
    public long deleteFinishedBefore(Instant cutoff) {
        return query.delete(RUN).where(RUN.status.ne(BatchRunStatus.RUNNING), RUN.finishedAt.lt(cutoff)).execute();
    }

    private static BooleanExpression[] conditions(BatchRunCriteria criteria) {
        return new BooleanExpression[] {
                criteria.jobKey() == null ? null : RUN.jobKey.eq(criteria.jobKey()),
                criteria.status() == null ? null : RUN.status.eq(criteria.status()),
                criteria.triggerType() == null ? null : RUN.triggerType.eq(criteria.triggerType()),
                criteria.startedFrom() == null ? null : RUN.startedAt.goe(criteria.startedFrom()),
                criteria.startedTo() == null ? null : RUN.startedAt.loe(criteria.startedTo())
        };
    }
}
