package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyAdminRefundEntity;
import com.community.groupbuy.entity.GroupBuyRefundEntity;
import com.community.groupbuy.entity.QGroupBuyAdminRefundEntity;
import com.community.groupbuy.entity.QGroupBuyRefundEntity;
import com.community.groupbuy.entity.QGroupBuyRequestEntity;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.entity.RefundStatus;
import com.community.groupbuy.service.GroupBuyRefundService.Parts;
import com.community.payment.entity.QReconciliationIssueEntity;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 환불 기록({@code group_buy_refunds}, 결정 36·75) — 공구 쪽에서 돈을 돌려주는 일 한 건마다 한 줄. 결제 모듈과는 취소 대기열 ID({@code payment_cancel_id})·요청 키로 잇는다.
 * 운영 환불 승인 대기({@code group_buy_admin_refunds} — 결정 70)도 여기서.
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). Native SQL 로 남긴 것과 이유: 결제 취소 결과로 기록을 바꾸는 세 문장({@link #completeByCancel} ·
 * {@link #markManualByCancel} · {@link #switchToBankByCancel} — 조건부 {@code UPDATE … RETURNING}: 이긴 쪽만 바뀐 그 기록을 같은 문장에서 받아 알림을 낸다. 결제 모듈의
 * {@code onCanceled} 가 결제 트랜잭션 안에서 부른다).
 */
@Repository
public class GroupBuyRefundStore extends GroupBuyStoreSupport {

    private static final QGroupBuyRefundEntity R = QGroupBuyRefundEntity.groupBuyRefundEntity;
    private static final QGroupBuyAdminRefundEntity AR = QGroupBuyAdminRefundEntity.groupBuyAdminRefundEntity;
    private static final QGroupBuyRequestEntity RQ = QGroupBuyRequestEntity.groupBuyRequestEntity;
    private static final QReconciliationIssueEntity RI = QReconciliationIssueEntity.reconciliationIssueEntity;

    /** 환불 기록 한 줄 */
    public record RefundRow(long id, long participationId, long groupBuyId, long userId, RefundCause cause, Long requestId, long itemRefund, long shippingRefund,
                            long reshippingFee, long reshippingRefund, long returnFee, long compensationPg, long compensationBank, long feeCovered, long feeRefund,
                            long operatorAmount, long amount, String method, Long paymentOrderId, Long paymentCancelId, RefundStatus status, String linesJson,
                            Instant createdAt, Instant completedAt) {
    }

    /** 새 환불 기록 */
    public record NewRefund(long id, long participationId, long groupBuyId, long userId, RefundCause cause, Long requestId, Parts parts, long compensationBank, long feeRefund,
                            long operatorAmount, long amount, Long paymentOrderId, String linesJson, Long actorId, String reason, Instant now) {
    }

    /** 운영 환불 승인 대기 한 줄 */
    public record PendingAdminRefund(long id, long participationId, String kind, long amount, String linesJson, boolean sellerFault, String reason, String status,
                                     long requestedBy) {
    }

    private static final ConstructorExpression<RefundRow> ROW = Projections.constructor(RefundRow.class, R.id, R.participationId, R.groupBuyId, R.userId, R.cause,
            R.requestId, R.itemRefund, R.shippingRefund, R.reshippingFee, R.reshippingRefund, R.returnFee, R.compensationPg, R.compensationBank, R.feeCovered, R.feeRefund,
            R.operatorAmount, R.amount, R.method, R.paymentOrderId, R.paymentCancelId, R.status, R.lines, R.createdAt, R.completedAt);

    /** {@code RETURNING} 칸(네이티브 세 문장) */
    private static final String COLUMNS = "id, participation_id, group_buy_id, user_id, cause, request_id, item_refund, shipping_refund, reshipping_fee, reshipping_refund, "
            + "return_fee, compensation_pg, compensation_bank, fee_covered, fee_refund, operator_amount, amount, method, payment_order_id, payment_cancel_id, status, "
            + "lines::text AS lines, created_at, completed_at";

    public GroupBuyRefundStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** 요청 키 {@code GB_REFUND:<id>} — 결제 취소 대기열에 같은 키로 넣는다 */
    public static String requestKey(long refundId) {
        return "GB_REFUND:" + refundId;
    }

    /** 0원이면 취소를 넣지 않고 바로 완료(method NONE — 결정 68), 아니면 진행 중(PG_CANCEL) */
    public void insert(NewRefund r) {
        Parts p = r.parts();
        insert(GroupBuyRefundEntity.create(r.id(), r.participationId(), r.groupBuyId(), r.userId(), r.cause(), r.requestId(), p.itemRefund(), p.shippingRefund(),
                p.reshippingFee(), p.reshippingRefund(), p.returnFee(), p.compensationPg(), r.compensationBank(), p.feeCovered(), r.feeRefund(), r.operatorAmount(), r.amount(),
                r.paymentOrderId(), requestKey(r.id()), r.linesJson(), r.actorId(), r.reason(), r.now()));
    }

    public void attachCancel(long refundId, long paymentCancelId) {
        query.update(R).set(R.paymentCancelId, paymentCancelId).where(R.id.eq(refundId)).execute();
    }

    /** 결제 취소가 끝났다 — 진행 중(또는 지연)이던 기록을 완료로(이긴 쪽만). 그 기록 */
    public Optional<RefundRow> completeByCancel(long paymentCancelId, Instant now) {
        return jdbc.query("UPDATE group_buy_refunds SET status = 'COMPLETED', completed_at = ? WHERE payment_cancel_id = ? AND status IN ('IN_PROGRESS', 'DELAYED') "
                + "RETURNING " + COLUMNS, GroupBuyRefundStore::returned, Timestamp.from(now), paymentCancelId).stream().findFirst();
    }

    /** 운영자가 콘솔에서 직접 취소했다 — MANUAL(완료 시각)로(결정 16·74). 그 기록 */
    public Optional<RefundRow> markManualByCancel(long paymentCancelId, Instant now) {
        return jdbc.query("UPDATE group_buy_refunds SET status = 'MANUAL', completed_at = ? WHERE payment_cancel_id = ? AND status IN ('IN_PROGRESS', 'DELAYED') "
                + "RETURNING " + COLUMNS, GroupBuyRefundStore::returned, Timestamp.from(now), paymentCancelId).stream().findFirst();
    }

    /** 계좌 송금으로 바꾼다(결정 67) — 기록은 진행 중 그대로, 송금 완료 때 완료 */
    public Optional<RefundRow> switchToBankByCancel(long paymentCancelId) {
        return jdbc.query("UPDATE group_buy_refunds SET method = 'BANK_TRANSFER' WHERE payment_cancel_id = ? AND status IN ('IN_PROGRESS', 'DELAYED') AND method = 'PG_CANCEL' "
                + "RETURNING " + COLUMNS, GroupBuyRefundStore::returned, paymentCancelId).stream().findFirst();
    }

    /** 계좌 송금 완료 → 완료(이긴 쪽만) */
    public boolean completeBank(long refundId, Instant now) {
        return query.update(R).set(R.status, RefundStatus.COMPLETED).set(R.completedAt, now)
                .where(R.id.eq(refundId), R.method.eq("BANK_TRANSFER"), R.status.in(RefundStatus.IN_PROGRESS, RefundStatus.DELAYED)).execute() == 1;
    }

    public Optional<RefundRow> find(long id) {
        return Optional.ofNullable(query.select(ROW).from(R).where(R.id.eq(id)).fetchFirst());
    }

    public List<RefundRow> ofParticipation(long participationId) {
        return query.select(ROW).from(R).where(R.participationId.eq(participationId)).orderBy(R.createdAt.asc(), R.id.asc()).fetch();
    }

    public List<RefundRow> ofRequest(long requestId) {
        return query.select(ROW).from(R).where(R.requestId.eq(requestId)).orderBy(R.createdAt.asc(), R.id.asc()).fetch();
    }

    /** 그 주문에서 운영자가 금액을 정한 환불의 누계(고액 문턱 — 결정 70, 쪼개서 피하지 못하게) */
    public long operatorAmountTotal(long participationId) {
        return n(query.select(sumLong(R.operatorAmount)).from(R).where(R.participationId.eq(participationId)).fetchOne());
    }

    /** 반품비 추가 결제의 환불 누계를 올린다 — 낸 금액을 넘지 않을 때만(결정 68) */
    public boolean addFeeRefunded(long requestId, long amount, Instant now) {
        return query.update(RQ).set(RQ.feeRefundedAmount, RQ.feeRefundedAmount.add(amount)).set(RQ.updatedAt, now)
                .where(RQ.id.eq(requestId), RQ.feeRefundedAmount.add(amount).loe(RQ.feePaidAmount)).execute() == 1;
    }

    /**
     * 그 결제에 열린 대사 이슈 CANCEL_MISMATCH 가 있는가(콘솔 직접 취소 — 결정 73).
     * 결제 엔티티({@code reconciliation_issues})를 읽는다.
     */
    public boolean paymentUnreconciled(Long paymentOrderId) {
        if (paymentOrderId == null) {
            return false;
        }
        return query.selectOne().from(RI).where(RI.paymentOrderId.eq(paymentOrderId), RI.issueType.eq("CANCEL_MISMATCH"), RI.status.eq("OPEN")).fetchFirst() != null;
    }

    // ── 운영 환불 승인 대기(결정 70) ─────────────────────────────────────────

    public void insertPending(long id, long participationId, String kind, long amount, String linesJson, boolean sellerFault, String reason, long requestedBy, Instant now) {
        insert(GroupBuyAdminRefundEntity.awaiting(id, participationId, kind, amount, linesJson, sellerFault, reason, requestedBy, now));
    }

    public Optional<PendingAdminRefund> lockPending(long id) {
        if (!RowLocks.forUpdate(entityManager, GroupBuyAdminRefundEntity.class, id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(query.select(Projections.constructor(PendingAdminRefund.class, AR.id, AR.participationId, AR.kind, AR.amount, AR.lines, AR.sellerFault,
                AR.reason, AR.status, AR.requestedBy)).from(AR).where(AR.id.eq(id)).fetchFirst());
    }

    public boolean decidePending(long id, boolean approved, Long refundId, long operatorId, Instant now) {
        return query.update(AR).set(AR.status, approved ? "APPROVED" : "REJECTED").set(AR.refundId, refundId).set(AR.decidedBy, operatorId).set(AR.decidedAt, now)
                .where(AR.id.eq(id), AR.status.eq("AWAITING_APPROVAL")).execute() == 1;
    }

    private static RefundRow returned(ResultSet rs, int i) throws SQLException {
        return new RefundRow(rs.getLong("id"), rs.getLong("participation_id"), rs.getLong("group_buy_id"), rs.getLong("user_id"), RefundCause.valueOf(rs.getString("cause")),
                nLong(rs, "request_id"), rs.getLong("item_refund"), rs.getLong("shipping_refund"), rs.getLong("reshipping_fee"), rs.getLong("reshipping_refund"),
                rs.getLong("return_fee"), rs.getLong("compensation_pg"), rs.getLong("compensation_bank"), rs.getLong("fee_covered"), rs.getLong("fee_refund"),
                rs.getLong("operator_amount"), rs.getLong("amount"), rs.getString("method"), nLong(rs, "payment_order_id"), nLong(rs, "payment_cancel_id"),
                RefundStatus.valueOf(rs.getString("status")), rs.getString("lines"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("completed_at")));
    }

    private static Long nLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
