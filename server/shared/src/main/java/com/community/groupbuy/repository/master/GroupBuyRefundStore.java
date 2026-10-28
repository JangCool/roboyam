package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.instant;
import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.entity.RefundStatus;
import com.community.groupbuy.service.GroupBuyRefundService.Parts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 환불 기록({@code group_buy_refunds}, 결정 36·75) — 공구 쪽에서 돈을 돌려주는 일 한 건마다 한 줄. 결제 모듈과는 취소 대기열 ID({@code payment_cancel_id})·요청 키로 잇는다.
 * 운영 환불 승인 대기({@code group_buy_admin_refunds} — 결정 70)도 여기서.
 */
@Repository
public class GroupBuyRefundStore {

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

    private static final String COLUMNS = "id, participation_id, group_buy_id, user_id, cause, request_id, item_refund, shipping_refund, reshipping_fee, reshipping_refund, "
            + "return_fee, compensation_pg, compensation_bank, fee_covered, fee_refund, operator_amount, amount, method, payment_order_id, payment_cancel_id, status, "
            + "lines::text AS lines, created_at, completed_at";

    private final JdbcTemplate jdbc;

    public GroupBuyRefundStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 요청 키 {@code GB_REFUND:<id>} — 결제 취소 대기열에 같은 키로 넣는다 */
    public static String requestKey(long refundId) {
        return "GB_REFUND:" + refundId;
    }

    /** 0원이면 취소를 넣지 않고 바로 완료(method NONE — 결정 68), 아니면 진행 중(PG_CANCEL) */
    public void insert(NewRefund r) {
        boolean none = r.amount() == 0;
        Parts p = r.parts();
        jdbc.update("INSERT INTO group_buy_refunds (id, participation_id, group_buy_id, user_id, cause, request_id, item_refund, shipping_refund, reshipping_fee, "
                        + "reshipping_refund, return_fee, compensation_pg, compensation_bank, fee_covered, fee_refund, operator_amount, amount, method, payment_order_id, "
                        + "request_key, status, lines, actor_id, reason, created_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)", r.id(), r.participationId(), r.groupBuyId(), r.userId(),
                r.cause().name(), r.requestId(), p.itemRefund(), p.shippingRefund(), p.reshippingFee(), p.reshippingRefund(), p.returnFee(), p.compensationPg(),
                r.compensationBank(), p.feeCovered(), r.feeRefund(), r.operatorAmount(), r.amount(), none ? "NONE" : "PG_CANCEL", r.paymentOrderId(), requestKey(r.id()),
                none ? RefundStatus.COMPLETED.name() : RefundStatus.IN_PROGRESS.name(), r.linesJson(), r.actorId(), r.reason(), ts(r.now()), none ? ts(r.now()) : null);
    }

    public void attachCancel(long refundId, long paymentCancelId) {
        jdbc.update("UPDATE group_buy_refunds SET payment_cancel_id = ? WHERE id = ?", paymentCancelId, refundId);
    }

    /** 결제 취소가 끝났다 — 진행 중(또는 지연)이던 기록을 완료로(이긴 쪽만). 그 기록 */
    public Optional<RefundRow> completeByCancel(long paymentCancelId, Instant now) {
        return jdbc.query("UPDATE group_buy_refunds SET status = 'COMPLETED', completed_at = ? WHERE payment_cancel_id = ? AND status IN ('IN_PROGRESS', 'DELAYED') "
                + "RETURNING " + COLUMNS, GroupBuyRefundStore::row, ts(now), paymentCancelId).stream().findFirst();
    }

    /** 운영자가 콘솔에서 직접 취소했다 — MANUAL(완료 시각)로(결정 16·74). 그 기록 */
    public Optional<RefundRow> markManualByCancel(long paymentCancelId, Instant now) {
        return jdbc.query("UPDATE group_buy_refunds SET status = 'MANUAL', completed_at = ? WHERE payment_cancel_id = ? AND status IN ('IN_PROGRESS', 'DELAYED') "
                + "RETURNING " + COLUMNS, GroupBuyRefundStore::row, ts(now), paymentCancelId).stream().findFirst();
    }

    /** 계좌 송금으로 바꾼다(결정 67) — 기록은 진행 중 그대로, 송금 완료 때 완료 */
    public Optional<RefundRow> switchToBankByCancel(long paymentCancelId) {
        return jdbc.query("UPDATE group_buy_refunds SET method = 'BANK_TRANSFER' WHERE payment_cancel_id = ? AND status IN ('IN_PROGRESS', 'DELAYED') AND method = 'PG_CANCEL' "
                + "RETURNING " + COLUMNS, GroupBuyRefundStore::row, paymentCancelId).stream().findFirst();
    }

    /** 계좌 송금 완료 → 완료(이긴 쪽만) */
    public boolean completeBank(long refundId, Instant now) {
        return jdbc.update("UPDATE group_buy_refunds SET status = 'COMPLETED', completed_at = ? WHERE id = ? AND method = 'BANK_TRANSFER' AND status IN ('IN_PROGRESS', 'DELAYED')",
                ts(now), refundId) == 1;
    }

    public Optional<RefundRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_refunds WHERE id = ?", GroupBuyRefundStore::row, id).stream().findFirst();
    }

    public List<RefundRow> ofParticipation(long participationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_refunds WHERE participation_id = ? ORDER BY created_at, id", GroupBuyRefundStore::row, participationId);
    }

    public List<RefundRow> ofRequest(long requestId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_refunds WHERE request_id = ? ORDER BY created_at, id", GroupBuyRefundStore::row, requestId);
    }

    /** 그 주문에서 운영자가 금액을 정한 환불의 누계(고액 문턱 — 결정 70, 쪼개서 피하지 못하게) */
    public long operatorAmountTotal(long participationId) {
        Long n = jdbc.queryForObject("SELECT coalesce(sum(operator_amount), 0) FROM group_buy_refunds WHERE participation_id = ?", Long.class, participationId);
        return n == null ? 0 : n;
    }

    /** 반품비 추가 결제의 환불 누계를 올린다 — 낸 금액을 넘지 않을 때만(결정 68) */
    public boolean addFeeRefunded(long requestId, long amount, Instant now) {
        return jdbc.update("UPDATE group_buy_requests SET fee_refunded_amount = fee_refunded_amount + ?, updated_at = ? WHERE id = ? "
                + "AND fee_refunded_amount + ? <= fee_paid_amount", amount, ts(now), requestId, amount) == 1;
    }

    /** 그 결제에 열린 대사 이슈 CANCEL_MISMATCH 가 있는가(콘솔 직접 취소 — 결정 73) */
    public boolean paymentUnreconciled(Long paymentOrderId) {
        if (paymentOrderId == null) {
            return false;
        }
        Integer n = jdbc.queryForObject("SELECT count(*) FROM reconciliation_issues WHERE payment_order_id = ? AND type = 'CANCEL_MISMATCH' AND status = 'OPEN'",
                Integer.class, paymentOrderId);
        return n != null && n > 0;
    }

    // ── 운영 환불 승인 대기(결정 70) ─────────────────────────────────────────

    public void insertPending(long id, long participationId, String kind, long amount, String linesJson, boolean sellerFault, String reason, long requestedBy, Instant now) {
        jdbc.update("INSERT INTO group_buy_admin_refunds (id, participation_id, kind, amount, lines, seller_fault, reason, status, requested_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'AWAITING_APPROVAL', ?, ?)", id, participationId, kind, amount, linesJson, sellerFault, reason, requestedBy, ts(now));
    }

    public Optional<PendingAdminRefund> lockPending(long id) {
        return jdbc.query("SELECT id, participation_id, kind, amount, lines::text, seller_fault, reason, status, requested_by FROM group_buy_admin_refunds WHERE id = ? FOR UPDATE",
                (rs, i) -> new PendingAdminRefund(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getBoolean(6), rs.getString(7),
                        rs.getString(8), rs.getLong(9)), id).stream().findFirst();
    }

    public boolean decidePending(long id, boolean approved, Long refundId, long operatorId, Instant now) {
        return jdbc.update("UPDATE group_buy_admin_refunds SET status = ?, refund_id = ?, decided_by = ?, decided_at = ? WHERE id = ? AND status = 'AWAITING_APPROVAL'",
                approved ? "APPROVED" : "REJECTED", refundId, operatorId, ts(now), id) == 1;
    }

    static RefundRow row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new RefundRow(rs.getLong("id"), rs.getLong("participation_id"), rs.getLong("group_buy_id"), rs.getLong("user_id"), RefundCause.valueOf(rs.getString("cause")),
                nLong(rs, "request_id"), rs.getLong("item_refund"), rs.getLong("shipping_refund"), rs.getLong("reshipping_fee"), rs.getLong("reshipping_refund"),
                rs.getLong("return_fee"), rs.getLong("compensation_pg"), rs.getLong("compensation_bank"), rs.getLong("fee_covered"), rs.getLong("fee_refund"),
                rs.getLong("operator_amount"), rs.getLong("amount"), rs.getString("method"), nLong(rs, "payment_order_id"), nLong(rs, "payment_cancel_id"),
                RefundStatus.valueOf(rs.getString("status")), rs.getString("lines"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("completed_at")));
    }
}
