package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.ts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대사 이슈({@code reconciliation_issues}) 쓰기 — 2a-2 는 콘솔 직접 취소 웹훅(P18)의 {@code CANCEL_MISMATCH} 만 남긴다(대사 배치·처리 API 는 2a-3).
 * 열린 이슈는 (종류, 결제) 하나 — 같은 것이 다시 와도 새 줄을 만들지 않는다. 자동 보정하지 않는다(commerce.md 16-1).
 */
@Repository
public class ReconciliationIssueStore {

    /** 이슈 종류(DB CHECK 와 같은 목록) */
    public enum Type {
        PG_PAID_LOCAL_UNPAID, LOCAL_PAID_PG_MISSING, AMOUNT_MISMATCH, CANCEL_MISMATCH, PG_ONLY
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    public ReconciliationIssueStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 열린 이슈를 남긴다 — 같은 (종류, 결제)의 열린 이슈가 있으면 넣지 않는다.
     *
     * @return 넣었으면 true
     */
    public boolean open(long id, Type type, String paymentId, Long paymentOrderId, LocalDate detectedDay, Map<String, Object> detail, Instant now) {
        return jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, payment_order_id, detected_day, detail, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), 'OPEN', ?) ON CONFLICT (type, payment_id) WHERE status = 'OPEN' DO NOTHING", id, type.name(), paymentId,
                paymentOrderId, java.sql.Date.valueOf(detectedDay), detail == null || detail.isEmpty() ? null : JSON.writeValueAsString(detail), ts(now)) == 1;
    }
}
