package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchParamType;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.entity.BatchRunStatus;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.payment.batch.PaymentReconciliationJob;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import com.community.zconfiguration.time.ServiceTime;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PG 대사 Java 배치 {@code payment-reconciliation}(commerce.md 16-1·15-3·15-5, 계약 결정 16) — 실제 PostgreSQL, 포트원은 가짜 PG({@code fake_pg_payments}).
 * 불일치 5종을 이슈로 남기고 <b>자동 보정하지 않는다</b>, 다시 돌려도 열린 이슈는 (종류, 결제) 하나, 「아직 안 한 날 전부」(놓친 날·실패한 날 메꿈), 날짜 지정은 그 날만·어제까지,
 * 포트원 장애는 그 날만 FAILED + 실패 기록, 새 이슈가 있으면 운영자 알림 하나.
 * <p>DB 를 다른 결제 시험과 같이 쓴다 — 다른 시험의 시계(1990년 근처)와 겹치지 않게 이 시험의 날짜는 1940년대이고, 시험마다 다른 해를 쓴다.
 */
class PaymentReconciliationJobTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    /** 이 시험의 결제 대기(1940년대 유효 시각)를 닫는다 — 다른 시험의 READY 정리(「차례가 된 READY 전부」)가 집지 않게 */
    @AfterEach
    void closeOwnReady() {
        kit.jdbc.update("UPDATE payment_orders SET status = 'EXPIRED', updated_at = now() WHERE status = 'READY' AND expires_at < '1950-01-01'");
    }

    /** 그 날(KST) 정오로 시계를 옮긴다 */
    private static void at(LocalDate day) {
        kit.clock.set(day.atTime(LocalTime.NOON).atZone(ServiceTime.ZONE).toInstant());
    }

    /** 결제하고 확정까지(받아들임) */
    private static PaymentTestKit.Created paidAndConfirmed(long amount) {
        PaymentTestKit.Created created = kit.create(kit.newMember(), amount, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), amount);
        kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);
        return created;
    }

    private static String randomPaymentId() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder id = new StringBuilder("pay_");
        for (int i = 0; i < 32; i++) {
            id.append(letters.charAt(RANDOM.nextInt(letters.length())));
        }
        return id.toString();
    }

    private static List<Map<String, Object>> issuesOf(String paymentId) {
        return kit.jdbc.queryForList("SELECT type, status, payment_order_id, detected_day, detail::text AS detail FROM reconciliation_issues WHERE payment_id = ? ORDER BY created_at, id",
                paymentId);
    }

    private static Map<String, Object> dayRow(LocalDate day) {
        List<Map<String, Object>> rows = kit.jdbc.queryForList("SELECT status, checked_count, issue_count, run_id, finished_at FROM payment_reconcile_days WHERE day = ?",
                java.sql.Date.valueOf(day));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    void 카탈로그는_Java_배치_매일_04시_50분이고_날짜_파라미터_이어_받기를_지원한다() {
        assertThat(BatchCatalog.PAYMENT_RECONCILIATION.isJava()).isTrue();
        assertThat(BatchCatalog.PAYMENT_RECONCILIATION.defaultCron()).isEqualTo("0 50 4 * * *");
        assertThat(BatchCatalog.PAYMENT_RECONCILIATION.defaultEnabled()).isTrue();
        assertThat(kit.reconcileJob.definition()).isEqualTo(BatchCatalog.PAYMENT_RECONCILIATION);
        assertThat(kit.reconcileJob.resumable()).isTrue();
        assertThat(kit.reconcileJob.params()).singleElement().satisfies(param -> {
            assertThat(param.name()).isEqualTo("day");
            assertThat(param.type()).isEqualTo(BatchParamType.DATE);
            assertThat(param.required()).isFalse();
        });
    }

    @Test
    void 불일치_다섯_종류를_이슈로_남기고_결제_주문은_고치지_않는다() {
        LocalDate day = LocalDate.of(1941, 3, 14);
        at(day);
        PaymentTestKit.Created unpaid = kit.create(kit.newMember(), 4_000, Decision.ACCEPT);
        kit.pgPaid(unpaid.paymentId(), 4_000); // 포트원은 결제됐는데 우리는 READY(확정 요청이 오지 않았다)
        PaymentTestKit.Created missing = paidAndConfirmed(5_000);
        kit.jdbc.update("DELETE FROM fake_pg_payments WHERE payment_id = ?", missing.paymentId()); // 우리는 결제됐는데 포트원에 없다
        PaymentTestKit.Created amount = paidAndConfirmed(6_000);
        kit.jdbc.update("UPDATE fake_pg_payments SET amount = amount + 500 WHERE payment_id = ?", amount.paymentId());
        PaymentTestKit.Created external = paidAndConfirmed(7_000);
        kit.fakePg.externalCancel(external.paymentId(), 2_000, "ext-" + external.orderId(), kit.now()); // 콘솔 직접 취소(웹훅이 오지 않았다)
        String pgOnly = randomPaymentId();
        kit.fakePg.pay(pgOnly, 3_000, "KRW", kit.now());
        PaymentTestKit.Created normal = paidAndConfirmed(8_000);
        OrderRow unpaidBefore = kit.order(unpaid.paymentId());
        OrderRow amountBefore = kit.order(amount.paymentId());
        int ordersBefore = kit.count("SELECT count(*) FROM payment_orders");
        int cancelsBefore = kit.count("SELECT count(*) FROM payment_cancels");

        at(day.plusDays(1));
        RunReport run = kit.runReconciliation(day);

        assertThat(run.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(issuesOf(unpaid.paymentId())).singleElement().satisfies(issue -> {
            assertThat(issue.get("type")).isEqualTo("PG_PAID_LOCAL_UNPAID");
            assertThat(issue.get("status")).isEqualTo("OPEN");
            assertThat(((Number) issue.get("payment_order_id")).longValue()).isEqualTo(unpaid.orderId());
            assertThat(issue.get("detected_day").toString()).isEqualTo(day.toString());
            assertThat((String) issue.get("detail")).contains("\"pgStatus\": \"PAID\"").contains("\"localStatus\": \"READY\"");
        });
        assertThat(issuesOf(missing.paymentId())).singleElement().satisfies(issue -> {
            assertThat(issue.get("type")).isEqualTo("LOCAL_PAID_PG_MISSING");
            assertThat((String) issue.get("detail")).contains("NOT_FOUND");
        });
        assertThat(issuesOf(amount.paymentId())).singleElement().satisfies(issue -> {
            assertThat(issue.get("type")).isEqualTo("AMOUNT_MISMATCH");
            assertThat((String) issue.get("detail")).contains("6500").contains("6000");
        });
        assertThat(issuesOf(external.paymentId())).singleElement().satisfies(issue -> {
            assertThat(issue.get("type")).isEqualTo("CANCEL_MISMATCH");
            assertThat((String) issue.get("detail")).contains("\"pgCancelled\": 2000").contains("\"localCanceled\": 0");
        });
        assertThat(issuesOf(pgOnly)).singleElement().satisfies(issue -> {
            assertThat(issue.get("type")).isEqualTo("PG_ONLY");
            assertThat(issue.get("payment_order_id")).isNull();
        });
        assertThat(issuesOf(normal.paymentId())).as("맞는 결제는 이슈가 없다").isEmpty();

        // 자동 보정 없음 — 우리 결제 주문·취소 대기열은 그대로
        OrderRow unpaidAfter = kit.order(unpaid.paymentId());
        assertThat(unpaidAfter.status()).isEqualTo(PaymentStatus.READY);
        assertThat(unpaidAfter.updatedAt()).isEqualTo(unpaidBefore.updatedAt());
        assertThat(kit.order(amount.paymentId()).paidAmount()).isEqualTo(amountBefore.paidAmount());
        assertThat(kit.order(external.paymentId()).canceledAmount()).isZero();
        assertThat(kit.order(external.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(kit.count("SELECT count(*) FROM payment_orders")).as("우리에게 없는 결제를 만들지 않는다").isEqualTo(ordersBefore);
        assertThat(kit.count("SELECT count(*) FROM payment_cancels")).as("취소를 넣지 않는다").isEqualTo(cancelsBefore);
        assertThat(kit.gateway.cancelCalls.get()).isZero();

        Map<String, Object> row = dayRow(day);
        assertThat(row.get("status")).isEqualTo("MISMATCH");
        assertThat(((Number) row.get("checked_count")).intValue()).isEqualTo(6);
        assertThat(((Number) row.get("issue_count")).intValue()).isEqualTo(5);
        assertThat(((Number) row.get("run_id")).longValue()).isEqualTo(run.runId());
        assertThat(row.get("finished_at")).isNotNull();
        assertThat(kit.notices).as("새 이슈가 있으면 운영자 알림 하나").containsExactly(AdminNotificationEvent.COMMERCE_RECONCILE_MISMATCH);
    }

    @Test
    void 다시_돌려도_열린_이슈는_종류와_결제마다_하나이고_새_이슈가_없으면_알리지_않는다_처리한_이슈는_어긋남이_남으면_다시_열린다() {
        LocalDate day = LocalDate.of(1942, 6, 2);
        at(day);
        PaymentTestKit.Created unpaid = kit.create(kit.newMember(), 4_000, Decision.ACCEPT);
        kit.pgPaid(unpaid.paymentId(), 4_000);
        at(day.plusDays(1));
        kit.runReconciliation(day);
        kit.notices.clear();

        RunReport again = kit.runReconciliation(day);
        assertThat(again.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(issuesOf(unpaid.paymentId())).hasSize(1);
        assertThat(kit.notices).as("새로 연 이슈가 없다").isEmpty();
        assertThat(dayRow(day).get("issue_count")).as("그 날의 불일치 수는 그대로 센다").isEqualTo(1);

        long issueId = kit.jdbc.queryForObject("SELECT id FROM reconciliation_issues WHERE payment_id = ?", Long.class, unpaid.paymentId());
        kit.issueStore.resolve(issueId, 1L, "운영자가 확인함", kit.now());
        kit.runReconciliation(day);
        assertThat(issuesOf(unpaid.paymentId())).extracting(issue -> issue.get("status")).containsExactly("RESOLVED", "OPEN");
        assertThat(kit.notices).containsExactly(AdminNotificationEvent.COMMERCE_RECONCILE_MISMATCH);
    }

    @Test
    void 파라미터가_없으면_아직_끝나지_않은_날_전부를_메꾸고_끝난_날은_다시_하지_않는다() {
        LocalDate first = LocalDate.of(1940, 1, 5); // 이 DB 에서 가장 이른 결제가 생긴 날(다른 결제 시험은 1990년 근처)
        at(first);
        PaymentTestKit.Created early = kit.create(kit.newMember(), 4_000, Decision.ACCEPT);
        kit.pgPaid(early.paymentId(), 4_000);
        at(first.plusDays(2));
        PaymentTestKit.Created later = paidAndConfirmed(5_000);
        kit.jdbc.update("INSERT INTO payment_reconcile_days (day, status, checked_count, issue_count, finished_at) VALUES (?, 'DONE', 9, 0, now()) "
                + "ON CONFLICT (day) DO NOTHING", java.sql.Date.valueOf(first.plusDays(1))); // 이미 끝낸 날
        kit.jdbc.update("INSERT INTO payment_reconcile_days (day, status, checked_count, issue_count) VALUES (?, 'FAILED', 0, 0) ON CONFLICT (day) DO NOTHING",
                java.sql.Date.valueOf(first.plusDays(3))); // 실패했던 날

        at(first.plusDays(5)); // 어제 = 01-09
        RunReport run = kit.runReconciliation(null);

        assertThat(run.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(dayRow(first).get("status")).isEqualTo("MISMATCH");
        assertThat(issuesOf(early.paymentId())).extracting(issue -> issue.get("type")).containsExactly("PG_PAID_LOCAL_UNPAID");
        assertThat(dayRow(first.plusDays(1))).as("끝낸 날은 그대로").satisfies(row -> {
            assertThat(row.get("checked_count")).isEqualTo(9);
            assertThat(row.get("run_id")).isNull();
        });
        assertThat(dayRow(first.plusDays(2)).get("status")).isEqualTo("DONE");
        assertThat(dayRow(first.plusDays(2)).get("checked_count")).isEqualTo(1);
        assertThat(issuesOf(later.paymentId())).isEmpty();
        assertThat(dayRow(first.plusDays(3)).get("status")).as("실패했던 날도 메꾼다").isEqualTo("DONE");
        assertThat(dayRow(first.plusDays(4)).get("status")).isEqualTo("DONE");
        assertThat(dayRow(first.plusDays(5))).as("오늘은 아직").isNull();
        Map<String, Object> step = kit.jdbc.queryForMap("SELECT processed_count FROM batch_run_steps WHERE run_id = ? AND step_name = 'main'", run.runId());
        assertThat(((Number) step.get("processed_count")).longValue()).as("01-05·07·08·09").isEqualTo(4);

        RunReport again = kit.runReconciliation(null);
        assertThat(again.status()).as("끝나지 않은 날이 없다").isEqualTo(BatchRunStatus.SKIP);
    }

    @Test
    void 날짜를_지정하면_그_날만_하고_어제까지만_받는다() {
        LocalDate day = LocalDate.of(1943, 9, 9);
        at(day);
        PaymentTestKit.Created unpaid = kit.create(kit.newMember(), 4_000, Decision.ACCEPT);
        kit.pgPaid(unpaid.paymentId(), 4_000);
        at(day.plusDays(3));

        assertThat(kit.reconcileJob.validate(BatchParameters.of(Map.of(PaymentReconciliationJob.PARAM_DAY, day.plusDays(3).toString())))).as("오늘").containsKey("day");
        assertThat(kit.reconcileJob.validate(BatchParameters.of(Map.of(PaymentReconciliationJob.PARAM_DAY, day.plusDays(4).toString())))).as("미래").containsKey("day");
        assertThat(kit.reconcileJob.validate(BatchParameters.of(Map.of(PaymentReconciliationJob.PARAM_DAY, day.plusDays(2).toString())))).as("어제").isEmpty();
        assertThat(kit.reconcileJob.validate(BatchParameters.empty())).isEmpty();

        kit.runReconciliation(day.plusDays(1));
        assertThat(dayRow(day.plusDays(1)).get("status")).isEqualTo("DONE");
        assertThat(dayRow(day)).as("지정하지 않은 날은 하지 않는다").isNull();
        assertThat(dayRow(day.plusDays(2))).isNull();
        assertThat(issuesOf(unpaid.paymentId())).isEmpty();

        kit.runReconciliation(day);
        assertThat(dayRow(day).get("status")).isEqualTo("MISMATCH");
        assertThat(issuesOf(unpaid.paymentId())).hasSize(1);
    }

    @Test
    void 포트원_장애는_그_날만_FAILED_와_실패_기록이고_다음_실행이_다시_한다() {
        LocalDate day = LocalDate.of(1944, 2, 20);
        at(day);
        PaymentTestKit.Created unpaid = kit.create(kit.newMember(), 4_000, Decision.ACCEPT);
        kit.pgPaid(unpaid.paymentId(), 4_000);
        at(day.plusDays(1));
        kit.gateway.listDown = true;

        RunReport failed = kit.runReconciliation(day);

        assertThat(dayRow(day).get("status")).isEqualTo("FAILED");
        assertThat(issuesOf(unpaid.paymentId())).isEmpty();
        List<Map<String, Object>> failures = kit.jdbc.queryForList("SELECT target_type, target_id, error_code FROM batch_run_failures WHERE run_id = ?", failed.runId());
        assertThat(failures).singleElement().satisfies(f -> {
            assertThat(f.get("target_type")).isEqualTo("PAYMENT_RECONCILE_DAY");
            assertThat(f.get("target_id")).isEqualTo(day.toString());
            assertThat(f.get("error_code")).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        });
        assertThat(kit.notices).isEmpty();

        kit.gateway.listDown = false;
        kit.runReconciliation(day);
        assertThat(dayRow(day).get("status")).isEqualTo("MISMATCH");
        assertThat(issuesOf(unpaid.paymentId())).hasSize(1);
    }
}
