package com.community.testsupport;

import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchRequestQueue;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.repository.master.BatchRunFailureRepository;
import com.community.batch.repository.master.BatchRunRepository;
import com.community.batch.repository.master.BatchRunRequestRepository;
import com.community.batch.repository.master.BatchRunStepRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 배치 core 기록기(JPA·QueryDSL)를 손으로 조립하는 시험의 도우미 — 서버 번호({@code node_id})를 시험마다 따로 주려고 빈을 쓰지 않고 만든다. {@code beans} 는 시험의 스프링 문맥
 * (앱 시험) 또는 {@link JpaTestContext#context()}(손으로 조립하는 시험 — {@link #recorder(SnowflakeIdGenerator, int)} 처럼 문맥을 받지 않는 판). 트랜잭션 관리자는 그 문맥의 JPA 것이다.
 */
public final class BatchTestParts {

    private BatchTestParts() {
    }

    public static BatchRunRecorder recorder(BeanFactory beans, SnowflakeIdGenerator ids, int nodeId) {
        return new BatchRunRecorder(query(beans), beans.getBean(BatchRunRepository.class), beans.getBean(BatchRunStepRepository.class), beans.getBean(EntityManager.class),
                transactions(beans), ids, nodeId);
    }

    public static BatchFailureRecorder failures(BeanFactory beans, SnowflakeIdGenerator ids) {
        return new BatchFailureRecorder(query(beans), beans.getBean(BatchRunFailureRepository.class), transactions(beans), ids);
    }

    public static BatchRequestQueue queue(BeanFactory beans, SnowflakeIdGenerator ids, int nodeId) {
        return new BatchRequestQueue(query(beans), beans.getBean(BatchRunRequestRepository.class), beans.getBean(EntityManager.class), transactions(beans), ids, nodeId);
    }

    /** {@link JpaTestContext} 의 빈으로 */
    public static BatchRunRecorder recorder(SnowflakeIdGenerator ids, int nodeId) {
        return recorder(JpaTestContext.context(), ids, nodeId);
    }

    /** {@link JpaTestContext} 의 빈으로 */
    public static BatchFailureRecorder failures(SnowflakeIdGenerator ids) {
        return failures(JpaTestContext.context(), ids);
    }

    /** {@link JpaTestContext} 의 빈으로 */
    public static BatchRequestQueue queue(SnowflakeIdGenerator ids, int nodeId) {
        return queue(JpaTestContext.context(), ids, nodeId);
    }

    public static JPAQueryFactory query(BeanFactory beans) {
        return beans.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
    }

    public static PlatformTransactionManager transactions(BeanFactory beans) {
        return beans.getBean(PlatformTransactionManager.class);
    }
}
