package com.community.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.mail.outbox.dto.MailOutboxPolicy;
import com.community.mail.outbox.dto.OutboxDelivery;
import com.community.mail.outbox.repository.master.MailOutboxStore;
import com.community.mail.outbox.service.MailOutboxWorker;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 메일 대기열 워커의 「언제 돌릴지」(commerce.md 15-4, commerce-foundation-contract 결정 17) — 배치를 켠 관리자 서버에서만 돌고, 바퀴마다 {@code worker_heartbeats} 에
 * 키 {@code mail-outbox} 를 남긴다. 실제 PostgreSQL. 워커 시계는 과거로 두어 다른 시험의 대기열 줄을 집지 않는다.
 */
class MailOutboxSchedulerTest {

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

    private static MailOutboxWorker worker(AtomicInteger calls) {
        OutboxMailSenderStub sender = new OutboxMailSenderStub(calls);
        return new MailOutboxWorker(new MailOutboxStore(dataSource), sender, MailOutboxPolicy.DEFAULTS, 901,
                new MutableTestClock(Instant.parse("1950-01-01T00:00:00Z")));
    }

    record OutboxMailSenderStub(AtomicInteger calls) implements com.community.mail.outbox.service.OutboxMailSender {
        @Override
        public OutboxDelivery send(long userId, String kind, Map<String, Object> variables) {
            calls.incrementAndGet();
            return OutboxDelivery.sent("ab***@example.com");
        }
    }

    @Test
    void 배치를_끈_서버는_돌지_않고_워커_기록도_없다() {
        jdbc.update("DELETE FROM worker_heartbeats WHERE worker_key = 'mail-outbox'");
        MailOutboxScheduler scheduler = new MailOutboxScheduler(properties(false), worker(new AtomicInteger()),
                new WorkerHeartbeats(dataSource, 901, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.tick()).isEqualTo(-1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM worker_heartbeats WHERE worker_key = 'mail-outbox'", Integer.class)).isZero();
    }

    @Test
    void 배치를_켠_서버는_한_바퀴_돌고_워커_기록을_남긴다() {
        MailOutboxScheduler scheduler = new MailOutboxScheduler(properties(true), worker(new AtomicInteger()),
                new WorkerHeartbeats(dataSource, 902, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.tick()).isZero();
        Map<String, Object> row = jdbc.queryForMap("SELECT node_id, last_success_at, last_count FROM worker_heartbeats WHERE worker_key = 'mail-outbox'");
        assertThat(row.get("node_id")).isEqualTo(902);
        assertThat(row.get("last_success_at")).isNotNull();
        assertThat(((Number) row.get("last_count")).longValue()).isZero();
    }

    @Test
    void 워커_한_바퀴가_실패하면_가린_오류를_워커_기록에_남긴다() {
        MailOutboxWorker broken = new MailOutboxWorker(new MailOutboxStore(dataSource), (userId, kind, variables) -> OutboxDelivery.sent(null), MailOutboxPolicy.DEFAULTS,
                903, new MutableTestClock(Instant.parse("1950-01-01T00:00:00Z"))) {
            @Override
            public int runOnce() {
                throw new IllegalStateException("db down for kim@example.com");
            }
        };
        MailOutboxScheduler scheduler = new MailOutboxScheduler(properties(true), broken, new WorkerHeartbeats(dataSource, 903, Duration.ZERO, Clock.systemUTC()));
        assertThat(scheduler.tick()).isZero();
        String error = jdbc.queryForObject("SELECT last_error FROM worker_heartbeats WHERE worker_key = 'mail-outbox'", String.class);
        assertThat(error).contains("IllegalStateException").doesNotContain("kim@example.com");
    }
}
