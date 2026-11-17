package com.community.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.grade.service.XpCurve;
import com.community.testsupport.CommerceTestDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 레벨 곡선의 <b>이중 구현</b>(grade-level-contract 6절) — Java {@link XpCurve} 와 SQL 함수 {@code grade_level_xp}·{@code grade_xp_level}(shared V245)이 같은 값을 내는지.
 * 지수 다섯 값 × 기준값 1·10·1000 × 레벨 경계(1·2·작은 값·100·큰 값·넘침 구간), {@code levelOf} ↔ {@code grade_xp_level} 은 경계 경험치의 −1·0·+1 과 양 끝 값.
 * 연결 하나로 돈다(시험 DB 는 부를 때마다 새 연결을 연다 — 수천 번 부르면 임시 포트가 모자란다).
 */
class XpCurveSqlParityTest {

    private static final int[] BASES = {1, 10, 1000};
    /** 레벨 경계 — 0 이하·1(= 0 XP), 작은 값, 100 둘레, 큰 값, 넘침 구간(지수 3.0·기준값 1000 은 Lv.209,717 부터 bigint 를 넘는다), 레벨 상한 */
    private static final int[] LEVELS = {-3, 0, 1, 2, 3, 4, 5, 6, 10, 11, 20, 99, 100, 101, 1000, 4097, 209_716, 209_717, 209_718, 1_000_000, 2_097_153, 1_999_999_999,
            2_000_000_000};
    private static final long[] XPS = {Long.MIN_VALUE, -1, 0, 1, 9, 10, 11, 159, 160, 161, 12_345, 98_009, 98_010, 98_011, 1_000_000_000_000L, Long.MAX_VALUE - 2,
            Long.MAX_VALUE - 1, Long.MAX_VALUE};

    private static Connection connection;
    private static PreparedStatement levelXp;
    private static PreparedStatement xpLevel;

    @BeforeAll
    static void connect() throws SQLException {
        connection = CommerceTestDatabase.migrated().getConnection();
        levelXp = connection.prepareStatement("SELECT grade_level_xp(?, ?, ?)");
        xpLevel = connection.prepareStatement("SELECT grade_xp_level(?, ?, ?)");
    }

    @AfterAll
    static void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private static long sqlLevelXp(int level, int base, BigDecimal exponent) throws SQLException {
        levelXp.setInt(1, level);
        levelXp.setInt(2, base);
        levelXp.setBigDecimal(3, exponent);
        try (ResultSet rs = levelXp.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static int sqlXpLevel(long xp, int base, BigDecimal exponent) throws SQLException {
        xpLevel.setLong(1, xp);
        xpLevel.setInt(2, base);
        xpLevel.setBigDecimal(3, exponent);
        try (ResultSet rs = xpLevel.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void 레벨이_시작하는_경험치는_자바와_SQL_이_같다() throws SQLException {
        for (BigDecimal exponent : XpCurve.EXPONENTS) {
            for (int base : BASES) {
                for (int level : LEVELS) {
                    assertThat(sqlLevelXp(level, base, exponent)).as("levelXp 지수=%s 기준값=%d 레벨=%d", exponent, base, level)
                            .isEqualTo(XpCurve.levelXp(level, base, exponent));
                }
            }
        }
    }

    @Test
    void 경험치의_레벨은_경계의_앞뒤와_양_끝에서_자바와_SQL_이_같다() throws SQLException {
        for (BigDecimal exponent : XpCurve.EXPONENTS) {
            for (int base : BASES) {
                TreeSet<Long> xps = new TreeSet<>();
                for (long xp : XPS) {
                    xps.add(xp);
                }
                for (int level : LEVELS) {
                    long at = XpCurve.levelXp(level, base, exponent);
                    xps.add(at);
                    xps.add(at - 1);
                    if (at < Long.MAX_VALUE) {
                        xps.add(at + 1);
                    }
                }
                for (long xp : xps) {
                    int java = XpCurve.levelOf(xp, base, exponent);
                    assertThat(sqlXpLevel(xp, base, exponent)).as("levelOf 지수=%s 기준값=%d 경험치=%d", exponent, base, xp).isEqualTo(java);
                    // 레벨의 뜻: levelXp(L) <= xp < levelXp(L+1) — 넘쳐 잘린 레벨(최댓값)에는 닿지 않는다
                    long capped = Math.min(Math.max(xp, 0), Long.MAX_VALUE - 1);
                    assertThat(XpCurve.levelXp(java, base, exponent)).as("레벨 시작 지수=%s 기준값=%d 경험치=%d", exponent, base, xp).isLessThanOrEqualTo(capped);
                    if (java < XpCurve.MAX_LEVEL) {
                        assertThat(XpCurve.levelXp(java + 1, base, exponent)).as("다음 레벨 지수=%s 기준값=%d 경험치=%d", exponent, base, xp).isGreaterThan(capped);
                    }
                }
            }
        }
    }

    @Test
    void 계약의_숫자와_넘침_자르기() throws SQLException {
        BigDecimal two = new BigDecimal("2.0");
        BigDecimal oneHalf = new BigDecimal("1.5");
        BigDecimal three = new BigDecimal("3.0");
        // 6절: 지수 2.0 에서 Lv.100 = 98,010 XP(지수 1.5 는 9,851), 견습 Lv.5 = 160(옛 Lv.10 = 270), 정식 Lv.10 = 810
        assertThat(XpCurve.levelXp(100, 10, two)).isEqualTo(98_010);
        assertThat(XpCurve.levelXp(100, 10, oneHalf)).isEqualTo(9_851);
        assertThat(XpCurve.levelXp(5, 10, two)).isEqualTo(160);
        assertThat(XpCurve.levelXp(10, 10, oneHalf)).isEqualTo(270);
        assertThat(XpCurve.levelXp(10, 10, two)).isEqualTo(810);
        assertThat(XpCurve.levelOf(98_009, 10, two)).isEqualTo(99);
        assertThat(XpCurve.levelOf(98_010, 10, two)).isEqualTo(100);
        // 넘침: 값이 long 을 넘으면 최댓값으로 자르고(SQL 은 오류 없이 bigint 최댓값), 그 레벨에는 경험치 최댓값으로도 닿지 않는다
        assertThat(XpCurve.levelXp(XpCurve.MAX_LEVEL, 1000, three)).isEqualTo(Long.MAX_VALUE);
        assertThat(sqlLevelXp(XpCurve.MAX_LEVEL, 1000, three)).isEqualTo(Long.MAX_VALUE);
        assertThat(XpCurve.levelXp(209_717, 1000, three)).isEqualTo(Long.MAX_VALUE);
        assertThat(XpCurve.levelOf(Long.MAX_VALUE, 1000, three)).isEqualTo(209_716).isEqualTo(sqlXpLevel(Long.MAX_VALUE, 1000, three));
        // 레벨 상한: 지수 1.0·기준값 1 은 레벨 = 경험치 + 1 이라 상한에 닿는다
        assertThat(XpCurve.levelOf(Long.MAX_VALUE, 1, new BigDecimal("1.0"))).isEqualTo(XpCurve.MAX_LEVEL).isEqualTo(sqlXpLevel(Long.MAX_VALUE, 1, new BigDecimal("1.0")));
    }

    @Test
    void 지수는_다섯_값뿐이고_자릿수는_보지_않는다() {
        List<String> allowed = new ArrayList<>();
        XpCurve.EXPONENTS.forEach(e -> allowed.add(e.toPlainString()));
        assertThat(allowed).containsExactly("1.0", "1.5", "2.0", "2.5", "3.0");
        assertThat(XpCurve.DEFAULT_EXPONENT).isEqualByComparingTo("2.0");
        assertThat(XpCurve.isExponent(new BigDecimal("2"))).isTrue();
        assertThat(XpCurve.isExponent(new BigDecimal("2.50"))).isTrue();
        assertThat(XpCurve.isExponent(new BigDecimal("1.7"))).isFalse();
        assertThat(XpCurve.isExponent(new BigDecimal("0.5"))).isFalse();
        assertThat(XpCurve.isExponent(new BigDecimal("3.5"))).isFalse();
        assertThat(XpCurve.isExponent(null)).isFalse();
        assertThat(XpCurve.exponentOrDefault(new BigDecimal("3")).toPlainString()).isEqualTo("3.0");
        assertThat(XpCurve.exponentOrDefault(new BigDecimal("1.7")).toPlainString()).isEqualTo("2.0");
        assertThat(XpCurve.exponentOrDefault(null).toPlainString()).isEqualTo("2.0");
        // 받지 않는 지수는 기본 곡선으로 센다(자바) — 자릿수가 달라도 같은 값
        assertThat(XpCurve.levelXp(100, 10, new BigDecimal("1.7"))).isEqualTo(98_010);
        assertThat(XpCurve.levelXp(100, 10, new BigDecimal("2"))).isEqualTo(98_010);
        assertThat(XpCurve.Shape.of(0, null)).isEqualTo(XpCurve.Shape.DEFAULT);
        assertThat(XpCurve.Shape.of(10, new BigDecimal("2")).sameAs(XpCurve.Shape.DEFAULT)).isTrue();
        assertThat(XpCurve.Shape.of(10, new BigDecimal("1.5")).sameAs(XpCurve.Shape.DEFAULT)).isFalse();
    }

    @Test
    void SQL_함수는_받지_않는_지수를_거절한다() {
        assertThatThrownBy(() -> sqlLevelXp(10, 10, new BigDecimal("1.7"))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> sqlXpLevel(100, 10, new BigDecimal("0.5"))).isInstanceOf(SQLException.class);
    }
}
