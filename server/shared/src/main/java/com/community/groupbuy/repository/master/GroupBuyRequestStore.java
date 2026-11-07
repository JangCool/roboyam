package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.GroupBuyRequestEntity;
import com.community.groupbuy.entity.GroupBuyRequestItemEntity;
import com.community.groupbuy.entity.GroupBuyRequestPhotoEntity;
import com.community.groupbuy.entity.GroupBuyReturnDispositionEntity;
import com.community.groupbuy.entity.GroupBuySupplyFailureEntity;
import com.community.groupbuy.entity.QGroupBuyRequestEntity;
import com.community.groupbuy.entity.QGroupBuyRequestItemEntity;
import com.community.groupbuy.entity.QGroupBuyRequestPhotoEntity;
import com.community.groupbuy.entity.QGroupBuySupplyChoiceEntity;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.entity.SupplyChoice;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 취소·반품 요청({@code group_buy_requests})·요청 줄·처리 방법·사진, 단품 공급 불가·회원 선택(결정 53). 상태 전이는 한 문장 조건부 UPDATE(이긴 쪽만).
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). 요청 행 잠금은 요청 행만 고르는 잠금 문장 뒤에 읽는다.
 * Native SQL 로 남긴 것과 이유: 오래된 붙지 않은 사진 지우기({@code DELETE … RETURNING image_key} — 지운 줄의 파일 키를 같은 문장에서 받아 저장소를 정리한다),
 * 회원 선택 줄 넣기({@code ON CONFLICT (supply_failure_id, participation_id) DO NOTHING} — 다시 돌려도 한 번).
 */
@Repository
public class GroupBuyRequestStore extends GroupBuyStoreSupport {

    private static final QGroupBuyRequestEntity RQ = QGroupBuyRequestEntity.groupBuyRequestEntity;
    private static final QGroupBuyRequestItemEntity RI = QGroupBuyRequestItemEntity.groupBuyRequestItemEntity;
    private static final QGroupBuyRequestPhotoEntity PH = QGroupBuyRequestPhotoEntity.groupBuyRequestPhotoEntity;
    private static final QGroupBuySupplyChoiceEntity CH = QGroupBuySupplyChoiceEntity.groupBuySupplyChoiceEntity;

    public record RequestRow(long id, long participationId, long groupBuyId, long userId, RequestType type, RequestStatus status, CollectionMethod method,
                             Resolution resolution, boolean sellerFault, String memo, long previewAmount, Long feePaymentOrderId, long feeAmount, long feePaidAmount,
                             long feeRefundedAmount, Instant closesAt, Instant receivedAt, long compensationAmount, String rejectReasonCode, Instant createdAt,
                             LocalDate defectKnownOn) {

        /** 낸 반품비 중 아직 돌려주지 않은 몫 */
        public long feeHeld() {
            return feePaidAmount - feeRefundedAmount;
        }
    }

    public record ItemRow(long id, long requestId, long participationItemId, long skuId, int qty, RequestReason reason, Long refundAmount, long priorRefundOffset) {
    }

    public record NewRequest(long id, long participationId, long groupBuyId, long userId, RequestType type, RequestStatus status, CollectionMethod method,
                             Resolution resolution, boolean sellerFault, String memo, long previewAmount, long feeAmount, Instant closesAt, LocalDate defectKnownOn,
                             Instant now) {
    }

    public record SupplyFailureRow(long id, long groupBuyId, long skuId, String reason, Instant choiceUntil) {
    }

    public record SupplyChoiceRow(long id, long supplyFailureId, long participationId, long userId, SupplyChoice choice, Instant deadline, boolean defaulted) {
    }

    private static final ConstructorExpression<RequestRow> ROW = Projections.constructor(RequestRow.class, RQ.id, RQ.participationId, RQ.groupBuyId, RQ.userId, RQ.type,
            RQ.status, RQ.collectionMethod, RQ.resolution, RQ.sellerFault, RQ.memo, RQ.previewAmount, RQ.feePaymentOrderId, RQ.feeAmount, RQ.feePaidAmount,
            RQ.feeRefundedAmount, RQ.closesAt, RQ.receivedAt, RQ.compensationAmount, RQ.rejectReasonCode, RQ.createdAt, RQ.defectKnownOn);
    private static final ConstructorExpression<SupplyChoiceRow> CHOICE = Projections.constructor(SupplyChoiceRow.class, CH.id, CH.supplyFailureId, CH.participationId,
            CH.userId, CH.choice, CH.deadline, CH.defaulted);

    public GroupBuyRequestStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 요청 ────────────────────────────────────────────────────────────────

    public void insert(NewRequest r) {
        insert(GroupBuyRequestEntity.create(r.id(), r.participationId(), r.groupBuyId(), r.userId(), r.type(), r.status(), r.method(), r.resolution(), r.sellerFault(),
                r.memo(), r.previewAmount(), r.feeAmount(), r.closesAt(), r.defectKnownOn(), r.now()));
    }

    public void insertItem(long id, long requestId, long participationItemId, long skuId, int qty, RequestReason reason) {
        insert(GroupBuyRequestItemEntity.create(id, requestId, participationItemId, skuId, qty, reason));
    }

    private JPAQuery<RequestRow> rows() {
        return query.select(ROW).from(RQ);
    }

    public Optional<RequestRow> find(long id) {
        return Optional.ofNullable(rows().where(RQ.id.eq(id)).fetchFirst());
    }

    /** 잠그고 읽는다 — 요청 처리는 주문 행 다음(주문 행 → 요청 행 → 수량 → 결제 행) */
    public Optional<RequestRow> lock(long id) {
        return RowLocks.forUpdate(entityManager, GroupBuyRequestEntity.class, id) ? find(id) : Optional.empty();
    }

    public List<RequestRow> openOf(long participationId) {
        return rows().where(RQ.participationId.eq(participationId), RQ.status.in(RequestStatus.OPEN)).orderBy(RQ.id.asc()).fetch();
    }

    public boolean hasOpen(long participationId) {
        return query.selectOne().from(RQ).where(RQ.participationId.eq(participationId), RQ.status.in(RequestStatus.OPEN)).fetchFirst() != null;
    }

    public List<RequestRow> ofParticipation(long participationId) {
        return rows().where(RQ.participationId.eq(participationId)).orderBy(RQ.id.asc()).fetch();
    }

    public List<ItemRow> items(long requestId) {
        return query.select(Projections.constructor(ItemRow.class, RI.id, RI.requestId, RI.participationItemId, RI.skuId, RI.qty, RI.reason, RI.refundAmount,
                RI.priorRefundOffset)).from(RI).where(RI.requestId.eq(requestId)).orderBy(RI.skuId.asc()).fetch();
    }

    /** 상태를 바꾼다(지금 상태가 {@code from} 중 하나일 때만 — 이긴 쪽만 true) */
    public boolean transition(long id, Set<RequestStatus> from, RequestStatus to, Long operatorId, Instant now) {
        JPAUpdateClause update = query.update(RQ).set(RQ.status, to).set(RQ.updatedAt, now);
        if (operatorId != null) {
            // 결정(승인·거절)은 처음 한 번만 — 뒤의 회수·완료 같은 전이가 승인 시각·승인한 사람을 덮지 않는다(이력 순서가 뒤집혔다, 2b-5 확인)
            update.set(RQ.decidedBy, RQ.decidedBy.coalesce(operatorId)).set(RQ.decidedAt, RQ.decidedAt.coalesce(now));
        }
        if (to == RequestStatus.COMPLETED) {
            update.set(RQ.completedAt, now);
        }
        return update.where(RQ.id.eq(id), RQ.status.in(from)).execute() == 1;
    }

    public boolean reject(long id, Set<RequestStatus> from, String reasonCode, String note, long operatorId, Instant now) {
        return query.update(RQ).set(RQ.status, RequestStatus.REJECTED).set(RQ.rejectReasonCode, reasonCode).set(RQ.rejectNote, note).set(RQ.decidedBy, operatorId)
                .set(RQ.decidedAt, now).set(RQ.updatedAt, now).where(RQ.id.eq(id), RQ.status.in(from)).execute() == 1;
    }

    public void attachFeePayment(long id, long paymentOrderId, Instant now) {
        query.update(RQ).set(RQ.feePaymentOrderId, paymentOrderId).set(RQ.updatedAt, now).where(RQ.id.eq(id)).execute();
    }

    /** 부족분 결제 확정 → 접수(AWAITING_FEE 이고 닫히기 전이고 금액이 같을 때만 — 결정 68) */
    public boolean acceptFee(long id, long paymentOrderId, long amount, Instant now) {
        return query.update(RQ).set(RQ.status, RequestStatus.REQUESTED).set(RQ.feePaidAmount, amount).set(RQ.feePaymentOrderId, paymentOrderId).set(RQ.updatedAt, now)
                .where(RQ.id.eq(id), RQ.status.eq(RequestStatus.AWAITING_FEE), RQ.feeAmount.eq(amount), RQ.closesAt.gt(now)).execute() == 1;
    }

    /** 닫을 때가 된 부족분 결제 전 요청 */
    public List<Long> awaitingFeeDue(Instant now, int limit) {
        return query.select(RQ.id).from(RQ).where(RQ.status.eq(RequestStatus.AWAITING_FEE), RQ.closesAt.loe(now)).orderBy(RQ.closesAt.asc(), RQ.id.asc()).limit(limit)
                .fetch();
    }

    public void setPickup(long id, String carrierCode, String trackingNo, Instant now) {
        query.update(RQ).set(RQ.pickupCarrierCode, carrierCode).set(RQ.pickupTrackingNo, trackingNo).set(RQ.updatedAt, now).where(RQ.id.eq(id)).execute();
    }

    public boolean markReceived(long id, Instant now) {
        return query.update(RQ).set(RQ.status, RequestStatus.RECEIVED).set(RQ.receivedAt, now).set(RQ.updatedAt, now)
                .where(RQ.id.eq(id), RQ.status.in(RequestStatus.REQUESTED, RequestStatus.APPROVED)).execute() == 1;
    }

    public void setCompensation(long id, long amount, Instant now) {
        query.update(RQ).set(RQ.compensationAmount, amount).set(RQ.updatedAt, now).where(RQ.id.eq(id)).execute();
    }

    public void setPreview(long id, long amount, Instant now) {
        query.update(RQ).set(RQ.previewAmount, amount).set(RQ.updatedAt, now).where(RQ.id.eq(id)).execute();
    }

    public boolean toRefund(long id, Instant now) {
        return query.update(RQ).set(RQ.resolution, Resolution.REFUND).set(RQ.updatedAt, now)
                .where(RQ.id.eq(id), RQ.resolution.eq(Resolution.EXCHANGE), RQ.status.in(RequestStatus.OPEN)).execute() == 1;
    }

    public void setItemAmount(long itemId, long amount, long priorOffset) {
        query.update(RI).set(RI.refundAmount, amount).set(RI.priorRefundOffset, priorOffset).where(RI.id.eq(itemId)).execute();
    }

    public void insertDisposition(long id, long requestItemId, String disposition, int qty, Long operatorId, Instant now) {
        insert(GroupBuyReturnDispositionEntity.create(id, requestItemId, disposition, qty, operatorId, now));
    }

    // ── 처리 기한 경보(P35 — GroupBuyDeadlineWatch) ─────────────────────────

    /** 기한 경보를 볼 열린 요청 — {@code alertLevel} 지금까지 알린 단계(null · WARNING) */
    public record DeadlineRow(long id, RequestType type, Instant createdAt, Instant receivedAt, String alertLevel) {
    }

    /** 처리 중(REQUESTED·APPROVED·RECEIVED)이고 아직 심각(CRITICAL)을 알리지 않은 요청 — 오래된 요청 차례, 최대 {@code limit} */
    public List<DeadlineRow> deadlineCandidates(int limit) {
        return query.select(Projections.constructor(DeadlineRow.class, RQ.id, RQ.type, RQ.createdAt, RQ.receivedAt, RQ.deadlineAlertLevel)).from(RQ)
                .where(RQ.status.in(RequestStatus.REQUESTED, RequestStatus.APPROVED, RequestStatus.RECEIVED),
                        RQ.deadlineAlertLevel.isNull().or(RQ.deadlineAlertLevel.ne("CRITICAL")))
                .orderBy(RQ.createdAt.asc()).limit(limit).fetch();
    }

    /** 경보 단계를 올린다 — 지금 단계가 {@code previous}(null 이면 아직 없음)일 때만(이긴 쪽만 알린다) */
    public boolean moveDeadlineAlert(long id, String level, String previous) {
        return query.update(RQ).set(RQ.deadlineAlertLevel, level)
                .where(RQ.id.eq(id), previous == null ? RQ.deadlineAlertLevel.isNull() : RQ.deadlineAlertLevel.eq(previous)).execute() == 1;
    }

    // ── 사진 ────────────────────────────────────────────────────────────────

    public void insertPhoto(long id, String ownerKind, long ownerId, String imageKey, Instant now) {
        insert(GroupBuyRequestPhotoEntity.loose(id, ownerKind, ownerId, imageKey, now));
    }

    /** 그 주인의 붙지 않은 사진을 붙인다 — 붙인 수 */
    public int attachPhotos(long requestId, String ownerKind, long ownerId, List<Long> photoIds, Instant now) {
        int n = 0;
        for (long photoId : photoIds) {
            n += (int) query.update(PH).set(PH.requestId, requestId).set(PH.attachedAt, now)
                    .where(PH.id.eq(photoId), PH.ownerKind.eq(ownerKind), PH.ownerId.eq(ownerId), PH.requestId.isNull()).execute();
        }
        return n;
    }

    public int photoCount(long requestId, String ownerKind) {
        Long n = query.select(PH.count()).from(PH).where(PH.requestId.eq(requestId), PH.ownerKind.eq(ownerKind)).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /**
     * 붙지 않은 지 오래된 사진(24시간 — C 195)을 지운다. 지운 이미지 키(저장소 정리는 부르는 쪽).
     * Native SQL 인 이유: {@code DELETE … RETURNING image_key} — 지운 줄의 파일 키를 같은 문장에서 받는다(따로 고르면 그 사이 붙은 사진의 파일까지 지울 수 있다).
     */
    public List<String> deleteLoosePhotos(Instant before) {
        return jdbc.queryForList("DELETE FROM group_buy_request_photos WHERE request_id IS NULL AND created_at < ? RETURNING image_key", String.class, Timestamp.from(before));
    }

    // ── 단품 공급 불가·회원 선택(결정 53) ─────────────────────────────────────

    public void insertSupplyFailure(long id, long groupBuyId, long skuId, String reason, Instant choiceUntil, Long operatorId, Instant now) {
        insert(GroupBuySupplyFailureEntity.create(id, groupBuyId, skuId, reason, choiceUntil, operatorId, now));
    }

    /** 회원 선택 줄 — Native SQL 인 이유: {@code ON CONFLICT (supply_failure_id, participation_id) DO NOTHING}(다시 돌려도 한 번) */
    public void insertSupplyChoice(long id, long supplyFailureId, long participationId, long userId, Instant deadline, Instant now) {
        jdbc.update("INSERT INTO group_buy_supply_choices (id, supply_failure_id, participation_id, user_id, deadline, created_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (supply_failure_id, participation_id) DO NOTHING", id, supplyFailureId, participationId, userId, Timestamp.from(deadline), Timestamp.from(now));
    }

    /** 답하지 않은 선택(기한 안 — 그동안 발송하지 않는다) */
    public List<SupplyChoiceRow> openChoices(long participationId) {
        return query.select(CHOICE).from(CH).where(CH.participationId.eq(participationId), CH.choice.isNull()).orderBy(CH.id.asc()).fetch();
    }

    public List<SupplyChoiceRow> choicesOf(long participationId) {
        return query.select(CHOICE).from(CH).where(CH.participationId.eq(participationId)).orderBy(CH.id.asc()).fetch();
    }

    /** 회원이 답한다(기한 전, 답하지 않았을 때만) — 답하지 않아 정해 주는 것({@code defaulted})은 기한이 지났을 때만 */
    public boolean decideChoice(long id, SupplyChoice choice, boolean defaulted, Instant now) {
        return query.update(CH).set(CH.choice, choice).set(CH.decidedAt, now).set(CH.defaulted, defaulted)
                .where(CH.id.eq(id), CH.choice.isNull(), defaulted ? CH.deadline.loe(now) : CH.deadline.gt(now)).execute() == 1;
    }

    /** 기한이 지났는데 답하지 않은 선택 */
    public List<Long> choicesDue(Instant now, int limit) {
        return query.select(CH.id).from(CH).where(CH.choice.isNull(), CH.deadline.loe(now)).orderBy(CH.deadline.asc(), CH.id.asc()).limit(limit).fetch();
    }

    /** 답을 기다리는 선택이 있는가(발송 막기 — 결정 53) */
    public boolean choicePending(long participationId) {
        return query.selectOne().from(CH).where(CH.participationId.eq(participationId), CH.choice.isNull()).fetchFirst() != null;
    }
}
