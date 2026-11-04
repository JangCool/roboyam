package com.community.analytics.repository.master;

import com.community.analytics.store.AnalyticsDatabase;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 「방문자 통계」 읽기(site-analytics-contract 「「방문자 통계」 화면」) — 분석 전용 연결({@code analyticsJdbc}), JPA 없이 SQL. <b>집계 표만</b> 읽고, 원본({@code analytics_page_views})은
 * 실시간의 최근 30분만 읽는다. 묶기·정렬·자르기는 모두 DB 에서 한다(줄을 자바로 끌어와 세지 않는다). 질의마다 쓰는 기본 키·인덱스를 적는다.
 *
 * <p>기간 [from, to] 은 한국 날짜(양끝 포함, 최대 90일 — 서비스가 막는다). 일별 표는 {@code day BETWEEN} 으로, 시간 표는 정시 시각 범위로 읽는다.
 * 일별 표의 「기간 + 경로 = ?」는 PostgreSQL 18 의 B-tree 건너뛰기 검색(skip scan)으로 앞 칸(day) 범위를 날마다 건너뛴다 — 기간이 최대 90일이라 내려가기도 최대 90번.
 */
@Repository
public class VisitAnalyticsStore {

    public record Hour(Instant hour, long visitors, long newVisitors, long pageViews) {
    }

    /** 키 하나와 수 */
    public record Count(String key, long count) {
    }

    /** 페이지 순위 한 줄 + 거른 범위 전체의 페이지 수·조회수 합(창 함수) */
    public record PageRank(String path, String kind, long views, long total, long totalViews) {
    }

    public record Page(String path, String kind, long views) {
    }

    public record DayCount(LocalDate day, long count) {
    }

    public record DayGroup(LocalDate day, String group, long visits) {
    }

    /** 이동의 한쪽 끝 + 그쪽 전체 합(창 함수) */
    public record End(String path, long moves, long total) {
    }

    public record Move(String from, String to, long moves) {
    }

    public record Campaign(String source, String medium, String campaign, long visits) {
    }

    public record Minute(Instant at, long pageViews) {
    }

    private final JdbcTemplate jdbc;

    public VisitAnalyticsStore(AnalyticsDatabase db) {
        this.jdbc = db.jdbc();
    }

    // ── 시간 칸(analytics_visit_hourly) ──────────────────────────────────────

    /** [from, to) 의 시간 칸 — PK(hour) 범위. 요약은 직전 기간까지 한 번에(최대 180일 × 24줄) */
    public List<Hour> hours(Instant from, Instant to) {
        return jdbc.query("SELECT hour, visitors, new_visitors, page_views FROM analytics_visit_hourly WHERE hour >= ? AND hour < ? ORDER BY hour",
                (rs, i) -> new Hour(rs.getTimestamp(1).toInstant(), rs.getLong(2), rs.getLong(3), rs.getLong(4)), ts(from), ts(to));
    }

    /** 지금 접속 — ix_analytics_visit_active_seen(last_seen) 범위 COUNT */
    public long activeSince(Instant since) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM analytics_visit_active WHERE last_seen >= ?", Long.class, ts(since));
        return n == null ? 0 : n;
    }

    // ── 페이지(analytics_page_daily) ─────────────────────────────────────────

    /**
     * 기간의 페이지 순위 한 쪽 — PK(day, path, kind) 앞 칸(day) 범위를 읽어 (path, kind) 로 묶는다(기간의 서로 다른 날·경로 줄 수만큼).
     * 거르기: 화면 종류(같음) · 경로 일부(대소문자 무시) 또는 덧붙인 경로 목록(게시판 이름이 맞는 게시판 주소). 총수·조회수 합은 같은 문장의 창 함수.
     */
    public List<PageRank> pages(LocalDate from, LocalDate to, String kind, String pathLike, Collection<String> extraPaths, int offset, int limit) {
        StringBuilder sql = new StringBuilder("WITH agg AS (SELECT path, kind, sum(views) AS views FROM analytics_page_daily WHERE day >= ? AND day <= ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        if (kind != null) {
            sql.append(" AND kind = ?");
            args.add(kind);
        }
        if (pathLike != null) {
            sql.append(" AND (path ILIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(pathLike) + "%");
            if (!extraPaths.isEmpty()) {
                sql.append(" OR path = ANY (?::text[])");
                args.add(textArray(extraPaths));
            }
            sql.append(")");
        }
        sql.append(" GROUP BY path, kind) SELECT path, kind, views, count(*) OVER () AS total, sum(views) OVER () AS total_views FROM agg "
                + "ORDER BY views DESC, path, kind OFFSET ? LIMIT ?");
        args.add(offset);
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, i) -> new PageRank(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)), args.toArray());
    }

    /** 순위 쪽이 범위 밖(빈 쪽)일 때만 — 같은 거르기의 페이지 수·조회수 합 */
    public long[] pageTotals(LocalDate from, LocalDate to, String kind, String pathLike, Collection<String> extraPaths) {
        StringBuilder sql = new StringBuilder("SELECT count(*), coalesce(sum(views), 0) FROM (SELECT sum(views) AS views FROM analytics_page_daily WHERE day >= ? AND day <= ?");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        if (kind != null) {
            sql.append(" AND kind = ?");
            args.add(kind);
        }
        if (pathLike != null) {
            sql.append(" AND (path ILIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(pathLike) + "%");
            if (!extraPaths.isEmpty()) {
                sql.append(" OR path = ANY (?::text[])");
                args.add(textArray(extraPaths));
            }
            sql.append(")");
        }
        sql.append(" GROUP BY path, kind) agg");
        return jdbc.queryForObject(sql.toString(), (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, args.toArray());
    }

    /** 한 경로의 일별 조회 — PK(day, path, kind) 를 day 범위 + path 같음으로(건너뛰기 검색) */
    public List<DayCount> pageDays(LocalDate from, LocalDate to, String path) {
        return jdbc.query("SELECT day, sum(views) FROM analytics_page_daily WHERE day >= ? AND day <= ? AND path = ? GROUP BY day ORDER BY day",
                (rs, i) -> new DayCount(rs.getDate(1).toLocalDate(), rs.getLong(2)), Date.valueOf(from), Date.valueOf(to), path);
    }

    /** 한 경로의 화면 종류(기간에 가장 많이 본 것) — 위와 같은 접근. 없으면 null */
    public String pageKind(LocalDate from, LocalDate to, String path) {
        List<String> kinds = jdbc.query("SELECT kind FROM analytics_page_daily WHERE day >= ? AND day <= ? AND path = ? GROUP BY kind ORDER BY sum(views) DESC, kind LIMIT 1",
                (rs, i) -> rs.getString(1), Date.valueOf(from), Date.valueOf(to), path);
        return kinds.isEmpty() ? null : kinds.get(0);
    }

    // ── 유입(analytics_referrer_daily · analytics_campaign_daily) ───────────

    /** 기간의 날·분류별 방문 — PK(day, referrer_group, referrer_domain) 앞 칸 범위 */
    public List<DayGroup> referrerDayGroups(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT day, referrer_group, sum(visits) FROM analytics_referrer_daily WHERE day >= ? AND day <= ? GROUP BY day, referrer_group ORDER BY day",
                (rs, i) -> new DayGroup(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getLong(3)), Date.valueOf(from), Date.valueOf(to));
    }

    /** 기간의 분류별 방문 — 위와 같은 범위(일별 추이가 필요 없을 때) */
    public List<Count> referrerGroups(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT referrer_group, sum(visits) FROM analytics_referrer_daily WHERE day >= ? AND day <= ? GROUP BY referrer_group",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), Date.valueOf(from), Date.valueOf(to));
    }

    /** 기간의 유입 도메인 상위 — 도메인이 없는 분류(직접·캠페인, '')는 빠진다 */
    public List<Count> referrerDomains(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("SELECT referrer_domain, sum(visits) AS n FROM analytics_referrer_daily WHERE day >= ? AND day <= ? AND referrer_domain <> '' "
                        + "GROUP BY referrer_domain ORDER BY n DESC, referrer_domain LIMIT ?",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), Date.valueOf(from), Date.valueOf(to), limit);
    }

    /** 기간의 캠페인 상위 — PK(day, utm_source, utm_medium, utm_campaign) 앞 칸 범위. 빈 칸 '' 는 부르는 쪽이 null 로 */
    public List<Campaign> campaigns(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("SELECT utm_source, utm_medium, utm_campaign, sum(visits) AS n FROM analytics_campaign_daily WHERE day >= ? AND day <= ? "
                        + "GROUP BY utm_source, utm_medium, utm_campaign ORDER BY n DESC, utm_source, utm_medium, utm_campaign LIMIT ?",
                (rs, i) -> new Campaign(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)), Date.valueOf(from), Date.valueOf(to), limit);
    }

    // ── 이동(analytics_flow_daily) ───────────────────────────────────────────

    /** 기간에 들어온 이동(「바로 들어옴」 포함)이 가장 많은 페이지 — ix_analytics_flow_daily_to(day, to_path) 범위. 이동이 없으면 null */
    public String topDestination(LocalDate from, LocalDate to) {
        List<String> paths = jdbc.query("SELECT to_path FROM analytics_flow_daily WHERE day >= ? AND day <= ? GROUP BY to_path ORDER BY sum(moves) DESC, to_path LIMIT 1",
                (rs, i) -> rs.getString(1), Date.valueOf(from), Date.valueOf(to));
        return paths.isEmpty() ? null : paths.get(0);
    }

    /** 한 페이지로 들어온 곳 상위 limit + 들어온 이동 합 — ix_analytics_flow_daily_to(day, to_path) 를 day 범위 + to_path 같음으로. 「바로 들어옴」은 from '' */
    public List<End> incoming(LocalDate from, LocalDate to, String path, int limit) {
        return jdbc.query("SELECT from_path, n, sum(n) OVER () FROM (SELECT from_path, sum(moves) AS n FROM analytics_flow_daily WHERE day >= ? AND day <= ? AND to_path = ? "
                        + "GROUP BY from_path) t ORDER BY n DESC, from_path LIMIT ?",
                (rs, i) -> new End(rs.getString(1), rs.getLong(2), rs.getLong(3)), Date.valueOf(from), Date.valueOf(to), path, limit);
    }

    /** 한 페이지에서 나간 곳 상위 limit + 나간 이동 합 — PK(day, from_path, to_path) 를 day 범위 + from_path 같음으로(건너뛰기 검색) */
    public List<End> outgoing(LocalDate from, LocalDate to, String path, int limit) {
        return jdbc.query("SELECT to_path, n, sum(n) OVER () FROM (SELECT to_path, sum(moves) AS n FROM analytics_flow_daily WHERE day >= ? AND day <= ? AND from_path = ? "
                        + "GROUP BY to_path) t ORDER BY n DESC, to_path LIMIT ?",
                (rs, i) -> new End(rs.getString(1), rs.getLong(2), rs.getLong(3)), Date.valueOf(from), Date.valueOf(to), path, limit);
    }

    /**
     * 기간 전체 상위 이동 — PK 앞 칸(day) 범위의 모든 이동 줄을 (from, to) 로 묶는다. 「바로 들어옴」('')은 페이지 사이 이동이 아니라 뺀다.
     * 이 표에서 가장 무거운 질의다(기간 날 수 × 그 날 서로 다른 이동 수) — 결과 캐시(60초)가 받친다.
     */
    public List<Move> topMoves(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("SELECT from_path, to_path, sum(moves) AS n FROM analytics_flow_daily WHERE day >= ? AND day <= ? AND from_path <> '' "
                        + "GROUP BY from_path, to_path ORDER BY n DESC, from_path, to_path LIMIT ?",
                (rs, i) -> new Move(rs.getString(1), rs.getString(2), rs.getLong(3)), Date.valueOf(from), Date.valueOf(to), limit);
    }

    // ── 실시간(원본 analytics_page_views, 최근 30분) ───────────────────────

    /** 분당 페이지뷰 — PK(viewed_at, id) 앞 칸 범위(월 조각은 실행 때 잘라낸다). 빈 분은 줄이 없다 */
    public List<Minute> perMinute(Instant since) {
        return jdbc.query("SELECT date_trunc('minute', viewed_at) AS m, count(*) FROM analytics_page_views WHERE viewed_at >= ? GROUP BY m ORDER BY m",
                (rs, i) -> new Minute(rs.getTimestamp(1).toInstant(), rs.getLong(2)), ts(since));
    }

    /** 최근 많이 본 페이지 — 위와 같은 범위 */
    public List<Page> recentPages(Instant since, int limit) {
        return jdbc.query("SELECT path, kind, count(*) AS n FROM analytics_page_views WHERE viewed_at >= ? GROUP BY path, kind ORDER BY n DESC, path, kind LIMIT ?",
                (rs, i) -> new Page(rs.getString(1), rs.getString(2), rs.getLong(3)), ts(since), limit);
    }

    /** 최근 유입 분류 — 세션 첫 페이지 줄(referrer_group 이 있는 줄)만 */
    public List<Count> recentReferrerGroups(Instant since) {
        return jdbc.query("SELECT referrer_group, count(*) FROM analytics_page_views WHERE viewed_at >= ? AND referrer_group IS NOT NULL GROUP BY referrer_group",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), ts(since));
    }

    private static String escapeLike(String v) {
        return v.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** PostgreSQL 배열 글자 — 각 값을 큰따옴표로 감싸고 \ 와 " 를 이스케이프한다 */
    private static String textArray(Collection<String> values) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String v : values) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }
}
