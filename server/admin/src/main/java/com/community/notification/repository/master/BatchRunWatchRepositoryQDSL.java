package com.community.notification.repository.master;

import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.QBatchRunEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 배치 알림 감시자가 보는 실행 로그 — 끝났는데({@code finished_at}) 아직 처리하지 않은({@code notified_at IS NULL}) 줄. Java 배치와 DB 배치가 같은 표를 쓰므로 한 경로다.
 * <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class BatchRunWatchRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QBatchRunEntity RUN = QBatchRunEntity.batchRunEntity;

    public BatchRunWatchRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 처리할 줄을 오래된 것부터 가져오며 <b>잠근다</b>({@code FOR UPDATE SKIP LOCKED}) — 서버가 여러 대여도 같은 줄을 두 서버가 알리지 않는다(다른 서버가 잡은 줄은 건너뛴다).
     * 트랜잭션 안에서만 의미가 있다.
     */
    public List<BatchRunEntity> lockPending(int limit) {
        return query.selectFrom(RUN).where(RUN.notifiedAt.isNull(), RUN.finishedAt.isNotNull()).orderBy(RUN.id.asc()).limit(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint("jakarta.persistence.lock.timeout", -2).fetch();
    }

    /** 처리한 줄을 표시한다(알림을 냈든 낼 일이 아니었든). 바뀐 줄 수 */
    public long markNotified(Collection<Long> ids, Instant now) {
        if (ids.isEmpty()) {
            return 0;
        }
        return query.update(RUN).set(RUN.notifiedAt, now).where(RUN.id.in(ids), RUN.notifiedAt.isNull()).execute();
    }
}
