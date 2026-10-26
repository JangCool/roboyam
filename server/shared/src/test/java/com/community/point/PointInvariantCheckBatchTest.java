package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.batch.entity.BatchSource;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 포인트 불변식 검사 DB 배치 {@code point-invariant-check}({@code R__batch_29}, commerce.md 5-5) — 프로시저는 DB 전체를 보므로 같은 시험 컨테이너에 <b>빈 DB 를 하나 더</b> 만들어
 * 앱과 같은 길로 이관한 뒤(다른 시험의 행이 없다) 직접 넣은 행으로 판정을 본다. pg_cron 이 없어 {@code CALL} 로 바로 부른다.
 * 순서: 빈 DB(SKIP) → 맞는 데이터(OK) → 잔액만 어긋남(FAIL, 회원 ID) → 되돌림(OK) → 묶음 반영 없는 원장 줄(FAIL, 줄 ID).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PointInvariantCheckBatchTest {

    private static final String DB = "point_inv_" + Long.toString(System.nanoTime(), 36);
    private static final AtomicLong IDS = new AtomicLong(400_000_000_000_000_000L);

    private static JdbcTemplate admin;
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;

    private static long memberA;
    private static long memberB;
    private static long lotA;

    @BeforeAll
    static void setUp() {
        admin = new JdbcTemplate(CommerceTestDatabase.migrated());
        admin.execute("CREATE DATABASE " + DB);
        dataSource = new DriverManagerDataSource(CommerceTestDatabase.jdbcUrl().replaceFirst("/roboyam(?=\\?|$)", "/" + DB), CommerceTestDatabase.username(),
                CommerceTestDatabase.password());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        Flyway flyway = SchemaMigration.STANDARD.configure(Flyway.configure().dataSource(dataSource), SchemaMigration.SHARED).load();
        flyway.migrate();
    }

    @AfterAll
    static void tearDown() {
        if (admin != null) {
            admin.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
        }
    }

    private static long id() {
        return IDS.incrementAndGet();
    }

    private static long user() {
        long id = id();
        jdbc.update("INSERT INTO users (id, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, 'v1', now(), now())", id);
        return id;
    }

    private static long line(long user, String reason, long free, long paid, long after, Instant at) {
        long id = id();
        jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, created_at) VALUES (?, ?, 'EARN', ?, ?, ?, ?, ?, ?)",
                id, user, reason, (int) (free + paid), free, paid, after, Timestamp.from(at));
        return id;
    }

    private static long lot(long user, String kind, long original, long remaining, String source, Long sourceLedger) {
        long id = id();
        jdbc.update("INSERT INTO point_lots (id, user_id, kind, original_amount, remaining_amount, expires_at, source_reason, source_ledger_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())", id, user, kind, original, remaining, "FREE".equals(kind) ? Timestamp.from(Instant.now().plus(365, ChronoUnit.DAYS)) : null,
                source, sourceLedger);
        return id;
    }

    private static void part(long ledgerId, long lotId, long amount) {
        jdbc.update("INSERT INTO point_ledger_lots (ledger_id, lot_id, amount) VALUES (?, ?, ?)", ledgerId, lotId, amount);
    }

    private static void balance(long user, long free, long paid) {
        jdbc.update("INSERT INTO point_balances (user_id, free_balance, paid_balance, revoke_debt, updated_at) VALUES (?, ?, ?, 0, now()) "
                + "ON CONFLICT (user_id) DO UPDATE SET free_balance = EXCLUDED.free_balance, paid_balance = EXCLUDED.paid_balance", user, free, paid);
    }

    /** 프로시저를 「지금 실행」처럼 부르고 그 실행 줄 */
    private static Map<String, Object> call() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true); // 프로시저가 COMMIT 한다
            try (CallableStatement call = connection.prepareCall("CALL batch_point_invariant_check('MANUAL', NULL, NULL)")) {
                call.execute();
            }
        }
        return jdbc.queryForMap("SELECT status, affected_count, message_key, message_args, error_message FROM batch_runs WHERE job_key = 'point-invariant-check' "
                + "ORDER BY started_at DESC, id DESC LIMIT 1");
    }

    @Test
    @Order(1)
    void 검사할_것이_없으면_건너뜀() throws Exception {
        Map<String, Object> run = call();

        assertThat(run.get("status")).isEqualTo("SKIP");
        assertThat(run.get("message_key")).isEqualTo("batch.result.pointInvariantCheck.none");
    }

    @Test
    @Order(2)
    void 원장_합_잔액_묶음_합이_종류별로_맞고_줄마다_묶음_반영이_맞으면_OK_전환_이전_줄은_보지_않는다() throws Exception {
        Instant started = jdbc.queryForObject("SELECT lot_tracking_started_at FROM point_settings WHERE id = 1", Timestamp.class).toInstant();
        // 회원 A: 무상 적립 100(묶음 원래 양 200 — 아래 시험이 남은 양을 늘린다) + 유상 50
        memberA = user();
        long earn = line(memberA, "ADMIN_ADJUST", 100, 0, 100, Instant.now());
        lotA = lot(memberA, "FREE", 200, 100, "ADMIN_ADJUST", earn);
        part(earn, lotA, 100);
        long charge = line(memberA, "CHARGE", 0, 50, 150, Instant.now());
        long paidLot = lot(memberA, "PAID", 50, 50, "CHARGE", charge);
        part(charge, paidLot, 50);
        balance(memberA, 100, 50);
        // 회원 B: 전환 이전 줄(묶음 반영 없음) + 전환 묶음(원장 줄 없음)
        memberB = user();
        line(memberB, "POST", 30, 0, 30, started.minus(1, ChronoUnit.DAYS));
        lot(memberB, "FREE", 30, 30, "MIGRATION", null);
        balance(memberB, 30, 0);

        Map<String, Object> run = call();

        assertThat(run.get("status")).isEqualTo("OK");
        assertThat(((Number) run.get("affected_count")).longValue()).as("검사한 회원").isEqualTo(2);
        assertThat(run.get("message_key")).isEqualTo("batch.result.pointInvariantCheck");
        assertThat((String) run.get("message_args")).as("회원 2명 · 추적 뒤 원장 줄 2줄").isEqualTo("[\"2\",\"2\"]");
    }

    @Test
    @Order(3)
    void 잔액이_원장_묶음과_어긋나면_FAIL_사유에_건수와_회원_ID() throws Exception {
        jdbc.update("UPDATE point_balances SET free_balance = 90 WHERE user_id = ?", memberA);
        try {
            Map<String, Object> run = call();

            assertThat(run.get("status")).isEqualTo("FAIL");
            String error = (String) run.get("error_message");
            assertThat(error).contains("회원 1명").contains(Long.toString(memberA)).contains("원장 줄 0줄").doesNotContain(Long.toString(memberB));
        } finally {
            jdbc.update("UPDATE point_balances SET free_balance = 100 WHERE user_id = ?", memberA);
        }

        assertThat(call().get("status")).as("되돌리면 다시 OK").isEqualTo("OK");
    }

    @Test
    @Order(4)
    void 추적_뒤_원장_줄의_묶음_반영_합이_몫과_다르면_FAIL_사유에_줄_ID() throws Exception {
        // 회원 합은 맞게(잔액 110 = 원장 110 = 묶음 110) 두고 줄 하나만 묶음 반영 없이 넣는다
        long orphan = line(memberA, "ADMIN_ADJUST", 10, 0, 160, Instant.now());
        jdbc.update("UPDATE point_lots SET remaining_amount = 110 WHERE id = ?", lotA);
        balance(memberA, 110, 50);

        Map<String, Object> run = call();

        assertThat(run.get("status")).isEqualTo("FAIL");
        String error = (String) run.get("error_message");
        assertThat(error).contains("회원 0명").contains("원장 줄 1줄").contains(Long.toString(orphan));
    }

    @Test
    void 카탈로그에_DB_배치로_있고_기본_시각이_등록돼_있다() {
        assertThat(BatchCatalog.POINT_INVARIANT_CHECK.source()).isEqualTo(BatchSource.POSTGRESQL);
        assertThat(BatchCatalog.POINT_INVARIANT_CHECK.procedureName()).isEqualTo("batch_point_invariant_check");
        Map<String, Object> defined = jdbc.queryForMap("SELECT * FROM batch_db_jobs WHERE job_key = 'point-invariant-check'");
        assertThat(defined.get("procedure_name")).isEqualTo("batch_point_invariant_check");
        assertThat(((Number) defined.get("kst_hour")).intValue()).as("매일 05:30 KST").isEqualTo(5);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = 'point-invariant-check' AND param_key = 'sample_size'", Long.class))
                .isEqualTo(5L);
    }
}
