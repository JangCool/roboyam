package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.batch.core.BatchMessageCodec;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.http.Cookie;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 관리자 대사 API(커머스 2a-3 — commerce.md 16-1, commerce-payment-contract 결정 12·16·확정한 목업 가정 3) — 실제 관리자 앱·PostgreSQL.
 * <ul>
 *   <li>날짜별 대사 요약(어제부터 14일 · 기록 없는 날은 PENDING · 「다시 대사」 요청이 끝나지 않은 날은 RUNNING · 다음 자동 실행 04:50 KST), 이슈 목록(열린 것 먼저·거르기).</li>
 *   <li>이슈 처리 = {@code commerce.reconcile.manage} + 사유 5~200자 + 감사(돈 관련) — 데이터를 고치지 않는다(자동 보정 없음). 이미 처리됐으면 409.</li>
 *   <li>이 날 다시 대사 = 사유 없이 배치 요청 표에 그 날 파라미터로(비동기), 어제까지만, 끝나지 않은 요청이 있으면 409, 감사는 일반.</li>
 * </ul>
 */
class ReconciliationAdminTest extends PaymentAdminTestBase {

    private static final String REASON = "포트원 콘솔과 맞춰 확인함";

    @AfterEach
    void clearRequests() {
        jdbc.update("DELETE FROM batch_run_requests WHERE job_key = 'payment-reconciliation' AND status = 'PENDING'"); // 이 앱은 배치를 끄고 돌아 요청이 남는다
    }

    private static LocalDate yesterday() {
        return LocalDate.now(ServiceTime.ZONE).minusDays(1);
    }

    private long issue(String type, String paymentId, Long orderId, LocalDate day, String status, String detailJson) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO reconciliation_issues (id, type, payment_id, payment_order_id, detected_day, detail, status, resolved_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, now())", id, type, paymentId, orderId, Date.valueOf(day), detailJson, status,
                "RESOLVED".equals(status) ? java.sql.Timestamp.from(Instant.now()) : null);
        return id;
    }

    private void day(LocalDate day, String status, int checked, int issues) {
        jdbc.update("INSERT INTO payment_reconcile_days (day, status, checked_count, issue_count, finished_at) VALUES (?, ?, ?, ?, now()) "
                + "ON CONFLICT (day) DO UPDATE SET status = excluded.status, checked_count = excluded.checked_count, issue_count = excluded.issue_count, "
                + "finished_at = excluded.finished_at", Date.valueOf(day), status, checked, issues);
    }

    @Test
    void 조회는_commerce_payment_read_처리와_다시_대사는_commerce_reconcile_manage_가_없으면_403() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.COMMERCE_PAYMENT_READ));
        long open = issue("PG_ONLY", paymentId(), null, yesterday(), "OPEN", null);

        mvc.perform(get("/api/admin/payment-reconciliation").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payment-reconciliation/issues").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payment-reconciliation").cookie(reader)).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/issues/" + open + "/resolve"), reader).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/days/" + yesterday() + "/rerun"), reader)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM reconciliation_issues WHERE id = ?", String.class, open)).isEqualTo("OPEN");
    }

    @Test
    void 요약은_어제부터_14일이고_기록_없는_날은_PENDING_다음_자동_실행은_04시_50분() throws Exception {
        Cookie session = login(operatorWith(Permission.COMMERCE_PAYMENT_READ));
        LocalDate yesterday = yesterday();
        day(yesterday, "MISMATCH", 12, 2);
        day(yesterday.minusDays(1), "DONE", 7, 0);
        jdbc.update("DELETE FROM payment_reconcile_days WHERE day = ?", Date.valueOf(yesterday.minusDays(2)));
        issue("AMOUNT_MISMATCH", paymentId(), null, yesterday, "OPEN", null);
        issue("PG_ONLY", paymentId(), null, yesterday, "RESOLVED", null);

        ZonedDateTime next = ZonedDateTime.now(ServiceTime.ZONE).withHour(4).withMinute(50).withSecond(0).withNano(0);
        if (!next.isAfter(ZonedDateTime.now(ServiceTime.ZONE))) {
            next = next.plusDays(1);
        }
        mvc.perform(get("/api/admin/payment-reconciliation").cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.days.length()").value(14))
                .andExpect(jsonPath("$.data.days[0].day").value(yesterday.toString()))
                .andExpect(jsonPath("$.data.days[0].status").value("MISMATCH"))
                .andExpect(jsonPath("$.data.days[0].checkedCount").value(12))
                .andExpect(jsonPath("$.data.days[0].issueCount").value(2))
                .andExpect(jsonPath("$.data.days[0].openIssueCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.days[1].status").value("DONE"))
                .andExpect(jsonPath("$.data.days[2].status").value("PENDING"))
                .andExpect(jsonPath("$.data.days[2].finishedAt").isEmpty())
                .andExpect(jsonPath("$.data.days[13].day").value(yesterday.minusDays(13).toString()))
                .andExpect(jsonPath("$.data.lastCheckedDay").value(yesterday.toString()))
                .andExpect(jsonPath("$.data.nextRunAt").value(next.toInstant().toString()))
                .andExpect(jsonPath("$.data.openIssueCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void 이슈_목록은_열린_것_먼저이고_종류_상태_날짜로_거르며_내용은_요청_언어로_푼다() throws Exception {
        Cookie session = login(operatorWith(Permission.COMMERCE_PAYMENT_READ));
        LocalDate day = LocalDate.of(2006, 7, 8);
        Order order = paidOrder(newMember(), 5_000);
        long resolved = issue("CANCEL_MISMATCH", order.paymentId(), order.id(), day, "RESOLVED", "{\"pgCancelled\": 2000, \"localCanceled\": 0}");
        long open = issue("AMOUNT_MISMATCH", order.paymentId(), order.id(), day, "OPEN", "{\"pgAmount\": 5500, \"localPaidAmount\": 5000}");
        String pgOnly = paymentId();
        long orphan = issue("PG_ONLY", pgOnly, null, day, "OPEN", "{\"pgStatus\": \"PAID\", \"pgAmount\": 3000}");

        mvc.perform(get("/api/admin/payment-reconciliation/issues").param("day", day.toString()).header("Accept-Language", "ko").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[2].id").value(Long.toString(resolved))) // 열린 것 먼저
                .andExpect(jsonPath("$.data.items[2].status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + open + "')].detail").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("5,500원"), org.hamcrest.Matchers.containsString("5,000원")))))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + orphan + "')].paymentOrderId").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + orphan + "')].detail").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("결제 완료"))));
        mvc.perform(get("/api/admin/payment-reconciliation/issues").param("day", day.toString()).param("status", "OPEN").param("type", "PG_ONLY").cookie(session))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].paymentId").value(pgOnly));
        mvc.perform(get("/api/admin/payment-reconciliation/issues").param("day", "2006-13-01").param("type", "NOPE").cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.day").isNotEmpty()).andExpect(jsonPath("$.error.fields.type").isNotEmpty());
    }

    @Test
    void 이슈_처리는_사유와_돈_관련_감사를_남기고_결제는_고치지_않는다_두_번째는_409() throws Exception {
        long operator = operatorWith(Permission.COMMERCE_PAYMENT_READ, Permission.COMMERCE_RECONCILE_MANAGE);
        Cookie session = login(operator);
        Order order = paidOrder(newMember(), 5_000);
        Map<String, Object> before = orderRow(order.id());
        long open = issue("AMOUNT_MISMATCH", order.paymentId(), order.id(), yesterday(), "OPEN", "{\"pgAmount\": 5500, \"localPaidAmount\": 5000}");

        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/issues/" + open + "/resolve"), session).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/issues/" + open + "/resolve"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolveNote").value(REASON))
                .andExpect(jsonPath("$.data.resolvedBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());
        assertThat(orderRow(order.id())).as("자동 보정 없음 — 결제 주문은 그대로").isEqualTo(before);
        Map<String, Object> audit = jdbc.queryForMap("SELECT target_type, target_id, detail, retention_class FROM audit_logs WHERE actor_id = ? AND action = 'RECONCILE_ISSUE_RESOLVE'",
                operator);
        assertThat(audit.get("target_type")).isEqualTo("RECONCILIATION_ISSUE");
        assertThat(audit.get("target_id")).isEqualTo(Long.toString(open));
        assertThat((String) audit.get("detail")).contains("AMOUNT_MISMATCH").contains(order.paymentId()).contains(REASON);
        assertThat(audit.get("retention_class")).isEqualTo("MONEY");

        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/issues/" + open + "/resolve"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RECONCILE_ISSUE_NOT_OPEN"));
        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/issues/" + ids.nextId() + "/resolve"), session).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RECONCILE_ISSUE_NOT_FOUND"));
    }

    @Test
    void 이_날_다시_대사는_사유_없이_배치_요청으로_넣고_어제까지만_끝나지_않은_요청이_있으면_409() throws Exception {
        long operator = operatorWith(Permission.COMMERCE_PAYMENT_READ, Permission.COMMERCE_RECONCILE_MANAGE);
        Cookie session = login(operator);
        LocalDate target = yesterday().minusDays(3);
        LocalDate today = LocalDate.now(ServiceTime.ZONE);

        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/days/" + today + "/rerun"), session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.day").isNotEmpty());
        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/days/" + today.plusDays(1) + "/rerun"), session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.day").isNotEmpty());
        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/days/not-a-day/rerun"), session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.day").isNotEmpty());

        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/days/" + target + "/rerun"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day").value(target.toString()))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
        List<Map<String, Object>> requests = jdbc.queryForList("SELECT kind, params, operator_id FROM batch_run_requests WHERE job_key = 'payment-reconciliation' "
                + "AND status = 'PENDING'");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.get("kind")).isEqualTo("NOW");
            assertThat(BatchMessageCodec.decodeParams((String) request.get("params"))).containsExactlyEntriesOf(Map.of("day", target.toString()));
            assertThat(((Number) request.get("operator_id")).longValue()).isEqualTo(operator);
        });
        Map<String, Object> audit = jdbc.queryForMap("SELECT target_type, target_id, retention_class FROM audit_logs WHERE actor_id = ? AND action = 'PAYMENT_RECONCILE_RERUN'",
                operator);
        assertThat(audit.get("target_type")).isEqualTo("PAYMENT_RECONCILE_DAY");
        assertThat(audit.get("target_id")).isEqualTo(target.toString());
        assertThat(audit.get("retention_class")).as("돈을 움직이지 않는다").isEqualTo("GENERAL");

        mvc.perform(get("/api/admin/payment-reconciliation").cookie(session))
                .andExpect(jsonPath("$.data.days[?(@.day == '" + target + "')].status").value(org.hamcrest.Matchers.hasItem("RUNNING")));
        mvc.perform(withCsrf(post("/api/admin/payment-reconciliation/days/" + target.minusDays(1) + "/rerun"), session))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RECONCILE_ALREADY_RUNNING"));
    }
}
