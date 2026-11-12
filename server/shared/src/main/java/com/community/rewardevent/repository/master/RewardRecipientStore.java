package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.QRewardEventLineResultEntity;
import com.community.rewardevent.entity.QRewardEventRecipientEntity;
import com.community.rewardevent.entity.RewardEventLineResultEntity;
import com.community.rewardevent.entity.RewardEventRecipientEntity;
import com.community.rewardevent.entity.RewardKind;
import com.community.rewardevent.entity.RewardLineReason;
import com.community.rewardevent.entity.RewardLineResultStatus;
import com.community.rewardevent.entity.RewardRecipientMethod;
import com.community.rewardevent.entity.RewardRecipientStatus;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.zconfiguration.jpa.Ilike;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 받는 사람·줄 결과 읽기·쓰기 — {@code com.community.rewardevent.service} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p><b>읽기는 한 줄 모양(프로젝션), 쓰기는 즉시 실행</b>이다 — 넣기는 {@code persist} 뒤 바로 내보내고 문맥에서 떼고, 상태·결과 칸은 QueryDSL DML 이다. 보상 줄 처리는 줄마다 JDBC
 * 세이브포인트 안에서 돌고 선착순 자동 지급은 인증 트랜잭션의 세이브포인트 안에서 돈다 — 문맥에 붙은 엔티티가 남아 세이브포인트를 되돌린 뒤에 내보내지는 일이 없게
 * ({@code GroupBuySavepoint} 의 약속과 같다). 이 저장소의 DML 은 0 줄로 지거나 CHECK 를 지키는 값만 쓴다(세이브포인트 안에서 실패하지 않게).
 */
@Repository
public class RewardRecipientStore extends IrobitRepositoryQDSL {

    /** 받는 사람 한 줄 */
    public record RecipientRow(long id, long eventId, long tierId, long userId, RewardRecipientMethod method, RewardRecipientStatus status, Instant selectedAt,
                               Long selectedBy, Long drawId, Long queuedRunId, String revokeReason) {
    }

    /** 줄 결과 한 줄 */
    public record ResultRow(long id, long recipientId, long lineId, long userId, RewardKind kind, RewardLineResultStatus status, RewardLineReason reason, Instant at,
                            int points, int items, Integer revokedPoints, Instant deliveredAt, Long deliveredBy, String deliveredMemo) {
    }

    /** 이벤트 숫자 재료 */
    public record StatsRow(long targets, long pending, long granted, long partial, long failed, long skipped, long revoked, long skippedLines, long grantedPoints,
                           long grantedItems, long manualPending, long manualDelivered) {

        public static final StatsRow EMPTY = new StatsRow(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** 받는 사람 목록 거르기 */
    public record RecipientFilter(long eventId, RewardRecipientStatus status, Long tierId, String q, Long qId) {
    }

    private static final QRewardEventRecipientEntity R = QRewardEventRecipientEntity.rewardEventRecipientEntity;
    private static final QRewardEventLineResultEntity X = QRewardEventLineResultEntity.rewardEventLineResultEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity P = QProfileEntity.profileEntity;

    private final EntityManager entityManager;
    private final RewardEventRecipientRepository recipients;
    private final RewardEventLineResultRepository results;

    public RewardRecipientStore(JPAQueryFactory query, EntityManager entityManager, RewardEventRecipientRepository recipients, RewardEventLineResultRepository results) {
        super(query);
        this.entityManager = entityManager;
        this.recipients = recipients;
        this.results = results;
    }

    // ── 넣기 · 지우기 ───────────────────────────────────────────────────────

    /** 받는 사람과 그 줄 결과(대기) — 넣고 바로 내보낸 뒤 문맥에서 뗀다. 같은 회원이 이미 있으면 유일 위반(부르는 쪽이 먼저 걸렀다) */
    public void insert(Collection<RewardEventRecipientEntity> newRecipients, Collection<RewardEventLineResultEntity> newResults) {
        newRecipients.forEach(recipients::persist);
        entityManager.flush();
        newResults.forEach(results::persist);
        entityManager.flush();
        newRecipients.forEach(entityManager::detach);
        newResults.forEach(entityManager::detach);
    }

    public void insertResults(Collection<RewardEventLineResultEntity> newResults) {
        newResults.forEach(results::persist);
        entityManager.flush();
        newResults.forEach(entityManager::detach);
    }

    /** 받는 사람 하나와 그 줄 결과를 지운다(지급 전 — 대기 줄만) */
    public void delete(long recipientId) {
        query.delete(X).where(X.recipientId.eq(recipientId)).execute();
        query.delete(R).where(R.id.eq(recipientId)).execute();
    }

    /** 그 등수들의 받는 사람을 지운다(준비 상태 고치기 — 빠진 등수) */
    public void deleteOfTiers(Collection<Long> tierIds) {
        if (tierIds.isEmpty()) {
            return;
        }
        query.delete(X).where(X.recipientId.in(JPAExpressions.select(R.id).from(R).where(R.tierId.in(tierIds)))).execute();
        query.delete(R).where(R.tierId.in(tierIds)).execute();
    }

    /** 이벤트의 줄 결과를 모두 지운다(준비 상태 고치기 — 보상 줄을 다시 만들기 전, 대기 줄뿐이다) */
    public void deleteResultsOf(long eventId) {
        query.delete(X).where(X.eventId.eq(eventId)).execute();
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    public boolean exists(long eventId, long userId) {
        return query.selectOne().from(R).where(R.eventId.eq(eventId), R.userId.eq(userId)).fetchFirst() != null;
    }

    /** 이 이벤트에 이미 있는 회원(고른 회원 중) */
    public Set<Long> existingUsers(long eventId, Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new java.util.HashSet<>(query.select(R.userId).from(R).where(R.eventId.eq(eventId), R.userId.in(userIds)).fetch());
    }

    public Optional<RecipientRow> recipient(long id) {
        return rows(R.id.eq(id)).stream().findFirst();
    }

    public List<RecipientRow> recipients(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : rows(R.id.in(ids));
    }

    /** 등수별 받는 사람 수 */
    public Map<Long, Long> countByTier(long eventId) {
        Map<Long, Long> out = new LinkedHashMap<>();
        for (Tuple t : query.select(R.tierId, R.count()).from(R).where(R.eventId.eq(eventId)).groupBy(R.tierId).fetch()) {
            out.put(t.get(R.tierId), t.get(R.count()));
        }
        return out;
    }

    /** 등수별 그 상태의 받는 사람 수 */
    public Map<Long, Long> countByTier(long eventId, RewardRecipientStatus status) {
        Map<Long, Long> out = new LinkedHashMap<>();
        for (Tuple t : query.select(R.tierId, R.count()).from(R).where(R.eventId.eq(eventId), R.status.eq(status)).groupBy(R.tierId).fetch()) {
            out.put(t.get(R.tierId), t.get(R.count()));
        }
        return out;
    }

    public long countByStatus(long eventId, RewardRecipientStatus... statuses) {
        Long n = query.select(R.count()).from(R).where(R.eventId.eq(eventId), R.status.in(statuses)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 받는 사람 목록 한 쪽 — 선정 시각 내림차순(같으면 번호 내림차순) */
    public List<RecipientRow> page(RecipientFilter f, int page, int size) {
        return query.select(R.id, R.eventId, R.tierId, R.userId, R.method, R.status, R.selectedAt, R.selectedBy, R.drawId, R.queuedRunId, R.revokeReason)
                .from(R).where(filter(f)).orderBy(R.selectedAt.desc(), R.id.desc()).offset((long) page * size).limit(size).fetch().stream().map(RewardRecipientStore::rowOf)
                .toList();
    }

    public long count(RecipientFilter f) {
        Long n = query.select(R.count()).from(R).where(filter(f)).fetchOne();
        return n == null ? 0 : n;
    }

    private static BooleanExpression filter(RecipientFilter f) {
        BooleanExpression where = R.eventId.eq(f.eventId());
        if (f.status() != null) {
            where = where.and(R.status.eq(f.status()));
        }
        if (f.tierId() != null) {
            where = where.and(R.tierId.eq(f.tierId()));
        }
        if (f.q() != null) {
            BooleanExpression member = JPAExpressions.selectOne().from(U).leftJoin(P).on(P.userId.eq(U.id))
                    .where(U.id.eq(R.userId), Ilike.contains(P.nickname, f.q()).or(Ilike.contains(U.email, f.q()))).exists();
            where = where.and(f.qId() != null ? member.or(R.userId.eq(f.qId())) : member);
        }
        return where;
    }

    /** 받는 사람들의 줄 결과 — 받는 사람 → (줄 → 결과) */
    public Map<Long, List<ResultRow>> resultsOf(Collection<Long> recipientIds) {
        Map<Long, List<ResultRow>> out = new HashMap<>();
        if (recipientIds.isEmpty()) {
            return out;
        }
        for (ResultRow row : resultRows(X.recipientId.in(recipientIds))) {
            out.computeIfAbsent(row.recipientId(), k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    public List<ResultRow> results(long recipientId) {
        return resultRows(X.recipientId.eq(recipientId));
    }

    private List<ResultRow> resultRows(BooleanExpression where) {
        return query.select(X.id, X.recipientId, X.lineId, X.userId, X.kind, X.status, X.reason, X.at, X.points, X.items, X.revokedPoints, X.deliveredAt, X.deliveredBy,
                X.deliveredMemo).from(X).where(where).orderBy(X.id.asc()).fetch().stream()
                .map(t -> new ResultRow(t.get(X.id), t.get(X.recipientId), t.get(X.lineId), t.get(X.userId), t.get(X.kind), t.get(X.status), t.get(X.reason), t.get(X.at),
                        t.get(X.points), t.get(X.items), t.get(X.revokedPoints), t.get(X.deliveredAt), t.get(X.deliveredBy), t.get(X.deliveredMemo)))
                .toList();
    }

    /** 이벤트들의 숫자 — 받는 사람 상태별 · 건너뛴 줄 · 지급 포인트(회수분 뺌) · 아이템 · 직접 전달(한 문장씩) */
    public Map<Long, StatsRow> stats(Collection<Long> eventIds) {
        Map<Long, StatsRow> out = new HashMap<>();
        if (eventIds.isEmpty()) {
            return out;
        }
        Map<Long, long[]> people = new HashMap<>();
        for (Tuple t : query.select(R.eventId, R.status, R.count()).from(R).where(R.eventId.in(eventIds)).groupBy(R.eventId, R.status).fetch()) {
            long[] c = people.computeIfAbsent(t.get(R.eventId), k -> new long[7]);
            long n = t.get(R.count());
            c[0] += n;
            c[1 + t.get(R.status).ordinal()] += n;
        }
        NumberExpression<Integer> skippedLine = new CaseBuilder().when(X.status.eq(RewardLineResultStatus.SKIPPED)).then(1).otherwise(0);
        NumberExpression<Integer> points = new CaseBuilder().when(X.status.eq(RewardLineResultStatus.SUCCESS)).then(X.points)
                .when(X.status.eq(RewardLineResultStatus.REVOKED)).then(X.points.subtract(X.revokedPoints.coalesce(0))).otherwise(0);
        NumberExpression<Integer> items = new CaseBuilder().when(X.status.eq(RewardLineResultStatus.SUCCESS)).then(X.items).otherwise(0);
        NumberExpression<Integer> manualPending = new CaseBuilder().when(X.status.eq(RewardLineResultStatus.DELIVERY_PENDING)).then(1).otherwise(0);
        NumberExpression<Integer> manualDone = new CaseBuilder().when(X.status.eq(RewardLineResultStatus.DELIVERED)).then(1).otherwise(0);
        NumberExpression<Long> sSkipped = sumLong(skippedLine);
        NumberExpression<Long> sPoints = sumLong(points);
        NumberExpression<Long> sItems = sumLong(items);
        NumberExpression<Long> sManualPending = sumLong(manualPending);
        NumberExpression<Long> sManualDone = sumLong(manualDone);
        Map<Long, long[]> lines = new HashMap<>();
        for (Tuple t : query.select(X.eventId, sSkipped, sPoints, sItems, sManualPending, sManualDone).from(X).where(X.eventId.in(eventIds)).groupBy(X.eventId).fetch()) {
            lines.put(t.get(X.eventId), new long[] {nz(t.get(sSkipped)), nz(t.get(sPoints)), nz(t.get(sItems)), nz(t.get(sManualPending)), nz(t.get(sManualDone))});
        }
        for (Long id : eventIds) {
            long[] c = people.getOrDefault(id, new long[7]);
            long[] l = lines.getOrDefault(id, new long[5]);
            // 받는 사람 상태 차례: PENDING GRANTED PARTIAL FAILED SKIPPED REVOKED
            out.put(id, new StatsRow(c[0], c[1], c[2], c[3], c[4], c[5], c[6], l[0], l[1], l[2], l[3], l[4]));
        }
        return out;
    }

    /** {@code sum(식)} — Hibernate 는 정수 합을 Long 으로 돌려준다 */
    private static NumberExpression<Long> sumLong(com.querydsl.core.types.Expression<? extends Number> value) {
        return com.querydsl.core.types.dsl.Expressions.numberTemplate(Long.class, "sum({0})", value);
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private List<RecipientRow> rows(BooleanExpression where) {
        return query.select(R.id, R.eventId, R.tierId, R.userId, R.method, R.status, R.selectedAt, R.selectedBy, R.drawId, R.queuedRunId, R.revokeReason).from(R).where(where)
                .orderBy(R.id.asc()).fetch().stream().map(RewardRecipientStore::rowOf).toList();
    }

    private static RecipientRow rowOf(Tuple t) {
        return new RecipientRow(t.get(R.id), t.get(R.eventId), t.get(R.tierId), t.get(R.userId), t.get(R.method), t.get(R.status), t.get(R.selectedAt), t.get(R.selectedBy),
                t.get(R.drawId), t.get(R.queuedRunId), t.get(R.revokeReason));
    }

    // ── 지급 대기열 ─────────────────────────────────────────────────────────

    /** 그 상태의 받는 사람을 그 실행에 올린다 — 올린 수 */
    public int enqueue(long eventId, long runId, Collection<RewardRecipientStatus> statuses) {
        return (int) query.update(R).set(R.queuedRunId, runId).where(R.eventId.eq(eventId), R.status.in(statuses), R.queuedRunId.isNull()).execute();
    }

    /** 그 실행의 다음 덩어리(번호 차례) */
    public List<RecipientRow> queued(long runId, int limit) {
        return query.select(R.id, R.eventId, R.tierId, R.userId, R.method, R.status, R.selectedAt, R.selectedBy, R.drawId, R.queuedRunId, R.revokeReason).from(R)
                .where(R.queuedRunId.eq(runId)).orderBy(R.id.asc()).limit(limit).fetch().stream().map(RewardRecipientStore::rowOf).toList();
    }

    public long countQueued(long runId) {
        Long n = query.select(R.count()).from(R).where(R.queuedRunId.eq(runId)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 처리한 받는 사람 — 상태를 고치고 대기열에서 내린다 */
    public void settle(long recipientId, RewardRecipientStatus status) {
        query.update(R).set(R.status, status).setNull(R.queuedRunId).where(R.id.eq(recipientId)).execute();
    }

    /** 회수 — 받는 사람 상태 REVOKED + 사유·시각·운영자 */
    public void revoke(long recipientId, String reason, long operatorId, Instant now) {
        query.update(R).set(R.status, RewardRecipientStatus.REVOKED).set(R.revokeReason, reason).set(R.revokedAt, now).set(R.revokedBy, operatorId)
                .where(R.id.eq(recipientId), R.status.ne(RewardRecipientStatus.REVOKED)).execute();
    }

    // ── 줄 결과 쓰기 ────────────────────────────────────────────────────────

    /** 지급 결과 — 대기·실패 줄만 고친다(성공 줄은 다시 지급하지 않는다). 고쳤으면 true */
    public boolean applyResult(long resultId, RewardLineResultStatus status, RewardLineReason reason, int points, int items, Instant at) {
        return query.update(X).set(X.status, status).set(X.reason, reason).set(X.points, points).set(X.items, items).set(X.at, at)
                .where(X.id.eq(resultId), X.status.in(RewardLineResultStatus.PENDING, RewardLineResultStatus.FAILED)).execute() == 1;
    }

    /** 회수 결과 — 성공·전달 대기 줄을 회수됨으로(회수한 포인트·덜 회수한 사유) */
    public void revokeResult(long resultId, Integer revokedPoints, RewardLineReason reason, Instant at) {
        query.update(X).set(X.status, RewardLineResultStatus.REVOKED).set(X.revokedPoints, revokedPoints).set(X.reason, reason).set(X.at, at)
                .where(X.id.eq(resultId), X.status.in(RewardLineResultStatus.SUCCESS, RewardLineResultStatus.DELIVERY_PENDING)).execute();
    }

    /** 회수하지 못한 줄에 사유만 남긴다(쓴 아이템 · 직접 전달 기록만) */
    public void markReason(long resultId, RewardLineReason reason, Instant at) {
        query.update(X).set(X.reason, reason).set(X.at, at).where(X.id.eq(resultId)).execute();
    }

    /** 직접 전달 완료 — 전달 대기일 때만. 고쳤으면 true */
    public boolean deliver(long resultId, long operatorId, String memo, Instant now) {
        return query.update(X).set(X.status, RewardLineResultStatus.DELIVERED).set(X.deliveredAt, now).set(X.deliveredBy, operatorId).set(X.deliveredMemo, memo)
                .where(X.id.eq(resultId), X.status.eq(RewardLineResultStatus.DELIVERY_PENDING)).execute() == 1;
    }
}
