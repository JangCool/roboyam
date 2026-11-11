package com.community.analytics.collect;

import static com.community.analytics.collect.AnalyticsTestSupport.ALL_ON;
import static com.community.analytics.collect.AnalyticsTestSupport.IDS;
import static com.community.analytics.collect.AnalyticsTestSupport.INDEX;
import static com.community.analytics.collect.AnalyticsTestSupport.hash;
import static com.community.analytics.collect.AnalyticsTestSupport.settings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.AnalyticsWriteStore;
import com.community.analytics.store.AnalyticsWriteStore.MemberRow;
import com.community.analytics.store.AnalyticsWriteStore.VisitorRow;
import com.community.analytics.store.VisitPeriods;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * 주·월 순방문자 몰아쓰기(site-analytics-contract 「3단계」 결정 ④ · 결정 ⑥ — analytics V8 표시 네 표 + 숫자 표 {@code analytics_unique_counts}) — 실제 PostgreSQL.
 *
 * <p>「서버」 하나 = 수집기 + 몰아쓰기 + 자기 분석 연결({@code VisitFlushMultiServerTest} 와 같은 모양). 날짜는 다른 시험과 겹치지 않는 먼 미래(2086년), 방문자 해시·회원 번호는
 * 무작위라 표시 줄은 시험마다 따로다. 숫자 표는 기간마다 한 칸이라 다른 시험이 같은 기간에 쓸 수 있어 늘 <b>앞뒤 차이</b>로 본다. 시각은 수집기와 같은 식
 * ({@code ServiceTime.dateOf(t)}, 정시 자르기)으로 만든다.
 */
class VisitUniqueFlushTest {

    private static final AtomicLong MEMBER_IDS = new AtomicLong(9_086_000_000_000L + (System.nanoTime() & 0xFFFFFL) * 1000);
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

    // ── 준비 ──────────────────────────────────────────────────────────────────

    record Server(VisitCollector collector, VisitFlusher flusher) {

        void visit(String visitorHash, Instant at) {
            collector.buffer().visit(ServiceTime.dateOf(at), visitorHash, at.truncatedTo(ChronoUnit.HOURS), false, at);
        }

        /** 로그인 회원의 신호 — 수집기는 방문자 신호와 함께 회원 줄도 모은다(뺄 계정이 아닐 때) */
        void member(long memberId, Instant at) {
            collector.buffer().member(ServiceTime.dateOf(at), memberId);
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

    /** 한국 시각 */
    private static Instant kst(int y, int m, int d, int h, int min, int s) {
        return LocalDateTime.of(y, m, d, h, min, s).atZone(ServiceTime.ZONE).toInstant();
    }

    private static long member() {
        return MEMBER_IDS.incrementAndGet();
    }

    private static String uuids(Collection<String> hashes) {
        return "{" + String.join(",", hashes.stream().map(h -> VisitPeriods.visitorId(h).toString()).toList()) + "}";
    }

    private static String longs(Collection<Long> ids) {
        return "{" + String.join(",", ids.stream().map(String::valueOf).toList()) + "}";
    }

    /** 방문자 주·월 표시 줄 수 — 기간 시작(그 주 월요일 · 그 달 1일)을 주면 그 기간만 */
    private static long visitorMarks(String kind, Collection<String> hashes, LocalDate start) {
        String table = VisitPeriods.WEEK.equals(kind) ? "analytics_visitor_week_marks" : "analytics_visitor_month_marks";
        return start == null
                ? jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE visitor_id = ANY (?::uuid[])", Long.class, uuids(hashes))
                : jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE visitor_id = ANY (?::uuid[]) AND period_start = ?", Long.class, uuids(hashes),
                Date.valueOf(start));
    }

    private static long memberMarks(String kind, Collection<Long> ids, LocalDate start) {
        String table = VisitPeriods.WEEK.equals(kind) ? "analytics_member_week_marks" : "analytics_member_month_marks";
        return start == null
                ? jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE member_id = ANY (?::bigint[])", Long.class, longs(ids))
                : jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE member_id = ANY (?::bigint[]) AND period_start = ?", Long.class, longs(ids),
                Date.valueOf(start));
    }

    /** 숫자 표 한 칸(대상·종류·기간 시작·그 날) — 줄이 없으면 0 */
    private static long added(String audience, String kind, LocalDate start, LocalDate day) {
        return jdbc.queryForObject("SELECT coalesce(sum(added), 0) FROM analytics_unique_counts WHERE audience = ? AND period_kind = ? AND period_start = ? AND day = ?",
                Long.class, audience, kind, Date.valueOf(start), Date.valueOf(day));
    }

    /** 그 날 칸 [방문자 주, 방문자 달, 회원 주, 회원 달] — 기간 시작은 그 날이 든 주·달 */
    private static long[] addedOn(LocalDate day) {
        LocalDate w = VisitPeriods.weekStart(day);
        LocalDate m = VisitPeriods.monthStart(day);
        return new long[] {added(VisitPeriods.VISITOR, VisitPeriods.WEEK, w, day), added(VisitPeriods.VISITOR, VisitPeriods.MONTH, m, day),
                added(VisitPeriods.MEMBER, VisitPeriods.WEEK, w, day), added(VisitPeriods.MEMBER, VisitPeriods.MONTH, m, day)};
    }

    private static long[] minus(long[] a, long[] b) {
        long[] out = new long[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] - b[i];
        }
        return out;
    }

    private static boolean exists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
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

    /** 몰아쓰기가 넘긴 회원 줄을 남기고, 켜 두면 다 쓴 뒤 실패해 트랜잭션째 되돌린다 */
    static final class RecordingStore extends AnalyticsWriteStore {

        final AtomicBoolean fail = new AtomicBoolean();
        final List<List<MemberRow>> calls = new CopyOnWriteArrayList<>();

        RecordingStore(AnalyticsDatabase db) {
            super(db);
        }

        @Override
        public void visits(Collection<VisitorRow> visitors, Map<Instant, Long> pageViews, Map<String, Instant> active, Collection<MemberRow> members) {
            calls.add(List.copyOf(members));
            super.visits(visitors, pageViews, active, members);
            if (fail.get()) {
                throw new IllegalStateException("시험 — 주·월 표시까지 쓴 뒤 실패");
            }
        }

        List<MemberRow> last() {
            return calls.isEmpty() ? List.of() : calls.get(calls.size() - 1);
        }
    }

    // ── 1. 서버 여러 대 — 1명은 1명 ────────────────────────────────────────────

    @Test
    void 세_서버가_같은_방문자와_회원을_동시에_몰아써도_주_월_표시는_1줄이고_숫자_표는_한_번만_더한다() throws Exception {
        // 라운드마다 다른 주(아직 조각이 없는 기간) — 세 서버가 함께 23514 를 받고 조각을 만들어 다시 쓰는 길도 지난다
        for (int round = 0; round < 3; round++) {
            Instant at = kst(2086, 2, 5, 11, 0, 0).plus(round * 7L, ChronoUnit.DAYS); // 화요일
            LocalDate day = ServiceTime.dateOf(at);
            List<Server> servers = List.of(server(), server(), server());
            List<String> visitors = new ArrayList<>();
            List<Long> members = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                visitors.add(hash());
            }
            for (int i = 0; i < 80; i++) {
                members.add(member());
            }
            for (Server s : servers) { // 같은 방문자·회원의 신호가 세 서버에 나뉘어 들어왔다
                for (int i = 0; i < visitors.size(); i++) {
                    s.visit(visitors.get(i), at.plusSeconds(i % 60));
                }
                members.forEach(m -> s.member(m, at));
            }
            long[] before = addedOn(day);

            flushTogether(servers);

            for (Server s : servers) {
                assertThat(s.bufferEmpty()).as("몰아쓰기가 실패해 되돌린 묶음이 없다 — round %d", round).isTrue();
            }
            assertThat(visitorMarks(VisitPeriods.WEEK, visitors, VisitPeriods.weekStart(day))).isEqualTo(visitors.size());
            assertThat(visitorMarks(VisitPeriods.MONTH, visitors, VisitPeriods.monthStart(day))).isEqualTo(visitors.size());
            assertThat(memberMarks(VisitPeriods.WEEK, members, VisitPeriods.weekStart(day))).isEqualTo(members.size());
            assertThat(memberMarks(VisitPeriods.MONTH, members, VisitPeriods.monthStart(day))).isEqualTo(members.size());
            assertThat(minus(addedOn(day), before)).as("방문자·회원 각각 한 명당 +1 한 번 — round %d", round)
                    .containsExactly(visitors.size(), visitors.size(), members.size(), members.size());
        }
    }

    @Test
    void 같은_주_다른_날_다시_오면_하루_방문자는_새로_세도_주_월_숫자는_늘지_않는다() {
        Instant tue = kst(2086, 3, 12, 9, 0, 0);
        Instant thu = kst(2086, 3, 14, 20, 0, 0);
        LocalDate d1 = ServiceTime.dateOf(tue);
        LocalDate d2 = ServiceTime.dateOf(thu);
        assertThat(VisitPeriods.weekStart(d1)).isEqualTo(VisitPeriods.weekStart(d2));
        String v = hash();
        long m = member();
        Server s = server();
        long[] before1 = addedOn(d1);
        long[] before2 = addedOn(d2);

        s.visit(v, tue);
        s.member(m, tue);
        s.flusher().flush();
        assertThat(minus(addedOn(d1), before1)).containsExactly(1, 1, 1, 1);

        // 이틀 뒤 — 하루 표시는 새로 들어가지만(그날 방문자 1), 주·달 표시는 이미 있다. 회원은 다른 서버(이 기간을 기억하지 않는 새 메모리)가 받았다
        Server other = server();
        other.visit(v, thu);
        other.member(m, thu);
        other.flusher().flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_visit_marks WHERE visitor_hash = ?", Long.class, v)).as("하루 표시는 날마다").isEqualTo(2);
        assertThat(minus(addedOn(d2), before2)).as("주·달 숫자는 늘지 않는다(그 날 칸에 0)").containsExactly(0, 0, 0, 0);
        assertThat(minus(addedOn(d1), before1)).containsExactly(1, 1, 1, 1);
        assertThat(visitorMarks(VisitPeriods.WEEK, List.of(v), null)).isEqualTo(1);
        assertThat(visitorMarks(VisitPeriods.MONTH, List.of(v), null)).isEqualTo(1);
        assertThat(memberMarks(VisitPeriods.WEEK, List.of(m), null)).isEqualTo(1);
        assertThat(memberMarks(VisitPeriods.MONTH, List.of(m), null)).isEqualTo(1);
    }

    // ── 2. 기간 경계(한국 자정) ────────────────────────────────────────────────

    @Test
    void 일요일_23시59분59초와_월요일_0시는_다른_주로_세고_같은_달이면_달_숫자는_한_번() {
        Instant sun = kst(2086, 6, 23, 23, 59, 59);
        Instant mon = kst(2086, 6, 24, 0, 0, 0);
        LocalDate dSun = ServiceTime.dateOf(sun);
        LocalDate dMon = ServiceTime.dateOf(mon);
        assertThat(dSun.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(dMon.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        String v = hash();
        long m = member();
        long[] beforeSun = addedOn(dSun);
        long[] beforeMon = addedOn(dMon);
        Server s = server();
        // 같은 1분 묶음 안에 자정 앞뒤 신호
        s.visit(v, sun);
        s.visit(v, mon);
        s.member(m, sun);
        s.member(m, mon);
        s.flusher().flush();

        assertThat(visitorMarks(VisitPeriods.WEEK, List.of(v), VisitPeriods.weekStart(dSun))).isEqualTo(1);
        assertThat(visitorMarks(VisitPeriods.WEEK, List.of(v), dMon)).as("월요일 = 새 주의 시작").isEqualTo(1);
        assertThat(visitorMarks(VisitPeriods.MONTH, List.of(v), null)).isEqualTo(1);
        assertThat(memberMarks(VisitPeriods.WEEK, List.of(m), null)).isEqualTo(2);
        assertThat(memberMarks(VisitPeriods.MONTH, List.of(m), null)).isEqualTo(1);
        assertThat(minus(addedOn(dSun), beforeSun)).as("일요일 칸 — 지난 주 +1, 달 +1(처음 본 날)").containsExactly(1, 1, 1, 1);
        assertThat(minus(addedOn(dMon), beforeMon)).as("월요일 칸 — 새 주 +1, 달은 이미 셌다").containsExactly(1, 0, 1, 0);

        // 다음 1분에 회원이 또 와도(같은 서버 메모리) 새 주를 다시 세지 않는다
        s.member(m, mon.plusSeconds(60));
        s.flusher().flush();
        assertThat(minus(addedOn(dMon), beforeMon)).containsExactly(1, 0, 1, 0);
    }

    @Test
    void 말일_23시59분59초와_1일_0시는_다른_달로_세고_같은_주면_주_숫자는_한_번() {
        Instant last = kst(2086, 5, 31, 23, 59, 59); // 금요일
        Instant first = kst(2086, 6, 1, 0, 0, 0); // 토요일 — 같은 주
        LocalDate dLast = ServiceTime.dateOf(last);
        LocalDate dFirst = ServiceTime.dateOf(first);
        assertThat(VisitPeriods.weekStart(dLast)).isEqualTo(VisitPeriods.weekStart(dFirst));
        String v = hash();
        long m = member();
        long[] beforeLast = addedOn(dLast);
        long[] beforeFirst = addedOn(dFirst);
        Server s = server();
        s.visit(v, last);
        s.visit(v, first);
        s.member(m, last);
        s.member(m, first);
        s.flusher().flush();

        assertThat(visitorMarks(VisitPeriods.MONTH, List.of(v), LocalDate.of(2086, 5, 1))).isEqualTo(1);
        assertThat(visitorMarks(VisitPeriods.MONTH, List.of(v), LocalDate.of(2086, 6, 1))).isEqualTo(1);
        assertThat(visitorMarks(VisitPeriods.WEEK, List.of(v), null)).isEqualTo(1);
        assertThat(memberMarks(VisitPeriods.MONTH, List.of(m), null)).isEqualTo(2);
        assertThat(memberMarks(VisitPeriods.WEEK, List.of(m), null)).isEqualTo(1);
        assertThat(minus(addedOn(dLast), beforeLast)).as("말일 칸 — 주 +1, 5월 +1").containsExactly(1, 1, 1, 1);
        assertThat(minus(addedOn(dFirst), beforeFirst)).as("1일 칸 — 6월 +1, 주는 이미 셌다").containsExactly(0, 1, 0, 1);
    }

    @Test
    void 일요일_말일에서_월요일_1일로_넘어가면_주와_달_모두_새로_센다() {
        Instant sun = kst(2086, 6, 30, 23, 59, 59);
        Instant mon = kst(2086, 7, 1, 0, 0, 0);
        LocalDate dSun = ServiceTime.dateOf(sun);
        LocalDate dMon = ServiceTime.dateOf(mon);
        assertThat(dSun.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(dMon).isEqualTo(LocalDate.of(2086, 7, 1));
        String v = hash();
        long m = member();
        long[] beforeSun = addedOn(dSun);
        long[] beforeMon = addedOn(dMon);
        Server s = server();
        s.visit(v, sun);
        s.visit(v, mon);
        s.member(m, sun);
        s.member(m, mon);
        s.flusher().flush();

        assertThat(minus(addedOn(dSun), beforeSun)).containsExactly(1, 1, 1, 1);
        assertThat(minus(addedOn(dMon), beforeMon)).containsExactly(1, 1, 1, 1);
        assertThat(visitorMarks(VisitPeriods.WEEK, List.of(v), null)).isEqualTo(2);
        assertThat(visitorMarks(VisitPeriods.MONTH, List.of(v), null)).isEqualTo(2);
    }

    // ── 3. 조각 없음(23514) ───────────────────────────────────────────────────

    @Test
    void 기간_조각이_없으면_저장소는_23514로_거절하고_몰아쓰기는_조각을_만들어_다시_써서_넣는다() throws Exception {
        // 2087년 9월 둘째 주 — 다른 시험이 만들지 않는 기간
        Instant at = kst(2087, 9, 9, 15, 0, 0);
        LocalDate day = ServiceTime.dateOf(at);
        String week = "analytics_visitor_week_marks_" + VisitPeriods.weekStart(day).toString().replace("-", "");
        String month = "analytics_member_month_marks_" + day.toString().substring(0, 7).replace("-", "");
        assertThat(exists(week)).as("시험 앞 — 그 주 조각이 아직 없다").isFalse();
        assertThat(exists(month)).isFalse();
        // 하루 표시 조각은 미리 — 거절 원인이 주·월 표시 조각이 되게
        jdbc.queryForObject("SELECT analytics_ensure_visit_mark_partitions(ARRAY[?]::date[])", Integer.class, Date.valueOf(day));
        String v = hash();
        long m = member();

        // 저장소를 직접 부르면(한 트랜잭션) 주·월 표시 넣기가 23514 로 깨지고 하루 표시·시간 칸도 함께 되돌려진다
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            AnalyticsWriteStore direct = new AnalyticsWriteStore(new AnalyticsDatabase(new SingleConnectionDataSource(c, true), false));
            Throwable t = catchThrowable(() -> direct.visits(List.of(new VisitorRow(day, v, at, false)), Map.of(at, 1L), Map.of(), List.of(new MemberRow(day, m, true, true))));
            c.rollback();
            assertThat(sqlState(t)).isEqualTo("23514");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_visit_marks WHERE visitor_hash = ?", Long.class, v)).isZero();

        long[] before = addedOn(day);
        Server s = server();
        s.visit(v, at);
        s.member(m, at);
        s.flusher().flush();

        assertThat(s.bufferEmpty()).as("한 번 만들고 다시 써서 성공했다 — 되돌린 묶음 없음").isTrue();
        assertThat(exists(week)).isTrue();
        assertThat(exists(month)).isTrue();
        assertThat(visitorMarks(VisitPeriods.WEEK, List.of(v), null)).isEqualTo(1);
        assertThat(memberMarks(VisitPeriods.MONTH, List.of(m), null)).isEqualTo(1);
        assertThat(minus(addedOn(day), before)).containsExactly(1, 1, 1, 1);
    }

    private static String sqlState(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException e && e.getSQLState() != null) {
                return e.getSQLState();
            }
        }
        return null;
    }

    // ── 4. 회원 메모리(VisitMemberPeriods) ────────────────────────────────────

    @Test
    void 회원_메모리는_커밋된_뒤에만_기억하고_실패한_줄은_다시_넣으며_새_서버는_DB_ON_CONFLICT로_두_번_세지_않는다() {
        Instant at = kst(2086, 10, 7, 10, 0, 0);
        LocalDate day = ServiceTime.dateOf(at);
        long m = member();
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        RecordingStore store = new RecordingStore(db);
        Server s = server(store, db);
        long[] before = addedOn(day);

        // ① 쓰기가 실패한다 — 표시·숫자가 남지 않고, 메모리도 「넣음」으로 기억하지 않는다
        store.fail.set(true);
        s.member(m, at);
        s.flusher().flush();
        assertThat(memberMarks(VisitPeriods.WEEK, List.of(m), null)).isZero();
        assertThat(minus(addedOn(day), before)).containsExactly(0, 0, 0, 0);

        // ② 되돌린 묶음을 다음 1분에 다시 쓴다 — 그 회원을 다시 넣어 본다(기억했다면 빠졌을 것)
        store.fail.set(false);
        s.flusher().flush();
        assertThat(store.last()).containsExactly(new MemberRow(day, m, true, true));
        assertThat(memberMarks(VisitPeriods.WEEK, List.of(m), null)).isEqualTo(1);
        assertThat(memberMarks(VisitPeriods.MONTH, List.of(m), null)).isEqualTo(1);
        assertThat(minus(addedOn(day), before)).containsExactly(0, 0, 1, 1);

        // ③ 커밋 뒤에는 기억한다 — 같은 서버에 같은 기간 신호가 또 와도 넣어 보지 않는다(같은 주 다른 날도)
        int calls = store.calls.size();
        s.member(m, at.plusSeconds(120));
        s.member(m, at.plus(2, ChronoUnit.DAYS));
        s.flusher().flush(); // 모은 회원 줄이 있어 몰아쓰기는 일어난다(넘기는 줄만 빈다)
        assertThat(store.calls.size()).isEqualTo(calls + 1);
        assertThat(store.last()).as("이미 넣은 회원은 이 서버에서 빠진다").isEmpty();

        // ④ 서버를 새로 띄우면(메모리 비어 있음) 다시 넣어 보지만 DB 의 ON CONFLICT 가 받지 않는다 — 숫자는 그대로
        RecordingStore store2 = new RecordingStore(db);
        Server restarted = server(store2, db);
        restarted.member(m, at.plus(1, ChronoUnit.DAYS));
        restarted.flusher().flush();
        assertThat(store2.last()).as("새 서버는 넣어 본다").containsExactly(new MemberRow(day.plusDays(1), m, true, true));
        assertThat(memberMarks(VisitPeriods.WEEK, List.of(m), null)).isEqualTo(1);
        assertThat(memberMarks(VisitPeriods.MONTH, List.of(m), null)).isEqualTo(1);
        assertThat(minus(addedOn(day), before)).containsExactly(0, 0, 1, 1);
        assertThat(minus(addedOn(day.plusDays(1)), new long[4])).as("다음 날 칸에도 더하지 않는다").containsExactly(0, 0, 0, 0);
    }

    @Test
    void 회원_메모리는_주가_바뀌면_새_주만_넣어_보고_달은_그대로_기억한다() {
        VisitMemberPeriods periods = new VisitMemberPeriods();
        LocalDate wed = LocalDate.of(2086, 10, 16);
        LocalDate nextMon = LocalDate.of(2086, 10, 21);
        assertThat(wed.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(nextMon.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        periods.committed(periods.rows(List.of(new VisitBuffer.MemberKey(wed, 7L))));
        assertThat(periods.rows(List.of(new VisitBuffer.MemberKey(wed.plusDays(1), 7L)))).as("같은 주·같은 달 — 넣어 볼 것 없음").isEmpty();
        assertThat(periods.rows(List.of(new VisitBuffer.MemberKey(nextMon, 7L)))).containsExactly(new MemberRow(nextMon, 7L, true, false));
        assertThat(periods.rows(List.of(new VisitBuffer.MemberKey(nextMon, 8L)))).as("다른 회원은 둘 다").containsExactly(new MemberRow(nextMon, 8L, true, true));
    }
}
