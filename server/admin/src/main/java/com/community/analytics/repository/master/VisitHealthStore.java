package com.community.analytics.repository.master;

import com.community.analytics.store.AnalyticsDatabase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 「방문자 통계 › 수집 진단」·「정보주체 요청」 읽기(site-analytics-contract 「2단계」) — 분석 전용 연결({@code analyticsJdbc}), JPA 없이 SQL. 표는 analytics V4.
 * 진단 표는 서버 수 × 시간 칸만큼이라 작다(최근 24시간만 읽는다). 원본 보관은 표 줄을 세지 않고 카탈로그({@code pg_inherits}·{@code pg_class})와 기본 키 끝값으로 본다.
 */
@Repository
public class VisitHealthStore {

    /** 서버 한 줄 — 마지막 기록 시각(없으면 null) + 최근 합 + 지난 1시간의 기록 실패 */
    public record InstanceRow(String instance, Instant lastWriteAt, long flushOk, long flushFail, long dropped, long ignoredBot, long ignoredInvalid,
                              long recentFail) {
    }

    public record FailureRow(Instant at, String instance, String reason, String detail) {
    }

    /** 원본 보관 — 조각 수 · 대략 줄 수(통계를 한 번도 모으지 않았으면 null) · 가장 오래된 시각 · 다음 달 조각 있음 */
    public record RawRow(int partitions, Long approxRows, Instant oldest, boolean nextPartitionReady) {
    }

    public record ForgetRow(long id, Instant receivedAt, String status, Long deletedRows, Instant processedAt, long total) {
    }

    public record ForgetCounts(long total, long pending, long recent) {
    }

    private final JdbcTemplate jdbc;

    public VisitHealthStore(AnalyticsDatabase db) {
        this.jdbc = db.jdbc();
    }

    /**
     * {@code since} 뒤에 기록한 서버(「살아 있음」 또는 시간 칸) — 시간 칸 PK(hour, instance) 범위를 서버로 묶고, 살아 있음(PK instance)과 붙인다.
     * {@code recentSince} 뒤의 기록 실패는 따로 센다(지금도 실패 중인지) — 시간 칸은 정시라 「지난 1시간」을 칸으로 자르면 앞 칸의 실패가 빠지므로,
     * 정확한 시각이 있는 실패 기록({@code analytics_collector_failures.failed_at}, PK 범위)도 함께 센다(둘 중 하나라도 있으면 「최근 실패」 — QA 2026-11-04).
     */
    public List<InstanceRow> instances(Instant since, Instant recentSince) {
        return jdbc.query("WITH h AS (SELECT instance, sum(flush_ok) AS ok, sum(flush_fail) AS fail, sum(dropped) AS dropped, sum(ignored_bot) AS bot, "
                        + "sum(ignored_invalid) AS invalid, coalesce(sum(flush_fail) FILTER (WHERE hour >= ?), 0) AS recent "
                        + "FROM analytics_collector_hourly WHERE hour >= ? GROUP BY instance), "
                        + "b AS (SELECT instance, last_write_at FROM analytics_collector_beats WHERE last_write_at >= ?), "
                        + "f AS (SELECT instance, count(*) AS recent FROM analytics_collector_failures WHERE failed_at >= ? GROUP BY instance) "
                        + "SELECT coalesce(b.instance, h.instance), b.last_write_at, coalesce(h.ok, 0), coalesce(h.fail, 0), coalesce(h.dropped, 0), coalesce(h.bot, 0), "
                        + "coalesce(h.invalid, 0), coalesce(h.recent, 0) + coalesce(f.recent, 0) FROM b FULL JOIN h ON h.instance = b.instance "
                        + "LEFT JOIN f ON f.instance = coalesce(b.instance, h.instance) ORDER BY 1",
                (rs, i) -> {
                    Timestamp at = rs.getTimestamp(2);
                    return new InstanceRow(rs.getString(1), at == null ? null : at.toInstant(), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7),
                            rs.getLong(8));
                },
                Timestamp.from(recentSince), Timestamp.from(since), Timestamp.from(since), Timestamp.from(recentSince));
    }

    /** 최근 기록 실패 — PK(failed_at, id) 거꾸로 */
    public List<FailureRow> failures(Instant since, int limit) {
        return jdbc.query("SELECT failed_at, instance, reason, detail FROM analytics_collector_failures WHERE failed_at >= ? ORDER BY failed_at DESC, id DESC LIMIT ?",
                (rs, i) -> new FailureRow(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3), rs.getString(4)), Timestamp.from(since), limit);
    }

    /**
     * 원본 보관 — 조각 수·대략 줄 수(pg_class.reltuples, 한 번도 분석하지 않은 조각은 -1 이라 뺀다) · 가장 오래된 시각(기본 키 (viewed_at, id) 앞 끝 — 조각마다 인덱스 한 칸) ·
     * 다음 달(한국 달) 조각 {@code analytics_page_views_YYYY_MM} 이 있나.
     */
    public RawRow raw() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM pg_inherits i WHERE i.inhparent = 'analytics_page_views'::regclass), "
                        + "(SELECT sum(c.reltuples)::bigint FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid "
                        + "  WHERE i.inhparent = 'analytics_page_views'::regclass AND c.reltuples >= 0), "
                        + "(SELECT min(viewed_at) FROM analytics_page_views), "
                        + "to_regclass(format('analytics_page_views_%s', to_char(date_trunc('month', now() AT TIME ZONE 'Asia/Seoul') + interval '1 month', 'YYYY_MM'))) IS NOT NULL",
                (rs, i) -> {
                    long approx = rs.getLong(2);
                    boolean known = !rs.wasNull();
                    Timestamp oldest = rs.getTimestamp(3);
                    return new RawRow(rs.getInt(1), known ? approx : null, oldest == null ? null : oldest.toInstant(), rs.getBoolean(4));
                });
    }

    /** 요청 한 쪽 — 최신 차례(ix_analytics_forget_requests_received 거꾸로), 전체 수는 창 함수 */
    public List<ForgetRow> forgetRequests(int offset, int limit) {
        return jdbc.query("SELECT id, received_at, status, deleted_rows, processed_at, count(*) OVER () FROM analytics_forget_requests "
                        + "ORDER BY received_at DESC, id DESC OFFSET ? LIMIT ?",
                (rs, i) -> {
                    long deleted = rs.getLong(4);
                    Long deletedRows = rs.wasNull() ? null : deleted;
                    Timestamp processed = rs.getTimestamp(5);
                    return new ForgetRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3), deletedRows, processed == null ? null : processed.toInstant(),
                            rs.getLong(6));
                },
                offset, limit);
    }

    /** 전체 · 대기 · {@code since} 뒤 받은 수 — 표는 1년 보관이라 작다(한 번 훑기) */
    public ForgetCounts forgetCounts(Instant since) {
        return jdbc.queryForObject("SELECT count(*), count(*) FILTER (WHERE status = 'PENDING'), count(*) FILTER (WHERE received_at >= ?) FROM analytics_forget_requests",
                (rs, i) -> new ForgetCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3)), Timestamp.from(since));
    }
}
