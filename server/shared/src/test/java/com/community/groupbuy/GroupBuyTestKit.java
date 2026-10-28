package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditEvent;
import com.community.audit.service.AuditLogService;
import com.community.groupbuy.dto.GroupBuyBuyer;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.JoinRequest;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.dto.Quote;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.groupbuy.handler.GroupBuyPaymentHandler;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.groupbuy.service.DefaultGroupBuySalesSwitch;
import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.service.GroupBuyProductService;
import com.community.groupbuy.service.GroupBuyNoticeCatalog;
import com.community.groupbuy.service.GroupBuyNoticeService;
import com.community.groupbuy.service.GroupBuyAdminService;
import com.community.groupbuy.service.GroupBuyInvariantCheck;
import com.community.groupbuy.service.GroupBuyJoinService;
import com.community.groupbuy.service.GroupBuyQuoteService;
import com.community.groupbuy.service.GroupBuyRefundWorker;
import com.community.groupbuy.service.GroupBuySalesSwitch;
import com.community.groupbuy.service.GroupBuyStateWorker;
import com.community.groupbuy.service.HoldExpiryService;
import com.community.groupbuy.service.ParticipationCancelService;
import com.community.groupbuy.service.ShippingPolicyService;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.repository.master.IdentityStore;
import com.community.identity.service.IdentityVerificationService;
import com.community.membernotification.repository.master.MemberNotificationWriter;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.payment.config.PaymentProperties;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.gateway.fake.FakePgStore;
import com.community.payment.handler.PaymentOrderHandler;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.service.PaymentAnomalyService;
import com.community.payment.service.PaymentCancelExecutor;
import com.community.payment.service.PaymentCancelImmediateAttempt;
import com.community.payment.service.PaymentCancelService;
import com.community.payment.service.PaymentCancelWorker;
import com.community.payment.service.PaymentConfirmService;
import com.community.payment.service.PaymentOrderService;
import com.community.restriction.repository.master.MemberRestrictionStore;
import com.community.restriction.service.MemberRestrictionService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공구 시험의 바탕 — 실제 PostgreSQL({@link CommerceTestDatabase}) 위에 결제 모듈(가짜 PG)과 공구 패키지 전체를 <b>작은 스프링 문맥</b>으로 조립한다
 * (트랜잭션 프록시·{@code MANDATORY}·커밋 뒤 즉시 취소 시도·이벤트를 실제로 확인하려고). 운영자 감사·알림은 모아 두기만 한다.
 *
 * <ul>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 회원·공구·정책 이름은 Snowflake 로 겹치지 않는다. 시계는 실제 지금에서 시작해 앞으로만 간다(배송비 정책 트리거가 DB 의 지금을 본다).
 *       워커는 「차례가 된 줄 전부」를 집으므로 앞 시험이 남긴 줄도 함께 처리될 수 있다 — 시험은 자기 줄만 확인한다.</li>
 *   <li>한 JVM 에 하나를 같이 쓴다({@link #get}).</li>
 * </ul>
 */
final class GroupBuyTestKit {

    static final int NODE_ID = 952;
    private static GroupBuyTestKit instance;
    private static GroupBuyTestKit current;

    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final MutableTestClock clock;
    final SnowflakeIdGenerator ids;
    final AnnotationConfigApplicationContext spring;
    final TransactionTemplate tx;
    final FakePgStore fakePg;
    final List<Object> events = new CopyOnWriteArrayList<>();
    final List<AuditEvent> audits = new CopyOnWriteArrayList<>();
    final List<AdminNotificationEvent> operatorNotices = new CopyOnWriteArrayList<>();
    volatile boolean salesOpen = true;

    final GroupBuyAdminService admin;
    final ShippingPolicyService policies;
    final GroupBuyJoinService join;
    final GroupBuyQuoteService quotes;
    final HoldExpiryService holds;
    final ParticipationCancelService cancels;
    final GroupBuyStateWorker stateWorker;
    final GroupBuyRefundWorker refundWorker;
    final GroupBuyPaymentHandler handler;
    final GroupBuyInvariantCheck invariants;
    final GroupBuyNoticeService notices;
    final GroupBuyNoticeCatalog catalog;
    final GroupBuyProductService products;
    /** 시험 카테고리(상품마다 필수 — 결정 89) */
    final long category;
    final GroupBuyStore groupStore;
    final ParticipationStore participationStore;
    final PaymentOrderStore paymentOrders;
    final PaymentCancelStore paymentCancels;
    final PaymentConfirmService confirm;
    final PaymentCancelWorker cancelWorker;
    final long operator;

    static synchronized GroupBuyTestKit get() {
        if (instance == null) {
            instance = new GroupBuyTestKit();
        }
        return instance;
    }

    /** 시험 문맥 — 공구 패키지는 스캔(판매 스위치 기본 구현만 빼고 시험 것으로), 결제 모듈과 0단계 장치는 손으로 */
    @Configuration
    @EnableTransactionManagement
    @ComponentScan(basePackages = "com.community.groupbuy", excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {DefaultGroupBuySalesSwitch.class, GroupBuyNoticeCatalog.class}))
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
        GroupBuyNoticeCatalog groupBuyNoticeCatalog() {
            // 실제 카탈로그 + 시험 전용 옛 버전(카탈로그 버전 고정 시험)
            return new GroupBuyNoticeCatalog(GroupBuyNoticeCatalog.DEFAULT_PATTERN, "classpath*:groupbuy-test/notice-catalog-*.json");
        }

        @Bean
        GroupBuySalesSwitch groupBuySalesSwitch() {
            return () -> current.salesOpen;
        }

        @Bean
        EventSink eventSink() {
            return new EventSink(current.events);
        }

        @Bean
        AuditLogService auditLogService() {
            List<AuditEvent> sink = current.audits;
            return new AuditLogService(null, null) {
                @Override
                public void record(AuditEvent event) {
                    sink.add(event);
                }
            };
        }

        @Bean
        DataCipher dataCipher() {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            return new DataCipher("1:" + Base64.getEncoder().encodeToString(key), "");
        }

        @Bean
        IdentityVerificationService identityVerificationService(DataSource dataSource, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                                                                ObjectProvider<Clock> clocks) {
            return new IdentityVerificationService(new IdentityStore(dataSource), null, null, null, null, ids, transactions, null, clocks);
        }

        @Bean
        MemberRestrictionService memberRestrictionService(DataSource dataSource, AuditLogService audit, SnowflakeIdGenerator ids, ApplicationEventPublisher events,
                                                          ObjectProvider<Clock> clocks) {
            MemberNotificationPublisher notices = new MemberNotificationPublisher(new MemberNotificationWriter(dataSource), new IsolatedSideWork(dataSource, noFlush()),
                    ids, events);
            Messages messages = new Messages(null, new StandardEnvironment(), "messages/messages") {
                @Override
                public String get(MessageRef ref) {
                    return ref.key();
                }
            };
            return new MemberRestrictionService(new MemberRestrictionStore(dataSource), audit, notices, ids, messages, clocks);
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
        PaymentGateway paymentGateway(DataSource dataSource, PlatformTransactionManager transactions, Clock clock) {
            TransactionTemplate separate = new TransactionTemplate(transactions);
            separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return new FakePaymentGateway(new FakePgStore(dataSource), separate, clock);
        }

        @Bean
        PaymentOrderHandlers paymentOrderHandlers(List<PaymentOrderHandler> handlers) {
            return new PaymentOrderHandlers(handlers);
        }

        @Bean
        NotificationPublisher notificationPublisher(DataSource dataSource, SnowflakeIdGenerator ids) {
            List<AdminNotificationEvent> sink = current.operatorNotices;
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
            return new PaymentCancelWorker(cancels, executor, properties, duration -> {
            }, clock);
        }
    }

    /** 공구 업무 이벤트를 모은다 */
    static class EventSink {

        private final List<Object> sink;

        EventSink(List<Object> sink) {
            this.sink = sink;
        }

        @EventListener
        public void on(Object event) {
            if (event.getClass().getName().startsWith("com.community.groupbuy.")) {
                sink.add(event);
            }
        }
    }

    // 조립 중에만 쓰는 값(Wiring 이 읽는다)
    private final DataSource dataSourceForWiring;
    private final MutableTestClock clockForWiring;
    private final SnowflakeIdGenerator idsForWiring;

    private GroupBuyTestKit() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        clock = new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        ids = new SnowflakeIdGenerator(NODE_ID);
        dataSourceForWiring = dataSource;
        clockForWiring = clock;
        idsForWiring = ids;
        current = this;
        spring = new AnnotationConfigApplicationContext(Wiring.class);
        tx = new TransactionTemplate(spring.getBean(PlatformTransactionManager.class));
        fakePg = new FakePgStore(dataSource);
        admin = spring.getBean(GroupBuyAdminService.class);
        policies = spring.getBean(ShippingPolicyService.class);
        join = spring.getBean(GroupBuyJoinService.class);
        quotes = spring.getBean(GroupBuyQuoteService.class);
        holds = spring.getBean(HoldExpiryService.class);
        cancels = spring.getBean(ParticipationCancelService.class);
        stateWorker = spring.getBean(GroupBuyStateWorker.class);
        refundWorker = spring.getBean(GroupBuyRefundWorker.class);
        handler = spring.getBean(GroupBuyPaymentHandler.class);
        invariants = spring.getBean(GroupBuyInvariantCheck.class);
        notices = spring.getBean(GroupBuyNoticeService.class);
        catalog = spring.getBean(GroupBuyNoticeCatalog.class);
        products = spring.getBean(GroupBuyProductService.class);
        groupStore = spring.getBean(GroupBuyStore.class);
        participationStore = spring.getBean(ParticipationStore.class);
        paymentOrders = spring.getBean(PaymentOrderStore.class);
        paymentCancels = spring.getBean(PaymentCancelStore.class);
        confirm = spring.getBean(PaymentConfirmService.class);
        cancelWorker = spring.getBean(PaymentCancelWorker.class);
        operator = newOperator();
        category = products.createCategory("시험 " + ids.nextId(), null, 0, operator, "127.0.0.1");
    }

    private static EntityManager noFlush() {
        return (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(), new Class<?>[] {EntityManager.class}, (proxy, method, args) -> switch (method.getName()) {
            case "flush" -> null;
            case "toString" -> "noFlushEntityManager";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    /** 시험 하나의 시작 — 판매 스위치 켜기, 모은 이벤트·감사 비우기, 시계를 조금 앞으로(앞 시험과 같은 시각을 쓰지 않게) */
    void reset() {
        salesOpen = true;
        events.clear();
        audits.clear();
        operatorNotices.clear();
        clock.advance(Duration.ofMinutes(1));
    }

    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    void advance(Duration duration) {
        clock.advance(duration);
    }

    // ── 사람 ────────────────────────────────────────────────────────────────

    /** 이메일 인증·본인인증(만 19세 이상)을 마친 정상 회원 */
    long newMember() {
        long id = newMemberWithoutIdentity();
        verify(id, LocalDate.of(1990, 1, 1));
        return id;
    }

    long newMemberWithoutIdentity() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO users (id, email, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, now(), 'v1', now(), now())", id,
                "gb-" + id + "@test.local");
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, "g" + base36.substring(Math.max(0, base36.length() - 11)));
        return id;
    }

    void verify(long userId, LocalDate birthDate) {
        new IdentityStore(dataSource).insertVerification(userId, String.format("%064x", userId), "f" + String.format("%063x", userId), birthDate, IdentityProviderKind.FAKE, "fake:" + userId, now());
    }

    long newOperator() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "gbop-" + id + "@test.local", "gbop" + (id % 10_000_000_000L));
        return id;
    }

    static GroupBuyBuyer buyer(long userId) {
        return new GroupBuyBuyer(userId, true);
    }

    // ── 배송비 정책·공구 ─────────────────────────────────────────────────────

    /** 시험마다 겹치지 않는 정책 이름 — 이미 시작된(1년 전부터) 줄 하나 */
    String policy(long baseFee, Long freeShippingOver, long returnFee, String mode) {
        String code = "T" + Long.toString(ids.nextId(), 36).toUpperCase();
        jdbc.update("INSERT INTO shipping_policies (id, code, name, base_fee, free_shipping_over, return_fee, return_fee_mode, effective_from, created_at) "
                + "VALUES (?, ?, '시험 정책', ?, ?, ?, ?, ?, ?)", ids.nextId(), code, baseFee, freeShippingOver, returnFee, mode, Timestamp.from(now().minus(Duration.ofDays(365))),
                Timestamp.from(now()));
        return code;
    }

    /** 공구 등록 값 — 기본: 선착순·즉시 발송, 1분 전 시작·하루 뒤 마감 */
    Spec spec(String policyCode) {
        return new Spec(policyCode);
    }

    /**
     * 공구 등록 값 빌더 — 상품(결정 89)은 처음 {@link #draft()} 때 만들고, 상품 칸(축·조합·기본 공급가)이 바뀌었으면 그때 상품을 고친다.
     * 상품 단품의 기본 공급가는 {@link #supply}(기본 10,000), 추가 구성상품은 상품을 하나씩 만든다(기본 공급가 = 가격의 반).
     */
    final class Spec {
        String title = "시험 공구";
        String productName = "시험 상품";
        long price = 25_000;
        String policy;
        int minQty = 0;
        int maxQty = 100;
        Integer perUser = null;
        Instant startAt = now().minus(Duration.ofMinutes(1));
        Instant endAt = now().plus(Duration.ofDays(1));
        String extension = "DEMAND";
        String fulfillment = "IMMEDIATE";
        LocalDate shipPlannedAt = null;
        boolean roundLimitShared = false;
        Long supply = 10_000L;
        long categoryId = category;
        List<GroupBuyDraft.AxisDraft> axes = new ArrayList<>();
        /** 조합(값 이름 「 / 」 이음, 축 없으면 "") → 공구 단품 설정 */
        java.util.Map<String, Combo> combos = new java.util.LinkedHashMap<>();
        List<AddonSpec> addons = new ArrayList<>();
        Long productId;
        ProductDraft savedProduct;

        record Combo(long extra, Integer max, boolean onSale, Long supply) {
        }

        record AddonSpec(String name, long price, Integer max, Long supply, long productSkuId) {
        }

        Spec(String policy) {
            this.policy = policy;
        }

        Spec price(long value) {
            price = value;
            return this;
        }

        Spec qty(int min, int max) {
            minQty = min;
            maxQty = max;
            if (min > 0) {
                fulfillment = "AFTER_CLOSE";
            }
            return this;
        }

        Spec afterClose() {
            fulfillment = "AFTER_CLOSE";
            return this;
        }

        Spec perUser(Integer value) {
            perUser = value;
            return this;
        }

        Spec period(Instant start, Instant end) {
            startAt = start;
            endAt = end;
            return this;
        }

        Spec extension(String mode) {
            extension = mode;
            return this;
        }

        /** 상품 단품의 기본 공급가(null = 없음) */
        Spec supply(Long value) {
            supply = value;
            return this;
        }

        /** 상품 카테고리 */
        Spec category(long id) {
            categoryId = id;
            return this;
        }

        /** 이미 있는 상품으로(같은 상품의 두 번째 공구) */
        Spec product(long id) {
            productId = id;
            savedProduct = null;
            return this;
        }

        /** 축 하나(「옵션」)짜리 상품의 값 하나 = 단품 하나(이름 = 값) — 예전 「옵션」 시험용 */
        Spec option(String name, long extra, Integer max) {
            if (axes.isEmpty()) {
                axes.add(new GroupBuyDraft.AxisDraft("옵션", new ArrayList<>()));
            }
            axes.getFirst().values().add(name);
            combos.put(name, new Combo(extra, max, true, null));
            return this;
        }

        /** 옵션 축 */
        Spec axis(String name, String... values) {
            axes.add(new GroupBuyDraft.AxisDraft(name, new ArrayList<>(List.of(values))));
            return this;
        }

        /** 조합 설정(축 차례대로 값) */
        Spec combo(List<String> values, long extra, Integer max, boolean onSale) {
            combos.put(String.join(" / ", values), new Combo(extra, max, onSale, null));
            return this;
        }

        /** 조합의 공급가(공구 단품) */
        Spec comboSupply(List<String> values, Long supplyPrice) {
            Combo c = combos.getOrDefault(String.join(" / ", values), new Combo(0, null, true, null));
            combos.put(String.join(" / ", values), new Combo(c.extra(), c.max(), c.onSale(), supplyPrice));
            return this;
        }

        /** 축 없는 상품의 MAIN 단품 한도 */
        Spec mainMax(Integer max) {
            combos.put("", new Combo(0, max, true, null));
            return this;
        }

        /** 추가 구성상품(축 없는 상품을 하나 만든다) */
        Spec addon(String name, long addonPrice, Integer max) {
            addons.add(new AddonSpec(name, addonPrice, max, null, 0));
            return this;
        }

        Spec shared() {
            roundLimitShared = true;
            return this;
        }

        /** 상품 등록 값 — 단품 목록(축 값 조합마다 한 줄, 이름 = 값을 「 / 」로 이은 것, 축이 없으면 상품 이름). 이미 있는 상품이면 같은 이름의 단품 ID 를 싣는다 */
        ProductDraft productDraft() {
            return productDraft(true);
        }

        private ProductDraft productDraft(boolean withIds) {
            java.util.Map<String, Long> existing = new java.util.HashMap<>();
            if (withIds && productId != null) {
                products.skus(productId).forEach(s -> existing.put(s.name(), s.id()));
            }
            List<ProductDraft.SkuDraft> skus = new ArrayList<>();
            for (List<String> combination : combinations()) {
                String name = combination.isEmpty() ? productName : String.join(" / ", combination);
                skus.add(new ProductDraft.SkuDraft(existing.get(name), name, combination, supply, true));
            }
            return new ProductDraft(categoryId, productName, "요약", "사양", "<p>설명</p>", null, null, null, null, axes.stream().map(GroupBuyDraft.AxisDraft::name).toList(),
                    skus, List.of(new GroupBuyDraft.ImageRef("img/a.jpg", "img/a-t.jpg")));
        }

        private List<List<String>> combinations() {
            List<List<String>> out = new ArrayList<>();
            out.add(List.of());
            for (GroupBuyDraft.AxisDraft axis : axes) {
                List<List<String>> next = new ArrayList<>();
                for (List<String> prefix : out) {
                    for (String value : axis.values()) {
                        List<String> c = new ArrayList<>(prefix);
                        c.add(value);
                        next.add(List.copyOf(c));
                    }
                }
                out = next;
            }
            return out;
        }

        /** 상품을 만들거나(처음) 상품 칸이 바뀌었으면 고친다 */
        long ensureProduct() {
            ProductDraft key = productDraft(false);
            if (productId == null) {
                productId = products.create(key, operator, "127.0.0.1");
                savedProduct = key;
            } else if (savedProduct != null && !savedProduct.equals(key)) {
                products.update(productId, products.product(productId).version(), productDraft(true), operator, "127.0.0.1");
                savedProduct = key;
            }
            for (int k = 0; k < addons.size(); k++) {
                AddonSpec a = addons.get(k);
                if (a.productSkuId() == 0) {
                    long addonProduct = products.create(new ProductDraft(category, a.name(), null, null, null, null, null, null, null, List.of(),
                            List.of(new ProductDraft.SkuDraft(null, a.name(), List.of(), a.price() / 2, true)), List.of()), operator, "127.0.0.1");
                    addons.set(k, new AddonSpec(a.name(), a.price(), a.max(), a.supply(), products.skus(addonProduct).getFirst().id()));
                }
            }
            return productId;
        }

        GroupBuyDraft draft() {
            long product = ensureProduct();
            LocalDate plan = "AFTER_CLOSE".equals(fulfillment) ? (shipPlannedAt != null ? shipPlannedAt : LocalDate.ofInstant(endAt, java.time.ZoneOffset.UTC).plusDays(7))
                    : null;
            List<GroupBuyDraft.SkuDraft> skus = new ArrayList<>();
            for (var ps : products.skus(product)) {
                Combo c = combos.get(ps.valueIds().isEmpty() ? "" : ps.name());
                if (c != null) {
                    skus.add(new GroupBuyDraft.SkuDraft(ps.id(), c.extra(), c.max(), c.onSale(), c.supply()));
                }
            }
            List<GroupBuyDraft.AddonDraft> addonDrafts = addons.stream().map(a -> new GroupBuyDraft.AddonDraft(a.productSkuId(), a.price(), a.max(), a.supply())).toList();
            return new GroupBuyDraft(product, title, price, policy, minQty, maxQty, perUser, startAt, endAt, extension, fulfillment, plan, null, null, roundLimitShared,
                    skus, addonDrafts, false);
        }

        /** 등록만(DRAFT) */
        long create() {
            return admin.create(draft(), operator, "127.0.0.1");
        }

        /** 등록 + 모집 시작(시작 시각이 지났으면 그 자리에서 RECRUITING) */
        long open() {
            long id = create();
            fillNotice(id);
            admin.publish(id, group(id).version(), operator, "127.0.0.1");
            return id;
        }
    }

    // ── 상품정보제공고시 ──────────────────────────────────────────────────────

    /** 그 품목(지금 카탈로그)의 모든 항목을 채운 양식 */
    NoticeDraft fullNotice(String categoryCode, String title, List<String> skuNames) {
        List<NoticeDraft.Value> values = catalog.current().category(categoryCode).orElseThrow().items().stream()
                .map(i -> new NoticeDraft.Value(i.key(), "시험 값 " + i.no(), false, null)).toList();
        return new NoticeDraft(categoryCode, title, skuNames, values);
    }

    /** 공구 상품과 추가 구성상품의 상품마다(결정 88·89) 상품 전체를 덮는 「기타 재화」 양식 하나를 다 채워 저장한다 */
    void fillNotice(long groupBuyId) {
        for (long productId : productsOf(groupBuyId)) {
            notices.save(productId, List.of(fullNotice("OTHER_GOODS", "본체", List.of())), operator, "127.0.0.1");
        }
    }

    /** 공구 상품 */
    long productOf(long groupBuyId) {
        return group(groupBuyId).productId();
    }

    /** 공구 상품과 추가 구성상품의 상품(공구 상품 먼저) */
    List<Long> productsOf(long groupBuyId) {
        java.util.LinkedHashSet<Long> out = new java.util.LinkedHashSet<>();
        out.add(productOf(groupBuyId));
        skus(groupBuyId).stream().filter(s -> !s.main()).forEach(s -> out.add(productSkuOwner(s.productSkuId())));
        return List.copyOf(out);
    }

    long productSkuOwner(long productSkuId) {
        return jdbc.queryForObject("SELECT product_id FROM group_buy_product_skus WHERE id = ?", Long.class, productSkuId);
    }

    GroupBuyRow group(long id) {
        return groupStore.find(id).orElseThrow();
    }

    List<SkuRow> skus(long groupBuyId) {
        return groupStore.skus(groupBuyId);
    }

    long skuId(long groupBuyId, String name) {
        return skus(groupBuyId).stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow().id();
    }

    SkuRow sku(long groupBuyId, long skuId) {
        return skus(groupBuyId).stream().filter(s -> s.id() == skuId).findFirst().orElseThrow();
    }

    /** 축 없는 공구의 MAIN 단품(하나) */
    long mainSku(long groupBuyId) {
        List<SkuRow> mains = skus(groupBuyId).stream().filter(SkuRow::main).toList();
        assertThat(mains).as("축 없는 공구의 MAIN 단품").hasSize(1);
        return mains.getFirst().id();
    }

    // ── 주문·결제 ───────────────────────────────────────────────────────────

    static ShippingAddress address() {
        return new ShippingAddress("홍길동", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101호", "문 앞");
    }

    static List<OrderLine> lines(Object... skuAndQty) {
        List<OrderLine> out = new ArrayList<>();
        for (int i = 0; i < skuAndQty.length; i += 2) {
            out.add(new OrderLine((Long) skuAndQty[i], (Integer) skuAndQty[i + 1]));
        }
        return out;
    }

    /** 견적 금액 그대로 주문(동의 3종 모두) */
    JoinResult order(long userId, long groupBuyId, List<OrderLine> lines) {
        Quote quote = quotes.quote(groupBuyId, lines);
        return join.join(buyer(userId), new JoinRequest(groupBuyId, lines, address(), true, true, true, quote.amount()));
    }

    /** 축 없는 공구에 수량만(MAIN 단품 하나) */
    JoinResult order(long userId, long groupBuyId, int qty) {
        return order(userId, groupBuyId, lines(mainSku(groupBuyId), qty));
    }

    /** 가짜 PG 에 결제됨(지금 시각)을 쓰고 확정 코드를 부른다(웹훅) */
    PaymentConfirmService.ConfirmResult pay(JoinResult joined) {
        return payAt(joined.checkout().paymentId(), joined.amount(), now());
    }

    PaymentConfirmService.ConfirmResult payAt(String paymentId, long amount, Instant paidAt) {
        fakePg.pay(paymentId, amount, "KRW", paidAt);
        return confirm.confirm(paymentId, ConfirmedVia.WEBHOOK);
    }

    /** 주문 + 결제 */
    JoinResult paidOrder(long userId, long groupBuyId, List<OrderLine> lines) {
        JoinResult joined = order(userId, groupBuyId, lines);
        assertThat(pay(joined).outcome()).isEqualTo(PaymentConfirmService.Outcome.ACCEPTED);
        return joined;
    }

    JoinResult paidOrder(long userId, long groupBuyId, int qty) {
        return paidOrder(userId, groupBuyId, lines(mainSku(groupBuyId), qty));
    }

    ParticipationRow participation(long id) {
        return participationStore.find(id).orElseThrow();
    }

    List<ItemRow> items(long participationId) {
        return participationStore.items(participationId);
    }

    OrderRow payment(String paymentId) {
        return paymentOrders.findByPaymentId(paymentId).orElseThrow();
    }

    int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    /** 그 공구들의 불변식(결정 28·39·65·77·80)이 모두 맞다 */
    void assertInvariants(Long... groupBuyIds) {
        assertThat(invariants.violations(List.of(groupBuyIds))).as("공구 불변식").isEmpty();
    }

    /** 일이 없어질 때까지 워커를 돌린다(앞 시험의 줄도 함께 끝난다) */
    void runStateWorker() {
        for (int i = 0; i < 10 && stateWorker.runOnce() > 0; i++) {
            // 다음 단계가 앞 단계 결과를 이어 받는다
        }
    }

    void runRefundWorker() {
        for (int i = 0; i < 100 && refundWorker.runOnce() > 0; i++) {
            // 바퀴당 상한 50
        }
    }

    @SuppressWarnings("unchecked")
    <T> List<T> eventsOf(Class<T> type) {
        return (List<T>) events.stream().filter(type::isInstance).toList();
    }

    /** 여러 일을 동시에 시작한다(연결 여럿) — 각 일의 예외는 결과 목록에 담는다 */
    static List<Object> runTogether(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> out = new ArrayList<>();
            for (Future<Object> future : futures) {
                out.add(future.get(60, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
