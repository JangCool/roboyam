package com.community.analytics.collect;

import static com.community.analytics.collect.AnalyticsTestSupport.IDS;
import static com.community.analytics.collect.AnalyticsTestSupport.INDEX;
import static com.community.analytics.collect.AnalyticsTestSupport.settings;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.settings.StatsCollection;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.AnalyticsWriteStore;
import com.community.testsupport.CommerceTestDatabase;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 방문자 통계 2단계의 1분 몰아쓰기(analytics V4) — 실제 PostgreSQL. 환경·검색어·걸러진 검색·봇 일별 더하기를 서버 2~3대가 같은 칸에 동시에 써도 합이 정확한지,
 * 수집 진단(살아 있음 · 시간 칸 · 실패 줄)이 서버 이름별로 정확한지.
 *
 * <p>날짜는 다른 시험과 겹치지 않는 2098년(시험마다 다른 날). 수집 진단은 몰아쓰기가 지금 시각을 직접 써서 서버 이름(시험마다 무작위)으로 가른다.
 */
class VisitStage2FlushTest {

    static final StatsCollection ALL2 = new StatsCollection(true, true, true, 3, true, true, true, true, true, false, false);
    private static final AtomicLong DAYS = new AtomicLong();
    private static final List<String> DEVICES = List.of("MOBILE", "TABLET", "DESKTOP");
    private static final List<String> BROWSERS = List.of("CHROME", "SAFARI", "EDGE", "SAMSUNG", "WHALE", "FIREFOX", "OTHER");
    private static final List<String> OSES = List.of("WINDOWS", "MACOS", "ANDROID", "IOS", "LINUX", "OTHER");
    private static final List<String> SCREENS = List.of("W480", "W768", "W1024", "W1440", "WIDE");
    private static final List<String> KINDS = List.of("GOOGLE", "NAVER", "BING", "DAUM", "OTHER_CRAWLER", "TOOL");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static ExecutorService pool;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated("api", "analytics");
        jdbc = new JdbcTemplate(dataSource);
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterAll
    static void stop() {
        pool.shutdownNow();
    }

    private static LocalDate freshDay() {
        return LocalDate.of(2098, 1, 1).plusDays(DAYS.incrementAndGet() * 2);
    }

    record Server(VisitCollector collector, VisitFlusher flusher, String instance) {

        VisitBuffer buffer() {
            return collector.buffer();
        }
    }

    private static Server server(String instance, AnalyticsWriteStore store, AnalyticsDatabase db) {
        VisitCollector c = new VisitCollector(settings(ALL2), INDEX, IDS);
        return new Server(c, new VisitFlusher(c, store, db, instance, 0), instance);
    }

    private static Server server() {
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        return server("qa-s2-" + UUID.randomUUID(), new AnalyticsWriteStore(db), db);
    }

    private static void flushTogether(List<Server> servers) throws Exception {
        CyclicBarrier start = new CyclicBarrier(servers.size());
        List<Future<?>> done = new ArrayList<>();
        for (Server s : servers) {
            done.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                s.flusher().flush();
                return null;
            }));
        }
        for (Future<?> f : done) {
            f.get(60, TimeUnit.SECONDS);
        }
    }

    private static long envVisits(LocalDate day, String device, String browser, String os, String screen) {
        List<Long> v = jdbc.queryForList("SELECT visits FROM analytics_env_daily WHERE day = ? AND device = ? AND browser = ? AND os = ? AND screen = ?", Long.class,
                Date.valueOf(day), device, browser, os, screen);
        return v.isEmpty() ? 0 : v.get(0);
    }

    private static long[] search(LocalDate day, String term) {
        List<long[]> v = jdbc.query("SELECT searches, no_result FROM analytics_search_daily WHERE day = ? AND term = ?", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)},
                Date.valueOf(day), term);
        return v.isEmpty() ? new long[2] : v.get(0);
    }

    private static long filtered(LocalDate day) {
        List<Long> v = jdbc.queryForList("SELECT searches FROM analytics_search_filtered_daily WHERE day = ?", Long.class, Date.valueOf(day));
        return v.isEmpty() ? 0 : v.get(0);
    }

    private static long bots(LocalDate day, String kind) {
        List<Long> v = jdbc.queryForList("SELECT requests FROM analytics_bot_daily WHERE day = ? AND kind = ?", Long.class, Date.valueOf(day), kind);
        return v.isEmpty() ? 0 : v.get(0);
    }

    /** 이 서버의 진단 시간 칸 합 [ok, fail, dropped, ignored_bot, ignored_invalid] — 시험 중 정시를 넘길 수 있어 시간 칸을 모두 더한다 */
    private static long[] hourly(String instance) {
        return jdbc.queryForObject("SELECT coalesce(sum(flush_ok), 0), coalesce(sum(flush_fail), 0), coalesce(sum(dropped), 0), coalesce(sum(ignored_bot), 0), "
                        + "coalesce(sum(ignored_invalid), 0) FROM analytics_collector_hourly WHERE instance = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)}, instance);
    }

    private static List<String> failures(String instance) {
        return jdbc.queryForList("SELECT reason || ' | ' || detail FROM analytics_collector_failures WHERE instance = ? ORDER BY failed_at, id", String.class, instance);
    }

    private static long beats(String instance) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_collector_beats WHERE instance = ?", Long.class, instance);
    }

    // ── 1·2·3. 서버 여러 대 동시 — 합이 정확 ─────────────────────────────────

    @Test
    void 세_서버가_같은_환경_칸_630개를_동시에_몰아써도_합이_정확하다() throws Exception {
        for (int round = 0; round < 4; round++) {
            LocalDate day = freshDay();
            List<Server> servers = List.of(server(), server(), server());
            int n = 0;
            for (Server s : servers) {
                n++;
                for (String d : DEVICES) {
                    for (String b : BROWSERS) {
                        for (String o : OSES) {
                            for (String sc : SCREENS) {
                                for (int i = 0; i < n; i++) { // 서버 1 은 1번, 2 는 2번, 3 은 3번
                                    s.buffer().env(day, d, b, o, sc);
                                }
                            }
                        }
                    }
                }
            }
            flushTogether(servers);
            for (Server s : servers) {
                assertThat(s.buffer().drain().isEmpty()).as("교착 등으로 되돌린 묶음이 없어야 한다 — round %d", round).isTrue();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_env_daily WHERE day = ?", Long.class, Date.valueOf(day))).isEqualTo(630);
            assertThat(jdbc.queryForObject("SELECT sum(visits) FROM analytics_env_daily WHERE day = ?", Long.class, Date.valueOf(day))).isEqualTo(630 * 6);
            assertThat(envVisits(day, "TABLET", "SAFARI", "IOS", "W1024")).isEqualTo(6);
        }
    }

    @Test
    void 세_서버가_같은_검색어와_걸러진_수를_동시에_몰아써도_합이_정확하다() throws Exception {
        for (int round = 0; round < 4; round++) {
            LocalDate day = freshDay();
            List<Server> servers = List.of(server(), server(), server());
            for (Server s : servers) {
                for (int t = 0; t < 300; t++) {
                    s.buffer().search(day, "qa 검색어 " + t, false);
                    s.buffer().search(day, "qa 검색어 " + t, t % 2 == 0);
                }
                for (int i = 0; i < 7; i++) {
                    s.buffer().searchFiltered(day);
                }
            }
            flushTogether(servers);
            for (Server s : servers) {
                assertThat(s.buffer().drain().isEmpty()).isTrue();
            }
            assertThat(search(day, "qa 검색어 0")).containsExactly(6, 3);
            assertThat(search(day, "qa 검색어 1")).containsExactly(6, 0);
            assertThat(jdbc.queryForObject("SELECT sum(searches) FROM analytics_search_daily WHERE day = ?", Long.class, Date.valueOf(day))).isEqualTo(300 * 6);
            assertThat(jdbc.queryForObject("SELECT sum(no_result) FROM analytics_search_daily WHERE day = ?", Long.class, Date.valueOf(day))).isEqualTo(150 * 3);
            assertThat(filtered(day)).isEqualTo(21);
        }
    }

    @Test
    void 세_서버가_같은_봇_칸을_동시에_몰아써도_합이_정확하다() throws Exception {
        for (int round = 0; round < 4; round++) {
            LocalDate day = freshDay();
            List<Server> servers = List.of(server(), server(), server());
            for (Server s : servers) {
                for (String k : KINDS) {
                    for (int i = 0; i < 11; i++) {
                        s.buffer().bot(day, k);
                    }
                    s.buffer().bot(day.plusDays(1), k); // 자정 넘긴 같은 1분 — 다음 날 칸
                }
            }
            flushTogether(servers);
            for (String k : KINDS) {
                assertThat(bots(day, k)).isEqualTo(33);
                assertThat(bots(day.plusDays(1), k)).isEqualTo(3);
            }
        }
    }

    @Test
    void 다듬은_50자_이모지_검색어도_그대로_저장된다() {
        LocalDate day = freshDay();
        Server s = server();
        String term = VisitCollector.normalizeTerm("😀".repeat(50));
        s.buffer().search(day, term, true);
        s.flusher().flush();
        assertThat(s.buffer().drain().isEmpty()).as("varchar(50) 에 들어가야 한다(코드 포인트)").isTrue();
        assertThat(search(day, term)).containsExactly(1, 1);
    }

    // ── 4. 수집 진단 ─────────────────────────────────────────────────────────

    @Test
    void 신호가_없어도_1분마다_살아_있음과_시간_칸을_쓰고_서버_이름별로_줄이_있다() {
        Server a = server();
        Server b = server();
        a.flusher().flush();
        a.flusher().flush();
        b.flusher().flush();
        assertThat(beats(a.instance())).isEqualTo(1);
        assertThat(beats(b.instance())).isEqualTo(1);
        assertThat(hourly(a.instance())).containsExactly(0, 0, 0, 0, 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_collector_hourly WHERE instance = ?", Long.class, a.instance())).isBetween(1L, 2L);

        // 묶음 하나 = 성공 1(환경·검색어·봇 세 묶음이면 3)
        LocalDate day = freshDay();
        a.buffer().env(day, "DESKTOP", "CHROME", "WINDOWS", "W1440");
        a.buffer().search(day, "qa diag", false);
        a.buffer().bot(day, "GOOGLE");
        a.flusher().flush();
        assertThat(hourly(a.instance())).containsExactly(3, 0, 0, 0, 0);
        assertThat(hourly(b.instance())).containsExactly(0, 0, 0, 0, 0);
    }

    @Test
    void 무시한_신호와_버린_줄은_그_서버_시간_칸에_정확히_더한다() {
        Server s = server();
        LocalDate day = LocalDate.of(2099, 3, 1); // 날 칸 11개를 쓴다 — freshDay(2일 간격)와 겹치지 않는 해
        for (int i = 0; i < 3; i++) {
            s.collector().accept("{\"visitorId\":\"" + UUID.randomUUID() + "\"}", "Googlebot/2.1", "irobit.test");
        }
        s.collector().accept("not-json", "Mozilla/5.0 Chrome/130", "irobit.test");
        s.collector().accept("{}", "Mozilla/5.0 Chrome/130", "irobit.test");
        for (int i = 0; i <= VisitBuffer.MAX_DAYS; i++) { // 걸러진 검색 날 칸 상한 + 1 → 가장 오래된 1줄 버림
            s.buffer().searchFiltered(day.plusDays(i));
        }
        s.flusher().flush();
        assertThat(hourly(s.instance())).containsExactly(1, 0, 1, 3, 2);
        assertThat(filtered(day)).as("버린 줄").isZero();
        assertThat(filtered(day.plusDays(1))).isEqualTo(1);
    }

    /** 정해 둔 묶음에서 실패하는 저장소(트랜잭션째 되돌려진다) */
    static final class FlakyStore extends AnalyticsWriteStore {

        final AtomicInteger envFails = new AtomicInteger();
        final AtomicInteger diagFails = new AtomicInteger();
        RuntimeException envError = new QueryTimeoutException("시험 — 환경 쓰기 시간 초과");

        FlakyStore(AnalyticsDatabase db) {
            super(db);
        }

        @Override
        public void env(List<EnvRow> rows) {
            super.env(rows);
            if (envFails.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw envError;
            }
        }

        @Override
        public void diagnostics(Diagnostics d) {
            if (diagFails.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new DataAccessResourceFailureException("시험 — 진단 쓰기 실패");
            }
            super.diagnostics(d);
        }
    }

    @Test
    void 기록_실패는_시간_칸과_실패_줄에_남고_진단마저_실패하면_다음_성공_때_합쳐_쓴다() {
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        FlakyStore store = new FlakyStore(db);
        Server s = server("qa-s2-flaky-" + UUID.randomUUID(), store, db);
        LocalDate day = freshDay();

        // ① 환경 쓰기 실패(검색어는 성공) — 실패 1 · 성공 1 · 실패 줄 DB_TIMEOUT, 환경은 메모리로 되돌림
        store.envFails.set(1);
        s.buffer().env(day, "MOBILE", "SAFARI", "IOS", "W480");
        s.buffer().search(day, "qa flaky", false);
        s.flusher().flush();
        assertThat(hourly(s.instance())).containsExactly(1, 1, 0, 0, 0);
        assertThat(failures(s.instance())).hasSize(1);
        assertThat(failures(s.instance()).get(0)).startsWith("DB_TIMEOUT | QueryTimeoutException").endsWith("환경");
        assertThat(envVisits(day, "MOBILE", "SAFARI", "IOS", "W480")).as("되돌려진 트랜잭션").isZero();

        // ② 환경은 성공(되돌린 1 + 새 1), 진단 쓰기가 실패 — DB 의 진단은 그대로, 숫자는 메모리에
        store.diagFails.set(1);
        s.buffer().env(day, "MOBILE", "SAFARI", "IOS", "W480");
        s.collector().accept("{}", "Googlebot/2.1", "irobit.test");
        s.flusher().flush();
        assertThat(envVisits(day, "MOBILE", "SAFARI", "IOS", "W480")).isEqualTo(2);
        assertThat(hourly(s.instance())).containsExactly(1, 1, 0, 0, 0);
        assertThat(failures(s.instance())).hasSize(1);

        // ③ 신호 없이 다음 1분 — 남긴 숫자(성공 1 · 무시한 봇 1)와 진단 실패 줄을 함께 쓴다
        s.flusher().flush();
        assertThat(hourly(s.instance())).containsExactly(2, 1, 0, 1, 0);
        List<String> f = failures(s.instance());
        assertThat(f).hasSize(2);
        assertThat(f.get(1)).startsWith("DB_UNAVAILABLE | DataAccessResourceFailureException").endsWith("수집 진단");

        // ④ 한 번 더 — 이미 쓴 것은 다시 더하지 않는다
        s.flusher().flush();
        assertThat(hourly(s.instance())).containsExactly(2, 1, 0, 1, 0);
        assertThat(failures(s.instance())).hasSize(2);
    }

    @Test
    void 실패_사유_코드는_예외와_SQLSTATE_로_가른다() {
        assertThat(VisitFlusher.reason(new QueryTimeoutException("x"), null)).isEqualTo("DB_TIMEOUT");
        assertThat(VisitFlusher.reason(new DataAccessResourceFailureException("x"), null)).isEqualTo("DB_UNAVAILABLE");
        assertThat(VisitFlusher.reason(new DataIntegrityViolationException("x"), null)).isEqualTo("CONSTRAINT");
        assertThat(VisitFlusher.reason(new RuntimeException(new java.sql.SQLTimeoutException("x")), null)).isEqualTo("DB_TIMEOUT");
        assertThat(VisitFlusher.reason(new RuntimeException("x"), "57014")).isEqualTo("DB_TIMEOUT");
        assertThat(VisitFlusher.reason(new RuntimeException("x"), "55P03")).isEqualTo("DB_TIMEOUT");
        assertThat(VisitFlusher.reason(new RuntimeException("x"), "08006")).isEqualTo("DB_UNAVAILABLE");
        assertThat(VisitFlusher.reason(new RuntimeException("x"), "53300")).isEqualTo("DB_UNAVAILABLE");
        assertThat(VisitFlusher.reason(new RuntimeException("x"), "57P01")).isEqualTo("DB_UNAVAILABLE");
        assertThat(VisitFlusher.reason(new RuntimeException("x"), "23505")).isEqualTo("CONSTRAINT");
        assertThat(VisitFlusher.reason(new RuntimeException("x"), "42P01")).isEqualTo("UNKNOWN");
        assertThat(VisitFlusher.reason(new RuntimeException(new SQLException("x")), null)).isEqualTo("UNKNOWN");
    }

    @Test
    void 서버_이름은_설정값_없으면_api_NODE_ID_이고_64자로_자른다() {
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        AnalyticsWriteStore store = new AnalyticsWriteStore(db);
        VisitCollector c = new VisitCollector(settings(ALL2), INDEX, IDS);
        int node = 900 + (int) (DAYS.incrementAndGet() % 99);
        jdbc.update("DELETE FROM analytics_collector_beats WHERE instance = ?", "api-" + node);
        new VisitFlusher(c, store, db, "  ", node).flush();
        assertThat(beats("api-" + node)).isEqualTo(1);

        String longName = "qa-" + UUID.randomUUID() + "-" + "x".repeat(80);
        new VisitFlusher(c, store, db, longName, node).flush();
        assertThat(beats(longName.substring(0, VisitFlusher.INSTANCE_MAX))).isEqualTo(1);
    }

    @Test
    void 메모리_되돌림은_다음_1분에_한_번만_더한다() {
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        FlakyStore store = new FlakyStore(db);
        Server s = server("qa-s2-restore-" + UUID.randomUUID(), store, db);
        LocalDate day = freshDay();
        store.envFails.set(2);
        for (int i = 0; i < 5; i++) {
            s.buffer().env(day, "DESKTOP", "EDGE", "WINDOWS", "WIDE");
        }
        s.flusher().flush();
        s.flusher().flush();
        assertThat(envVisits(day, "DESKTOP", "EDGE", "WINDOWS", "WIDE")).isZero();
        s.flusher().flush();
        assertThat(envVisits(day, "DESKTOP", "EDGE", "WINDOWS", "WIDE")).isEqualTo(5);
        assertThat(hourly(s.instance())).containsExactly(1, 2, 0, 0, 0);
        assertThat(failures(s.instance())).hasSize(2);
    }
}
