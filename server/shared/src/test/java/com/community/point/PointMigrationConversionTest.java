package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 포인트 확장 마이그레이션의 <b>전환</b>(D4, P22) — 같은 시험 컨테이너에 빈 DB 를 하나 더 만들어 포인트 확장 <b>바로 앞 번호까지</b> 이관하고, 옛 모양의 잔액·원장을 넣은 뒤
 * 포인트 확장을 적용해 결과를 본다. 파일 번호는 합칠 때 바뀔 수 있어(가번호) 설명 「point extension」으로 찾는다.
 *
 * <p>확인: 잔액이 있는 회원마다 무상 묶음 하나(남은 양 = 잔액, 소멸 = 전환 시각 + 1년, 출처 MIGRATION, ID 는 작은 순번 — Snowflake 와 겹치지 않음), 0 인 회원은 없음, 원장 줄은 늘지 않고
 * 기존 줄은 무상 몫 = delta, 무상 잔액 = 옛 잔액, 종류별 불변식(원장 합 = 잔액 = 묶음 합), 회원 삭제 연쇄가 사라짐.
 */
class PointMigrationConversionTest {

    private static final String DB = "point_d4_" + Long.toString(System.nanoTime(), 36);

    private static JdbcTemplate admin;
    private static DriverManagerDataSource legacy;
    private static JdbcTemplate jdbc;
    private static MigrationVersion pointExtension;

    @BeforeAll
    static void setUp() {
        admin = new JdbcTemplate(CommerceTestDatabase.migrated());
        admin.execute("CREATE DATABASE " + DB);
        legacy = new DriverManagerDataSource(CommerceTestDatabase.jdbcUrl().replaceFirst("/roboyam(?=\\?|$)", "/" + DB), CommerceTestDatabase.username(),
                CommerceTestDatabase.password());
        legacy.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(legacy);

        MigrationInfo[] all = flyway(null).info().all();
        MigrationInfo point = Arrays.stream(all).filter(info -> "point extension".equals(info.getDescription())).findFirst().orElseThrow();
        pointExtension = point.getVersion();
        MigrationVersion before = Arrays.stream(all).map(MigrationInfo::getVersion).filter(v -> v != null && v.compareTo(pointExtension) < 0)
                .max(MigrationVersion::compareTo).orElseThrow();
        flyway(before).migrate();
    }

    @AfterAll
    static void tearDown() {
        if (admin != null) {
            admin.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
        }
    }

    private static Flyway flyway(MigrationVersion target) {
        var configuration = SchemaMigration.STANDARD.configure(Flyway.configure().dataSource(legacy), SchemaMigration.SHARED);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void user(long id) {
        jdbc.update("INSERT INTO users (id, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, 'v1', now(), now())", id);
    }

    private static void line(long id, long user, String kind, String reason, int delta, long after) {
        jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, balance_after, ref_type, ref_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())",
                id, user, kind, reason, delta, after, reason.startsWith("POST") ? "POST" : null, reason.startsWith("POST") ? id : null);
    }

    @Test
    void 잔액이_있는_회원마다_1년짜리_무상_묶음_하나_원장_줄은_그대로() {
        // 옛 모양(포인트 확장 전): balance 칸 하나, 원장에 몫 칸 없음. ID 는 Snowflake 처럼 큰 수
        long rich = 300_000_000_000_000_001L;
        long poor = 300_000_000_000_000_002L;
        long none = 300_000_000_000_000_003L;
        user(rich);
        user(poor);
        user(none);
        line(300_000_000_000_001_001L, rich, "EARN", "POST", 20, 20);
        line(300_000_000_000_001_002L, rich, "EARN", "ADMIN_ADJUST", 100, 120);
        line(300_000_000_000_001_003L, rich, "REVOKE", "ADMIN_ADJUST", -30, 90);
        line(300_000_000_000_001_004L, poor, "EARN", "COMMENT", 5, 5);
        line(300_000_000_000_001_005L, poor, "REVOKE", "ADMIN_ADJUST", -5, 0);
        jdbc.update("INSERT INTO point_balances (user_id, balance, updated_at) VALUES (?, 90, now()), (?, 0, now())", rich, poor);

        flyway(pointExtension).migrate();

        Map<String, Object> started = jdbc.queryForMap("SELECT lot_tracking_started_at AS t FROM point_settings WHERE id = 1");
        List<Map<String, Object>> lots = jdbc.queryForList("SELECT l.id, l.user_id, l.kind, l.original_amount, l.remaining_amount, l.source_reason, l.source_ledger_id, "
                + "l.expires_at = s.lot_tracking_started_at + interval '1 year' AS one_year, l.created_at = s.lot_tracking_started_at AS at_conversion "
                + "FROM point_lots l CROSS JOIN point_settings s WHERE s.id = 1 AND l.user_id IN (?, ?, ?)", rich, poor, none);
        assertThat(started.get("t")).isNotNull();
        assertThat(lots).hasSize(1);
        Map<String, Object> lot = lots.get(0);
        assertThat(lot).containsEntry("user_id", rich).containsEntry("kind", "FREE").containsEntry("original_amount", 90L).containsEntry("remaining_amount", 90L)
                .containsEntry("source_reason", "MIGRATION").containsEntry("one_year", true).containsEntry("at_conversion", true);
        assertThat(lot.get("source_ledger_id")).isNull();
        assertThat(((Number) lot.get("id")).longValue()).as("순번 ID — Snowflake 보다 훨씬 작다").isLessThan(1L << 32);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledger", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE free_delta <> delta OR paid_delta <> 0", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT free_balance, paid_balance, revoke_debt, balance FROM point_balances WHERE user_id = ?", rich).get(0))
                .containsEntry("free_balance", 90L).containsEntry("paid_balance", 0L).containsEntry("revoke_debt", 0L).containsEntry("balance", 90L);

        // 종류별 불변식: 원장 몫 합 = 잔액 = 남은 묶음 합
        List<Long> broken = jdbc.queryForList("SELECT b.user_id FROM point_balances b "
                + "LEFT JOIN (SELECT user_id, sum(free_delta) f, sum(paid_delta) p FROM point_ledger GROUP BY user_id) l ON l.user_id = b.user_id "
                + "LEFT JOIN (SELECT user_id, sum(remaining_amount) FILTER (WHERE kind = 'FREE') f, sum(remaining_amount) FILTER (WHERE kind = 'PAID') p FROM point_lots GROUP BY user_id) t "
                + "ON t.user_id = b.user_id WHERE coalesce(l.f, 0) <> b.free_balance OR coalesce(t.f, 0) <> b.free_balance OR coalesce(l.p, 0) <> b.paid_balance "
                + "OR coalesce(t.p, 0) <> b.paid_balance", Long.class);
        assertThat(broken).isEmpty();

        // 회원 삭제 연쇄가 없다 — 잔액·원장이 있는 회원 행은 지우지 못한다
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", poor)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.update("DELETE FROM users WHERE id = ?", none)).isEqualTo(1);
    }
}
