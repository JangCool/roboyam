package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.QShippingPolicyEntity;
import com.community.groupbuy.entity.ShippingPolicyEntity;
import com.community.operator.entity.QOperatorEntity;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 배송비 정책({@code shipping_policies}) — 정책 이름별 기간 줄(commerce.md 10-2 「배송비 정책과 예약 변경」). 기간 겹침은 DB 배제 제약, 적용이 시작된 줄의 불변은 DB 트리거가 막는다.
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). Native SQL 로 남긴 것과 이유: 같은 정책 이름의 예약·등록 줄 세우기({@code pg_advisory_xact_lock} — DB 함수).
 */
@Repository
public class ShippingPolicyStore extends GroupBuyStoreSupport {

    private static final QShippingPolicyEntity SP = QShippingPolicyEntity.shippingPolicyEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    /** 정책 줄 */
    public record PolicyRow(long id, String code, String name, long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode, Instant effectiveFrom,
                            Instant effectiveTo) {
    }

    private static final ConstructorExpression<PolicyRow> ROW = Projections.constructor(PolicyRow.class, SP.id, SP.code, SP.name, SP.baseFee, SP.freeShippingOver,
            SP.returnFee, SP.returnFeeMode, SP.effectiveFrom, SP.effectiveTo);

    public ShippingPolicyStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 그 시각에 유효한 줄 */
    public Optional<PolicyRow> effective(String code, Instant at) {
        return Optional.ofNullable(query.select(ROW).from(SP).where(SP.code.eq(code), SP.effectiveFrom.loe(at), SP.effectiveTo.isNull().or(SP.effectiveTo.gt(at)))
                .fetchFirst());
    }

    /** 같은 정책 이름의 줄 전부(시작 차례) */
    public List<PolicyRow> history(String code) {
        return query.select(ROW).from(SP).where(SP.code.eq(code)).orderBy(SP.effectiveFrom.asc()).fetch();
    }

    /** 모든 줄(정책 이름 · 시작 차례) — 설정 화면 */
    public List<PolicyRow> all() {
        return query.select(ROW).from(SP).orderBy(SP.code.asc(), SP.effectiveFrom.asc()).fetch();
    }

    public Optional<PolicyRow> find(long id) {
        return Optional.ofNullable(query.select(ROW).from(SP).where(SP.id.eq(id)).fetchFirst());
    }

    /** 아직 시작하지 않은 줄을 지운다(시작한 줄은 0 행 — 트리거도 막는다) */
    public boolean deleteNotStarted(long id, Instant now) {
        return query.delete(SP).where(SP.id.eq(id), SP.effectiveFrom.gt(now)).execute() == 1;
    }

    /** 지운 예약 바로 앞 줄의 끝을 다시 연다(그 예약 시작 시각에 닫혀 있던 줄) */
    public void reopen(String code, Instant closedAt) {
        query.update(SP).setNull(SP.effectiveTo).where(SP.code.eq(code), SP.effectiveTo.eq(closedAt)).execute();
    }

    /** 줄마다 만든 운영자 이름(지워진 운영자·만든 사람 없음은 null) */
    public Map<Long, String> creators() {
        Map<Long, String> out = new HashMap<>();
        query.select(SP.id, O.operatorName).from(SP).leftJoin(O).on(O.id.eq(SP.createdBy)).fetch().forEach(t -> out.put(t.get(SP.id), t.get(O.operatorName)));
        return out;
    }

    /** 줄의 만든 시각 */
    public Map<Long, Instant> createdAt() {
        Map<Long, Instant> out = new HashMap<>();
        query.select(SP.id, SP.createdAt).from(SP).fetch().forEach(t -> out.put(t.get(SP.id), t.get(SP.createdAt)));
        return out;
    }

    /** 같은 정책 이름의 예약·등록을 줄 세운다(트랜잭션 범위 어드바이저리 잠금 — Native SQL 인 이유: DB 함수) */
    public void lockCode(String code) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('shipping_policy:' || ?))", code);
    }

    public void insert(long id, String code, String name, long baseFee, Long freeShippingOver, long returnFee, String returnFeeMode, Instant from, Instant to,
                       Long operatorId, Instant now) {
        insert(ShippingPolicyEntity.create(id, code, name, baseFee, freeShippingOver, returnFee, returnFeeMode, from, to, operatorId, now));
    }

    /** 끝이 열린(또는 더 늦은) 줄을 그 시각에 닫는다 */
    public boolean close(long id, Instant at) {
        return query.update(SP).set(SP.effectiveTo, at).where(SP.id.eq(id), SP.effectiveTo.isNull().or(SP.effectiveTo.gt(at))).execute() == 1;
    }
}
