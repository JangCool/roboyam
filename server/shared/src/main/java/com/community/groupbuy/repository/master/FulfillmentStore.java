package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.CommerceAddressViewEntity;
import com.community.groupbuy.entity.GroupBuyShipmentEntity;
import com.community.groupbuy.entity.QCommerceCarrierEntity;
import com.community.groupbuy.entity.QGroupBuyEscrowJobEntity;
import com.community.groupbuy.entity.QGroupBuyRequestEntity;
import com.community.groupbuy.entity.QGroupBuyShipmentEntity;
import com.community.groupbuy.entity.RequestStatus;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 발송 쪽 표 — 택배사({@code commerce_carriers})·발송({@code group_buy_shipments})·에스크로 호출 대기열({@code group_buy_escrow_jobs})·배송지 열람 기록({@code commerce_address_views}).
 * 상태 바꾸기는 한 문장 조건부 UPDATE(이긴 쪽만).
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). 에스크로 일 집기({@link #claimEscrow})는 그 줄만 고르는 {@code SKIP LOCKED} 잠금 → 미루기 UPDATE → 읽기(같은 트랜잭션).
 * Native SQL 로 남긴 것과 이유: 택배사 넣기·고치기({@code INSERT … ON CONFLICT (code) DO UPDATE} — 코드가 키인 upsert), 에스크로 일 넣기
 * ({@code ON CONFLICT (shipment_id, kind) WHERE status = 'PENDING' DO NOTHING} — 부분 유일 색인이 서버 여러 대의 겹친 넣기를 한 번만 받는다).
 */
@Repository
public class FulfillmentStore extends GroupBuyStoreSupport {

    private static final QCommerceCarrierEntity CA = QCommerceCarrierEntity.commerceCarrierEntity;
    private static final QGroupBuyShipmentEntity SH = QGroupBuyShipmentEntity.groupBuyShipmentEntity;
    private static final QGroupBuyEscrowJobEntity J = QGroupBuyEscrowJobEntity.groupBuyEscrowJobEntity;
    private static final QGroupBuyRequestEntity RQ = QGroupBuyRequestEntity.groupBuyRequestEntity;

    public record CarrierRow(String code, String name, String trackingUrl, boolean enabled, int sortOrder) {
    }

    public record ShipmentRow(long id, long participationId, long groupBuyId, String kind, Long requestId, String carrierCode, String trackingNo, Instant shippedAt,
                              Instant deliveredAt, Boolean escrowApplicable, Instant escrowLogisticsAt, Instant escrowCompletedAt) {
    }

    public record EscrowJobRow(long id, long shipmentId, String kind, String status, int attempts, Instant nextAttemptAt, String lastError) {
    }

    private static final ConstructorExpression<ShipmentRow> SHIPMENT = Projections.constructor(ShipmentRow.class, SH.id, SH.participationId, SH.groupBuyId, SH.kind,
            SH.requestId, SH.carrierCode, SH.trackingNo, SH.shippedAt, SH.deliveredAt, SH.escrowApplicable, SH.escrowLogisticsAt, SH.escrowCompletedAt);
    private static final ConstructorExpression<EscrowJobRow> JOB = Projections.constructor(EscrowJobRow.class, J.id, J.shipmentId, J.kind, J.status, J.attempts,
            J.nextAttemptAt, J.lastError);

    public FulfillmentStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 택배사 ──────────────────────────────────────────────────────────────

    public List<CarrierRow> carriers() {
        return query.select(Projections.constructor(CarrierRow.class, CA.code, CA.name, CA.trackingUrl, CA.enabled, CA.sortOrder)).from(CA)
                .orderBy(CA.sortOrder.asc(), CA.code.asc()).fetch();
    }

    /** 넣거나 고친다 — Native SQL 인 이유: 코드가 키인 {@code ON CONFLICT (code) DO UPDATE} upsert */
    public void upsertCarrier(CarrierRow c, Long operatorId, Instant now) {
        jdbc.update("INSERT INTO commerce_carriers (code, name, tracking_url, enabled, sort_order, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET name = excluded.name, tracking_url = excluded.tracking_url, enabled = excluded.enabled, sort_order = excluded.sort_order, "
                        + "updated_at = excluded.updated_at, updated_by = excluded.updated_by", c.code(), c.name(), c.trackingUrl(), c.enabled(), c.sortOrder(), Timestamp.from(now),
                operatorId);
    }

    public void deleteCarrier(String code) {
        query.delete(CA).where(CA.code.eq(code)).execute();
    }

    /** 발송·회수 이력에 쓰인 택배사(지우지 못하고 끄기만 — 결정 11) */
    public boolean carrierInUse(String code) {
        return query.selectOne().from(SH).where(SH.carrierCode.eq(code)).fetchFirst() != null
                || query.selectOne().from(RQ).where(RQ.pickupCarrierCode.eq(code)).fetchFirst() != null;
    }

    // ── 발송 ────────────────────────────────────────────────────────────────

    public void insertShipment(long id, long participationId, long groupBuyId, String kind, Long requestId, String carrierCode, String trackingNo, Long operatorId,
                               Instant now) {
        insert(GroupBuyShipmentEntity.create(id, participationId, groupBuyId, kind, requestId, carrierCode, trackingNo, operatorId, now));
    }

    public Optional<ShipmentRow> original(long participationId) {
        return Optional.ofNullable(query.select(SHIPMENT).from(SH).where(SH.participationId.eq(participationId), SH.kind.eq("ORIGINAL")).fetchFirst());
    }

    public Optional<ShipmentRow> shipment(long id) {
        return Optional.ofNullable(query.select(SHIPMENT).from(SH).where(SH.id.eq(id)).fetchFirst());
    }

    public List<ShipmentRow> shipmentsOf(long participationId) {
        return query.select(SHIPMENT).from(SH).where(SH.participationId.eq(participationId)).orderBy(SH.shippedAt.asc(), SH.id.asc()).fetch();
    }

    public boolean correctTracking(long shipmentId, String carrierCode, String trackingNo, Instant now) {
        return query.update(SH).set(SH.carrierCode, carrierCode).set(SH.trackingNo, trackingNo).set(SH.updatedAt, now).where(SH.id.eq(shipmentId)).execute() == 1;
    }

    public void markDelivered(long participationId, Instant now) {
        query.update(SH).set(SH.deliveredAt, now).set(SH.updatedAt, now).where(SH.participationId.eq(participationId), SH.kind.eq("ORIGINAL"), SH.deliveredAt.isNull())
                .execute();
    }

    /** 에스크로 결과 기록(조건부 — 결정 64) */
    public void stampEscrow(long shipmentId, String kind, boolean applicable, Instant now) {
        if (!applicable) {
            query.update(SH).set(SH.escrowApplicable, false).set(SH.updatedAt, now).where(SH.id.eq(shipmentId)).execute();
        } else if ("LOGISTICS".equals(kind)) {
            query.update(SH).set(SH.escrowApplicable, true).set(SH.escrowLogisticsAt, SH.escrowLogisticsAt.coalesce(now)).set(SH.updatedAt, now).where(SH.id.eq(shipmentId))
                    .execute();
        } else {
            query.update(SH).set(SH.escrowApplicable, true).set(SH.escrowCompletedAt, now).set(SH.updatedAt, now)
                    .where(SH.id.eq(shipmentId), SH.escrowLogisticsAt.isNotNull(), SH.escrowCompletedAt.isNull()).execute();
        }
    }

    /**
     * 구매 확정 대상(결정 14) — 배송완료 뒤 {@code deliveredBefore} 이전이고, 배송 정보가 등록됐고, 주문에 열린 요청이 없는 처음 발송.
     */
    public List<Long> escrowCompleteDue(Instant deliveredBefore, int limit) {
        QGroupBuyRequestEntity open = new QGroupBuyRequestEntity("openRequest");
        QGroupBuyEscrowJobEntity job = new QGroupBuyEscrowJobEntity("completeJob");
        return query.select(SH.id).from(SH)
                .where(SH.kind.eq("ORIGINAL"), SH.deliveredAt.lt(deliveredBefore), SH.escrowLogisticsAt.isNotNull(), SH.escrowCompletedAt.isNull(),
                        SH.escrowApplicable.isNull().or(SH.escrowApplicable.isTrue()), // IS NOT false
                        JPAExpressions.selectOne().from(open).where(open.participationId.eq(SH.participationId), open.status.in(RequestStatus.OPEN)).notExists(),
                        JPAExpressions.selectOne().from(job).where(job.shipmentId.eq(SH.id), job.kind.eq("COMPLETE"), job.status.in("PENDING", "GAVE_UP")).notExists())
                .orderBy(SH.deliveredAt.asc(), SH.id.asc()).limit(limit).fetch();
    }

    // ── 에스크로 호출 대기열(결정 64) ───────────────────────────────────────

    /** 넣는다 — 같은 발송·종류의 끝나지 않은 일이 있으면 그 줄을 지금 다시 시도하게(송장 정정은 새 값을 그 줄이 보낸다) */
    public void enqueueEscrow(long id, long shipmentId, String kind, Instant now) {
        long updated = query.update(J).set(J.nextAttemptAt, now).where(J.shipmentId.eq(shipmentId), J.kind.eq(kind), J.status.eq("PENDING")).execute();
        if (updated == 0) {
            // Native SQL 인 이유: 부분 유일 색인 ux_gbej_pending 에 기대는 ON CONFLICT … DO NOTHING(겹친 넣기는 한 번만)
            jdbc.update("INSERT INTO group_buy_escrow_jobs (id, shipment_id, kind, next_attempt_at, created_at) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (shipment_id, kind) WHERE status = 'PENDING' DO NOTHING", id, shipmentId, kind, Timestamp.from(now), Timestamp.from(now));
        }
    }

    public List<Long> escrowDue(Instant now, int limit) {
        return query.select(J.id).from(J).where(J.status.eq("PENDING"), J.nextAttemptAt.loe(now)).orderBy(J.nextAttemptAt.asc(), J.id.asc()).limit(limit).fetch();
    }

    /**
     * 집는다 — 잠겨 있지 않고 차례가 됐으면 다음 시도를 {@code leaseUntil} 로 미뤄 다른 서버가 겹쳐 부르지 않게(호출은 트랜잭션 밖). 그 줄만 {@code SKIP LOCKED} 로 잠그고
     * (차례 조건 포함) 미룬 뒤 읽는다 — 잠근 트랜잭션이 끝날 때까지 다른 서버는 그 줄을 건너뛴다.
     */
    public Optional<EscrowJobRow> claimEscrow(long id, Instant now, Instant leaseUntil) {
        // 잠금 세기: PESSIMISTIC_WRITE = FOR NO KEY UPDATE SKIP LOCKED(전환 전 FOR UPDATE SKIP LOCKED). group_buy_escrow_jobs 를 FK 로 가리키는 표가 없어(KEY SHARE 를
        // 잡는 쪽이 없다) 건너뛰는 줄·막히는 쪽이 전환 전과 같다 — 그대로 둔다
        Long locked = query.select(J.id).from(J).where(J.id.eq(id), J.status.eq("PENDING"), J.nextAttemptAt.loe(now)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", -2).fetchFirst();
        if (locked == null) {
            return Optional.empty();
        }
        query.update(J).set(J.nextAttemptAt, leaseUntil).set(J.attempts, J.attempts.add(1)).where(J.id.eq(id)).execute();
        return Optional.ofNullable(query.select(JOB).from(J).where(J.id.eq(id)).fetchFirst());
    }

    public void finishEscrow(long id, String status, String error, Instant now) {
        query.update(J).set(J.status, status).set(J.lastError, error).set(J.completedAt, now).where(J.id.eq(id), J.status.eq("PENDING")).execute();
    }

    public void retryEscrow(long id, String error, Instant nextAttemptAt) {
        query.update(J).set(J.lastError, error).set(J.nextAttemptAt, nextAttemptAt).where(J.id.eq(id), J.status.eq("PENDING")).execute();
    }

    public List<EscrowJobRow> escrowJobs(long shipmentId) {
        return query.select(JOB).from(J).where(J.shipmentId.eq(shipmentId)).orderBy(J.createdAt.asc(), J.id.asc()).fetch();
    }

    // ── 배송지 열람 기록 ───────────────────────────────────────────────────

    public void insertAddressView(long id, String kind, Long participationId, long groupBuyId, long operatorId, String reason, Integer rowCount, Instant now) {
        insert(CommerceAddressViewEntity.create(id, kind, participationId, groupBuyId, operatorId, reason, rowCount, now));
    }
}
