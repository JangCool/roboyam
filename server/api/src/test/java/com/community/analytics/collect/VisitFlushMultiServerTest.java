package com.community.analytics.collect;

import static com.community.analytics.collect.AnalyticsTestSupport.ALL_ON;
import static com.community.analytics.collect.AnalyticsTestSupport.IDS;
import static com.community.analytics.collect.AnalyticsTestSupport.INDEX;
import static com.community.analytics.collect.AnalyticsTestSupport.hash;
import static com.community.analytics.collect.AnalyticsTestSupport.settings;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.AnalyticsWriteStore;
import com.community.analytics.store.AnalyticsWriteStore.MemberRow;
import com.community.analytics.store.VisitPeriods;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 방문 몰아쓰기의 서버 여러 대 정확성(site-analytics-contract 「수집·쓰기」 — API 서버 2~3대 전제) — 실제 PostgreSQL({@link CommerceTestDatabase}, analytics 스트림).
 *
 * <p>「서버」 하나 = 수집기({@link VisitCollector}, 자기 메모리 모음) + 몰아쓰기({@link VisitFlusher}) + 자기 분석 연결. 시험마다 다른 먼 미래 시간 칸(2093년~)과 무작위 방문자 해시를 써서
 * 다른 시험의 줄과 겹치지 않는다. 날짜·정시는 수집기와 같은 식({@code ServiceTime.dateOf(t)}, {@code t.truncatedTo(HOURS)})으로 만든다 — 수집기가 {@code Instant.now()} 를
 * 직접 써서 시각을 주입할 수 없기 때문이다.
 */
class VisitFlushMultiServerTest {

    private static final AtomicLong HOURS = new AtomicLong();
    /** 시험 회원 번호(다른 시험의 회원 표시와 겹치지 않는 큰 수) */
    private static final AtomicLong MEMBER_IDS = new AtomicLong(9_093_000_000_000L + (System.nanoTime() & 0xFFFFFL) * 1000);
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

    /** 시험마다 겹치지 않는 정시(2093년 1월 1일부터 하루 간격 — 같은 날 다른 시험과 표시가 섞이지 않게) */
    private static Instant freshHour() {
        return Instant.parse("2093-01-01T03:00:00Z").plus(Duration.ofDays(HOURS.incrementAndGet() * 3));
    }

    record Server(VisitCollector collector, VisitFlusher flusher) {

        void visit(String visitorHash, Instant at, boolean newVisitor) {
            collector.buffer().visit(ServiceTime.dateOf(at), visitorHash, at.truncatedTo(ChronoUnit.HOURS), newVisitor, at);
        }

        boolean bufferEmpty() {
            return collector.buffer().drain().isEmpty();
        }
    }

    private static Server server(AnalyticsWriteStore store, AnalyticsDatabase db) {
        VisitCollector c = new VisitCollector(settings(ALL_ON), INDEX, IDS);
        return new Server(c, new VisitFlusher(c, store, db));
    }

    private static Server server() {
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        return server(new AnalyticsWriteStore(db), db);
    }

    private static long marks(Collection<String> hashes) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_visit_marks WHERE visitor_hash = ANY (?::text[])", Long.class,
                "{" + String.join(",", hashes) + "}");
    }

    /**
     * 그 날 표시 조각(analytics V6 — 하루 파티션)과 그 날이 든 주·달의 주·월 순방문자 표시 조각(analytics V8 — 3단계 결정 ④)을 미리 만든다. 몰아쓰기({@link VisitFlusher})는
     * 조각 없음(23514)을 받으면 스스로 만들고 다시 쓰지만, 저장소를 직접 부르는 시험은 열어 둔 트랜잭션이 23514 로 깨지므로 먼저 만들어 둔다
     * (저장소의 방문자 쓰기는 같은 트랜잭션에서 주·월 표시도 넣는다).
     */
    private static void ensureMarkPartition(LocalDate day) {
        jdbc.queryForObject("SELECT analytics_ensure_visit_mark_partitions(ARRAY[?]::date[])", Integer.class, Date.valueOf(day));
        jdbc.queryForObject("SELECT analytics_ensure_period_mark_partitions(ARRAY[?]::date[])", Integer.class, Date.valueOf(day));
    }

    /** 방문자 주·월 표시 줄 수(해시 앞 16바이트 uuid 로) — {@code table} = analytics_visitor_week_marks · analytics_visitor_month_marks */
    private static long visitorPeriodMarks(String table, Collection<String> hashes) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE visitor_id = ANY (?::uuid[])", Long.class,
                "{" + String.join(",", hashes.stream().map(h -> VisitPeriods.visitorId(h).toString()).toList()) + "}");
    }

    /** 회원 주·월 표시 줄 수 — {@code table} = analytics_member_week_marks · analytics_member_month_marks */
    private static long memberPeriodMarks(String table, long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE member_id = ?", Long.class, memberId);
    }

    /** 숫자 표의 한 기간 합(대상·종류·그 날이 든 기간) — 다른 시험도 같은 기간에 쓸 수 있어 시험은 앞뒤 차이로 본다 */
    private static long uniqueAdded(String audience, String kind, LocalDate day) {
        LocalDate start = VisitPeriods.WEEK.equals(kind) ? VisitPeriods.weekStart(day) : VisitPeriods.monthStart(day);
        return jdbc.queryForObject("SELECT coalesce(sum(added), 0) FROM analytics_unique_counts WHERE audience = ? AND period_kind = ? AND period_start = ?", Long.class,
                audience, kind, Date.valueOf(start));
    }

    /** [방문자 주, 방문자 달, 회원 주, 회원 달] 숫자 표 합 */
    private static long[] uniqueAdded(LocalDate day) {
        return new long[] {uniqueAdded(VisitPeriods.VISITOR, VisitPeriods.WEEK, day), uniqueAdded(VisitPeriods.VISITOR, VisitPeriods.MONTH, day),
                uniqueAdded(VisitPeriods.MEMBER, VisitPeriods.WEEK, day), uniqueAdded(VisitPeriods.MEMBER, VisitPeriods.MONTH, day)};
    }

    private static long[] minus(long[] a, long[] b) {
        long[] out = new long[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] - b[i];
        }
        return out;
    }

    private static long marksOn(LocalDate day, String hash) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_visit_marks WHERE day = ? AND visitor_hash = ?", Long.class, Date.valueOf(day), hash);
    }

    /** 시간 칸 [visitors, new_visitors, page_views] — 줄이 없으면 0 */
    private static long[] hourly(Instant hour) {
        List<long[]> rows = jdbc.query("SELECT visitors, new_visitors, page_views FROM analytics_visit_hourly WHERE hour = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, Timestamp.from(hour));
        return rows.isEmpty() ? new long[3] : rows.get(0);
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

    // ── 1. 방문자 중복 없음(서버 여러 대) ───────────────────────────────────

    @Test
    void 세_서버가_같은_방문자들을_동시에_몰아써도_표시_1줄_방문자_1명이고_페이지뷰는_보낸_만큼_더한다() throws Exception {
        for (int round = 0; round < 5; round++) {
            Instant hour = freshHour();
            List<Server> servers = List.of(server(), server(), server());
            List<String> shared = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                shared.add(hash());
            }
            List<String> all = new ArrayList<>(shared);
            long expectedNew = 0;
            long views = 0;
            for (int i = 0; i < shared.size(); i++) {
                boolean isNew = i % 3 == 0;
                expectedNew += isNew ? 1 : 0;
                for (Server s : servers) { // 같은 방문자의 신호가 세 서버에 나뉘어 들어왔다(신규 여부는 같은 값)
                    s.visit(shared.get(i), hour.plusSeconds(i % 60), isNew);
                    views++;
                }
            }
            for (Server s : servers) { // 서버마다 그 서버만 본 방문자 50명(한 명이 2번씩 봤다)
                for (int i = 0; i < 50; i++) {
                    String own = hash();
                    all.add(own);
                    s.visit(own, hour.plusSeconds(5), false);
                    s.visit(own, hour.plusSeconds(6), false);
                    views += 2;
                }
            }

            flushTogether(servers);

            for (Server s : servers) {
                assertThat(s.bufferEmpty()).as("몰아쓰기가 실패해(교착 등) 메모리에 되돌린 묶음이 없어야 한다 — round %d", round).isTrue();
            }
            assertThat(marks(all)).isEqualTo(all.size());
            long[] h = hourly(hour);
            assertThat(h[0]).as("방문자 = 서로 다른 방문자 수").isEqualTo(all.size());
            assertThat(h[1]).as("신규 방문자").isEqualTo(expectedNew);
            assertThat(h[2]).as("페이지뷰 = 세 서버가 받은 신호의 합").isEqualTo(views);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_visit_active WHERE visitor_hash = ANY (?::text[])", Long.class,
                    "{" + String.join(",", all) + "}")).as("지금 접속도 방문자당 한 줄").isEqualTo(all.size());
        }
    }

    @Test
    void 앞_서버가_커밋하기_전에_같은_방문자를_넣으면_뒤_서버는_기다렸다가_건너뛴다() throws Exception {
        Instant hour = freshHour();
        LocalDate day = ServiceTime.dateOf(hour);
        String visitor = hash();
        ensureMarkPartition(day);
        long[] before = uniqueAdded(day);
        try (Connection a = dataSource.getConnection()) {
            a.setAutoCommit(false);
            AnalyticsWriteStore storeA = new AnalyticsWriteStore(new AnalyticsDatabase(new SingleConnectionDataSource(a, true), false));
            storeA.visits(List.of(new AnalyticsWriteStore.VisitorRow(day, visitor, hour, true)), Map.of(hour, 1L), Map.of(visitor, hour));

            Server b = server();
            b.visit(visitor, hour.plusSeconds(30), true);
            Future<?> flushB = pool.submit(() -> b.flusher().flush());
            Thread.sleep(700);
            assertThat(flushB.isDone()).as("뒤 서버는 앞 서버의 커밋을 기다린다").isFalse();
            a.commit();
            flushB.get(30, TimeUnit.SECONDS);
            assertThat(b.bufferEmpty()).isTrue();
        }
        assertThat(marksOn(day, visitor)).isEqualTo(1);
        assertThat(hourly(hour)).containsExactly(1, 1, 2);
        // 주·월 순방문자(3단계 결정 ④) — 뒤 서버는 하루 표시를 새로 넣지 못했으므로 주·달 표시도 넣어 보지 않는다(앞 서버 몫 1번)
        assertThat(visitorPeriodMarks("analytics_visitor_week_marks", List.of(visitor))).isEqualTo(1);
        assertThat(visitorPeriodMarks("analytics_visitor_month_marks", List.of(visitor))).isEqualTo(1);
        assertThat(minus(uniqueAdded(day), before)).containsExactly(1, 1, 0, 0);
    }

    @Test
    void 앞_서버가_되돌리면_기다리던_뒤_서버가_넣고_센다() throws Exception {
        Instant hour = freshHour();
        LocalDate day = ServiceTime.dateOf(hour);
        String visitor = hash();
        ensureMarkPartition(day);
        long[] before = uniqueAdded(day);
        try (Connection a = dataSource.getConnection()) {
            a.setAutoCommit(false);
            AnalyticsWriteStore storeA = new AnalyticsWriteStore(new AnalyticsDatabase(new SingleConnectionDataSource(a, true), false));
            storeA.visits(List.of(new AnalyticsWriteStore.VisitorRow(day, visitor, hour, false)), Map.of(hour, 1L), Map.of(visitor, hour));

            Server b = server();
            b.visit(visitor, hour.plusSeconds(30), false);
            Future<?> flushB = pool.submit(() -> b.flusher().flush());
            Thread.sleep(700);
            assertThat(flushB.isDone()).isFalse();
            a.rollback();
            flushB.get(30, TimeUnit.SECONDS);
        }
        assertThat(marksOn(day, visitor)).isEqualTo(1);
        assertThat(hourly(hour)).containsExactly(1, 0, 1);
        // 앞 서버의 주·달 표시·숫자도 함께 되돌려졌고 뒤 서버가 한 번 넣었다
        assertThat(visitorPeriodMarks("analytics_visitor_week_marks", List.of(visitor))).isEqualTo(1);
        assertThat(visitorPeriodMarks("analytics_visitor_month_marks", List.of(visitor))).isEqualTo(1);
        assertThat(minus(uniqueAdded(day), before)).containsExactly(1, 1, 0, 0);
    }

    /** 방문자 쓰기 뒤에 실패해 트랜잭션째 되돌려지는 저장소 */
    static final class FailingStore extends AnalyticsWriteStore {

        final AtomicBoolean fail = new AtomicBoolean(true);

        FailingStore(AnalyticsDatabase db) {
            super(db);
        }

        /** 몰아쓰기({@link VisitFlusher})가 부르는 회원 포함 4인자 — 주·월 표시·숫자 표까지 넣은 뒤 실패한다(3인자도 이것을 거친다) */
        @Override
        public void visits(Collection<VisitorRow> visitors, Map<Instant, Long> pageViews, Map<String, Instant> active, Collection<MemberRow> members) {
            super.visits(visitors, pageViews, active, members);
            if (fail.get()) {
                throw new IllegalStateException("시험 — 더하기 뒤 실패");
            }
        }
    }

    @Test
    void 쓰기가_실패하면_표시도_숫자도_남지_않고_되돌린_묶음을_다시_써도_한_번만_센다() {
        Instant hour = freshHour();
        LocalDate day = ServiceTime.dateOf(hour);
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        FailingStore failing = new FailingStore(db);
        Server s = server(failing, db);
        String v1 = hash();
        String v2 = hash();
        String v3 = hash();
        long member = MEMBER_IDS.incrementAndGet();
        List<String> all = List.of(v1, v2, v3);
        long[] before = uniqueAdded(day);
        s.visit(v1, hour, true);
        s.visit(v1, hour.plusSeconds(10), false);
        s.visit(v2, hour.plusSeconds(20), false);
        s.visit(v3, hour.plusSeconds(30), false);
        s.collector().buffer().member(day, member);

        s.flusher().flush();
        assertThat(marks(all)).as("실패한 트랜잭션의 표시는 남지 않는다").isZero();
        assertThat(hourly(hour)).containsExactly(0, 0, 0);
        assertThat(visitorPeriodMarks("analytics_visitor_week_marks", all)).as("주·달 표시도 같은 트랜잭션 — 남지 않는다").isZero();
        assertThat(visitorPeriodMarks("analytics_visitor_month_marks", all)).isZero();
        assertThat(memberPeriodMarks("analytics_member_week_marks", member)).isZero();
        assertThat(memberPeriodMarks("analytics_member_month_marks", member)).isZero();
        assertThat(minus(uniqueAdded(day), before)).as("숫자 표도 남지 않는다").containsExactly(0, 0, 0, 0);

        // 그 사이 다른 서버가 v3 을 먼저 셌다
        Server other = server();
        other.visit(v3, hour.plusSeconds(40), false);
        other.flusher().flush();
        assertThat(hourly(hour)).containsExactly(1, 0, 1);
        assertThat(minus(uniqueAdded(day), before)).containsExactly(1, 1, 0, 0);

        // 되돌린 묶음 + 그 사이 새 신호(v2 한 번 더)를 다음 번에 쓴다 — 실패한 회원 줄은 서버 메모리에 「넣음」으로 남지 않아 다시 넣어 본다
        s.visit(v2, hour.plusSeconds(50), false);
        failing.fail.set(false);
        s.flusher().flush();
        assertThat(marksOn(day, v1)).isEqualTo(1);
        assertThat(marksOn(day, v2)).isEqualTo(1);
        assertThat(marksOn(day, v3)).isEqualTo(1);
        assertThat(hourly(hour)).as("v1·v2 는 한 번만, v3 은 다른 서버 몫 — 페이지뷰는 4 + 1 + 1").containsExactly(3, 1, 6);
        assertThat(visitorPeriodMarks("analytics_visitor_week_marks", all)).isEqualTo(3);
        assertThat(visitorPeriodMarks("analytics_visitor_month_marks", all)).isEqualTo(3);
        assertThat(memberPeriodMarks("analytics_member_week_marks", member)).isEqualTo(1);
        assertThat(memberPeriodMarks("analytics_member_month_marks", member)).isEqualTo(1);
        assertThat(minus(uniqueAdded(day), before)).as("방문자 3명(v3 은 다른 서버 몫)·회원 1명 — 한 번씩").containsExactly(3, 3, 1, 1);

        s.flusher().flush(); // 빈 묶음 — 아무것도 더하지 않는다
        assertThat(hourly(hour)).containsExactly(3, 1, 6);
        assertThat(minus(uniqueAdded(day), before)).containsExactly(3, 3, 1, 1);
    }

    @Test
    void 신규_방문자_신호를_받은_서버가_늦게_써도_신규_방문자가_빠지지_않는다() {
        // QA 가 찾은 버그의 재현(2026-11-04) → analytics V2 「신규」 칸으로 고침. 한 방문자의 첫 페이지(신규 = true)는 서버 A, 1초 뒤 두 번째 페이지(신규 = false)는 서버 B 가 받았다(부하 분산). B 가 먼저 1분 몰아쓰기를 했다
        Instant hour = freshHour();
        String visitor = hash();
        Server a = server();
        Server b = server();
        a.visit(visitor, hour.plusSeconds(1), true);
        b.visit(visitor, hour.plusSeconds(2), false);
        b.flusher().flush();
        a.flusher().flush();
        assertThat(hourly(hour)[0]).isEqualTo(1);
        assertThat(hourly(hour)[1]).as("이 방문자는 오늘 처음 온 신규 방문자다 — 서버 한 대라면 1 로 센다").isEqualTo(1);
    }

    // ── 2. 자정·시간 경계(KST) ───────────────────────────────────────────────

    @Test
    void 한국_23시59분59초와_0시_신호는_다른_날_다른_시간_칸이고_다음_날_같은_방문자는_다시_1명() {
        Instant beforeMidnight = Instant.parse("2093-12-31T14:59:59Z"); // 한국 2093-12-31 23:59:59
        Instant midnight = Instant.parse("2093-12-31T15:00:00Z"); // 한국 2094-01-01 00:00:00
        assertThat(ServiceTime.dateOf(beforeMidnight)).isEqualTo(LocalDate.of(2093, 12, 31));
        assertThat(ServiceTime.dateOf(midnight)).isEqualTo(LocalDate.of(2094, 1, 1));
        Instant hour23 = beforeMidnight.truncatedTo(ChronoUnit.HOURS);
        Instant hour0 = midnight.truncatedTo(ChronoUnit.HOURS);
        assertThat(hour23.atZone(ServiceTime.ZONE).getHour()).isEqualTo(23);
        assertThat(hour0.atZone(ServiceTime.ZONE).getHour()).isZero();

        String visitor = hash();
        String other = hash();
        Server s = server();
        // 같은 1분 묶음 안에 자정 앞뒤 신호가 들었다
        s.visit(visitor, beforeMidnight, false);
        s.visit(visitor, midnight, false);
        s.visit(other, beforeMidnight, false);
        s.flusher().flush();

        assertThat(marksOn(LocalDate.of(2093, 12, 31), visitor)).isEqualTo(1);
        assertThat(marksOn(LocalDate.of(2094, 1, 1), visitor)).isEqualTo(1);
        assertThat(hourly(hour23)).containsExactly(2, 0, 2);
        assertThat(hourly(hour0)).containsExactly(1, 0, 1);

        // 다음 날 같은 방문자가 다른 시간에 또 와도 그 날은 이미 셌다 — 페이지뷰만 늘어난다
        Instant later = midnight.plus(Duration.ofHours(5));
        s.visit(visitor, later, false);
        s.flusher().flush();
        assertThat(hourly(later.truncatedTo(ChronoUnit.HOURS))).containsExactly(0, 0, 1);
    }

    @Test
    void 정시를_넘긴_같은_1분_묶음의_방문자는_먼저_본_시간_칸에_센다() {
        Instant h10 = Instant.parse("2093-06-01T01:59:58Z"); // 한국 10:59:58
        Instant h11 = Instant.parse("2093-06-01T02:00:03Z"); // 한국 11:00:03
        String visitor = hash();
        Server s = server();
        s.visit(visitor, h11, false);
        s.visit(visitor, h10, false); // 늦게 도착했지만 먼저 본 시각
        s.flusher().flush();
        assertThat(hourly(h10.truncatedTo(ChronoUnit.HOURS))).containsExactly(1, 0, 1);
        assertThat(hourly(h11.truncatedTo(ChronoUnit.HOURS))).containsExactly(0, 0, 1);
    }
}
