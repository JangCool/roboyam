package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.restriction.repository.master.MemberRestrictionStore;
import com.community.restriction.service.MemberRestrictionService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 포인트 시험의 바탕 — 실제 PostgreSQL({@link CommerceTestDatabase}) 위에서 {@link PointService} 를 <b>손으로</b> 조립한다(스프링 문맥 없음 — 이 PC 는 Mockito 를 쓰지 않는다).
 *
 * <ul>
 *   <li>트랜잭션은 {@link DataSourceTransactionManager} — 서비스·저장소의 JDBC 와 {@link IsolatedSideWork} 의 세이브포인트가 같은 연결을 쓴다. 영속성 컨텍스트가 없어 {@code flush} 는
 *       아무 일도 하지 않는 대역이다. 기능 제한은 실제 저장소({@code member_restrictions})를 읽는다(감사·알림·문구는 쓰지 않으므로 비운다).</li>
 *   <li>시계는 {@link MutableTestClock} — 소멸일·만료 묶음을 기다리지 않고 본다. Snowflake ID 와 「오늘」(하루 상한)은 실제 시계다.</li>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 회원·운영자를 시험마다 새로 만들고(Snowflake ID) 자기 행만 본다.</li>
 * </ul>
 */
final class PointTestKit {

    /** 다른 시험 클래스의 발급기와 겹치지 않는 서버 번호 */
    private static final int NODE_ID = 901;

    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final MutableTestClock clock;
    final SnowflakeIdGenerator ids;
    final PointLedgerWriter writer;
    final PointPolicy policy;
    final PointService points;
    final long operator;

    PointTestKit() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        clock = new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        ids = new SnowflakeIdGenerator(NODE_ID);
        writer = new PointLedgerWriter(dataSource);
        policy = new PointPolicy(dataSource);
        ObjectProvider<Clock> clocks = new StaticListableBeanFactory(Map.of("clock", clock)).getBeanProvider(Clock.class);
        MemberRestrictionService restrictions = new MemberRestrictionService(new MemberRestrictionStore(dataSource), null, null, ids, null, clocks);
        points = new PointService(writer, policy, new IsolatedSideWork(dataSource, noFlush()), ids, restrictions, clocks);
        operator = newOperator();
    }

    /** 영속성 컨텍스트가 없다 — {@code flush} 만 받는 대역 */
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

    long newMember() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO users (id, email, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, 'v1', now(), now())", id, "pt-" + id + "@test.local");
        return id;
    }

    long newOperator() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "ptop-" + id + "@test.local", "ptop" + (id % 10_000_000_000L));
        return id;
    }

    /** 포인트 동결(기한 없음, 자동 부여 모양) */
    void freeze(long userId) {
        jdbc.update("INSERT INTO member_restrictions (id, user_id, type, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'POINT_FREEZE', ?, 'AUTO', 'POINT_TEST', ?)",
                ids.nextId(), userId, Timestamp.from(clock.instant().minusSeconds(60)), Timestamp.from(clock.instant()));
    }

    // ── 서비스 부르기(각각 한 트랜잭션) ─────────────────────────────────────

    <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /** 글 적립(새 글) — 지급한 양 */
    long earnPost(long userId, long postId) {
        return inTx(() -> points.earn(userId, PointReason.POST, postId));
    }

    long revokePost(long userId, long postId) {
        return inTx(() -> points.revokeContent(userId, PointService.REF_POST, postId));
    }

    long restorePost(long userId, long postId) {
        return inTx(() -> points.restoreContent(userId, postId));
    }

    PointService.Adjusted adjust(long userId, int delta) {
        return inTx(() -> points.adjust(userId, delta, "시험 조정", operator));
    }

    /** 운영 지급(+) — 그 지급 줄 ID(미회수 상계 줄이 뒤에 붙어도 지급 줄) */
    long grantLine(long userId, int amount) {
        adjust(userId, amount);
        return jdbc.queryForObject("SELECT max(id) FROM point_ledger WHERE user_id = ? AND reason = 'ADMIN_ADJUST' AND kind = 'EARN'", Long.class, userId);
    }

    /** 포인트 상점 구매처럼 쓴다 — 사용 줄 ID */
    long use(long userId, long amount) {
        long purchase = ids.nextId();
        return inTx(() -> points.use(userId, amount, PointReason.POINT_SHOP_PURCHASE, PointService.REF_POINT_SHOP_PURCHASE, purchase));
    }

    /** 그 사용 줄의 구매를 환불처럼 취소한다 — 사용 취소 줄 ID */
    long cancel(long useLedgerId) {
        long purchase = jdbc.queryForObject("SELECT ref_id FROM point_ledger WHERE id = ?", Long.class, useLedgerId);
        return inTx(() -> points.cancelUse(useLedgerId, PointReason.POINT_SHOP_REFUND, PointService.REF_POINT_SHOP_PURCHASE, purchase));
    }

    long expireLot(long lotId) {
        return inTx(() -> points.expireLot(lotId));
    }

    /**
     * 유상 충전을 흉내 낸다(3단계 충전 기능이 아직 없다) — 서비스와 같은 순서로 잔액 행을 잠그고 원장 줄(CHARGE, 유상 몫) + 유상 묶음 + 반영을 남긴다. 유상 묶음 ID
     */
    long chargePaid(long userId, long amount) {
        return inTx(() -> {
            Instant now = clock.instant();
            PointLedgerWriter.Balance balance = writer.lockBalance(userId, now);
            long line = ids.nextId();
            writer.insertEntry(line, userId, com.community.point.entity.PointKind.EARN, PointReason.CHARGE.name(), (int) amount, 0, amount,
                    balance.free() + balance.paid() + amount, null, null, null, null, now);
            long lot = ids.nextId();
            writer.insertLot(new PointLedgerWriter.NewLot(lot, userId, com.community.point.entity.PointLotKind.PAID, amount, null, PointReason.CHARGE.name(), null, null,
                    line, null, now));
            writer.insertEntryLot(line, lot, amount);
            writer.saveBalance(userId, balance.free(), balance.paid() + amount, balance.debt(), now);
            return lot;
        });
    }

    /** 정책 칸 하나를 잠시 바꾼다(시험 DB 의 정책 행은 하나 — 끝나면 되돌린다) */
    void withSetting(String column, int value, Runnable work) {
        Integer before = jdbc.queryForObject("SELECT " + column + " FROM point_settings WHERE id = 1", Integer.class);
        jdbc.update("UPDATE point_settings SET " + column + " = ? WHERE id = 1", value);
        policy.invalidate();
        try {
            work.run();
        } finally {
            jdbc.update("UPDATE point_settings SET " + column + " = ? WHERE id = 1", before);
            policy.invalidate();
        }
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    /** {free, paid, debt, balance} */
    long[] balance(long userId) {
        List<long[]> rows = jdbc.query("SELECT free_balance, paid_balance, revoke_debt, balance FROM point_balances WHERE user_id = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, userId);
        return rows.isEmpty() ? new long[] {0, 0, 0, 0} : rows.get(0);
    }

    long free(long userId) {
        return balance(userId)[0];
    }

    long paid(long userId) {
        return balance(userId)[1];
    }

    long debt(long userId) {
        return balance(userId)[2];
    }

    long remaining(long lotId) {
        return jdbc.queryForObject("SELECT remaining_amount FROM point_lots WHERE id = ?", Long.class, lotId);
    }

    /** 그 원장 줄이 만든 묶음 */
    long lotOf(long ledgerId) {
        return jdbc.queryForObject("SELECT id FROM point_lots WHERE source_ledger_id = ?", Long.class, ledgerId);
    }

    /** 그 회원의 그 사유·대상 줄 ID */
    long entryId(long userId, String reason, String refType, long refId) {
        return jdbc.queryForObject("SELECT id FROM point_ledger WHERE user_id = ? AND reason = ? AND ref_type = ? AND ref_id = ?", Long.class, userId, reason, refType, refId);
    }

    /** 그 회원의 원장 줄(먼저 생긴 순) — 칸: id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id */
    List<Map<String, Object>> entries(long userId) {
        return jdbc.queryForList("SELECT id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id FROM point_ledger WHERE user_id = ? ORDER BY id",
                userId);
    }

    /** 원장 한 줄의 묶음 반영 {묶음 ID → 양} */
    Map<Long, Long> entryLots(long ledgerId) {
        Map<Long, Long> parts = new java.util.LinkedHashMap<>();
        jdbc.query("SELECT lot_id, amount FROM point_ledger_lots WHERE ledger_id = ? ORDER BY lot_id", rs -> {
            parts.put(rs.getLong(1), rs.getLong(2));
        }, ledgerId);
        return parts;
    }

    int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    // ── 불변식(commerce.md 5-5) ─────────────────────────────────────────────

    /**
     * 회원마다 종류별로: 원장 몫 합 = 잔액 = 남은 묶음 합(합계 칸도 같다), 원장 줄마다 묶음 반영 합 = delta(무상 묶음 반영 합 = free_delta, 유상 = paid_delta),
     * 묶음마다 남은 양 = 반영 합(전환 묶음은 + 원래 양), 마지막 줄의 balance_after = 잔액, 미회수 ≥ 0.
     */
    void assertInvariants(long... userIds) {
        for (long userId : userIds) {
            long[] b = balance(userId);
            Map<String, Object> ledger = jdbc.queryForMap("SELECT coalesce(sum(free_delta), 0) AS f, coalesce(sum(paid_delta), 0) AS p, coalesce(sum(delta), 0) AS d "
                    + "FROM point_ledger WHERE user_id = ?", userId);
            Map<String, Object> lots = jdbc.queryForMap("SELECT coalesce(sum(remaining_amount) FILTER (WHERE kind = 'FREE'), 0) AS f, "
                    + "coalesce(sum(remaining_amount) FILTER (WHERE kind = 'PAID'), 0) AS p FROM point_lots WHERE user_id = ?", userId);
            String who = "회원 " + userId;
            assertThat(((Number) ledger.get("f")).longValue()).as(who + " 원장 무상 합 = 무상 잔액").isEqualTo(b[0]);
            assertThat(((Number) ledger.get("p")).longValue()).as(who + " 원장 유상 합 = 유상 잔액").isEqualTo(b[1]);
            assertThat(((Number) lots.get("f")).longValue()).as(who + " 무상 묶음 합 = 무상 잔액").isEqualTo(b[0]);
            assertThat(((Number) lots.get("p")).longValue()).as(who + " 유상 묶음 합 = 유상 잔액").isEqualTo(b[1]);
            assertThat(((Number) ledger.get("d")).longValue()).as(who + " 원장 합 = 합계").isEqualTo(b[3]);
            assertThat(b[0] + b[1]).as(who + " 합계 칸").isEqualTo(b[3]);
            assertThat(b[2]).as(who + " 미회수 ≥ 0").isGreaterThanOrEqualTo(0);

            List<Long> badLines = jdbc.queryForList("SELECT l.id FROM point_ledger l LEFT JOIN ("
                    + "  SELECT x.ledger_id, sum(x.amount) AS s, coalesce(sum(x.amount) FILTER (WHERE t.kind = 'FREE'), 0) AS f, "
                    + "         coalesce(sum(x.amount) FILTER (WHERE t.kind = 'PAID'), 0) AS p "
                    + "  FROM point_ledger_lots x JOIN point_lots t ON t.id = x.lot_id GROUP BY x.ledger_id) r ON r.ledger_id = l.id "
                    + "WHERE l.user_id = ? AND l.created_at >= (SELECT lot_tracking_started_at FROM point_settings WHERE id = 1) "
                    + "AND (coalesce(r.s, 0) <> l.delta OR coalesce(r.f, 0) <> l.free_delta OR coalesce(r.p, 0) <> l.paid_delta)", Long.class, userId);
            assertThat(badLines).as(who + " 묶음 반영 합 = delta 가 아닌 줄").isEmpty();

            List<Long> badLots = jdbc.queryForList("SELECT t.id FROM point_lots t LEFT JOIN (SELECT lot_id, sum(amount) AS s FROM point_ledger_lots GROUP BY lot_id) r "
                    + "ON r.lot_id = t.id WHERE t.user_id = ? AND t.remaining_amount <> coalesce(r.s, 0) + CASE WHEN t.source_reason = 'MIGRATION' THEN t.original_amount ELSE 0 END",
                    Long.class, userId);
            assertThat(badLots).as(who + " 남은 양 = 반영 합이 아닌 묶음").isEmpty();

            List<Long> badAfter = jdbc.queryForList("SELECT id FROM (SELECT id, balance_after, sum(delta) OVER (ORDER BY id) AS running FROM point_ledger WHERE user_id = ?) x "
                    + "WHERE balance_after <> running", Long.class, userId);
            assertThat(badAfter).as(who + " balance_after = 그때까지의 원장 합이 아닌 줄").isEmpty();

            List<Long> last = jdbc.queryForList("SELECT balance_after FROM point_ledger WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, userId);
            if (!last.isEmpty()) {
                assertThat(last.get(0)).as(who + " 마지막 줄의 잔액 = 잔액").isEqualTo(b[3]);
            }
        }
    }
}
