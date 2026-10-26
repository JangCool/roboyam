package com.community.group.repository.master;

import com.community.group.entity.GroupEventEntity;
import com.community.group.entity.GroupEventResponseType;
import com.community.group.entity.GroupEventStatus;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.QGroupEventEntity;
import com.community.group.entity.QGroupEventResponseEntity;
import com.community.group.entity.QGroupMemberEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
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
 * 소모임 일정 질의(V93 — community api-groups.md 9절) — 다가오는/지난 일정, 다음 일정, 참석 집계·참석자. 「지금」은 부르는 쪽이 넘기고 「끝났나」는 {@code closes_at} 한 조건이다.
 *
 * <p>집계·참석자는 <b>지금 멤버(ACTIVE)이고 탈퇴하지 않은 회원의 응답만</b> 센다 — 나가거나 강퇴된 멤버의 응답은 남아 있어도 빠지고 다시 가입하면 돌아온다
 * (원본은 서비스 탈퇴 때 응답을 지운다 — 로보얌은 탈퇴 정리가 아직 없어 읽을 때 뺀다). 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class GroupEventQueries extends RoboyamRepositoryQDSL {

    /** 한 일정의 응답별 인원 */
    public record Counts(int going, int notGoing, int maybe) {
    }

    /** 참석자 한 줄 — 회원과 응답 시각 */
    public record Attendee(long userId, Instant respondedAt) {
    }

    private static final QGroupEventEntity E = QGroupEventEntity.groupEventEntity;
    private static final QGroupEventResponseEntity R = QGroupEventResponseEntity.groupEventResponseEntity;
    private static final QGroupMemberEntity M = QGroupMemberEntity.groupMemberEntity;
    private static final QUserEntity U = QUserEntity.userEntity;

    public GroupEventQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 취소되지 않았고 아직 끝나지 않은(시작 전·진행 중) 일정 */
    private static BooleanExpression open(Instant now) {
        return E.status.eq(GroupEventStatus.ACTIVE).and(E.closesAt.gt(now));
    }

    /** 응답한 사람이 그 소모임의 지금 멤버이고 탈퇴하지 않았다 */
    private static BooleanExpression countable(long groupId) {
        return M.groupId.eq(groupId).and(M.userId.eq(R.userId)).and(M.status.eq(GroupMemberStatus.ACTIVE))
                .and(U.id.eq(R.userId)).and(U.status.ne(UserStatus.WITHDRAWN)).and(U.deletedAt.isNull());
    }

    /** 다가오는 일정(시작 전·진행 중) — 가까운 순 */
    public List<GroupEventEntity> upcoming(long groupId, Instant now, long offset, int limit) {
        return query.selectFrom(E).where(E.groupId.eq(groupId), open(now)).orderBy(E.startsAt.asc(), E.id.asc()).offset(offset).limit(limit).fetch();
    }

    public long countUpcoming(long groupId, Instant now) {
        Long count = query.select(E.count()).from(E).where(E.groupId.eq(groupId), open(now)).fetchOne();
        return count == null ? 0 : count;
    }

    /** 지난 일정(끝났거나 취소됨) — 최근 순 */
    public List<GroupEventEntity> past(long groupId, Instant now, long offset, int limit) {
        return query.selectFrom(E).where(E.groupId.eq(groupId), open(now).not()).orderBy(E.startsAt.desc(), E.id.desc()).offset(offset).limit(limit).fetch();
    }

    public long countPast(long groupId, Instant now) {
        Long count = query.select(E.count()).from(E).where(E.groupId.eq(groupId), open(now).not()).fetchOne();
        return count == null ? 0 : count;
    }

    /** 각 소모임의 가장 가까운 「시작 전」 일정(취소·시작한 일정 제외) — 내 소모임의 다음 일정. 키는 소모임 ID */
    public Map<Long, GroupEventEntity> nextEvents(Collection<Long> groupIds, Instant now) {
        Map<Long, GroupEventEntity> result = new HashMap<>();
        if (groupIds.isEmpty()) {
            return result;
        }
        for (GroupEventEntity e : query.selectFrom(E).where(E.groupId.in(groupIds), E.status.eq(GroupEventStatus.ACTIVE), E.startsAt.gt(now))
                .orderBy(E.startsAt.asc(), E.id.asc()).fetch()) {
            result.putIfAbsent(e.getGroupId(), e);
        }
        return result;
    }

    // ── 참석 ──

    /** 이 일정들에 대한 내 응답 — 키는 일정 ID */
    public Map<Long, GroupEventResponseType> myResponses(long userId, Collection<Long> eventIds) {
        Map<Long, GroupEventResponseType> result = new HashMap<>();
        if (eventIds.isEmpty()) {
            return result;
        }
        for (Tuple t : query.select(R.eventId, R.response).from(R).where(R.userId.eq(userId), R.eventId.in(eventIds)).fetch()) {
            result.put(t.get(R.eventId), t.get(R.response));
        }
        return result;
    }

    /** 이 일정들의 참석(GOING) 인원 — 지금 멤버만. 응답이 없는 일정은 빠진다. 키는 일정 ID */
    public Map<Long, Integer> goingCounts(long groupId, Collection<Long> eventIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (eventIds.isEmpty()) {
            return result;
        }
        for (Tuple t : query.select(R.eventId, R.count()).from(R, M, U)
                .where(countable(groupId), R.response.eq(GroupEventResponseType.GOING), R.eventId.in(eventIds))
                .groupBy(R.eventId).fetch()) {
            Long count = t.get(1, Long.class);
            result.put(t.get(R.eventId), count == null ? 0 : count.intValue());
        }
        return result;
    }

    /** 한 일정의 응답별 인원 — 지금 멤버만 */
    public Counts counts(long groupId, long eventId) {
        int going = 0;
        int notGoing = 0;
        int maybe = 0;
        for (Tuple t : query.select(R.response, R.count()).from(R, M, U).where(countable(groupId), R.eventId.eq(eventId)).groupBy(R.response).fetch()) {
            Long count = t.get(1, Long.class);
            int n = count == null ? 0 : count.intValue();
            GroupEventResponseType type = t.get(R.response);
            if (type == GroupEventResponseType.GOING) {
                going = n;
            } else if (type == GroupEventResponseType.NOT_GOING) {
                notGoing = n;
            } else if (type == GroupEventResponseType.MAYBE) {
                maybe = n;
            }
        }
        return new Counts(going, notGoing, maybe);
    }

    /** 참석자(GOING, 지금 멤버) — 응답 최신순 */
    public List<Attendee> attendees(long groupId, long eventId, long offset, int limit) {
        return query.select(R.userId, R.respondedAt).from(R, M, U)
                .where(countable(groupId), R.response.eq(GroupEventResponseType.GOING), R.eventId.eq(eventId))
                .orderBy(R.respondedAt.desc(), R.userId.desc()).offset(offset).limit(limit).fetch().stream()
                .map(t -> new Attendee(t.get(R.userId), t.get(R.respondedAt))).toList();
    }
}
