package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyRequestEntity;
import com.community.groupbuy.entity.QGroupBuyRequestItemEntity;
import com.community.groupbuy.entity.QGroupBuyRequestPhotoEntity;
import com.community.groupbuy.entity.QGroupBuyReturnDispositionEntity;
import com.community.groupbuy.entity.QGroupBuyShipmentEntity;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.operator.entity.QOperatorEntity;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.entity.QPaymentOrderEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 관리자 취소·반품 요청 대기열({@code GroupBuyRequestAdminFacade})이 읽는 값 — Facade 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다(읽기만).
 *
 * <p>대기열의 「기한 경고만」({@code level=WARN})은 예전에 모든 요청을 읽어 자바로 거르고 쪽을 나눴다 — 지난 영업일 수는 시작일이 뒤로 갈수록 줄기만 하므로 「2영업일 지남」 =
 * 「시작 시각 &lt; 경계 시각」({@code GroupBuyBusinessDays#elapsedCutoff})이라 DB 에서 거르고 나눈다({@link ListFilter#warnBefore}). 시작 = 취소는 요청한 시각, 반품은 돌려받은 시각.
 */
@Repository
public class GroupBuyRequestAdminQueries extends IrobitRepositoryQDSL {

    private static final QGroupBuyRequestEntity RQ = QGroupBuyRequestEntity.groupBuyRequestEntity;
    private static final QGroupBuyRequestItemEntity RI = QGroupBuyRequestItemEntity.groupBuyRequestItemEntity;
    private static final QGroupBuyReturnDispositionEntity D = QGroupBuyReturnDispositionEntity.groupBuyReturnDispositionEntity;
    private static final QGroupBuyRequestPhotoEntity PH = QGroupBuyRequestPhotoEntity.groupBuyRequestPhotoEntity;
    private static final QGroupBuyShipmentEntity SH = QGroupBuyShipmentEntity.groupBuyShipmentEntity;
    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QPaymentOrderEntity PO = QPaymentOrderEntity.paymentOrderEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    /**
     * 대기열 거르기(추가 결제 기다림은 늘 뺀다) — {@code type}, 찾기({@code exact} 주문 번호·회원 번호 글자, {@code like} 닉네임 ILIKE), {@code statuses} 상태(대기열 「승인」 묶음은
     * 둘), {@code warnBefore} 기한 경고만(열린 요청 중 시작 시각이 이것보다 앞 — null 이면 거르지 않는다). 쓰지 않는 칸은 null.
     */
    public record ListFilter(RequestType type, String exact, String like, List<RequestStatus> statuses, Instant warnBefore) {
    }

    public record DispositionRow(long requestItemId, String disposition, int qty) {
    }

    public record RequestExtra(String pickupCarrierCode, String pickupTrackingNo, String rejectNote, String operatorName, Instant decidedAt, Instant completedAt) {
    }

    public record ShipmentRow(String carrierCode, String trackingNo, Instant shippedAt) {
    }

    public record FeePaymentRow(PaymentStatus status, long amount, String paymentId, Instant paidAt) {
    }

    public record PhotoRow(long id, String imageKey) {
    }

    public GroupBuyRequestAdminQueries(JPAQueryFactory query) {
        super(query);
    }

    private static BooleanExpression where(ListFilter f, boolean full) {
        BooleanExpression where = RQ.status.ne(RequestStatus.AWAITING_FEE);
        if (f.type() != null) {
            where = where.and(RQ.type.eq(f.type()));
        }
        if (f.exact() != null) {
            where = where.and(PA.orderNo.eq(f.exact()).or(GroupBuyRowNames.ilike(PR.nickname, f.like())).or(RQ.userId.stringValue().eq(f.exact())));
        }
        if (!full) {
            return where;
        }
        if (f.statuses() != null) {
            where = where.and(f.statuses().size() == 1 ? RQ.status.eq(f.statuses().get(0)) : RQ.status.in(f.statuses()));
        }
        if (f.warnBefore() != null) {
            where = where.and(RQ.status.in(RequestStatus.OPEN)).and(RQ.type.eq(RequestType.CANCEL).and(RQ.createdAt.lt(f.warnBefore()))
                    .or(RQ.type.ne(RequestType.CANCEL).and(RQ.receivedAt.lt(f.warnBefore()))));
        }
        return where;
    }

    /** 상태 거르기 전 상태별 수(대기열 탭 수 — 묶음은 부르는 쪽이 합친다) */
    public Map<RequestStatus, Long> statusCounts(ListFilter f) {
        Map<RequestStatus, Long> out = new LinkedHashMap<>();
        query.select(RQ.status, RQ.count()).from(RQ).join(PA).on(PA.id.eq(RQ.participationId)).leftJoin(PR).on(PR.userId.eq(RQ.userId)).where(where(f, false))
                .groupBy(RQ.status).fetch().forEach(t -> out.put(t.get(0, RequestStatus.class), n(t.get(1, Long.class))));
        return out;
    }

    public long count(ListFilter f) {
        return n(query.select(RQ.count()).from(RQ).join(PA).on(PA.id.eq(RQ.participationId)).leftJoin(PR).on(PR.userId.eq(RQ.userId)).where(where(f, true)).fetchOne());
    }

    /** 한 쪽의 요청 ID — 오래된 요청 먼저 */
    public List<Long> pageIds(ListFilter f, long offset, int limit) {
        return query.select(RQ.id).from(RQ).join(PA).on(PA.id.eq(RQ.participationId)).leftJoin(PR).on(PR.userId.eq(RQ.userId)).where(where(f, true))
                .orderBy(RQ.createdAt.asc(), RQ.id.asc()).offset(offset).limit(limit).fetch();
    }

    /** 반품 줄의 처리 방법 */
    public List<DispositionRow> dispositions(long requestId) {
        return query.select(D.requestItemId, D.disposition, D.qty).from(D).join(RI).on(RI.id.eq(D.requestItemId)).where(RI.requestId.eq(requestId)).fetch().stream()
                .map(t -> new DispositionRow(t.get(D.requestItemId), t.get(D.disposition), t.get(D.qty) == null ? 0 : t.get(D.qty))).toList();
    }

    /** 회수 송장·거절 메모·처리한 운영자·처리 시각 */
    public Optional<RequestExtra> extra(long requestId) {
        Tuple t = query.select(RQ.pickupCarrierCode, RQ.pickupTrackingNo, RQ.rejectNote, O.operatorName, RQ.decidedAt, RQ.completedAt).from(RQ).leftJoin(O)
                .on(O.id.eq(RQ.decidedBy)).where(RQ.id.eq(requestId)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new RequestExtra(t.get(RQ.pickupCarrierCode), t.get(RQ.pickupTrackingNo), t.get(RQ.rejectNote),
                t.get(O.operatorName), t.get(RQ.decidedAt), t.get(RQ.completedAt)));
    }

    /** 교환 재발송 */
    public Optional<ShipmentRow> exchangeShipment(long requestId) {
        Tuple t = query.select(SH.carrierCode, SH.trackingNo, SH.shippedAt).from(SH).where(SH.requestId.eq(requestId), SH.kind.eq("EXCHANGE")).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new ShipmentRow(t.get(SH.carrierCode), t.get(SH.trackingNo), t.get(SH.shippedAt)));
    }

    /** 공구 상품의 청약철회 제한 문구(없으면 null) */
    public String withdrawalRestriction(long groupBuyId) {
        return query.select(P.withdrawalRestriction).from(G).join(P).on(P.id.eq(G.productId)).where(G.id.eq(groupBuyId)).fetchFirst();
    }

    /** 반품비 추가 결제 */
    public Optional<FeePaymentRow> feePayment(long paymentOrderId) {
        Tuple t = query.select(PO.status, PO.amount, PO.paymentId, PO.paidAt).from(PO).where(PO.id.eq(paymentOrderId)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new FeePaymentRow(t.get(PO.status), n(t.get(PO.amount)), t.get(PO.paymentId), t.get(PO.paidAt)));
    }

    /** 요청 사진 — 올린 사람 종류(MEMBER · OPERATOR)마다, ID 차례 */
    public List<PhotoRow> photos(long requestId, String ownerKind) {
        return query.select(PH.id, PH.imageKey).from(PH).where(PH.requestId.eq(requestId), PH.ownerKind.eq(ownerKind)).orderBy(PH.id.asc()).fetch().stream()
                .map(t -> new PhotoRow(t.get(PH.id), t.get(PH.imageKey))).toList();
    }

    static long n(Long value) {
        return value == null ? 0 : value;
    }
}
