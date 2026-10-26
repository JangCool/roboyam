package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.service.PaymentConfirmService.Outcome;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 동시 확정(commerce.md 8-2 의 6 「먼저 온 쪽이 처리, 나중 것은 조건부 UPDATE 에 진다」, 계약 「규칙 → 테스트」) — 완료 API·웹훅·READY 정리가 같은 결제를 <b>동시에</b>(연결 여럿)
 * 확정해도 처리기 {@code onPaid} 는 정확히 한 번, 결제 이력 PAID 도 한 줄이다. 실제 PostgreSQL 의 행 잠금으로 확인한다.
 */
class PaymentConfirmConcurrencyTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    @Test
    void 완료_API_웹훅_READY_정리가_동시에_와도_onPaid_는_정확히_한_번() throws Exception {
        for (int round = 0; round < 5; round++) {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 15_000, Decision.ACCEPT);
            kit.pgPaid(created.paymentId(), 15_000);
            AtomicInteger accepted = new AtomicInteger();
            List<Callable<Void>> paths = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                paths.add(() -> {
                    kit.completion.complete(created.userId(), created.paymentId(), PaymentOrderType.PAYMENT_TEST, created.orderRefId());
                    return null;
                });
                paths.add(() -> {
                    assertThat(kit.webhooks.handle(TestWebhooks.paid(created.paymentId(), kit.now())).httpStatus()).isEqualTo(200);
                    return null;
                });
                paths.add(() -> {
                    if (kit.confirm.confirm(created.paymentId(), ConfirmedVia.READY_CLEANUP).outcome() == Outcome.ACCEPTED) {
                        accepted.incrementAndGet();
                    }
                    return null;
                });
            }
            runTogether(paths);

            assertThat(kit.handler.cashOf(created.orderRefId())).as("onPaid 한 번 — 현금은 결제 금액 한 번").isEqualTo(15_000);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
            assertThat(kit.events(created.orderId(), "PAID")).isEqualTo(1);
            assertThat(accepted.get()).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void 거절되는_결제도_동시에_와서_자동_취소가_한_번만_들어간다() throws Exception {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 9_000, Decision.REJECT);
        kit.pgPaid(created.paymentId(), 9_000);
        List<Callable<Void>> paths = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ConfirmedVia via = i % 2 == 0 ? ConfirmedVia.WEBHOOK : ConfirmedVia.COMPLETE_API;
            paths.add(() -> {
                kit.confirm.confirm(created.paymentId(), via);
                return null;
            });
        }
        runTogether(paths);
        assertThat(kit.cancelsOf(created.orderId())).hasSize(1);
        assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(9_000);
        assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
    }

    private static void runTogether(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
