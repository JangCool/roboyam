package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizPriceScheduleEntity;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.QBizPriceRuleEntity;
import com.community.bizapplication.entity.QBizPriceScheduleEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

/**
 * 요금표(V112 {@code biz_price_rules} — 상품 하나에 한 줄, 설치 기본값 + 신청 받기) + 가격 기간표(V208 {@code biz_price_schedules}, docs/features/ad-price-rules-contract.md).
 * QueryDSL 만 쓴다.
 *
 * <ul>
 *   <li><b>지금 값</b>({@link #all()}·{@link #find}) = 「적용 시각 ≤ 지금인 가장 늦은 기간표 줄, 없으면 요금표 줄 값」(규칙 2) — 배치 없이 적용 시각부터 정확하다. 기간표 줄은 값 넷을
 *       통째로 가지므로 가장 늦은 한 줄만 읽는다(상품·적용 시각 유일 색인). 신청 받기({@code accepting})는 예약하지 않으므로 늘 요금표 줄 값이다.</li>
 *   <li>적용 시각이 지난 기간표 줄은 지우지 않는다 — 「언제부터 얼마였나」 기록이자 「최근 변경」 원천. 고치기·취소는 아직 적용 전인 줄만(부르는 쪽이 검사).</li>
 *   <li><b>잠금</b> — 같은 상품을 동시에 고치지 못하게 요금표 줄을 잠근다({@link #lockRules}, {@code PESSIMISTIC_WRITE} — 여러 상품이면 상품 이름 차례).</li>
 * </ul>
 * 「지금」은 앱 시계({@link Clock} 빈이 있으면 그것 — 시험이 옮긴다).
 */
@Repository
public class BizPriceRuleStore extends IrobitRepositoryQDSL {

    private static final QBizPriceRuleEntity R = QBizPriceRuleEntity.bizPriceRuleEntity;
    private static final QBizPriceScheduleEntity S = QBizPriceScheduleEntity.bizPriceScheduleEntity;
    private static final QBizPriceScheduleEntity S2 = new QBizPriceScheduleEntity("s2");
    /** DB 의 현재 시각({@link #DB_NOW} 와 같은 식) — {@code Instant} 칸에 넣을 때 */
    private static final DateTimeExpression<Instant> DB_INSTANT = Expressions.dateTimeTemplate(Instant.class, "offset_datetime");

    /** 요금표 한 줄 — {@code capacity} 는 건당(POST)이면 null(한도 없음) */
    public record PriceRule(BizProduct product, boolean perPost, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, boolean accepting) {

        /** 예약 값 넷으로 바꾼 줄(받기·단위는 그대로, 건당이면 한도 null) */
        public PriceRule with(Schedule s) {
            return new PriceRule(product, perPost, s.unitPrice(), perPost ? null : s.capacity(), s.maxDays(), s.maxLeadDays(), accepting);
        }
    }

    /** 기간표 한 줄(「적용 시각부터 이 값」) — {@code updatedBy}·{@code updatedAt} 은 고친 적 없으면 null */
    public record Schedule(long id, BizProduct product, Instant effectiveAt, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, long createdBy,
                           Instant createdAt, Long updatedBy, Instant updatedAt) {

        /** 마지막으로 손댄 운영자 */
        public long lastEditor() {
            return updatedBy != null ? updatedBy : createdBy;
        }
    }

    private final EntityManager entityManager;
    private final Clock clock;

    public BizPriceRuleStore(JPAQueryFactory query, EntityManager entityManager, ObjectProvider<Clock> clocks) {
        super(query);
        this.entityManager = entityManager;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 읽기 ───────────────────────────────────────────────────────────────

    /** 지금 적용되는 값 */
    public Map<BizProduct, PriceRule> all() {
        return effectiveAt(clock.instant());
    }

    /** 그 순간에 적용되는 값 — 상품마다 적용 시각 ≤ {@code at} 인 가장 늦은 기간표 줄, 없으면 요금표 줄 값 */
    public Map<BizProduct, PriceRule> effectiveAt(Instant at) {
        Map<BizProduct, PriceRule> out = rows();
        for (Schedule s : latestUpTo(at).values()) {
            out.computeIfPresent(s.product(), (p, rule) -> rule.with(s));
        }
        return out;
    }

    /** 상품마다 적용 시각 ≤ {@code at} 인 가장 늦은 기간표 줄(없는 상품은 빠진다) */
    public Map<BizProduct, Schedule> latestUpTo(Instant at) {
        Map<BizProduct, Schedule> out = new EnumMap<>(BizProduct.class);
        for (Schedule s : schedules(S.effectiveAt.eq(JPAExpressions.select(S2.effectiveAt.max()).from(S2).where(S2.product.eq(S.product), S2.effectiveAt.loe(at))))) {
            out.put(s.product(), s);
        }
        return out;
    }

    public Optional<PriceRule> find(BizProduct product) {
        return Optional.ofNullable(all().get(product));
    }

    /** 요금표 줄 값 그대로(예약을 덮지 않음) */
    public Map<BizProduct, PriceRule> rows() {
        Map<BizProduct, PriceRule> out = new EnumMap<>(BizProduct.class);
        query.select(R.product, R.unit, R.unitPrice, R.capacity, R.maxDays, R.maxLeadDays, R.accepting).from(R).fetch().forEach(t -> {
            BizProduct p = t.get(R.product);
            out.put(p, new PriceRule(p, "POST".equals(t.get(R.unit)), t.get(R.unitPrice), t.get(R.capacity), t.get(R.maxDays), t.get(R.maxLeadDays),
                    Boolean.TRUE.equals(t.get(R.accepting))));
        });
        return out;
    }

    /** 아직 적용 전({@code effective_at > at})인 줄 전부 — 상품 · 적용 시각 차례 */
    public List<Schedule> pendingSchedules(Instant at) {
        return schedules(S.effectiveAt.gt(at));
    }

    /** 한 상품의 기간표 전부(지난 줄 포함) — 적용 시각 차례 */
    public List<Schedule> schedulesOf(BizProduct product) {
        return schedules(S.product.eq(product));
    }

    /** 아직 적용 전({@code effective_at > at})인 예약 중 상품마다 가장 가까운 하나 */
    public Map<BizProduct, Schedule> nextSchedules(Instant at) {
        Map<BizProduct, Schedule> out = new EnumMap<>(BizProduct.class);
        for (Schedule s : schedules(S.effectiveAt.gt(at))) {
            out.putIfAbsent(s.product(), s);
        }
        return out;
    }

    public Optional<Schedule> schedule(long id) {
        List<Schedule> found = schedules(S.id.eq(id));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private List<Schedule> schedules(com.querydsl.core.types.Predicate where) {
        List<Tuple> rows = query.select(S.id, S.product, S.effectiveAt, S.unitPrice, S.capacity, S.maxDays, S.maxLeadDays, S.createdBy, S.createdAt, S.updatedBy, S.updatedAt)
                .from(S).where(where).orderBy(S.product.asc(), S.effectiveAt.asc(), S.id.asc()).fetch();
        List<Schedule> out = new ArrayList<>(rows.size());
        for (Tuple t : rows) {
            out.add(new Schedule(t.get(S.id), t.get(S.product), t.get(S.effectiveAt), t.get(S.unitPrice), t.get(S.capacity), t.get(S.maxDays), t.get(S.maxLeadDays),
                    t.get(S.createdBy), t.get(S.createdAt), t.get(S.updatedBy), t.get(S.updatedAt)));
        }
        return out;
    }

    // ── 쓰기(트랜잭션은 부르는 쪽) ─────────────────────────────────────────

    /** 요금표 줄을 잠근다(상품 이름 차례 — 여러 요청이 서로 기다리기만 하고 엇갈려 막히지 않게). 트랜잭션 끝까지 */
    public void lockRules(Collection<BizProduct> products) {
        for (BizProduct product : products.stream().distinct().sorted(Comparator.comparing(BizProduct::name)).toList()) {
            query.select(R.product).from(R).where(R.product.eq(product)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst();
        }
    }

    public void insertSchedule(long id, BizProduct product, Instant effectiveAt, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, long operatorId,
                               Instant now) {
        JpaStoreWrites.insert(entityManager, BizPriceScheduleEntity.create(id, product, effectiveAt, unitPrice, capacity, maxDays, maxLeadDays, operatorId, now));
    }

    public void updateSchedule(long id, Instant effectiveAt, long unitPrice, Integer capacity, int maxDays, int maxLeadDays, long operatorId, Instant now) {
        query.update(S).set(S.effectiveAt, effectiveAt).set(S.unitPrice, unitPrice).set(S.capacity, capacity).set(S.maxDays, maxDays).set(S.maxLeadDays, maxLeadDays)
                .set(S.updatedBy, operatorId).set(S.updatedAt, now).where(S.id.eq(id)).execute();
    }

    public void deleteSchedules(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            query.delete(S).where(S.id.in(ids)).execute();
        }
    }

    /** 받기 켜기/끄기(관리자 화면) — 바뀐 시각·사람을 남긴다 */
    public void setAccepting(BizProduct product, boolean accepting, long operatorId, Instant now) {
        query.update(R).set(R.accepting, accepting).set(R.updatedAt, now).set(R.updatedBy, operatorId).where(R.product.eq(product)).execute();
    }

    /** 시험·운영 도구 — 받기 켜기/끄기(사람 기록 없음). 바뀐 시각은 DB 시각(예전 {@code now()}) */
    public void setAccepting(BizProduct product, boolean accepting) {
        query.update(R).set(R.accepting, accepting).set(R.updatedAt, DB_INSTANT).where(R.product.eq(product)).execute();
    }
}
