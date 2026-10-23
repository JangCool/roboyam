package com.community.mfa.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.testsupport.CommerceTestDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * admin V3(관리자 2차 인증 표)·DB 함수 {@code admin_mfa_reset}·정리 배치 {@code batch_admin_mfa_cleanup} 을 실제 PostgreSQL 에서 확인한다(commerce.md 18절 — Testcontainers).
 * DB 를 다른 시험과 같이 쓴다 — 자기가 만든 운영자만 보고, 설정 행은 바꾸면 되돌린다.
 */
class MfaSchemaTest {

    private static final AtomicLong IDS = new AtomicLong(8_100_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated("admin");
        jdbc = new JdbcTemplate(dataSource);
    }

    private static long newOperator(String email) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved) VALUES (?, ?, ?, 'x', true)", id, email, "s" + id % 1_000_000_000_000L);
        return id;
    }

    private static long newOperator() {
        long id = IDS.get() + 1;
        return newOperator("schema-" + id + "-" + System.nanoTime() + "@test.local");
    }

    private static void enroll(long operatorId) {
        jdbc.update("INSERT INTO operator_mfa (operator_id, secret_enc, enrolled_at, last_step) VALUES (?, 'v1:secret-cipher', now(), 1)", operatorId);
    }

    private static long recovery(long operatorId, String hash) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO operator_mfa_recovery_codes (id, operator_id, code_hash, created_at) VALUES (?, ?, ?, now())", id, operatorId, hash);
        return id;
    }

    private static long device(long operatorId, String hash, String expiresIn) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO operator_trusted_devices (id, operator_id, token_hash, trusted_at, expires_at) VALUES (?, ?, ?, now() - interval '40 days', now() + ?::interval)",
                id, operatorId, hash, expiresIn);
        return id;
    }

    private static String hash(char c) {
        return String.valueOf(c).repeat(63) + (IDS.incrementAndGet() % 10);
    }

    @Nested
    class 설정_행 {

        @Test
        void 설치_직후에는_꺼짐_14일_1개월_5회다() {
            Map<String, Object> row = jdbc.queryForMap("SELECT mfa_enabled, grace_days, trust_months, max_failures, mfa_enabled_at FROM admin_security_settings WHERE id = 1");
            // 다른 시험이 바꿨다 되돌렸을 수 있으니 마이그레이션의 기본값(칸 DEFAULT)을 본다
            Map<String, Object> defaults = jdbc.queryForMap("SELECT "
                    + "(SELECT column_default FROM information_schema.columns WHERE table_name = 'admin_security_settings' AND column_name = 'mfa_enabled') AS enabled, "
                    + "(SELECT column_default FROM information_schema.columns WHERE table_name = 'admin_security_settings' AND column_name = 'grace_days') AS grace, "
                    + "(SELECT column_default FROM information_schema.columns WHERE table_name = 'admin_security_settings' AND column_name = 'trust_months') AS trust, "
                    + "(SELECT column_default FROM information_schema.columns WHERE table_name = 'admin_security_settings' AND column_name = 'max_failures') AS failures");

            assertThat(row).isNotNull();
            assertThat(defaults.get("enabled")).isEqualTo("false");
            assertThat(defaults.get("grace")).isEqualTo("14");
            assertThat(defaults.get("trust")).isEqualTo("1");
            assertThat(defaults.get("failures")).isEqualTo("5");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_security_settings", Integer.class)).isEqualTo(1);
        }

        @Test
        void 행은_하나뿐이다_id_1() {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO admin_security_settings (id) VALUES (2)")).isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("ck_admin_security_settings_single_row");
        }

        @Test
        void 범위를_벗어난_값은_DB_가_거절한다() {
            for (String bad : List.of("grace_days = 0", "grace_days = 91", "trust_months = 0", "trust_months = 13", "max_failures = 2", "max_failures = 11")) {
                assertThatThrownBy(() -> jdbc.update("UPDATE admin_security_settings SET " + bad + " WHERE id = 1")).as(bad).isInstanceOf(DataAccessException.class);
            }
            Integer grace = jdbc.queryForObject("SELECT grace_days FROM admin_security_settings WHERE id = 1", Integer.class);
            assertThat(grace).isBetween(1, 90);
        }

        @Test
        void 경계값은_받는다() throws SQLException {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    JdbcTemplate tx = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
                    assertThat(tx.update("UPDATE admin_security_settings SET grace_days = 1, trust_months = 1, max_failures = 3 WHERE id = 1")).isEqualTo(1);
                    assertThat(tx.update("UPDATE admin_security_settings SET grace_days = 90, trust_months = 12, max_failures = 10 WHERE id = 1")).isEqualTo(1);
                } finally {
                    connection.rollback(); // 설정 행은 공용 — 되돌린다
                }
            }
        }
    }

    @Nested
    class 해시_유일 {

        @Test
        void 같은_운영자의_같은_복구_코드_해시는_두_번_넣을_수_없다() {
            long operator = newOperator();
            String h = hash('a');
            recovery(operator, h);

            assertThatThrownBy(() -> recovery(operator, h)).isInstanceOf(DataAccessException.class).hasMessageContaining("ux_operator_mfa_recovery_codes");
            recovery(newOperator(), h); // 다른 운영자는 된다(해시에 운영자 ID 가 들어가 실제로는 겹치지 않는다)
        }

        @Test
        void 신뢰_기기_쿠키_해시는_전체에서_유일하다() {
            String h = hash('b');
            device(newOperator(), h, "30 days");

            assertThatThrownBy(() -> device(newOperator(), h, "30 days")).isInstanceOf(DataAccessException.class).hasMessageContaining("ux_operator_trusted_devices_token");
        }

        @Test
        void 등록_중_비밀키와_시작_시각은_함께_있거나_함께_없다() {
            long operator = newOperator();

            assertThatThrownBy(() -> jdbc.update("INSERT INTO operator_mfa (operator_id, pending_secret_enc) VALUES (?, 'v1:x')", operator))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_operator_mfa_pending");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO operator_mfa (operator_id, secret_enc) VALUES (?, 'v1:x')", operator))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_operator_mfa_enrolled");
        }
    }

    @Test
    void 운영자가_지워지면_2차_인증_기록이_모두_지워진다() {
        long operator = newOperator();
        enroll(operator);
        recovery(operator, hash('c'));
        device(operator, hash('d'), "30 days");

        jdbc.update("DELETE FROM operators WHERE id = ?", operator);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa WHERE operator_id = ?", Integer.class, operator)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa_recovery_codes WHERE operator_id = ?", Integer.class, operator)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_trusted_devices WHERE operator_id = ?", Integer.class, operator)).isZero();
    }

    @Nested
    class 서버_초기화_명령_admin_mfa_reset {

        @Test
        void 없는_이메일이면_NOT_FOUND_이고_아무것도_바꾸지_않는다() {
            Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'MFA_RESET'", Integer.class);

            assertThat(jdbc.queryForObject("SELECT admin_mfa_reset(?)", String.class, "nobody-" + System.nanoTime() + "@test.local")).isEqualTo("NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'MFA_RESET'", Integer.class)).isEqualTo(audits);
        }

        @Test
        void 대소문자를_무시하고_찾아_OTP_복구_코드_신뢰_기기를_지우고_SYSTEM_감사를_남긴다() {
            String email = "reset-" + System.nanoTime() + "@test.local";
            long operator = newOperator(email);
            enroll(operator);
            recovery(operator, hash('e'));
            recovery(operator, hash('f'));
            device(operator, hash('g'), "30 days");

            String result = jdbc.queryForObject("SELECT admin_mfa_reset(?)", String.class, "  " + email.toUpperCase() + " ");

            assertThat(result).isEqualTo("RESET");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa WHERE operator_id = ?", Integer.class, operator)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa_recovery_codes WHERE operator_id = ?", Integer.class, operator)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_trusted_devices WHERE operator_id = ?", Integer.class, operator)).isZero();
            Map<String, Object> audit = jdbc.queryForMap("SELECT actor_type, actor_id, result, target_type, detail FROM audit_logs WHERE action = 'MFA_RESET' AND target_id = ?",
                    Long.toString(operator));
            assertThat(audit.get("actor_type")).isEqualTo("SYSTEM");
            assertThat(audit.get("actor_id")).isNull();
            assertThat(audit.get("result")).isEqualTo("SUCCESS");
            assertThat(audit.get("target_type")).isEqualTo("OPERATOR");
            assertThat((String) audit.get("detail")).contains("\"via\": \"SERVER_COMMAND\"", "\"wasEnrolled\": true", "\"recoveryCodes\": 2", "\"trustedDevices\": 1")
                    .doesNotContain("v1:secret-cipher");
        }

        @Test
        void 등록하지_않은_운영자도_RESET_이고_감사를_남긴다() {
            String email = "reset-none-" + System.nanoTime() + "@test.local";
            long operator = newOperator(email);

            assertThat(jdbc.queryForObject("SELECT admin_mfa_reset(?)", String.class, email)).isEqualTo("RESET");
            assertThat(jdbc.queryForObject("SELECT detail FROM audit_logs WHERE action = 'MFA_RESET' AND target_id = ?", String.class, Long.toString(operator)))
                    .contains("\"wasEnrolled\": false");
        }

        @Test
        void 이메일은_문자열로만_쓰인다_SQL_주입이_되지_않는다() {
            long before = jdbc.queryForObject("SELECT count(*) FROM operators", Long.class);

            assertThat(jdbc.queryForObject("SELECT admin_mfa_reset(?)", String.class, "x'); DELETE FROM operators; --")).isEqualTo("NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operators", Long.class)).isEqualTo(before);
        }
    }

    @Nested
    class 정리_배치 {

        @Test
        void 기간이_끝난_신뢰_기기와_10분_넘은_등록_중_비밀키를_지운다() {
            long operator = newOperator();
            long expired = device(operator, hash('h'), "-1 minute");
            long alive = device(operator, hash('i'), "10 days");
            long stale = newOperator();
            jdbc.update("INSERT INTO operator_mfa (operator_id, pending_secret_enc, pending_started_at) VALUES (?, 'v1:p', now() - interval '11 minutes')", stale);
            long fresh = newOperator();
            jdbc.update("INSERT INTO operator_mfa (operator_id, pending_secret_enc, pending_started_at) VALUES (?, 'v1:p', now() - interval '2 minutes')", fresh);
            long enrolledWithStalePending = newOperator();
            jdbc.update("INSERT INTO operator_mfa (operator_id, secret_enc, enrolled_at, pending_secret_enc, pending_started_at, pending_reenroll) "
                    + "VALUES (?, 'v1:s', now(), 'v1:p', now() - interval '30 minutes', true)", enrolledWithStalePending);

            jdbc.execute("CALL batch_admin_mfa_cleanup('MANUAL', NULL, NULL)");

            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_trusted_devices WHERE id = ?", Integer.class, expired)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_trusted_devices WHERE id = ?", Integer.class, alive)).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa WHERE operator_id = ?", Integer.class, stale)).isZero(); // 빈 행은 지운다
            assertThat(jdbc.queryForObject("SELECT pending_secret_enc FROM operator_mfa WHERE operator_id = ?", String.class, fresh)).isEqualTo("v1:p");
            Map<String, Object> kept = jdbc.queryForMap("SELECT secret_enc, pending_secret_enc, pending_reenroll FROM operator_mfa WHERE operator_id = ?", enrolledWithStalePending);
            assertThat(kept.get("secret_enc")).isEqualTo("v1:s"); // 등록된 비밀키는 그대로
            assertThat(kept.get("pending_secret_enc")).isNull();
            assertThat(kept.get("pending_reenroll")).isEqualTo(false);
            String status = jdbc.queryForObject("SELECT status FROM batch_runs WHERE job_key = 'admin-mfa-cleanup' ORDER BY started_at DESC LIMIT 1", String.class);
            assertThat(status).isEqualTo("OK");
        }

        @Test
        void 배치가_정의돼_있다() {
            assertThat(jdbc.queryForObject("SELECT procedure_name FROM batch_db_jobs WHERE job_key = 'admin-mfa-cleanup'", String.class)).isEqualTo("batch_admin_mfa_cleanup");
        }
    }
}
