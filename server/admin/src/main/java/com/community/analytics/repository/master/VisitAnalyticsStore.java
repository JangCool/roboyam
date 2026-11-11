package com.community.analytics.repository.master;

import com.community.analytics.store.AnalyticsDatabase;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
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

    // ── 2단계 — 환경(analytics_env_daily) ───────────────────────────────────

    /** 환경 묶음 한 줄 — {@code dim} D(기기 — 날마다) · B(브라우저) · O(OS) · S(화면), 기기만 {@code day} 가 있다 */
    public record EnvCount(String dim, LocalDate day, String key, long visits) {
    }

    /**
     * 기간의 일별 기기 · 브라우저 · OS · 화면 합 — 한 번 훑어 묶음 넷(GROUPING SETS). PK(day, device, browser, os, screen) 앞 칸(day) 범위 — 하루 최대 630 줄 × 최대 90일.
     */
    public List<EnvCount> envCounts(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT CASE WHEN GROUPING(day) = 0 THEN 'D' WHEN GROUPING(browser) = 0 THEN 'B' WHEN GROUPING(os) = 0 THEN 'O' ELSE 'S' END AS dim, "
                        + "day, coalesce(device, browser, os, screen) AS k, sum(visits) FROM analytics_env_daily WHERE day BETWEEN ? AND ? "
                        + "GROUP BY GROUPING SETS ((day, device), (browser), (os), (screen))",
                (rs, i) -> new EnvCount(rs.getString(1), rs.getDate(2) == null ? null : rs.getDate(2).toLocalDate(), rs.getString(3), rs.getLong(4)),
                Date.valueOf(from), Date.valueOf(to));
    }

    // ── 2단계 — 검색어(analytics_search_daily · analytics_search_filtered_daily) ─

    public record SearchTotals(long searches, long noResult, long terms, long filtered) {
    }

    public record TermCount(String term, long searches, long noResult, LocalDate lastNoResultDay) {
    }

    /** 기간의 합 — PK(day, term) 앞 칸 범위 + 걸러진 수 PK(day) 범위(한 문장) */
    public SearchTotals searchTotals(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("SELECT coalesce(sum(searches), 0), coalesce(sum(no_result), 0), count(DISTINCT term), "
                        + "(SELECT coalesce(sum(f.searches), 0) FROM analytics_search_filtered_daily f WHERE f.day BETWEEN ? AND ?) "
                        + "FROM analytics_search_daily WHERE day BETWEEN ? AND ?",
                (rs, i) -> new SearchTotals(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)), Date.valueOf(from), Date.valueOf(to), Date.valueOf(from),
                Date.valueOf(to));
    }

    /** 검색 수 상위 — 같은 범위를 검색어로 묶어 정렬(같으면 검색어 차례) */
    public List<TermCount> topTerms(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("SELECT term, sum(searches) AS n, sum(no_result) FROM analytics_search_daily WHERE day BETWEEN ? AND ? GROUP BY term ORDER BY n DESC, term LIMIT ?",
                (rs, i) -> new TermCount(rs.getString(1), rs.getLong(2), rs.getLong(3), null), Date.valueOf(from), Date.valueOf(to), limit);
    }

    /** 결과 없는 검색 수 상위 — 결과 없던 줄만, 마지막으로 결과 없던 날 */
    public List<TermCount> noResultTerms(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("SELECT term, sum(searches), sum(no_result) AS z, max(day) FROM analytics_search_daily WHERE day BETWEEN ? AND ? AND no_result > 0 "
                        + "GROUP BY term ORDER BY z DESC, term LIMIT ?",
                (rs, i) -> new TermCount(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getDate(4).toLocalDate()), Date.valueOf(from), Date.valueOf(to), limit);
    }

    // ── 2단계 — 봇(analytics_bot_daily) · 사람 페이지뷰 ─────────────────────

    public record BotDay(LocalDate day, String kind, long requests) {
    }

    /** 기간 + 오늘의 봇 줄 — PK(day, kind) 범위(하루 최대 6 줄) */
    public List<BotDay> botDays(LocalDate from, LocalDate to, LocalDate today) {
        return jdbc.query("SELECT day, kind, requests FROM analytics_bot_daily WHERE day BETWEEN ? AND ? OR day = ?",
                (rs, i) -> new BotDay(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getLong(3)), Date.valueOf(from), Date.valueOf(to), Date.valueOf(today));
    }

    /** [from, to) 의 사람 페이지뷰 합 — analytics_visit_hourly PK(hour) 범위 */
    public long pageViews(Instant from, Instant to) {
        Long n = jdbc.queryForObject("SELECT coalesce(sum(page_views), 0) FROM analytics_visit_hourly WHERE hour >= ? AND hour < ?", Long.class, ts(from), ts(to));
        return n == null ? 0 : n;
    }

    // ── 3단계 — 주·월 순방문자 숫자 표(analytics_unique_counts, analytics V8) ──────

    /** 한 기간의 순방문자 — 대상(VISITOR · MEMBER) · 기간 시작 · 그 기간 줄의 합 */
    public record PeriodTotal(String audience, LocalDate start, long total) {
    }

    /** 대상별 세기 시작일(숫자 표의 가장 이른 날) — 대상에 줄이 없으면 맵에 없다. 작은 표(하루 최대 4줄)라 통째로 묶는다 */
    public Map<String, LocalDate> uniqueFirstDays() {
        Map<String, LocalDate> out = new HashMap<>();
        jdbc.query("SELECT audience, min(day) FROM analytics_unique_counts GROUP BY audience",
                (RowCallbackHandler) rs -> out.put(rs.getString(1), rs.getDate(2).toLocalDate()));
        return out;
    }

    /** 종류(WEEK · MONTH)의 기간 시작이 [from, to] 인 기간별 합 — PK(audience, period_kind, period_start, day) 를 대상마다 건너뛰기 검색 */
    public List<PeriodTotal> uniqueTotals(String kind, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT audience, period_start, sum(added) FROM analytics_unique_counts WHERE period_kind = ? AND period_start >= ? AND period_start <= ? "
                        + "GROUP BY audience, period_start",
                (rs, i) -> new PeriodTotal(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getLong(3)), kind, Date.valueOf(from), Date.valueOf(to));
    }

    /** 한 기간을 그 날짜까지만 더한 합(「같은 시점까지」) — 대상별, 줄이 없는 대상은 맵에 없다 */
    public Map<String, Long> uniqueUntil(String kind, LocalDate start, LocalDate until) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("SELECT audience, sum(added) FROM analytics_unique_counts WHERE period_kind = ? AND period_start = ? AND day <= ? GROUP BY audience",
                (RowCallbackHandler) rs -> out.put(rs.getString(1), rs.getLong(2)), kind, Date.valueOf(start), Date.valueOf(until));
        return out;
    }

    // ── 3단계 — 월별 보기(analytics V7 월별 표 + 아직 합치지 않은 달은 일별·시간별) ──

    public record MonthVisit(LocalDate month, long visitorsDailySum, long newVisitors, long pageViews) {
    }

    /** 합친 기록이 있는 달(analytics_monthly_rollups) 중 [from, to] — 그 달 1일 */
    public Set<LocalDate> rolledMonths(LocalDate from, LocalDate to) {
        return new HashSet<>(jdbc.query("SELECT month FROM analytics_monthly_rollups WHERE month >= ? AND month <= ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), Date.valueOf(from), Date.valueOf(to)));
    }

    /** 합친 달의 월 방문(analytics_visit_monthly PK(month)) — 줄이 없는 달은 빠진다 */
    public List<MonthVisit> visitMonths(Collection<LocalDate> months) {
        if (months.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT month, visitors_daily_sum, new_visitors, page_views FROM analytics_visit_monthly WHERE month = ANY (?::date[])",
                (rs, i) -> new MonthVisit(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3), rs.getLong(4)), textArray(months.stream().map(LocalDate::toString).toList()));
    }

    /** [from, to) 시간 칸의 방문자(일별 합)·신규·페이지뷰 합 — 아직 합치지 않은 달(analytics_visit_hourly PK(hour) 범위) */
    public long[] hourTotals(Instant from, Instant to) {
        return jdbc.queryForObject("SELECT coalesce(sum(visitors), 0), coalesce(sum(new_visitors), 0), coalesce(sum(page_views), 0) FROM analytics_visit_hourly "
                + "WHERE hour >= ? AND hour < ?", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, ts(from), ts(to));
    }

    /**
     * 달 여럿의 많이 본 페이지 상위 limit + 전체 페이지 수·조회수 합(창 함수) — 합친 달은 analytics_page_monthly(PK(month, path, kind)),
     * 아직 합치지 않은 달은 analytics_page_daily(PK(day, path, kind) 앞 칸 범위)를 더한다. {@code PageRank.total} = 페이지 수.
     */
    public List<PageRank> monthPages(Collection<LocalDate> rolled, Collection<LocalDate> open, int limit) {
        DailyRange r = DailyRange.of(open);
        return jdbc.query("WITH src AS ("
                        + "  SELECT path, kind, views FROM analytics_page_monthly WHERE month = ANY (?::date[])"
                        + "  UNION ALL SELECT path, kind, views FROM analytics_page_daily WHERE day >= ? AND day < ? AND date_trunc('month', day)::date = ANY (?::date[])"
                        + "), agg AS (SELECT path, kind, sum(views) AS views FROM src GROUP BY path, kind) "
                        + "SELECT path, kind, views, count(*) OVER (), sum(views) OVER () FROM agg ORDER BY views DESC, path, kind LIMIT ?",
                (rs, i) -> new PageRank(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)),
                textArray(rolled.stream().map(LocalDate::toString).toList()), Date.valueOf(r.from()), Date.valueOf(r.to()),
                textArray(open.stream().map(LocalDate::toString).toList()), limit);
    }

    /** 달 여럿의 유입 분류별 방문(세션 첫 페이지 수) — 합친 달은 analytics_referrer_monthly, 아직 합치지 않은 달은 analytics_referrer_daily(PK 앞 칸 범위) */
    public List<Count> monthReferrerGroups(Collection<LocalDate> rolled, Collection<LocalDate> open) {
        DailyRange r = DailyRange.of(open);
        return jdbc.query("SELECT referrer_group, sum(visits) FROM ("
                        + "  SELECT referrer_group, visits FROM analytics_referrer_monthly WHERE month = ANY (?::date[])"
                        + "  UNION ALL SELECT referrer_group, visits FROM analytics_referrer_daily WHERE day >= ? AND day < ? AND date_trunc('month', day)::date = ANY (?::date[])"
                        + ") src GROUP BY referrer_group",
                (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), textArray(rolled.stream().map(LocalDate::toString).toList()), Date.valueOf(r.from()),
                Date.valueOf(r.to()), textArray(open.stream().map(LocalDate::toString).toList()));
    }

    /** 아직 합치지 않은 달들(그 달 1일)을 덮는 일별 범위 [from, to) — 비었으면 빈 범위 */
    private record DailyRange(LocalDate from, LocalDate to) {

        static DailyRange of(Collection<LocalDate> months) {
            LocalDate min = months.stream().min(LocalDate::compareTo).orElse(LocalDate.of(2000, 1, 1));
            LocalDate max = months.stream().max(LocalDate::compareTo).map(m -> m.plusMonths(1)).orElse(min);
            return new DailyRange(min, max);
        }
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
