package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.testsupport.MutableTestClock;
import com.community.testsupport.PointInvariants;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커머스 1-1 관리자 포인트 API 시험의 바탕 — {@link Commerce04AdminTestBase}(실제 필터 체인·권한 인터셉터·PostgreSQL) + <b>옮길 수 있는 시계</b>(앱의 {@code PointService}·묶음 조회가
 * 같은 시계를 쓴다 — 지난 묶음·소멸 예정을 기다리지 않고 만든다). 시험 데이터는 실제 {@link PointService} 로 만든다(유상 충전만 3단계 기능이 없어 {@link #chargePaid} 가 서비스와 같은 순서로 흉내 낸다).
 */
@Import(PointAdminHttpTestBase.ClockConfig.class)
abstract class PointAdminHttpTestBase extends Commerce04AdminTestBase {

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableTestClock pointTestClock() {
            return new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
    }

    @Autowired
    protected MutableTestClock clock;
    @Autowired
    protected PointService points;
    @Autowired
    protected PointLedgerWriter writer;
    @Autowired
    protected PointPolicy policy;
    @Autowired
    private PlatformTransactionManager transactions;

    protected TransactionTemplate tx;
    /** 시험 지급을 한 운영자(감사·원장 처리자 칸) */
    protected long granter;

    @BeforeEach
    void pointBaseSetUp() {
        tx = new TransactionTemplate(transactions);
        clock.set(now());
        granter = operatorWith();
    }

    protected static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** {@code at} 에 운영 지급(+) — 그 지급이 만든 무상 묶음 ID */
    protected long grant(long member, int amount, Instant at) {
        clock.set(at);
        try {
            tx.executeWithoutResult(s -> points.adjust(member, amount, "시험 지급", granter));
            Long line = jdbc.queryForObject("SELECT max(id) FROM point_ledger WHERE user_id = ? AND reason = 'ADMIN_ADJUST' AND kind = 'EARN'", Long.class, member);
            return jdbc.queryForObject("SELECT id FROM point_lots WHERE source_ledger_id = ?", Long.class, line);
        } finally {
            clock.set(now());
        }
    }

    /** {@code at} 에 포인트 상점 구매처럼 쓴다 — 사용 줄 ID */
    protected long use(long member, long amount, Instant at) {
        clock.set(at);
        try {
            long purchase = ids.nextId();
            return tx.execute(s -> points.use(member, amount, PointReason.POINT_SHOP_PURCHASE, PointService.REF_POINT_SHOP_PURCHASE, purchase));
        } finally {
            clock.set(now());
        }
    }

    /** {@code at} 에 그 사용을 환불처럼 취소한다 — 사용 취소 줄 ID */
    protected long cancel(long useLine, Instant at) {
        clock.set(at);
        try {
            long purchase = jdbc.queryForObject("SELECT ref_id FROM point_ledger WHERE id = ?", Long.class, useLine);
            return tx.execute(s -> points.cancelUse(useLine, PointReason.POINT_SHOP_REFUND, PointService.REF_POINT_SHOP_PURCHASE, purchase));
        } finally {
            clock.set(now());
        }
    }

    /** {@code at} 에 묶음 하나를 소멸(소멸 배치가 하는 일) — 소멸한 양 */
    protected long expire(long lotId, Instant at) {
        clock.set(at);
        try {
            return points.expireLot(lotId);
        } finally {
            clock.set(now());
        }
    }

    /**
     * 유상 충전을 흉내 낸다(3단계 충전 기능이 아직 없다 — 관리자 API 로는 유상을 만들 길이 없다) — 서비스와 같은 순서로 잔액 행을 잠그고 원장 줄(CHARGE, 유상 몫) + 유상 묶음 + 반영. 유상 묶음 ID
     */
    protected long chargePaid(long member, long amount, Instant at) {
        return tx.execute(s -> {
            PointLedgerWriter.Balance balance = writer.lockBalance(member, at);
            long line = ids.nextId();
            writer.insertEntry(line, member, PointKind.EARN, PointReason.CHARGE.name(), (int) amount, 0, amount, balance.free() + balance.paid() + amount, null, null,
                    null, null, at);
            long lot = ids.nextId();
            writer.insertLot(new PointLedgerWriter.NewLot(lot, member, PointLotKind.PAID, amount, null, PointReason.CHARGE.name(), null, null, line, null, at));
            writer.insertEntryLot(line, lot, amount);
            writer.saveBalance(member, balance.free(), balance.paid() + amount, balance.debt(), at);
            return lot;
        });
    }

    /** {free, paid, debt, balance} — 잔액 행이 없으면 모두 0 */
    protected long[] balance(long member) {
        return jdbc.query("SELECT free_balance, paid_balance, revoke_debt, balance FROM point_balances WHERE user_id = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, member).stream().findFirst().orElse(new long[] {0, 0, 0, 0});
    }

    protected int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    /** 정책 칸 하나를 잠시 바꾼다(시험 DB 의 정책 행은 하나 — 끝나면 되돌린다) */
    protected void withSetting(String column, int value, ThrowingRunnable work) throws Exception {
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

    protected void assertInvariants(long... members) {
        assertThat(PointInvariants.violations(jdbc, members)).as("포인트 불변식(commerce.md 5-5)").isEmpty();
    }

    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }
}
