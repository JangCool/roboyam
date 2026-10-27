package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.payment.TestGateway.CancelScript;
import com.community.payment.config.PaymentProperties;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.service.PaymentAnomalyService;
import com.community.payment.service.PaymentCancelExecutor;
import com.community.payment.service.PaymentCancelWorker;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 포트원 취소는 DB 잠금 밖에서(계약 「2a 보강 — 취소 호출을 잠금 밖으로」) — 포트원 응답을 기다리는 동안 결제 주문 행이 잠겨 있지 않고(확정·웹훅·관리자 처리가 막히지 않는다),
 * 그 대신 한 결제에 보내는 중(SENDING)인 취소는 하나뿐이라(V100) 같은 결제의 취소가 같은 잔액으로 겹쳐 나가 포트원에서 거절되는 일이 없다. 실제 PostgreSQL.
 */
class PaymentCancelOutsideLockTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    /** 다른 연결에서 결제 주문 행을 기다리지 않고(NOWAIT) 잠가 본다 — 잠겨 있으면 false */
    private static boolean lockableNow(String paymentId) {
        try (Connection connection = kit.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM payment_orders WHERE payment_id = ? FOR UPDATE NOWAIT")) {
                statement.setString(1, paymentId);
                statement.executeQuery().close();
                return true;
            } catch (SQLException e) {
                if ("55P03".equals(e.getSQLState())) {
                    return false;
                }
                throw new IllegalStateException(e);
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PaymentTestKit.Created paid(long amount) {
        PaymentTestKit.Created created = kit.create(kit.newMember(), amount, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), amount);
        kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);
        return created;
    }

    /** 즉시 시도 없이 취소 줄만 넣는다(차례 = 지금) */
    private static long pending(long orderId, long amount) {
        long id = kit.ids.nextId();
        assertThat(kit.cancelStore.insert(id, orderId, amount, "시험", CancelKind.REFUND, "R:" + id, null, kit.now())).isTrue();
        return id;
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
    void 커밋_뒤_즉시_시도가_포트원을_기다리는_동안_결제_주문_행은_잠겨_있지_않다() {
        PaymentTestKit.Created created = paid(2_000);
        List<Boolean> seen = new CopyOnWriteArrayList<>();
        kit.gateway.whileCancelling = paymentId -> seen.add(lockableNow(paymentId));

        Long cancelId = kit.tx.execute(status -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), 2_000, CancelKind.REFUND, "시험", "R:" + kit.ids.nextId(), null)));

        assertThat(seen).as("포트원 호출 한 번, 그동안 다른 연결이 결제 행을 바로 잠글 수 있다").containsExactly(true);
        assertThat(kit.cancelStore.findById(cancelId).orElseThrow().status()).isEqualTo(CancelStatus.SUCCEEDED);
        assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(2_000);
    }

    @Test
    void 워커가_포트원을_기다리는_동안_결제_주문_행은_잠겨_있지_않다() {
        PaymentTestKit.Created created = paid(2_000);
        kit.gateway.script(CancelScript.DOWN); // 즉시 시도는 실패 — 워커 몫으로 남긴다
        Long cancelId = kit.tx.execute(status -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), 2_000, CancelKind.REFUND, "시험", "R:" + kit.ids.nextId(), null)));
        kit.clock.advance(Duration.ofMinutes(1));
        List<Boolean> seen = new CopyOnWriteArrayList<>();
        kit.gateway.whileCancelling = paymentId -> seen.add(lockableNow(paymentId));

        assertThat(kit.worker.runOnce()).isEqualTo(1);

        assertThat(seen).containsExactly(true);
        assertThat(kit.cancelStore.findById(cancelId).orElseThrow().status()).isEqualTo(CancelStatus.SUCCEEDED);
    }

    @Test
    void 포트원_거절도_잠금_밖에서_기다리고_재시도로_남는다() {
        PaymentTestKit.Created created = paid(2_000);
        kit.gateway.script(CancelScript.FAILED);
        List<Boolean> seen = new CopyOnWriteArrayList<>();
        kit.gateway.whileCancelling = paymentId -> seen.add(lockableNow(paymentId));

        Long cancelId = kit.tx.execute(status -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), 2_000, CancelKind.REFUND, "시험", "R:" + kit.ids.nextId(), null)));

        assertThat(seen).containsExactly(true);
        CancelRow row = kit.cancelStore.findById(cancelId).orElseThrow();
        assertThat(row.status()).isEqualTo(CancelStatus.PENDING);
        assertThat(row.attempts()).isEqualTo(1);
        assertThat(kit.order(created.paymentId()).canceledAmount()).isZero();
    }

    @Test
    void 한_결제에_보내는_중인_취소는_하나다() {
        PaymentTestKit.Created created = paid(3_000);
        long first = pending(created.orderId(), 1_000);
        long second = pending(created.orderId(), 1_000);
        int node = kit.executor.nodeId();

        List<CancelRow> claimed = kit.cancelStore.claimDue(20, node, kit.now());
        assertThat(claimed).as("한 결제에서는 차례가 가장 이른 한 줄만").extracting(CancelRow::id).containsExactly(first);
        assertThat(kit.cancelStore.claimOne(second, node, kit.now())).as("앞의 것이 보내는 중이면 즉시 시도도 못 집는다").isEmpty();
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE payment_cancels SET status = 'SENDING', locked_by = 1, locked_at = now() WHERE id = ?", second))
                .as("색인이 마지막 방어선").isInstanceOf(DuplicateKeyException.class);

        kit.executor.execute(claimed.get(0));
        List<CancelRow> next = kit.cancelStore.claimDue(20, node, kit.now());
        assertThat(next).extracting(CancelRow::id).containsExactly(second);
        kit.executor.execute(next.get(0));

        assertThat(kit.cancelStore.findById(first).orElseThrow().status()).isEqualTo(CancelStatus.SUCCEEDED);
        assertThat(kit.cancelStore.findById(second).orElseThrow().status()).isEqualTo(CancelStatus.SUCCEEDED);
        assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(2_000);
        assertThat(kit.gateway.cancelCurrentCancellable).as("둘째는 첫째가 끝난 뒤의 잔액으로 — 포트원 거절 없음").containsExactly(3_000L, 2_000L);
    }

    @Test
    void 다른_결제의_취소는_줄을_서지_않는다() {
        PaymentTestKit.Created a = paid(1_000);
        PaymentTestKit.Created b = paid(1_000);
        long ca = pending(a.orderId(), 1_000);
        long cb = pending(b.orderId(), 1_000);

        assertThat(kit.cancelStore.claimDue(20, kit.executor.nodeId(), kit.now())).extracting(CancelRow::id).containsExactlyInAnyOrder(ca, cb);
    }

    @Test
    void 즉시_시도가_같은_결제의_다른_취소와_부딪치면_못_집은_것으로_끝난다() {
        PaymentTestKit.Created created = paid(3_000);
        long first = pending(created.orderId(), 1_000);
        long second = pending(created.orderId(), 1_000);
        assertThat(kit.cancelStore.claimDue(20, kit.executor.nodeId(), kit.now())).extracting(CancelRow::id).containsExactly(first);

        assertThat(kit.executor.attemptNow(second)).isFalse();
        assertThat(kit.cancelStore.findById(second).orElseThrow().status()).isEqualTo(CancelStatus.PENDING);
        assertThat(kit.gateway.cancelCalls.get()).isZero();
    }

    @Test
    void 서버_둘이_같은_결제의_부분_취소들을_동시에_처리해도_포트원_요청은_취소마다_한_번이다() throws Exception {
        int orders = 15;
        List<PaymentTestKit.Created> created = new ArrayList<>();
        List<Long> cancelIds = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            PaymentTestKit.Created order = paid(3_000);
            created.add(order);
            cancelIds.add(pending(order.orderId(), 1_000));
            cancelIds.add(pending(order.orderId(), 2_000));
        }
        List<PaymentCancelWorker> workers = List.of(workerOn(971), workerOn(972));
        ExecutorService pool = Executors.newFixedThreadPool(workers.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (PaymentCancelWorker worker : workers) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int round = 0; round < 12; round++) {
                        worker.runOnce();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(kit.gateway.cancelCalls.get()).as("거절돼 다시 보낸 요청 없음").isEqualTo(orders * 2);
        for (long id : cancelIds) {
            CancelRow row = kit.cancelStore.findById(id).orElseThrow();
            assertThat(row.status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(row.attempts()).isEqualTo(1);
        }
        for (PaymentTestKit.Created order : created) {
            assertThat(kit.order(order.paymentId()).canceledAmount()).isEqualTo(3_000);
        }
    }
}
