package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QCommerceCarrierEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyRequestEntity;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 관리자 커머스 설정·대시보드({@code CommerceGroupBuySettingsFacade})가 읽는 수 — Facade 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다(읽기만).
 *
 * <p>열린 요청의 기한 단계(2영업일 경고 · 3영업일 심각)는 예전에 모든 열린 요청을 읽어 자바로 셌다 — 지난 영업일 수는 시작일이 뒤로 갈수록 줄기만 하므로 단계마다 「시작 시각 &lt; 경계」
 * 수를 DB 에서 센다({@link #openRequestsStartedBefore}). 시작 = 취소는 요청한 시각, 반품은 돌려받은 시각(없으면 단계 없음).
 */
@Repository
public class CommerceGroupBuySettingsQueries extends RoboyamRepositoryQDSL {

    private static final QCommerceCarrierEntity CA = QCommerceCarrierEntity.commerceCarrierEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyRequestEntity RQ = QGroupBuyRequestEntity.groupBuyRequestEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;

    /** 택배사 표를 마지막으로 고친 시각·운영자 */
    public record CarrierEdit(Instant updatedAt, String operatorName) {
    }

    public CommerceGroupBuySettingsQueries(JPAQueryFactory query) {
        super(query);
    }

    public Optional<CarrierEdit> lastCarrierEdit() {
        Tuple t = query.select(CA.updatedAt, O.operatorName).from(CA).leftJoin(O).on(O.id.eq(CA.updatedBy)).where(CA.updatedAt.isNotNull()).orderBy(CA.updatedAt.desc())
                .fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new CarrierEdit(t.get(CA.updatedAt), t.get(O.operatorName)));
    }

    /** 발주 대기(결제 확인됨 — CONFIRMED) 주문 수 */
    public long confirmedOrders() {
        return n(query.select(PA.count()).from(PA).where(PA.status.eq(ParticipationStatus.CONFIRMED)).fetchOne());
    }

    private static BooleanExpression openRequest() {
        return RQ.status.in(RequestStatus.REQUESTED, RequestStatus.APPROVED, RequestStatus.RECEIVED);
    }

    /** 열린 요청(요청됨·승인·회수 확인) 수 */
    public long openRequests() {
        return n(query.select(RQ.count()).from(RQ).where(openRequest()).fetchOne());
    }

    /** 열린 요청 중 시작 시각(취소 = 요청 시각, 반품 = 돌려받은 시각)이 {@code before} 보다 앞인 수 */
    public long openRequestsStartedBefore(Instant before) {
        BooleanExpression started = RQ.type.eq(RequestType.CANCEL).and(RQ.createdAt.lt(before)).or(RQ.type.ne(RequestType.CANCEL).and(RQ.receivedAt.lt(before)));
        return n(query.select(RQ.count()).from(RQ).where(openRequest(), started).fetchOne());
    }

    /** 곧 풀리는 선점 — 결제 대기 중 선점 끝이 {@code (now, until]} */
    public long holdsEndingBy(Instant now, Instant until) {
        return n(query.select(PA.count()).from(PA).where(PA.status.eq(ParticipationStatus.PENDING_PAYMENT), PA.reservedUntil.gt(now), PA.reservedUntil.loe(until)).fetchOne());
    }

    /** 모집 중인데 남은 수량이 5개 이하이거나 최대의 10% 이하인 공구 수 */
    public long lowStockGroupBuys() {
        NumberExpression<Integer> remaining = G.maxQty.subtract(G.currentQty).subtract(G.reservedQty);
        return n(query.select(G.count()).from(G).where(G.status.eq(GroupBuyStatus.RECRUITING), remaining.loe(5).or(remaining.multiply(10).loe(G.maxQty))).fetchOne());
    }

    /** 발송한 지 오래된(그 시각 전에 발송한) 배송중 주문 수 */
    public long shippedBefore(Instant before) {
        return n(query.select(PA.count()).from(PA).where(PA.status.eq(ParticipationStatus.SHIPPED), PA.shippedAt.lt(before)).fetchOne());
    }

    static long n(Long value) {
        return value == null ? 0 : value;
    }
}
