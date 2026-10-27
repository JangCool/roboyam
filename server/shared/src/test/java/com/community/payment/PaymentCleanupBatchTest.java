package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결제 웹훅 수신 기록 보관 정리 DB 배치 {@code payment-webhook-log-cleanup}(commerce.md 8-1 「90일」·15-5, 계약 「마이그레이션 계획」 {@code R__batch_31}) — 실제 PostgreSQL 에서
 * 프로시저를 {@code CALL} 한다(시험 DB 에는 pg_cron 이 없다 — 등록 자체는 막지 못한다). 받은 지 90일이 지난 줄만 지우고 결제 이력(webhook-id 한 번 장치)은 그대로,
 * 보관 일수는 {@code batch_params}(하한 30일). 다른 시험의 행이 지워질 수 있지만 이 시험은 자기가 만든 행만 본다.
 */
class PaymentCleanupBatchTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(753);
    private static final String JOB = "payment-webhook-log-cleanup";

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        DataSource dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void restoreParams() {
        jdbc.update("UPDATE batch_params SET param_value = 90 WHERE job_key = ? AND param_key = 'retention_days'", JOB);
    }

    private long log(int daysAgo) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO payment_webhook_logs (id, webhook_id, event_type, payment_id, result, http_status, received_at) "
                + "VALUES (?, ?, 'Transaction.Paid', 'pay_cleanup', 'PROCESSED', 200, now() - make_interval(days => ?))", id, "wh-" + id, daysAgo);
        return id;
    }

    private boolean exists(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_webhook_logs WHERE id = ?", Integer.class, id) > 0;
    }

    private String lastResultKey() {
        return jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, JOB);
    }

    @Test
    void 카탈로그는_DB_배치이고_매일_04시_45분_기본_보관_90일() {
        assertThat(BatchCatalog.PAYMENT_WEBHOOK_LOG_CLEANUP.isJava()).isFalse();
        assertThat(BatchCatalog.PAYMENT_WEBHOOK_LOG_CLEANUP.procedureName()).isEqualTo("batch_payment_webhook_log_cleanup");
        Map<String, Object> defined = jdbc.queryForMap("SELECT procedure_name, kst_hour, kst_minute, cadence FROM batch_db_jobs WHERE job_key = ?", JOB);
        assertThat(defined.get("procedure_name")).isEqualTo("batch_payment_webhook_log_cleanup");
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(4);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(45);
        assertThat(defined.get("cadence")).isEqualTo("DAILY");
        assertThat(jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'retention_days'", Long.class, JOB)).isEqualTo(90L);
    }

    @Test
    void 받은_지_90일이_지난_웹훅_기록만_지우고_결제_이력은_남긴다() {
        long old = log(91);
        long recent = log(89);
        long eventId = IDS.nextId();
        jdbc.update("INSERT INTO payment_events (id, payment_order_id, kind, provider_event_id, created_at) VALUES (?, ?, 'WEBHOOK', ?, now() - interval '200 days')",
                eventId, IDS.nextId(), "wh-kept-" + eventId);

        jdbc.execute("CALL batch_payment_webhook_log_cleanup('MANUAL', NULL, NULL)");

        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_events WHERE id = ?", Integer.class, eventId)).as("같은 webhook-id 를 두 번 처리하지 않는 장치는 5년").isOne();
        assertThat(lastResultKey()).isIn("batch.result.paymentWebhookLogCleanup", "batch.result.paymentWebhookLogCleanup.none");
    }

    @Test
    void 보관_일수는_batch_params_로_바꾸고_하한보다_짧으면_기본값을_쓴다() {
        long day40 = log(40);
        long day20 = log(20);
        jdbc.update("UPDATE batch_params SET param_value = 30 WHERE job_key = ? AND param_key = 'retention_days'", JOB);
        jdbc.execute("CALL batch_payment_webhook_log_cleanup('MANUAL', NULL, NULL)");
        assertThat(exists(day40)).isFalse();
        assertThat(exists(day20)).isTrue();

        long day50 = log(50);
        jdbc.update("UPDATE batch_params SET param_value = 5 WHERE job_key = ? AND param_key = 'retention_days'", JOB); // 하한 30 미만 → 기본 90
        jdbc.execute("CALL batch_payment_webhook_log_cleanup('MANUAL', NULL, NULL)");
        assertThat(exists(day50)).isTrue();
        assertThat(exists(day20)).isTrue();
    }
}
