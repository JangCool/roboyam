package com.community.holiday;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 쉬는 날 표 하나(shared V150 「site holidays」)의 <b>옮기기</b> — 같은 시험 컨테이너에 빈 DB 를 하나 더 만들어 바로 앞 번호까지 이관하고, 옛 모양(공휴일 표 V146 · 공구 설정 휴일 칸)에
 * 값을 넣은 뒤 V150 을 적용해 본다(파일 번호는 합칠 때 바뀔 수 있어 설명으로 찾는다).
 *
 * <p>확인: V146 시드가 기본값(SEED)으로(대체공휴일 여부 포함 — 2026 22줄 · 2027 24줄) · 공구 휴일이 회사 휴무일(OPERATOR, 넣은 운영자 없음, 이름 「공동구매 휴일(옮김)」)로 —
 * 공휴일과 같은 날이어도 두 줄, 겹친 날은 한 줄 · 공구 설정 휴일 칸과 옛 공휴일 표는 없어짐 · 받기 결과·API 주소 표는 비어 있음.
 */
class SiteHolidayMigrationTest {

    private static final String DB = "holiday_v150_" + Long.toString(System.nanoTime(), 36);

    private static JdbcTemplate admin;
    private static DriverManagerDataSource legacy;
    private static JdbcTemplate jdbc;
    private static MigrationVersion siteHolidays;

    @BeforeAll
    static void setUp() {
        admin = new JdbcTemplate(CommerceTestDatabase.migrated());
        admin.execute("CREATE DATABASE " + DB);
        legacy = new DriverManagerDataSource(CommerceTestDatabase.jdbcUrl().replaceFirst("/irobit(?=\\?|$)", "/" + DB), CommerceTestDatabase.username(),
                CommerceTestDatabase.password());
        legacy.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(legacy);

        MigrationInfo[] all = flyway(null).info().all();
        siteHolidays = Arrays.stream(all).filter(info -> "site holidays".equals(info.getDescription())).findFirst().orElseThrow().getVersion();
        MigrationVersion before = Arrays.stream(all).map(MigrationInfo::getVersion).filter(v -> v != null && v.compareTo(siteHolidays) < 0)
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
            // 옛 판까지는 반복 마이그레이션(R__)을 빼고 올린다(PointMigrationConversionTest 와 같은 까닭)
            configuration.target(target).repeatableSqlMigrationPrefix("NOT_A_REPEATABLE__");
        }
        return configuration.load();
    }

    @Test
    void 시드는_기본값으로_공구_휴일은_회사_휴무일로_옮기고_옛_칸과_표는_지운다() {
        jdbc.update("UPDATE group_buy_settings SET holidays = ARRAY['2026-12-31'::date, '2026-03-02'::date, '2026-12-31'::date] WHERE id = 1");

        flyway(siteHolidays).migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_holidays WHERE source = 'SEED' AND holiday_date BETWEEN '2026-01-01' AND '2026-12-31'", Integer.class))
                .isEqualTo(22);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_holidays WHERE source = 'SEED' AND holiday_date BETWEEN '2027-01-01' AND '2027-12-31'", Integer.class))
                .isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT substitute FROM site_holidays WHERE source = 'SEED' AND holiday_date = '2026-03-02'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT substitute FROM site_holidays WHERE source = 'SEED' AND holiday_date = '2026-03-01'", Boolean.class)).isFalse();

        List<Map<String, Object>> moved = jdbc.queryForList("SELECT holiday_date::text AS d, name, created_by FROM site_holidays WHERE source = 'OPERATOR' ORDER BY holiday_date");
        assertThat(moved).extracting(m -> m.get("d")).containsExactly("2026-03-02", "2026-12-31");
        assertThat(moved).allSatisfy(m -> {
            assertThat(m.get("name")).isEqualTo("공동구매 휴일(옮김)");
            assertThat(m.get("created_by")).isNull();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_holidays WHERE holiday_date = '2026-03-02'", Integer.class)).as("공휴일 + 회사 휴무일 두 줄").isEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'group_buy_settings' AND column_name = 'holidays'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public_holidays') IS NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_holiday_sync", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public_data_endpoints", Integer.class)).isZero();
    }
}
