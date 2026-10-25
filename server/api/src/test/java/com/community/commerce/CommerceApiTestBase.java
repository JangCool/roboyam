package com.community.commerce;

import com.community.api.ApiApplication;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커머스 0-4(본인인증·기능 제한·탈퇴 대비) 규칙 시험의 바탕 — 공개 사이트 앱 문맥 전체를 커머스 시험 DB({@link CommerceTestDatabase}, 실제 PostgreSQL)에 띄운다(commerce.md 18절).
 *
 * <ul>
 *   <li>프로필은 앱 기본값 local — 본인인증은 가짜 구현(roboyam-shared-local.yml), 데이터 키·해시 키는 local 의 개발용 키다. 설정 비밀값 키(ROBOYAM_CRYPTO_KEY)는 시험용 고정값.</li>
 *   <li>시계는 {@link MutableClock}(시험마다 지금으로 되돌린다).</li>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 회원·운영자는 시험마다 새로 만들고(겹치지 않는 Snowflake ID), 표를 비우지 않는다.</li>
 * </ul>
 */
@SpringBootTest(classes = ApiApplication.class, properties = {
        "roboyam.crypto.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@Import(CommerceApiTestBase.TestClockConfig.class)
public abstract class CommerceApiTestBase {

    private static final AtomicLong SEQ = new AtomicLong();

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
        CommerceTestDatabase.migrated("api");
        registry.add("spring.datasource.url", CommerceTestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", CommerceTestDatabase::username);
        registry.add("spring.datasource.password", CommerceTestDatabase::password);
    }

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected DataSource dataSource;
    @Autowired
    protected SnowflakeIdGenerator ids;
    @Autowired
    protected PlatformTransactionManager transactions;

    protected JdbcTemplate jdbc;
    protected TransactionTemplate tx;

    @BeforeEach
    void baseSetUp() {
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(transactions);
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS)); // DB 가 마이크로초로 반올림하므로 처음부터 맞춘다
    }

    /** 정상 회원을 새로 만든다 */
    protected long newMember() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO users (id, email, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, 'v1', now(), now())", id,
                "c04-" + id + "@test.local");
        return id;
    }

    /** 탈퇴 처리(상태만) */
    protected void markWithdrawn(long userId) {
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', email = NULL WHERE id = ?", userId);
    }

    /** 활성 운영자를 새로 만든다 */
    protected long newOperator() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "c04op-" + id + "@test.local", "op" + (id % 10_000_000_000L));
        return id;
    }

    /** 이 실행에서 겹치지 않는 가짜 본인인증 사람 번호 */
    protected static String newPerson() {
        return "p" + Long.toString(System.nanoTime() % 1_000_000_000L, 36) + "-" + SEQ.incrementAndGet();
    }

    /** 그 대상의 마지막 감사 기록(없으면 null) */
    protected Map<String, Object> lastAudit(String targetType, String targetId, String action) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT actor_type, actor_id, detail FROM audit_logs WHERE target_type = ? AND target_id = ? AND action = ? "
                + "ORDER BY id DESC LIMIT 1", targetType, targetId, action);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected int auditCount(String targetType, String targetId, String action) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_type = ? AND target_id = ? AND action = ?", Integer.class, targetType, targetId,
                action);
        return count == null ? 0 : count;
    }

    protected static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
