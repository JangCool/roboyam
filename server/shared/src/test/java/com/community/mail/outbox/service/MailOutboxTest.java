package com.community.mail.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.mail.outbox.dto.MailOutboxPolicy;
import com.community.mail.outbox.dto.OutboxDelivery;
import com.community.mail.outbox.dto.OutboxMail;
import com.community.mail.outbox.repository.master.MailOutboxStore;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커머스 메일 대기열(docs/domain/commerce.md 14절 · 15-4 · 18절 P27, commerce-foundation-contract 결정 10·17 · 「0-5」) — 실제 PostgreSQL.
 * 넣기의 {@code MANDATORY} 는 스프링 트랜잭션 프록시로 확인하려고 작은 스프링 문맥을 띄운다. 워커는 가짜 발송기로 결과(보냄·재시도·받을 수 없음·오류)를 정한다.
 * DB 를 다른 시험과 같이 쓰므로 워커가 집는 줄은 이 시험이 만든 것만이 되도록, 시험 시계를 실제보다 <b>과거</b>(시험마다 더 이르게)로 두고 줄의 다음 시도 시각도 그 시계로 쓴다 —
 * 다른 시험의 줄은 이 시계로는 아직 차례가 아니다.
 */
class MailOutboxTest {

    private static final AtomicLong USERS = new AtomicLong(7_300_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);
    private static final AtomicLong EPOCH = new AtomicLong(0);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext spring;
    private static MailOutbox outbox;
    private static TransactionTemplate business;

    private MutableTestClock clock;
    private MailOutboxStore store;
    private long userId;

    @Configuration
    @EnableTransactionManagement
    static class Wiring {

        @Bean
        DataSource dataSource() {
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        MailOutboxStore mailOutboxStore(DataSource source) {
            return new MailOutboxStore(source);
        }

        @Bean
        MailOutbox mailOutbox(MailOutboxStore store) {
            return new MailOutbox(store, new SnowflakeIdGenerator(731));
        }
    }

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS zz_outbox_orders (id bigint PRIMARY KEY, user_id bigint NOT NULL)");
        spring = new AnnotationConfigApplicationContext(Wiring.class);
        outbox = spring.getBean(MailOutbox.class);
        business = new TransactionTemplate(spring.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void close() {
        spring.close();
    }

    @BeforeEach
    void setUp() {
        // 시험마다 앞 시험보다 30일씩 <b>이른</b> 과거 시각 — 앞 시험이 남긴 줄·다른 시험 클래스의 줄(지금 시각)은 이 시계로는 아직 보낼 차례가 아니다
        clock = new MutableTestClock(Instant.parse("1999-01-01T00:00:00Z").minus(Duration.ofDays(30 * EPOCH.incrementAndGet())));
        store = new MailOutboxStore(dataSource);
        userId = USERS.incrementAndGet();
    }

    private static String dedupe(String what) {
        return "ZZ_TEST:" + what + ":" + System.nanoTime();
    }

    /** 이 시험의 시계로 넣는다(업무 트랜잭션 안) */
    private long enqueueAt(String dedupeKey, Map<String, Object> variables) {
        long id = new SnowflakeIdGenerator(732).nextId();
        business.executeWithoutResult(status -> assertThat(store.insert(id, dedupeKey, userId, RequiredMail.MAIL_TEST.code(), json(variables), clock.instant())).isTrue());
        return id;
    }

    private static String json(Map<String, Object> variables) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(variables);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM mail_outbox WHERE id = ?", id);
    }

    private MailOutboxWorker worker(OutboxMailSender sender, int nodeId) {
        return new MailOutboxWorker(store, sender, MailOutboxPolicy.DEFAULTS, nodeId, clock);
    }

    /** 결과를 정해 주는 가짜 발송기 — 부른 횟수를 줄마다 센다 */
    static final class FakeSender implements OutboxMailSender {
        final Map<Long, AtomicInteger> callsByUser = new ConcurrentHashMap<>();
        volatile BiFunction<Long, Map<String, Object>, OutboxDelivery> behavior = (user, vars) -> OutboxDelivery.sent("ab***@example.com");
        final List<Map<String, Object>> seenVariables = new ArrayList<>();

        @Override
        public synchronized OutboxDelivery send(long userId, String kind, Map<String, Object> variables) {
            callsByUser.computeIfAbsent(userId, k -> new AtomicInteger()).incrementAndGet();
            seenVariables.add(variables);
            return behavior.apply(userId, variables);
        }
    }

    @Nested
    class 넣기 {

        @Test
        void 업무_트랜잭션_밖에서_넣으면_던진다_MANDATORY() {
            assertThatThrownBy(() -> outbox.enqueue(new OutboxMail(dedupe("no-tx"), userId, RequiredMail.MAIL_TEST, Map.of())))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        void 같은_열쇠로_두_번_넣으면_한_줄만_남는다() {
            String key = dedupe("dup");
            Boolean first = business.execute(status -> outbox.enqueue(new OutboxMail(key, userId, RequiredMail.MAIL_TEST, Map.of("n", 1))));
            Boolean second = business.execute(status -> outbox.enqueue(new OutboxMail(key, userId, RequiredMail.MAIL_TEST, Map.of("n", 2))));
            assertThat(first).isTrue();
            assertThat(second).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox WHERE dedupe_key = ?", Integer.class, key)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO mail_outbox (id, dedupe_key, user_id, mail_kind) VALUES (?, ?, ?, 'MAIL_TEST')",
                    new SnowflakeIdGenerator(733).nextId(), key, userId)).as("DB 유일 제약").isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 업무가_되돌려지면_대기열에도_남지_않는다() {
            String key = dedupe("rollback");
            assertThatThrownBy(() -> business.executeWithoutResult(status -> {
                outbox.enqueue(new OutboxMail(key, userId, RequiredMail.MAIL_TEST, Map.of()));
                throw new IllegalStateException("업무 실패");
            })).hasMessage("업무 실패");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox WHERE dedupe_key = ?", Integer.class, key)).isZero();
        }

        @Test
        void 받는_주소_원문은_대기열에_없다() {
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'mail_outbox'", String.class))
                    .doesNotContain("to_address", "email", "recipient", "to_email")
                    .contains("user_id", "to_masked", "dedupe_key", "variables");
        }

        @Test
        void 중복_방지_열쇠는_1자에서_200자() {
            assertThatThrownBy(() -> new OutboxMail("", userId, RequiredMail.MAIL_TEST, Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxMail("x".repeat(201), userId, RequiredMail.MAIL_TEST, Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThat(new OutboxMail("x".repeat(200), userId, RequiredMail.MAIL_TEST, null).variables()).isEmpty();
        }
    }

    @Nested
    class 발송 {

        @Test
        void 보내면_SENT_본문을_지우고_가린_주소만_남긴다() {
            long id = enqueueAt(dedupe("sent"), Map.of("orderNo", "A-1"));
            FakeSender sender = new FakeSender();
            assertThat(worker(sender, 11).runOnce()).isEqualTo(1);

            Map<String, Object> row = row(id);
            assertThat(row.get("status")).isEqualTo("SENT");
            assertThat(row.get("variables")).isNull();
            assertThat(row.get("to_masked")).isEqualTo("ab***@example.com");
            assertThat(row.get("sent_at")).isNotNull();
            assertThat(row.get("finished_at")).isNotNull();
            assertThat(row.get("attempts")).isEqualTo(1);
            assertThat(sender.seenVariables.get(0)).containsEntry("orderNo", "A-1");
            assertThat(worker(sender, 11).runOnce()).as("다시 보내지 않는다").isZero();
        }

        @Test
        void 메일_서버_장애_중에도_업무는_확정되고_복구되면_보낸다_P27() {
            long orderId = new SnowflakeIdGenerator(734).nextId();
            String key = dedupe("p27");
            business.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO zz_outbox_orders (id, user_id) VALUES (?, ?)", orderId, userId);
                long id = new SnowflakeIdGenerator(735).nextId();
                store.insert(id, key, userId, RequiredMail.MAIL_TEST.code(), "{\"orderNo\":\"P27\"}", clock.instant());
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM zz_outbox_orders WHERE id = ?", Integer.class, orderId)).as("업무는 확정").isEqualTo(1);
            long id = jdbc.queryForObject("SELECT id FROM mail_outbox WHERE dedupe_key = ?", Long.class, key);

            FakeSender sender = new FakeSender();
            sender.behavior = (user, vars) -> OutboxDelivery.retry("MAIL_NOT_CONFIGURED");
            worker(sender, 12).runOnce();
            Map<String, Object> waiting = row(id);
            assertThat(waiting.get("status")).isEqualTo("PENDING");
            assertThat(waiting.get("attempts")).isEqualTo(1);
            assertThat(waiting.get("last_error")).isEqualTo("MAIL_NOT_CONFIGURED");
            assertThat(waiting.get("variables")).as("아직 보내지 못했으니 본문은 남는다").isNotNull();

            worker(sender, 12).runOnce();
            assertThat(row(id).get("attempts")).as("다음 시도 시각 전에는 다시 집지 않는다").isEqualTo(1);

            sender.behavior = (user, vars) -> OutboxDelivery.sent("ab***@example.com");
            clock.advance(Duration.ofMinutes(1));
            worker(sender, 12).runOnce();
            assertThat(row(id).get("status")).isEqualTo("SENT");
            assertThat(row(id).get("attempts")).isEqualTo(2);
        }

        @Test
        void 보내다_던지면_재시도하고_간격은_1분부터_두_배_최대_2시간() {
            long id = enqueueAt(dedupe("backoff"), Map.of());
            FakeSender sender = new FakeSender();
            sender.behavior = (user, vars) -> {
                throw new IllegalStateException("smtp down for kim123@gmail.com");
            };
            MailOutboxWorker worker = worker(sender, 13);
            Duration[] expected = {Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4), Duration.ofMinutes(8)};
            for (Duration gap : expected) {
                Instant before = clock.instant();
                worker.runOnce();
                Instant next = ((java.sql.Timestamp) row(id).get("next_attempt_at")).toInstant();
                assertThat(Duration.between(before, next)).isEqualTo(gap);
                clock.set(next);
            }
            assertThat((String) row(id).get("last_error")).as("오류 문구의 이메일은 가린다").doesNotContain("kim123@gmail.com");
            assertThat(MailOutboxPolicy.DEFAULTS.backoffAfter(8)).isEqualTo(Duration.ofHours(2));
            assertThat(MailOutboxPolicy.DEFAULTS.backoffAfter(30)).isEqualTo(Duration.ofHours(2));
        }

        @Test
        void 시도_한도까지_못_보내면_DEAD_본문을_지운다() {
            long id = enqueueAt(dedupe("dead"), Map.of("x", "y"));
            FakeSender sender = new FakeSender();
            sender.behavior = (user, vars) -> OutboxDelivery.retry("MAIL_NOT_CONFIGURED");
            MailOutboxPolicy policy = new MailOutboxPolicy(3, 20, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(2));
            MailOutboxWorker worker = new MailOutboxWorker(store, sender, policy, 14, clock);
            for (int i = 0; i < 3; i++) {
                worker.runOnce();
                clock.advance(Duration.ofHours(3));
            }
            Map<String, Object> row = row(id);
            assertThat(row.get("status")).isEqualTo("DEAD");
            assertThat(row.get("attempts")).isEqualTo(3);
            assertThat(row.get("variables")).isNull();
            assertThat(row.get("finished_at")).isNotNull();
            worker.runOnce();
            assertThat(sender.callsByUser.get(userId)).as("DEAD 는 다시 집지 않는다").hasValue(3);
            assertThat(MailOutboxPolicy.DEFAULTS.maxAttempts()).isEqualTo(12);
        }

        @Test
        void 받을_수_없는_회원이면_CANCELED_본문을_지운다() {
            long id = enqueueAt(dedupe("cancel"), Map.of("x", 1));
            FakeSender sender = new FakeSender();
            sender.behavior = (user, vars) -> OutboxDelivery.canceled("EMAIL_NOT_VERIFIED");
            worker(sender, 15).runOnce();
            Map<String, Object> row = row(id);
            assertThat(row.get("status")).isEqualTo("CANCELED");
            assertThat(row.get("last_error")).isEqualTo("EMAIL_NOT_VERIFIED");
            assertThat(row.get("variables")).isNull();
        }

        @Test
        void 보내던_워커가_죽어_SENDING_에_머문_줄은_10분_뒤_다시_보낼_차례가_된다() {
            long id = enqueueAt(dedupe("stale"), Map.of());
            assertThat(store.claim(20, 16, clock.instant())).extracting(MailOutboxStore.Claimed::id).contains(id);
            assertThat(row(id).get("status")).isEqualTo("SENDING");

            FakeSender sender = new FakeSender();
            clock.advance(Duration.ofMinutes(9));
            worker(sender, 17).runOnce();
            assertThat(row(id).get("status")).as("10분 전에는 그대로").isEqualTo("SENDING");

            clock.advance(Duration.ofMinutes(2));
            worker(sender, 17).runOnce();
            assertThat(row(id).get("status")).isEqualTo("SENT");
            assertThat(row(id).get("attempts")).isEqualTo(2);
        }

        @Test
        void 끝난_줄에_본문이_남는_것을_DB_가_막는다() {
            long id = enqueueAt(dedupe("check"), Map.of("x", 1));
            assertThatThrownBy(() -> jdbc.update("UPDATE mail_outbox SET status = 'SENT', sent_at = now(), finished_at = now() WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE mail_outbox SET status = 'DEAD', finished_at = NULL, variables = NULL WHERE id = ?", id))
                    .as("끝난 줄에는 끝난 시각").isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 서버_여럿의_워커가_동시에_돌아도_같은_줄을_두_번_보내지_않는다() throws Exception {
            int mails = 30;
            List<Long> users = new ArrayList<>();
            for (int i = 0; i < mails; i++) {
                long user = USERS.incrementAndGet();
                users.add(user);
                long id = new SnowflakeIdGenerator(736).nextId();
                store.insert(id, dedupe("concurrent-" + i), user, RequiredMail.MAIL_TEST.code(), "{}", clock.instant());
            }
            FakeSender sender = new FakeSender();
            sender.behavior = (user, vars) -> {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return OutboxDelivery.sent("ab***@example.com");
            };
            int workers = 6;
            ExecutorService pool = Executors.newFixedThreadPool(workers);
            CountDownLatch go = new CountDownLatch(1);
            try {
                List<Future<Integer>> runs = new ArrayList<>();
                for (int w = 0; w < workers; w++) {
                    MailOutboxWorker worker = new MailOutboxWorker(new MailOutboxStore(dataSource), sender,
                            new MailOutboxPolicy(12, 4, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(2)), 100 + w, clock);
                    runs.add(pool.submit(() -> {
                        go.await();
                        int total = 0;
                        for (int round = 0; round < 10; round++) {
                            total += worker.runOnce();
                        }
                        return total;
                    }));
                }
                go.countDown();
                int handled = 0;
                for (Future<Integer> run : runs) {
                    handled += run.get(60, TimeUnit.SECONDS);
                }
                assertThat(handled).isEqualTo(mails);
                for (long user : users) {
                    assertThat(sender.callsByUser.get(user)).as("회원 %d 의 메일", user).hasValue(1);
                }
                assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox WHERE user_id = ANY (?) AND status = 'SENT'", Integer.class,
                        (Object) users.toArray(Long[]::new))).isEqualTo(mails);
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
