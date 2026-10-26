package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.batch.entity.BatchSource;
import com.community.testsupport.CommerceTestDatabase;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 정리 DB 배치 {@code member-privacy-retention}(계약 「0-4」, commerce.md 17-3) — 기간이 끝난 재가입 제한 해시 · 파기 예정일이 지난 연락처 · 30일(설정값) 지난 인증 시도를 지우고
 * 나머지는 남긴다. pg_cron 이 없는 시험 DB 라 프로시저를 {@code CALL} 로 바로 부른다. 다른 시험의 행이 있으므로 자기 행만 본다.
 */
class MemberPrivacyRetentionBatchTest {

    private static final AtomicLong IDS = new AtomicLong(8_500_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static long id() {
        return IDS.incrementAndGet();
    }

    private static long withdrawal(String expiresIn) {
        long id = id();
        jdbc.update("INSERT INTO identity_withdrawals (id, kind, hash, withdrawn_at, expires_at) VALUES (?, 'EMAIL', ?, now() - interval '40 days', now() + ?::interval)",
                id, String.format("%064x", id), expiresIn);
        return id;
    }

    private static long contact(String purgeIn) {
        long user = id();
        jdbc.update("INSERT INTO withdrawn_member_contacts (user_id, email_cipher, withdrawn_at, purge_after) VALUES (?, 'd1:AAAA', now() - interval '6 years', now() + ?::interval)",
                user, purgeIn);
        return user;
    }

    private static String attempt(String age, String status) {
        String id = "iv-batch-" + id();
        jdbc.update("INSERT INTO identity_verification_attempts (id, user_id, provider, status, failure_code, created_at, completed_at) VALUES (?, ?, 'FAKE', ?, ?, "
                + "now() - ?::interval, CASE WHEN ? = 'PENDING' THEN NULL ELSE now() END)", id, id(), status, status.equals("FAILED") ? "IDENTITY_PROVIDER_FAILED" : null, age,
                status);
        return id;
    }

    private static long call() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true); // 프로시저가 COMMIT 한다
            try (CallableStatement call = connection.prepareCall("CALL batch_member_privacy_retention('MANUAL', NULL, NULL)")) {
                call.execute();
            }
        }
        return jdbc.queryForObject("SELECT id FROM batch_runs WHERE job_key = 'member-privacy-retention' ORDER BY started_at DESC, id DESC LIMIT 1", Long.class);
    }

    private static boolean exists(String sql, Object id) {
        return !jdbc.queryForList(sql, Integer.class, id).isEmpty();
    }

    @Test
    void 기간이_끝난_해시_파기일이_지난_연락처_30일_지난_시도를_지우고_나머지는_남긴다() throws Exception {
        long expiredHash = withdrawal("-1 second");
        long liveHash = withdrawal("1 day");
        long purged = contact("-1 minute");
        long kept = contact("1 day");
        String oldVerified = attempt("31 days", "VERIFIED");
        String oldPending = attempt("31 days", "PENDING");
        String oldFailed = attempt("31 days", "FAILED");
        String recent = attempt("29 days", "FAILED");

        long runId = call();

        assertThat(exists("SELECT 1 FROM identity_withdrawals WHERE id = ?", expiredHash)).isFalse();
        assertThat(exists("SELECT 1 FROM identity_withdrawals WHERE id = ?", liveHash)).isTrue();
        assertThat(exists("SELECT 1 FROM withdrawn_member_contacts WHERE user_id = ?", purged)).isFalse();
        assertThat(exists("SELECT 1 FROM withdrawn_member_contacts WHERE user_id = ?", kept)).isTrue();
        for (String old : new String[] {oldVerified, oldPending, oldFailed}) {
            assertThat(exists("SELECT 1 FROM identity_verification_attempts WHERE id = ?", old)).as(old).isFalse();
        }
        assertThat(exists("SELECT 1 FROM identity_verification_attempts WHERE id = ?", recent)).isTrue();

        Map<String, Object> run = jdbc.queryForMap("SELECT status, trigger_type, affected_count FROM batch_runs WHERE id = ?", runId);
        assertThat(run.get("status")).isEqualTo("OK");
        assertThat(run.get("trigger_type")).isEqualTo("MANUAL");
        assertThat(((Number) run.get("affected_count")).longValue()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void 보관_일수는_설정값이다() throws Exception {
        jdbc.update("INSERT INTO batch_params (job_key, param_key, param_value) VALUES ('member-privacy-retention', 'attempt_retention_days', 60) "
                + "ON CONFLICT (job_key, param_key) DO UPDATE SET param_value = EXCLUDED.param_value");
        try {
            String fortyDays = attempt("40 days", "VERIFIED");
            String seventyDays = attempt("70 days", "VERIFIED");
            call();
            assertThat(exists("SELECT 1 FROM identity_verification_attempts WHERE id = ?", fortyDays)).isTrue();
            assertThat(exists("SELECT 1 FROM identity_verification_attempts WHERE id = ?", seventyDays)).isFalse();
        } finally {
            jdbc.update("UPDATE batch_params SET param_value = 30 WHERE job_key = 'member-privacy-retention' AND param_key = 'attempt_retention_days'");
        }
    }

    @Test
    void 카탈로그에_DB_배치로_있고_기본_값이_등록돼_있다() {
        assertThat(BatchCatalog.MEMBER_PRIVACY_RETENTION.key()).isEqualTo("member-privacy-retention");
        assertThat(BatchCatalog.MEMBER_PRIVACY_RETENTION.source()).isEqualTo(BatchSource.POSTGRESQL);
        assertThat(BatchCatalog.MEMBER_PRIVACY_RETENTION.procedureName()).isEqualTo("batch_member_privacy_retention");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE proname = 'batch_member_privacy_retention'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT param_key FROM batch_params WHERE job_key = 'member-privacy-retention'", String.class))
                .contains("attempt_retention_days", "chunk_size", "max_per_run");
        assertThat(jdbc.queryForList("SELECT procedure_name FROM batch_db_jobs WHERE job_key = 'member-privacy-retention'", String.class))
                .containsExactly("batch_member_privacy_retention");
    }
}
