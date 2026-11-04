package com.community.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.dto.VisitAnalyticsAdmin.FlowEnd;
import com.community.analytics.dto.VisitAnalyticsAdmin.GroupCount;
import com.community.analytics.dto.VisitAnalyticsAdmin.PageRow;
import com.community.analytics.dto.VisitAnalyticsAdmin.TimeCell;
import com.community.analytics.repository.master.VisitAnalyticsStore;
import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.dashboard.repository.master.DashboardAnalyticsStore.Name;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.flyway.SchemaMigration;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 「방문자 통계」 관리자 API 7개의 계산(site-analytics-contract 「「방문자 통계」 화면」) — 실제 PostgreSQL(analytics 스트림), 시험마다 연결 하나의 트랜잭션으로 넣고 되돌린다.
 *
 * <p>시험은 서비스 생성자로 고정 시계를 준다(결과 캐시도 그 시계를 쓴다) — 「오늘」·정시·자정 직후를 정확히 맞추려고.
 * 시각은 다른 시험과 겹치지 않는 먼 미래(2097년 3월, 지금 = 3월 13일 10:20:30 한국). 게시판·글 이름은 {@link VisitPageTitles} 를 흉내 낸다(이름 붙이기는 대시보드 시험의 몫).
 */
class VisitAnalyticsAdminServiceTest {

    static final ZoneId KST = ServiceTime.ZONE;
    static final Instant NOW = kst(2097, 3, 13, 10, 20, 30);
    static final StatsCollection ALL_ON = new StatsCollection(true, true, true, 90, true, true, false, false);

    private static DataSource base;
    private Connection connection;
    private JdbcTemplate jdbc;
    private VisitAnalyticsStore store;
    private FakeTitles titles;
    private FakeMessages messages;
    private volatile StatsCollection current = ALL_ON;
    private StatsCollectionSettings settings;
    private MutableClock clock;
    private VisitResultCache cache;
    private long ids = 9_700_000_000L;

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
        messages = new FakeMessages();
        settings = new StatsCollectionSettings(null) {
            @Override
            public StatsCollection current() {
                return current;
            }
        };
        clock = new MutableClock(NOW);
    }

    @AfterEach
    void close() throws Exception {
        connection.rollback();
        connection.close();
    }

    // ── 준비 ──────────────────────────────────────────────────────────────────

    static Instant kst(int y, int m, int d, int h, int min) {
        return kst(y, m, d, h, min, 0);
    }

    static Instant kst(int y, int m, int d, int h, int min, int s) {
        return LocalDateTime.of(y, m, d, h, min, s).atZone(KST).toInstant();
    }

    static LocalDate day(int m, int d) {
        return LocalDate.of(2097, m, d);
    }

    /** 서비스 + 고정 시계(생성자로 준다 — 결과 캐시도 그 시계로 새로 만들어진다) */
    private VisitAnalyticsAdminService service() {
        VisitAnalyticsAdminService svc = new VisitAnalyticsAdminService(store, titles, settings, messages, clock);
        cache = svc.cache();
        return svc;
    }

    private void hour(Instant hour, long visitors, long newVisitors, long views) {
        jdbc.update("INSERT INTO analytics_visit_hourly (hour, visitors, new_visitors, page_views) VALUES (?, ?, ?, ?)", Timestamp.from(hour), visitors, newVisitors, views);
    }

    private void active(String hash, Instant lastSeen) {
        jdbc.update("INSERT INTO analytics_visit_active (visitor_hash, last_seen) VALUES (?, ?) ON CONFLICT (visitor_hash) DO UPDATE SET last_seen = EXCLUDED.last_seen",
                hash, Timestamp.from(lastSeen));
    }

    private void page(LocalDate day, String path, String kind, long views) {
        jdbc.update("INSERT INTO analytics_page_daily (day, path, kind, views) VALUES (?, ?, ?, ?)", Date.valueOf(day), path, kind, views);
    }

    private void referrer(LocalDate day, String group, String domain, long visits) {
        jdbc.update("INSERT INTO analytics_referrer_daily (day, referrer_group, referrer_domain, visits) VALUES (?, ?, ?, ?)", Date.valueOf(day), group, domain, visits);
    }

    private void campaign(LocalDate day, String source, String medium, String name, long visits) {
        jdbc.update("INSERT INTO analytics_campaign_daily (day, utm_source, utm_medium, utm_campaign, visits) VALUES (?, ?, ?, ?, ?)", Date.valueOf(day), source, medium,
                name, visits);
    }

    private void flow(LocalDate day, String from, String to, long moves) {
        jdbc.update("INSERT INTO analytics_flow_daily (day, from_path, to_path, moves) VALUES (?, ?, ?, ?)", Date.valueOf(day), from, to, moves);
    }

    /** 원본 월 조각(2097년 3월, 한국 달 경계) — 트랜잭션과 함께 되돌린다 */
    private void rawPartition() {
        jdbc.execute("CREATE TABLE qa_analytics_page_views_2097_03 PARTITION OF analytics_page_views FOR VALUES FROM ('2097-03-01 00:00:00+09') TO ('2097-04-01 00:00:00+09')");
    }

    private void view(Instant at, String path, String kind, String group) {
        long id = ids++;
        jdbc.update("INSERT INTO analytics_page_views (id, viewed_at, visitor_hash, session_hash, path, kind, referrer_group, device, new_visitor) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PC', false)", id, Timestamp.from(at), "qa-v-" + id, "qa-s-" + id, path, kind, group);
    }

    private static Map<String, ?> fieldsOf(Runnable call) {
        try {
            call.run();
        } catch (ValidationException e) {
            return e.getFields();
        }
        throw new AssertionError("ValidationException 이 나야 한다");
    }

    private static TimeCell cell(VisitAnalyticsAdmin.Time t, int weekday, int hour) {
        TimeCell c = t.cells().get((weekday - 1) * 24 + hour);
        assertThat(c.weekday()).isEqualTo(weekday);
        assertThat(c.hour()).isEqualTo(hour);
        return c;
    }

    private static int weekday(LocalDate d) {
        return d.getDayOfWeek().getValue();
    }

    // ── 1. summary ───────────────────────────────────────────────────────────

    @Test
    void 지난_하루는_시간별_24칸이고_빈_칸은_0_직전은_그_전날_전체() {
        hour(kst(2097, 3, 10, 0, 0), 5, 2, 50);
        hour(kst(2097, 3, 10, 13, 0), 7, 1, 70);
        hour(kst(2097, 3, 11, 0, 0), 100, 9, 1000); // 다음 날 — 안 센다
        hour(kst(2097, 3, 9, 0, 0), 1, 0, 10); // 직전
        hour(kst(2097, 3, 9, 23, 0), 4, 1, 40); // 직전
        hour(kst(2097, 3, 8, 23, 0), 100, 9, 1000); // 그 전 — 안 센다

        VisitAnalyticsAdmin.Summary s = service().summary("2097-03-10", "2097-03-10");

        assertThat(s.granularity()).isEqualTo("HOUR");
        assertThat(s.range()).isEqualTo(new VisitAnalyticsAdmin.Range("2097-03-10", "2097-03-10"));
        assertThat(s.series()).hasSize(24);
        assertThat(s.series().get(0).at()).isEqualTo(kst(2097, 3, 10, 0, 0).toString());
        assertThat(s.series().get(23).at()).isEqualTo(kst(2097, 3, 10, 23, 0).toString());
        assertThat(s.series().get(0)).isEqualTo(new VisitAnalyticsAdmin.SeriesPoint(kst(2097, 3, 10, 0, 0).toString(), 5, 2, 50));
        assertThat(s.series().get(13).visitors()).isEqualTo(7);
        assertThat(s.series().get(1).visitors()).isZero();
        assertThat(s.totals()).isEqualTo(new VisitAnalyticsAdmin.Counts(12, 3, 120));
        assertThat(s.previous()).isEqualTo(new VisitAnalyticsAdmin.PreviousCounts(new VisitAnalyticsAdmin.Range("2097-03-09", "2097-03-09"), 5, 1, 50));
        assertThat(s.asOf()).isEqualTo(NOW);
        assertThat(s.collect()).isEqualTo(new VisitAnalyticsAdmin.Collect(true, true, true, true, true));
        assertThat(s.rawRetentionDays()).isEqualTo(90);
    }

    @Test
    void 여러_날은_한국_날짜로_일별_칸을_묶고_빈_날도_채운다() {
        hour(kst(2097, 3, 3, 23, 0), 2, 1, 20); // UTC 로는 3일 14시 → 3일
        hour(kst(2097, 3, 4, 0, 0), 3, 1, 30); // UTC 로는 3일 15시 → 한국 4일
        hour(kst(2097, 3, 4, 5, 0), 4, 0, 40);
        hour(kst(2097, 3, 7, 23, 0), 1, 1, 10);
        hour(kst(2097, 3, 8, 0, 0), 100, 9, 1000); // 기간 뒤
        hour(kst(2097, 2, 22, 0, 0), 6, 2, 60); // 직전 첫날
        hour(kst(2097, 2, 28, 23, 0), 1, 0, 10); // 직전 끝날 23시
        hour(kst(2097, 2, 21, 23, 0), 100, 9, 1000); // 직전 앞

        VisitAnalyticsAdmin.Summary s = service().summary("2097-03-01", "2097-03-07");

        assertThat(s.granularity()).isEqualTo("DAY");
        assertThat(s.series()).extracting(VisitAnalyticsAdmin.SeriesPoint::at)
                .containsExactly("2097-03-01", "2097-03-02", "2097-03-03", "2097-03-04", "2097-03-05", "2097-03-06", "2097-03-07");
        assertThat(s.series().get(0).visitors()).isZero();
        assertThat(s.series().get(2)).isEqualTo(new VisitAnalyticsAdmin.SeriesPoint("2097-03-03", 2, 1, 20));
        assertThat(s.series().get(3)).isEqualTo(new VisitAnalyticsAdmin.SeriesPoint("2097-03-04", 7, 1, 70));
        assertThat(s.series().get(6)).isEqualTo(new VisitAnalyticsAdmin.SeriesPoint("2097-03-07", 1, 1, 10));
        assertThat(s.totals()).isEqualTo(new VisitAnalyticsAdmin.Counts(10, 3, 100));
        assertThat(s.previous()).isEqualTo(new VisitAnalyticsAdmin.PreviousCounts(new VisitAnalyticsAdmin.Range("2097-02-22", "2097-02-28"), 7, 2, 70));
    }

    @Test
    void 오늘_하루는_지금_시각이_든_칸까지_직전은_어제_같은_시각이_든_칸까지() {
        hour(kst(2097, 3, 13, 0, 0), 3, 1, 30);
        hour(kst(2097, 3, 13, 9, 0), 4, 2, 40);
        hour(kst(2097, 3, 13, 10, 0), 2, 0, 20); // 지금 칸(집계 중) — 요약에는 넣는다(대시보드와 같다)
        hour(kst(2097, 3, 13, 11, 0), 100, 9, 1000); // 미래 칸
        hour(kst(2097, 3, 12, 0, 0), 5, 1, 50);
        hour(kst(2097, 3, 12, 10, 0), 7, 2, 70); // 어제 같은 시각이 든 칸 — 넣는다
        hour(kst(2097, 3, 12, 11, 0), 100, 9, 1000); // 같은 시각 뒤
        hour(kst(2097, 3, 12, 23, 0), 100, 9, 1000);
        active("qa-a1", NOW.minusSeconds(60));
        active("qa-a2", NOW.minusSeconds(300)); // 딱 5분 — 넣는다
        active("qa-a3", NOW.minusSeconds(301));

        VisitAnalyticsAdmin.Summary s = service().summary("2097-03-13", "2097-03-13");

        assertThat(s.granularity()).isEqualTo("HOUR");
        assertThat(s.series()).hasSize(11);
        assertThat(s.series().get(10).at()).isEqualTo(kst(2097, 3, 13, 10, 0).toString());
        assertThat(s.totals()).isEqualTo(new VisitAnalyticsAdmin.Counts(9, 3, 90));
        assertThat(s.previous()).isEqualTo(new VisitAnalyticsAdmin.PreviousCounts(new VisitAnalyticsAdmin.Range("2097-03-12", "2097-03-12"), 12, 3, 120));
        assertThat(s.activeNow()).as("지금 접속 = 5분 안(경계 포함)").isEqualTo(2);
    }

    @Test
    void 오늘이_낀_7일의_직전_기간은_7일_전_같은_시각이_든_칸까지() {
        hour(kst(2097, 3, 7, 0, 0), 2, 0, 20);
        hour(kst(2097, 3, 13, 10, 0), 1, 1, 10);
        hour(kst(2097, 3, 13, 11, 0), 100, 9, 1000); // 미래
        hour(kst(2097, 2, 28, 0, 0), 4, 1, 40); // 직전 첫 칸
        hour(kst(2097, 3, 5, 23, 0), 5, 0, 50); // 직전 가운데 날 끝
        hour(kst(2097, 3, 6, 10, 0), 3, 1, 30); // 직전 끝날 같은 시각 칸 — 넣는다
        hour(kst(2097, 3, 6, 11, 0), 100, 9, 1000); // 같은 시각 뒤 — 뺀다
        hour(kst(2097, 3, 6, 23, 0), 100, 9, 1000);
        hour(kst(2097, 2, 27, 23, 0), 100, 9, 1000); // 직전 앞

        VisitAnalyticsAdmin.Summary s = service().summary("2097-03-07", "2097-03-13");

        assertThat(s.granularity()).isEqualTo("DAY");
        assertThat(s.series()).hasSize(7);
        assertThat(s.series().get(6)).isEqualTo(new VisitAnalyticsAdmin.SeriesPoint("2097-03-13", 1, 1, 10));
        assertThat(s.totals()).isEqualTo(new VisitAnalyticsAdmin.Counts(3, 1, 30));
        assertThat(s.previous()).isEqualTo(new VisitAnalyticsAdmin.PreviousCounts(new VisitAnalyticsAdmin.Range("2097-02-28", "2097-03-06"), 12, 2, 120));
    }

    @Test
    void 자정_직후에는_오늘_0시_칸과_어제_0시_칸만_비교한다() {
        clock.now = kst(2097, 3, 13, 0, 0, 30);
        hour(kst(2097, 3, 13, 0, 0), 1, 1, 10);
        hour(kst(2097, 3, 12, 0, 0), 6, 0, 60);
        hour(kst(2097, 3, 12, 1, 0), 100, 9, 1000);

        VisitAnalyticsAdmin.Summary s = service().summary("2097-03-13", "2097-03-13");

        assertThat(s.series()).hasSize(1);
        assertThat(s.totals().visitors()).isEqualTo(1);
        assertThat(s.previous().visitors()).isEqualTo(6);
        assertThat(s.previous().pageViews()).isEqualTo(60);
    }

    @Test
    void 방문_수집이_꺼지면_합은_0_series_는_빈_배열_collect_는_전체와_항목을_접은_값() {
        hour(kst(2097, 3, 10, 0, 0), 5, 2, 50);
        active("qa-a1", NOW.minusSeconds(10));
        current = new StatsCollection(false, true, true, 180, true, true, true, true); // 전체 꺼짐 — 하위 칸은 켜져 있어도 꺼진 것

        VisitAnalyticsAdmin.Summary s = service().summary("2097-03-10", "2097-03-10");

        assertThat(s.totals()).isEqualTo(new VisitAnalyticsAdmin.Counts(0, 0, 0));
        assertThat(s.previous().visitors()).isZero();
        assertThat(s.previous().range()).isEqualTo(new VisitAnalyticsAdmin.Range("2097-03-09", "2097-03-09"));
        assertThat(s.series()).isEmpty();
        assertThat(s.activeNow()).isZero();
        assertThat(s.collect()).isEqualTo(new VisitAnalyticsAdmin.Collect(false, false, false, false, false));
        assertThat(s.rawRetentionDays()).isEqualTo(180);

        current = new StatsCollection(true, false, true, 90, false, true, false, false); // 방문자만 꺼짐
        VisitAnalyticsAdmin.Summary s2 = service().summary("2097-03-10", "2097-03-10");
        assertThat(s2.totals().visitors()).isZero();
        assertThat(s2.series()).isEmpty();
        assertThat(s2.collect()).isEqualTo(new VisitAnalyticsAdmin.Collect(true, false, true, false, true));
    }

    // ── 2. time ──────────────────────────────────────────────────────────────

    @Test
    void 요일_시간_칸은_한국_시각으로_묶고_168칸_모두_기간의_칸_수를_days_로_준다() {
        hour(kst(2097, 3, 2, 1, 0), 10, 1, 100);
        hour(kst(2097, 3, 9, 1, 0), 5, 1, 50); // 같은 요일·같은 시
        hour(kst(2097, 3, 1, 23, 0), 2, 0, 20); // UTC 로는 1일 14시 — 한국 23시
        hour(kst(2097, 3, 2, 0, 0), 3, 0, 30); // UTC 로는 1일 15시 — 한국 2일(다음 요일) 0시
        hour(kst(2097, 2, 26, 1, 0), 100, 9, 1000); // 기간 앞
        hour(kst(2097, 3, 13, 1, 0), 100, 9, 1000); // 기간 뒤

        VisitAnalyticsAdmin.Time t = service().time("2097-02-27", "2097-03-12"); // 14일 — 요일마다 두 번

        assertThat(t.cells()).hasSize(168);
        assertThat(t.cells()).allSatisfy(c -> assertThat(c.days()).isEqualTo(2));
        TimeCell c = cell(t, weekday(day(3, 2)), 1);
        assertThat(c.visitors()).isEqualTo(15);
        assertThat(c.pageViews()).isEqualTo(150);
        assertThat(cell(t, weekday(day(3, 1)), 23).visitors()).isEqualTo(2);
        assertThat(cell(t, weekday(day(3, 2)), 0).visitors()).isEqualTo(3);
        assertThat(cell(t, weekday(day(3, 1)), 0).visitors()).isZero();
        assertThat(t.cells().stream().mapToLong(TimeCell::visitors).sum()).isEqualTo(20);
        assertThat(t.range()).isEqualTo(new VisitAnalyticsAdmin.Range("2097-02-27", "2097-03-12"));
    }

    @Test
    void 오늘이_끼면_지금_시간과_그_뒤_칸은_값도_days_도_빼고_줄_없는_칸도_days_를_센다() {
        int today = weekday(day(3, 13));
        hour(kst(2097, 3, 13, 9, 0), 4, 1, 40); // 지난 시간 — 넣는다
        hour(kst(2097, 3, 13, 10, 0), 100, 9, 1000); // 지금 시간(집계 중) — 뺀다
        hour(kst(2097, 3, 13, 11, 0), 100, 9, 1000); // 그 뒤 — 뺀다
        hour(kst(2097, 3, 7, 10, 0), 3, 0, 30); // 다른 요일 같은 시

        VisitAnalyticsAdmin.Time t = service().time("2097-03-07", "2097-03-13"); // 7일 — 요일마다 한 번

        assertThat(cell(t, today, 9).visitors()).isEqualTo(4);
        assertThat(cell(t, today, 9).days()).isEqualTo(1);
        assertThat(cell(t, today, 0).days()).as("줄 없는 지난 칸도 센다").isEqualTo(1);
        assertThat(cell(t, today, 10).visitors()).isZero();
        assertThat(cell(t, today, 10).days()).isZero();
        assertThat(cell(t, today, 23).days()).isZero();
        assertThat(cell(t, weekday(day(3, 7)), 10).visitors()).isEqualTo(3);
        assertThat(cell(t, weekday(day(3, 7)), 23).days()).isEqualTo(1);
        assertThat(t.cells().stream().mapToInt(TimeCell::days).sum()).isEqualTo(6 * 24 + 10);
        assertThat(t.cells().stream().mapToLong(TimeCell::visitors).sum()).isEqualTo(7);
    }

    @Test
    void 자정_직후_오늘만_고르면_모든_칸이_0이고_days_도_0() {
        clock.now = kst(2097, 3, 13, 0, 0, 30);
        hour(kst(2097, 3, 13, 0, 0), 5, 1, 50);

        VisitAnalyticsAdmin.Time t = service().time("2097-03-13", "2097-03-13");

        assertThat(t.cells()).hasSize(168).allSatisfy(c -> {
            assertThat(c.days()).isZero();
            assertThat(c.visitors()).isZero();
        });
    }

    @Test
    void 방문_수집이_꺼지면_168칸_모두_0() {
        hour(kst(2097, 3, 10, 5, 0), 5, 1, 50);
        current = new StatsCollection(true, false, true, 90, true, true, false, false);
        VisitAnalyticsAdmin.Time t = service().time("2097-03-10", "2097-03-10");
        assertThat(t.cells()).hasSize(168).allSatisfy(c -> {
            assertThat(c.days()).isZero();
            assertThat(c.visitors()).isZero();
            assertThat(c.pageViews()).isZero();
        });
    }

    // ── 3. pages ─────────────────────────────────────────────────────────────

    private void pageSet() {
        page(day(3, 10), "/", "HOME", 10);
        page(day(3, 11), "/", "HOME", 5);
        page(day(3, 10), "/posts/1", "POST", 20);
        page(day(3, 11), "/posts/2", "POST", 15);
        page(day(3, 10), "/boards/free", "BOARD", 3);
        page(day(3, 11), "/search", "SEARCH", 1);
        page(day(3, 10), "/posts/1", "OTHER", 2); // 같은 경로 다른 종류 — 다른 줄
        page(day(3, 9), "/posts/9", "POST", 999); // 기간 앞
        page(day(3, 12), "/posts/9", "POST", 999); // 기간 뒤
    }

    @Test
    void 페이지_순위는_조회수_차례_같으면_경로_차례이고_total_과_totalViews_는_거른_범위_전체() {
        pageSet();
        titles.names.put("/posts/1", new Name("첫 글", "First post"));
        VisitAnalyticsAdminService svc = service();

        VisitAnalyticsAdmin.PagePage p0 = svc.pages("2097-03-10", "2097-03-11", null, null, "0", "2");
        assertThat(p0.items()).extracting(PageRow::path, PageRow::kind, PageRow::views)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("/posts/1", "POST", 20L), org.assertj.core.groups.Tuple.tuple("/", "HOME", 15L));
        assertThat(p0.items().get(0).title()).isEqualTo("첫 글");
        assertThat(p0.items().get(0).titleEn()).isEqualTo("First post");
        assertThat(p0.items().get(1).title()).isNull();
        assertThat(p0.total()).isEqualTo(6);
        assertThat(p0.totalViews()).isEqualTo(56);

        assertThat(svc.pages("2097-03-10", "2097-03-11", null, null, "1", "2").items()).extracting(PageRow::path).containsExactly("/posts/2", "/boards/free");
        VisitAnalyticsAdmin.PagePage p2 = svc.pages("2097-03-10", "2097-03-11", null, null, "2", "2");
        assertThat(p2.items()).extracting(PageRow::path, PageRow::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("/posts/1", "OTHER"), org.assertj.core.groups.Tuple.tuple("/search", "SEARCH"));
        assertThat(p2.total()).isEqualTo(6);

        VisitAnalyticsAdmin.PagePage empty = svc.pages("2097-03-10", "2097-03-11", null, null, "3", "2");
        assertThat(empty.items()).isEmpty();
        assertThat(empty.total()).as("범위 밖 쪽도 총수를 준다").isEqualTo(6);
        assertThat(empty.totalViews()).isEqualTo(56);

        VisitAnalyticsAdmin.PagePage posts = svc.pages("2097-03-10", "2097-03-11", "POST", null, null, null);
        assertThat(posts.items()).extracting(PageRow::path).containsExactly("/posts/1", "/posts/2");
        assertThat(posts.total()).isEqualTo(2);
        assertThat(posts.totalViews()).isEqualTo(35);

        VisitAnalyticsAdmin.PagePage none = svc.pages("2097-03-01", "2097-03-02", null, null, null, null);
        assertThat(none.items()).isEmpty();
        assertThat(none.total()).isZero();
        assertThat(none.totalViews()).isZero();
    }

    @Test
    void 검색어의_퍼센트_밑줄_역슬래시는_글자_그대로_찾고_대소문자는_무시한다() {
        LocalDate d = day(3, 10);
        page(d, "/search/100%", "SEARCH", 7);
        page(d, "/search/100x", "SEARCH", 6);
        page(d, "/a_b", "OTHER", 5);
        page(d, "/axb", "OTHER", 4);
        page(d, "/Boards/Free", "BOARD", 3);
        page(d, "/x\\y", "OTHER", 2);
        page(d, "/xy", "OTHER", 1);
        VisitAnalyticsAdminService svc = service();

        assertThat(svc.pages("2097-03-10", "2097-03-10", null, "%", null, null).items()).extracting(PageRow::path).containsExactly("/search/100%");
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, "0%", null, null).items()).extracting(PageRow::path).containsExactly("/search/100%");
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, "_", null, null).items()).extracting(PageRow::path).containsExactly("/a_b");
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, " _ ", null, null).items()).as("앞뒤 공백은 뗀다").extracting(PageRow::path).containsExactly("/a_b");
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, "\\", null, null).items()).extracting(PageRow::path).containsExactly("/x\\y");
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, "boards", null, null).items()).extracting(PageRow::path).containsExactly("/Boards/Free");
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, "FREE", null, null).items()).extracting(PageRow::path).containsExactly("/Boards/Free");
        VisitAnalyticsAdmin.PagePage search = svc.pages("2097-03-10", "2097-03-10", null, "100", null, null);
        assertThat(search.items()).extracting(PageRow::path).containsExactly("/search/100%", "/search/100x");
        assertThat(search.total()).isEqualTo(2);
        assertThat(search.totalViews()).isEqualTo(13);
        assertThat(svc.pages("2097-03-10", "2097-03-10", "OTHER", "x", null, null).items()).extracting(PageRow::path).containsExactly("/axb", "/x\\y", "/xy");
    }

    @Test
    void 검색어는_게시판_이름이_맞는_게시판_주소도_고르고_빈_쪽의_총수에도_같이_센다() {
        page(day(3, 10), "/boards/free", "BOARD", 9);
        page(day(3, 10), "/posts/1", "POST", 5);
        titles.boards.put("자유", List.of("/boards/free"));
        VisitAnalyticsAdminService svc = service();

        VisitAnalyticsAdmin.PagePage p = svc.pages("2097-03-10", "2097-03-10", null, "자유", null, null);
        assertThat(p.items()).extracting(PageRow::path).containsExactly("/boards/free");
        assertThat(p.total()).isEqualTo(1);
        VisitAnalyticsAdmin.PagePage beyond = svc.pages("2097-03-10", "2097-03-10", null, "자유", "5", null);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(1);
        assertThat(beyond.totalViews()).isEqualTo(9);
    }

    @Test
    void 쪽_크기는_기본_20_최대_100() {
        for (int i = 0; i <= 100; i++) {
            page(day(3, 10), String.format("/p/%03d", i), "OTHER", i + 1);
        }
        VisitAnalyticsAdminService svc = service();
        VisitAnalyticsAdmin.PagePage def = svc.pages("2097-03-10", "2097-03-10", null, null, null, null);
        assertThat(def.items()).hasSize(20);
        assertThat(def.items().get(0).path()).isEqualTo("/p/100");
        assertThat(def.total()).isEqualTo(101);
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, null, null, "100").items()).hasSize(100);
        assertThat(fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", null, null, null, "101"))).containsOnlyKeys("size");
    }

    @Test
    void 페이지_입력이_틀리면_칸마다_400_이고_q_는_100자까지() {
        VisitAnalyticsAdminService svc = service();
        String q100 = "a".repeat(100);
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, q100, null, null).items()).isEmpty();
        assertThat(fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", null, q100 + "b", null, null))).containsOnlyKeys("q");
        assertThat(fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", null, null, null, "0"))).containsOnlyKeys("size");
        assertThat(fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", null, null, null, "x"))).containsOnlyKeys("size");
        assertThat(fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", null, null, "-1", null))).containsOnlyKeys("page");
        assertThat(fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", null, null, "10001", null))).containsOnlyKeys("page");
        assertThat(fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", "NOPE", null, null, null))).containsOnlyKeys("kind");
        assertThat(fieldsOf(() -> svc.pages("", "2097-03-10", "NOPE", q100 + "b", "-1", "0"))).as("한꺼번에 모은다").containsOnlyKeys("from", "kind", "q", "page", "size");
    }

    @Test
    void 원본_기록이_꺼지면_페이지_순위는_비고_0() {
        pageSet();
        current = new StatsCollection(true, true, false, 90, true, true, false, false);
        VisitAnalyticsAdmin.PagePage p = service().pages("2097-03-10", "2097-03-11", null, null, null, null);
        assertThat(p.items()).isEmpty();
        assertThat(p.total()).isZero();
        assertThat(p.totalViews()).isZero();
    }

    // ── 4. pages/detail · flows ─────────────────────────────────────────────

    private void flowSet() {
        LocalDate d10 = day(3, 10);
        LocalDate d11 = day(3, 11);
        flow(d10, "", "/posts/1", 30); // 바로 들어옴
        flow(d11, "", "/posts/1", 10);
        flow(d10, "/", "/posts/1", 20);
        flow(d10, "/boards/free", "/posts/1", 15);
        flow(d10, "/a", "/posts/1", 5);
        flow(d10, "/b", "/posts/1", 4);
        flow(d10, "/c", "/posts/1", 3);
        flow(d10, "/d", "/posts/1", 2);
        flow(d10, "/posts/1", "/x", 9);
        flow(d10, "/posts/1", "/y", 8);
        flow(d10, "/posts/1", "/z", 7);
        flow(d10, "/posts/1", "/w", 6);
        flow(d10, "/posts/1", "/v", 5);
        flow(d10, "/posts/1", "/u", 4);
        flow(day(3, 9), "/posts/1", "/x", 1000); // 기간 앞
        flow(day(3, 9), "", "/posts/1", 1000);
    }

    @Test
    void 서랍은_일별_조회_빈_날_채움_가장_많이_본_종류_들어온_곳_나간_곳_5개와_전체_합() {
        page(day(3, 10), "/posts/1", "POST", 5);
        page(day(3, 10), "/posts/1", "OTHER", 1);
        page(day(3, 12), "/posts/1", "POST", 7);
        page(day(3, 13), "/posts/1", "POST", 999); // 기간 뒤
        flowSet();
        titles.names.put("/posts/1", new Name("첫 글", "First"));
        titles.names.put("/boards/free", new Name("자유", "Free"));

        VisitAnalyticsAdmin.PageDetail d = service().pageDetail("2097-03-10", "2097-03-12", "/posts/1");

        assertThat(d.path()).isEqualTo("/posts/1");
        assertThat(d.kind()).isEqualTo("POST");
        assertThat(d.title()).isEqualTo("첫 글");
        assertThat(d.views()).isEqualTo(13);
        assertThat(d.days()).containsExactly(new VisitAnalyticsAdmin.DayViews("2097-03-10", 6), new VisitAnalyticsAdmin.DayViews("2097-03-11", 0),
                new VisitAnalyticsAdmin.DayViews("2097-03-12", 7));
        assertThat(d.incoming()).containsExactly(new FlowEnd(null, null, null, 40), new FlowEnd("/", null, null, 20), new FlowEnd("/boards/free", "자유", "Free", 15),
                new FlowEnd("/a", null, null, 5), new FlowEnd("/b", null, null, 4));
        assertThat(d.incomingTotal()).as("5개 밖과 「바로 들어옴」까지 합").isEqualTo(89);
        assertThat(d.outgoing()).extracting(FlowEnd::path).containsExactly("/x", "/y", "/z", "/w", "/v");
        assertThat(d.outgoingTotal()).isEqualTo(39);
    }

    @Test
    void 서랍의_경로에_기록이_없으면_종류는_OTHER_이고_모두_0() {
        VisitAnalyticsAdmin.PageDetail d = service().pageDetail("2097-03-10", "2097-03-12", "/nothing");
        assertThat(d.kind()).isEqualTo("OTHER");
        assertThat(d.views()).isZero();
        assertThat(d.days()).hasSize(3).allSatisfy(x -> assertThat(x.views()).isZero());
        assertThat(d.incoming()).isEmpty();
        assertThat(d.outgoing()).isEmpty();
        assertThat(d.incomingTotal()).isZero();
        assertThat(d.outgoingTotal()).isZero();
    }

    @Test
    void 서랍_일별은_원본_기록_스위치_들어온_곳은_이동_스위치를_따른다() {
        page(day(3, 10), "/posts/1", "POST", 5);
        flowSet();
        current = new StatsCollection(true, true, false, 90, true, true, false, false);
        VisitAnalyticsAdmin.PageDetail rawOff = service().pageDetail("2097-03-10", "2097-03-12", "/posts/1");
        assertThat(rawOff.views()).isZero();
        assertThat(rawOff.kind()).isEqualTo("OTHER");
        assertThat(rawOff.incomingTotal()).isEqualTo(89);

        current = new StatsCollection(true, true, true, 90, true, false, false, false);
        VisitAnalyticsAdmin.PageDetail flowsOff = service().pageDetail("2097-03-10", "2097-03-12", "/posts/1");
        assertThat(flowsOff.views()).isEqualTo(5);
        assertThat(flowsOff.incoming()).isEmpty();
        assertThat(flowsOff.incomingTotal()).isZero();
    }

    @Test
    void 이동_흐름은_path_가_없으면_바로_들어옴까지_센_들어온_이동이_가장_많은_페이지이고_상위_이동에서_바로_들어옴은_뺀다() {
        LocalDate d = day(3, 10);
        flow(d, "", "/landing", 50);
        flow(d, "/a", "/b", 30);
        flow(d, "/c", "/b", 15); // /b 로 들어온 이동 45 < /landing 50
        for (int i = 1; i <= 11; i++) {
            flow(d, "/m" + i, "/n" + i, i);
        }
        page(d, "/b", "GROUP_BUY", 3);
        VisitAnalyticsAdminService svc = service();

        VisitAnalyticsAdmin.Flows f = svc.flows("2097-03-10", "2097-03-10", null);
        assertThat(f.path()).isEqualTo("/landing");
        assertThat(f.kind()).isEqualTo("OTHER");
        assertThat(f.incoming()).containsExactly(new FlowEnd(null, null, null, 50));
        assertThat(f.incomingTotal()).isEqualTo(50);
        assertThat(f.outgoing()).isEmpty();
        assertThat(f.top()).hasSize(10);
        assertThat(f.top().get(0)).isEqualTo(new VisitAnalyticsAdmin.FlowMove("/a", "/b", 30));
        assertThat(f.top().get(1)).isEqualTo(new VisitAnalyticsAdmin.FlowMove("/c", "/b", 15));
        assertThat(f.top()).extracting(VisitAnalyticsAdmin.FlowMove::from).doesNotContain("").doesNotContainNull();

        VisitAnalyticsAdmin.Flows b = svc.flows("2097-03-10", "2097-03-10", "/b");
        assertThat(b.path()).isEqualTo("/b");
        assertThat(b.kind()).isEqualTo("GROUP_BUY");
        assertThat(b.incoming()).extracting(FlowEnd::path).containsExactly("/a", "/c");
        assertThat(b.incomingTotal()).isEqualTo(45);
        assertThat(svc.flows("2097-03-10", "2097-03-10", "  ").path()).as("빈 path 는 없는 것").isEqualTo("/landing");
    }

    @Test
    void 이동_수집이_꺼졌거나_이동이_없으면_기준은_홈이고_모두_빈_값() {
        flow(day(3, 10), "/a", "/b", 30);
        current = new StatsCollection(true, true, true, 90, true, false, false, false);
        VisitAnalyticsAdmin.Flows off = service().flows("2097-03-10", "2097-03-10", null);
        assertThat(off.path()).isEqualTo("/");
        assertThat(off.incoming()).isEmpty();
        assertThat(off.outgoing()).isEmpty();
        assertThat(off.top()).isEmpty();

        current = ALL_ON;
        VisitAnalyticsAdmin.Flows none = service().flows("2097-03-01", "2097-03-01", null);
        assertThat(none.path()).isEqualTo("/");
        assertThat(none.top()).isEmpty();
    }

    @Test
    void 경로_규칙이_틀리면_400_fields_path() {
        VisitAnalyticsAdminService svc = service();
        for (String bad : new String[] {"abc", "//evil.example", "/a b", "/a\tb", "/" + "a".repeat(300)}) {
            assertThat(fieldsOf(() -> svc.pageDetail("2097-03-10", "2097-03-10", bad))).as(bad).containsOnlyKeys("path");
            assertThat(fieldsOf(() -> svc.flows("2097-03-10", "2097-03-10", bad))).as(bad).containsOnlyKeys("path");
        }
        assertThat(fieldsOf(() -> svc.pageDetail("2097-03-10", "2097-03-10", null))).containsOnlyKeys("path");
        assertThat(fieldsOf(() -> svc.pageDetail("2097-03-10", "2097-03-10", " "))).containsOnlyKeys("path");
        assertThat(svc.pageDetail("2097-03-10", "2097-03-10", "/" + "a".repeat(299)).path()).hasSize(300);
    }

    // ── 5. referrers ────────────────────────────────────────────────────────

    private void referrerSet() {
        referrer(day(3, 10), "SEARCH", "www.google.com", 5);
        referrer(day(3, 10), "SEARCH", "search.naver.com", 7);
        referrer(day(3, 10), "DIRECT", "", 13);
        referrer(day(3, 11), "SOCIAL", "t.co", 3);
        referrer(day(3, 12), "SEARCH", "www.google.com", 1);
        referrer(day(3, 9), "OTHER_SITE", "old.example", 99); // 기간 앞
        campaign(day(3, 10), "news", "email", "nov", 4);
        campaign(day(3, 11), "news", "email", "nov", 2);
        campaign(day(3, 11), "", "", "spring", 3);
        campaign(day(3, 9), "x", "y", "z", 100);
    }

    @Test
    void 유입은_다섯_분류_모두_많은_차례_같으면_화면_차례이고_일별_추이와_캠페인을_준다() {
        referrerSet();
        VisitAnalyticsAdmin.Referrers r = service().referrers("2097-03-10", "2097-03-12", null);

        assertThat(r.groups()).containsExactly(new GroupCount("SEARCH", 13), new GroupCount("DIRECT", 13), new GroupCount("SOCIAL", 3),
                new GroupCount("OTHER_SITE", 0), new GroupCount("CAMPAIGN", 0));
        assertThat(r.total()).isEqualTo(29);
        assertThat(r.domains()).containsExactly(new VisitAnalyticsAdmin.ReferrerDomain("search.naver.com", 7), new VisitAnalyticsAdmin.ReferrerDomain("www.google.com", 6),
                new VisitAnalyticsAdmin.ReferrerDomain("t.co", 3));
        assertThat(r.series()).extracting(VisitAnalyticsAdmin.ReferrerSeriesPoint::date).containsExactly("2097-03-10", "2097-03-11", "2097-03-12");
        assertThat(r.series().get(0).groups().keySet()).containsExactly("SEARCH", "SOCIAL", "DIRECT", "OTHER_SITE", "CAMPAIGN");
        assertThat(r.series().get(0).groups()).containsEntry("SEARCH", 12L).containsEntry("DIRECT", 13L).containsEntry("CAMPAIGN", 0L);
        assertThat(r.series().get(1).groups()).containsEntry("SOCIAL", 3L).containsEntry("SEARCH", 0L);
        assertThat(r.campaigns()).containsExactly(new VisitAnalyticsAdmin.CampaignRow("news", "email", "nov", 6), new VisitAnalyticsAdmin.CampaignRow(null, null, "spring", 3));
    }

    @Test
    void detail_false_면_일별_추이와_캠페인은_빈_배열이고_분류_사이트_합은_같다() {
        referrerSet();
        VisitAnalyticsAdminService svc = service();
        VisitAnalyticsAdmin.Referrers full = svc.referrers("2097-03-10", "2097-03-12", "true");
        VisitAnalyticsAdmin.Referrers light = svc.referrers("2097-03-10", "2097-03-12", "FALSE");

        assertThat(light.series()).isNotNull().isEmpty();
        assertThat(light.campaigns()).isNotNull().isEmpty();
        assertThat(light.groups()).isEqualTo(full.groups());
        assertThat(light.total()).isEqualTo(full.total());
        assertThat(light.domains()).isEqualTo(full.domains());
        assertThat(fieldsOf(() -> svc.referrers("2097-03-10", "2097-03-12", "yes"))).containsOnlyKeys("detail");
    }

    @Test
    void 유입_사이트는_10개_캠페인은_20개까지() {
        for (int i = 1; i <= 12; i++) {
            referrer(day(3, 10), "OTHER_SITE", String.format("d%02d.example", i), i);
        }
        for (int i = 1; i <= 22; i++) {
            campaign(day(3, 10), "src", "med", String.format("c%02d", i), i);
        }
        VisitAnalyticsAdmin.Referrers r = service().referrers("2097-03-10", "2097-03-10", null);
        assertThat(r.domains()).hasSize(10);
        assertThat(r.domains().get(0).domain()).isEqualTo("d12.example");
        assertThat(r.campaigns()).hasSize(20);
        assertThat(r.campaigns().get(0).campaign()).isEqualTo("c22");
        assertThat(r.total()).as("사이트 목록은 10개여도 합은 전부").isEqualTo(78);
    }

    @Test
    void 유입_수집이_꺼지면_0과_빈_배열() {
        referrerSet();
        current = new StatsCollection(true, true, true, 90, false, true, false, false);
        VisitAnalyticsAdmin.Referrers r = service().referrers("2097-03-10", "2097-03-12", null);
        assertThat(r.total()).isZero();
        assertThat(r.domains()).isEmpty();
        assertThat(r.campaigns()).isNotNull().isEmpty();
        assertThat(r.series()).isNotNull().isEmpty();
    }

    // ── 6. realtime ─────────────────────────────────────────────────────────

    @Test
    void 실시간은_최근_30분_분당_30칸_빈_분_0_최근_5분_페이지와_유입() {
        rawPartition();
        view(kst(2097, 3, 13, 10, 20, 10), "/", "HOME", "DIRECT");
        view(kst(2097, 3, 13, 10, 20, 20), "/", "HOME", null);
        view(kst(2097, 3, 13, 10, 19, 0), "/posts/1", "POST", "SEARCH");
        view(NOW.minusSeconds(299), "/posts/1", "POST", null); // 최근 5분 안(10:15:31)
        view(NOW.minusSeconds(301), "/gone", "OTHER", "SOCIAL"); // 5분 밖(10:15:29) — 분당에는 든다
        view(kst(2097, 3, 13, 9, 51, 0), "/old", "OTHER", "SEARCH"); // 30칸의 첫 분
        view(kst(2097, 3, 13, 9, 50, 59), "/older", "OTHER", "SEARCH"); // 30분 밖
        active("qa-r1", NOW.minusSeconds(299));
        active("qa-r2", NOW.minusSeconds(300));
        active("qa-r3", NOW.minusSeconds(301));

        VisitAnalyticsAdmin.Realtime r = service().realtime();

        assertThat(r.perMinute()).hasSize(30);
        assertThat(r.perMinute().get(0)).isEqualTo(new VisitAnalyticsAdmin.MinutePoint(kst(2097, 3, 13, 9, 51), 1));
        assertThat(r.perMinute().get(29)).isEqualTo(new VisitAnalyticsAdmin.MinutePoint(kst(2097, 3, 13, 10, 20), 2));
        assertThat(r.perMinute().get(28).pageViews()).isEqualTo(1);
        assertThat(r.perMinute().get(24)).isEqualTo(new VisitAnalyticsAdmin.MinutePoint(kst(2097, 3, 13, 10, 15), 2));
        assertThat(r.perMinute().get(1).pageViews()).isZero();
        assertThat(r.perMinute().stream().mapToLong(VisitAnalyticsAdmin.MinutePoint::pageViews).sum()).isEqualTo(6);
        assertThat(r.pages()).extracting(PageRow::path, PageRow::views)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("/", 2L), org.assertj.core.groups.Tuple.tuple("/posts/1", 2L));
        assertThat(r.referrers()).containsExactly(new GroupCount("SEARCH", 1), new GroupCount("DIRECT", 1), new GroupCount("SOCIAL", 0), new GroupCount("OTHER_SITE", 0),
                new GroupCount("CAMPAIGN", 0));
        assertThat(r.activeNow()).isEqualTo(2);
        assertThat(r.asOf()).isEqualTo(NOW);
    }

    @Test
    void 실시간_페이지는_10개까지() {
        rawPartition();
        for (int i = 0; i < 12; i++) {
            view(kst(2097, 3, 13, 10, 18, i), "/p/" + i, "OTHER", null);
        }
        assertThat(service().realtime().pages()).hasSize(10);
    }

    @Test
    void 원본_기록이_꺼지면_실시간은_지금_접속만_방문이_꺼지면_지금_접속은_0() {
        rawPartition();
        view(kst(2097, 3, 13, 10, 20, 0), "/", "HOME", "DIRECT");
        active("qa-r1", NOW.minusSeconds(30));

        current = new StatsCollection(true, true, false, 90, true, true, false, false);
        VisitAnalyticsAdmin.Realtime rawOff = service().realtime();
        assertThat(rawOff.activeNow()).isEqualTo(1);
        assertThat(rawOff.perMinute()).isEmpty();
        assertThat(rawOff.pages()).isEmpty();
        assertThat(rawOff.referrers()).isEmpty();
        assertThat(rawOff.collect().rawEvents()).isFalse();

        current = new StatsCollection(true, false, true, 90, true, true, false, false);
        VisitAnalyticsAdmin.Realtime visitsOff = service().realtime();
        assertThat(visitsOff.activeNow()).isZero();
        assertThat(visitsOff.perMinute()).hasSize(30);
        assertThat(visitsOff.perMinute().get(29).pageViews()).isEqualTo(1);
    }

    // ── 7. 기간 검증 ────────────────────────────────────────────────────────

    @Test
    void 기간이_비었거나_형식이_틀리거나_거꾸로이거나_90일을_넘으면_from_오늘_뒤면_to() {
        VisitAnalyticsAdminService svc = service();
        List<Supplier<Object>> ok = List.of(() -> svc.summary("2096-12-14", "2097-03-13"), () -> svc.time("2097-03-13", "2097-03-13"));
        ok.forEach(Supplier::get); // 90일 · 오늘 = 통과

        String[][] fromCases = {{null, "2097-03-10"}, {"2097-03-10", null}, {"", "2097-03-10"}, {"2097/03/10", "2097-03-10"}, {"2097-3-10", "2097-03-10"},
                {"2097-03-10", "2097-02-30"}, {"2097-03-11", "2097-03-10"}, {"2096-12-13", "2097-03-13"}, {"x", "y"}, {"2097-03-10", " "}};
        for (String[] c : fromCases) {
            String from = c[0];
            String to = c[1];
            for (Runnable call : List.<Runnable>of(() -> svc.summary(from, to), () -> svc.time(from, to), () -> svc.pages(from, to, null, null, null, null),
                    () -> svc.pageDetail(from, to, "/"), () -> svc.referrers(from, to, null), () -> svc.flows(from, to, null))) {
                assertThat(fieldsOf(call)).as(from + " ~ " + to).containsOnlyKeys("from");
            }
        }
        for (Runnable call : List.<Runnable>of(() -> svc.summary("2097-03-13", "2097-03-14"), () -> svc.time("2097-03-14", "2097-03-14"),
                () -> svc.pages("2097-03-10", "2097-03-14", null, null, null, null), () -> svc.pageDetail("2097-03-10", "2097-03-14", "/"),
                () -> svc.referrers("2097-03-10", "2097-03-14", null), () -> svc.flows("2097-03-10", "2097-03-14", null))) {
            assertThat(fieldsOf(call)).containsOnlyKeys("to");
        }
        clock.now = kst(2097, 3, 13, 23, 59, 59);
        svc.summary("2097-03-13", "2097-03-13");
        clock.now = kst(2097, 3, 14, 0, 0, 0); // 한국 날짜가 바뀌면 그날이 오늘
        svc.time("2097-03-14", "2097-03-14");
    }

    // ── 8. 캐시 ─────────────────────────────────────────────────────────────

    @Test
    void 결과는_60초_동안_같은_질의에_같은_값이고_지나면_새로_계산한다() {
        hour(kst(2097, 3, 10, 0, 0), 5, 2, 50);
        VisitAnalyticsAdminService svc = service();
        VisitAnalyticsAdmin.Summary first = svc.summary("2097-03-10", "2097-03-10");
        hour(kst(2097, 3, 10, 1, 0), 1, 1, 10);

        clock.now = NOW.plusSeconds(59);
        assertThat(svc.summary("2097-03-10", "2097-03-10")).isSameAs(first);
        assertThat(svc.summary(" 2097-03-10 ", "2097-03-10")).as("정규화한 값이 키").isSameAs(first);
        assertThat(svc.summary("2097-03-09", "2097-03-10").totals().visitors()).as("다른 질의는 다른 키").isEqualTo(6);

        clock.now = NOW.plusSeconds(61);
        assertThat(svc.summary("2097-03-10", "2097-03-10").totals().visitors()).isEqualTo(6);
        assertThat(VisitAnalyticsAdminService.TTL).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void 수집_스위치를_바꾸면_새_키라_바로_반영된다() {
        hour(kst(2097, 3, 10, 0, 0), 5, 2, 50);
        page(day(3, 10), "/", "HOME", 4);
        current = new StatsCollection(false, true, true, 90, true, true, false, false);
        VisitAnalyticsAdminService svc = service();
        assertThat(svc.summary("2097-03-10", "2097-03-10").totals().visitors()).isZero();
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, null, null, null).total()).isZero();

        current = ALL_ON; // 시계는 그대로
        VisitAnalyticsAdmin.Summary on = svc.summary("2097-03-10", "2097-03-10");
        assertThat(on.totals().visitors()).isEqualTo(5);
        assertThat(on.collect().enabled()).isTrue();
        assertThat(svc.pages("2097-03-10", "2097-03-10", null, null, null, null).total()).isEqualTo(1);

        current = new StatsCollection(true, true, true, 365, true, true, false, false); // 보관 일수도 응답 칸이다
        assertThat(svc.summary("2097-03-10", "2097-03-10").rawRetentionDays()).isEqualTo(365);
    }

    @Test
    void 검증_오류는_캐시에_들어가지_않는다() {
        VisitAnalyticsAdminService svc = service();
        fieldsOf(() -> svc.summary("bad", "2097-03-10"));
        fieldsOf(() -> svc.pages("2097-03-10", "2097-03-10", null, "q".repeat(101), null, null));
        fieldsOf(() -> svc.pageDetail("2097-03-10", "2097-03-10", "bad"));
        fieldsOf(() -> svc.referrers("2097-03-10", "2097-03-10", "maybe"));
        fieldsOf(() -> svc.flows("2097-03-10", "2097-03-10", "bad"));
        fieldsOf(() -> svc.time("2097-03-10", "2097-03-14"));
        assertThat(cache.size()).isZero();
        svc.summary("2097-03-10", "2097-03-10");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void 실시간은_20초_동안_운영자와_언어에_상관없이_한_값을_나눠_쓴다() {
        rawPartition();
        view(kst(2097, 3, 13, 10, 20, 0), "/", "HOME", null);
        VisitAnalyticsAdminService svc = service();
        VisitAnalyticsAdmin.Realtime first = svc.realtime();
        view(kst(2097, 3, 13, 10, 20, 5), "/", "HOME", null);

        messages.locale = Locale.ENGLISH;
        clock.now = NOW.plusSeconds(19);
        assertThat(svc.realtime()).isSameAs(first);

        clock.now = NOW.plusSeconds(21);
        VisitAnalyticsAdmin.Realtime later = svc.realtime();
        assertThat(later).isNotSameAs(first);
        assertThat(later.pages().get(0).views()).isEqualTo(2);
        assertThat(VisitAnalyticsAdminService.REALTIME_TTL).isEqualTo(Duration.ofSeconds(20));
    }

    /** 이름 붙이기 흉내 — 넣어 둔 경로만 이름이 있다(null·빈 경로는 건너뛴다) */
    static final class FakeTitles extends VisitPageTitles {

        final Map<String, Name> names = new HashMap<>();
        final Map<String, List<String>> boards = new HashMap<>();

        FakeTitles() {
            super(null, null);
        }

        @Override
        public Map<String, Name> resolve(Collection<String> paths) {
            Map<String, Name> out = new HashMap<>();
            for (String p : paths) {
                if (p != null && !p.isEmpty() && names.containsKey(p)) {
                    out.put(p, names.get(p));
                }
            }
            return out;
        }

        @Override
        public List<String> boardPathsMatching(String q) {
            return boards.getOrDefault(q, List.of());
        }
    }

    /** 요청 언어 흉내 */
    static final class FakeMessages extends Messages {

        volatile Locale locale = Locale.KOREAN;

        FakeMessages() {
            super(new StaticMessageSource(), new StandardEnvironment(), "messages");
        }

        @Override
        public Locale locale() {
            return locale;
        }
    }

    /** 고칠 수 있는 시계 */
    static final class MutableClock extends Clock {

        volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
