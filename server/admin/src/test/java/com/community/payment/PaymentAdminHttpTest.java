package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리자 결제 API(커머스 2a-3 — commerce-payment-contract 「2a-1 화면 목업」 API·확정한 목업 가정 1~6, 결정 12·17) — 실제 관리자 앱·보안 필터·권한 인터셉터·PostgreSQL.
 * <ul>
 *   <li>결제 주문 목록·상세(회원은 번호·닉네임만, 상세를 열면 개인정보 열람 감사), 웹훅 수신 내역, 결제 이상(이상 결제 + 멈췄던 취소, 기본 처리 안 된 것만).</li>
 *   <li>다시 시도(GAVE_UP → PENDING, 시도 0)·수동 처리 완료(GAVE_UP → MANUAL, 취소 누계 불변)·포트원에서 다시 확인(확정 코드 · 이미 확정이면 취소 목록 맞춤, 자동 보정 없음) —
 *       모두 {@code commerce.reconcile.manage} + 사유 5~200자 + 감사(돈 관련).</li>
 * </ul>
 */
class PaymentAdminHttpTest extends PaymentAdminTestBase {

    private static final String REASON = "포트원 콘솔에서 확인함";

    private Cookie reader() throws Exception {
        return login(operatorWith(Permission.COMMERCE_PAYMENT_READ));
    }

    @Test
    void 조회는_commerce_payment_read_처리는_commerce_reconcile_manage_가_없으면_403() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = reader();
        Order order = paidOrder(newMember(), 5_000);
        long cancel = gaveUpCancel(order, "REFUND", 1_000, now());

        for (String path : List.of("/api/admin/payments", "/api/admin/payments/" + order.id(), "/api/admin/payment-webhooks", "/api/admin/payment-anomalies")) {
            mvc.perform(get(path).cookie(none)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        }
        mvc.perform(get("/api/admin/payments").cookie(reader)).andExpect(status().isOk());
        for (String path : List.of("/api/admin/payments/" + order.id() + "/recheck", "/api/admin/payment-cancels/" + cancel + "/retry",
                "/api/admin/payment-cancels/" + cancel + "/manual")) {
            mvc.perform(withCsrf(post(path), reader).content("{\"reason\":\"" + REASON + "\"}")).andExpect(status().isForbidden());
        }
        assertThat(cancelRow(cancel).get("status")).isEqualTo("GAVE_UP");
    }

    @Test
    void 결제_주문_목록은_거르기와_쪽을_지키고_회원은_번호와_닉네임만_탈퇴_회원은_번호가_없다() throws Exception {
        Cookie session = reader();
        long member = newMember();
        long gone = withdrawnMember(Instant.now());
        Instant base = Instant.parse("2003-04-05T03:00:00Z");
        Order older = order(member, "PAID", null, 5_000, 0, base);
        Order newer = order(member, "EXPIRED", null, 6_000, 0, base.plusSeconds(60));
        Order late = order(gone, "CANCELED", "LATE_PAYMENT", 7_000, 7_000, base.plusSeconds(120));
        String from = base.toString();
        String to = base.plusSeconds(3600).toString();

        MvcResult all = mvc.perform(get("/api/admin/payments").param("from", from).param("to", to).param("size", "2").cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(Long.toString(late.id()))) // 만든 시각 내림차순
                .andExpect(jsonPath("$.data.items[0].member.id").isEmpty())
                .andExpect(jsonPath("$.data.items[0].member.withdrawn").value(true))
                .andExpect(jsonPath("$.data.items[0].anomaly").value("LATE_PAYMENT"))
                .andExpect(jsonPath("$.data.items[1].id").value(Long.toString(newer.id())))
                .andExpect(jsonPath("$.data.items[1].member.id").value(Long.toString(member)))
                .andExpect(jsonPath("$.data.items[1].member.nickname").isNotEmpty())
                .andExpect(jsonPath("$.data.items[1].paidAmount").isEmpty())
                .andReturn();
        assertThat(all.getResponse().getContentAsString()).doesNotContain("@test.local");
        mvc.perform(get("/api/admin/payments").param("from", from).param("to", to).param("size", "2").param("page", "1").cookie(session))
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(Long.toString(older.id())))
                .andExpect(jsonPath("$.data.items[0].payMethod").value("카드"));
        mvc.perform(get("/api/admin/payments").param("from", from).param("to", to).param("anomaly", "ANY").cookie(session))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(Long.toString(late.id())));
        mvc.perform(get("/api/admin/payments").param("from", from).param("to", to).param("status", "EXPIRED").cookie(session))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(Long.toString(newer.id())));
        mvc.perform(get("/api/admin/payments").param("q", older.paymentId().substring(4, 20)).cookie(session))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(Long.toString(older.id())));
        mvc.perform(get("/api/admin/payments").param("q", Long.toString(member)).cookie(session)).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/admin/payments").param("status", "NOPE").param("size", "500").cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.status").isNotEmpty()).andExpect(jsonPath("$.error.fields.size").isNotEmpty());
    }

    @Test
    void 상세는_이력_취소_웹훅_열린_이슈를_요청_언어로_싣고_열면_개인정보_열람_감사가_남는다() throws Exception {
        long operator = operatorWith(Permission.COMMERCE_PAYMENT_READ);
        Cookie session = login(operator);
        Order order = paidOrder(newMember(), 5_000);
        event(order.id(), "WEBHOOK", "{\"type\": \"Transaction.Paid\"}", "wh-detail-" + order.id(), now());
        long cancel = gaveUpCancel(order, "REFUND", 1_000, now());
        webhookLog("wh-detail-" + order.id(), "Transaction.Paid", order.paymentId(), "PROCESSED", 200, null, now());
        jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, payment_order_id, detected_day, status, created_at) VALUES (?, 'AMOUNT_MISMATCH', ?, ?, "
                + "current_date - 1, 'OPEN', now())", ids.nextId(), order.paymentId(), order.id());

        MvcResult result = mvc.perform(get("/api/admin/payments/" + order.id()).header("Accept-Language", "ko").cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.paymentId").value(order.paymentId()))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.confirmedVia").value("WEBHOOK"))
                .andExpect(jsonPath("$.data.cancelPending").value(true))
                .andExpect(jsonPath("$.data.events[0].kind").value("CREATED"))
                .andExpect(jsonPath("$.data.events[0].detail").value(org.hamcrest.Matchers.containsString("5,000원")))
                .andExpect(jsonPath("$.data.events[1].kind").value("WEBHOOK"))
                .andExpect(jsonPath("$.data.events[1].providerEventId").value("wh-detail-" + order.id()))
                .andExpect(jsonPath("$.data.cancels[0].id").value(Long.toString(cancel)))
                .andExpect(jsonPath("$.data.cancels[0].status").value("GAVE_UP"))
                .andExpect(jsonPath("$.data.cancels[0].nextAttemptAt").isEmpty())
                .andExpect(jsonPath("$.data.cancels[0].lastError").value("PG 거절(가림)"))
                .andExpect(jsonPath("$.data.webhooks[0].webhookId").value("wh-detail-" + order.id()))
                .andExpect(jsonPath("$.data.webhooks[0].paymentOrderId").value(Long.toString(order.id())))
                .andExpect(jsonPath("$.data.openIssueCount").value(1))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("@test.local");
        Map<String, Object> audit = jdbc.queryForMap("SELECT target_type, target_id, retention_class FROM audit_logs WHERE actor_id = ? AND action = 'PAYMENT_ORDER_VIEW' "
                + "ORDER BY id DESC LIMIT 1", operator);
        assertThat(audit.get("target_type")).isEqualTo("PAYMENT_ORDER");
        assertThat(audit.get("target_id")).isEqualTo(Long.toString(order.id()));
        assertThat(audit.get("retention_class")).isEqualTo("PERSONAL_DATA");

        mvc.perform(get("/api/admin/payments/" + ids.nextId()).cookie(session)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_ORDER_NOT_FOUND"));
        mvc.perform(get("/api/admin/payments/abc").cookie(session)).andExpect(status().isNotFound());
    }

    @Test
    void 웹훅_수신_내역은_결과와_번호로_거르고_우리_결제면_주문_번호를_싣는다() throws Exception {
        Cookie session = reader();
        Order order = paidOrder(newMember(), 5_000);
        Instant base = Instant.parse("2004-05-06T00:00:00Z");
        String tag = "wh-list-" + order.id();
        webhookLog(tag + "-a", "Transaction.Paid", order.paymentId(), "PROCESSED", 200, null, base);
        webhookLog(tag + "-b", null, null, "REJECTED", 400, "SIGNATURE_INVALID", base.plusSeconds(10));
        webhookLog(tag + "-c", "Transaction.Paid", "pay_unknownunknownunknownunknown00", "IGNORED", 200, "UNKNOWN_PAYMENT", base.plusSeconds(20));
        String from = base.toString();
        String to = base.plus(Duration.ofHours(1)).toString();

        mvc.perform(get("/api/admin/payment-webhooks").param("from", from).param("to", to).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].webhookId").value(tag + "-c")) // 받은 시각 내림차순
                .andExpect(jsonPath("$.data.items[0].paymentOrderId").isEmpty())
                .andExpect(jsonPath("$.data.items[2].paymentOrderId").value(Long.toString(order.id())))
                .andExpect(jsonPath("$.data.items[1].errorCode").value("SIGNATURE_INVALID"))
                .andExpect(jsonPath("$.data.items[1].httpStatus").value(400));
        mvc.perform(get("/api/admin/payment-webhooks").param("from", from).param("to", to).param("result", "REJECTED").cookie(session))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/admin/payment-webhooks").param("q", order.paymentId()).cookie(session))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/admin/payment-webhooks").param("q", tag + "-b").cookie(session)).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/admin/payment-webhooks").param("result", "WHATEVER").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.result").isNotEmpty());
    }

    @Test
    void 결제_이상은_이상_결제와_멈췄던_취소를_한_목록으로_기본_처리_안_된_것만_보이고_처리_안_된_수를_싣는다() throws Exception {
        Cookie session = reader();
        Instant base = Instant.parse("2005-06-07T01:00:00Z");
        Order late = order(newMember(), "PAID", "LATE_PAYMENT", 5_000, 0, base);
        event(late.id(), "ANOMALY", "{\"anomaly\": \"LATE_PAYMENT\"}", null, base.plusSeconds(31));
        long autoCancel = gaveUpCancel(late, "AUTO_REJECT", 5_000, base.plusSeconds(60));
        Order refunded = order(newMember(), "PAID", null, 8_000, 0, base.plusSeconds(100));
        long refund = gaveUpCancel(refunded, "REFUND", 3_000, base.plusSeconds(200));
        Order handled = order(newMember(), "PAID", null, 9_000, 0, base.plusSeconds(300));
        long manual = gaveUpCancel(handled, "REFUND", 1_000, base.plusSeconds(400));
        jdbc.update("UPDATE payment_cancels SET status = 'MANUAL' WHERE id = ?", manual);
        String from = base.toString();
        String to = base.plus(Duration.ofHours(1)).toString();

        mvc.perform(get("/api/admin/payment-anomalies").param("from", from).param("to", to).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.openCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.items[0].key").value("CANCEL:" + refund)) // 발견 시각 내림차순
                .andExpect(jsonPath("$.data.items[0].kind").value("CANCEL_GAVE_UP"))
                .andExpect(jsonPath("$.data.items[0].cancel.id").value(Long.toString(refund)))
                .andExpect(jsonPath("$.data.items[0].order.id").value(Long.toString(refunded.id())))
                .andExpect(jsonPath("$.data.items[0].resolved").value(false))
                .andExpect(jsonPath("$.data.items[1].key").value("ORDER:" + late.id()))
                .andExpect(jsonPath("$.data.items[1].kind").value("LATE_PAYMENT"))
                .andExpect(jsonPath("$.data.items[1].cancel.id").value(Long.toString(autoCancel)))
                .andExpect(jsonPath("$.data.items[1].detectedAt").value(org.hamcrest.Matchers.startsWith("2005-06-07T01:00:31")));
        mvc.perform(get("/api/admin/payment-anomalies").param("from", from).param("to", to).param("includeResolved", "true").cookie(session))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[2].key").value("CANCEL:" + manual)) // 처리 안 된 것 먼저
                .andExpect(jsonPath("$.data.items[2].resolved").value(true));
        mvc.perform(get("/api/admin/payment-anomalies").param("from", from).param("to", to).param("kind", "LATE_PAYMENT").cookie(session))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/admin/payment-anomalies").param("kind", "NOPE").cookie(session)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.kind").isNotEmpty());
    }

    @Test
    void 다시_시도는_GAVE_UP_을_대기열로_되돌리고_사유와_감사를_남긴다_두_번째는_409() throws Exception {
        long operator = operatorWith(Permission.COMMERCE_PAYMENT_READ, Permission.COMMERCE_RECONCILE_MANAGE);
        Cookie session = login(operator);
        Order order = paidOrder(newMember(), 5_000);
        long cancel = gaveUpCancel(order, "REFUND", 2_000, now().minusSeconds(3600));

        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + cancel + "/retry"), session).content("{\"reason\":\"짧음\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + cancel + "/retry"), session).content("{\"reason\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
        assertThat(cancelRow(cancel).get("status")).isEqualTo("GAVE_UP");

        Instant before = now();
        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + cancel + "/retry"), session).content("{\"reason\":\"  " + REASON + "  \"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.id").value(Long.toString(cancel)))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.attempts").value(0))
                .andExpect(jsonPath("$.data.nextAttemptAt").isNotEmpty())
                .andExpect(jsonPath("$.data.resolvedBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.resolveNote").value(REASON));
        Map<String, Object> row = cancelRow(cancel);
        assertThat(row.get("completed_at")).isNull();
        assertThat(((java.sql.Timestamp) row.get("next_attempt_at")).toInstant()).as("다음 시도 지금").isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(((Number) row.get("resolved_by")).longValue()).isEqualTo(operator);
        assertThat(events(order.id(), "CANCEL_REQUESTED")).isEqualTo(1);
        assertThat(((Number) orderRow(order.id()).get("canceled_amount")).longValue()).isZero();
        Map<String, Object> audit = jdbc.queryForMap("SELECT target_type, target_id, detail, retention_class FROM audit_logs WHERE actor_id = ? AND action = 'PAYMENT_CANCEL_RETRY'",
                operator);
        assertThat(audit.get("target_type")).isEqualTo("PAYMENT_CANCEL");
        assertThat(audit.get("target_id")).isEqualTo(Long.toString(cancel));
        assertThat((String) audit.get("detail")).contains(REASON).contains(order.paymentId());
        assertThat(audit.get("retention_class")).isEqualTo("MONEY");

        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + cancel + "/retry"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PAYMENT_CANCEL_NOT_GAVE_UP"));
        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + cancel + "/manual"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PAYMENT_CANCEL_NOT_GAVE_UP"));
        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + ids.nextId() + "/retry"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PAYMENT_CANCEL_NOT_FOUND"));
    }

    @Test
    void 수동_처리_완료는_표시만_바꾸고_결제의_취소_누계는_그대로다() throws Exception {
        long operator = operatorWith(Permission.COMMERCE_RECONCILE_MANAGE);
        Cookie session = login(operator);
        Order order = paidOrder(newMember(), 5_000);
        long cancel = gaveUpCancel(order, "REFUND", 2_000, now());

        mvc.perform(withCsrf(post("/api/admin/payment-cancels/" + cancel + "/manual"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MANUAL"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.resolveNote").value(REASON));
        Map<String, Object> o = orderRow(order.id());
        assertThat(((Number) o.get("canceled_amount")).longValue()).as("취소 누계 불변").isZero();
        assertThat(o.get("status")).isEqualTo("PAID");
        assertThat(events(order.id(), "CANCEL_MANUAL")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE actor_id = ? AND action = 'PAYMENT_CANCEL_MANUAL'", String.class, operator))
                .isEqualTo("MONEY");
    }

    @Test
    void 포트원에서_다시_확인은_확정_코드를_태우고_결과_셋을_돌려준다() throws Exception {
        long operator = operatorWith(Permission.COMMERCE_PAYMENT_READ, Permission.COMMERCE_RECONCILE_MANAGE);
        Cookie session = login(operator);
        Order paidAtPg = readyOrder(newMember(), 5_000);
        fakePg.pay(paidAtPg.paymentId(), 5_000, "KRW", now());
        Order notPaid = readyOrder(newMember(), 6_000);

        mvc.perform(withCsrf(post("/api/admin/payments/" + paidAtPg.id() + "/recheck"), session).content("{\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
        assertThat(orderRow(paidAtPg.id()).get("status")).as("사유가 틀리면 포트원을 부르지 않는다").isEqualTo("READY");

        mvc.perform(withCsrf(post("/api/admin/payments/" + paidAtPg.id() + "/recheck"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.detail.status").value("PAID"))
                .andExpect(jsonPath("$.data.detail.confirmedVia").value("ADMIN_RECHECK"))
                .andExpect(jsonPath("$.data.detail.anomaly").isEmpty());
        assertThat(events(paidAtPg.id(), "RECHECKED")).isEqualTo(1);
        Map<String, Object> audit = jdbc.queryForMap("SELECT target_type, target_id, detail, retention_class FROM audit_logs WHERE actor_id = ? AND action = 'PAYMENT_RECHECK'",
                operator);
        assertThat(audit.get("target_type")).isEqualTo("PAYMENT_ORDER");
        assertThat((String) audit.get("detail")).contains("CONFIRMED").contains(REASON);
        assertThat(audit.get("retention_class")).isEqualTo("MONEY");

        mvc.perform(withCsrf(post("/api/admin/payments/" + notPaid.id() + "/recheck"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("PG_NOT_PAID"))
                .andExpect(jsonPath("$.data.detail.status").value("READY"));
        mvc.perform(withCsrf(post("/api/admin/payments/" + ids.nextId() + "/recheck"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PAYMENT_ORDER_NOT_FOUND"));
    }

    @Test
    void 만료된_결제가_포트원에서_결제됐으면_늦은_결제로_확정하고_자동_취소한다() throws Exception {
        Cookie session = login(operatorWith(Permission.COMMERCE_RECONCILE_MANAGE));
        Order expired = order(newMember(), "EXPIRED", null, 5_000, 0, now().minusSeconds(3600));
        fakePg.pay(expired.paymentId(), 5_000, "KRW", now());

        mvc.perform(withCsrf(post("/api/admin/payments/" + expired.id() + "/recheck"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.detail.anomaly").value("LATE_PAYMENT"))
                .andExpect(jsonPath("$.data.detail.cancels[0].kind").value("AUTO_REJECT"));
    }

    @Test
    void 이미_확정된_결제는_포트원_취소_목록만_맞추고_우리_것이_아닌_취소는_이슈로만_남긴다() throws Exception {
        Cookie session = login(operatorWith(Permission.COMMERCE_RECONCILE_MANAGE));
        Order paid = paidOrder(newMember(), 5_000);
        fakePg.pay(paid.paymentId(), 5_000, "KRW", now());
        fakePg.externalCancel(paid.paymentId(), 2_000, "ext-" + paid.id(), now());

        mvc.perform(withCsrf(post("/api/admin/payments/" + paid.id() + "/recheck"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("UNCHANGED"))
                .andExpect(jsonPath("$.data.detail.canceledAmount").value(0))
                .andExpect(jsonPath("$.data.detail.status").value("PAID"))
                .andExpect(jsonPath("$.data.detail.openIssueCount").value(1));
        assertThat(events(paid.id(), "EXTERNAL_CANCEL")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT type FROM reconciliation_issues WHERE payment_id = ? AND status = 'OPEN'", String.class, paid.paymentId()))
                .isEqualTo("CANCEL_MISMATCH");
    }

    @Test
    void 멈춘_취소를_포트원이_실제로_취소했으면_다시_확인이_그_줄을_SUCCEEDED_로_맞춘다() throws Exception {
        Cookie session = login(operatorWith(Permission.COMMERCE_RECONCILE_MANAGE));
        Order paid = paidOrder(newMember(), 5_000);
        long cancel = gaveUpCancel(paid, "REFUND", 2_000, now());
        fakePg.pay(paid.paymentId(), 5_000, "KRW", now());
        fakePg.cancel(paid.paymentId(), 2_000, 5_000, "roboyam REFUND rc:" + cancel, "pgc-" + cancel, now()); // 응답은 못 받았지만 포트원은 취소했다

        mvc.perform(withCsrf(post("/api/admin/payments/" + paid.id() + "/recheck"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("UNCHANGED"))
                .andExpect(jsonPath("$.data.detail.canceledAmount").value(2000))
                .andExpect(jsonPath("$.data.detail.cancels[0].status").value("SUCCEEDED"));
        assertThat(cancelRow(cancel).get("status")).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_issues WHERE payment_id = ?", Integer.class, paid.paymentId())).as("우리 취소는 이슈가 아니다")
                .isZero();
    }
}
