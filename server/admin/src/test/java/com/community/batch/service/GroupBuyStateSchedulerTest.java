package com.community.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.groupbuy.service.GroupBuyRefundWorker;
import com.community.groupbuy.service.GroupBuyFulfillmentWorker;
import com.community.groupbuy.service.GroupBuyStateWorker;
import com.community.testsupport.CommerceTestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 공구 워커 둘의 「언제 돌릴지」(commerce.md 15-4, 계약 결정 61 — 상태 {@code groupbuy-state}·환불 {@code groupbuy-refund} 따로) — 배치를 켠 관리자 서버에서만 돌고 바퀴마다
 * {@code worker_heartbeats} 를 남긴다(실패는 가린 한 줄). 한 바퀴의 일은 shared 시험({@code GroupBuyStateWorkerTest})이 본다. 실제 PostgreSQL.
 */
class GroupBuyStateSchedulerTest {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated("admin");
        jdbc = new JdbcTemplate(dataSource);
    }

    private static BatchProperties properties(boolean enabled) {
        return new BatchProperties(enabled, "Asia/Seoul", 30, 60, 4, 2000, 120, 30);
    }

    private static GroupBuyStateWorker state(AtomicInteger calls, int handled) {
        return new GroupBuyStateWorker(null, null, null, null, null, null, null, null, null,
                new StaticListableBeanFactory(Map.of("clock", Clock.systemUTC())).getBeanProvider(Clock.class)) {
            @Override
            public int runOnce() {
                calls.incrementAndGet();
                if (handled < 0) {
                    throw new IllegalStateException("db down for kim@example.com");
                }
                return handled;
            }
        };
    }

    private static final AtomicInteger FULFILLMENT_CALLS = new AtomicInteger();

    private static GroupBuyFulfillmentWorker fulfillment() {
        return new GroupBuyFulfillmentWorker(null, null, null) {
            @Override
            public int runOnce() {
                FULFILLMENT_CALLS.incrementAndGet();
                return 3;
            }
        };
    }

    private static GroupBuyRefundWorker refund(AtomicInteger calls, int handled) {
        return new GroupBuyRefundWorker(null, null, null, null) {
            @Override
            public int runOnce() {
                calls.incrementAndGet();
                return handled;
            }
        };
    }

    @Test
    void 배치를_끈_서버는_두_워커_모두_돌지_않는다() {
        jdbc.update("DELETE FROM worker_heartbeats WHERE worker_key IN ('groupbuy-state', 'groupbuy-refund')");
        AtomicInteger calls = new AtomicInteger();
        GroupBuyStateScheduler scheduler = new GroupBuyStateScheduler(properties(false), state(calls, 1), refund(calls, 1), fulfillment(),
                new WorkerHeartbeats(dataSource, 921, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.stateTick()).isEqualTo(-1);
        assertThat(scheduler.refundTick()).isEqualTo(-1);
        assertThat(scheduler.fulfillmentTick()).isEqualTo(-1);
        assertThat(calls.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM worker_heartbeats WHERE worker_key IN ('groupbuy-state', 'groupbuy-refund')", Integer.class)).isZero();
    }

    @Test
    void 배치를_켠_서버는_따로_돌고_각자_워커_기록을_남긴다() {
        AtomicInteger stateCalls = new AtomicInteger();
        AtomicInteger refundCalls = new AtomicInteger();
        GroupBuyStateScheduler scheduler = new GroupBuyStateScheduler(properties(true), state(stateCalls, 4), refund(refundCalls, 7), fulfillment(),
                new WorkerHeartbeats(dataSource, 922, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.stateTick()).isEqualTo(4);
        assertThat(scheduler.refundTick()).isEqualTo(7);
        assertThat(scheduler.fulfillmentTick()).as("발송·요청 워커(2b-3)").isEqualTo(3);
        assertThat(stateCalls.get()).isEqualTo(1);
        assertThat(refundCalls.get()).isEqualTo(1);
        assertThat(((Number) jdbc.queryForMap("SELECT last_count FROM worker_heartbeats WHERE worker_key = 'groupbuy-state'").get("last_count")).longValue()).isEqualTo(4);
        assertThat(((Number) jdbc.queryForMap("SELECT last_count FROM worker_heartbeats WHERE worker_key = 'groupbuy-refund'").get("last_count")).longValue()).isEqualTo(7);
    }

    @Test
    void 상태_워커가_실패하면_가린_오류를_남기고_환불_워커는_그대로_돈다() {
        AtomicInteger calls = new AtomicInteger();
        GroupBuyStateScheduler scheduler = new GroupBuyStateScheduler(properties(true), state(calls, -1), refund(calls, 2), fulfillment(),
                new WorkerHeartbeats(dataSource, 923, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.stateTick()).isZero();
        assertThat(scheduler.refundTick()).isEqualTo(2);
        String error = jdbc.queryForObject("SELECT last_error FROM worker_heartbeats WHERE worker_key = 'groupbuy-state'", String.class);
        assertThat(error).contains("IllegalStateException").doesNotContain("kim@example.com");
    }
}
