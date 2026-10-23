package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 0-5 의 DB 정리 배치(commerce.md 13-4 · 15-5, commerce-foundation-contract 「0-5」) — 감사 보관 분류별 기간, 멱등 키 24시간, 요청 속도 카운터 지난 창(매시),
 * 메일 대기열 보관(끝난 줄 365일). 실제 PostgreSQL 에서 프로시저를 {@code CALL} 한다(시험 DB 에는 pg_cron 이 없다). 다른 시험의 행이 지워질 수 있지만 이 시험은 자기가 만든 행만 본다.
 */
class CommerceCleanupBatchesTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(751);
    private static final AtomicLong USERS = new AtomicLong(7_500_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void restoreParams() {
        jdbc.update("UPDATE batch_params SET param_value = 365 WHERE job_key = 'audit-log-retention' AND param_key = 'retention_days'");
        jdbc.update("UPDATE batch_params SET param_value = 730 WHERE job_key = 'audit-log-retention' AND param_key = 'personal_data_retention_days'");
        jdbc.update("UPDATE batch_params SET param_value = 1825 WHERE job_key = 'audit-log-retention' AND param_key = 'money_retention_days'");
    }

    private long audit(String retentionClass, int daysAgo) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO audit_logs (id, occurred_at, actor_type, action, result, retention_class) VALUES (?, now() - make_interval(days => ?), 'SYSTEM', 'ZZ_TEST', 'SUCCESS', ?)",
                id, daysAgo, retentionClass);
        return id;
    }

    private boolean exists(String table, String idColumn, Object id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + idColumn + " = ?", Integer.class, id) > 0;
    }

    private String lastResultKey(String job) {
        return jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, job);
    }

    @Test
    void 감사_로그는_분류별_기간이_지난_줄만_지운다_일반_1년_개인정보_2년_돈_5년() {
        long general366 = audit("GENERAL", 366);
        long general364 = audit("GENERAL", 364);
        long pii731 = audit("PERSONAL_DATA", 731);
        long pii400 = audit("PERSONAL_DATA", 400);
        long money1826 = audit("MONEY", 1826);
        long money1000 = audit("MONEY", 1000);

        jdbc.execute("CALL batch_audit_log_retention('MANUAL', NULL, NULL)");

        assertThat(exists("audit_logs", "id", general366)).isFalse();
        assertThat(exists("audit_logs", "id", general364)).isTrue();
        assertThat(exists("audit_logs", "id", pii731)).isFalse();
        assertThat(exists("audit_logs", "id", pii400)).as("일반 기간(1년)은 지났지만 개인정보 열람은 2년").isTrue();
        assertThat(exists("audit_logs", "id", money1826)).isFalse();
        assertThat(exists("audit_logs", "id", money1000)).as("돈 관련은 5년").isTrue();
        assertThat(lastResultKey("audit-log-retention")).isEqualTo("batch.result.auditLogRetention.classes");
    }

    @Test
    void 하한보다_짧은_값은_DB_를_직접_고쳐도_무시하고_기본값을_쓴다() {
        jdbc.update("UPDATE batch_params SET param_value = 100 WHERE job_key = 'audit-log-retention' AND param_key = 'personal_data_retention_days'");
        jdbc.update("UPDATE batch_params SET param_value = 30 WHERE job_key = 'audit-log-retention' AND param_key = 'money_retention_days'");
        jdbc.update("UPDATE batch_params SET param_value = 30 WHERE job_key = 'audit-log-retention' AND param_key = 'retention_days'");
        long pii400 = audit("PERSONAL_DATA", 400);
        long money1000 = audit("MONEY", 1000);
        long general31 = audit("GENERAL", 31);

        jdbc.execute("CALL batch_audit_log_retention('MANUAL', NULL, NULL)");

        assertThat(exists("audit_logs", "id", pii400)).as("하한 365일보다 짧은 100일은 무시 — 기본 730일").isTrue();
        assertThat(exists("audit_logs", "id", money1000)).as("하한 1825일보다 짧은 30일은 무시").isTrue();
        assertThat(exists("audit_logs", "id", general31)).as("일반 운영은 하한이 없어(1일) 30일을 쓴다").isFalse();
    }

    @Test
    void 기존_감사_줄과_SQL_이_남기는_줄은_일반_운영이_기본값이다() {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO audit_logs (id, occurred_at, actor_type, action, result) VALUES (?, now(), 'SYSTEM', 'ZZ_TEST', 'SUCCESS')", id);
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE id = ?", String.class, id)).isEqualTo("GENERAL");
    }

    @Test
    void 멱등_키는_만든_지_24시간이_지나면_지운다() {
        long user = USERS.incrementAndGet();
        jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status, created_at) VALUES (?, 'old-key-0001', 'SHOP_PURCHASE', ?, 'IN_PROGRESS', now() - interval '25 hours')",
                user, "0".repeat(64));
        jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status, created_at) VALUES (?, 'new-key-0001', 'SHOP_PURCHASE', ?, 'IN_PROGRESS', now() - interval '23 hours')",
                user, "0".repeat(64));

        jdbc.execute("CALL batch_idempotency_request_cleanup('MANUAL', NULL, NULL)");

        assertThat(jdbc.queryForList("SELECT idem_key FROM idempotency_requests WHERE user_id = ?", String.class, user)).containsExactly("new-key-0001");
    }

    @Test
    void 요청_속도_카운터는_지난_창을_지우고_지금_창은_남긴다() {
        long user = USERS.incrementAndGet();
        jdbc.update("INSERT INTO request_rate_counters (user_id, action, window_start, count) VALUES (?, 'POINT_CHARGE', date_trunc('minute', now()) - interval '10 minutes', 3)", user);
        jdbc.update("INSERT INTO request_rate_counters (user_id, action, window_start, count) VALUES (?, 'POINT_CHARGE', date_trunc('minute', now()), 1)", user);

        jdbc.execute("CALL batch_request_rate_counter_cleanup('MANUAL', NULL, NULL)");

        assertThat(jdbc.queryForList("SELECT count FROM request_rate_counters WHERE user_id = ?", Integer.class, user)).containsExactly(1);
    }

    @Test
    void 메일_대기열은_끝난_지_365일이_지난_줄만_지우고_보낼_줄은_두지_않는다() {
        long user = USERS.incrementAndGet();
        long sentOld = outbox(user, "SENT", "now() - interval '366 days'", null);
        long sentRecent = outbox(user, "SENT", "now() - interval '10 days'", null);
        long deadOld = outbox(user, "DEAD", "now() - interval '400 days'", null);
        long canceledOld = outbox(user, "CANCELED", "now() - interval '400 days'", null);
        long pendingOld = outbox(user, "PENDING", null, "now() - interval '400 days'");

        jdbc.execute("CALL batch_mail_outbox_cleanup('MANUAL', NULL, NULL)");

        assertThat(exists("mail_outbox", "id", sentOld)).isFalse();
        assertThat(exists("mail_outbox", "id", deadOld)).isFalse();
        assertThat(exists("mail_outbox", "id", canceledOld)).isFalse();
        assertThat(exists("mail_outbox", "id", sentRecent)).isTrue();
        assertThat(exists("mail_outbox", "id", pendingOld)).as("보낼 차례인 줄은 오래돼도 지우지 않는다").isTrue();
        jdbc.update("DELETE FROM mail_outbox WHERE id = ?", pendingOld); // 다른 시험의 워커가 집지 않게
    }

    private long outbox(long user, String status, String finishedAt, String createdAt) {
        long id = IDS.nextId();
        boolean finished = finishedAt != null;
        jdbc.update("INSERT INTO mail_outbox (id, dedupe_key, user_id, mail_kind, variables, status, next_attempt_at, created_at, sent_at, finished_at) VALUES (?, ?, ?, 'MAIL_TEST', "
                        + (finished ? "NULL" : "'{}'::jsonb") + ", ?, now() + interval '100 years', " + (createdAt == null ? "now()" : createdAt) + ", "
                        + ("SENT".equals(status) ? finishedAt : "NULL") + ", " + (finished ? finishedAt : "NULL") + ")",
                id, "ZZ_CLEANUP:" + id, user, status);
        return id;
    }

    @Test
    void 새_정리_배치_셋은_카탈로그_정의_번들이_있고_카운터_정리는_매시다() {
        for (BatchCatalog job : List.of(BatchCatalog.IDEMPOTENCY_REQUEST_CLEANUP, BatchCatalog.REQUEST_RATE_COUNTER_CLEANUP, BatchCatalog.MAIL_OUTBOX_CLEANUP)) {
            String procedure = jdbc.queryForObject("SELECT procedure_name FROM batch_db_jobs WHERE job_key = ?", String.class, job.key());
            assertThat(procedure).isEqualTo("batch_" + job.key().replace('-', '_'));
            for (Locale locale : List.of(Locale.KOREAN, Locale.ENGLISH)) {
                ResourceBundle bundle = ResourceBundle.getBundle("messages/messages", locale);
                assertThat(bundle.containsKey(job.nameKey())).as(job.key() + " " + locale).isTrue();
                assertThat(bundle.containsKey(job.descriptionKey())).as(job.key() + " " + locale).isTrue();
            }
        }
        assertThat(jdbc.queryForObject("SELECT cadence FROM batch_db_jobs WHERE job_key = 'request-rate-counter-cleanup'", String.class)).isEqualTo("HOURLY");
        assertThat(jdbc.queryForObject("SELECT cadence FROM batch_db_jobs WHERE job_key = 'idempotency-request-cleanup'", String.class)).isEqualTo("DAILY");
        assertThat(jdbc.queryForObject("SELECT cadence FROM batch_db_jobs WHERE job_key = 'mail-outbox-cleanup'", String.class)).isEqualTo("DAILY");
        assertThat(jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = 'mail-outbox-cleanup' AND param_key = 'retention_days'", Long.class)).isEqualTo(365L);
        for (String key : List.of("batch.result.auditLogRetention.classes", "batch.result.idempotencyRequestCleanup", "batch.result.requestRateCounterCleanup",
                "batch.result.mailOutboxCleanup")) {
            for (Locale locale : List.of(Locale.KOREAN, Locale.ENGLISH)) {
                ResourceBundle bundle = ResourceBundle.getBundle("messages/messages", locale);
                assertThat(bundle.containsKey(key)).as(key).isTrue();
                assertThat(bundle.containsKey(key + ".none")).as(key + ".none").isTrue();
            }
        }
    }
}
