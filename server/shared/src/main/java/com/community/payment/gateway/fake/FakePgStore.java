package com.community.payment.gateway.fake;

import com.community.payment.dto.PgCancelStatus;
import com.community.payment.gateway.PgCancellation;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가짜 PG 의 결제 상태({@code fake_pg_payments}, 결정 8) — api·admin 두 서버가 같은 가짜 결제를 보게 DB 에 둔다. local·시험 전용(가짜 게이트웨이가 dev·prod 에서 기동을 막으므로
 * 그 환경에서는 비어 있다). 화면의 가짜 결제창({@code /api/dev/payments})과 시험이 「결제했다」를 여기에 쓴다.
 *
 * <p>Native SQL(JDBC)인 이유: 행 하나를 잠그고 고치는 짧은 일과 {@code jsonb} 취소 목록. 스프링 빈이 아니다 — {@code PaymentConfig} 가 가짜 게이트웨이와 함께 만든다.
 */
public class FakePgStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Map<String, Object>>> CANCELLATIONS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;

    public FakePgStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 결제했다(가짜 결제창의 「결제」) — 같은 결제 ID 가 있으면 덮어쓴다 */
    public void pay(String paymentId, long amount, String currency, Instant paidAt) {
        jdbc.update("INSERT INTO fake_pg_payments (payment_id, status, amount, currency, paid_at, cancelled_amount, cancellations, updated_at) "
                + "VALUES (?, 'PAID', ?, ?, ?, 0, '[]'::jsonb, ?) ON CONFLICT (payment_id) DO UPDATE SET status = 'PAID', amount = EXCLUDED.amount, "
                + "currency = EXCLUDED.currency, paid_at = EXCLUDED.paid_at, cancelled_amount = 0, cancellations = '[]'::jsonb, updated_at = EXCLUDED.updated_at",
                paymentId, amount, currency, Timestamp.from(paidAt), Timestamp.from(paidAt));
    }

    /** 결제 실패·대기 등 결제가 아닌 상태로 둔다(가짜 결제창의 「실패」) */
    public void setStatus(String paymentId, PgPaymentStatus status, long amount, Instant now) {
        jdbc.update("INSERT INTO fake_pg_payments (payment_id, status, amount, currency, updated_at) VALUES (?, ?, ?, 'KRW', ?) "
                + "ON CONFLICT (payment_id) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at", paymentId, status.name(), amount, Timestamp.from(now));
    }

    /**
     * 가짜 결제창의 결과를 <b>처음 한 번만</b> 남긴다(화면의 가짜 결제창 — {@code /api/dev/payments/{paymentId}/fake-checkout}). 그 결제 ID 의 줄이 이미 있으면(결제함·실패·취소)
     * 아무것도 바꾸지 않는다 — 한 문장({@code ON CONFLICT DO NOTHING})이라 동시에 두 번 눌러도 한쪽만 남는다.
     *
     * @param paidAt 결제 시각(PAID 일 때만 — 늦은 결제 흉내면 유효 시각 뒤)
     * @return 이번에 남겼으면 true, 이미 있었으면 false
     */
    public boolean finishCheckoutOnce(String paymentId, PgPaymentStatus status, long amount, String currency, Instant paidAt, Instant now) {
        boolean paid = status == PgPaymentStatus.PAID;
        return jdbc.update("INSERT INTO fake_pg_payments (payment_id, status, amount, currency, paid_at, cancelled_amount, cancellations, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 0, '[]'::jsonb, ?) ON CONFLICT (payment_id) DO NOTHING", paymentId, status.name(), amount, currency,
                paid ? Timestamp.from(paidAt) : null, Timestamp.from(now)) == 1;
    }

    public Optional<PgPayment> find(String paymentId, String storeId) {
        return jdbc.query("SELECT payment_id, status, amount, currency, paid_at, cancelled_amount, cancellations::text, updated_at FROM fake_pg_payments WHERE payment_id = ?",
                (rs, i) -> payment(rs, storeId), paymentId).stream().findFirst();
    }

    /** 상태 바뀐 시각이 [from, until] 인 결제(쪽 차례 — 결제 ID) */
    public List<PgPayment> list(Instant from, Instant until, int offset, int limit, String storeId) {
        return jdbc.query("SELECT payment_id, status, amount, currency, paid_at, cancelled_amount, cancellations::text, updated_at FROM fake_pg_payments "
                        + "WHERE updated_at >= ? AND updated_at <= ? ORDER BY payment_id OFFSET ? LIMIT ?", (rs, i) -> payment(rs, storeId), Timestamp.from(from),
                Timestamp.from(until), offset, limit);
    }

    /**
     * 취소 — 행을 잠그고 포트원과 같은 규칙으로 판단한다: 결제되지 않았으면 {@code PAYMENT_NOT_PAID}, 다 취소됐으면 {@code PAYMENT_ALREADY_CANCELLED},
     * 잔액 검증이 틀리면 {@code CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN}, 넘치면 {@code CANCEL_AMOUNT_EXCEEDS_CANCELLABLE_AMOUNT}. 부르는 쪽이 트랜잭션을 연다.
     *
     * @return 성공이면 취소 내역 아이디, 거절이면 오류 type 을 담은 결과
     */
    public CancelOutcome cancel(String paymentId, long amount, long currentCancellableAmount, String reason, String cancellationId, Instant now) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, amount, cancelled_amount, cancellations::text AS c FROM fake_pg_payments WHERE payment_id = ? FOR UPDATE",
                paymentId);
        if (rows.isEmpty()) {
            return CancelOutcome.rejected("PAYMENT_NOT_FOUND");
        }
        Map<String, Object> row = rows.get(0);
        String status = (String) row.get("status");
        long total = ((Number) row.get("amount")).longValue();
        long cancelled = ((Number) row.get("cancelled_amount")).longValue();
        if ("CANCELLED".equals(status)) {
            return CancelOutcome.rejected("PAYMENT_ALREADY_CANCELLED");
        }
        if (!"PAID".equals(status) && !"PARTIAL_CANCELLED".equals(status)) {
            return CancelOutcome.rejected("PAYMENT_NOT_PAID");
        }
        if (currentCancellableAmount != total - cancelled) {
            return CancelOutcome.rejected("CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN");
        }
        if (amount > total - cancelled) {
            return CancelOutcome.rejected("CANCEL_AMOUNT_EXCEEDS_CANCELLABLE_AMOUNT");
        }
        List<Map<String, Object>> list = new ArrayList<>(JSON.readValue((String) row.get("c"), CANCELLATIONS));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", cancellationId);
        entry.put("amount", amount);
        entry.put("reason", reason);
        entry.put("cancelledAt", now.toString());
        list.add(entry);
        long after = cancelled + amount;
        jdbc.update("UPDATE fake_pg_payments SET cancelled_amount = ?, status = ?, cancellations = CAST(? AS jsonb), updated_at = ? WHERE payment_id = ?", after,
                after == total ? "CANCELLED" : "PARTIAL_CANCELLED", JSON.writeValueAsString(list), Timestamp.from(now), paymentId);
        return CancelOutcome.succeeded(cancellationId);
    }

    /**
     * 콘솔에서 직접 취소한 것처럼 취소를 하나 더한다(시험·흉내 — 잔액 검증 없음).
     *
     * @return 취소 내역 아이디
     */
    public String externalCancel(String paymentId, long amount, String cancellationId, Instant now) {
        CancelOutcome outcome = cancel(paymentId, amount, currentCancellable(paymentId), "콘솔 취소", cancellationId, now);
        if (outcome.errorType() != null) {
            throw new IllegalStateException("가짜 결제를 취소할 수 없다: " + outcome.errorType());
        }
        return outcome.cancellationId();
    }

    private long currentCancellable(String paymentId) {
        Long left = jdbc.queryForObject("SELECT amount - cancelled_amount FROM fake_pg_payments WHERE payment_id = ?", Long.class, paymentId);
        return left == null ? 0 : left;
    }

    private static PgPayment payment(ResultSet rs, String storeId) throws SQLException {
        List<PgCancellation> cancellations = new ArrayList<>();
        for (Map<String, Object> entry : JSON.readValue(rs.getString(7), CANCELLATIONS)) {
            cancellations.add(new PgCancellation((String) entry.get("id"), PgCancelStatus.SUCCEEDED, ((Number) entry.get("amount")).longValue(), (String) entry.get("reason"),
                    Instant.parse((String) entry.get("cancelledAt"))));
        }
        Timestamp paidAt = rs.getTimestamp(5);
        return new PgPayment(rs.getString(1), storeId, PgPaymentStatus.parse(rs.getString(2)), rs.getLong(3), rs.getLong(6), rs.getString(4),
                paidAt == null ? null : paidAt.toInstant(), "fake-tx-" + rs.getString(1), null, "PaymentMethodCard", "FAKE", cancellations,
                rs.getTimestamp(8).toInstant());
    }

    /** 가짜 취소 결과 — 성공이면 아이디, 거절이면 오류 type */
    public record CancelOutcome(String cancellationId, String errorType) {

        static CancelOutcome succeeded(String id) {
            return new CancelOutcome(id, null);
        }

        static CancelOutcome rejected(String type) {
            return new CancelOutcome(null, type);
        }
    }
}
