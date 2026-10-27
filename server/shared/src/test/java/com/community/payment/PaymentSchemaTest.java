package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V97 결제 모듈 표의 제약(계약 「표」·결정 3, commerce.md 8-1) — 실제 PostgreSQL 에 직접 넣어 본다. CHECK(취소 ≤ 결제·결제 계열 칸·통화·이상 값),
 * payment_id 유일·형식, <b>받아들인 결제는 주문마다 하나(이상 결제는 세지 않는다)</b>, webhook-id 유일, request_key·pg_cancel_id 유일, 열린 대사 이슈 유일, FK 없음.
 */
class PaymentSchemaTest {

    private static PaymentTestKit kit;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
        jdbc = kit.jdbc;
    }

    private static String paymentId() {
        String base = Long.toString(kit.ids.nextId(), 36);
        return "pay_" + (base + "abcdefghijklmnopqrstuvwxyzABCDEF").substring(0, 32);
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    /** 결제 주문 한 줄을 넣는다 — status·paid_amount·canceled·anomaly·paid_at 를 정해서 */
    private static long insertOrder(String paymentId, long orderRefId, String status, Long paidAmount, long canceled, String anomaly, boolean paidAt, String currency) {
        long id = kit.ids.nextId();
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, currency, status, paid_amount, canceled_amount, "
                        + "anomaly, paid_at, confirmed_via, expires_at, created_at, updated_at) VALUES (?, ?, ?, 'PAYMENT_TEST', ?, '시험', 1000, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, paymentId, 1L, orderRefId, currency, status, paidAmount, canceled, anomaly, paidAt ? now() : null, paidAt ? "WEBHOOK" : null, now(), now(), now());
        return id;
    }

    private static long readyOrder(long orderRefId) {
        return insertOrder(paymentId(), orderRefId, "READY", null, 0, null, false, "KRW");
    }

    @Test
    void 결제_ID_는_유일하고_pay_뒤_32자_영숫자다() {
        String id = paymentId();
        insertOrder(id, kit.ids.nextId(), "READY", null, 0, null, false, "KRW");
        assertThatThrownBy(() -> insertOrder(id, kit.ids.nextId(), "READY", null, 0, null, false, "KRW")).isInstanceOf(DataIntegrityViolationException.class);
        for (String bad : List.of("pay_short", "pi_" + "a".repeat(33), "pay_" + "a".repeat(31) + "-", "pay_" + "a".repeat(33))) {
            assertThatThrownBy(() -> insertOrder(bad, kit.ids.nextId(), "READY", null, 0, null, false, "KRW")).as(bad).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void 취소_누계는_0_이상_결제_금액_이하다() {
        assertThatCode(() -> insertOrder(paymentId(), kit.ids.nextId(), "CANCELED", 1000L, 1000, null, true, "KRW")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), "PARTIAL_CANCELED", 1000L, 1001, null, true, "KRW"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), "PAID", 1000L, -1, null, true, "KRW")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), "READY", null, 1, null, false, "KRW")).as("결제 전 취소").isInstanceOf(DataIntegrityViolationException.class);
        // 조건부 UPDATE 밖에서 고쳐도 DB 가 막는다
        long id = insertOrder(paymentId(), kit.ids.nextId(), "PAID", 1000L, 0, null, true, "KRW");
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_orders SET canceled_amount = 2000 WHERE id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 결제된_계열이면_결제_금액_결제_시각_확정_길이_반드시_있다() {
        for (String status : List.of("PAID", "PARTIAL_CANCELED", "CANCELED")) {
            assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), status, null, 0, null, true, "KRW")).as(status + " 금액 없음")
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), status, 1000L, 0, null, false, "KRW")).as(status + " 시각 없음")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void 통화는_KRW_상태_이상_종류_확정_길은_정해진_값만() {
        assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), "READY", null, 0, null, false, "USD")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), "FAILED", null, 0, null, false, "KRW")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), "PAID", 1000L, 0, "WEIRD", true, "KRW")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrder(paymentId(), kit.ids.nextId(), "READY", null, 0, "LATE_PAYMENT", false, "KRW")).as("결제 전 이상")
                .isInstanceOf(DataIntegrityViolationException.class);
        long id = readyOrder(kit.ids.nextId());
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_orders SET order_type = 'SHOP' WHERE id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_orders SET amount = 0 WHERE id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 받아들인_결제는_주문마다_하나이고_이상_결제는_세지_않는다() {
        long ref = kit.ids.nextId();
        insertOrder(paymentId(), ref, "PAID", 1000L, 0, null, true, "KRW");
        assertThatThrownBy(() -> insertOrder(paymentId(), ref, "PAID", 1000L, 0, null, true, "KRW")).as("두 번째 정상 결제")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOrder(paymentId(), ref, "PARTIAL_CANCELED", 1000L, 10, null, true, "KRW")).as("부분 취소도 받아들인 결제")
                .isInstanceOf(DataIntegrityViolationException.class);
        // 이상 결제(늦은 결제·도메인 거절·금액 불일치)와 끝난 결제·READY 는 몇 개라도
        assertThatCode(() -> {
            insertOrder(paymentId(), ref, "PAID", 1000L, 0, "LATE_PAYMENT", true, "KRW");
            insertOrder(paymentId(), ref, "PAID", 1000L, 0, "DOMAIN_REJECTED", true, "KRW");
            insertOrder(paymentId(), ref, "PARTIAL_CANCELED", 1000L, 10, "AMOUNT_MISMATCH", true, "KRW");
            insertOrder(paymentId(), ref, "CANCELED", 1000L, 1000, null, true, "KRW");
            readyOrder(ref);
            insertOrder(paymentId(), ref, "EXPIRED", null, 0, null, false, "KRW");
        }).doesNotThrowAnyException();
    }

    private static long insertCancel(long orderId, String requestKey, String pgCancelId, String status, long amount) {
        long id = kit.ids.nextId();
        boolean done = List.of("SUCCEEDED", "GAVE_UP", "MANUAL").contains(status);
        boolean sending = "SENDING".equals(status);
        jdbc.update("INSERT INTO payment_cancels (id, payment_order_id, amount, reason, kind, request_key, status, pg_cancel_id, attempts, next_attempt_at, locked_by, "
                        + "locked_at, created_at, completed_at) VALUES (?, ?, ?, '시험', 'ADMIN', ?, ?, ?, 0, ?, ?, ?, ?, ?)", id, orderId, amount, requestKey, status, pgCancelId,
                now(), sending ? 1 : null, sending ? now() : null, now(), done ? now() : null);
        return id;
    }

    @Test
    void 취소_대기열은_요청_키와_포트원_취소_ID_가_유일하고_상태_칸이_맞물린다() {
        long order = readyOrder(kit.ids.nextId());
        String key = "ADMIN:" + kit.ids.nextId();
        insertCancel(order, key, null, "PENDING", 100);
        assertThatThrownBy(() -> insertCancel(order, key, null, "PENDING", 100)).isInstanceOf(DataIntegrityViolationException.class);
        String pg = "pgc-" + kit.ids.nextId();
        insertCancel(order, "ADMIN:" + kit.ids.nextId(), pg, "SUCCEEDED", 100);
        assertThatThrownBy(() -> insertCancel(order, "ADMIN:" + kit.ids.nextId(), pg, "SUCCEEDED", 100)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> {
            insertCancel(order, "ADMIN:" + kit.ids.nextId(), null, "PENDING", 1);
            insertCancel(order, "ADMIN:" + kit.ids.nextId(), null, "PENDING", 1); // pg_cancel_id 없는 줄은 여럿
        }).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertCancel(order, "ADMIN:" + kit.ids.nextId(), null, "PENDING", 0)).as("금액 0").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCancel(order, "ADMIN:" + kit.ids.nextId(), null, "REQUESTED", 1)).as("우리 상태가 아님")
                .isInstanceOf(DataIntegrityViolationException.class);
        long pending = insertCancel(order, "ADMIN:" + kit.ids.nextId(), null, "PENDING", 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_cancels SET status = 'SENDING' WHERE id = ?", pending)).as("SENDING 인데 집은 서버 없음")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_cancels SET status = 'SUCCEEDED' WHERE id = ?", pending)).as("끝났는데 끝난 시각 없음")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_cancels SET kind = 'MISC' WHERE id = ?", pending)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_cancels SET pg_status = 'DONE' WHERE id = ?", pending)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_웹훅_ID_는_이력에_한_번만() {
        long order = readyOrder(kit.ids.nextId());
        String webhookId = "msg_" + kit.ids.nextId();
        jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, provider_event_id, created_at) VALUES (?, ?, 'WEBHOOK', ?, now())", kit.ids.nextId(), order,
                webhookId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, provider_event_id, created_at) VALUES (?, ?, 'WEBHOOK', ?, now())",
                kit.ids.nextId(), order, webhookId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> {
            jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, created_at) VALUES (?, ?, 'PAID', now())", kit.ids.nextId(), order);
            jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, created_at) VALUES (?, ?, 'PAID', now())", kit.ids.nextId(), order);
        }).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, created_at) VALUES (?, ?, 'OOPS', now())", kit.ids.nextId(), order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 열린_대사_이슈는_종류_결제마다_하나이고_처리하면_다시_열_수_있다() {
        String pay = paymentId();
        long first = kit.ids.nextId();
        jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, detected_day, status, created_at) VALUES (?, 'CANCEL_MISMATCH', ?, current_date, 'OPEN', now())",
                first, pay);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, detected_day, status, created_at) "
                + "VALUES (?, 'CANCEL_MISMATCH', ?, current_date, 'OPEN', now())", kit.ids.nextId(), pay)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, detected_day, status, created_at) "
                + "VALUES (?, 'AMOUNT_MISMATCH', ?, current_date, 'OPEN', now())", kit.ids.nextId(), pay)).as("다른 종류").doesNotThrowAnyException();
        jdbc.update("UPDATE reconciliation_issues SET status = 'RESOLVED', resolved_at = now() WHERE id = ?", first);
        assertThatCode(() -> jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, detected_day, status, created_at) "
                + "VALUES (?, 'CANCEL_MISMATCH', ?, current_date, 'OPEN', now())", kit.ids.nextId(), pay)).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update("UPDATE reconciliation_issues SET resolved_at = NULL WHERE id = ?", first)).as("처리했는데 처리 시각 없음")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 새_표에는_다른_모듈_표로_가는_외래키가_없다() {
        Integer foreignKeys = jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY' AND table_name IN "
                + "('payment_orders', 'payment_cancels', 'payment_events', 'payment_webhook_logs', 'reconciliation_issues', 'payment_reconcile_days', 'fake_pg_payments')",
                Integer.class);
        assertThat(foreignKeys).isZero();
        Integer tables = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('payment_orders', 'payment_cancels', 'payment_events', "
                + "'payment_webhook_logs', 'reconciliation_issues', 'payment_reconcile_days', 'fake_pg_payments')", Integer.class);
        assertThat(tables).isEqualTo(7);
    }

    @Test
    void 웹훅_기록과_가짜_PG_의_값_검사() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_webhook_logs (id, result, http_status, received_at) VALUES (?, 'MAYBE', 200, now())", kit.ids.nextId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        String pay = paymentId();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO fake_pg_payments (payment_id, status, amount, currency, cancelled_amount, updated_at) VALUES (?, 'PAID', 100, 'KRW', 101, now())",
                pay)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_reconcile_days (day, status) VALUES (DATE '1980-01-01', 'MAYBE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
