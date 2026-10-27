package com.community.payment;

import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.core.BatchRunner;
import com.community.batch.entity.BatchRequestKind;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.payment.batch.PaymentReadyCleanupJob;
import com.community.payment.batch.PaymentReconciliationJob;
import com.community.payment.config.PaymentProperties;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PgPaymentStatus;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.gateway.fake.FakePgStore;
import com.community.payment.handler.PaymentOrderHandler;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentReconcileDayStore;
import com.community.payment.repository.master.PaymentReconcileQueries;
import com.community.payment.repository.master.PaymentWebhookLogStore;
import com.community.payment.repository.master.ReconciliationIssueStore;
import com.community.payment.service.PaymentAnomalyService;
import com.community.payment.service.PaymentCancelExecutor;
import com.community.payment.service.PaymentCancelImmediateAttempt;
import com.community.payment.service.PaymentCancelService;
import com.community.payment.service.PaymentCancelWorker;
import com.community.payment.service.PaymentCompletionService;
import com.community.payment.service.PaymentConfirmService;
import com.community.payment.service.PaymentOrderService;
import com.community.payment.service.PaymentWebhookService;
import com.community.payment.service.ReconciliationService;
import com.community.payment.testorder.PaymentTestOrderHandler;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 시험의 바탕 — 실제 PostgreSQL({@link CommerceTestDatabase}) 위에 결제 모듈을 <b>작은 스프링 문맥</b>으로 조립한다(트랜잭션 프록시 — {@code MANDATORY}·커밋 뒤 이벤트를
 * 실제로 확인하려고). 게이트웨이는 가짜 PG 를 감싼 {@link TestGateway}, 처리기는 실제 시험 주문 처리기({@link PaymentTestOrderHandler}), 운영자 알림은 모아 두기만 한다.
 *
 * <ul>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 워커·READY 정리는 「차례가 된 줄 전부」를 집으므로, 시험마다 {@link #newEpoch} 로 시계를 <b>앞 시험보다 이른 과거</b>로 옮긴다
 *       (앞 시험이 남긴 줄은 이 시계로는 아직 차례가 아니다 — MailOutboxTest 와 같은 방법). 회원·주문 번호는 Snowflake.</li>
 *   <li>한 JVM 에 하나를 같이 쓴다({@link #get}).</li>
 * </ul>
 */
final class PaymentTestKit {

    /** 시험 문맥의 서버 번호(취소 줄의 locked_by) */
    static final int NODE_ID = 951;

    private static final AtomicLong EPOCH = new AtomicLong();
    private static PaymentTestKit instance;

    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final MutableTestClock clock;
    final SnowflakeIdGenerator ids;
    final AnnotationConfigApplicationContext spring;
    final TransactionTemplate tx;
    final TestGateway gateway;
    final FakePgStore fakePg;
    final PaymentTestOrderHandler handler;
    final List<AdminNotificationEvent> notices = new CopyOnWriteArrayList<>();
    final List<Duration> pauses = new CopyOnWriteArrayList<>();
    final PaymentOrderStore orderStore;
    final PaymentCancelStore cancelStore;
    final PaymentOrderService orders;
    final PaymentCancelService cancels;
    final PaymentConfirmService confirm;
    final PaymentCancelExecutor executor;
    final PaymentCancelWorker worker;
    final PaymentCompletionService completion;
    final PaymentWebhookService webhooks;
    final PaymentReadyCleanupJob readyCleanup;
    final ReconciliationService reconciliation;
    final PaymentReconciliationJob reconcileJob;
    final PaymentReconcileDayStore reconcileDays;
    final ReconciliationIssueStore issueStore;

    private static PaymentTestKit current;

    static synchronized PaymentTestKit get() {
        if (instance == null) {
            instance = new PaymentTestKit();
        }
        return instance;
    }

    @Configuration
    @EnableTransactionManagement
    static class Wiring {

        @Bean
        DataSource dataSource() {
            return current.dataSourceForWiring;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        Clock clock() {
            return current.clockForWiring;
        }

        @Bean
        SnowflakeIdGenerator snowflakeIdGenerator() {
            return current.idsForWiring;
        }

        @Bean
        PaymentProperties paymentProperties() {
            return PaymentProperties.DEFAULTS;
        }

        @Bean
        PaymentOrderStore paymentOrderStore(DataSource dataSource) {
            return new PaymentOrderStore(dataSource);
        }

        @Bean
        PaymentCancelStore paymentCancelStore(DataSource dataSource) {
            return new PaymentCancelStore(dataSource);
        }

        @Bean
        PaymentEventStore paymentEventStore(DataSource dataSource) {
            return new PaymentEventStore(dataSource);
        }

        @Bean
        PaymentWebhookLogStore paymentWebhookLogStore(DataSource dataSource) {
            return new PaymentWebhookLogStore(dataSource);
        }

        @Bean
        ReconciliationIssueStore reconciliationIssueStore(DataSource dataSource) {
            return new ReconciliationIssueStore(dataSource);
        }

        @Bean
        TestGateway paymentGateway(DataSource dataSource, PlatformTransactionManager transactions, Clock clock) {
            TransactionTemplate separate = new TransactionTemplate(transactions);
            separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return new TestGateway(new FakePaymentGateway(new FakePgStore(dataSource), separate, clock));
        }

        @Bean
        PaymentTestOrderHandler paymentTestOrderHandler() {
            return new PaymentTestOrderHandler();
        }

        @Bean
        PaymentOrderHandlers paymentOrderHandlers(List<PaymentOrderHandler> handlers) {
            return new PaymentOrderHandlers(handlers);
        }

        @Bean
        NotificationPublisher notificationPublisher(DataSource dataSource, SnowflakeIdGenerator ids) {
            List<AdminNotificationEvent> sink = current.notices;
            return new NotificationPublisher(null, null, null, null, ids, null, null, dataSource) {
                @Override
                public int publish(AdminNotificationEvent event, NotificationContext context) {
                    sink.add(event);
                    return 1;
                }
            };
        }

        @Bean
        PaymentAnomalyService paymentAnomalyService(NotificationPublisher publisher) {
            return new PaymentAnomalyService(publisher);
        }

        @Bean
        PaymentOrderService paymentOrderService(PaymentOrderStore orders, PaymentEventStore events, PaymentOrderHandlers handlers, PaymentGateway gateway,
                                                SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
            return new PaymentOrderService(orders, events, handlers, gateway, ids, clocks);
        }

        @Bean
        PaymentCancelService paymentCancelService(PaymentOrderStore orders, PaymentCancelStore cancels, PaymentEventStore events, SnowflakeIdGenerator ids,
                                                  ApplicationEventPublisher publisher, ObjectProvider<Clock> clocks) {
            return new PaymentCancelService(orders, cancels, events, ids, publisher, clocks);
        }

        @Bean
        PaymentConfirmService paymentConfirmService(PaymentOrderStore orders, PaymentEventStore events, PaymentCancelService cancels, PaymentOrderHandlers handlers,
                                                    PaymentGateway gateway, PaymentAnomalyService anomalies, SnowflakeIdGenerator ids,
                                                    PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
            return new PaymentConfirmService(orders, events, cancels, handlers, gateway, anomalies, ids, transactions, clocks);
        }

        @Bean
        PaymentCancelExecutor paymentCancelExecutor(PaymentOrderStore orders, PaymentCancelStore cancels, PaymentEventStore events, PaymentOrderHandlers handlers,
                                                    PaymentGateway gateway, PaymentAnomalyService anomalies, PaymentProperties properties, SnowflakeIdGenerator ids,
                                                    PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
            return new PaymentCancelExecutor(orders, cancels, events, handlers, gateway, anomalies, properties, ids, transactions, NODE_ID, clocks);
        }

        @Bean
        PaymentCancelImmediateAttempt paymentCancelImmediateAttempt(PaymentCancelExecutor executor) {
            return new PaymentCancelImmediateAttempt(executor);
        }

        @Bean
        PaymentCancelWorker paymentCancelWorker(PaymentCancelStore cancels, PaymentCancelExecutor executor, PaymentProperties properties, Clock clock) {
            List<Duration> pauses = current.pauses;
            return new PaymentCancelWorker(cancels, executor, properties, pauses::add, clock);
        }

        @Bean
        PaymentCompletionService paymentCompletionService(PaymentOrderStore orders, PaymentConfirmService confirm) {
            return new PaymentCompletionService(orders, confirm);
        }

        @Bean
        PaymentWebhookService paymentWebhookService(PaymentGateway gateway, PaymentConfirmService confirm, PaymentCancelExecutor executor, PaymentOrderStore orders,
                                                    PaymentCancelStore cancels, PaymentEventStore events, PaymentWebhookLogStore logs, ReconciliationIssueStore issues,
                                                    SnowflakeIdGenerator ids, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
            return new PaymentWebhookService(gateway, confirm, executor, orders, cancels, events, logs, issues, ids, transactions, clocks);
        }

        @Bean
        PaymentReconcileQueries paymentReconcileQueries(DataSource dataSource) {
            return new PaymentReconcileQueries(dataSource);
        }

        @Bean
        PaymentReconcileDayStore paymentReconcileDayStore(DataSource dataSource) {
            return new PaymentReconcileDayStore(dataSource);
        }

        @Bean
        ReconciliationService reconciliationService(PaymentGateway gateway, PaymentOrderStore orders, PaymentReconcileQueries queries, PaymentReconcileDayStore days,
                                                    ReconciliationIssueStore issues, NotificationPublisher publisher, SnowflakeIdGenerator ids,
                                                    PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
            return new ReconciliationService(gateway, orders, queries, days, issues, publisher, ids, transactions, clocks);
        }

        @Bean
        PaymentReconciliationJob paymentReconciliationJob(ReconciliationService reconciliation, PaymentReconcileDayStore days) {
            return new PaymentReconciliationJob(reconciliation, days);
        }

        @Bean
        PaymentReadyCleanupJob paymentReadyCleanupJob(PaymentOrderStore orders, PaymentConfirmService confirm, DataSource dataSource, ObjectProvider<Clock> clocks) {
            return new PaymentReadyCleanupJob(orders, confirm, dataSource, clocks);
        }
    }

    // 조립 중에만 쓰는 값(Wiring 이 읽는다)
    private final DataSource dataSourceForWiring;
    private final MutableTestClock clockForWiring;
    private final SnowflakeIdGenerator idsForWiring;

    private PaymentTestKit() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        clock = new MutableTestClock(Instant.parse("1990-01-01T00:00:00Z"));
        ids = new SnowflakeIdGenerator(NODE_ID);
        dataSourceForWiring = dataSource;
        clockForWiring = clock;
        idsForWiring = ids;
        current = this;
        spring = new AnnotationConfigApplicationContext(Wiring.class);
        tx = new TransactionTemplate(spring.getBean(PlatformTransactionManager.class));
        gateway = spring.getBean(TestGateway.class);
        fakePg = gateway.fake.store();
        handler = spring.getBean(PaymentTestOrderHandler.class);
        orderStore = spring.getBean(PaymentOrderStore.class);
        cancelStore = spring.getBean(PaymentCancelStore.class);
        orders = spring.getBean(PaymentOrderService.class);
        cancels = spring.getBean(PaymentCancelService.class);
        confirm = spring.getBean(PaymentConfirmService.class);
        executor = spring.getBean(PaymentCancelExecutor.class);
        worker = spring.getBean(PaymentCancelWorker.class);
        completion = spring.getBean(PaymentCompletionService.class);
        webhooks = spring.getBean(PaymentWebhookService.class);
        readyCleanup = spring.getBean(PaymentReadyCleanupJob.class);
        reconciliation = spring.getBean(ReconciliationService.class);
        reconcileJob = spring.getBean(PaymentReconciliationJob.class);
        reconcileDays = spring.getBean(PaymentReconcileDayStore.class);
        issueStore = spring.getBean(ReconciliationIssueStore.class);
    }

    /** 시험 하나의 시작 — 시계를 앞 시험보다 30일 이른 과거로, 게이트웨이·알림·쉼 기록을 비운다 */
    void newEpoch() {
        clock.set(Instant.parse("1990-01-01T00:00:00Z").minus(Duration.ofDays(30 * EPOCH.incrementAndGet())));
        gateway.reset();
        notices.clear();
        pauses.clear();
    }

    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    long newMember() {
        return ids.nextId();
    }

    /** 시험 주문 결제를 만든다(도메인 주문 번호 새로, 판정 지정) */
    Created create(long userId, long amount, PaymentTestOrderHandler.Decision decision) {
        long ref = ids.nextId();
        return createFor(userId, ref, amount, decision);
    }

    /** 같은 도메인 주문의 결제를 만든다(재결제) */
    Created createFor(long userId, long orderRefId, long amount, PaymentTestOrderHandler.Decision decision) {
        handler.decide(orderRefId, decision);
        PaymentCheckout checkout = tx.execute(status -> orders.create(new PaymentOrderRequest(userId, PaymentOrderType.PAYMENT_TEST, orderRefId, "시험 결제", amount,
                Duration.ofMinutes(5))));
        return new Created(userId, orderRefId, checkout);
    }

    /** 가짜 PG 에 「결제됨」을 쓴다(지금 시각) */
    void pgPaid(String paymentId, long amount) {
        fakePg.pay(paymentId, amount, "KRW", now());
    }

    void pgPaidAt(String paymentId, long amount, Instant paidAt) {
        fakePg.pay(paymentId, amount, "KRW", paidAt);
    }

    void pgStatus(String paymentId, PgPaymentStatus status, long amount) {
        fakePg.setStatus(paymentId, status, amount, now());
    }

    OrderRow order(String paymentId) {
        return orderStore.findByPaymentId(paymentId).orElseThrow();
    }

    List<CancelRow> cancelsOf(long paymentOrderId) {
        return cancelStore.findByOrder(paymentOrderId);
    }

    int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    int events(long paymentOrderId, String kind) {
        return count("SELECT count(*) FROM payment_events WHERE payment_order_id = ? AND kind = ?", paymentOrderId, kind);
    }

    /** 배치 실행기(배치 core) — 결제 READY 정리를 「지금 실행」으로 돌린다 */
    BatchRunner.RunReport runReadyCleanup() {
        return runJob(readyCleanup, Map.of());
    }

    /** 배치 실행기(배치 core) — 결제 대사를 「지금 실행」으로 돌린다({@code day} 가 있으면 그 날만) */
    BatchRunner.RunReport runReconciliation(java.time.LocalDate day) {
        return runJob(reconcileJob, day == null ? Map.of() : Map.of(PaymentReconciliationJob.PARAM_DAY, day.toString()));
    }

    BatchRunner.RunReport runJob(BatchJob job, Map<String, String> params) {
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        BatchRunner runner = new BatchRunner(new BatchLockService(dataSource), new BatchRunRecorder(dataSource, transactions, ids, NODE_ID),
                new BatchFailureRecorder(dataSource, transactions, ids), BatchRegistry.forJobs(List.of(job)), transactions);
        return runner.runRequested(new BatchRunner.RunRequest(job.definition(), BatchRequestKind.NOW, null, BatchParameters.of(params), null, null, null));
    }

    /** 만든 결제 */
    record Created(long userId, long orderRefId, PaymentCheckout checkout) {

        String paymentId() {
            return checkout.paymentId();
        }

        long orderId() {
            return checkout.paymentOrderId();
        }

        long amount() {
            return checkout.amount();
        }
    }
}
