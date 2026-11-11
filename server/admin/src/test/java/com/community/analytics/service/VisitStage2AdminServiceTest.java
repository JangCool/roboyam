package com.community.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.dto.VisitAnalyticsAdmin;
import com.community.analytics.dto.VisitAnalyticsAdmin.BotKindCount;
import com.community.analytics.dto.VisitAnalyticsAdmin.ShareRow;
import com.community.analytics.repository.master.VisitAnalyticsStore;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.FakeMessages;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.FakeTitles;
import com.community.analytics.service.VisitAnalyticsAdminServiceTest.MutableClock;
import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.flyway.SchemaMigration;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 「방문자 통계」 2단계 관리자 API 환경·검색어·봇의 계산(site-analytics-contract 「2단계」) — 실제 PostgreSQL(analytics 스트림), 시험마다 연결 하나의 트랜잭션으로 넣고 되돌린다.
 * 고정 시계(2098년 5월 20일 10:20:30 한국 — 다른 시험과 겹치지 않는 해). 꺼짐이면 모든 키 0 · {@code series} 빈 배열, 정렬, 기간 검증, 캐시 60초와 캐시 키의 새 칸.
 */
class VisitStage2AdminServiceTest {

    static final Instant NOW = LocalDateTime.of(2098, 5, 20, 10, 20, 30).atZone(ServiceTime.ZONE).toInstant();
    static final StatsCollection ALL2 = new StatsCollection(true, true, true, 3, true, true, true, true, true, false, false);

    private static DataSource base;
    private Connection connection;
    private JdbcTemplate jdbc;
    private VisitAnalyticsStore store;
    private volatile StatsCollection current = ALL2;
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
        settings = new StatsCollectionSettings(null, null) {
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

    private VisitAnalyticsAdminService service() {
        return new VisitAnalyticsAdminService(store, new FakeTitles(), settings, new FakeMessages(), clock);
    }

    static LocalDate day(int m, int d) {
        return LocalDate.of(2098, m, d);
    }

    private static Instant kst(int m, int d, int h) {
        return LocalDateTime.of(2098, m, d, h, 0).atZone(ServiceTime.ZONE).toInstant();
    }

    private void env(LocalDate day, String device, String browser, String os, String screen, long visits) {
        jdbc.update("INSERT INTO analytics_env_daily (day, device, browser, os, screen, visits) VALUES (?, ?, ?, ?, ?, ?)", Date.valueOf(day), device, browser, os, screen,
                visits);
    }

    private void term(LocalDate day, String term, long searches, long noResult) {
        jdbc.update("INSERT INTO analytics_search_daily (day, term, searches, no_result) VALUES (?, ?, ?, ?)", Date.valueOf(day), term, searches, noResult);
    }

    private void filtered(LocalDate day, long searches) {
        jdbc.update("INSERT INTO analytics_search_filtered_daily (day, searches) VALUES (?, ?)", Date.valueOf(day), searches);
    }

    private void bot(LocalDate day, String kind, long requests) {
        jdbc.update("INSERT INTO analytics_bot_daily (day, kind, requests) VALUES (?, ?, ?)", Date.valueOf(day), kind, requests);
    }

    private void hour(Instant hour, long views) {
        jdbc.update("INSERT INTO analytics_visit_hourly (hour, visitors, new_visitors, page_views) VALUES (?, 1, 0, ?)", Timestamp.from(hour), views);
    }

    private static Map<String, Long> asMap(List<ShareRow> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        rows.forEach(r -> m.put(r.key(), r.visits()));
        return m;
    }

    private static Map<String, ?> fieldsOf(Runnable call) {
        try {
            call.run();
        } catch (ValidationException e) {
            return e.getFields();
        }
        throw new AssertionError("ValidationException 이 나야 한다");
    }

    // ── 환경 ─────────────────────────────────────────────────────────────────

    @Test
    void 환경은_기간의_분류별_합이고_기기_브라우저_OS_는_많은_차례_화면은_구간_차례_일별_기기는_빈_날도_0() {
        env(day(5, 10), "MOBILE", "SAFARI", "IOS", "W480", 5);
        env(day(5, 10), "DESKTOP", "CHROME", "WINDOWS", "W1440", 3);
        env(day(5, 12), "MOBILE", "SAMSUNG", "ANDROID", "W480", 2);
        env(day(5, 12), "TABLET", "SAFARI", "IOS", "W1024", 1);
        env(day(5, 9), "DESKTOP", "CHROME", "WINDOWS", "WIDE", 100); // 기간 앞
        env(day(5, 13), "DESKTOP", "CHROME", "WINDOWS", "WIDE", 100); // 기간 뒤

        VisitAnalyticsAdmin.Environment e = service().environment("2098-05-10", "2098-05-12");

        assertThat(e.total()).isEqualTo(11);
        assertThat(asMap(e.devices())).containsExactly(Map.entry("MOBILE", 7L), Map.entry("DESKTOP", 3L), Map.entry("TABLET", 1L));
        assertThat(asMap(e.browsers())).containsExactly(Map.entry("SAFARI", 6L), Map.entry("CHROME", 3L), Map.entry("SAMSUNG", 2L), Map.entry("EDGE", 0L),
                Map.entry("WHALE", 0L), Map.entry("FIREFOX", 0L), Map.entry("OTHER", 0L));
        assertThat(asMap(e.oses())).containsExactly(Map.entry("IOS", 6L), Map.entry("WINDOWS", 3L), Map.entry("ANDROID", 2L), Map.entry("MACOS", 0L),
                Map.entry("LINUX", 0L), Map.entry("OTHER", 0L));
        assertThat(asMap(e.screens())).as("구간 차례 그대로").containsExactly(Map.entry("W480", 7L), Map.entry("W768", 0L), Map.entry("W1024", 1L),
                Map.entry("W1440", 3L), Map.entry("WIDE", 0L));
        assertThat(e.series()).extracting(VisitAnalyticsAdmin.DeviceSeriesPoint::date).containsExactly("2098-05-10", "2098-05-11", "2098-05-12");
        assertThat(e.series().get(0).devices()).containsExactly(Map.entry("MOBILE", 5L), Map.entry("TABLET", 0L), Map.entry("DESKTOP", 3L));
        assertThat(e.series().get(1).devices()).containsExactly(Map.entry("MOBILE", 0L), Map.entry("TABLET", 0L), Map.entry("DESKTOP", 0L));
        assertThat(e.series().get(2).devices()).containsExactly(Map.entry("MOBILE", 2L), Map.entry("TABLET", 1L), Map.entry("DESKTOP", 0L));
        assertThat(e.collect()).isEqualTo(new VisitAnalyticsAdmin.CollectMore(true, true, true, true, true));
        assertThat(e.range()).isEqualTo(new VisitAnalyticsAdmin.Range("2098-05-10", "2098-05-12"));
        assertThat(e.asOf()).isEqualTo(NOW);
    }

    @Test
    void 같은_수의_분류는_정해진_키_차례() {
        env(day(5, 10), "DESKTOP", "FIREFOX", "LINUX", "W768", 2);
        env(day(5, 10), "MOBILE", "EDGE", "MACOS", "W768", 2);
        VisitAnalyticsAdmin.Environment e = service().environment("2098-05-10", "2098-05-10");
        assertThat(e.devices()).extracting(ShareRow::key).containsExactly("MOBILE", "DESKTOP", "TABLET");
        assertThat(e.browsers()).extracting(ShareRow::key).startsWith("EDGE", "FIREFOX", "CHROME");
        assertThat(e.oses()).extracting(ShareRow::key).startsWith("MACOS", "LINUX", "WINDOWS");
    }

    @Test
    void 환경이_꺼져_있으면_DB_에_줄이_있어도_모든_키_0_series_빈_배열() {
        env(day(5, 10), "MOBILE", "SAFARI", "IOS", "W480", 5);
        for (StatsCollection off : List.of(new StatsCollection(true, true, true, 3, true, true, false, true, true, false, false),
                new StatsCollection(false, true, true, 3, true, true, true, true, true, false, false))) {
            current = off;
            VisitAnalyticsAdmin.Environment e = service().environment("2098-05-10", "2098-05-10");
            assertThat(e.total()).isZero();
            assertThat(e.devices()).hasSize(3).allMatch(r -> r.visits() == 0);
            assertThat(e.browsers()).hasSize(7).allMatch(r -> r.visits() == 0);
            assertThat(e.oses()).hasSize(6).allMatch(r -> r.visits() == 0);
            assertThat(e.screens()).hasSize(5).allMatch(r -> r.visits() == 0);
            assertThat(e.series()).isEmpty();
            assertThat(e.collect().environment()).isFalse();
            assertThat(e.collect().enabled()).isEqualTo(off.enabled());
        }
    }

    // ── 검색어 ───────────────────────────────────────────────────────────────

    @Test
    void 검색어는_기간_합_상위_20_결과_없는_상위_20_마지막_결과_없던_날() {
        term(day(5, 10), "rtx 4090", 10, 0);
        term(day(5, 11), "rtx 4090", 5, 1);
        term(day(5, 12), "rtx 4090", 3, 0); // 결과 있음 — 마지막 결과 없던 날이 아니다
        term(day(5, 10), "없는 상품", 4, 4);
        term(day(5, 12), "없는 상품", 2, 2);
        term(day(5, 11), "b term", 7, 0);
        term(day(5, 11), "a term", 7, 7);
        term(day(5, 9), "기간 앞", 100, 100);
        term(day(5, 13), "기간 뒤", 100, 100);
        for (int i = 0; i < 25; i++) {
            term(day(5, 12), String.format("long tail %02d", i), 1, 0);
        }
        filtered(day(5, 10), 3);
        filtered(day(5, 12), 4);
        filtered(day(5, 13), 100);

        VisitAnalyticsAdmin.Search s = service().search("2098-05-10", "2098-05-12");

        assertThat(s.totals()).isEqualTo(new VisitAnalyticsAdmin.SearchTotals(18 + 6 + 14 + 25, 1 + 6 + 7, 4 + 25, 7));
        assertThat(s.terms()).hasSize(20);
        assertThat(s.terms().subList(0, 4)).containsExactly(new VisitAnalyticsAdmin.SearchTerm("rtx 4090", 18, 1), new VisitAnalyticsAdmin.SearchTerm("a term", 7, 7),
                new VisitAnalyticsAdmin.SearchTerm("b term", 7, 0), new VisitAnalyticsAdmin.SearchTerm("없는 상품", 6, 6));
        assertThat(s.terms().get(4).term()).isEqualTo("long tail 00");
        assertThat(s.noResultTerms()).containsExactly(new VisitAnalyticsAdmin.NoResultTerm("a term", 7, "2098-05-11"),
                new VisitAnalyticsAdmin.NoResultTerm("없는 상품", 6, "2098-05-12"), new VisitAnalyticsAdmin.NoResultTerm("rtx 4090", 1, "2098-05-11"));
    }

    @Test
    void 검색어가_꺼져_있으면_0_과_빈_목록() {
        term(day(5, 10), "rtx 4090", 10, 1);
        filtered(day(5, 10), 3);
        current = new StatsCollection(true, true, true, 3, true, true, true, false, true, false, false);
        VisitAnalyticsAdmin.Search s = service().search("2098-05-10", "2098-05-10");
        assertThat(s.totals()).isEqualTo(new VisitAnalyticsAdmin.SearchTotals(0, 0, 0, 0));
        assertThat(s.terms()).isEmpty();
        assertThat(s.noResultTerms()).isEmpty();
        assertThat(s.collect().searchTerms()).isFalse();
    }

    // ── 봇 ───────────────────────────────────────────────────────────────────

    @Test
    void 봇은_기간_합과_종류별_많은_차례_일별_여섯_종류_오늘은_기간과_따로() {
        bot(day(5, 10), "GOOGLE", 30);
        bot(day(5, 10), "NAVER", 50);
        bot(day(5, 11), "TOOL", 7);
        bot(day(5, 12), "GOOGLE", 5);
        bot(day(5, 9), "BING", 100); // 기간 앞
        bot(day(5, 20), "DAUM", 9); // 오늘 — 기간 밖
        bot(day(5, 20), "GOOGLE", 1);
        hour(kst(5, 10, 0), 100);
        hour(kst(5, 12, 23), 20);
        hour(kst(5, 13, 0), 1000); // 기간 뒤
        hour(kst(5, 20, 0), 40); // 오늘
        hour(kst(5, 20, 10), 2); // 오늘 지금 칸

        VisitAnalyticsAdmin.Bots b = service().bots("2098-05-10", "2098-05-12");

        assertThat(b.totals()).isEqualTo(new VisitAnalyticsAdmin.BotCounts(92, 120L));
        assertThat(b.today()).isEqualTo(new VisitAnalyticsAdmin.BotCounts(10, 42L));
        assertThat(b.kinds()).containsExactly(new BotKindCount("NAVER", 50), new BotKindCount("GOOGLE", 35), new BotKindCount("TOOL", 7), new BotKindCount("BING", 0),
                new BotKindCount("DAUM", 0), new BotKindCount("OTHER_CRAWLER", 0));
        assertThat(b.series()).hasSize(3);
        assertThat(b.series().get(0).kinds()).containsExactly(Map.entry("GOOGLE", 30L), Map.entry("NAVER", 50L), Map.entry("BING", 0L), Map.entry("DAUM", 0L),
                Map.entry("OTHER_CRAWLER", 0L), Map.entry("TOOL", 0L));
        assertThat(b.series().get(1).kinds().get("TOOL")).isEqualTo(7);
        assertThat(b.series().get(2).kinds().get("GOOGLE")).isEqualTo(5);
    }

    @Test
    void 기간에_오늘이_들면_오늘_봇은_기간에도_든다() {
        bot(day(5, 20), "GOOGLE", 4);
        bot(day(5, 19), "GOOGLE", 6);
        VisitAnalyticsAdmin.Bots b = service().bots("2098-05-19", "2098-05-20");
        assertThat(b.today().bots()).isEqualTo(4);
        assertThat(b.totals().bots()).isEqualTo(10);
    }

    @Test
    void 봇이_꺼지면_봇_수는_0_series_빈_배열이고_방문자_수집이_꺼지면_사람_대비는_null() {
        bot(day(5, 10), "GOOGLE", 30);
        bot(day(5, 20), "GOOGLE", 3);
        hour(kst(5, 10, 0), 100);
        current = new StatsCollection(true, true, true, 3, true, true, true, true, false, false, false);
        VisitAnalyticsAdmin.Bots off = service().bots("2098-05-10", "2098-05-10");
        assertThat(off.totals()).isEqualTo(new VisitAnalyticsAdmin.BotCounts(0, 100L));
        assertThat(off.today().bots()).isZero();
        assertThat(off.kinds()).hasSize(6).allMatch(k -> k.requests() == 0);
        assertThat(off.series()).isEmpty();
        assertThat(off.collect().bots()).isFalse();

        current = new StatsCollection(true, false, true, 3, true, true, true, true, true, false, false);
        VisitAnalyticsAdmin.Bots noHumans = service().bots("2098-05-10", "2098-05-10");
        assertThat(noHumans.totals()).isEqualTo(new VisitAnalyticsAdmin.BotCounts(30, null));
        assertThat(noHumans.today()).isEqualTo(new VisitAnalyticsAdmin.BotCounts(3, null));
        assertThat(noHumans.collect().visits()).isFalse();
    }

    // ── 기간 검증 · 캐시 ─────────────────────────────────────────────────────

    @Test
    void 기간이_틀리면_세_API_모두_400_fields_from_오늘_뒤면_fields_to() {
        VisitAnalyticsAdminService svc = service();
        List<java.util.function.BiConsumer<String, String>> apis = List.of(svc::environment, svc::search, svc::bots);
        for (java.util.function.BiConsumer<String, String> api : apis) {
            assertThat(fieldsOf(() -> api.accept(null, "2098-05-20"))).containsOnlyKeys("from");
            assertThat(fieldsOf(() -> api.accept("2098-05-20", "2098-05-19"))).containsOnlyKeys("from");
            assertThat(fieldsOf(() -> api.accept("2098-02-19", "2098-05-20"))).as("91일").containsOnlyKeys("from");
            assertThat(fieldsOf(() -> api.accept("2098/05/01", "2098-05-20"))).containsOnlyKeys("from");
            assertThat(fieldsOf(() -> api.accept("2098-05-20", "2098-05-21"))).containsOnlyKeys("to");
            api.accept("2098-02-20", "2098-05-20"); // 90일
        }
    }

    @Test
    void 결과는_60초_캐시이고_설정의_새_칸이_바뀌면_새로_계산한다() {
        env(day(5, 10), "MOBILE", "SAFARI", "IOS", "W480", 5);
        term(day(5, 10), "rtx", 1, 0);
        bot(day(5, 10), "GOOGLE", 1);
        VisitAnalyticsAdminService svc = service();
        assertThat(svc.environment("2098-05-10", "2098-05-10").total()).isEqualTo(5);
        assertThat(svc.search("2098-05-10", "2098-05-10").totals().searches()).isEqualTo(1);
        assertThat(svc.bots("2098-05-10", "2098-05-10").totals().bots()).isEqualTo(1);

        env(day(5, 10), "DESKTOP", "CHROME", "WINDOWS", "W1440", 2);
        term(day(5, 10), "gpu", 1, 0);
        bot(day(5, 10), "NAVER", 1);
        clock.now = NOW.plusSeconds(59);
        assertThat(svc.environment("2098-05-10", "2098-05-10").total()).as("60초 안 — 캐시").isEqualTo(5);
        assertThat(svc.search("2098-05-10", "2098-05-10").totals().searches()).isEqualTo(1);
        assertThat(svc.bots("2098-05-10", "2098-05-10").totals().bots()).isEqualTo(1);

        // 같은 시각이라도 2단계 칸 하나가 바뀌면 새 키 — 옛 값이 남지 않는다
        current = new StatsCollection(true, true, true, 3, true, true, true, true, false, false, false);
        assertThat(svc.environment("2098-05-10", "2098-05-10").total()).isEqualTo(7);
        current = ALL2;
        clock.now = NOW.plusSeconds(61);
        assertThat(svc.search("2098-05-10", "2098-05-10").totals().searches()).isEqualTo(2);
        assertThat(svc.bots("2098-05-10", "2098-05-10").totals().bots()).isEqualTo(2);
    }

    @Test
    void 캐시_키_지문은_새_칸_세_개를_모두_가른다() {
        Set<String> prints = new HashSet<>();
        prints.add(VisitAnalyticsAdminService.fingerprint(ALL2));
        prints.add(VisitAnalyticsAdminService.fingerprint(new StatsCollection(true, true, true, 3, true, true, false, true, true, false, false)));
        prints.add(VisitAnalyticsAdminService.fingerprint(new StatsCollection(true, true, true, 3, true, true, true, false, true, false, false)));
        prints.add(VisitAnalyticsAdminService.fingerprint(new StatsCollection(true, true, true, 3, true, true, true, true, false, false, false)));
        assertThat(prints).hasSize(4);
        // 전체가 꺼지면 저장 값과 상관없이 같은 지문(동작 여부로 접는다)
        assertThat(VisitAnalyticsAdminService.fingerprint(new StatsCollection(false, true, true, 3, true, true, true, true, true, false, false)))
                .isEqualTo(VisitAnalyticsAdminService.fingerprint(new StatsCollection(false, false, false, 3, false, false, false, false, false, false, false)));
    }

    @Test
    void 접은_collect_는_전체와_항목() {
        assertThat(VisitAnalyticsAdminService.collectMore(new StatsCollection(false, true, true, 3, true, true, true, true, true, false, false)))
                .isEqualTo(new VisitAnalyticsAdmin.CollectMore(false, false, false, false, false));
        assertThat(VisitAnalyticsAdminService.collectMore(new StatsCollection(true, false, true, 3, true, true, true, false, true, false, false)))
                .isEqualTo(new VisitAnalyticsAdmin.CollectMore(true, true, false, true, false));
        assertThat(Duration.ofSeconds(60)).isEqualTo(VisitAnalyticsAdminService.TTL);
    }
}
