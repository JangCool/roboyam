package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.QRewardEventDrawEntity;
import com.community.rewardevent.entity.QRewardEventEntity;
import com.community.rewardevent.entity.QRewardEventLineEntity;
import com.community.rewardevent.entity.QRewardEventRunEntity;
import com.community.rewardevent.entity.QRewardEventTierEntity;
import com.community.rewardevent.entity.RewardEventDrawEntity;
import com.community.rewardevent.entity.RewardEventEntity;
import com.community.rewardevent.entity.RewardEventLineEntity;
import com.community.rewardevent.entity.RewardEventMode;
import com.community.rewardevent.entity.RewardEventRunEntity;
import com.community.rewardevent.entity.RewardEventStatus;
import com.community.rewardevent.entity.RewardEventTierEntity;
import com.community.rewardevent.entity.RewardEventTrigger;
import com.community.rewardevent.entity.RewardRunKind;
import com.community.rewardevent.entity.RewardRunPhase;
import com.community.rewardevent.entity.RewardRunStatus;
import com.community.zconfiguration.jpa.Ilike;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 보상 이벤트·등수·보상 줄·추첨·지급 실행 읽기·쓰기 — {@code com.community.rewardevent.service} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <ul>
 *   <li>QueryDSL 이 기본이다. 이벤트 읽기는 엔티티를 찾자마자 문맥에서 뗀다({@link #event}) — 같은 트랜잭션의 조건부 UPDATE(상태 전이·선착순 인원)가 바꾼 값을 늘 DB 에서 다시 읽게.
 *       운영자의 고치기만 문맥에 붙은 엔티티를 쓰고({@link #eventForEdit}) 바로 내보낸 뒤 뗀다({@link #saveEdit}).</li>
 *   <li>상태 전이는 조건부 UPDATE({@code where status = …} 한 문장 — 1 이면 이겼다), 선착순 인원은 「한 문장 조건부 증가」({@link #takeFirstComeSlot}) — 동시에 와도 인원을 넘지 않는다.</li>
 *   <li>잠금: 이벤트 행은 쓰는 흐름의 첫 문장에서 {@link #lock}({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE}). 이 행을 외래 키로 가리키는 줄(등수·받는 사람·실행)은 이벤트를
 *       먼저 잠그거나(운영자 흐름) 이벤트 행을 조건부 UPDATE 한(선착순 — 같은 행 잠금) 흐름, 또는 실행 행을 잠근 덩어리 처리(일괄 대상 — 판정이 그 줄의 있고 없음을 보지 않는다)가
 *       넣으므로 표준 잠금으로 결과가 같다(repository.md 「잠금 세기」). 실행 집기는 {@link #claimRun}({@code SKIP LOCKED} — 서버 여러 대에서 한 곳만).</li>
 *   <li>Native SQL 로 남긴 것과 이유: 번호 시퀀스({@code nextval} — DB 함수) · 선착순 자동 지급의 날마다 실행 줄({@code INSERT … ON CONFLICT DO NOTHING} — 그리고 그 길은 인증 트랜잭션의
 *       JDBC 세이브포인트 안이다).</li>
 * </ul>
 */
@Repository
public class RewardEventStore extends IrobitRepositoryQDSL {

    /** 등수 한 줄 */
    public record TierRow(long id, long eventId, int position, String name, int quota) {
    }

    /** 보상 줄 한 줄 */
    public record LineRow(long id, long eventId, long tierId, int position, com.community.rewardevent.entity.RewardKind kind, Integer points, Long itemId, String itemName,
                          String itemKind, String itemUsage, Integer itemDurationDays, Integer quantity, Long packId, String packName, String manualName, String manualMethod) {

        /** 아이템 수(상점 아이템 수량 · 팩 1 · 그 밖 0) */
        public int itemCount() {
            return switch (kind) {
                case SHOP_ITEM -> quantity == null ? 1 : quantity;
                case EMOTICON_PACK -> 1;
                default -> 0;
            };
        }
    }

    /** 목록 거르기 */
    public record EventFilter(RewardEventStatus status, RewardEventMode mode, String q, Long no, Instant now) {
    }

    private static final QRewardEventEntity E = QRewardEventEntity.rewardEventEntity;
    private static final QRewardEventTierEntity T = QRewardEventTierEntity.rewardEventTierEntity;
    private static final QRewardEventLineEntity L = QRewardEventLineEntity.rewardEventLineEntity;
    private static final QRewardEventRunEntity RUN = QRewardEventRunEntity.rewardEventRunEntity;
    private static final QRewardEventDrawEntity D = QRewardEventDrawEntity.rewardEventDrawEntity;

    private final EntityManager entityManager;
    private final RewardEventRepository events;
    private final RewardEventTierRepository tiers;
    private final RewardEventLineRepository lines;
    private final RewardEventRunRepository runs;
    private final RewardEventDrawRepository draws;
    private final JdbcTemplate jdbc;

    public RewardEventStore(JPAQueryFactory query, EntityManager entityManager, RewardEventRepository events, RewardEventTierRepository tiers,
                            RewardEventLineRepository lines, RewardEventRunRepository runs, RewardEventDrawRepository draws, DataSource dataSource) {
        super(query);
        this.entityManager = entityManager;
        this.events = events;
        this.tiers = tiers;
        this.lines = lines;
        this.runs = runs;
        this.draws = draws;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 이벤트 ──────────────────────────────────────────────────────────────

    /** 다음 번호 — DB 시퀀스(nextval, DB 함수라 네이티브) */
    public long nextNo() {
        return ((Number) entityManager.createNativeQuery("SELECT nextval('reward_event_no_seq')").getSingleResult()).longValue();
    }

    public void insert(RewardEventEntity event) {
        events.persist(event);
        entityManager.flush();
        entityManager.detach(event);
    }

    /** 이벤트 한 건(문맥에서 뗀 사본 — 늘 DB 의 지금 값) */
    public Optional<RewardEventEntity> event(long id) {
        RewardEventEntity found = entityManager.find(RewardEventEntity.class, id);
        if (found != null) {
            entityManager.detach(found);
        }
        return Optional.ofNullable(found);
    }

    /** 고치기용 — 문맥에 붙은 엔티티. {@link #saveEdit} 로 끝낸다 */
    public Optional<RewardEventEntity> eventForEdit(long id) {
        return Optional.ofNullable(entityManager.find(RewardEventEntity.class, id));
    }

    public void saveEdit(RewardEventEntity event) {
        entityManager.flush();
        entityManager.detach(event);
    }

    /** 이벤트 행을 잠근다(쓰는 흐름의 첫 문장) — 없으면 false */
    public boolean lock(long id) {
        return query.select(E.id).from(E).where(E.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() != null;
    }

    /** 시작 — 준비이고 끝 시각 전일 때만 */
    public boolean start(long id, long operatorId, Instant now) {
        return query.update(E).set(E.status, RewardEventStatus.ACTIVE).set(E.updatedAt, now).set(E.updatedBy, operatorId)
                .where(E.id.eq(id), E.status.eq(RewardEventStatus.DRAFT), E.endAt.gt(now)).execute() == 1;
    }

    /** 운영자 마감 — 진행 중일 때만 */
    public boolean close(long id, long operatorId, Instant now) {
        return query.update(E).set(E.status, RewardEventStatus.CLOSED).set(E.closedAt, now).set(E.updatedAt, now).set(E.updatedBy, operatorId)
                .where(E.id.eq(id), E.status.eq(RewardEventStatus.ACTIVE)).execute() == 1;
    }

    /** 끝 시각이 지난 진행 중을 마감으로 고정(다음 쓰기 때 — 배치 없음) */
    public boolean closeExpired(long id, Instant now) {
        return query.update(E).set(E.status, RewardEventStatus.CLOSED).set(E.closedAt, E.endAt).set(E.updatedAt, now)
                .where(E.id.eq(id), E.status.eq(RewardEventStatus.ACTIVE), E.endAt.loe(now)).execute() == 1;
    }

    /** 선착순 인원이 다 찼으면 마감 */
    public boolean closeFull(long id, Instant now) {
        return query.update(E).set(E.status, RewardEventStatus.CLOSED).set(E.closedAt, now).set(E.updatedAt, now)
                .where(E.id.eq(id), E.status.eq(RewardEventStatus.ACTIVE), E.firstComeLimit.isNotNull(), E.firstComeCount.goe(E.firstComeLimit)).execute() == 1;
    }

    /** 일괄 지급을 진행 중에 시작하면 마감으로(지급 대상은 그 순간의 조건) */
    public boolean closeForGrant(long id, Instant now) {
        return query.update(E).set(E.status, RewardEventStatus.CLOSED).set(E.closedAt, now).set(E.updatedAt, now)
                .where(E.id.eq(id), E.status.eq(RewardEventStatus.ACTIVE)).execute() == 1;
    }

    /** 지급 끝 — 마감일 때만 */
    public boolean done(long id, Instant now) {
        return query.update(E).set(E.status, RewardEventStatus.DONE).set(E.doneAt, now).set(E.updatedAt, now)
                .where(E.id.eq(id), E.status.eq(RewardEventStatus.CLOSED)).execute() == 1;
    }

    /** 취소 — 준비·진행 중·마감일 때만 */
    public boolean cancel(long id, String reason, long operatorId, Instant now) {
        return query.update(E).set(E.status, RewardEventStatus.CANCELED).set(E.cancelReason, reason).set(E.canceledAt, now).set(E.canceledBy, operatorId)
                .set(E.updatedAt, now).set(E.updatedBy, operatorId)
                .where(E.id.eq(id), E.status.in(RewardEventStatus.DRAFT, RewardEventStatus.ACTIVE, RewardEventStatus.CLOSED)).execute() == 1;
    }

    /**
     * 선착순 자리 하나를 잡는다 — 한 문장 조건부 증가: 진행 중 · 기간 안 · 받은 수 &lt; 인원일 때만 받은 수 + 1. 같은 이벤트의 동시 지급은 이 행에서 줄을 서고(PostgreSQL 이 잠근 뒤 조건을 다시
     * 본다) 인원을 넘지 않는다(CHECK 가 끝까지 막는다). 잡았으면 true.
     */
    public boolean takeFirstComeSlot(long id, Instant now) {
        return query.update(E).set(E.firstComeCount, E.firstComeCount.add(1)).set(E.updatedAt, now)
                .where(E.id.eq(id), E.status.eq(RewardEventStatus.ACTIVE), E.mode.eq(RewardEventMode.FIRST_COME), E.startAt.loe(now), E.endAt.gt(now),
                        E.firstComeCount.lt(E.firstComeLimit)).execute() == 1;
    }

    /** 잡은 선착순 자리를 돌려준다(그 자리에 받는 사람을 넣지 못했다) */
    public void releaseFirstComeSlot(long id) {
        query.update(E).set(E.firstComeCount, E.firstComeCount.subtract(1)).where(E.id.eq(id), E.firstComeCount.gt(0)).execute();
    }

    /** 지금 받을 수 있는 선착순 이벤트(진행 중 · 기간 안 · 자리 남음 · 그 기준 사건) — 번호 차례 */
    public List<Long> openFirstCome(RewardEventTrigger trigger, Instant now) {
        return query.select(E.id).from(E).where(E.status.eq(RewardEventStatus.ACTIVE), E.mode.eq(RewardEventMode.FIRST_COME), E.firstComeTrigger.eq(trigger),
                E.startAt.loe(now), E.endAt.gt(now), E.firstComeCount.lt(E.firstComeLimit)).orderBy(E.no.asc()).fetch();
    }

    /** 목록 한 쪽 — 번호 내림차순. 상태 거르기는 읽을 때의 상태(끝 시각이 지난 진행 중 = 마감) */
    public List<RewardEventEntity> page(EventFilter f, int page, int size) {
        List<RewardEventEntity> found = query.selectFrom(E).where(where(f), statusWhere(f.status(), f.now())).orderBy(E.no.desc()).offset((long) page * size).limit(size).fetch();
        found.forEach(entityManager::detach);
        return found;
    }

    public long count(EventFilter f) {
        Long n = query.select(E.count()).from(E).where(where(f), statusWhere(f.status(), f.now())).fetchOne();
        return n == null ? 0 : n;
    }

    /** 검색어·방식을 적용한 상태별 건수(읽을 때의 상태) */
    public Map<RewardEventStatus, Long> counts(EventFilter f) {
        Map<RewardEventStatus, Long> out = new EnumMap<>(RewardEventStatus.class);
        for (RewardEventStatus s : RewardEventStatus.values()) {
            out.put(s, 0L);
        }
        for (Tuple t : query.select(E.status, E.count()).from(E).where(where(f), E.status.ne(RewardEventStatus.ACTIVE)).groupBy(E.status).fetch()) {
            out.put(t.get(E.status), t.get(E.count()));
        }
        Long active = query.select(E.count()).from(E).where(where(f), E.status.eq(RewardEventStatus.ACTIVE), E.endAt.gt(f.now())).fetchOne();
        Long expired = query.select(E.count()).from(E).where(where(f), E.status.eq(RewardEventStatus.ACTIVE), E.endAt.loe(f.now())).fetchOne();
        out.put(RewardEventStatus.ACTIVE, active == null ? 0 : active);
        out.merge(RewardEventStatus.CLOSED, expired == null ? 0 : expired, Long::sum);
        return out;
    }

    private static BooleanExpression where(EventFilter f) {
        BooleanExpression where = E.id.isNotNull();
        if (f.mode() != null) {
            where = where.and(E.mode.eq(f.mode()));
        }
        if (f.q() != null) {
            BooleanExpression text = Ilike.contains(E.name, f.q());
            where = where.and(f.no() != null ? text.or(E.no.eq(f.no())) : text);
        }
        return where;
    }

    private static BooleanExpression statusWhere(RewardEventStatus status, Instant now) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ACTIVE -> E.status.eq(RewardEventStatus.ACTIVE).and(E.endAt.gt(now));
            case CLOSED -> E.status.eq(RewardEventStatus.CLOSED).or(E.status.eq(RewardEventStatus.ACTIVE).and(E.endAt.loe(now)));
            default -> E.status.eq(status);
        };
    }

    // ── 등수 · 보상 줄 ───────────────────────────────────────────────────────

    public void insertTiers(Collection<RewardEventTierEntity> rows) {
        rows.forEach(tiers::persist);
        entityManager.flush();
        rows.forEach(entityManager::detach);
    }

    public void insertLines(Collection<RewardEventLineEntity> rows) {
        rows.forEach(lines::persist);
        entityManager.flush();
        rows.forEach(entityManager::detach);
    }

    public List<TierRow> tiers(long eventId) {
        return tiersOf(List.of(eventId)).getOrDefault(eventId, List.of());
    }

    public Map<Long, List<TierRow>> tiersOf(Collection<Long> eventIds) {
        Map<Long, List<TierRow>> out = new LinkedHashMap<>();
        if (eventIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(T.id, T.eventId, T.position, T.name, T.quota).from(T).where(T.eventId.in(eventIds)).orderBy(T.eventId.asc(), T.position.asc()).fetch()) {
            out.computeIfAbsent(t.get(T.eventId), k -> new java.util.ArrayList<>())
                    .add(new TierRow(t.get(T.id), t.get(T.eventId), t.get(T.position), t.get(T.name), t.get(T.quota)));
        }
        return out;
    }

    public List<LineRow> lines(long eventId) {
        return linesOf(List.of(eventId)).getOrDefault(eventId, List.of());
    }

    public Map<Long, List<LineRow>> linesOf(Collection<Long> eventIds) {
        Map<Long, List<LineRow>> out = new LinkedHashMap<>();
        if (eventIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(L.id, L.eventId, L.tierId, L.position, L.kind, L.points, L.itemId, L.itemName, L.itemKind, L.itemUsage, L.itemDurationDays, L.quantity,
                L.packId, L.packName, L.manualName, L.manualMethod).from(L).where(L.eventId.in(eventIds)).orderBy(L.eventId.asc(), L.tierId.asc(), L.position.asc()).fetch()) {
            Short q = t.get(L.quantity);
            out.computeIfAbsent(t.get(L.eventId), k -> new java.util.ArrayList<>())
                    .add(new LineRow(t.get(L.id), t.get(L.eventId), t.get(L.tierId), t.get(L.position), t.get(L.kind), t.get(L.points), t.get(L.itemId), t.get(L.itemName),
                            t.get(L.itemKind), t.get(L.itemUsage), t.get(L.itemDurationDays), q == null ? null : (int) q, t.get(L.packId), t.get(L.packName),
                            t.get(L.manualName), t.get(L.manualMethod)));
        }
        return out;
    }

    /** 등수 이름·인원·차례 고치기 */
    public void updateTier(long id, int position, String name, int quota) {
        query.update(T).set(T.position, (short) position).set(T.name, name).set(T.quota, quota).where(T.id.eq(id)).execute();
    }

    public void updateTierQuota(long id, int quota) {
        query.update(T).set(T.quota, quota).where(T.id.eq(id)).execute();
    }

    public void deleteTiers(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            query.delete(T).where(T.id.in(ids)).execute();
        }
    }

    public void deleteLines(long eventId) {
        query.delete(L).where(L.eventId.eq(eventId)).execute();
    }

    // ── 추첨 ────────────────────────────────────────────────────────────────

    public void insertDraw(RewardEventDrawEntity draw) {
        draws.persist(draw);
        entityManager.flush();
        entityManager.detach(draw);
    }

    /** 추첨 기록 — 최근 것이 위 */
    public List<RewardEventDrawEntity> draws(long eventId) {
        List<RewardEventDrawEntity> found = query.selectFrom(D).where(D.eventId.eq(eventId)).orderBy(D.id.desc()).fetch();
        found.forEach(entityManager::detach);
        return found;
    }

    // ── 지급 실행 ───────────────────────────────────────────────────────────

    public void insertRun(RewardEventRunEntity run) {
        runs.persist(run);
        entityManager.flush();
        entityManager.detach(run);
    }

    public Optional<RewardEventRunEntity> run(long id) {
        RewardEventRunEntity found = entityManager.find(RewardEventRunEntity.class, id);
        if (found != null) {
            entityManager.detach(found);
        }
        return Optional.ofNullable(found);
    }

    /** 이벤트의 실행 — 최근 것이 위 */
    public List<RewardEventRunEntity> runs(long eventId) {
        List<RewardEventRunEntity> found = query.selectFrom(RUN).where(RUN.eventId.eq(eventId)).orderBy(RUN.id.desc()).fetch();
        found.forEach(entityManager::detach);
        return found;
    }

    public Optional<RewardEventRunEntity> runByKey(long eventId, String key) {
        RewardEventRunEntity found = query.selectFrom(RUN).where(RUN.eventId.eq(eventId), RUN.idempotencyKey.eq(key)).fetchFirst();
        if (found != null) {
            entityManager.detach(found);
        }
        return Optional.ofNullable(found);
    }

    /** 나눠 처리 중인 실행(이벤트마다 하나 — 유일 색인) */
    public Optional<RewardEventRunEntity> runningRun(long eventId) {
        RewardEventRunEntity found = query.selectFrom(RUN).where(RUN.eventId.eq(eventId), RUN.status.eq(RewardRunStatus.RUNNING)).fetchFirst();
        if (found != null) {
            entityManager.detach(found);
        }
        return Optional.ofNullable(found);
    }

    /** 나눠 처리 중인 실행이 있는 이벤트들 */
    public java.util.Set<Long> runningEvents(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(query.select(RUN.eventId).from(RUN).where(RUN.eventId.in(eventIds), RUN.status.eq(RewardRunStatus.RUNNING)).fetch());
    }

    /** 지급이 있었나(운영자 지급 · 선착순 자동 — 취소를 막는다) */
    public java.util.Set<Long> grantedEvents(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(query.select(RUN.eventId).from(RUN).where(RUN.eventId.in(eventIds), RUN.kind.in(RewardRunKind.GRANT, RewardRunKind.AUTO)).fetch());
    }

    /** 집을 실행들(오래된 것부터) */
    public List<Long> runningIds(int limit) {
        return query.select(RUN.id).from(RUN).where(RUN.status.eq(RewardRunStatus.RUNNING)).orderBy(RUN.id.asc()).limit(limit).fetch();
    }

    /**
     * 실행 행을 잡는다 — {@code FOR NO KEY UPDATE SKIP LOCKED}(표준 잠금 + 힌트 lock.timeout = -2). 다른 서버·요청이 이미 잡고 있거나 끝난 실행이면 false. 잡은 트랜잭션이 끝날 때까지
     * 다른 곳은 이 실행을 처리하지 않는다(서버 여러 대여도 한 곳만 — 서버가 죽으면 잠금이 풀려 다른 곳이 이어 한다).
     */
    public boolean claimRun(long id) {
        return query.select(RUN.id).from(RUN).where(RUN.id.eq(id), RUN.status.eq(RewardRunStatus.RUNNING)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", -2).fetchFirst() != null;
    }

    /** 일괄 대상 만들기 진행 — 회원 번호 cursor(다음 덩어리는 그 뒤부터) */
    public void advanceTargets(long id, long cursor, Instant now) {
        query.update(RUN).set(RUN.cursorUserId, cursor).set(RUN.updatedAt, now).where(RUN.id.eq(id)).execute();
    }

    /** 대상 만들기 끝 — 보상 지급 단계로, 대상 수는 실제로 만든 수(시작 때 넣은 값은 미리 센 수) */
    public void targetsDone(long id, int total, Instant now) {
        query.update(RUN).set(RUN.phase, RewardRunPhase.LINES).set(RUN.targetCount, total).set(RUN.updatedAt, now).where(RUN.id.eq(id)).execute();
    }

    /** 덩어리 하나의 합계를 더한다 */
    public void addProgress(long id, int done, int success, int failed, int skipped, long points, int items, Instant now) {
        query.update(RUN).set(RUN.doneCount, RUN.doneCount.add(done)).set(RUN.success, RUN.success.add(success)).set(RUN.failed, RUN.failed.add(failed))
                .set(RUN.skipped, RUN.skipped.add(skipped)).set(RUN.points, RUN.points.add(points)).set(RUN.items, RUN.items.add(items)).set(RUN.updatedAt, now)
                .where(RUN.id.eq(id)).execute();
    }

    /** 실행 끝 */
    public boolean finishRun(long id, Instant now) {
        return query.update(RUN).set(RUN.status, RewardRunStatus.DONE).set(RUN.finishedAt, now).set(RUN.updatedAt, now)
                .where(RUN.id.eq(id), RUN.status.eq(RewardRunStatus.RUNNING)).execute() == 1;
    }

    /**
     * 선착순 자동 지급의 그날 실행 줄 — 없으면 만들고(운영자 없음, 끝난 상태) 그 ID 를 준다. Native SQL 인 이유: {@code INSERT … ON CONFLICT (event_id, day) DO NOTHING}(upsert) —
     * 같은 날 동시 지급이 유일 위반으로 깨지지 않게(그리고 이 길은 인증 트랜잭션의 JDBC 세이브포인트 안이다).
     */
    public long autoRunOf(long eventId, LocalDate day, long newId, Instant now) {
        jdbc.update("INSERT INTO reward_event_runs (id, event_id, kind, status, phase, day, created_at, updated_at, finished_at) "
                        + "VALUES (?, ?, 'AUTO', 'DONE', 'LINES', ?, ?, ?, ?) ON CONFLICT (event_id, day) WHERE kind = 'AUTO' DO NOTHING", newId, eventId, java.sql.Date.valueOf(day),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        return jdbc.queryForObject("SELECT id FROM reward_event_runs WHERE event_id = ? AND day = ? AND kind = 'AUTO'", Long.class, eventId, java.sql.Date.valueOf(day));
    }

    /** 선착순 자동 지급 한 사람을 그날 실행에 더한다 */
    public void addAuto(long runId, int success, int failed, int skipped, long points, int items, Instant now) {
        query.update(RUN).set(RUN.targetCount, RUN.targetCount.add(1)).set(RUN.doneCount, RUN.doneCount.add(1)).set(RUN.success, RUN.success.add(success))
                .set(RUN.failed, RUN.failed.add(failed)).set(RUN.skipped, RUN.skipped.add(skipped)).set(RUN.points, RUN.points.add(points)).set(RUN.items, RUN.items.add(items))
                .set(RUN.updatedAt, now).set(RUN.finishedAt, now).where(RUN.id.eq(runId)).execute();
    }
}
