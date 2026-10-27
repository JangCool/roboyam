package com.community.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.payment.config.PaymentProperties;
import com.community.payment.service.PaymentCancelWorker;
import com.community.testsupport.CommerceTestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PG 취소 재시도 워커의 「언제 돌릴지」(commerce.md 15-4·15-5 「1분」, 계약 「패키지」 {@code PaymentCancelScheduler}) — 배치를 켠 관리자 서버에서만 돌고, 바퀴마다
 * {@code worker_heartbeats} 에 키 {@code payment-cancel} 을 남긴다(실패는 가린 한 줄). 한 바퀴의 일은 shared 시험({@code PaymentCancelWorkerTest})이 본다. 실제 PostgreSQL.
 */
class PaymentCancelSchedulerTest {

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

    /** 한 바퀴를 세기만 하는 워커(대기열은 읽지 않는다) */
    private static PaymentCancelWorker counting(AtomicInteger calls, int handled) {
        return new PaymentCancelWorker(null, null, PaymentProperties.DEFAULTS, duration -> {
        }, Clock.systemUTC()) {
            @Override
            public int runOnce() {
                calls.incrementAndGet();
                return handled;
            }
        };
    }

    @Test
    void 배치를_끈_서버는_돌지_않고_워커_기록도_없다() {
        jdbc.update("DELETE FROM worker_heartbeats WHERE worker_key = 'payment-cancel'");
        AtomicInteger calls = new AtomicInteger();
        PaymentCancelScheduler scheduler = new PaymentCancelScheduler(properties(false), counting(calls, 3),
                new WorkerHeartbeats(dataSource, 911, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.tick()).isEqualTo(-1);
        assertThat(calls.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM worker_heartbeats WHERE worker_key = 'payment-cancel'", Integer.class)).isZero();
    }

    @Test
    void 배치를_켠_서버는_한_바퀴_돌고_워커_기록에_건수를_남긴다() {
        AtomicInteger calls = new AtomicInteger();
        PaymentCancelScheduler scheduler = new PaymentCancelScheduler(properties(true), counting(calls, 3),
                new WorkerHeartbeats(dataSource, 912, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.tick()).isEqualTo(3);
        assertThat(calls.get()).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT node_id, last_success_at, last_count FROM worker_heartbeats WHERE worker_key = 'payment-cancel'");
        assertThat(row.get("node_id")).isEqualTo(912);
        assertThat(row.get("last_success_at")).isNotNull();
        assertThat(((Number) row.get("last_count")).longValue()).isEqualTo(3);
        assertThat(PaymentCancelWorker.WORKER_KEY).isEqualTo("payment-cancel");
    }

    @Test
    void 한_바퀴가_실패하면_가린_오류를_워커_기록에_남긴다() {
        PaymentCancelWorker broken = new PaymentCancelWorker(null, null, PaymentProperties.DEFAULTS, duration -> {
        }, Clock.systemUTC()) {
            @Override
            public int runOnce() {
                throw new IllegalStateException("db down for kim@example.com");
            }
        };
        PaymentCancelScheduler scheduler = new PaymentCancelScheduler(properties(true), broken, new WorkerHeartbeats(dataSource, 913, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.tick()).isZero();
        String error = jdbc.queryForObject("SELECT last_error FROM worker_heartbeats WHERE worker_key = 'payment-cancel'", String.class);
        assertThat(error).contains("IllegalStateException").doesNotContain("kim@example.com");
    }
}
