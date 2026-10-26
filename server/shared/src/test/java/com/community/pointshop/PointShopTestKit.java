package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditEvent;
import com.community.audit.service.AuditLogService;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.membernotification.repository.master.MemberNotificationWriter;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.pointshop.dto.PointShopBuyer;
import com.community.pointshop.dto.SelfRefundOutcome;
import com.community.pointshop.dto.request.PointShopItemSaveRequest;
import com.community.pointshop.dto.response.PointShopAdminItemView;
import com.community.pointshop.dto.response.PointShopPurchaseResultView;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.service.AvatarCatalog;
import com.community.pointshop.service.NicknameTicketService;
import com.community.pointshop.service.PointShopCatalogService;
import com.community.pointshop.service.PointShopPurchaseService;
import com.community.pointshop.service.PointShopRefundService;
import com.community.pointshop.service.ProfileAvatarService;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.pointshop.service.UserItemService;
import com.community.restriction.repository.master.MemberRestrictionStore;
import com.community.restriction.service.MemberRestrictionService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import com.community.testsupport.PointInvariants;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 포인트 상점 시험의 바탕 — 실제 PostgreSQL({@link CommerceTestDatabase}) 위에서 포인트({@link PointService})와 포인트 상점 서비스를 <b>손으로</b> 조립한다(1-1 {@code PointTestKit} 과 같은 방식 —
 * 스프링 문맥 없음, Mockito 없음). 트랜잭션은 {@link DataSourceTransactionManager}(서비스·저장소의 JDBC 가 같은 연결을 쓴다), 시계는 {@link MutableTestClock}.
 * 감사는 기록을 모으는 대역({@link #audits}). DB 는 다른 시험과 같이 쓴다 — 회원·품목을 시험마다 새로 만들고, 공용 행(설정·시드 아바타)을 바꾸면 되돌린다.
 */
final class PointShopTestKit {

    /** 다른 시험 클래스의 발급기와 겹치지 않는 서버 번호 */
    private static final int NODE_ID = 902;

    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final MutableTestClock clock;
    final SnowflakeIdGenerator ids;
    final PointService points;
    final PointShopItemStore itemStore;
    final AvatarCatalog avatars;
    final List<AuditEvent> audits = Collections.synchronizedList(new ArrayList<>());
    /** 낸 운영자 알림(대역 — 발행 이벤트만 모은다) */
    final List<AdminNotificationEvent> operatorNotices = Collections.synchronizedList(new ArrayList<>());
    /** 회원 알림 메일 요청(커밋 뒤 발송기에게 가는 것) */
    final List<Object> memberEmailEvents = Collections.synchronizedList(new ArrayList<>());
    final PointShopCatalogService catalog;
    final PointShopPurchaseService purchases;
    final PointShopRefundService refunds;
    final UserItemService userItems;
    final NicknameTicketService tickets;
    final ProfileAvatarService profileAvatars;
    final ShopDecorationResolver decorations;
    final long operator;

    PointShopTestKit() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        clock = new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        ids = new SnowflakeIdGenerator(NODE_ID);
        ObjectProvider<Clock> clocks = new StaticListableBeanFactory(Map.of("clock", clock)).getBeanProvider(Clock.class);
        MemberRestrictionService restrictions = new MemberRestrictionService(new MemberRestrictionStore(dataSource), null, null, ids, null, clocks);
        points = new PointService(new PointLedgerWriter(dataSource), new PointPolicy(dataSource), new IsolatedSideWork(dataSource, noFlush()), ids, restrictions, clocks);
        AuditLogService audit = new AuditLogService(null, null) {
            @Override
            public void record(AuditEvent event) {
                audits.add(event);
            }
        };
        itemStore = new PointShopItemStore(dataSource);
        PointShopPurchaseStore purchaseStore = new PointShopPurchaseStore(dataSource);
        UserItemStore userItemStore = new UserItemStore(dataSource);
        PointShopMemberStore members = new PointShopMemberStore(dataSource);
        avatars = new AvatarCatalog();
        catalog = new PointShopCatalogService(itemStore, userItemStore, members, avatars, audit, ids, clocks);
        purchases = new PointShopPurchaseService(itemStore, purchaseStore, userItemStore, members, catalog, points, restrictions, ids, clocks);
        NotificationPublisher operatorPublisher = new NotificationPublisher(null, null, null, null, ids, null, null, dataSource) {
            @Override
            public int publish(AdminNotificationEvent event, NotificationContext context) {
                operatorNotices.add(event);
                return 1;
            }
        };
        MemberNotificationPublisher memberPublisher = new MemberNotificationPublisher(new MemberNotificationWriter(dataSource), new IsolatedSideWork(dataSource, noFlush()),
                ids, memberEmailEvents::add);
        refunds = new PointShopRefundService(itemStore, purchaseStore, userItemStore, members, points, audit, operatorPublisher, memberPublisher, ids, clocks);
        userItems = new UserItemService(userItemStore, itemStore, members, purchaseStore, avatars, clocks);
        tickets = new NicknameTicketService(userItemStore, itemStore, clocks);
        profileAvatars = new ProfileAvatarService(avatars, itemStore, userItemStore, members, clocks);
        decorations = new ShopDecorationResolver(userItemStore, members, avatars, clocks);
        operator = newOperator();
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

    // ── 사람 ────────────────────────────────────────────────────────────────

    /** 이메일 인증을 마친 정상 회원 + 프로필 */
    long newMember() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO users (id, email, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, now(), 'v1', now(), now())", id,
                "ps-" + id + "@test.local");
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, "p" + base36.substring(Math.max(0, base36.length() - 11)));
        return id;
    }

    long newOperator() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "psop-" + id + "@test.local", "psop" + (id % 10_000_000_000L));
        return id;
    }

    /** 포인트 동결(기한 없음, 공개 사유 있음) */
    void freeze(long userId) {
        jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'POINT_FREEZE', '시험 동결', ?, 'AUTO', "
                + "'POINT_TEST', ?)", ids.nextId(), userId, Timestamp.from(clock.instant().minusSeconds(60)), Timestamp.from(clock.instant()));
    }

    // ── 포인트 ──────────────────────────────────────────────────────────────

    <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /** 적립(무상) 포인트 — 운영 지급 묶음 하나(1년) */
    void grant(long userId, int amount) {
        inTx(() -> points.adjust(userId, amount, "시험 지급", operator));
    }

    /** 충전(유상) 포인트를 흉내 낸다(3단계 충전이 아직 없다 — 1-1 PointTestKit 과 같은 방식) — 유상 묶음 ID */
    long chargePaid(long userId, long amount) {
        return inTx(() -> {
            Instant now = clock.instant();
            PointLedgerWriter writer = new PointLedgerWriter(dataSource);
            PointLedgerWriter.Balance balance = writer.lockBalance(userId, now);
            long line = ids.nextId();
            writer.insertEntry(line, userId, PointKind.EARN, PointReason.CHARGE.name(), (int) amount, 0, amount, balance.free() + balance.paid() + amount, null, null, null,
                    null, now);
            long lot = ids.nextId();
            writer.insertLot(new PointLedgerWriter.NewLot(lot, userId, PointLotKind.PAID, amount, null, PointReason.CHARGE.name(), null, null, line, null, now));
            writer.insertEntryLot(line, lot, amount);
            writer.saveBalance(userId, balance.free(), balance.paid() + amount, balance.debt(), now);
            return lot;
        });
    }

    /** {free, paid, debt, balance} */
    long[] balance(long userId) {
        List<long[]> rows = jdbc.query("SELECT free_balance, paid_balance, revoke_debt, balance FROM point_balances WHERE user_id = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, userId);
        return rows.isEmpty() ? new long[] {0, 0, 0, 0} : rows.get(0);
    }

    long total(long userId) {
        return balance(userId)[3];
    }

    void assertInvariants(long... userIds) {
        assertThat(PointInvariants.violations(jdbc, userIds)).as("포인트 불변식").isEmpty();
    }

    // ── 품목 ────────────────────────────────────────────────────────────────

    /** 시험마다 겹치지 않는 코드 */
    String code(String prefix) {
        return prefix + "-" + Long.toString(ids.nextId(), 36);
    }

    static PointShopItemSaveRequest request(String code, PointShopItemKind kind, PointShopUsageType usage, int price, String color, String border, Integer duration,
                                            Integer stock, Instant saleStartAt, Instant saleEndAt, boolean active) {
        return new PointShopItemSaveRequest(code, kind == null ? null : kind.name(), usage == null ? null : usage.name(), "시험 " + code, null, "시험 품목이에요.", null, color, border, null, price, duration, stock, saleStartAt, saleEndAt,
                active, 0);
    }

    PointShopAdminItemView create(PointShopItemSaveRequest request) {
        return inTx(() -> catalog.create(request, operator, "127.0.0.1"));
    }

    long color(int price, String hex) {
        return Long.parseLong(create(request(code("c"), PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, price, hex, null, null, null, null, null, true)).id());
    }

    long border(int price, String style, Integer stock) {
        return Long.parseLong(create(request(code("b"), PointShopItemKind.PROFILE_BORDER, PointShopUsageType.PERMANENT, price, null, style, null, stock, null, null, true)).id());
    }

    long periodColor(int price, int days) {
        return Long.parseLong(create(request(code("pc"), PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERIOD, price, "#EA580C", null, days, null, null, null, true)).id());
    }

    long ticketItem(int price) {
        return Long.parseLong(create(request(code("t"), PointShopItemKind.NICKNAME_CHANGE_TICKET, PointShopUsageType.CONSUMABLE, price, null, null, null, null, null, null,
                true)).id());
    }

    /** 시드 아바타 품목 ID */
    long avatarItem(String avatarId) {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE avatar_id = ?", Long.class, avatarId);
    }

    /** 시드 변경권 품목 ID */
    long seedTicket() {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'nickname-change'", Long.class);
    }

    Integer stock(long itemId) {
        return jdbc.queryForObject("SELECT stock FROM point_shop_items WHERE id = ?", Integer.class, itemId);
    }

    // ── 구매·환불 ───────────────────────────────────────────────────────────

    PointShopPurchaseResultView buy(long userId, long itemId) {
        return inTx(() -> purchases.purchase(new PointShopBuyer(userId, true), itemId, true));
    }

    long buyItem(long userId, long itemId) {
        return Long.parseLong(buy(userId, itemId).userItemId());
    }

    long refundSelf(long userId, long userItemId) {
        SelfRefundOutcome outcome = inTx(() -> refunds.refundSelf(userId, userItemId));
        assertThat(outcome.isRequest()).as("바로 환불(정지 중이 아님)").isFalse();
        return outcome.refunded().refundedPoints();
    }

    /** 셀프 환불을 부르고 결과를 그대로(정지 중이면 신청) */
    SelfRefundOutcome selfRefund(long userId, long userItemId) {
        return inTx(() -> refunds.refundSelf(userId, userItemId));
    }

    void suspend(long userId) {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", userId);
    }

    void unsuspend(long userId) {
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", userId);
    }

    long purchaseOf(long userItemId) {
        return jdbc.queryForObject("SELECT purchase_id FROM user_items WHERE id = ?", Long.class, userItemId);
    }

    Map<String, Object> purchaseRow(long purchaseId) {
        return jdbc.queryForMap("SELECT * FROM point_shop_purchases WHERE id = ?", purchaseId);
    }

    Map<String, Object> userItemRow(long userItemId) {
        return jdbc.queryForMap("SELECT * FROM user_items WHERE id = ?", userItemId);
    }

    /** 셀프 환불 기한을 잠시 바꾼다(설정 행은 하나 — 끝나면 되돌린다) */
    void withRefundDays(int days, Runnable work) {
        Integer before = jdbc.queryForObject("SELECT refund_days FROM point_shop_settings WHERE id = 1", Integer.class);
        jdbc.update("UPDATE point_shop_settings SET refund_days = ? WHERE id = 1", days);
        try {
            work.run();
        } finally {
            jdbc.update("UPDATE point_shop_settings SET refund_days = ? WHERE id = 1", before);
        }
    }

    int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    // ── 동시에 ──────────────────────────────────────────────────────────────

    /** 일을 한꺼번에 출발시킨다(스레드마다 연결·트랜잭션 따로) */
    static <T> List<T> runTogether(List<Callable<T>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> job : jobs) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return job.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
