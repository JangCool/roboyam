package com.community.notification.repository.master;

import com.community.notification.entity.AdminNotificationEntity;
import com.community.notification.entity.QAdminNotificationEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 알림 서랍의 조회(운영자 본인 것만 — 모든 질의에 {@code operator_id} 조건이 있다)와 읽음 처리, 이벤트별 발송 수. 목록·미읽음 수·마지막 id 는 <b>같은 이벤트 거르기</b>({@code codes} — 내가 볼 수 있고
 * 끄지 않은 이벤트)를 받아 서로 어긋나지 않는다. {@code codes} 가 비어 있으면 아무것도 보이지 않는다. <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class AdminNotificationRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QAdminNotificationEntity NOTIFICATION = QAdminNotificationEntity.adminNotificationEntity;

    public AdminNotificationRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 최근순 한 쪽 — {@code beforeId} 보다 오래된 것부터 {@code limit} 개(다음 쪽 유무를 알려고 한 개 더 읽는 것은 부르는 쪽이 정한다) */
    public List<AdminNotificationEntity> page(long operatorId, Collection<String> codes, Long beforeId, int limit) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(NOTIFICATION)
                .where(NOTIFICATION.operatorId.eq(operatorId), NOTIFICATION.eventCode.in(codes), beforeId == null ? null : NOTIFICATION.id.lt(beforeId))
                .orderBy(NOTIFICATION.id.desc())
                .limit(limit)
                .fetch();
    }

    /** 안 읽은 수 */
    public long unread(long operatorId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return 0;
        }
        Long count = query.select(NOTIFICATION.count()).from(NOTIFICATION)
                .where(NOTIFICATION.operatorId.eq(operatorId), NOTIFICATION.eventCode.in(codes), unreadCondition()).fetchOne();
        return count == null ? 0 : count;
    }

    /** 가장 최근 알림의 id(읽음 여부와 상관없이). 없으면 null */
    public Long latestId(long operatorId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return null;
        }
        return query.select(NOTIFICATION.id.max()).from(NOTIFICATION).where(NOTIFICATION.operatorId.eq(operatorId), NOTIFICATION.eventCode.in(codes)).fetchOne();
    }

    /** 내 알림인가 */
    public boolean exists(long operatorId, long id) {
        return query.selectOne().from(NOTIFICATION).where(NOTIFICATION.id.eq(id), NOTIFICATION.operatorId.eq(operatorId)).fetchFirst() != null;
    }

    /** 읽음 처리 — 이미 읽었으면 그대로(처음 읽은 시각을 지킨다). 바뀐 줄 수 */
    public long markRead(long operatorId, long id, Instant now) {
        return query.update(NOTIFICATION).set(NOTIFICATION.readAt, now)
                .where(NOTIFICATION.id.eq(id), NOTIFICATION.operatorId.eq(operatorId), unreadCondition()).execute();
    }

    /** 내가 볼 수 있는 안 읽은 알림을 모두 읽음 처리한다. 바뀐 줄 수 */
    public long markAllRead(long operatorId, Collection<String> codes, Instant now) {
        if (codes.isEmpty()) {
            return 0;
        }
        return query.update(NOTIFICATION).set(NOTIFICATION.readAt, now)
                .where(NOTIFICATION.operatorId.eq(operatorId), NOTIFICATION.eventCode.in(codes), unreadCondition()).execute();
    }

    /** {@code since} 이후 만들어진 알림 수를 이벤트별로(수신자마다 한 줄이라 받은 사람 수가 합쳐진 값이다). 알림이 없는 이벤트는 결과에 없다 */
    public Map<String, Long> countByEventSince(Instant since) {
        Map<String, Long> counts = new HashMap<>();
        for (Tuple row : query.select(NOTIFICATION.eventCode, NOTIFICATION.count()).from(NOTIFICATION).where(NOTIFICATION.createdAt.goe(since))
                .groupBy(NOTIFICATION.eventCode).fetch()) {
            counts.put(row.get(NOTIFICATION.eventCode), row.get(NOTIFICATION.count()));
        }
        return counts;
    }

    private static BooleanExpression unreadCondition() {
        return NOTIFICATION.readAt.isNull();
    }
}
