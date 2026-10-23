package com.community.mfa;

import com.community.admin.AdminApplication;
import com.community.adminauth.service.AdminActorService;
import com.community.mfa.dto.response.MfaEnrollmentView;
import com.community.mfa.service.MfaEnrollmentService;
import com.community.mfa.service.MfaSecrets;
import com.community.mfa.totp.Base32;
import com.community.mfa.totp.Totp;
import com.community.permission.dto.AdminActor;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 관리자 2차 인증 규칙 시험의 바탕 — 관리자 앱 문맥 전체를 커머스 시험 DB({@link CommerceTestDatabase}, 실제 PostgreSQL)에 띄운다(commerce.md 18절).
 *
 * <ul>
 *   <li>시계는 {@link MutableClock}(시험마다 지금 시각으로 되돌린다) — TOTP 칸·대기 5분·유예·기기 기간·잠금을 시각을 옮겨 확인한다.</li>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 운영자는 시험마다 새로 만들고(겹치지 않는 Snowflake ID), 설정 행({@code admin_security_settings})은 시험 끝에 원래 값으로 되돌린다.</li>
 *   <li>루트는 하나뿐이라(유일 색인) 있으면 쓰고 없으면 만든다. 루트의 OTP 상태는 시험마다 필요한 대로 정한다.</li>
 *   <li>배치 스케줄러·알림 감시자는 끈다. 설정 비밀값 키(ROBOYAM_CRYPTO_KEY)는 시험용 고정값이다.</li>
 * </ul>
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "roboyam.crypto.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "roboyam.batch.scheduler-enabled=false",
        "roboyam.notification.watcher-enabled=false"
})
@Import(MfaSpringTestBase.TestClockConfig.class)
public abstract class MfaSpringTestBase {

    protected static final String PASSWORD = "Passw0rd-mfa-test";
    protected static final String IP = "127.0.0.1";

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        CommerceTestDatabase.migrated("admin");
        registry.add("spring.datasource.url", CommerceTestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", CommerceTestDatabase::username);
        registry.add("spring.datasource.password", CommerceTestDatabase::password);
    }

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected DataSource dataSource;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected SnowflakeIdGenerator ids;
    @Autowired
    protected MfaSecrets secrets;
    @Autowired
    protected AdminActorService actors;
    @Autowired
    protected MfaEnrollmentService enrollments;

    protected JdbcTemplate jdbc;
    private Map<String, Object> savedSettings;
    private String passwordHash;

    @BeforeEach
    void baseSetUp() {
        jdbc = new JdbcTemplate(dataSource);
        clock.set(Instant.now());
        savedSettings = jdbc.queryForMap("SELECT mfa_enabled, grace_days, trust_months, max_failures, mfa_enabled_at, mfa_enabled_by FROM admin_security_settings WHERE id = 1");
        if (passwordHash == null) {
            passwordHash = encoder.encode(PASSWORD);
        }
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void baseTearDown() {
        jdbc.update("UPDATE admin_security_settings SET mfa_enabled = ?, grace_days = ?, trust_months = ?, max_failures = ?, mfa_enabled_at = ?, mfa_enabled_by = ? WHERE id = 1",
                savedSettings.get("mfa_enabled"), savedSettings.get("grace_days"), savedSettings.get("trust_months"), savedSettings.get("max_failures"),
                savedSettings.get("mfa_enabled_at"), savedSettings.get("mfa_enabled_by"));
        SecurityContextHolder.clearContext();
    }

    // ── 운영자 ─────────────────────────────────────────────────────────────

    /** 활성 운영자를 새로 만든다(비밀번호 {@link #PASSWORD}). 만든 시각은 DB 의 지금 */
    protected long newOperator() {
        return newOperator(null);
    }

    /** @param createdAt 계정을 만든 시각(null 이면 지금) */
    protected long newOperator(Instant createdAt) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, ?, true, ?, now())",
                id, email(id), "m" + (id % 10_000_000_000_000L), passwordHash, ts(createdAt == null ? clock.instant() : createdAt));
        return id;
    }

    protected static String email(long operatorId) {
        return "mfa-" + operatorId + "@test.local";
    }

    /** 루트(하나뿐) — 없으면 만든다 */
    protected long rootId() {
        List<Long> found = jdbc.queryForList("SELECT id FROM operators WHERE is_root", Long.class);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, is_root, approved, created_at, updated_at) VALUES (?, ?, ?, ?, true, true, now(), now())",
                id, email(id), "root" + (id % 1_000_000_000L), passwordHash);
        return id;
    }

    protected AdminActor actor(long operatorId) {
        return actors.load(operatorId, IP).orElseThrow();
    }

    // ── 설정 ─────────────────────────────────────────────────────────────

    protected void settings(boolean enabled, int graceDays, int trustMonths, int maxFailures, Instant enabledAt) {
        jdbc.update("UPDATE admin_security_settings SET mfa_enabled = ?, grace_days = ?, trust_months = ?, max_failures = ?, mfa_enabled_at = ? WHERE id = 1",
                enabled, graceDays, trustMonths, maxFailures, ts(enabledAt));
    }

    /** 켜짐 · 유예 14일 · 1개월 · 실패 5회, 켠 시각 = 하루 전 */
    protected void mfaOn() {
        settings(true, 14, 1, 5, clock.instant().minus(Duration.ofDays(1)));
    }

    protected void mfaOff() {
        settings(false, 14, 1, 5, null);
    }

    // ── OTP ─────────────────────────────────────────────────────────────

    /** DB 에 바로 등록한다(비밀키 암호문). 돌려주는 값은 base32 비밀키 */
    protected String enrollDirect(long operatorId) {
        String secret = MfaSecrets.newSecret();
        jdbc.update("INSERT INTO operator_mfa (operator_id, secret_enc, enrolled_at, updated_at) VALUES (?, ?, now(), now()) ON CONFLICT (operator_id) DO UPDATE SET "
                + "secret_enc = EXCLUDED.secret_enc, enrolled_at = EXCLUDED.enrolled_at, last_step = NULL, pending_secret_enc = NULL, pending_started_at = NULL", operatorId,
                secrets.encrypt(secret));
        return secret;
    }

    protected void unenroll(long operatorId) {
        jdbc.update("DELETE FROM operator_mfa WHERE operator_id = ?", operatorId);
        jdbc.update("DELETE FROM operator_mfa_recovery_codes WHERE operator_id = ?", operatorId);
    }

    /** 자발 등록을 서비스로 끝까지 — 비밀키와 복구 코드 */
    protected Enrolled enrollViaService(long operatorId) {
        AdminActor actor = actor(operatorId);
        MfaEnrollmentView view = enrollments.startVoluntary(actor);
        List<String> codes = enrollments.confirmVoluntary(actor, codeNow(view.secret()));
        return new Enrolled(view.secret(), codes);
    }

    protected record Enrolled(String secret, List<String> recoveryCodes) {
    }

    /** 지금 칸의 코드 */
    protected String codeNow(String base32Secret) {
        return Totp.code(Base32.decode(base32Secret), Totp.step(clock.instant()));
    }

    /** 시계를 한 칸(30초) 옮긴 뒤의 코드 — 같은 칸 재사용 거절을 피한다 */
    protected String nextCode(String base32Secret) {
        clock.advance(Duration.ofSeconds(Totp.PERIOD_SECONDS));
        return codeNow(base32Secret);
    }

    /** 틀린 코드(지금 칸 ±1 의 어느 것과도 다른 6자리) */
    protected String wrongCode(String base32Secret) {
        byte[] secret = Base32.decode(base32Secret);
        long step = Totp.step(clock.instant());
        for (int candidate = 0; candidate < 1_000_000; candidate++) {
            String code = String.format("%06d", candidate);
            if (!code.equals(Totp.code(secret, step - 1)) && !code.equals(Totp.code(secret, step)) && !code.equals(Totp.code(secret, step + 1))) {
                return code;
            }
        }
        throw new IllegalStateException();
    }

    // ── 확인용 ─────────────────────────────────────────────────────────────

    /** 그 운영자가 대상인 마지막 감사 기록의 detail(없으면 null) */
    protected String lastAudit(long targetOperatorId, String action) {
        List<String> rows = jdbc.queryForList("SELECT coalesce(detail, '') FROM audit_logs WHERE target_id = ? AND action = ? ORDER BY id DESC LIMIT 1", String.class,
                Long.toString(targetOperatorId), action);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected int auditCount(long targetOperatorId, String action) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id = ? AND action = ?", Integer.class, Long.toString(targetOperatorId), action);
        return count == null ? 0 : count;
    }

    protected int deviceCount(long operatorId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM operator_trusted_devices WHERE operator_id = ?", Integer.class, operatorId);
        return count == null ? 0 : count;
    }

    protected static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
