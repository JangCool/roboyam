package com.community.hotdeal.service;

import com.community.hotdeal.dto.DealSettings;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위장 홍보 의심 후보 계산(확정 가정 「위장 홍보 후보」, 서버 단계 고정 규칙 9·10) — 배치 {@code hotdeal-suspicion} 이 부른다. <b>자동 제재 없음</b> — 후보 표({@code deal_suspicions})에
 * 열린 후보 하나씩 두고 새로 열리면 관리자 알림 {@code HOTDEAL_SUSPECTED_PROMO}.
 *
 * <ul>
 *   <li>판매처 = 최종 호스트(없으면 호스트). <b>대형몰</b>(링크 규칙 {@code common} — 접미사 일치)은 빼고, <b>사업자 글</b>({@code source = BUSINESS})은 셈에서 뺀다. 지운 글도 센다
 *       (지우고 다시 올려 피하지 못하게 — 하루 글 한도와 같다). IP·기기 정보는 쓰지 않는다(법무 확인 — 1차는 안 씀).</li>
 *   <li><b>회원 후보</b>: 같은 회원이 {@code suspicionWindowDays} 안에 같은 판매처에 {@code suspicionMinPosts} 건 이상(0 = 끔).</li>
 *   <li><b>판매처 후보</b>: 같은 판매처를 {@code storeSuspicionWindowDays} 안에 서로 다른 회원 {@code storeSuspicionMembers} 명 이상(0 = 끔)이 올리고, 그 회원들이 같은 기간에 올린 핫딜 중
 *       <b>다른 판매처</b> 글의 비율이 {@code storeSuspicionOtherRatioPercent}% <b>미만</b>.</li>
 *   <li><b>열린 후보</b>: 대상(종류·판매처·회원)마다 하나 — 이미 열려 있으면 수만 고친다(다시 알리지 않는다). 운영자가 무시(DISMISSED)한 대상은 무시한 뒤 새 글이 생겨야 다시 연다.
 *       조건에서 벗어난 열린 후보를 저절로 닫지는 않는다(운영자가 무시).</li>
 * </ul>
 */
@Service
public class DealSuspicionService {

    public static final String MEMBER = "MEMBER";
    public static final String STORE = "STORE";

    /** 후보 줄의 글 ID 최대 수 */
    static final int POST_IDS_MAX = 200;

    /** 대형몰 제외 — 판매처 호스트 칸 이름을 받는 조각 */
    private static String notCommon(String hostExpr) {
        return "NOT EXISTS (SELECT 1 FROM deal_domains dd WHERE dd.common AND (" + hostExpr + " = dd.domain OR " + hostExpr + " LIKE '%.' || dd.domain))";
    }

    private static final String HOST = "coalesce(d.final_host, d.product_host)";

    private final JdbcTemplate jdbc;
    private final DealSettingsReader settings;
    private final SnowflakeIdGenerator ids;
    private final DealNotices notices;

    public DealSuspicionService(DataSource dataSource, DealSettingsReader settings, SnowflakeIdGenerator ids, DealNotices notices) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.settings = settings;
        this.ids = ids;
        this.notices = notices;
    }

    /** 계산한 후보 하나 */
    public record Candidate(String kind, Long memberId, String host, String storeName, int postCount, int memberCount, Integer otherRatioPercent, int windowDays,
                            List<Long> postIds, Instant lastPostAt) {
    }

    /** 한 번 계산한 결과 — 본 후보 수 · 새로 연 후보 수 */
    public record Tally(int seen, int opened) {
    }

    /** 회원 후보를 계산해 맞춘다 */
    @Transactional
    public Tally detectMembers(Instant now) {
        return apply(memberCandidates(now), now);
    }

    /** 판매처 후보를 계산해 맞춘다 */
    @Transactional
    public Tally detectStores(Instant now) {
        return apply(storeCandidates(now), now);
    }

    /** 회원 후보(끔이면 빈 목록) */
    public List<Candidate> memberCandidates(Instant now) {
        DealSettings s = settings.current();
        if (s.suspicionMinPosts() <= 0) {
            return List.of();
        }
        int days = s.suspicionWindowDays();
        return jdbc.query("SELECT d.author_id, " + HOST + " AS host, count(*) AS n, array_agg(d.post_id ORDER BY d.post_id DESC) AS ids, max(d.created_at) AS last_at, "
                        + "mode() WITHIN GROUP (ORDER BY d.store_name) AS store FROM post_deals d "
                        + "WHERE d.source = 'MEMBER' AND d.author_id IS NOT NULL AND d.created_at >= ? AND " + notCommon(HOST) + " "
                        + "GROUP BY d.author_id, " + HOST + " HAVING count(*) >= ? ORDER BY d.author_id, host",
                (rs, i) -> new Candidate(MEMBER, rs.getLong(1), rs.getString(2), rs.getString(6), rs.getInt(3), 1, null, days, longs(rs.getArray(4)),
                        rs.getTimestamp(5).toInstant()),
                ts(now.minus(Duration.ofDays(days))), s.suspicionMinPosts());
    }

    /** 판매처 후보(끔이면 빈 목록) */
    public List<Candidate> storeCandidates(Instant now) {
        DealSettings s = settings.current();
        if (s.storeSuspicionMembers() <= 0) {
            return List.of();
        }
        int days = s.storeSuspicionWindowDays();
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
            return new Candidate(STORE, null, rs.getString(1), rs.getString(6), rs.getInt(3), rs.getInt(2), (int) (other * 100 / Math.max(1, total)), days,
                    longs(rs.getArray(4)), rs.getTimestamp(5).toInstant());
        }, ts(now.minus(Duration.ofDays(days))), s.storeSuspicionMembers(), s.storeSuspicionOtherRatioPercent());
    }

    private Tally apply(List<Candidate> candidates, Instant now) {
        int opened = 0;
        for (Candidate c : candidates) {
            opened += upsert(c, now) ? 1 : 0;
        }
        return new Tally(candidates.size(), opened);
    }

    /** 열린 후보가 있으면 수만 고치고, 없으면(무시한 뒤 새 글이 없으면 건너뛴다) 연다 — 새로 열었으면 true(관리자 알림) */
    private boolean upsert(Candidate c, Instant now) {
        long memberKey = c.memberId() == null ? 0L : c.memberId();
        String postIds = c.postIds().stream().limit(POST_IDS_MAX).map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
        int updated = jdbc.update("UPDATE deal_suspicions SET post_count = ?, member_count = ?, other_ratio_percent = ?, window_days = ?, post_ids = ?::bigint[], "
                        + "store_name = ?, updated_at = ? WHERE kind = ? AND host = ? AND coalesce(member_id, 0) = ? AND status = 'OPEN'",
                c.postCount(), c.memberCount(), c.otherRatioPercent(), c.windowDays(), postIds, cut(c.storeName()), ts(now), c.kind(), c.host(), memberKey);
        if (updated > 0) {
            return false;
        }
        List<Timestamp> dismissed = jdbc.queryForList("SELECT max(dismissed_at) FROM deal_suspicions WHERE kind = ? AND host = ? AND coalesce(member_id, 0) = ? "
                + "AND status = 'DISMISSED'", Timestamp.class, c.kind(), c.host(), memberKey);
        if (!dismissed.isEmpty() && dismissed.get(0) != null && !c.lastPostAt().isAfter(dismissed.get(0).toInstant())) {
            return false; // 무시한 뒤 새 글이 없다
        }
        long id = ids.nextId();
        int inserted = jdbc.update("INSERT INTO deal_suspicions (id, kind, status, member_id, host, store_name, post_count, member_count, other_ratio_percent, window_days, "
                        + "post_ids, detected_at, updated_at) VALUES (?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?::bigint[], ?, ?) ON CONFLICT DO NOTHING",
                id, c.kind(), c.memberId(), c.host(), cut(c.storeName()), c.postCount(), c.memberCount(), c.otherRatioPercent(), c.windowDays(), postIds, ts(now), ts(now));
        if (inserted == 0) {
            return false; // 같은 대상을 다른 실행이 막 열었다
        }
        notices.suspected(id);
        return true;
    }

    private static String cut(String name) {
        if (name == null) {
            return null;
        }
        return name.codePointCount(0, name.length()) <= 40 ? name : name.substring(0, name.offsetByCodePoints(0, 40));
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
