package com.community.point.repository.master;

import com.community.point.entity.PointPendingGrantEntity;
import com.community.point.entity.QPointPendingGrantEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 포인트 보류 지급({@code point_pending_grants}, V238) 읽기와 확정 배치의 행 잠금. 넣기·취소는 세이브포인트 안이라 {@link PointLedgerWriter}(JDBC)다.
 * 트랜잭션은 부르는 서비스·배치가 연다.
 */
@Repository
public class PointPendingGrantStore extends IrobitRepositoryQDSL {

    private static final QPointPendingGrantEntity P = QPointPendingGrantEntity.pointPendingGrantEntity;
    private static final String LOCK_TIMEOUT = "jakarta.persistence.lock.timeout";

    /** 확정할 차례가 된 보류 — 키셋(확정 예정 시각, ID) */
    public record Due(long id, Instant releaseAt) {
    }

    public PointPendingGrantStore(JPAQueryFactory query) {
        super(query);
    }

    /** 확정 예정 시각이 {@code now} 이하인 대기 중 보류를 (예정 시각, ID) 차례로 {@code after} 다음부터 {@code limit} 개 — 잠그지 않는다(잠금은 건마다 {@link #lockDue}) */
    public List<Due> due(Instant now, Due after, int limit) {
        BooleanExpression where = P.status.eq(PointPendingGrantEntity.PENDING).and(P.releaseAt.loe(now));
        if (after != null) {
            where = where.and(P.releaseAt.gt(after.releaseAt()).or(P.releaseAt.eq(after.releaseAt()).and(P.id.gt(after.id()))));
        }
        return query.select(Projections.constructor(Due.class, P.id, P.releaseAt)).from(P).where(where).orderBy(P.releaseAt.asc(), P.id.asc()).limit(limit).fetch();
    }

    /** 「실패 건만 다시」 — 그 ID 중 아직 확정할 차례인 대기 중 보류 */
    public List<Due> dueByIds(Instant now, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.select(Projections.constructor(Due.class, P.id, P.releaseAt)).from(P)
                .where(P.id.in(ids), P.status.eq(PointPendingGrantEntity.PENDING), P.releaseAt.loe(now)).orderBy(P.releaseAt.asc(), P.id.asc()).fetch();
    }

    /**
     * 확정할 보류 하나를 잠근다 — 대기 중이고 예정 시각이 지났을 때만. 다른 서버의 배치가 이미 잡았으면 기다리지 않고 비었다({@code SKIP LOCKED} — {@code PESSIMISTIC_WRITE} + 잠금
     * 대기 −2). 이 표를 FK 로 가리키는 표가 없어 표준 잠금({@code FOR NO KEY UPDATE})으로 결과가 같다(repository.md 「잠금 세기」). 트랜잭션의 이 엔티티 첫 읽기다.
     */
    public Optional<PointPendingGrantEntity> lockDue(long id, Instant now) {
        return Optional.ofNullable(query.selectFrom(P).where(P.id.eq(id), P.status.eq(PointPendingGrantEntity.PENDING), P.releaseAt.loe(now))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint(LOCK_TIMEOUT, -2).fetchFirst());
    }

    /** 회원의 대기 중 보류 — 확정 예정이 가까운 것부터(같으면 먼저 생긴 것) */
    public List<PointPendingGrantEntity> pendingOf(long userId) {
        return query.selectFrom(P).where(P.userId.eq(userId), P.status.eq(PointPendingGrantEntity.PENDING)).orderBy(P.releaseAt.asc(), P.id.asc()).fetch();
    }

    /** 그 시각에 확정·취소로 끝낸 보류 수 [확정, 취소] — 배치 한 번은 실행 시작 시각 하나로 끝낸다(결과 요약) */
    public long[] settledAt(Instant at) {
        long released = 0;
        long cancelled = 0;
        for (com.querydsl.core.Tuple t : query.select(P.status, P.id.count()).from(P).where(P.settledAt.eq(at)).groupBy(P.status).fetch()) {
            long n = t.get(1, Long.class);
            if (PointPendingGrantEntity.RELEASED.equals(t.get(0, String.class))) {
                released += n;
            } else if (PointPendingGrantEntity.CANCELLED.equals(t.get(0, String.class))) {
                cancelled += n;
            }
        }
        return new long[] {released, cancelled};
    }
}
