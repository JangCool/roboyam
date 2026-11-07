package com.community.hotdeal.repository.master;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 위장 홍보 의심 후보 계산({@code post_deals} 집계)과 후보 표({@code deal_suspicions}) 맞추기 — {@code DealSuspicionService}(배치 {@code hotdeal-suspicion})가
 * <b>부르는 쪽 트랜잭션 안에서</b> 부른다. 무엇을 후보로 보는지는 그 서비스 설명.
 *
 * <p>Native SQL 인 이유: 후보 계산은 {@code mode() WITHIN GROUP}·{@code array_agg}·{@code count(DISTINCT …)}·{@code FILTER} 가 핵심인 CTE 집계이고, 대형몰 제외는 접미사
 * 비교({@code LIKE '%.' || domain})다. 후보 표는 엔티티가 없고 글 ID 배열({@code post_ids bigint[]})을 쓴다(관리자 {@code AdminDealQueries} 의 후보 읽기·무시와 같은 결정).
 * 열린 후보 만들기는 {@code INSERT … ON CONFLICT DO NOTHING}(대상마다 열린 후보 하나 — 다른 실행이 막 열었으면 건너뛴다).
 */
@Repository
public class DealSuspicionQueries {

    /** 계산한 후보 한 줄(회원 후보는 {@code memberCount = 1}·{@code otherRatioPercent = null}, 판매처 후보는 {@code memberId = null}) */
    public record CandidateRow(Long memberId, String host, String storeName, int postCount, int memberCount, Integer otherRatioPercent, List<Long> postIds,
                               Instant lastPostAt) {
    }

    /** 대형몰 제외 — 판매처 호스트 칸 이름을 받는 조각 */
    private static String notCommon(String hostExpr) {
        return "NOT EXISTS (SELECT 1 FROM deal_domains dd WHERE dd.common AND (" + hostExpr + " = dd.domain OR " + hostExpr + " LIKE '%.' || dd.domain))";
    }

    /** 판매처 = 최종 호스트(없으면 호스트) */
    private static final String HOST = "coalesce(d.final_host, d.product_host)";

    private final JdbcTemplate jdbc;

    public DealSuspicionQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 회원 후보 — {@code since} 이후 같은 회원이 같은 판매처(대형몰 빼고)에 회원 글 {@code minPosts} 건 이상 */
    public List<CandidateRow> memberCandidates(Instant since, int minPosts) {
        return jdbc.query("SELECT d.author_id, " + HOST + " AS host, count(*) AS n, array_agg(d.post_id ORDER BY d.post_id DESC) AS ids, max(d.created_at) AS last_at, "
                        + "mode() WITHIN GROUP (ORDER BY d.store_name) AS store FROM post_deals d "
                        + "WHERE d.source = 'MEMBER' AND d.author_id IS NOT NULL AND d.created_at >= ? AND " + notCommon(HOST) + " "
                        + "GROUP BY d.author_id, " + HOST + " HAVING count(*) >= ? ORDER BY d.author_id, host",
                (rs, i) -> new CandidateRow(rs.getLong(1), rs.getString(2), rs.getString(6), rs.getInt(3), 1, null, longs(rs.getArray(4)), rs.getTimestamp(5).toInstant()),
                ts(since), minPosts);
    }

    /** 판매처 후보 — {@code since} 이후 서로 다른 회원 {@code minMembers} 명 이상이 올리고, 그 회원들의 다른 판매처 글 비율이 {@code otherRatioPercent}% 미만 */
    public List<CandidateRow> storeCandidates(Instant since, int minMembers, int otherRatioPercent) {
        String sql = "WITH w AS (SELECT d.post_id, d.author_id, " + HOST + " AS host, d.store_name, d.created_at FROM post_deals d "
                + "  WHERE d.source = 'MEMBER' AND d.author_id IS NOT NULL AND d.created_at >= ?), "
                + "h AS (SELECT w.host, count(DISTINCT w.author_id) AS members, count(*) AS posts, array_agg(w.post_id ORDER BY w.post_id DESC) AS ids, "
                + "  max(w.created_at) AS last_at, mode() WITHIN GROUP (ORDER BY w.store_name) AS store FROM w WHERE " + notCommon("w.host") + " "
                + "  GROUP BY w.host HAVING count(DISTINCT w.author_id) >= ?), "
                + "r AS (SELECT h.host, count(*) FILTER (WHERE w.host <> h.host) AS other, count(*) AS total FROM h "
                + "  JOIN (SELECT DISTINCT host, author_id FROM w) a ON a.host = h.host JOIN w ON w.author_id = a.author_id GROUP BY h.host) "
                + "SELECT h.host, h.members, h.posts, h.ids, h.last_at, h.store, r.other, r.total FROM h JOIN r ON r.host = h.host "
                + "WHERE r.other * 100 < ? * r.total ORDER BY h.host";
        return jdbc.query(sql, (rs, i) -> {
            long other = rs.getLong(7);
            long total = rs.getLong(8);
            return new CandidateRow(null, rs.getString(1), rs.getString(6), rs.getInt(3), rs.getInt(2), (int) (other * 100 / Math.max(1, total)), longs(rs.getArray(4)),
                    rs.getTimestamp(5).toInstant());
        }, ts(since), minMembers, otherRatioPercent);
    }

    /** 대상(종류·판매처·회원 — 판매처 후보는 회원 0)의 열린 후보 수를 고친다 — 고쳤으면 true(열린 후보가 있다) */
    public boolean refreshOpen(String kind, String host, long memberKey, int postCount, int memberCount, Integer otherRatioPercent, int windowDays, List<Long> postIds,
                               String storeName, Instant now) {
        return jdbc.update("UPDATE deal_suspicions SET post_count = ?, member_count = ?, other_ratio_percent = ?, window_days = ?, post_ids = ?::bigint[], "
                        + "store_name = ?, updated_at = ? WHERE kind = ? AND host = ? AND coalesce(member_id, 0) = ? AND status = 'OPEN'",
                postCount, memberCount, otherRatioPercent, windowDays, array(postIds), storeName, ts(now), kind, host, memberKey) > 0;
    }

    /** 대상의 무시한 후보 가운데 마지막 무시 시각(없으면 null) */
    public Instant lastDismissedAt(String kind, String host, long memberKey) {
        List<Timestamp> dismissed = jdbc.queryForList("SELECT max(dismissed_at) FROM deal_suspicions WHERE kind = ? AND host = ? AND coalesce(member_id, 0) = ? "
                + "AND status = 'DISMISSED'", Timestamp.class, kind, host, memberKey);
        return dismissed.isEmpty() || dismissed.get(0) == null ? null : dismissed.get(0).toInstant();
    }

    /** 열린 후보를 만든다 — 만들었으면 true(같은 대상을 다른 실행이 막 열었으면 false) */
    public boolean insertOpen(long id, String kind, Long memberId, String host, String storeName, int postCount, int memberCount, Integer otherRatioPercent,
                              int windowDays, List<Long> postIds, Instant now) {
        return jdbc.update("INSERT INTO deal_suspicions (id, kind, status, member_id, host, store_name, post_count, member_count, other_ratio_percent, window_days, "
                        + "post_ids, detected_at, updated_at) VALUES (?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?::bigint[], ?, ?) ON CONFLICT DO NOTHING",
                id, kind, memberId, host, storeName, postCount, memberCount, otherRatioPercent, windowDays, array(postIds), ts(now), ts(now)) > 0;
    }

    /** {@code bigint[]} 글자 꼴({@code {1,2,3}}) */
    private static String array(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private static List<Long> longs(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (raw instanceof Long[] values) {
            return new ArrayList<>(Arrays.asList(values));
        }
        List<Long> out = new ArrayList<>();
        for (Object v : (Object[]) raw) {
            out.add(((Number) v).longValue());
        }
        return out;
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }
}
