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
 * 대사 이슈({@code reconciliation_issues}) 쓰기 — 콘솔 직접 취소 웹훅(P18, 2a-2)·대사 배치({@code ReconciliationService}, 2a-3)가 열고 운영자가 처리로 닫는다(2a-3).
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

    /**
     * 열린 이슈를 처리로 닫는다(운영자 — 처리자·사유). 열려 있을 때만.
     *
     * @return 닫았으면 true
     */
    public boolean resolve(long id, long operatorId, String note, Instant now) {
        return jdbc.update("UPDATE reconciliation_issues SET status = 'RESOLVED', resolve_note = ?, resolved_by = ?, resolved_at = ? WHERE id = ? AND status = 'OPEN'",
                note, operatorId, ts(now), id) == 1;
    }
}
