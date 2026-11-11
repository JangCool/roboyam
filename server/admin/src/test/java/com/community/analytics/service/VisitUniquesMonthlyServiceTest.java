package com.community.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.dto.VisitAnalyticsAdmin.MonthlyPoint;
import com.community.analytics.dto.VisitAnalyticsAdmin.UniquePeriod;
import com.community.analytics.dto.VisitAnalyticsAdmin.UniquesAudience;
import com.community.analytics.repository.master.VisitAnalyticsStore;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.FakeMessages;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.FakeTitles;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.MutableClock;
import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.VisitPeriods;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.flyway.SchemaMigration;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 주·월 순방문자({@code GET …/visits/uniques}, {@link VisitUniqueCounts})와 월별 보기({@code GET …/visits/monthly}) 계산(site-analytics-contract 「3단계」 결정 ④ ·
 * 「결정 ④ 화면 확정」) — 실제 PostgreSQL(analytics 스트림), 시험마다 연결 하나의 트랜잭션으로 넣고 되돌린다.
 *
 * <p>{@code VisitAnalyticsAdminServiceTest} 와 같은 준비(고정 시계 · 흉내 낸 이름 붙이기·언어). 날짜는 다른 시험과 겹치지 않는 먼 미래(2087년, 지금 = 3월 12일 수요일 10:20 한국).
 * 「세기 시작일」은 숫자 표 전체의 가장 이른 날이라 시험마다 트랜잭션 안에서 숫자 표를 먼저 비운다(되돌림 — 다른 시험이 커밋한 줄이 섞이지 않게).
 */
class VisitUniquesMonthlyServiceTest {

    static final Instant NOW = kst(2087, 3, 12, 10, 20);
    static final StatsCollection ALL_ON = new StatsCollection(true, true, true, 3, true, true, false, false);
    static final String V = VisitPeriods.VISITOR;
    static final String M = VisitPeriods.MEMBER;
    static final String W = VisitPeriods.WEEK;
    static final String MO = VisitPeriods.MONTH;

    private static DataSource base;
    private Connection connection;
    private JdbcTemplate jdbc;
    private VisitAnalyticsStore store;
    private FakeTitles titles;
    private volatile StatsCollection current = ALL_ON;
    private StatsCollectionSettings settings;
    private MutableClock clock;

    @BeforeAll
    static void tables() {
        base = CommerceTestDatabase.migrated("admin");
        new SchemaMigration("filesystem:../api/src/main/resources/db/migration/").migrateStream(base, SchemaMigration.ANALYTICS);
    }

    @BeforeEach
    void open() throws Exception {
        connection = base.getConnection();
        connection.setAutoCommit(false);
        SingleConnectionDataSource single = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(single);
        store = new VisitAnalyticsStore(new AnalyticsDatabase(single, false));
        titles = new FakeTitles();
        settings = new StatsCollectionSettings(null, null) {
            @Override
            public StatsCollection current() {
                return current;
            }
        };
        clock = new MutableClock(NOW);
        jdbc.update("DELETE FROM analytics_unique_counts");
    }

    @AfterEach
    void close() throws Exception {
        connection.rollback();
        connection.close();
    }

    // ── 준비 ──────────────────────────────────────────────────────────────────

    static Instant kst(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(ServiceTime.ZONE).toInstant();
    }

    static LocalDate day(int y, int m, int d) {
        return LocalDate.of(y, m, d);
    }

    private VisitAnalyticsAdminService service() {
        return new VisitAnalyticsAdminService(store, titles, settings, new FakeMessages(), clock);
    }

    /** 숫자 표 한 칸 — 기간 시작은 그 날이 든 주·달 */
    private void counted(String audience, String kind, LocalDate day, long added) {
        LocalDate start = W.equals(kind) ? VisitPeriods.weekStart(day) : VisitPeriods.monthStart(day);
        jdbc.update("INSERT INTO analytics_unique_counts (audience, period_kind, period_start, day, added) VALUES (?, ?, ?, ?, ?)", audience, kind, Date.valueOf(start),
                Date.valueOf(day), added);
    }

    private void hour(Instant hour, long visitors, long newVisitors, long views) {
        jdbc.update("INSERT INTO analytics_visit_hourly (hour, visitors, new_visitors, page_views) VALUES (?, ?, ?, ?)", Timestamp.from(hour), visitors, newVisitors, views);
    }

    private void rolled(LocalDate month, long visitorsDailySum, long newVisitors, long views) {
        jdbc.update("INSERT INTO analytics_monthly_rollups (month, rolled_at) VALUES (?, now())", Date.valueOf(month));
        monthlyRow(month, visitorsDailySum, newVisitors, views);
    }

    /** 월별 표 줄만(합친 기록 없이) */
    private void monthlyRow(LocalDate month, long visitorsDailySum, long newVisitors, long views) {
        jdbc.update("INSERT INTO analytics_visit_monthly (month, visitors_daily_sum, new_visitors, page_views) VALUES (?, ?, ?, ?)", Date.valueOf(month), visitorsDailySum,
                newVisitors, views);
    }

    private static Map<String, ?> fieldsOf(Runnable call) {
        try {
            call.run();
        } catch (ValidationException e) {
            return e.getFields();
        }
        throw new AssertionError("ValidationException 이 나야 한다");
    }

    private static List<Long> counts(List<UniquePeriod> periods) {
        return periods.stream().map(UniquePeriod::count).toList();
    }

    // ── 1. uniques — 같은 시점 자르기 ─────────────────────────────────────────

    @Test
    void 수요일이면_지난_주는_월_수까지_달은_지난달_같은_날까지_자르고_방문자와_회원을_따로_센다() {
        assertThat(ServiceTime.dateOf(NOW).getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        // 방문자 — 세기 시작 2086-12-15
        counted(V, MO, day(2086, 12, 15), 1);
        counted(V, W, day(2087, 3, 3), 5); // 지난 주 월
        counted(V, W, day(2087, 3, 5), 3); // 지난 주 수 — 같은 시점(넣는다)
        counted(V, W, day(2087, 3, 6), 100); // 지난 주 목 — 같은 시점 뒤
        counted(V, W, day(2087, 3, 9), 7); // 지난 주 일
        counted(V, W, day(2087, 3, 10), 4); // 이번 주 월
        counted(V, W, day(2087, 3, 11), 2);
        counted(V, W, day(2087, 3, 12), 1);
        counted(V, MO, day(2087, 2, 1), 10);
        counted(V, MO, day(2087, 2, 12), 5); // 지난달 같은 날 — 넣는다
        counted(V, MO, day(2087, 2, 13), 50);
        counted(V, MO, day(2087, 3, 1), 20);
        counted(V, MO, day(2087, 3, 12), 1);
        // 회원 — 세기 시작 2087-02-20
        counted(M, MO, day(2087, 2, 20), 3); // 지난달 같은 날 뒤뿐 — 같은 시점까지 0(null 아님)
        counted(M, W, day(2087, 3, 4), 1); // 지난 주 화
        counted(M, W, day(2087, 3, 11), 2);
        counted(M, MO, day(2087, 3, 10), 2);

        VisitAnalyticsAdmin.Uniques u = service().uniques();

        assertThat(u.asOf()).isEqualTo(NOW);
        assertThat(u.collect().visits()).isTrue();
        assertThat(u.audiences().keySet()).containsExactly(V, M);
        UniquesAudience v = u.audiences().get(V);
        assertThat(v.week().current()).isEqualTo(new UniquePeriod("2087-03-10", "2087-03-16", 7L, true));
        assertThat(v.week().previous()).isEqualTo(new UniquePeriod("2087-03-03", "2087-03-09", 115L, false));
        assertThat(v.week().previousSamePoint()).as("지난 주 월~수").isEqualTo(8L);
        assertThat(v.month().current()).isEqualTo(new UniquePeriod("2087-03-01", "2087-03-31", 21L, true));
        assertThat(v.month().previous()).isEqualTo(new UniquePeriod("2087-02-01", "2087-02-28", 65L, false));
        assertThat(v.month().previousSamePoint()).as("2월 1~12일").isEqualTo(15L);

        UniquesAudience m = u.audiences().get(M);
        assertThat(m.week().current().count()).isEqualTo(2L);
        assertThat(m.week().previous().count()).isEqualTo(1L);
        assertThat(m.week().previousSamePoint()).isEqualTo(1L);
        assertThat(m.month().current().count()).isEqualTo(2L);
        assertThat(m.month().previous().count()).isEqualTo(3L);
        assertThat(m.month().previousSamePoint()).as("지난달은 세었지만 같은 날까지는 없다 — 0").isEqualTo(0L);
    }

    @Test
    void 열두_주와_열두_달은_오래된_차례이고_마지막만_partial_세기_시작_전은_null_뒤의_빈_기간은_0() {
        counted(V, MO, day(2086, 12, 15), 1);
        counted(V, W, day(2087, 3, 4), 3);
        counted(V, MO, day(2087, 2, 12), 5);
        counted(M, MO, day(2087, 2, 20), 3);

        UniquesAudience v = service().uniques().audiences().get(V);

        assertThat(v.weeks()).hasSize(12);
        assertThat(v.weeks().get(0)).isEqualTo(new UniquePeriod("2086-12-23", "2086-12-29", 0L, false));
        assertThat(v.weeks().get(11)).isEqualTo(new UniquePeriod("2087-03-10", "2087-03-16", 0L, true));
        assertThat(v.weeks()).extracting(UniquePeriod::start).isSorted();
        assertThat(v.weeks().subList(0, 11)).allSatisfy(p -> assertThat(p.partial()).isFalse());
        assertThat(counts(v.weeks())).as("세기 시작(12월 15일) 뒤의 빈 주는 0").containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 3L, 0L);
        assertThat(v.months()).extracting(UniquePeriod::start).containsExactly("2086-04-01", "2086-05-01", "2086-06-01", "2086-07-01", "2086-08-01", "2086-09-01",
                "2086-10-01", "2086-11-01", "2086-12-01", "2087-01-01", "2087-02-01", "2087-03-01");
        assertThat(v.months().get(1).end()).isEqualTo("2086-05-31");
        assertThat(counts(v.months())).as("12월 앞 = 집계 전 null, 12월 1, 1월 0, 2월 5, 3월 0")
                .containsExactly(null, null, null, null, null, null, null, null, 1L, 0L, 5L, 0L);
        assertThat(v.months().get(11).partial()).isTrue();

        UniquesAudience m = service().uniques().audiences().get(M);
        // 회원 세기 시작 2월 20일(금) — 그 날이 든 주(2월 17~23일)부터 0, 그 앞 주는 null
        assertThat(m.weeks().get(8)).isEqualTo(new UniquePeriod("2087-02-17", "2087-02-23", 0L, false));
        assertThat(m.weeks().get(7).count()).isNull();
        assertThat(counts(m.months()).subList(9, 12)).containsExactly(null, 3L, 0L);
        assertThat(m.week().previousSamePoint()).as("지난 주는 세기 시작 뒤 빈 주 — 0").isEqualTo(0L);
    }

    @Test
    void 지난_기간이_세기_시작_전이면_previousSamePoint_는_null() {
        counted(V, W, day(2087, 3, 11), 4); // 세기 시작 = 이번 주 화요일
        counted(V, MO, day(2087, 3, 11), 4);

        UniquesAudience v = service().uniques().audiences().get(V);

        assertThat(v.week().current().count()).isEqualTo(4L);
        assertThat(v.week().previous().count()).isNull();
        assertThat(v.week().previousSamePoint()).isNull();
        assertThat(v.month().previous().count()).isNull();
        assertThat(v.month().previousSamePoint()).isNull();
        UniquesAudience m = service().uniques().audiences().get(M);
        assertThat(m.week().current().count()).as("회원은 아직 한 번도 세지 않았다 — 이번 기간도 null").isNull();
        assertThat(m.month().previousSamePoint()).isNull();
    }

    @Test
    void 삼월_31일이면_지난달은_2월_말일까지_월요일이면_지난_주는_월요일_하루() {
        clock.now = kst(2087, 3, 31, 9, 0);
        assertThat(ServiceTime.dateOf(clock.now).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        counted(V, MO, day(2087, 1, 5), 1); // 세기 시작
        counted(V, MO, day(2087, 2, 27), 3);
        counted(V, MO, day(2087, 2, 28), 4);
        counted(V, W, day(2087, 3, 24), 2); // 지난 주 월
        counted(V, W, day(2087, 3, 25), 9); // 지난 주 화 — 같은 시점 뒤

        UniquesAudience v = service().uniques().audiences().get(V);

        assertThat(v.month().previous()).isEqualTo(new UniquePeriod("2087-02-01", "2087-02-28", 7L, false));
        assertThat(v.month().previousSamePoint()).as("31일 → 2월 말일(28일)까지 — 지난달 전체").isEqualTo(7L);
        assertThat(v.week().previous().count()).isEqualTo(11L);
        assertThat(v.week().previousSamePoint()).isEqualTo(2L);

        // 윤년 — 2088년 3월 30일이면 2월 29일까지
        clock.now = kst(2088, 3, 30, 9, 0);
        counted(V, MO, day(2088, 2, 28), 1);
        counted(V, MO, day(2088, 2, 29), 6);
        UniquesAudience leap = service().uniques().audiences().get(V);
        assertThat(leap.month().previous().end()).isEqualTo("2088-02-29");
        assertThat(leap.month().previousSamePoint()).isEqualTo(7L);
    }

    @Test
    void 방문자_수집이_꺼져_있으면_모든_수가_null_이고_칸_모양은_그대로() {
        counted(V, W, day(2087, 3, 10), 4);
        counted(M, MO, day(2087, 3, 10), 2);
        current = new StatsCollection(true, false, true, 3, true, true, false, false); // visits 만 꺼짐

        VisitAnalyticsAdmin.Uniques u = service().uniques();

        assertThat(u.collect().visits()).isFalse();
        for (String audience : List.of(V, M)) {
            UniquesAudience a = u.audiences().get(audience);
            assertThat(a.weeks()).hasSize(12).allSatisfy(p -> assertThat(p.count()).isNull());
            assertThat(a.months()).hasSize(12).allSatisfy(p -> assertThat(p.count()).isNull());
            assertThat(a.week().current().count()).isNull();
            assertThat(a.week().previousSamePoint()).isNull();
            assertThat(a.month().previousSamePoint()).isNull();
            assertThat(a.week().current().partial()).isTrue();
        }
    }

    @Test
    void 이번_주_이번_달_한_줄은_uniques_의_이번_기간과_같다() {
        counted(V, W, day(2087, 3, 10), 4);
        counted(V, MO, day(2087, 3, 2), 9);
        VisitUniqueCounts counts = new VisitUniqueCounts(store);
        VisitUniqueCounts.Current c = counts.current(V, day(2087, 3, 12));
        UniquesAudience a = counts.audiences(day(2087, 3, 12), true).get(V);
        assertThat(c).isEqualTo(new VisitUniqueCounts.Current(a.week().current().count(), a.month().current().count()));
        assertThat(c).isEqualTo(new VisitUniqueCounts.Current(4L, 9L));
        assertThat(counts.current(M, day(2087, 3, 12))).as("회원 줄이 없다 — 집계 전").isEqualTo(new VisitUniqueCounts.Current(null, null));
    }

    // ── 2. monthly ───────────────────────────────────────────────────────────

    @Test
    void 합친_달은_월별_표에서_안_합친_달은_시간별_일별에서_채우고_월_순방문자는_숫자_표에서() {
        // 12월: 합침(월별 표) — 시간별 줄이 남아 있어도 읽지 않는다
        rolled(day(2086, 12, 1), 100, 10, 1000);
        hour(kst(2086, 12, 5, 9, 0), 999, 99, 9999);
        // 1월: 합친 기록 없음(크론이 아직 못 합침) — 월별 표 줄이 있어도 시간별에서
        monthlyRow(day(2087, 1, 1), 5555, 555, 55555);
        hour(kst(2087, 1, 1, 0, 0), 10, 1, 100); // UTC 로는 12월 31일 15시 — 한국 1월
        hour(kst(2087, 1, 31, 23, 0), 20, 2, 200);
        // 2월: 합침(오늘 12일 — 1~2일이 아니라 다시 합치는 중이 아니다)
        rolled(day(2087, 2, 1), 200, 20, 2000);
        hour(kst(2087, 2, 28, 23, 0), 999, 99, 9999);
        // 3월: 이번 달 — 합친 기록이 있어도 시간별에서
        rolled(day(2087, 3, 1), 8888, 888, 88888);
        hour(kst(2087, 3, 1, 0, 0), 3, 1, 30); // UTC 로는 2월 28일 15시 — 한국 3월
        hour(kst(2087, 3, 12, 10, 0), 4, 0, 40);
        // 페이지 — 합친 달은 page_monthly, 안 합친 달은 page_daily
        jdbc.update("INSERT INTO analytics_page_monthly (month, path, kind, views) VALUES ('2086-12-01', '/', 'HOME', 50), ('2087-02-01', '/', 'HOME', 30), "
                + "('2087-02-01', '/boards/free', 'BOARD', 40), ('2087-01-01', '/ghost', 'OTHER', 9999)");
        jdbc.update("INSERT INTO analytics_page_daily (day, path, kind, views) VALUES ('2087-01-10', '/boards/free', 'BOARD', 15), ('2087-03-05', '/', 'HOME', 5), "
                + "('2087-02-10', '/ghost', 'OTHER', 9999), ('2086-12-10', '/ghost', 'OTHER', 9999)");
        // 유입 — 같은 규칙
        jdbc.update("INSERT INTO analytics_referrer_monthly (month, referrer_group, referrer_domain, visits) VALUES ('2087-02-01', 'SEARCH', 'www.google.com', 10), "
                + "('2087-01-01', 'SOCIAL', 't.co', 9999)");
        jdbc.update("INSERT INTO analytics_referrer_daily (day, referrer_group, referrer_domain, visits) VALUES ('2087-03-05', 'DIRECT', '', 5), "
                + "('2087-01-20', 'SEARCH', 'search.naver.com', 2), ('2087-02-05', 'SOCIAL', 't.co', 9999)");
        // 월 순방문자 — 방문자 세기 시작 1월 3일, 회원 2월 1일
        counted(V, MO, day(2087, 1, 3), 25);
        counted(V, MO, day(2087, 2, 1), 150);
        counted(V, MO, day(2087, 3, 1), 6);
        counted(M, MO, day(2087, 2, 1), 12);
        counted(M, MO, day(2087, 3, 2), 2);
        titles.names.put("/", new com.community.dashboard.repository.master.DashboardAnalyticsStore.Name("홈", null));

        VisitAnalyticsAdmin.Monthly r = service().monthly("2086-12", "2087-03");

        assertThat(r.range()).isEqualTo(new VisitAnalyticsAdmin.MonthRange("2086-12", "2087-03"));
        assertThat(r.months()).containsExactly(
                new MonthlyPoint("2086-12", 100, 10, 1000, null, null, false),
                new MonthlyPoint("2087-01", 30, 3, 300, 25L, null, false),
                new MonthlyPoint("2087-02", 200, 20, 2000, 150L, 12L, false),
                new MonthlyPoint("2087-03", 7, 1, 70, 6L, 2L, true));
        assertThat(r.totals()).isEqualTo(new VisitAnalyticsAdmin.MonthlyTotals(337, 34, 3370));
        assertThat(r.pages()).extracting(VisitAnalyticsAdmin.PageRow::path).containsExactly("/", "/boards/free");
        assertThat(r.pages()).extracting(VisitAnalyticsAdmin.PageRow::views).containsExactly(85L, 55L);
        assertThat(r.pagesTotalViews()).isEqualTo(140);
        assertThat(r.referrers().total()).isEqualTo(17);
        assertThat(r.referrers().groups()).filteredOn(g -> g.visits() > 0).extracting(VisitAnalyticsAdmin.GroupCount::group)
                .containsExactlyInAnyOrder("SEARCH", "DIRECT");
    }

    @Test
    void 달_1_2일에는_지난달을_합쳤어도_시간별에서_채우고_3일부터는_월별_표에서() {
        rolled(day(2087, 2, 1), 200, 20, 2000);
        hour(kst(2087, 2, 10, 9, 0), 40, 4, 400);

        clock.now = kst(2087, 3, 2, 23, 0);
        MonthlyPoint feb = service().monthly("2087-02", "2087-03").months().get(0);
        assertThat(feb.visitorsDailySum()).as("2일 — 크론이 덮어써 다시 합치는 중이라 시간별").isEqualTo(40);
        assertThat(feb.partial()).isFalse();

        clock.now = kst(2087, 3, 3, 0, 30);
        assertThat(service().monthly("2087-02", "2087-03").months().get(0).visitorsDailySum()).isEqualTo(200);
    }

    @Test
    void 수집_스위치가_꺼져도_쌓인_월별_값과_월_순방문자를_준다() {
        rolled(day(2087, 2, 1), 200, 20, 2000);
        counted(V, MO, day(2087, 2, 3), 150);
        counted(M, MO, day(2087, 2, 3), 12);
        current = new StatsCollection(false, false, false, 2, false, false, false, false);

        VisitAnalyticsAdmin.Monthly r = service().monthly("2087-02", "2087-03");

        assertThat(r.collect().enabled()).isFalse();
        assertThat(r.collect().visits()).isFalse();
        assertThat(r.months().get(0)).isEqualTo(new MonthlyPoint("2087-02", 200, 20, 2000, 150L, 12L, false));
        assertThat(r.months().get(1).uniqueVisitors()).as("이번 달 — 세기 시작 뒤 빈 달 0").isEqualTo(0L);
    }

    @Test
    void 기간이_틀리면_400_fields_from_이번_달_뒤면_fields_to() {
        VisitAnalyticsAdminService svc = service();
        for (String[] c : new String[][] {{null, "2087-03"}, {"2087-01", null}, {"", "2087-03"}, {" ", "2087-03"}, {"2087/01", "2087-03"}, {"2087-1", "2087-03"},
                {"2087-13", "2087-03"}, {"2087-01-01", "2087-03"}, {"2087-03", "2087-02"}, {"2084-03", "2087-03"}}) {
            assertThat(fieldsOf(() -> svc.monthly(c[0], c[1]))).as("%s ~ %s", c[0], c[1]).containsOnlyKeys("from");
        }
        assertThat(fieldsOf(() -> svc.monthly("2087-03", "2087-04"))).containsOnlyKeys("to");
        assertThat(fieldsOf(() -> svc.monthly("2087-05", "2087-04"))).as("from > to 가 먼저").containsOnlyKeys("from");
        assertThat(svc.monthly("2084-04", "2087-03").months()).as("36개월은 된다").hasSize(36);
        assertThat(svc.monthly(" 2087-03 ", "2087-03").months()).as("앞뒤 공백은 뗀다").hasSize(1);
    }
}
