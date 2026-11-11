package com.community.analytics.store;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

/**
 * 방문 몰아쓰기의 SQL(analytics 스트림 표 — analytics V1). 모두 <b>여러 줄을 한 문장</b>({@code unnest} 배열)으로 쓴다 — 문장 수는 방문량과 무관하다. 분석 전용 연결({@code analyticsJdbc}).
 * 트랜잭션은 부르는 쪽({@code VisitFlusher})이 {@link AnalyticsDatabase#inTransaction} 으로 연다.
 *
 * <p><b>서버 여러 대 정확성</b>(API 서버 2~3대 전제):
 * <ul>
 *   <li>방문자: {@link #visits} 의 표시 넣기가 {@code ON CONFLICT DO NOTHING RETURNING} 이라 <b>실제로 들어간 줄만</b> 방문자로 더한다. 같은 방문자를 두 서버가 같은 1분에 넣으면
 *       기본 키(day, visitor_hash)가 한 서버만 받는다 — 동시면 PostgreSQL 이 뒤 서버를 앞 서버의 커밋까지 기다리게 하고 그 줄을 건너뛴다(앞 서버가 되돌리면 뒤 서버가 넣는다).
 *       넣기와 시간 칸 더하기가 한 트랜잭션이라 표시만 남고 숫자가 빠지는 일이 없다.
 *       신규 방문자: 표시에 「신규」 칸(analytics V2)을 두고, 이미 있는 표시라도 늦게 온 신호가 신규면 false → true 로 바꾸면서 그때만 신규 +1 —
 *       첫 화면(신규)은 서버 A, 다음 화면은 서버 B 가 받고 B 가 먼저 써도 신규가 빠지지 않는다(QA 재현 2026-11-04). 표시 표가 파티션이라 넣기·바꾸기를 두 문장으로 나눈다(3단계).</li>
 *   <li>페이지뷰·페이지별·유입처별·이동 횟수: 신호 하나는 서버 한 대만 받으므로 서버마다 자기 몫을 더해도 합이 정확하다. 더하기 upsert 는 행 잠금으로 차례가 정해진다.</li>
 *   <li>교착 방지: 모든 넣기를 키 차례({@code ORDER BY})로 해서 서버끼리 같은 행을 잠그는 차례가 같다.</li>
 *   <li>지금 접속: 방문자 키 한 줄에 마지막 시각을 {@code greatest} 로 — 늦게 도착한 옛 시각이 덮어쓰지 않는다. 세기는 DB 에서 한 번(관리자 대시보드).</li>
 * </ul>
 */
@Repository
public class AnalyticsWriteStore {

    /*
     * 방문자 몰아쓰기는 한 트랜잭션 안의 세 문장이다(3단계 — analytics V6 로 표시 표가 하루 파티션이 되면서 바꿈).
     * 예전 한 문장은 「새로 넣었나」를 RETURNING (xmax = 0) 으로 갈랐는데, PostgreSQL 은 파티션 표(부모)에 넣을 때 RETURNING 에서 시스템 칸을 꺼내지 못한다(0A000 —
     * QA 2026-11-11 재현: 몰아쓰기가 매번 되돌려져 방문자·페이지뷰·지금 접속이 하나도 쌓이지 않았다). 그래서:
     *   ① MARK_INSERT: ON CONFLICT DO NOTHING RETURNING — 돌려받은 줄 = 이번에 새로 넣은 표시(= 방문자 +1). 동시에 같은 키를 넣는 다른 서버가 있으면 그 커밋까지 기다렸다 건너뛴다.
     *   ② MARK_FLIP: ①에서 새로 넣지 못한 신규 신호만 「false → true」로 바꾼다(RETURNING 일반 칸). 다음 문장이라 READ COMMITTED 의 새 스냅숏으로 ①이 기다린 다른 서버의 표시도 본다
     *      — 첫 화면(신규)은 서버 A, 다음 화면은 서버 B 가 받고 B 가 먼저 써도 신규가 빠지지 않는다(QA 재현 2026-11-04). 잠그는 차례는 키 차례(FOR UPDATE … ORDER BY).
     *   ③ HOURLY: 시간 칸별 방문자·신규·페이지뷰를 자바에서 센 값으로 한 문장 더하기.
     */
    private static final String MARK_INSERT = "INSERT INTO analytics_visit_marks (day, visitor_hash, new_visitor) "
            + "SELECT t.day, t.visitor_hash, t.new_visitor FROM unnest(?::date[], ?::text[], ?::boolean[]) AS t(day, visitor_hash, new_visitor) ORDER BY t.day, t.visitor_hash "
            + "ON CONFLICT (day, visitor_hash) DO NOTHING RETURNING day, visitor_hash";

    private static final String MARK_FLIP = "WITH target AS ("
            + "  SELECT m.day, m.visitor_hash FROM analytics_visit_marks m"
            + "    JOIN unnest(?::date[], ?::text[]) AS t(day, visitor_hash) ON m.day = t.day AND m.visitor_hash = t.visitor_hash"
            + "   WHERE NOT m.new_visitor ORDER BY m.day, m.visitor_hash FOR UPDATE OF m"
            + ") "
            + "UPDATE analytics_visit_marks m SET new_visitor = true FROM target x "
            + " WHERE m.day = x.day AND m.visitor_hash = x.visitor_hash AND NOT m.new_visitor RETURNING m.day, m.visitor_hash";

    private static final String HOURLY = "INSERT INTO analytics_visit_hourly (hour, visitors, new_visitors, page_views) "
            + "SELECT t.h, t.v, t.n, t.p FROM unnest(?::timestamptz[], ?::bigint[], ?::bigint[], ?::bigint[]) AS t(h, v, n, p) ORDER BY t.h "
            + "ON CONFLICT (hour) DO UPDATE SET visitors = analytics_visit_hourly.visitors + EXCLUDED.visitors, "
            + "new_visitors = analytics_visit_hourly.new_visitors + EXCLUDED.new_visitors, page_views = analytics_visit_hourly.page_views + EXCLUDED.page_views";

    /*
     * 주·월 순방문자(3단계 결정 ④ — analytics V8). 같은 트랜잭션에서 ③ 다음에:
     *   ④ 방문자 주 표시 · ⑤ 방문자 달 표시 — ① 에서 새로 넣은 하루 표시(그날 처음 본 방문자)만 넣어 본다. 키 = (기간 시작, 해시 앞 16바이트 uuid)
     *   ⑥ 회원 주 표시 · ⑦ 회원 달 표시 — 부르는 쪽이 서버 메모리의 「이번 기간에 이미 넣은 회원」을 뺀 줄만 준다. 키 = (기간 시작, 회원 번호)
     *   ⑧ 숫자 표 — ④~⑦ 에서 돌려받은(= 실제로 새로 들어간) 줄을 (대상, 종류, 기간 시작, 그 날) 칸에 +1, 한 문장
     * 표시 넣기는 ① 과 같이 ON CONFLICT DO NOTHING RETURNING 일반 칸(파티션 표라 시스템 칸을 쓰지 않는다) — 같은 키를 다른 서버가 동시에 넣으면 그 커밋까지 기다렸다 건너뛴다(1명은 1명).
     * 모두 키 차례(ORDER BY)로 넣고 문장 차례도 서버마다 같아 서로 잠그는 차례가 같다. 그 기간 조각이 없으면 23514(부르는 쪽이 조각을 만들고 다시).
     */
    private static final String VISITOR_WEEK = periodMarks("analytics_visitor_week_marks", "visitor_id", "uuid");
    private static final String VISITOR_MONTH = periodMarks("analytics_visitor_month_marks", "visitor_id", "uuid");
    private static final String MEMBER_WEEK = periodMarks("analytics_member_week_marks", "member_id", "bigint");
    private static final String MEMBER_MONTH = periodMarks("analytics_member_month_marks", "member_id", "bigint");

    private static final String UNIQUE_COUNTS = "INSERT INTO analytics_unique_counts (audience, period_kind, period_start, day, added) "
            + "SELECT t.a, t.k, t.p, t.d, t.n FROM unnest(?::text[], ?::text[], ?::date[], ?::date[], ?::bigint[]) AS t(a, k, p, d, n) ORDER BY t.a, t.k, t.p, t.d "
            + "ON CONFLICT (audience, period_kind, period_start, day) DO UPDATE SET added = analytics_unique_counts.added + EXCLUDED.added";

    private static String periodMarks(String table, String column, String type) {
        return "INSERT INTO " + table + " (period_start, " + column + ") "
                + "SELECT t.p, t.v FROM unnest(?::date[], ?::" + type + "[]) AS t(p, v) ORDER BY t.p, t.v "
                + "ON CONFLICT (period_start, " + column + ") DO NOTHING RETURNING period_start, " + column;
    }

    private static final String ACTIVE = "INSERT INTO analytics_visit_active (visitor_hash, last_seen) "
            + "SELECT t.h, t.s FROM unnest(?::text[], ?::timestamptz[]) AS t(h, s) ORDER BY t.h "
            + "ON CONFLICT (visitor_hash) DO UPDATE SET last_seen = greatest(analytics_visit_active.last_seen, EXCLUDED.last_seen)";

    private static final String RAW = "INSERT INTO analytics_page_views (id, viewed_at, visitor_hash, session_hash, path, kind, prev_path, referrer_domain, referrer_group, "
            + "utm_source, utm_medium, utm_campaign, device, new_visitor) "
            + "SELECT * FROM unnest(?::bigint[], ?::timestamptz[], ?::text[], ?::text[], ?::text[], ?::text[], ?::text[], ?::text[], ?::text[], ?::text[], ?::text[], "
            + "?::text[], ?::text[], ?::boolean[]) ON CONFLICT DO NOTHING";

    private static final String PAGES = "INSERT INTO analytics_page_daily (day, path, kind, views) "
            + "SELECT t.d, t.p, t.k, t.n FROM unnest(?::date[], ?::text[], ?::text[], ?::bigint[]) AS t(d, p, k, n) ORDER BY t.d, t.p, t.k "
            + "ON CONFLICT (day, path, kind) DO UPDATE SET views = analytics_page_daily.views + EXCLUDED.views";

    private static final String REFERRERS = "INSERT INTO analytics_referrer_daily (day, referrer_group, referrer_domain, visits) "
            + "SELECT t.d, t.g, t.m, t.n FROM unnest(?::date[], ?::text[], ?::text[], ?::bigint[]) AS t(d, g, m, n) ORDER BY t.d, t.g, t.m "
            + "ON CONFLICT (day, referrer_group, referrer_domain) DO UPDATE SET visits = analytics_referrer_daily.visits + EXCLUDED.visits";

    private static final String FLOWS = "INSERT INTO analytics_flow_daily (day, from_path, to_path, moves) "
            + "SELECT t.d, t.f, t.g, t.n FROM unnest(?::date[], ?::text[], ?::text[], ?::bigint[]) AS t(d, f, g, n) ORDER BY t.d, t.f, t.g "
            + "ON CONFLICT (day, from_path, to_path) DO UPDATE SET moves = analytics_flow_daily.moves + EXCLUDED.moves";

    /** 캠페인 일별(analytics V3) — 빈 utm 칸은 '' 로 온다(부르는 쪽) */
    private static final String CAMPAIGNS = "INSERT INTO analytics_campaign_daily (day, utm_source, utm_medium, utm_campaign, visits) "
            + "SELECT t.d, t.s, t.m, t.c, t.n FROM unnest(?::date[], ?::text[], ?::text[], ?::text[], ?::bigint[]) AS t(d, s, m, c, n) ORDER BY t.d, t.s, t.m, t.c "
            + "ON CONFLICT (day, utm_source, utm_medium, utm_campaign) DO UPDATE SET visits = analytics_campaign_daily.visits + EXCLUDED.visits";

    // ── 2단계(analytics V4) — 환경·검색어·봇·수집 진단 ───────────────────────────
    // 모두 서버마다 자기 몫을 더하는 upsert 다(신호·요청 하나는 서버 한 대만 받는다 — 유입처·이동과 같은 근거). 키 차례로 넣어 서버끼리 잠그는 차례가 같다.

    private static final String ENV = "INSERT INTO analytics_env_daily (day, device, browser, os, screen, visits) "
            + "SELECT t.d, t.v, t.b, t.o, t.s, t.n FROM unnest(?::date[], ?::text[], ?::text[], ?::text[], ?::text[], ?::bigint[]) AS t(d, v, b, o, s, n) "
            + "ORDER BY t.d, t.v, t.b, t.o, t.s "
            + "ON CONFLICT (day, device, browser, os, screen) DO UPDATE SET visits = analytics_env_daily.visits + EXCLUDED.visits";

    private static final String SEARCHES = "INSERT INTO analytics_search_daily (day, term, searches, no_result) "
            + "SELECT t.d, t.t, t.n, t.z FROM unnest(?::date[], ?::text[], ?::bigint[], ?::bigint[]) AS t(d, t, n, z) ORDER BY t.d, t.t "
            + "ON CONFLICT (day, term) DO UPDATE SET searches = analytics_search_daily.searches + EXCLUDED.searches, "
            + "no_result = analytics_search_daily.no_result + EXCLUDED.no_result";

    private static final String SEARCH_FILTERED = "INSERT INTO analytics_search_filtered_daily (day, searches) "
            + "SELECT t.d, t.n FROM unnest(?::date[], ?::bigint[]) AS t(d, n) ORDER BY t.d "
            + "ON CONFLICT (day) DO UPDATE SET searches = analytics_search_filtered_daily.searches + EXCLUDED.searches";

    private static final String BOTS = "INSERT INTO analytics_bot_daily (day, kind, requests) "
            + "SELECT t.d, t.k, t.n FROM unnest(?::date[], ?::text[], ?::bigint[]) AS t(d, k, n) ORDER BY t.d, t.k "
            + "ON CONFLICT (day, kind) DO UPDATE SET requests = analytics_bot_daily.requests + EXCLUDED.requests";

    /**
     * 수집 진단 한 문장 — 「살아 있음」(서버별 마지막 기록 시각, 늦게 온 옛 시각이 덮지 않게 greatest) + 실패 줄 넣기 + 시간 칸 더하기.
     * PostgreSQL 은 WITH 안의 쓰기 문장을 바깥에서 쓰지 않아도 한 번씩 실행한다. 칸 수는 서버 수와 같아 작다(서버 한 대가 한 줄씩).
     */
    private static final String DIAGNOSTICS = "WITH beat AS ("
            + "  INSERT INTO analytics_collector_beats (instance, last_write_at) VALUES (?, ?)"
            + "  ON CONFLICT (instance) DO UPDATE SET last_write_at = greatest(analytics_collector_beats.last_write_at, EXCLUDED.last_write_at)"
            + "), fails AS ("
            + "  INSERT INTO analytics_collector_failures (failed_at, instance, reason, detail)"
            + "  SELECT t.f, ?, t.r, t.d FROM unnest(?::timestamptz[], ?::text[], ?::text[]) AS t(f, r, d)"
            + ") "
            + "INSERT INTO analytics_collector_hourly (hour, instance, flush_ok, flush_fail, dropped, ignored_bot, ignored_invalid, ignored_excluded, unknown_path) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (hour, instance) DO UPDATE SET flush_ok = analytics_collector_hourly.flush_ok + EXCLUDED.flush_ok, "
            + "flush_fail = analytics_collector_hourly.flush_fail + EXCLUDED.flush_fail, dropped = analytics_collector_hourly.dropped + EXCLUDED.dropped, "
            + "ignored_bot = analytics_collector_hourly.ignored_bot + EXCLUDED.ignored_bot, "
            + "ignored_invalid = analytics_collector_hourly.ignored_invalid + EXCLUDED.ignored_invalid, "
            + "ignored_excluded = analytics_collector_hourly.ignored_excluded + EXCLUDED.ignored_excluded, "
            + "unknown_path = analytics_collector_hourly.unknown_path + EXCLUDED.unknown_path";

    /** 캠페인 한 줄 — 그 날 · utm 세 칸(빈 칸 '') · 더할 방문 수 */
    public record CampaignRow(LocalDate day, String source, String medium, String campaign, long count) {
    }

    /** 환경 한 줄 — 그 날 · 기기 · 브라우저 · OS · 화면 구간 · 더할 방문 수 */
    public record EnvRow(LocalDate day, String device, String browser, String os, String screen, long count) {
    }

    /** 검색어 한 줄 — 그 날 · 다듬은 검색어 · 더할 검색 수 · 그중 결과 없는 수 */
    public record SearchRow(LocalDate day, String term, long searches, long noResult) {
    }

    /** 기록 실패 한 줄 — 사유 코드(DB_TIMEOUT · DB_UNAVAILABLE · CONSTRAINT · UNKNOWN) · 짧은 설명(예외 이름 — 개인 정보 없음) */
    public record FailureRow(Instant at, String reason, String detail) {
    }

    /** 수집 진단의 1분 몫 — {@code ignoredExcluded}(뺄 계정 신호)·{@code unknownPath}(「알 수 없는 주소」로 묶은 신호)는 analytics V5 */
    public record Diagnostics(String instance, Instant at, Instant hour, long flushOk, long flushFail, long dropped, long ignoredBot, long ignoredInvalid,
                              long ignoredExcluded, long unknownPath, List<FailureRow> failures) {

        /** 2단계 칸만 주는 모양(3단계 두 칸 = 0) — 2단계 시험이 그대로 쓴다 */
        public Diagnostics(String instance, Instant at, Instant hour, long flushOk, long flushFail, long dropped, long ignoredBot, long ignoredInvalid,
                           List<FailureRow> failures) {
            this(instance, at, hour, flushOk, flushFail, dropped, ignoredBot, ignoredInvalid, 0, 0, failures);
        }
    }

    /** 방문자 줄 — 그 날 · 방문자 키 · 이 1분에 처음 본 정시 · 신규 방문자 신호가 있었나 */
    public record VisitorRow(LocalDate day, String visitorHash, Instant hour, boolean newVisitor) {
    }

    /** 로그인 회원 줄(3단계 결정 ④) — 그 날 · 회원 번호 · 주 표시를 넣어 볼까 · 달 표시를 넣어 볼까(서버 메모리에 「이번 기간에 이미 넣음」이면 false) */
    public record MemberRow(LocalDate day, long memberId, boolean week, boolean month) {
    }

    /** 원본 한 줄 */
    public record RawRow(long id, Instant viewedAt, String visitorHash, String sessionHash, String path, String kind, String prevPath, String referrerDomain,
                         String referrerGroup, String utmSource, String utmMedium, String utmCampaign, String device, boolean newVisitor) {
    }

    /** 일별 더하기 한 줄 — 키 세 칸 + 더할 수 */
    public record DailyRow(LocalDate day, String a, String b, long count) {
    }

    private final JdbcTemplate jdbc;

    public AnalyticsWriteStore(AnalyticsDatabase db) {
        this.jdbc = db.jdbc();
    }

    /** 표시 키 — 한국 날짜 + 방문자 해시 */
    private record MarkKey(LocalDate day, String visitorHash) {
    }

    /** 주·월 표시 키 — 기간 시작 + 대상(방문자 uuid 글자 · 회원 번호 글자 — DB 가 돌려주는 글자와 같은 모양) */
    private record PeriodKey(LocalDate start, String id) {
    }

    /** 숫자 표 칸 */
    private record CountKey(String audience, String kind, LocalDate start, LocalDate day) {
    }

    /** 표시 넣기 — 이번에 새로 들어간 키(키마다 처음 본 날을 {@code firstDay} 에서 찾아 숫자 표 칸에 +1) */
    private void insertPeriodMarks(String sql, String audience, String kind, Map<PeriodKey, LocalDate> firstDay, Map<CountKey, Long> counts) {
        if (firstDay.isEmpty()) {
            return;
        }
        List<PeriodKey> keys = new ArrayList<>(firstDay.keySet());
        List<PeriodKey> inserted = jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setArray(1, SqlArrays.text(con, keys.stream().map(PeriodKey::start).toList()));
            ps.setArray(2, SqlArrays.text(con, keys.stream().map(PeriodKey::id).toList()));
            return ps;
        }, (rs, i) -> new PeriodKey(rs.getObject(1, LocalDate.class), rs.getString(2)));
        for (PeriodKey k : inserted) {
            LocalDate day = firstDay.get(k);
            if (day != null) {
                counts.merge(new CountKey(audience, kind, k.start(), day), 1L, Long::sum);
            }
        }
    }

    /** 같은 기간 키가 여러 날로 오면 이른 날로 */
    private static void earliest(Map<PeriodKey, LocalDate> map, PeriodKey key, LocalDate day) {
        map.merge(key, day, (a, b) -> b.isBefore(a) ? b : a);
    }

    /**
     * 주·월 순방문자 표시 넣기 + 숫자 표 더하기(위 ④~⑧). 방문자는 이번에 새로 넣은 하루 표시만, 회원은 부르는 쪽이 고른 줄만.
     */
    private void uniques(Collection<MarkKey> newVisitors, Collection<MemberRow> members) {
        Map<PeriodKey, LocalDate> visitorWeeks = new LinkedHashMap<>();
        Map<PeriodKey, LocalDate> visitorMonths = new LinkedHashMap<>();
        for (MarkKey k : newVisitors) {
            UUID id = VisitPeriods.visitorId(k.visitorHash());
            if (id == null) {
                continue;
            }
            earliest(visitorWeeks, new PeriodKey(VisitPeriods.weekStart(k.day()), id.toString()), k.day());
            earliest(visitorMonths, new PeriodKey(VisitPeriods.monthStart(k.day()), id.toString()), k.day());
        }
        Map<PeriodKey, LocalDate> memberWeeks = new LinkedHashMap<>();
        Map<PeriodKey, LocalDate> memberMonths = new LinkedHashMap<>();
        for (MemberRow m : members) {
            String id = Long.toString(m.memberId());
            if (m.week()) {
                earliest(memberWeeks, new PeriodKey(VisitPeriods.weekStart(m.day()), id), m.day());
            }
            if (m.month()) {
                earliest(memberMonths, new PeriodKey(VisitPeriods.monthStart(m.day()), id), m.day());
            }
        }
        Map<CountKey, Long> counts = new TreeMap<>(Comparator.comparing(CountKey::audience).thenComparing(CountKey::kind).thenComparing(CountKey::start)
                .thenComparing(CountKey::day));
        insertPeriodMarks(VISITOR_WEEK, VisitPeriods.VISITOR, VisitPeriods.WEEK, visitorWeeks, counts);
        insertPeriodMarks(VISITOR_MONTH, VisitPeriods.VISITOR, VisitPeriods.MONTH, visitorMonths, counts);
        insertPeriodMarks(MEMBER_WEEK, VisitPeriods.MEMBER, VisitPeriods.WEEK, memberWeeks, counts);
        insertPeriodMarks(MEMBER_MONTH, VisitPeriods.MEMBER, VisitPeriods.MONTH, memberMonths, counts);
        if (counts.isEmpty()) {
            return;
        }
        List<CountKey> keys = new ArrayList<>(counts.keySet());
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(UNIQUE_COUNTS);
            ps.setArray(1, SqlArrays.text(con, keys.stream().map(CountKey::audience).toList()));
            ps.setArray(2, SqlArrays.text(con, keys.stream().map(CountKey::kind).toList()));
            ps.setArray(3, SqlArrays.text(con, keys.stream().map(CountKey::start).toList()));
            ps.setArray(4, SqlArrays.text(con, keys.stream().map(CountKey::day).toList()));
            ps.setArray(5, SqlArrays.text(con, keys.stream().map(counts::get).toList()));
            return ps;
        });
    }

    /** 같은 표시 키 두 줄 중 남길 것 — 신규 우선, 같으면 이른 시간 칸 */
    private static VisitorRow preferred(VisitorRow a, VisitorRow b) {
        if (a.newVisitor() != b.newVisitor()) {
            return a.newVisitor() ? a : b;
        }
        return b.hour().isBefore(a.hour()) ? b : a;
    }

    /** ① 표시 넣기 — 이번에 새로 들어간 표시 키 */
    private Set<MarkKey> insertMarks(List<VisitorRow> rows) {
        List<MarkKey> keys = jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(MARK_INSERT);
            ps.setArray(1, SqlArrays.text(con, rows.stream().map(VisitorRow::day).toList()));
            ps.setArray(2, SqlArrays.text(con, rows.stream().map(VisitorRow::visitorHash).toList()));
            ps.setArray(3, SqlArrays.text(con, rows.stream().map(VisitorRow::newVisitor).toList()));
            return ps;
        }, (rs, i) -> new MarkKey(rs.getObject("day", LocalDate.class), rs.getString("visitor_hash")));
        return new HashSet<>(keys);
    }

    /** ② 이미 있던 표시 중 「신규 아님」을 신규로 — 실제로 바뀐 표시 키 */
    private Set<MarkKey> flipMarks(List<VisitorRow> rows) {
        List<MarkKey> keys = jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(MARK_FLIP);
            ps.setArray(1, SqlArrays.text(con, rows.stream().map(VisitorRow::day).toList()));
            ps.setArray(2, SqlArrays.text(con, rows.stream().map(VisitorRow::visitorHash).toList()));
            return ps;
        }, (rs, i) -> new MarkKey(rs.getObject("day", LocalDate.class), rs.getString("visitor_hash")));
        return new HashSet<>(keys);
    }

    /**
     * 방문자·신규·페이지뷰(표시 넣기 → 신규 바꾸기 → 시간 칸 더하기, 세 문장 — 위 설명) + 지금 접속(한 문장). 부르는 쪽이 한 트랜잭션으로 연다.
     * 그 날 표시 조각이 없으면 ①이 23514 로 거절된다(부르는 쪽이 {@link #ensureMarkPartitions} 뒤 다시). 로그인 회원 없이 — {@link #visits(Collection, Map, Map, Collection)}.
     */
    public void visits(Collection<VisitorRow> visitors, Map<Instant, Long> pageViews, Map<String, Instant> active) {
        visits(visitors, pageViews, active, List.of());
    }

    /**
     * {@link #visits(Collection, Map, Map)} + 주·월 순방문자(3단계 결정 ④ — 위 ④~⑧): ① 에서 새로 넣은 하루 표시의 방문자와 {@code members} 의 회원을 주·달 표시에 넣어 보고
     * 새로 들어간 줄만 숫자 표에 더한다. 모두 부르는 쪽의 한 트랜잭션 안. 그 기간 표시 조각이 없으면 23514(부르는 쪽이 {@link #ensurePeriodMarkPartitions} 뒤 다시).
     */
    public void visits(Collection<VisitorRow> visitors, Map<Instant, Long> pageViews, Map<String, Instant> active, Collection<MemberRow> members) {
        Set<MarkKey> newMarks = Set.of();
        if (!visitors.isEmpty() || !pageViews.isEmpty()) {
            // 같은 (날짜, 방문자)가 두 번 오면 하나로 — 신규 우선, 이른 시간 칸
            Map<MarkKey, VisitorRow> unique = new LinkedHashMap<>();
            for (VisitorRow v : visitors) {
                unique.merge(new MarkKey(v.day(), v.visitorHash()), v, AnalyticsWriteStore::preferred);
            }
            Set<MarkKey> inserted = unique.isEmpty() ? Set.of() : insertMarks(new ArrayList<>(unique.values()));
            newMarks = inserted;
            List<VisitorRow> flipCandidates = unique.entrySet().stream()
                    .filter(e -> e.getValue().newVisitor() && !inserted.contains(e.getKey())).map(Map.Entry::getValue).toList();
            Set<MarkKey> flipped = flipCandidates.isEmpty() ? Set.of() : flipMarks(flipCandidates);

            // 시간 칸마다 [방문자, 신규, 페이지뷰] — 새로 넣은 표시만 방문자, 「새로 넣은 신규」와 「false → true 로 바뀐 표시」만 신규
            Map<Instant, long[]> perHour = new TreeMap<>();
            unique.forEach((k, v) -> {
                boolean in = inserted.contains(k);
                boolean isNew = (in && v.newVisitor()) || flipped.contains(k);
                if (in || isNew) {
                    long[] c = perHour.computeIfAbsent(v.hour(), h -> new long[3]);
                    if (in) {
                        c[0]++;
                    }
                    if (isNew) {
                        c[1]++;
                    }
                }
            });
            pageViews.forEach((h, n) -> perHour.computeIfAbsent(h, x -> new long[3])[2] += n);
            if (!perHour.isEmpty()) {
                List<Instant> hours = new ArrayList<>(perHour.keySet());
                jdbc.update(con -> {
                    PreparedStatement ps = con.prepareStatement(HOURLY);
                    ps.setArray(1, SqlArrays.text(con, hours));
                    ps.setArray(2, SqlArrays.text(con, hours.stream().map(h -> perHour.get(h)[0]).toList()));
                    ps.setArray(3, SqlArrays.text(con, hours.stream().map(h -> perHour.get(h)[1]).toList()));
                    ps.setArray(4, SqlArrays.text(con, hours.stream().map(h -> perHour.get(h)[2]).toList()));
                    return ps;
                });
            }
        }
        if (!newMarks.isEmpty() || !members.isEmpty()) {
            uniques(newMarks, members);
        }
        if (!active.isEmpty()) {
            List<String> keys = new ArrayList<>(active.keySet());
            List<Instant> seen = keys.stream().map(active::get).toList();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(ACTIVE);
                ps.setArray(1, SqlArrays.text(con, keys));
                ps.setArray(2, SqlArrays.text(con, seen));
                return ps;
            });
        }
    }

    /** 원본 여러 줄(한 문장) — 그 달 조각이 없으면 PostgreSQL 이 23514 로 거절한다(부르는 쪽이 {@link #ensurePartitions} 후 다시) */
    public void raw(List<RawRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(RAW);
            ps.setArray(1, SqlArrays.text(con, rows.stream().map(RawRow::id).toList()));
            ps.setArray(2, SqlArrays.text(con, rows.stream().map(RawRow::viewedAt).toList()));
            ps.setArray(3, SqlArrays.text(con, rows.stream().map(RawRow::visitorHash).toList()));
            ps.setArray(4, SqlArrays.text(con, rows.stream().map(RawRow::sessionHash).toList()));
            ps.setArray(5, SqlArrays.text(con, rows.stream().map(RawRow::path).toList()));
            ps.setArray(6, SqlArrays.text(con, rows.stream().map(RawRow::kind).toList()));
            ps.setArray(7, SqlArrays.text(con, rows.stream().map(RawRow::prevPath).toList()));
            ps.setArray(8, SqlArrays.text(con, rows.stream().map(RawRow::referrerDomain).toList()));
            ps.setArray(9, SqlArrays.text(con, rows.stream().map(RawRow::referrerGroup).toList()));
            ps.setArray(10, SqlArrays.text(con, rows.stream().map(RawRow::utmSource).toList()));
            ps.setArray(11, SqlArrays.text(con, rows.stream().map(RawRow::utmMedium).toList()));
            ps.setArray(12, SqlArrays.text(con, rows.stream().map(RawRow::utmCampaign).toList()));
            ps.setArray(13, SqlArrays.text(con, rows.stream().map(RawRow::device).toList()));
            ps.setArray(14, SqlArrays.text(con, rows.stream().map(RawRow::newVisitor).toList()));
            return ps;
        });
    }

    /** 이번 달·다음 달 원본 조각을 만든다(R__analytics_10 의 함수 — 이미 있으면 그대로) */
    public void ensurePartitions() {
        jdbc.queryForObject("SELECT analytics_ensure_page_view_partitions(1)", Integer.class);
    }

    /**
     * 「그 날 셌다」 표시 조각(analytics V6 — 한국 날짜 하루 파티션)을 어제~모레 + 이 묶음의 날들로 만든다(R__analytics_11 의 함수 — 이미 있으면 그대로).
     * 그 날 조각이 없으면 {@link #visits} 가 23514 로 거절된다(부르는 쪽이 이것을 부른 뒤 다시).
     */
    public void ensureMarkPartitions(Collection<LocalDate> days) {
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement("SELECT analytics_ensure_visit_mark_partitions(?::date[])");
            ps.setArray(1, SqlArrays.text(con, List.copyOf(days)));
            return ps;
        }, (ResultSetExtractor<Void>) rs -> null);
    }

    /**
     * 주·월 순방문자 표시 조각(analytics V8 — 네 표)을 이번·다음 주와 이번·다음 달 + 이 묶음의 날들이 든 기간으로 만든다(R__analytics_16 의 함수 — 이미 있으면 그대로).
     * 그 기간 조각이 없으면 {@link #visits(Collection, Map, Map, Collection)} 가 23514 로 거절된다(부르는 쪽이 이것을 부른 뒤 다시).
     */
    public void ensurePeriodMarkPartitions(Collection<LocalDate> days) {
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement("SELECT analytics_ensure_period_mark_partitions(?::date[])");
            ps.setArray(1, SqlArrays.text(con, List.copyOf(days)));
            return ps;
        }, (ResultSetExtractor<Void>) rs -> null);
    }

    public void pages(List<DailyRow> rows) {
        daily(PAGES, rows);
    }

    public void referrers(List<DailyRow> rows) {
        daily(REFERRERS, rows);
    }

    public void flows(List<DailyRow> rows) {
        daily(FLOWS, rows);
    }

    /** 캠페인 일별 더하기(한 문장) — 유입처와 같은 트랜잭션에서 부른다 */
    public void campaigns(List<CampaignRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(CAMPAIGNS);
            ps.setArray(1, SqlArrays.text(con, rows.stream().map(CampaignRow::day).toList()));
            ps.setArray(2, SqlArrays.text(con, rows.stream().map(CampaignRow::source).toList()));
            ps.setArray(3, SqlArrays.text(con, rows.stream().map(CampaignRow::medium).toList()));
            ps.setArray(4, SqlArrays.text(con, rows.stream().map(CampaignRow::campaign).toList()));
            ps.setArray(5, SqlArrays.text(con, rows.stream().map(CampaignRow::count).toList()));
            return ps;
        });
    }

    /** 환경 일별 더하기(한 문장) */
    public void env(List<EnvRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(ENV);
            ps.setArray(1, SqlArrays.text(con, rows.stream().map(EnvRow::day).toList()));
            ps.setArray(2, SqlArrays.text(con, rows.stream().map(EnvRow::device).toList()));
            ps.setArray(3, SqlArrays.text(con, rows.stream().map(EnvRow::browser).toList()));
            ps.setArray(4, SqlArrays.text(con, rows.stream().map(EnvRow::os).toList()));
            ps.setArray(5, SqlArrays.text(con, rows.stream().map(EnvRow::screen).toList()));
            ps.setArray(6, SqlArrays.text(con, rows.stream().map(EnvRow::count).toList()));
            return ps;
        });
    }

    /** 검색어 일별 더하기(한 문장) + 걸러진 검색 수 일별 더하기(한 문장) — 한 트랜잭션에서 부른다 */
    public void searches(List<SearchRow> rows, Map<LocalDate, Long> filtered) {
        if (!rows.isEmpty()) {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(SEARCHES);
                ps.setArray(1, SqlArrays.text(con, rows.stream().map(SearchRow::day).toList()));
                ps.setArray(2, SqlArrays.text(con, rows.stream().map(SearchRow::term).toList()));
                ps.setArray(3, SqlArrays.text(con, rows.stream().map(SearchRow::searches).toList()));
                ps.setArray(4, SqlArrays.text(con, rows.stream().map(SearchRow::noResult).toList()));
                return ps;
            });
        }
        if (!filtered.isEmpty()) {
            List<LocalDate> days = new ArrayList<>(filtered.keySet());
            List<Long> counts = days.stream().map(filtered::get).toList();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(SEARCH_FILTERED);
                ps.setArray(1, SqlArrays.text(con, days));
                ps.setArray(2, SqlArrays.text(con, counts));
                return ps;
            });
        }
    }

    /** 봇 요청 일별 더하기(한 문장) — {@link DailyRow#a()} = 종류, {@link DailyRow#b()} 는 쓰지 않는다 */
    public void bots(List<DailyRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(BOTS);
            ps.setArray(1, SqlArrays.text(con, rows.stream().map(DailyRow::day).toList()));
            ps.setArray(2, SqlArrays.text(con, rows.stream().map(DailyRow::a).toList()));
            ps.setArray(3, SqlArrays.text(con, rows.stream().map(DailyRow::count).toList()));
            return ps;
        });
    }

    /** 수집 진단(한 문장) — 신호가 없어도 1분마다 부른다(「살아 있음」) */
    public void diagnostics(Diagnostics d) {
        List<FailureRow> f = d.failures();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(DIAGNOSTICS);
            ps.setString(1, d.instance());
            ps.setTimestamp(2, Timestamp.from(d.at()));
            ps.setString(3, d.instance());
            ps.setArray(4, SqlArrays.text(con, f.stream().map(FailureRow::at).toList()));
            ps.setArray(5, SqlArrays.text(con, f.stream().map(FailureRow::reason).toList()));
            ps.setArray(6, SqlArrays.text(con, f.stream().map(FailureRow::detail).toList()));
            ps.setTimestamp(7, Timestamp.from(d.hour()));
            ps.setString(8, d.instance());
            ps.setLong(9, d.flushOk());
            ps.setLong(10, d.flushFail());
            ps.setLong(11, d.dropped());
            ps.setLong(12, d.ignoredBot());
            ps.setLong(13, d.ignoredInvalid());
            ps.setLong(14, d.ignoredExcluded());
            ps.setLong(15, d.unknownPath());
            return ps;
        });
    }

    private void daily(String sql, List<DailyRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setArray(1, SqlArrays.text(con, rows.stream().map(DailyRow::day).toList()));
            ps.setArray(2, SqlArrays.text(con, rows.stream().map(DailyRow::a).toList()));
            ps.setArray(3, SqlArrays.text(con, rows.stream().map(DailyRow::b).toList()));
            ps.setArray(4, SqlArrays.text(con, rows.stream().map(DailyRow::count).toList()));
            return ps;
        });
    }
}
