package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.ts;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 대사가 우리 쪽에서 읽는 것 — 처음 결제가 생긴 순간, 그 날 결제되거나 취소가 끝난 우리 결제(포트원 목록에 없던 우리 쪽 변화를 단건 조회로 확인한다).
 *
 * <p>Native SQL(JDBC)인 이유: 결제 주문·취소 대기열 두 표의 {@code UNION} 을 쓰고, 결제 모듈의 다른 저장소처럼 JDBC 로 둔다.
 */
@Repository
public class PaymentReconcileQueries {

    private final JdbcTemplate jdbc;

    public PaymentReconcileQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 가장 오래된 결제 주문을 만든 순간(없으면 빈 값) — 처음 대사할 때 「아직 안 한 날」의 시작 */
    public Optional<Instant> firstOrderAt() {
        Timestamp at = jdbc.queryForObject("SELECT min(created_at) FROM payment_orders", Timestamp.class);
        return Optional.ofNullable(at).map(Timestamp::toInstant);
    }

    /** [from, until) 에 결제됐거나(결제된 계열) 성공한 취소가 끝난 우리 결제의 결제 ID(차례 — 결제 ID) */
    public List<String> localChanges(Instant from, Instant until) {
        return jdbc.queryForList("SELECT payment_id FROM payment_orders WHERE paid_at >= ? AND paid_at < ? AND status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED') "
                + "UNION SELECT o.payment_id FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id "
                + "WHERE c.status = 'SUCCEEDED' AND c.completed_at >= ? AND c.completed_at < ? ORDER BY 1", String.class, ts(from), ts(until), ts(from), ts(until));
    }
}
