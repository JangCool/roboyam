package com.community.payment.repository.master;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 결제 주문({@code payment_orders}) 읽기·쓰기. 상태 전이는 모두 한 문장 조건부 UPDATE({@code … WHERE id = ? AND status IN (…)}) — 이긴 쪽만 바꾼다(commerce.md 3절).
 *
 * <p>Native SQL(JDBC)인 이유: 조건부 상태 전이·{@code FOR UPDATE} 행 잠금·{@code RETURNING}·부분 유일 색인을 쓰고, 배치 서버로 뗄 때 JPA 구성 없이 돌게 한다(계약 「패키지」).
 * 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class PaymentOrderStore {

    private static final String COLUMNS = "id, payment_id, user_id, order_type, order_ref_id, order_name, amount, currency, status, paid_amount, canceled_amount, anomaly, "
            + "pay_method, pg_provider, pg_tx_id, receipt_url, paid_at, expires_at, confirmed_via, created_at, updated_at";

    /** 결제 주문 한 줄 */
    public record OrderRow(long id, String paymentId, long userId, PaymentOrderType orderType, long orderRefId, String orderName, long amount, String currency,
                           PaymentStatus status, Long paidAmount, long canceledAmount, PaymentAnomaly anomaly, String payMethod, String pgProvider, String pgTxId,
                           String receiptUrl, Instant paidAt, Instant expiresAt, ConfirmedVia confirmedVia, Instant createdAt, Instant updatedAt) {

        /** 받아들인 결제인가(결제된 계열이고 이상이 없다) */
        public boolean accepted() {
            return status.paid() && anomaly == null;
        }

        /** 남은 취소 가능 금액(결제 − 취소됨) — 결제되지 않았으면 0 */
        public long cancellable() {
            return paidAmount == null ? 0 : paidAmount - canceledAmount;
        }
    }

    /** 새 결제 주문 */
    public record NewOrder(long id, String paymentId, long userId, PaymentOrderType orderType, long orderRefId, String orderName, long amount, Instant expiresAt,
                           Instant now) {
    }

    /** 결제 확인 값(포트원 조회 결과에서) */
    public record PaidFields(long paidAmount, Instant paidAt, String payMethod, String pgProvider, String pgTxId, String receiptUrl) {
    }

    private final JdbcTemplate jdbc;

    public PaymentOrderStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(NewOrder order) {
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, currency, status, expires_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'KRW', 'READY', ?, ?, ?)", order.id(), order.paymentId(), order.userId(), order.orderType().name(),
                order.orderRefId(), order.orderName(), order.amount(), ts(order.expiresAt()), ts(order.now()), ts(order.now()));
    }

    public Optional<OrderRow> findByPaymentId(String paymentId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_orders WHERE payment_id = ?", PaymentOrderStore::row, paymentId).stream().findFirst();
    }

    public Optional<OrderRow> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_orders WHERE id = ?", PaymentOrderStore::row, id).stream().findFirst();
    }

    /** 행을 잠그고 읽는다(트랜잭션 안에서만 의미가 있다) */
    public Optional<OrderRow> lockById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_orders WHERE id = ? FOR UPDATE", PaymentOrderStore::row, id).stream().findFirst();
    }

    /** 같은 주문의 열린 READY 를 모두 EXPIRED 로 닫는다(재결제마다 새 번호 — commerce.md 8-1). 닫은 결제 주문 ID */
    public List<Long> expireOpenReady(PaymentOrderType orderType, long orderRefId, Instant now) {
        return jdbc.queryForList("UPDATE payment_orders SET status = 'EXPIRED', updated_at = ? WHERE order_type = ? AND order_ref_id = ? AND status = 'READY' RETURNING id",
                Long.class, ts(now), orderType.name(), orderRefId);
    }

    /**
     * READY·EXPIRED → PAID(결제 확인 값·이상·확정 길). 이긴 쪽만 true. 이상이 있으면 「받아들인 결제 하나」 색인에 세지 않는다(결정 3).
     */
    public boolean markPaid(long id, PaidFields paid, PaymentAnomaly anomaly, ConfirmedVia via, Instant now) {
        return jdbc.update("UPDATE payment_orders SET status = 'PAID', paid_amount = ?, paid_at = ?, pay_method = ?, pg_provider = ?, pg_tx_id = ?, receipt_url = ?, "
                        + "anomaly = ?, confirmed_via = ?, updated_at = ? WHERE id = ? AND status IN ('READY', 'EXPIRED')", paid.paidAmount(), ts(paid.paidAt()),
                cut(paid.payMethod(), 40), cut(paid.pgProvider(), 40), cut(paid.pgTxId(), 200), cut(paid.receiptUrl(), 1000), anomaly == null ? null : anomaly.name(),
                via.name(), ts(now), id) == 1;
    }

    /** 받아들인 결제에 이상을 표시한다(도메인 거절) — 이상이 없던 결제된 계열만 */
    public boolean markAnomaly(long id, PaymentAnomaly anomaly, Instant now) {
        return jdbc.update("UPDATE payment_orders SET anomaly = ?, updated_at = ? WHERE id = ? AND anomaly IS NULL AND status IN ('PAID', 'PARTIAL_CANCELED')",
                anomaly.name(), ts(now), id) == 1;
    }

    /** READY → EXPIRED(READY 정리). 이긴 쪽만 true */
    public boolean expireReady(long id, Instant now) {
        return jdbc.update("UPDATE payment_orders SET status = 'EXPIRED', updated_at = ? WHERE id = ? AND status = 'READY'", ts(now), id) == 1;
    }

    /**
     * 취소 누계를 조건부로 늘린다({@code canceled_amount + amount ≤ paid_amount} 일 때만 — commerce.md 8-3) — 전액이면 CANCELED, 아니면 PARTIAL_CANCELED.
     *
     * @return 늘렸으면 true(넘치면 false — 아무것도 바꾸지 않는다)
     */
    public boolean addCanceled(long id, long amount, Instant now) {
        return jdbc.update("UPDATE payment_orders SET canceled_amount = canceled_amount + ?, "
                        + "status = CASE WHEN canceled_amount + ? = paid_amount THEN 'CANCELED' ELSE 'PARTIAL_CANCELED' END, updated_at = ? "
                        + "WHERE id = ? AND status IN ('PAID', 'PARTIAL_CANCELED') AND canceled_amount + ? <= paid_amount", amount, amount, ts(now), id, amount) == 1;
    }

    /**
     * READY 정리 대상 — 유효 시각이 {@code cutoff} 이하인 READY 전부(「아직 처리 안 한 것 전부」), (유효 시각, ID) 키셋 차례.
     *
     * @param afterExpiresAt 이 키 다음부터(null 이면 처음부터)
     */
    public List<DueReady> dueReady(Instant cutoff, Instant afterExpiresAt, long afterId, int limit) {
        if (afterExpiresAt == null) {
            return jdbc.query("SELECT id, payment_id, expires_at FROM payment_orders WHERE status = 'READY' AND expires_at <= ? ORDER BY expires_at, id LIMIT ?",
                    PaymentOrderStore::due, ts(cutoff), limit);
        }
        return jdbc.query("SELECT id, payment_id, expires_at FROM payment_orders WHERE status = 'READY' AND expires_at <= ? AND (expires_at, id) > (?, ?) "
                + "ORDER BY expires_at, id LIMIT ?", PaymentOrderStore::due, ts(cutoff), ts(afterExpiresAt), afterId, limit);
    }

    /** 「실패 건만 다시」 — 결제 주문 ID 로 다시 읽는다(아직 READY 이고 기준 시각이 지난 것만) */
    public List<DueReady> dueReadyByIds(Instant cutoff, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT id, payment_id, expires_at FROM payment_orders WHERE status = 'READY' AND expires_at <= ? AND id = ANY (?) ORDER BY expires_at, id",
                PaymentOrderStore::due, ts(cutoff), ids.toArray(Long[]::new));
    }

    /** READY 정리 대상 한 건 */
    public record DueReady(long id, String paymentId, Instant expiresAt) {
    }

    private static DueReady due(ResultSet rs, int i) throws SQLException {
        return new DueReady(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant());
    }

    static OrderRow row(ResultSet rs, int i) throws SQLException {
        return new OrderRow(rs.getLong("id"), rs.getString("payment_id"), rs.getLong("user_id"), PaymentOrderType.valueOf(rs.getString("order_type")),
                rs.getLong("order_ref_id"), rs.getString("order_name"), rs.getLong("amount"), rs.getString("currency"), PaymentStatus.valueOf(rs.getString("status")),
                rs.getObject("paid_amount") == null ? null : rs.getLong("paid_amount"), rs.getLong("canceled_amount"),
                rs.getString("anomaly") == null ? null : PaymentAnomaly.valueOf(rs.getString("anomaly")), rs.getString("pay_method"), rs.getString("pg_provider"),
                rs.getString("pg_tx_id"), rs.getString("receipt_url"), instant(rs.getTimestamp("paid_at")), instant(rs.getTimestamp("expires_at")),
                rs.getString("confirmed_via") == null ? null : ConfirmedVia.valueOf(rs.getString("confirmed_via")), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
