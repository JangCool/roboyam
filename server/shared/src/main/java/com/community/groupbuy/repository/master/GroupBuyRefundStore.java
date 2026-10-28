package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.instant;
import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.entity.RefundStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 환불 기록({@code group_buy_refunds}, 결정 36·75) — 공구 쪽에서 돈을 돌려주는 일 한 건마다 한 줄. 결제 모듈과는 취소 대기열 ID({@code payment_cancel_id})·요청 키로 잇는다.
 */
@Repository
public class GroupBuyRefundStore {

    /** 환불 기록 한 줄 */
    public record RefundRow(long id, long participationId, long groupBuyId, long userId, RefundCause cause, long itemRefund, long shippingRefund, long reshippingFee,
                            long amount, String method, Long paymentCancelId, RefundStatus status, Instant createdAt, Instant completedAt) {
    }

    /** 새 환불 기록(금액 나눔 — 2b-2 가 쓰는 칸만: 상품·배송비 환불, 재부과 배송비) */
    public record NewRefund(long id, long participationId, long groupBuyId, long userId, RefundCause cause, long itemRefund, long shippingRefund, long reshippingFee, long reshippingRefund,
                            long amount, Long paymentOrderId, String linesJson, Long actorId, String reason, Instant now) {
    }

    private static final String COLUMNS = "id, participation_id, group_buy_id, user_id, cause, item_refund, shipping_refund, reshipping_fee, amount, method, "
            + "payment_cancel_id, status, created_at, completed_at";

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
        jdbc.update("INSERT INTO group_buy_refunds (id, participation_id, group_buy_id, user_id, cause, item_refund, shipping_refund, reshipping_fee, reshipping_refund, amount, method, "
                        + "payment_order_id, request_key, status, lines, actor_id, reason, created_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)", r.id(), r.participationId(), r.groupBuyId(), r.userId(), r.cause().name(),
                r.itemRefund(), r.shippingRefund(), r.reshippingFee(), r.reshippingRefund(), r.amount(), none ? "NONE" : "PG_CANCEL", r.paymentOrderId(), requestKey(r.id()),
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

    public List<RefundRow> ofParticipation(long participationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM group_buy_refunds WHERE participation_id = ? ORDER BY created_at, id", GroupBuyRefundStore::row, participationId);
    }

    static RefundRow row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new RefundRow(rs.getLong("id"), rs.getLong("participation_id"), rs.getLong("group_buy_id"), rs.getLong("user_id"), RefundCause.valueOf(rs.getString("cause")),
                rs.getLong("item_refund"), rs.getLong("shipping_refund"), rs.getLong("reshipping_fee"), rs.getLong("amount"), rs.getString("method"),
                nLong(rs, "payment_cancel_id"), RefundStatus.valueOf(rs.getString("status")), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("completed_at")));
    }
}
