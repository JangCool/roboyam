package com.community.batch.repository.master;

import com.community.batch.dto.BatchLastRun;
import com.community.batch.entity.QBatchJobEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

/**
 * 배치 목록의 「마지막 실행 요약」({@code last_*}) 갱신. 운영자가 동시에 바꾸는 설정(사용 여부·cron)을 덮어쓰지 않도록 이 칸들만 조건부 UPDATE 로 고친다.
 * <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class BatchJobRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QBatchJobEntity JOB = QBatchJobEntity.batchJobEntity;

    public BatchJobRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 마지막 실행 요약을 바꾼다 */
    public void updateLastRun(String jobKey, BatchLastRun last) {
        query.update(JOB)
                .set(JOB.lastRunAt, last.at())
                .set(JOB.lastStatus, last.status())
                .set(JOB.lastAffectedCount, last.affectedCount())
                .set(JOB.lastMessageKey, last.messageKey())
                .set(JOB.lastMessageArgs, last.messageArgs())
                .set(JOB.lastError, last.error())
                .where(JOB.jobKey.eq(jobKey))
                .execute();
    }

    /** 마지막 실행 요약이 아직 「실행 중」이면 실패로 닫는다(서버가 죽어 끝맺지 못한 실행). 이미 다른 실행의 요약으로 바뀌었으면 건드리지 않는다 */
    public void failRunningSummary(String jobKey, String messageKey) {
        query.update(JOB)
                .set(JOB.lastStatus, "FAIL")
                .set(JOB.lastMessageKey, messageKey)
                .setNull(JOB.lastMessageArgs)
                .where(JOB.jobKey.eq(jobKey), JOB.lastStatus.eq("RUNNING"))
                .execute();
    }
}
