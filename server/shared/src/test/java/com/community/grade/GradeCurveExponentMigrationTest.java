package com.community.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
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
 * 레벨 곡선 지수 마이그레이션(설명 「grade curve exponent」, grade-level-contract 6절)의 <b>전환</b> — 같은 시험 컨테이너에 빈 DB 를 만들어 바로 앞 번호까지 이관하고 옛 곡선(지수 1.5)으로
 * 매긴 회원을 넣은 뒤, 그 DB 를 본떠 두 사이트를 만들어 적용해 본다.
 * <ul>
 *   <li><b>기본 사다리 사이트</b>(서열별 최소 레벨이 V196 기본값 그대로): 최소 레벨 재배치(1·5·10·20·30·40·50·60·75·90·100) · 닉네임 이미지 조건이 기본값이면 서열 5 등급으로.</li>
 *   <li><b>운영자가 고친 사이트</b>: 최소 레벨·닉네임 이미지 조건 그대로.</li>
 *   <li>두 사이트 모두: 지수 2.0 · 전 회원 레벨 다시 매김(내려간다) · <b>등급은 내려가지 않는다</b> · 인자 둘짜리 함수는 없다.</li>
 *   <li>낮아진 문턱으로 오를 회원은 마이그레이션이 올리지 않고 일수 승급 배치({@code batch_grade_promotion})가 올린다(올리기만, 이력 kind=AUTO).</li>
 * </ul>
 */
class GradeCurveExponentMigrationTest {

    private static final String SUFFIX = Long.toString(System.nanoTime(), 36);
    private static final String BASE_DB = "grade_curve_base_" + SUFFIX;
    private static final String DEFAULT_DB = "grade_curve_default_" + SUFFIX;
    private static final String CUSTOM_DB = "grade_curve_custom_" + SUFFIX;

    /** 회원: 새싹·200 XP(옛 Lv.8 → 새 Lv.5 — 새 견습 문턱 160 XP 를 넘는다) */
    private static final long LOW = 9_200_000_000_000_000_001L;
    /** 회원: 서열 11(신화)·9,851 XP(옛 Lv.100 → 새 Lv.32) — 등급이 내려가면 안 된다 */
    private static final long TOP = 9_200_000_000_000_000_002L;
    /** 회원: 서열 2(견습)·810 XP(옛 Lv.19 → 새 Lv.10 — 새 정식 문턱) */
    private static final long MID = 9_200_000_000_000_000_003L;
    /** 회원: 새싹·0 XP */
    private static final long ZERO = 9_200_000_000_000_000_004L;
    /** 회원: 새싹·200 XP 인데 정지 중 — 배치가 올리지 않는다 */
    private static final long SUSPENDED = 9_200_000_000_000_000_005L;

    private static JdbcTemplate admin;
    private static JdbcTemplate standard;
    private static JdbcTemplate custom;

    @BeforeAll
    static void setUp() {
        admin = new JdbcTemplate(CommerceTestDatabase.migrated());
        admin.execute("CREATE DATABASE " + BASE_DB);
        DriverManagerDataSource base = source(BASE_DB);
        JdbcTemplate jdbc = new JdbcTemplate(base);

        MigrationInfo[] all = flyway(base, null).info().all();
        MigrationVersion curve = Arrays.stream(all).filter(info -> "grade curve exponent".equals(info.getDescription())).findFirst().orElseThrow().getVersion();
        MigrationVersion before = Arrays.stream(all).map(MigrationInfo::getVersion).filter(v -> v != null && v.compareTo(curve) < 0).max(MigrationVersion::compareTo)
                .orElseThrow();
        flyway(base, before).migrate();

        // 옛 판의 회원 — 레벨은 옛 곡선(인자 둘짜리 함수, 지수 1.5)으로 매긴다
        member(jdbc, LOW, "곡선낮음", gradeAt(jdbc, 1), 200, "ACTIVE");
        member(jdbc, TOP, "곡선높음", gradeAt(jdbc, 11), 9_851, "ACTIVE");
        member(jdbc, MID, "곡선중간", gradeAt(jdbc, 2), 810, "ACTIVE");
        member(jdbc, ZERO, "곡선영", gradeAt(jdbc, 1), 0, "ACTIVE");
        member(jdbc, SUSPENDED, "곡선정지", gradeAt(jdbc, 1), 200, "SUSPENDED");
        jdbc.update("UPDATE profiles SET level = grade_xp_level(xp, 10)");
        assertThat(level(jdbc, LOW)).as("옛 곡선").isEqualTo(8);
        assertThat(level(jdbc, TOP)).as("옛 곡선").isEqualTo(100);
        assertThat(level(jdbc, MID)).as("옛 곡선").isEqualTo(19);
        assertThat(minLevels(jdbc)).as("V196 기본 사다리").containsExactly(1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        assertThat(jdbc.queryForObject("SELECT mode || ':' || grade_key FROM nickname_image_settings WHERE id = 1", String.class)).isEqualTo("GRADE:VETERAN");

        // 본뜨기 — 기본 사다리 사이트 / 운영자가 고친 사이트(견습 최소 레벨 15)
        admin.execute("CREATE DATABASE " + DEFAULT_DB + " TEMPLATE " + BASE_DB);
        admin.execute("CREATE DATABASE " + CUSTOM_DB + " TEMPLATE " + BASE_DB);
        DriverManagerDataSource standardSource = source(DEFAULT_DB);
        DriverManagerDataSource customSource = source(CUSTOM_DB);
        standard = new JdbcTemplate(standardSource);
        custom = new JdbcTemplate(customSource);
        custom.update("UPDATE grades SET min_level = 15 WHERE level = 2");

        flyway(standardSource, curve).migrate();
        flyway(customSource, curve).migrate();
    }

    @AfterAll
    static void tearDown() {
        if (admin != null) {
            for (String db : List.of(DEFAULT_DB, CUSTOM_DB, BASE_DB)) {
                admin.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
            }
        }
    }

    private static DriverManagerDataSource source(String db) {
        DriverManagerDataSource source = new DriverManagerDataSource(CommerceTestDatabase.jdbcUrl().replaceFirst("/irobit(?=\\?|$)", "/" + db),
                CommerceTestDatabase.username(), CommerceTestDatabase.password());
        source.setDriverClassName("org.postgresql.Driver");
        return source;
    }

    private static Flyway flyway(DriverManagerDataSource source, MigrationVersion target) {
        var configuration = SchemaMigration.STANDARD.configure(Flyway.configure().dataSource(source), SchemaMigration.SHARED);
        if (target != null) {
            configuration.target(target).repeatableSqlMigrationPrefix("NOT_A_REPEATABLE__"); // 옛 판까지는 R__ 없이(PointBoardTierMigrationTest 와 같은 까닭)
        }
        return configuration.load();
    }

    private static void member(JdbcTemplate jdbc, long id, String nickname, String gradeKey, long xp, String status) {
        jdbc.update("INSERT INTO users (id, status, terms_version, terms_agreed_at, age_confirmed_at, created_at) VALUES (?, ?, 'v1', now(), now(), now() - interval '10 days')",
                id, status);
        jdbc.update("INSERT INTO profiles (user_id, nickname, grade_key, xp) VALUES (?, ?, ?, ?)", id, nickname, gradeKey, xp);
    }

    private static String gradeAt(JdbcTemplate jdbc, int rank) {
        return jdbc.queryForObject("SELECT grade_key FROM grades WHERE level = ?", String.class, rank);
    }

    private static int level(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForObject("SELECT level FROM profiles WHERE user_id = ?", Integer.class, userId);
    }

    private static int rank(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForObject("SELECT g.level FROM profiles p JOIN grades g ON g.grade_key = p.grade_key WHERE p.user_id = ?", Integer.class, userId);
    }

    private static List<Integer> minLevels(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT min_level FROM grades ORDER BY level", Integer.class);
    }

    @Test
    void 지수_칸은_2_0_에서_시작하고_다섯_값만_받는다() {
        for (JdbcTemplate jdbc : List.of(standard, custom)) {
            assertThat(jdbc.queryForObject("SELECT xp_curve_exponent FROM grade_settings WHERE id = 1", BigDecimal.class)).isEqualByComparingTo("2.0");
            assertThatThrownBy(() -> jdbc.update("UPDATE grade_settings SET xp_curve_exponent = 1.7 WHERE id = 1")).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE grade_settings SET xp_curve_exponent = 3.5 WHERE id = 1")).isInstanceOf(DataAccessException.class);
            // 인자 둘짜리(지수 1.5 고정)는 지웠다 — 남은 것은 인자 셋짜리 둘뿐
            assertThat(jdbc.queryForList("SELECT proname || '/' || pronargs FROM pg_proc WHERE proname IN ('grade_level_xp', 'grade_xp_level') ORDER BY 1", String.class))
                    .containsExactly("grade_level_xp/3", "grade_xp_level/3");
        }
    }

    @Test
    void 전_회원_레벨을_새_곡선으로_다시_매기고_등급은_내리지_않는다() {
        for (JdbcTemplate jdbc : List.of(standard, custom)) {
            assertThat(level(jdbc, LOW)).isEqualTo(5);   // 160 <= 200 < 250
            assertThat(level(jdbc, TOP)).isEqualTo(32);  // 9,610 <= 9,851 < 10,240
            assertThat(level(jdbc, MID)).isEqualTo(10);  // 810
            assertThat(level(jdbc, ZERO)).isEqualTo(1);
            assertThat(rank(jdbc, TOP)).as("레벨이 내려가도 등급은 그대로").isEqualTo(11);
            assertThat(rank(jdbc, MID)).isEqualTo(2);
            assertThat(rank(jdbc, LOW)).as("마이그레이션은 올리지도 않는다(배치가 올린다)").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_grade_changes WHERE user_id IN (?, ?, ?, ?, ?)", Long.class, LOW, TOP, MID, ZERO, SUSPENDED))
                    .isZero();
        }
    }

    @Test
    void 기본_사다리_사이트는_최소_레벨을_재배치하고_닉네임_이미지_조건을_서열_5_등급으로_낮춘다() {
        assertThat(minLevels(standard)).containsExactly(1, 5, 10, 20, 30, 40, 50, 60, 75, 90, 100);
        assertThat(standard.queryForObject("SELECT mode || ':' || grade_key FROM nickname_image_settings WHERE id = 1", String.class))
                .isEqualTo("GRADE:" + gradeAt(standard, 5));
        assertThat(gradeAt(standard, 5)).as("새 설치의 서열 5 키(설치 프리셋과 같다)").isEqualTo("GRADE_7");
    }

    @Test
    void 운영자가_고친_사이트는_최소_레벨과_닉네임_이미지_조건이_그대로다() {
        assertThat(minLevels(custom)).containsExactly(1, 15, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        assertThat(custom.queryForObject("SELECT mode || ':' || grade_key FROM nickname_image_settings WHERE id = 1", String.class)).isEqualTo("GRADE:VETERAN");
    }

    @Test
    void 낮아진_문턱으로_오를_회원은_일수_승급_배치가_올린다() {
        // 본뜬 DB 를 하나 더 — 반복 마이그레이션(배치 프로시저)까지 올린 뒤 배치를 직접 부른다
        String db = "grade_curve_batch_" + SUFFIX;
        admin.execute("CREATE DATABASE " + db + " TEMPLATE " + BASE_DB);
        try {
            DriverManagerDataSource source = source(db);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            flyway(source, null).migrate();
            assertThat(rank(jdbc, LOW)).isEqualTo(1);

            jdbc.execute("CALL batch_grade_promotion('MANUAL', NULL, NULL)");

            assertThat(rank(jdbc, LOW)).as("Lv.5 · 가입 10일 → 견습(최소 레벨 5 · 3일)").isEqualTo(2);
            assertThat(rank(jdbc, MID)).as("Lv.10 · 가입 10일 → 정식(최소 레벨 10 · 7일)").isEqualTo(3);
            assertThat(rank(jdbc, TOP)).isEqualTo(11);
            assertThat(rank(jdbc, ZERO)).isEqualTo(1);
            assertThat(rank(jdbc, SUSPENDED)).as("정지 회원은 자동 승급이 멈춘다").isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT kind FROM member_grade_changes WHERE user_id IN (?, ?) ORDER BY user_id", String.class, LOW, MID))
                    .containsExactly("AUTO", "AUTO");
        } finally {
            admin.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
        }
    }
}
