package com.community.bizapplication.repository.master;

import static com.community.bizapplication.repository.master.BizApplicationStore.instant;
import static com.community.bizapplication.repository.master.BizApplicationStore.ts;

import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 환불 줄(V112 {@code biz_refunds}) — 신청 건의 refund 하위 값은 가장 최근 줄이다. 열린(REQUESTED) 줄은 신청 건당 하나(부분 유일 색인) */
@Repository
public class BizRefundStore {

    /** 송금 상태(수동) */
    public static final String TRANSFER_PENDING = "PENDING";
    public static final String TRANSFER_DONE = "DONE";

    public record RefundRow(long id, long applicationId, BizRefundType type, BizRefundStatus status, BizPaymentMethod method, long paidAmount, long proposedAmount,
                            int usedDays, int totalDays, Long approvedAmount, String reason, String decisionReason, Long paymentCancelId, String transferStatus,
                            Instant transferredAt, Long transferredBy, String transferNote, Instant completedAt, Instant requestedAt, Instant decidedAt,
                            Long decidedBy) {
    }

    /** 새 환불 줄 */
    public record NewRefund(long id, long applicationId, BizRefundType type, BizRefundStatus status, BizPaymentMethod method, long paidAmount, long proposedAmount,
                            int usedDays, int totalDays, Long approvedAmount, String reason, String transferStatus, Instant requestedAt, Instant decidedAt,
                            Long decidedBy, Instant completedAt) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BizRefundStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public void insert(NewRefund r) {
        jdbc.update("INSERT INTO biz_refunds (id, application_id, type, status, method, paid_amount, proposed_amount, used_days, total_days, approved_amount, reason, "
                        + "transfer_status, requested_at, decided_at, decided_by, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.applicationId(), r.type().name(), r.status().name(), r.method().name(), r.paidAmount(), r.proposedAmount(), r.usedDays(), r.totalDays(),
                r.approvedAmount(), r.reason(), r.transferStatus(), ts(r.requestedAt()), ts(r.decidedAt()), r.decidedBy(), ts(r.completedAt()));
    }

    public Optional<RefundRow> latest(long applicationId) {
        return jdbc.query("SELECT * FROM biz_refunds WHERE application_id = ? ORDER BY requested_at DESC, id DESC LIMIT 1", (rs, i) -> row(rs), applicationId).stream()
                .findFirst();
    }

    public Map<Long, RefundRow> latest(Collection<Long> applicationIds) {
        Map<Long, RefundRow> out = new HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        named.query("SELECT DISTINCT ON (application_id) * FROM biz_refunds WHERE application_id IN (:ids) ORDER BY application_id, requested_at DESC, id DESC",
                new MapSqlParameterSource("ids", applicationIds), rs -> {
                    RefundRow row = row(rs);
                    out.put(row.applicationId(), row);
                });
        return out;
    }

    public List<RefundRow> all(long applicationId) {
        return jdbc.query("SELECT * FROM biz_refunds WHERE application_id = ? ORDER BY requested_at, id", (rs, i) -> row(rs), applicationId);
    }

    /** 열린(심사 대기) 줄을 잠근다 */
    public Optional<RefundRow> lockOpen(long applicationId) {
        return jdbc.query("SELECT * FROM biz_refunds WHERE application_id = ? AND status = 'REQUESTED' FOR UPDATE", (rs, i) -> row(rs), applicationId).stream().findFirst();
    }

    public boolean exists(long applicationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM biz_refunds WHERE application_id = ?)", Boolean.class, applicationId));
    }

    /** 이미 승인(돌려주기로 한) 금액 합 */
    public long approvedTotal(long applicationId) {
        Long n = jdbc.queryForObject("SELECT coalesce(sum(approved_amount), 0) FROM biz_refunds WHERE application_id = ? AND status = 'APPROVED'", Long.class, applicationId);
        return n == null ? 0 : n;
    }

    /** 심사 결정 — 열린 줄에서만 */
    public boolean decide(long id, BizRefundStatus to, Long approvedAmount, String decisionReason, String transferStatus, Instant completedAt, Long operatorId, Instant now) {
        return jdbc.update("UPDATE biz_refunds SET status = ?, approved_amount = ?, decision_reason = ?, transfer_status = ?, completed_at = ?, decided_at = ?, decided_by = ? "
                + "WHERE id = ? AND status = 'REQUESTED'", to.name(), approvedAmount, decisionReason, transferStatus, ts(completedAt), ts(now), operatorId, id) == 1;
    }

    public void linkCancel(long id, long paymentCancelId) {
        jdbc.update("UPDATE biz_refunds SET payment_cancel_id = ? WHERE id = ?", paymentCancelId, id);
    }

    /** PG 취소가 끝났다(결제 모듈 onCanceled) — 그 취소에 걸린 줄 */
    public Optional<RefundRow> completeByCancel(long paymentCancelId, Instant now) {
        List<RefundRow> rows = jdbc.query("UPDATE biz_refunds SET completed_at = coalesce(completed_at, ?) WHERE payment_cancel_id = ? RETURNING *", (rs, i) -> row(rs),
                ts(now), paymentCancelId);
        return rows.stream().findFirst();
    }

    /** 수동 송금 완료 표시 — 송금 대기에서만 */
    public boolean markTransferred(long id, long operatorId, String note, Instant now) {
        return jdbc.update("UPDATE biz_refunds SET transfer_status = 'DONE', transferred_at = ?, transferred_by = ?, transfer_note = ?, completed_at = coalesce(completed_at, ?) "
                + "WHERE id = ? AND transfer_status = 'PENDING'", ts(now), operatorId, note, ts(now), id) == 1;
    }

    private static RefundRow row(ResultSet rs) throws SQLException {
        return new RefundRow(rs.getLong("id"), rs.getLong("application_id"), BizRefundType.valueOf(rs.getString("type")), BizRefundStatus.valueOf(rs.getString("status")),
                BizPaymentMethod.valueOf(rs.getString("method")), rs.getLong("paid_amount"), rs.getLong("proposed_amount"), rs.getInt("used_days"), rs.getInt("total_days"),
                (Long) rs.getObject("approved_amount", Long.class), rs.getString("reason"), rs.getString("decision_reason"),
                (Long) rs.getObject("payment_cancel_id", Long.class), rs.getString("transfer_status"), instant(rs.getTimestamp("transferred_at")),
                (Long) rs.getObject("transferred_by", Long.class), rs.getString("transfer_note"), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("decided_at")), (Long) rs.getObject("decided_by", Long.class));
    }
}
