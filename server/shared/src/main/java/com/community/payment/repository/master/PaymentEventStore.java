package com.community.payment.repository.master;

import static com.community.payment.repository.master.PaymentOrderStore.ts;

import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 이력({@code payment_events}) — 결제 한 건의 일(만듦·결제·이상·만료·취소·웹훅)을 남긴다. 거래 기록이라 5년 보관(commerce.md 17-3), {@code detail} 에 개인정보를 넣지 않는다.
 * 웹훅은 {@code provider_event_id}(webhook-id) 부분 유일로 한 번만 남는다.
 *
 * <p>Native SQL(JDBC)인 이유: {@code jsonb} 칸과 부분 유일 색인의 {@code ON CONFLICT … DO NOTHING} 을 쓴다.
 */
@Repository
public class PaymentEventStore {

    /** 이력 종류(DB CHECK 와 같은 목록 — RECHECKED 는 V98, 운영자의 「포트원에서 다시 확인」) */
    public enum Kind {
        CREATED, PAID, ANOMALY, EXPIRED, CANCEL_REQUESTED, CANCEL_SUCCEEDED, CANCEL_RETRY, CANCEL_GAVE_UP, CANCEL_MANUAL, EXTERNAL_CANCEL, WEBHOOK, RECHECKED
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    public PaymentEventStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 이력 한 줄 */
    public void insert(long id, long paymentOrderId, Kind kind, Map<String, Object> detail, Instant now) {
        jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, detail, created_at) VALUES (?, ?, ?, CAST(? AS jsonb), ?)", id, paymentOrderId, kind.name(),
                json(detail), ts(now));
    }

    /**
     * 처리한 웹훅을 남긴다 — 같은 webhook-id 가 이미 있으면 넣지 않는다.
     *
     * @return 넣었으면 true
     */
    public boolean insertWebhook(long id, long paymentOrderId, String webhookId, Map<String, Object> detail, Instant now) {
        return jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, provider_event_id, detail, created_at) VALUES (?, ?, 'WEBHOOK', ?, CAST(? AS jsonb), ?) "
                + "ON CONFLICT (provider_event_id) WHERE provider_event_id IS NOT NULL DO NOTHING", id, paymentOrderId, webhookId, json(detail), ts(now)) == 1;
    }

    /** 그 webhook-id 를 이미 처리했는가 */
    public boolean webhookProcessed(String webhookId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM payment_events WHERE provider_event_id = ?)", Boolean.class, webhookId);
        return Boolean.TRUE.equals(found);
    }

    /** 그 포트원 취소 내역을 「우리 것이 아닌 취소」로 이미 남겼는가(같은 콘솔 취소가 다음 웹훅에도 실려 온다) */
    public boolean externalCancelRecorded(long paymentOrderId, String pgCancelId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM payment_events WHERE payment_order_id = ? AND kind = 'EXTERNAL_CANCEL' "
                + "AND detail ->> 'pgCancelId' = ?)", Boolean.class, paymentOrderId, pgCancelId);
        return Boolean.TRUE.equals(found);
    }

    private static String json(Map<String, Object> detail) {
        return detail == null || detail.isEmpty() ? null : JSON.writeValueAsString(detail);
    }
}
