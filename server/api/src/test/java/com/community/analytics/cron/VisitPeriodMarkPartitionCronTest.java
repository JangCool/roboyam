package com.community.analytics.cron;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.store.VisitPeriods;
import com.community.testsupport.CommerceTestDatabase;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DB 크론 {@code R__analytics_16}({@code batch_analytics_period_mark_partitions} — 주·월 순방문자 표시 조각, site-analytics-contract 「3단계」 결정 ④·⑥)을 실제 PostgreSQL 에서 부른다.
 *
 * <p>지우기 시험은 <b>지난 날짜(2020년)</b> 조각을 쓴다 — 지우는 함수는 「기간 끝 + 여유 ≤ 오늘」인 조각을 모두 지우므로, 먼 미래 날짜를 오늘로 주면 다른 시험의 조각까지 지운다.
 * 2020년 조각은 다른 시험이 만들지 않고, 크론의 오늘(실제 날짜)로도 이미 지울 대상이다. 시험이 만든 조각은 끝나면 지운다.
 */
class VisitPeriodMarkPartitionCronTest {

    private static final List<String> WEEK_TABLES = List.of("analytics_visitor_week_marks", "analytics_member_week_marks");
    private static final List<String> MONTH_TABLES = List.of("analytics_visitor_month_marks", "analytics_member_month_marks");
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated("api", "analytics");
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void dropOld() {
        for (String t : WEEK_TABLES) {
            jdbc.execute("DROP TABLE IF EXISTS " + t + "_20200106");
            jdbc.execute("DROP TABLE IF EXISTS " + t + "_20200309");
        }
        for (String t : MONTH_TABLES) {
            jdbc.execute("DROP TABLE IF EXISTS " + t + "_202001");
            jdbc.execute("DROP TABLE IF EXISTS " + t + "_202003");
        }
        jdbc.update("DELETE FROM analytics_unique_counts WHERE period_start >= '2020-01-01' AND period_start < '2020-04-01'");
    }

    private static boolean exists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
    }

    private static int ensure(String day) {
        return jdbc.queryForObject("SELECT analytics_ensure_period_mark_partitions(ARRAY[?]::date[])", Integer.class, Date.valueOf(day));
    }

    private static int dropOld(String today) {
        return jdbc.queryForObject("SELECT analytics_drop_old_period_mark_partitions(?, 3)", Integer.class, Date.valueOf(today));
    }

    /** 마지막 실행 [상태, 처리 건수, 문구 인자, 오류] */
    private static List<String> lastRun() {
        return jdbc.queryForObject("SELECT status, affected_count, coalesce(message_args, ''), coalesce(error_message, '') FROM batch_runs "
                        + "WHERE job_key = 'analytics-period-mark-partitions' ORDER BY started_at DESC, id DESC LIMIT 1",
                (rs, i) -> List.of(rs.getString(1), String.valueOf(rs.getObject(2)), rs.getString(3), rs.getString(4)));
    }

    /** 실제 오늘(한국 날짜 — 크론과 같은 식) */
    private static LocalDate today() {
        return jdbc.queryForObject("SELECT (now() AT TIME ZONE 'Asia/Seoul')::date", Date.class).toLocalDate();
    }

    @Test
    void 조각_만들기는_그_날이_든_주와_달_조각을_네_표에_한_번만_만든다() {
        int created = ensure("2020-01-08"); // 수요일 — 주 = 1월 6일(월) 시작
        assertThat(created).as("주 2표 + 달 2표(오늘·다음 기간 조각은 이미 있을 수 있어 4 이상)").isGreaterThanOrEqualTo(4);
        for (String t : WEEK_TABLES) {
            assertThat(exists(t + "_20200106")).isTrue();
        }
        for (String t : MONTH_TABLES) {
            assertThat(exists(t + "_202001")).isTrue();
        }
        assertThat(ensure("2020-01-12")).as("같은 주(일요일)·같은 달 — 이미 있다").isZero();
        // 조각의 범위 — 주는 [월, 다음 월), 달은 [1일, 다음 달 1일)
        jdbc.update("INSERT INTO analytics_visitor_week_marks (period_start, visitor_id) VALUES ('2020-01-06', ?)", UUID.randomUUID());
        jdbc.update("INSERT INTO analytics_member_month_marks (period_start, member_id) VALUES ('2020-01-01', 1)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_visitor_week_marks_20200106", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_member_month_marks_202001", Long.class)).isEqualTo(1);
    }

    @Test
    void 지난_조각은_기간_끝_더하기_3일이_된_날_지우고_그_전날은_남긴다() {
        ensure("2020-01-08");
        // 주 1월 6일~12일 — 끝(다음 기간 첫날) 13일 + 3일 = 16일부터 지운다
        assertThat(dropOld("2020-01-15")).isZero();
        assertThat(exists("analytics_visitor_week_marks_20200106")).isTrue();
        assertThat(dropOld("2020-01-16")).isEqualTo(2);
        for (String t : WEEK_TABLES) {
            assertThat(exists(t + "_20200106")).isFalse();
        }
        for (String t : MONTH_TABLES) {
            assertThat(exists(t + "_202001")).as("달은 아직").isTrue();
        }
        // 달 1월 — 끝 2월 1일 + 3일 = 2월 4일부터
        assertThat(dropOld("2020-02-03")).isZero();
        assertThat(dropOld("2020-02-04")).isEqualTo(2);
        for (String t : MONTH_TABLES) {
            assertThat(exists(t + "_202001")).isFalse();
        }
    }

    @Test
    void 크론은_이번_다음_기간_조각을_만들고_끝난_지난_조각을_지우며_숫자_표는_남긴다() {
        ensure("2020-03-11");
        jdbc.update("INSERT INTO analytics_visitor_week_marks (period_start, visitor_id) VALUES ('2020-03-09', ?)", UUID.randomUUID());
        jdbc.update("INSERT INTO analytics_unique_counts (audience, period_kind, period_start, day, added) VALUES ('VISITOR', 'WEEK', '2020-03-09', '2020-03-11', 1), "
                + "('VISITOR', 'MONTH', '2020-03-01', '2020-03-11', 1)");

        jdbc.execute("CALL batch_analytics_period_mark_partitions('MANUAL', NULL, NULL)");

        for (String t : WEEK_TABLES) {
            assertThat(exists(t + "_20200309")).as("끝난 지 오래된 주 조각은 지운다 — %s", t).isFalse();
        }
        for (String t : MONTH_TABLES) {
            assertThat(exists(t + "_202003")).isFalse();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_unique_counts WHERE period_start IN ('2020-03-09', '2020-03-01') AND day = '2020-03-11'",
                Long.class)).as("숫자 표는 기한 없이 남는다").isEqualTo(2);
        LocalDate today = today();
        LocalDate week = VisitPeriods.weekStart(today);
        LocalDate month = VisitPeriods.monthStart(today);
        for (LocalDate w : List.of(week, week.plusWeeks(1))) {
            for (String t : WEEK_TABLES) {
                assertThat(exists(t + "_" + w.toString().replace("-", ""))).as("이번·다음 주 조각 %s %s", t, w).isTrue();
            }
        }
        for (LocalDate m : List.of(month, month.plusMonths(1))) {
            for (String t : MONTH_TABLES) {
                assertThat(exists(t + "_" + m.toString().substring(0, 7).replace("-", ""))).as("이번·다음 달 조각 %s %s", t, m).isTrue();
            }
        }
        List<String> run = lastRun();
        assertThat(run.get(0)).isEqualTo("OK");
        assertThat(Long.parseLong(run.get(1))).as("지운 조각 4 이상(만든 조각 + 지운 조각)").isGreaterThanOrEqualTo(4);
        assertThat(run.get(3)).isEmpty();

        // 다시 돌리면 할 일이 없다(지운 조각이 없고 이번·다음 기간은 이미 있다)
        jdbc.execute("CALL batch_analytics_period_mark_partitions('MANUAL', NULL, NULL)");
        assertThat(lastRun().subList(0, 2)).containsExactly("SKIP", "0");
    }
}
