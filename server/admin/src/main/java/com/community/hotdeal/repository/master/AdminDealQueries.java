package com.community.hotdeal.repository.master;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 핫딜 글·의심 후보·영향 미리 보기 질의(hotdeal-contract 「API 후보」 관리자). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>Native SQL 인 이유: 읽을 때 계산하는 딜 상태(종료 · 기간 종료 = 마감 또는 등록 + 설정 일수 · 의심)를 상태별 건수와 함께 한 번에 세고, 글 표({@code posts} — 파티션 표라 연관이 없다)·
 * 딜 표·프로필을 함께 거른다. 상태 판정은 shared {@code DealViews#view} 와 같다(ENDED &gt; EXPIRED &gt; FLAGGED &gt; ACTIVE) — 한쪽을 고치면 같이 고친다.
 * 「살아 있는 글」 = 지우지 않음 · 공개 · 예약 삭제 전(숨긴 글은 든다 — 관리자 목록), 「공개 글」은 숨김도 아니고 일반 글(shared {@code DealQueries.PUBLIC_POST}).
 */
@Repository
public class AdminDealQueries {

    /** 살아 있는 글(관리자 목록 — 숨김 포함) — 인자 1개(now) */
    static final String ALIVE_POST = "p.deleted_at IS NULL AND p.status = 'PUBLISHED' AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > ?)";
    /** 공개 글 — 인자 2개(now, now) */
    static final String PUBLIC_POST = "p.deleted_at IS NULL AND p.status = 'PUBLISHED' AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > ?) "
            + "AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= ?)) AND p.group_id IS NULL";

    public static final String ALL = "ALL";

    private final JdbcTemplate jdbc;

    public AdminDealQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 목록 조건(상태·차단 조건 제외).
     *
     * @param boardIds 볼 핫딜 게시판(고른 하나 또는 모든 핫딜 게시판 — 비면 아무 글도 없다)
     * @param q        제목·쇼핑몰·작성자 닉네임 부분 일치(대소문자 무시, null 이면 없음)
     * @param source   출처 MEMBER · BUSINESS(V123 — null 이면 전체)
     */
    public record Filter(List<Long> boardIds, String q, Instant from, Instant to, String source) {
    }

    /** 목록 한 줄의 글 칸 */
    public record Row(long postId, String title, long boardId, Long prefixId, Long authorId, Instant createdAt, int likeCount, boolean publicPost) {
    }

    /** 한 쪽 */
    public record Page(long total, List<Row> rows) {
    }

    // ── 핫딜 글 목록 ────────────────────────────────────────────────────────

    /** 진행 중(저장 상태 ACTIVE)인 딜이 가는 호스트들(상품·최종 — 차단 판정용, 중복 없음) */
    public List<String> activeHosts(Collection<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return List.of();
        }
        String in = marks(boardIds.size());
        List<Object> args = new ArrayList<>(boardIds);
        args.addAll(boardIds);
        return jdbc.queryForList("SELECT product_host FROM post_deals WHERE status = 'ACTIVE' AND board_id IN (" + in + ") UNION "
                + "SELECT final_host FROM post_deals WHERE status = 'ACTIVE' AND final_host IS NOT NULL AND board_id IN (" + in + ")", String.class, args.toArray());
    }

    /**
     * 한 쪽(최신 등록순 — 글 ID 큰 차례).
     *
     * @param view         ACTIVE · FLAGGED · ENDED · EXPIRED · ALL
     * @param blockedHosts 차단 도메인 진행 중만 볼 때 그 호스트들(null 이면 거르지 않는다)
     */
    public Page search(Filter f, String view, Collection<String> blockedHosts, int autoExpireDays, Instant now, long offset, int limit) {
        Where w = where(f, now);
        w.view(view, autoExpireDays, now);
        if (blockedHosts != null) {
            w.blocked(blockedHosts, autoExpireDays, now);
        }
        Long total = jdbc.queryForObject("SELECT count(*) " + w.from() + w.sql(), Long.class, w.args.stream().map(AdminDealQueries::bind).toArray());
        List<Object> args = new ArrayList<>(List.of(now, now));
        args.addAll(w.args);
        args.add(limit);
        args.add(offset);
        List<Object> bound = args.stream().map(AdminDealQueries::bind).toList();
        List<Row> rows = jdbc.query("SELECT p.id, p.title, p.board_id, p.prefix_id, p.author_id, p.created_at, p.like_count, "
                        + "(p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= ?)) AND p.group_id IS NULL AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > ?) "
                        + w.from() + w.sql() + " ORDER BY d.post_id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getLong(3), (Long) rs.getObject(4), (Long) rs.getObject(5), rs.getTimestamp(6).toInstant(),
                        rs.getInt(7), rs.getBoolean(8)), bound.toArray());
        return new Page(total == null ? 0 : total, rows);
    }

    /** 상태별 건수(상태 조건만 뺀 같은 조건 — 차단 거르기를 켰으면 그것까지) — ACTIVE·FLAGGED·ENDED·EXPIRED·ALL */
    public Map<String, Long> counts(Filter f, Collection<String> blockedHosts, int autoExpireDays, Instant now) {
        Where w = where(f, now);
        if (blockedHosts != null) {
            w.blocked(blockedHosts, autoExpireDays, now);
        }
        String expired = expired(autoExpireDays);
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            args.add(now);
        }
        args.addAll(w.args);
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query("SELECT count(*) FILTER (WHERE d.status = 'ACTIVE' AND NOT " + expired + " AND d.flagged_at IS NULL), "
                + "count(*) FILTER (WHERE d.status = 'ACTIVE' AND NOT " + expired + " AND d.flagged_at IS NOT NULL), "
                + "count(*) FILTER (WHERE d.status = 'ENDED'), "
                + "count(*) FILTER (WHERE d.status = 'ACTIVE' AND " + expired + "), count(*) " + w.from() + w.sql(), rs -> {
                    out.put("ACTIVE", rs.getLong(1));
                    out.put("FLAGGED", rs.getLong(2));
                    out.put("ENDED", rs.getLong(3));
                    out.put("EXPIRED", rs.getLong(4));
                    out.put(ALL, rs.getLong(5));
                }, args.stream().map(AdminDealQueries::bind).toArray());
        return out;
    }

    /** 차단 도메인 진행 중 건수(상태·차단 조건을 뺀 같은 조건에서) */
    public long blockedActiveCount(Filter f, Collection<String> blockedHosts, int autoExpireDays, Instant now) {
        if (blockedHosts.isEmpty()) {
            return 0;
        }
        Where w = where(f, now);
        w.blocked(blockedHosts, autoExpireDays, now);
        Long n = jdbc.queryForObject("SELECT count(*) " + w.from() + w.sql(), Long.class, w.args.stream().map(AdminDealQueries::bind).toArray());
        return n == null ? 0 : n;
    }

    /** 살아 있는 핫딜 게시판 글 한 줄(상세 — 딜 줄이 있어야) */
    public Row row(long postId, Collection<Long> boardIds, Instant now) {
        if (boardIds.isEmpty()) {
            return null;
        }
        Filter f = new Filter(List.copyOf(boardIds), null, null, null, null);
        Where w = where(f, now);
        w.sql.append(" AND p.id = ?");
        w.args.add(postId);
        List<Object> args = new ArrayList<>(List.of(now, now));
        args.addAll(w.args);
        List<Row> rows = jdbc.query("SELECT p.id, p.title, p.board_id, p.prefix_id, p.author_id, p.created_at, p.like_count, "
                        + "(p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= ?)) AND p.group_id IS NULL AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > ?) "
                        + w.from() + w.sql(),
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getLong(3), (Long) rs.getObject(4), (Long) rs.getObject(5), rs.getTimestamp(6).toInstant(),
                        rs.getInt(7), rs.getBoolean(8)), args.stream().map(AdminDealQueries::bind).toArray());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 글 ID → 지금 게시판 ID(지운 글 포함) */
    public Map<Long, Long> postBoards(Collection<Long> postIds) {
        Map<Long, Long> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT id, board_id FROM posts WHERE id IN (" + marks(postIds.size()) + ")", rs -> {
            out.put(rs.getLong(1), rs.getLong(2));
        }, postIds.toArray());
        return out;
    }

    /** OG 사본의 원본 주소(사본 ID → 주소) */
    public Map<Long, String> ogOrigins(Collection<Long> ogImageIds) {
        Map<Long, String> out = new HashMap<>();
        if (ogImageIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT id, origin_url FROM deal_og_images WHERE id IN (" + marks(ogImageIds.size()) + ")", rs -> {
            out.put(rs.getLong(1), rs.getString(2));
        }, ogImageIds.toArray());
        return out;
    }

    /** 같은 딜(진행 중 · 공개 글 · 이 글 제외, 최신 차례) — 글 ID·제목·딜가·등록 시각 */
    public record SameRow(long postId, String title, long dealPrice, Instant createdAt) {
    }

    public List<SameRow> sameDeals(String key, long excludePostId, int autoExpireDays, Instant now, int limit) {
        return jdbc.query("SELECT d.post_id, p.title, d.deal_price, d.created_at FROM post_deals d JOIN posts p ON p.id = d.post_id WHERE d.product_url_key = ? "
                        + "AND d.post_id <> ? AND " + live() + " AND " + PUBLIC_POST + " ORDER BY d.post_id DESC LIMIT ?",
                (rs, i) -> new SameRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getTimestamp(4).toInstant()),
                key, excludePostId, ts(now), autoExpireDays, ts(now.minus(Duration.ofDays(autoExpireDays))), ts(now), ts(now), limit);
    }

    // ── 영향 미리 보기 ──────────────────────────────────────────────────────

    /** 진행 중(ACTIVE·FLAGGED) 공개 글의 딜 — 영향 미리 보기·제휴 방식 전환 셈 */
    public record LiveDeal(long postId, String title, String storeName, String productHost, String finalHost, boolean affiliate) {
    }

    /** 상품·최종 호스트가 이 도메인(하위 도메인 포함)인 진행 중 공개 글 수와 최근 것 몇 개 */
    public record DomainImpact(long count, List<LiveDeal> samples) {
    }

    public DomainImpact domainImpact(String domain, int autoExpireDays, Instant now, int sampleMax) {
        String match = "(d.product_host = ? OR d.product_host LIKE ? OR d.final_host = ? OR d.final_host LIKE ?)";
        String suffix = "%." + domain;
        List<Object> args = new ArrayList<>(List.of(domain, suffix, domain, suffix, ts(now), autoExpireDays, ts(now.minus(Duration.ofDays(autoExpireDays))), ts(now),
                ts(now)));
        String from = " FROM post_deals d JOIN posts p ON p.id = d.post_id WHERE " + match + " AND " + live() + " AND " + PUBLIC_POST;
        Long n = jdbc.queryForObject("SELECT count(*)" + from, Long.class, args.toArray());
        args.add(sampleMax);
        List<LiveDeal> samples = jdbc.query("SELECT d.post_id, p.title, d.store_name, d.product_host, d.final_host, d.affiliate" + from + " ORDER BY d.post_id DESC LIMIT ?",
                (rs, i) -> new LiveDeal(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6)), args.toArray());
        return new DomainImpact(n == null ? 0 : n, samples);
    }

    /** 진행 중 공개 글의 딜 전부(제휴 방식 전환 셈 — 호스트·개인 제휴 표시만) */
    public List<LiveDeal> liveDeals(int autoExpireDays, Instant now) {
        return jdbc.query("SELECT d.post_id, NULL, d.store_name, d.product_host, d.final_host, d.affiliate FROM post_deals d JOIN posts p ON p.id = d.post_id WHERE "
                        + live() + " AND " + PUBLIC_POST,
                (rs, i) -> new LiveDeal(rs.getLong(1), null, rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6)),
                ts(now), autoExpireDays, ts(now.minus(Duration.ofDays(autoExpireDays))), ts(now), ts(now));
    }

    // ── 위장 홍보 의심 후보 ──────────────────────────────────────────────────

    /** 후보 한 줄(표 + 읽을 때 센 값) */
    public record SuspicionRow(long id, String kind, String status, Long memberId, String host, String storeName, int postCount, int memberCount,
                               Integer otherRatioPercent, int windowDays, Instant detectedAt, Instant lastPostAt, Long dismissedBy, Instant dismissedAt,
                               String note, List<Long> postIds) {
    }

    private static final String SUSPICION_COLUMNS = "s.id, s.kind, s.status, s.member_id, s.host, s.store_name, s.post_count, s.member_count, s.other_ratio_percent, "
            + "s.window_days, s.detected_at, (SELECT max(d.created_at) FROM post_deals d WHERE d.post_id = ANY (s.post_ids)) AS last_post_at, s.dismissed_by, "
            + "s.dismissed_at, s.dismiss_note, s.post_ids";

    /** 상태(OPEN·DISMISSED)·종류(null = 둘 다)의 한 쪽 — 마지막 글이 최근인 것부터 */
    public List<SuspicionRow> suspicions(String status, String kind, long offset, int limit) {
        List<Object> args = new ArrayList<>(List.of(status));
        String where = " WHERE s.status = ?";
        if (kind != null) {
            where += " AND s.kind = ?";
            args.add(kind);
        }
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + SUSPICION_COLUMNS + " FROM deal_suspicions s" + where + " ORDER BY last_post_at DESC NULLS LAST, s.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> suspicion(rs), args.toArray());
    }

    public SuspicionRow suspicion(long id) {
        List<SuspicionRow> rows = jdbc.query("SELECT " + SUSPICION_COLUMNS + " FROM deal_suspicions s WHERE s.id = ?", (rs, i) -> suspicion(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** (상태 → 종류 → 수) */
    public Map<String, Map<String, Long>> suspicionCounts() {
        Map<String, Map<String, Long>> out = new HashMap<>();
        jdbc.query("SELECT status, kind, count(*) FROM deal_suspicions GROUP BY status, kind", rs -> {
            out.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), rs.getLong(3));
        });
        return out;
    }

    /** 같은 판매처에 기간 안 글을 올린 다른 회원 수(회원 후보 — 사업자 글 제외) */
    public int otherMembers(String host, long memberId, int windowDays, Instant now) {
        Integer n = jdbc.queryForObject("SELECT count(DISTINCT d.author_id) FROM post_deals d WHERE coalesce(d.final_host, d.product_host) = ? AND d.source = 'MEMBER' "
                + "AND d.author_id IS NOT NULL AND d.author_id <> ? AND d.created_at >= ?", Integer.class, host, memberId, ts(now.minus(Duration.ofDays(windowDays))));
        return n == null ? 0 : n;
    }

    /** 후보 글 한 줄 */
    public record SuspicionPost(long postId, String title, Long boardId, Long authorId, long dealPrice, String status, Instant flaggedAt, Instant endsAt,
                                Instant createdAt) {
    }

    /** 후보의 글(최신 차례, 지운 글은 글 행이 있으면 든다) */
    public List<SuspicionPost> suspicionPosts(List<Long> postIds, int limit) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>(postIds);
        args.add(limit);
        return jdbc.query("SELECT d.post_id, p.title, p.board_id, d.author_id, d.deal_price, d.status, d.flagged_at, d.ends_at, d.created_at FROM post_deals d "
                        + "LEFT JOIN posts p ON p.id = d.post_id WHERE d.post_id IN (" + marks(postIds.size()) + ") ORDER BY d.post_id DESC LIMIT ?",
                (rs, i) -> new SuspicionPost(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3), (Long) rs.getObject(4), rs.getLong(5), rs.getString(6),
                        instant(rs.getTimestamp(7)), instant(rs.getTimestamp(8)), rs.getTimestamp(9).toInstant()), args.toArray());
    }

    /** 판매처 후보의 회원별 셈 — (회원 → [이 판매처 글 수, 다른 판매처 핫딜 수]) 기간 안, 사업자 글 제외 */
    public Map<Long, long[]> storeMembers(String host, Collection<Long> memberIds, int windowDays, Instant now) {
        Map<Long, long[]> out = new LinkedHashMap<>();
        if (memberIds.isEmpty()) {
            return out;
        }
        List<Object> args = new ArrayList<>(List.of(host, host));
        args.addAll(memberIds);
        args.add(ts(now.minus(Duration.ofDays(windowDays))));
        jdbc.query("SELECT d.author_id, count(*) FILTER (WHERE coalesce(d.final_host, d.product_host) = ?), count(*) FILTER (WHERE coalesce(d.final_host, d.product_host) <> ?) "
                + "FROM post_deals d WHERE d.source = 'MEMBER' AND d.author_id IN (" + marks(memberIds.size()) + ") AND d.created_at >= ? GROUP BY d.author_id "
                + "ORDER BY 2 DESC, d.author_id", rs -> {
                    out.put(rs.getLong(1), new long[] {rs.getLong(2), rs.getLong(3)});
                }, args.toArray());
        return out;
    }

    /** 열린 후보 무시(조건부 — 이미 무시됐으면 0) */
    public int dismissSuspicion(long id, long operatorId, String note, Instant now) {
        return jdbc.update("UPDATE deal_suspicions SET status = 'DISMISSED', dismissed_at = ?, dismissed_by = ?, dismiss_note = ?, updated_at = ? WHERE id = ? AND status = 'OPEN'",
                ts(now), operatorId, note, ts(now), id);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private SuspicionRow suspicion(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Array arr = rs.getArray(16);
        List<Long> ids = new ArrayList<>();
        if (arr != null) {
            for (Object v : (Object[]) arr.getArray()) {
                ids.add(((Number) v).longValue());
            }
        }
        return new SuspicionRow(rs.getLong(1), rs.getString(2), rs.getString(3), (Long) rs.getObject(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8),
                (Integer) rs.getObject(9), rs.getInt(10), rs.getTimestamp(11).toInstant(), instant(rs.getTimestamp(12)), (Long) rs.getObject(13),
                instant(rs.getTimestamp(14)), rs.getString(15), ids);
    }

    /** 기간 종료 조건(인자 1개 now) — 마감 지남, 또는 마감 없고 등록 + 설정 일수 지남(0 = 끔) */
    private static String expired(int autoExpireDays) {
        return "coalesce(CASE WHEN d.ends_at IS NOT NULL THEN d.ends_at WHEN " + autoExpireDays + " > 0 THEN d.created_at + make_interval(days => " + autoExpireDays
                + ") END <= ?, false)";
    }

    /** 진행 중(ACTIVE·FLAGGED) — 인자 3개(now, autoExpireDays, now - autoExpireDays). shared {@code DealQueries.LIVE_DEAL} 과 같다 */
    private static String live() {
        return "d.status = 'ACTIVE' AND (d.ends_at IS NULL OR d.ends_at > ?) AND (d.ends_at IS NOT NULL OR ? = 0 OR d.created_at > ?)";
    }

    private Where where(Filter f, Instant now) {
        Where w = new Where();
        if (f.boardIds().isEmpty()) {
            w.sql.append(" AND false");
            return w;
        }
        w.sql.append(" AND ").append(ALIVE_POST);
        w.args.add(now);
        w.sql.append(" AND p.board_id IN (").append(marks(f.boardIds().size())).append(")");
        w.args.addAll(f.boardIds());
        if (f.from() != null) {
            w.sql.append(" AND p.created_at >= ?");
            w.args.add(f.from());
        }
        if (f.to() != null) {
            w.sql.append(" AND p.created_at <= ?");
            w.args.add(f.to());
        }
        if (f.source() != null) {
            w.sql.append(" AND d.source = ?");
            w.args.add(f.source());
        }
        if (f.q() != null) {
            String like = "%" + f.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.sql.append(" AND (p.title ILIKE ? ESCAPE '\\' OR d.store_name ILIKE ? ESCAPE '\\' OR pr.nickname ILIKE ? ESCAPE '\\')");
            w.args.add(like);
            w.args.add(like);
            w.args.add(like);
        }
        return w;
    }

    /** WHERE 조각과 인자(차례대로) */
    private static final class Where {

        private final StringBuilder sql = new StringBuilder();
        private final List<Object> args = new ArrayList<>();

        String from() {
            return " FROM post_deals d JOIN posts p ON p.id = d.post_id LEFT JOIN profiles pr ON pr.user_id = p.author_id";
        }

        String sql() {
            return " WHERE true" + sql;
        }

        void view(String view, int autoExpireDays, Instant now) {
            String expired = expired(autoExpireDays);
            switch (view) {
                case "ACTIVE" -> {
                    sql.append(" AND d.status = 'ACTIVE' AND NOT ").append(expired).append(" AND d.flagged_at IS NULL");
                    args.add(now);
                }
                case "FLAGGED" -> {
                    sql.append(" AND d.status = 'ACTIVE' AND NOT ").append(expired).append(" AND d.flagged_at IS NOT NULL");
                    args.add(now);
                }
                case "ENDED" -> sql.append(" AND d.status = 'ENDED'");
                case "EXPIRED" -> {
                    sql.append(" AND d.status = 'ACTIVE' AND ").append(expired);
                    args.add(now);
                }
                default -> {
                }
            }
        }

        /** 진행 중이고 상품·최종 호스트가 차단 호스트 */
        void blocked(Collection<String> hosts, int autoExpireDays, Instant now) {
            if (hosts.isEmpty()) {
                sql.append(" AND false");
                return;
            }
            sql.append(" AND ").append(live());
            args.add(now);
            args.add(autoExpireDays);
            args.add(now.minus(Duration.ofDays(autoExpireDays)));
            String in = marks(hosts.size());
            sql.append(" AND (d.product_host IN (").append(in).append(") OR d.final_host IN (").append(in).append("))");
            args.addAll(hosts);
            args.addAll(hosts);
        }
    }

    private static Object bind(Object value) {
        return value instanceof Instant at ? ts(at) : value;
    }

    private static String marks(int n) {
        return IntStream.range(0, n).mapToObj(i -> "?").collect(Collectors.joining(", "));
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
