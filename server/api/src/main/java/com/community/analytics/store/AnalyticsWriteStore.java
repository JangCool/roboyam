package com.community.analytics.store;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 방문 몰아쓰기의 SQL(analytics 스트림 표 — analytics V1). 모두 <b>여러 줄을 한 문장</b>({@code unnest} 배열)으로 쓴다 — 문장 수는 방문량과 무관하다. 분석 전용 연결({@code analyticsJdbc}).
 * 트랜잭션은 부르는 쪽({@code VisitFlusher})이 {@link AnalyticsDatabase#inTransaction} 으로 연다.
 *
 * <p><b>서버 여러 대 정확성</b>(API 서버 2~3대 전제):
 * <ul>
 *   <li>방문자: {@link #visits} 의 표시 넣기가 {@code ON CONFLICT DO NOTHING RETURNING} 이라 <b>실제로 들어간 줄만</b> 방문자로 더한다. 같은 방문자를 두 서버가 같은 1분에 넣으면
 *       기본 키(day, visitor_hash)가 한 서버만 받는다 — 동시면 PostgreSQL 이 뒤 서버를 앞 서버의 커밋까지 기다리게 하고 그 줄을 건너뛴다(앞 서버가 되돌리면 뒤 서버가 넣는다).
 *       넣기와 시간 칸 더하기가 한 문장(= 한 트랜잭션)이라 표시만 남고 숫자가 빠지는 일이 없다.
 *       신규 방문자: 표시에 「신규」 칸(analytics V2)을 두고, 이미 있는 표시라도 늦게 온 신호가 신규면 false → true 로 바꾸면서 그때만 신규 +1 —
 *       첫 화면(신규)은 서버 A, 다음 화면은 서버 B 가 받고 B 가 먼저 써도 신규가 빠지지 않는다(QA 재현 2026-11-04). 방문자 수는 새로 넣은 줄만 센다({@code xmax = 0}).</li>
 *   <li>페이지뷰·페이지별·유입처별·이동 횟수: 신호 하나는 서버 한 대만 받으므로 서버마다 자기 몫을 더해도 합이 정확하다. 더하기 upsert 는 행 잠금으로 차례가 정해진다.</li>
 *   <li>교착 방지: 모든 넣기를 키 차례({@code ORDER BY})로 해서 서버끼리 같은 행을 잠그는 차례가 같다.</li>
 *   <li>지금 접속: 방문자 키 한 줄에 마지막 시각을 {@code greatest} 로 — 늦게 도착한 옛 시각이 덮어쓰지 않는다. 세기는 DB 에서 한 번(관리자 대시보드).</li>
 * </ul>
 */
@Repository
public class AnalyticsWriteStore {

    /**
     * 표시 넣기 → 새로 들어간 줄만 시간 칸 방문자 +1, 신규는 「새로 넣은 신규 표시」와 「false → true 로 바뀐 표시」만 +1 — 페이지뷰와 함께 한 문장으로 더한다.
     * 같은 묶음에 같은 (날짜, 방문자)가 두 번 오면 하나로(신규 우선, 이른 시간 칸) — {@code ON CONFLICT DO UPDATE} 는 한 문장에서 같은 행을 두 번 바꿀 수 없다.
     */
    private static final String VISITS = "WITH input AS ("
            + "  SELECT DISTINCT ON (t.day, t.visitor_hash) t.day, t.visitor_hash, t.hour, t.new_visitor"
            + "    FROM unnest(?::date[], ?::text[], ?::timestamptz[], ?::boolean[]) AS t(day, visitor_hash, hour, new_visitor)"
            + "   ORDER BY t.day, t.visitor_hash, t.new_visitor DESC, t.hour"
            + "), ins AS ("
            + "  INSERT INTO analytics_visit_marks (day, visitor_hash, new_visitor) SELECT day, visitor_hash, new_visitor FROM input ORDER BY day, visitor_hash"
            + "  ON CONFLICT (day, visitor_hash) DO UPDATE SET new_visitor = true"
            + "    WHERE NOT analytics_visit_marks.new_visitor AND EXCLUDED.new_visitor"
            + "  RETURNING day, visitor_hash, (xmax = 0) AS inserted"
            + "), counted AS ("
            + "  SELECT i.hour, count(*) FILTER (WHERE ins.inserted) AS visitors, count(*) FILTER (WHERE i.new_visitor) AS new_visitors"
            + "    FROM ins JOIN input i ON i.day = ins.day AND i.visitor_hash = ins.visitor_hash GROUP BY i.hour"
            + "), views AS ("
            + "  SELECT p.hour, p.views FROM unnest(?::timestamptz[], ?::bigint[]) AS p(hour, views)"
            + ") "
            + "INSERT INTO analytics_visit_hourly (hour, visitors, new_visitors, page_views) "
            + "SELECT coalesce(c.hour, v.hour), coalesce(c.visitors, 0), coalesce(c.new_visitors, 0), coalesce(v.views, 0) "
            + "  FROM counted c FULL JOIN views v ON v.hour = c.hour ORDER BY 1 "
            + "ON CONFLICT (hour) DO UPDATE SET visitors = analytics_visit_hourly.visitors + EXCLUDED.visitors, "
            + "new_visitors = analytics_visit_hourly.new_visitors + EXCLUDED.new_visitors, page_views = analytics_visit_hourly.page_views + EXCLUDED.page_views";

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

    /** 방문자 줄 — 그 날 · 방문자 키 · 이 1분에 처음 본 정시 · 신규 방문자 신호가 있었나 */
    public record VisitorRow(LocalDate day, String visitorHash, Instant hour, boolean newVisitor) {
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

    /** 방문자·페이지뷰(한 문장) + 지금 접속(한 문장) */
    public void visits(Collection<VisitorRow> visitors, Map<Instant, Long> pageViews, Map<String, Instant> active) {
        if (!visitors.isEmpty() || !pageViews.isEmpty()) {
            List<Instant> hours = new ArrayList<>(pageViews.keySet());
            List<Long> views = hours.stream().map(pageViews::get).toList();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(VISITS);
                ps.setArray(1, SqlArrays.text(con, visitors.stream().map(VisitorRow::day).toList()));
                ps.setArray(2, SqlArrays.text(con, visitors.stream().map(VisitorRow::visitorHash).toList()));
                ps.setArray(3, SqlArrays.text(con, visitors.stream().map(VisitorRow::hour).toList()));
                ps.setArray(4, SqlArrays.text(con, visitors.stream().map(VisitorRow::newVisitor).toList()));
                ps.setArray(5, SqlArrays.text(con, hours));
                ps.setArray(6, SqlArrays.text(con, views));
                return ps;
            });
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

    public void pages(List<DailyRow> rows) {
        daily(PAGES, rows);
    }

    public void referrers(List<DailyRow> rows) {
        daily(REFERRERS, rows);
    }

    public void flows(List<DailyRow> rows) {
        daily(FLOWS, rows);
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
