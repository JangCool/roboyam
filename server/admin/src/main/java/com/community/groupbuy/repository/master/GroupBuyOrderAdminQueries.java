package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QCommerceAddressViewEntity;
import com.community.groupbuy.entity.QGroupBuyAdminRefundEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationItemEntity;
import com.community.groupbuy.entity.QGroupBuyRefundEntity;
import com.community.groupbuy.entity.QGroupBuyShipmentEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.QParticipationStatusHistoryEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.entity.QPaymentOrderEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 관리자 공구 주문 화면({@code GroupBuyOrderAdminFacade})이 읽고 쓰는 값 — Facade 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다(부르는 쪽 트랜잭션에 참여한다 —
 * 메모 쓰기는 Facade 가 그 문장 하나의 트랜잭션을 연다).
 */
@Repository
public class GroupBuyOrderAdminQueries extends IrobitRepositoryQDSL {

    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyParticipationItemEntity I = QGroupBuyParticipationItemEntity.groupBuyParticipationItemEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;
    private static final QGroupBuyShipmentEntity SH = QGroupBuyShipmentEntity.groupBuyShipmentEntity;
    private static final QGroupBuyRefundEntity R = QGroupBuyRefundEntity.groupBuyRefundEntity;
    private static final QGroupBuyAdminRefundEntity AR = QGroupBuyAdminRefundEntity.groupBuyAdminRefundEntity;
    private static final QCommerceAddressViewEntity V = QCommerceAddressViewEntity.commerceAddressViewEntity;
    private static final QParticipationStatusHistoryEntity H = QParticipationStatusHistoryEntity.participationStatusHistoryEntity;
    private static final QPaymentOrderEntity PO = QPaymentOrderEntity.paymentOrderEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;

    /**
     * 목록 거르기(선점 만료는 늘 뺀다) — {@code groupBuyId}·찾기({@code exact} 주문 번호·회원 번호 글자, {@code like} 닉네임 ILIKE)·주문 시각 {@code [from, to)}·
     * {@code onlyIds} 이 주문들만(배송 기한 지남 — 빈 목록이면 하나도 없다)·{@code shippedBefore} 발송 뒤 오래된 것만(배송중)·{@code status} 상태. 쓰지 않는 칸은 null.
     */
    public record ListFilter(Long groupBuyId, String exact, String like, Instant from, Instant to, List<Long> onlyIds, Instant shippedBefore, ParticipationStatus status) {

        public ListFilter withStatus(ParticipationStatus value) {
            return new ListFilter(groupBuyId, exact, like, from, to, onlyIds, shippedBefore, value);
        }
    }

    public record PaymentRow(long id, String paymentId, PaymentStatus status, String payMethod, boolean escrow, Instant paidAt) {
    }

    public record AddressViewRow(String operatorName, String reason, Instant viewedAt) {
    }

    public record HistoryRow(ParticipationStatus from, ParticipationStatus to, String operatorName, String cause, Instant createdAt, Long actorId) {
    }

    public record RefundExtra(String reason, String operatorName) {
    }

    public record PendingRefundRow(long id, long amount, String reason, Instant createdAt, String operatorName, boolean sellerFault) {
    }

    /** 회원 표시 — 닉네임(탈퇴했어도 남은 값), 탈퇴·삭제 여부 */
    public record MemberRow(String nickname, boolean gone) {
    }

    public record LineRow(String name, int qty) {
    }

    public GroupBuyOrderAdminQueries(JPAQueryFactory query) {
        super(query);
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    private static BooleanExpression where(ListFilter f, boolean withStatus) {
        BooleanExpression where = PA.status.ne(ParticipationStatus.EXPIRED);
        if (f.groupBuyId() != null) {
            where = where.and(PA.groupBuyId.eq(f.groupBuyId()));
        }
        if (f.exact() != null) {
            where = where.and(PA.orderNo.eq(f.exact()).or(GroupBuyRowNames.ilike(PR.nickname, f.like())).or(PA.userId.stringValue().eq(f.exact())));
        }
        if (f.from() != null) {
            where = where.and(PA.createdAt.goe(f.from()));
        }
        if (f.to() != null) {
            where = where.and(PA.createdAt.lt(f.to()));
        }
        if (f.onlyIds() != null) {
            where = where.and(f.onlyIds().isEmpty() ? PA.id.isNull() : PA.id.in(f.onlyIds())); // 빈 목록 = 예전 {@code = ANY('{}')} 처럼 하나도 맞지 않음(ID 는 null 이 아니다)
        }
        if (f.shippedBefore() != null) {
            where = where.and(PA.status.eq(ParticipationStatus.SHIPPED)).and(PA.shippedAt.lt(f.shippedBefore()));
        }
        if (withStatus && f.status() != null) {
            where = where.and(PA.status.eq(f.status()));
        }
        return where;
    }

    /** 상태 거르기 전 상태별 수(탭 수) */
    public Map<ParticipationStatus, Long> statusCounts(ListFilter f) {
        Map<ParticipationStatus, Long> out = new LinkedHashMap<>();
        query.select(PA.status, PA.count()).from(PA).leftJoin(PR).on(PR.userId.eq(PA.userId)).where(where(f, false)).groupBy(PA.status).fetch()
                .forEach(t -> out.put(t.get(0, ParticipationStatus.class), n(t.get(1, Long.class))));
        return out;
    }

    public long count(ListFilter f) {
        return n(query.select(PA.count()).from(PA).leftJoin(PR).on(PR.userId.eq(PA.userId)).where(where(f, true)).fetchOne());
    }

    /** 한 쪽의 주문 ID — 새 주문 먼저 */
    public List<Long> pageIds(ListFilter f, long offset, int limit) {
        return query.select(PA.id).from(PA).leftJoin(PR).on(PR.userId.eq(PA.userId)).where(where(f, true)).orderBy(PA.createdAt.desc(), PA.id.desc()).offset(offset)
                .limit(limit).fetch();
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    /** 결제 주문 — 에스크로 = 그 주문의 배송 중 에스크로 대상이 있는가 */
    public Optional<PaymentRow> payment(long participationId, long paymentOrderId) {
        // 예전과 같이 한 문장 — 결제 주문 줄과 에스크로 여부(EXISTS)를 같은 시점에 읽는다(상세는 열람 감사를 쓰는 쓰기 경로라 격리 수준으로 묶지 않는다)
        NumberExpression<Integer> escrow = new CaseBuilder()
                .when(JPAExpressions.selectOne().from(SH).where(SH.participationId.eq(participationId), SH.escrowApplicable.isTrue()).exists()).then(1).otherwise(0);
        Tuple t = query.select(PO.id, PO.paymentId, PO.status, PO.payMethod, PO.paidAt, escrow).from(PO).where(PO.id.eq(paymentOrderId)).fetchFirst();
        if (t == null) {
            return Optional.empty();
        }
        Integer hasEscrow = t.get(escrow);
        return Optional.of(new PaymentRow(t.get(PO.id), t.get(PO.paymentId), t.get(PO.status), t.get(PO.payMethod), hasEscrow != null && hasEscrow == 1, t.get(PO.paidAt)));
    }

    /** 배송지 열람 기록 — 새 것 먼저 */
    public List<AddressViewRow> addressViews(long participationId) {
        return query.select(O.operatorName, V.reason, V.createdAt).from(V).leftJoin(O).on(O.id.eq(V.operatorId)).where(V.participationId.eq(participationId), V.kind.eq("REVEAL"))
                .orderBy(V.createdAt.desc()).fetch().stream().map(t -> new AddressViewRow(t.get(O.operatorName), t.get(V.reason), t.get(V.createdAt))).toList();
    }

    /** 운영자 메모(없으면 null) */
    public String adminMemo(long participationId) {
        return query.select(PA.adminMemo).from(PA).where(PA.id.eq(participationId)).fetchFirst();
    }

    /** 상태 이력 — 오래된 것 먼저 */
    public List<HistoryRow> history(long participationId) {
        return query.select(H.fromStatus, H.toStatus, O.operatorName, H.cause, H.createdAt, H.actorId).from(H).leftJoin(O).on(O.id.eq(H.actorId))
                .where(H.participationId.eq(participationId)).orderBy(H.createdAt.asc(), H.id.asc()).fetch().stream()
                .map(t -> new HistoryRow(t.get(H.fromStatus), t.get(H.toStatus), t.get(O.operatorName), t.get(H.cause), t.get(H.createdAt), t.get(H.actorId))).toList();
    }

    /** 환불 기록의 사유·처리한 운영자 */
    public Optional<RefundExtra> refundExtra(long refundId) {
        Tuple t = query.select(R.reason, O.operatorName).from(R).leftJoin(O).on(O.id.eq(R.actorId)).where(R.id.eq(refundId)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new RefundExtra(t.get(R.reason), t.get(O.operatorName)));
    }

    /** 승인 대기 중인 운영 환불 */
    public List<PendingRefundRow> pendingRefunds(long participationId) {
        return query.select(AR.id, AR.amount, AR.reason, AR.createdAt, O.operatorName, AR.sellerFault).from(AR).leftJoin(O).on(O.id.eq(AR.requestedBy))
                .where(AR.participationId.eq(participationId), AR.status.eq("AWAITING_APPROVAL")).fetch().stream()
                .map(t -> new PendingRefundRow(t.get(AR.id), n(t.get(AR.amount)), t.get(AR.reason), t.get(AR.createdAt), t.get(O.operatorName),
                        Boolean.TRUE.equals(t.get(AR.sellerFault)))).toList();
    }

    /** 운영 환불 승인 대기 줄의 주문(없으면 null) */
    public Long pendingRefundOwner(long pendingId) {
        return query.select(AR.participationId).from(AR).where(AR.id.eq(pendingId)).fetchFirst();
    }

    /** 운영자 메모를 고친다(빈 글자면 지운다) — 주문이 없으면 false */
    public boolean saveMemo(long participationId, String memo, Instant now) {
        return query.update(PA).set(PA.adminMemo, memo).set(PA.updatedAt, now).where(PA.id.eq(participationId)).execute() > 0;
    }

    /** 회원 표시 값 — 회원 줄·프로필 줄을 따로 왼쪽 조인한 것과 같다(회원 줄이 없어도 프로필 닉네임은 읽는다) */
    public MemberRow member(long userId) {
        Tuple u = query.select(U.id, U.status, U.deletedAt).from(U).where(U.id.eq(userId)).fetchFirst();
        String nickname = query.select(PR.nickname).from(PR).where(PR.userId.eq(userId)).fetchFirst();
        return new MemberRow(nickname, u == null || GroupBuyRowNames.gone(u.get(U.id), u.get(U.status), u.get(U.deletedAt)));
    }

    /** 주문 줄 이름·수량 — 본품 먼저 · 차례 · 줄 ID */
    public List<LineRow> lines(long participationId) {
        return query.select(S.name, I.qty).from(I).join(S).on(S.id.eq(I.skuId)).where(I.participationId.eq(participationId)).orderBy(S.kind.desc(), S.sortOrder.asc(), I.id.asc())
                .fetch().stream().map(t -> new LineRow(t.get(S.name), n(t.get(I.qty)))).toList();
    }

    static long n(Long value) {
        return value == null ? 0 : value;
    }

    static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
