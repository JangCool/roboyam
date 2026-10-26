package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.entity.BatchRunStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 미확정(READY) 결제 정리 배치 {@code payment-ready-cleanup}(commerce.md 8-2 의 7·15-3·15-5, 계약 「규칙 → 테스트」) — 배치 core 로 실제로 돌린다(실제 PostgreSQL).
 * 여유 시간({@code grace_minutes}, 기본 10분)이 지난 READY 만 · 포트원이 PAID 면 확정 코드(늦은 결제 판정 포함) · 아니면 EXPIRED · 포트원 장애는 그 건만 실패 ·
 * 「아직 처리 안 한 것 전부」(덩어리 여럿). 시험 시계가 과거라 다른 시험의 READY 는 대상이 아니다.
 */
class PaymentReadyCleanupJobTest {

    private static final String KEY = BatchCatalog.PAYMENT_READY_CLEANUP.key();

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    @AfterEach
    void clearSettings() {
        kit.jdbc.update("DELETE FROM batch_params WHERE job_key = ?", KEY);
    }

    private static void setting(String param, long value) {
        kit.jdbc.update("INSERT INTO batch_params (job_key, param_key, param_value) VALUES (?, ?, ?) ON CONFLICT (job_key, param_key) DO UPDATE SET param_value = EXCLUDED.param_value",
                KEY, param, value);
    }

    @Test
    void 카탈로그는_Java_배치_10분마다이고_구현이_짝이다() {
        assertThat(BatchCatalog.PAYMENT_READY_CLEANUP.isJava()).isTrue();
        assertThat(BatchCatalog.PAYMENT_READY_CLEANUP.defaultCron()).isEqualTo("0 */10 * * * *");
        assertThat(BatchCatalog.PAYMENT_READY_CLEANUP.defaultEnabled()).isTrue();
        assertThat(kit.readyCleanup.definition()).isEqualTo(BatchCatalog.PAYMENT_READY_CLEANUP);
    }

    @Test
    void 여유_시간_10분이_지나야_대상이고_결제_안_됐으면_EXPIRED_결제됐으면_확정한다() {
        PaymentTestKit.Created unpaid = kit.create(kit.newMember(), 5_000, Decision.ACCEPT);
        PaymentTestKit.Created paidOnTime = kit.create(kit.newMember(), 6_000, Decision.ACCEPT);
        PaymentTestKit.Created paidLate = kit.create(kit.newMember(), 7_000, Decision.ACCEPT);
        OrderRow base = kit.order(unpaid.paymentId());
        kit.pgPaid(paidOnTime.paymentId(), 6_000);
        kit.pgPaidAt(paidLate.paymentId(), 7_000, base.expiresAt().plusSeconds(30));

        kit.clock.set(base.expiresAt().plus(Duration.ofMinutes(10)).minusSeconds(1));
        RunReport early = kit.runReadyCleanup();
        assertThat(early.status()).as("할 일 없음").isEqualTo(BatchRunStatus.SKIP);
        assertThat(kit.order(unpaid.paymentId()).status()).isEqualTo(PaymentStatus.READY);
        assertThat(kit.gateway.getCalls.get()).isZero();

        kit.clock.set(base.expiresAt().plus(Duration.ofMinutes(10)));
        RunReport run = kit.runReadyCleanup();
        assertThat(run.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(kit.order(unpaid.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.events(unpaid.orderId(), "EXPIRED")).isEqualTo(1);
        OrderRow onTime = kit.order(paidOnTime.paymentId());
        assertThat(onTime.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(onTime.accepted()).as("유효 시각 안의 결제 — 확인이 늦었을 뿐").isTrue();
        assertThat(onTime.confirmedVia()).isEqualTo(ConfirmedVia.READY_CLEANUP);
        assertThat(kit.handler.cashOf(paidOnTime.orderRefId())).isEqualTo(6_000);
        OrderRow late = kit.order(paidLate.paymentId());
        assertThat(late.anomaly()).isEqualTo(PaymentAnomaly.LATE_PAYMENT);
        assertThat(late.status()).as("자동 취소").isEqualTo(PaymentStatus.CANCELED);
        Map<String, Object> step = kit.jdbc.queryForMap("SELECT processed_count FROM batch_run_steps WHERE run_id = ? AND step_name = 'main'", run.runId());
        assertThat(((Number) step.get("processed_count")).longValue()).isEqualTo(3);

        RunReport again = kit.runReadyCleanup();
        assertThat(again.status()).as("두 번 돌려도 같다").isEqualTo(BatchRunStatus.SKIP);
    }

    @Test
    void 여유_시간은_batch_params_로_바꾼다() {
        setting("grace_minutes", 0);
        PaymentTestKit.Created created = kit.create(kit.newMember(), 5_000, Decision.ACCEPT);
        kit.clock.set(kit.order(created.paymentId()).expiresAt());
        kit.runReadyCleanup();
        assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);

        setting("grace_minutes", 99_999); // 범위(0~1440) 밖 → 기본 10분
        PaymentTestKit.Created other = kit.create(kit.newMember(), 5_000, Decision.ACCEPT);
        kit.clock.set(kit.order(other.paymentId()).expiresAt().plus(Duration.ofMinutes(10)));
        kit.runReadyCleanup();
        assertThat(kit.order(other.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void 포트원_장애는_그_건만_실패로_남고_나머지는_처리하며_다음_실행이_다시_집는다() {
        PaymentTestKit.Created ok = kit.create(kit.newMember(), 5_000, Decision.ACCEPT);
        PaymentTestKit.Created broken = kit.create(kit.newMember(), 5_000, Decision.ACCEPT);
        kit.gateway.downFor.add(broken.paymentId());
        kit.clock.set(kit.order(ok.paymentId()).expiresAt().plus(Duration.ofMinutes(10)));

        RunReport run = kit.runReadyCleanup();

        assertThat(kit.order(ok.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.order(broken.paymentId()).status()).isEqualTo(PaymentStatus.READY);
        List<Map<String, Object>> failures = kit.jdbc.queryForList("SELECT target_type, target_id, error_code FROM batch_run_failures WHERE run_id = ?", run.runId());
        assertThat(failures).singleElement().satisfies(f -> {
            assertThat(f.get("target_type")).isEqualTo("PAYMENT_ORDER");
            assertThat(f.get("target_id")).isEqualTo(Long.toString(broken.orderId()));
            assertThat(f.get("error_code")).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        });

        kit.gateway.downFor.clear();
        kit.runReadyCleanup();
        assertThat(kit.order(broken.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void 덩어리가_여럿이어도_아직_처리_안_한_것_전부를_한_실행이_처리한다() {
        setting("chunk_size", 2);
        List<PaymentTestKit.Created> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(kit.create(kit.newMember(), 1_000, Decision.ACCEPT));
            kit.clock.advance(Duration.ofSeconds(1));
        }
        kit.clock.advance(Duration.ofDays(3)); // 사흘 동안 배치가 멈췄다
        RunReport run = kit.runReadyCleanup();
        assertThat(run.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(created).allSatisfy(c -> assertThat(kit.order(c.paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED));
    }
}
