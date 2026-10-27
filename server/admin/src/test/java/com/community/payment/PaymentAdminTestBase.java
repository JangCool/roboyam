package com.community.payment;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.payment.gateway.fake.FakePgStore;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

/**
 * 관리자 결제 API 시험의 바탕(커머스 2a-3) — 관리자 앱 문맥 전체 + 실제 PostgreSQL({@link Commerce04AdminTestBase}). 이 앱은 local 설정이라 결제 게이트웨이가 가짜(가짜 PG 표)이고
 * 시험 주문 처리기가 켜져 있다. 결제 주문·취소·이력·웹훅 기록은 표에 바로 넣는다(겹치지 않는 Snowflake ID, 결제 번호는 무작위 — 표를 비우지 않는다).
 */
abstract class PaymentAdminTestBase extends Commerce04AdminTestBase {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    protected FakePgStore fakePg;

    @BeforeEach
    void paymentBaseSetUp() {
        fakePg = new FakePgStore(dataSource);
    }

    protected static String paymentId() {
        StringBuilder id = new StringBuilder("pay_");
        for (int i = 0; i < 32; i++) {
            id.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return id.toString();
    }

    protected static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 결제 주문 한 줄 — 결제된 계열이면 결제액·결제 시각·확정한 길을 채운다 */
    protected record Order(long id, String paymentId, long userId, long amount) {
    }

    protected Order order(long userId, String status, String anomaly, long amount, long canceled, Instant createdAt) {
        long id = ids.nextId();
        String paymentId = paymentId();
        boolean paid = List.of("PAID", "PARTIAL_CANCELED", "CANCELED").contains(status);
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, currency, status, paid_amount, canceled_amount, anomaly, "
                        + "pay_method, pg_provider, pg_tx_id, paid_at, expires_at, confirmed_via, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PAYMENT_TEST', ?, '시험 결제', ?, 'KRW', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, paymentId, userId, ids.nextId(), amount, status, paid ? amount : null, canceled, anomaly, paid ? "PaymentMethodCard" : null, paid ? "FAKE" : null,
                paid ? "fake-tx-" + id : null, paid ? Timestamp.from(createdAt.plusSeconds(30)) : null, Timestamp.from(createdAt.plusSeconds(300)),
                paid ? "WEBHOOK" : null, Timestamp.from(createdAt), Timestamp.from(createdAt));
        event(id, "CREATED", "{\"amount\": " + amount + ", \"expiresAt\": \"" + createdAt.plusSeconds(300) + "\"}", null, createdAt);
        return new Order(id, paymentId, userId, amount);
    }

    protected Order readyOrder(long userId, long amount) {
        return order(userId, "READY", null, amount, 0, now());
    }

    protected Order paidOrder(long userId, long amount) {
        return order(userId, "PAID", null, amount, 0, now());
    }

    protected void event(long orderId, String kind, String detailJson, String providerEventId, Instant at) {
        jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, provider_event_id, detail, created_at) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)", ids.nextId(),
                orderId, kind, providerEventId, detailJson, Timestamp.from(at));
    }

    /** 재시도를 다 쓴(GAVE_UP) 취소 + 그 이력(결제 이상 목록의 출처) */
    protected long gaveUpCancel(Order order, String kind, long amount, Instant at) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO payment_cancels (id, payment_order_id, amount, reason, kind, request_key, status, pg_status, attempts, next_attempt_at, last_error, created_at, "
                        + "completed_at) VALUES (?, ?, ?, '내부 사유', ?, ?, 'GAVE_UP', 'FAILED', 6, ?, 'PG 거절(가림)', ?, ?)",
                id, order.id(), amount, kind, kind.equals("AUTO_REJECT") ? "AUTO_REJECT:" + order.id() : kind + ":" + id, Timestamp.from(at), Timestamp.from(at),
                Timestamp.from(at));
        event(order.id(), "CANCEL_GAVE_UP", "{\"cancelId\": \"" + id + "\", \"attempts\": 6, \"error\": \"PG 거절(가림)\"}", null, at);
        return id;
    }

    protected void webhookLog(String webhookId, String type, String paymentId, String result, int httpStatus, String errorCode, Instant at) {
        jdbc.update("INSERT INTO payment_webhook_logs (id, webhook_id, event_type, payment_id, result, http_status, error_code, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ids.nextId(), webhookId, type, paymentId, result, httpStatus, errorCode, Timestamp.from(at));
    }

    protected Map<String, Object> cancelRow(long id) {
        return jdbc.queryForMap("SELECT * FROM payment_cancels WHERE id = ?", id);
    }

    protected Map<String, Object> orderRow(long id) {
        return jdbc.queryForMap("SELECT * FROM payment_orders WHERE id = ?", id);
    }

    protected int events(long orderId, String kind) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_events WHERE payment_order_id = ? AND kind = ?", Integer.class, orderId, kind);
    }
}
