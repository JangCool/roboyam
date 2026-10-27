package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.payment.TestGateway.CancelScript;
import com.community.payment.config.PaymentProperties;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.service.PaymentAnomalyService;
import com.community.payment.service.PaymentCancelExecutor;
import com.community.payment.service.PaymentCancelWorker;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 워커가 여럿이어도 같은 취소는 한 번(commerce.md 15-4 「{@code FOR UPDATE SKIP LOCKED} 로 서버끼리 나눠 잡음」, 계약 「규칙 → 테스트」) — 서버 번호가 다른 워커 넷이 동시에 돌아도
 * 포트원 취소 요청은 취소마다 한 번이고 취소 누계도 한 번만 늘어난다. 실제 PostgreSQL, 연결 여럿.
 */
class PaymentCancelConcurrencyTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    private static PaymentCancelWorker workerOn(int nodeId) {
        var spring = kit.spring;
        PaymentCancelExecutor executor = new PaymentCancelExecutor(spring.getBean(PaymentOrderStore.class), spring.getBean(PaymentCancelStore.class),
                spring.getBean(PaymentEventStore.class), spring.getBean(PaymentOrderHandlers.class), spring.getBean(PaymentGateway.class),
                spring.getBean(PaymentAnomalyService.class), spring.getBean(PaymentProperties.class), spring.getBean(SnowflakeIdGenerator.class),
                spring.getBean(PlatformTransactionManager.class), nodeId, spring.getBeanProvider(Clock.class));
        return new PaymentCancelWorker(spring.getBean(PaymentCancelStore.class), executor, spring.getBean(PaymentProperties.class), duration -> {
        }, kit.clock);
    }

    @Test
    void 서버_넷의_워커가_동시에_돌아도_취소마다_포트원_요청은_한_번이다() throws Exception {
        int count = 40;
        List<PaymentTestKit.Created> orders = new ArrayList<>();
        List<Long> cancelIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 2_000, Decision.ACCEPT);
            kit.pgPaid(created.paymentId(), 2_000);
            kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);
            kit.gateway.script(CancelScript.DOWN); // 즉시 시도는 실패 — 워커 몫으로 남긴다
            cancelIds.add(kit.tx.execute(status -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), 2_000, CancelKind.REFUND, "시험", "R:" + kit.ids.nextId(),
                    null))));
            orders.add(created);
        }
        assertThat(kit.gateway.cancelCalls.get()).isEqualTo(count);
        kit.clock.advance(Duration.ofMinutes(1));

        List<PaymentCancelWorker> workers = List.of(workerOn(961), workerOn(962), workerOn(963), workerOn(964));
        ExecutorService pool = Executors.newFixedThreadPool(workers.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (PaymentCancelWorker worker : workers) {
                futures.add(pool.submit(() -> {
                    start.await();
                    int handled = 0;
                    for (int round = 0; round < 4; round++) {
                        handled += worker.runOnce();
                    }
                    return handled;
                }));
            }
            start.countDown();
            int handled = 0;
            for (Future<Integer> future : futures) {
                handled += future.get(120, TimeUnit.SECONDS);
            }
            assertThat(handled).as("집은 줄의 합 = 취소 수").isEqualTo(count);
        } finally {
            pool.shutdownNow();
        }

        assertThat(kit.gateway.cancelCalls.get()).as("즉시 시도 40 + 워커 40 — 겹친 요청 없음").isEqualTo(count * 2);
        for (int i = 0; i < count; i++) {
            long id = cancelIds.get(i);
            assertThat(kit.cancelStore.findById(id).orElseThrow().status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(kit.order(orders.get(i).paymentId()).canceledAmount()).isEqualTo(2_000);
            assertThat(kit.handler.canceledCallsOf(id)).isEqualTo(1);
        }
    }
}
