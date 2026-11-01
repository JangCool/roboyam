package com.community.hotdeal.repository.master;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 핫딜 집계·한도 질의 — 글 표({@code posts})와 딜 표를 함께 보는 질의, 원자적 클릭 셈. <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>Native SQL 인 이유: 「공개·진행 중」 판정이 글 보이기 조건(지움·예약 삭제·숨김·소모임) + 딜 상태 + 기간 종료(마감 또는 등록 + 설정 일수)를 한 번에 보고,
 * 키·글별 묶음 수를 한 번에 센다(엔티티 연관이 없는 두 표 — 글 표는 파티션 표라 FK 도 없다). {@code ON CONFLICT DO NOTHING} 클릭 셈도 SQL 전용.
 * 「보이는 글」 조건은 {@code PostRepositoryQDSL#alive}·{@code #notHidden}·{@code #feedVisible}(일반 글만) 과 같다 — 한쪽을 고치면 같이 고친다.
 */
@Repository
public class DealQueries {

    /** 공개 글(지우지 않음·공개·예약 삭제 전·숨김 아님·일반 글) — 인자 2개(now, now) */
    static final String PUBLIC_POST = "p.deleted_at IS NULL AND p.status = 'PUBLISHED' AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > ?) "
            + "AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= ?)) AND p.group_id IS NULL";
    /** 진행 중(ACTIVE·FLAGGED — 종료도 기간 종료도 아님) — 인자 3개(now, autoExpireDays, now - autoExpireDays) */
    static final String LIVE_DEAL = "d.status = 'ACTIVE' AND (d.ends_at IS NULL OR d.ends_at > ?) AND (d.ends_at IS NOT NULL OR ? = 0 OR d.created_at > ?)";

    private final JdbcTemplate jdbc;

    public DealQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 같은 딜 한 줄 */
    public record SameDeal(long postId, String title, long dealPrice, Long shippingFee, Instant createdAt) {
    }

    /** 종료 요청 집계 */
    public record EndCounts(int total, int soldOut, int priceChanged, int linkDead) {
    }

    /** 쇼핑몰 칩 */
    public record StoreCount(String name, long count) {
    }

    /** 그 회원이 {@code since} 뒤에 올린 딜 수(지운 글 포함 — 지우고 다시 올려 한도를 넘지 못하게) */
    public int countByAuthorSince(long authorId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM post_deals WHERE author_id = ? AND created_at >= ?", Integer.class, authorId, ts(since));
        return n == null ? 0 : n;
    }

    /** 그 회원이 {@code since} 뒤에 같은 판매처(최종 호스트, 없으면 호스트)에 올린 마지막 딜 시각 */
    public Optional<Instant> lastAtSameStore(long authorId, String storeHost, Instant since) {
        List<Timestamp> rows = jdbc.queryForList("SELECT max(created_at) FROM post_deals WHERE author_id = ? AND coalesce(final_host, product_host) = ? AND created_at >= ?",
                Timestamp.class, authorId, storeHost, ts(since));
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0).toInstant());
    }

    /** 공개·진행 중인 같은 딜(최신 차례) — {@code excludePostId} 는 빼고 */
    public List<SameDeal> sameDeals(String key, Long excludePostId, int autoExpireDays, Instant now, int limit) {
        return jdbc.query("SELECT d.post_id, p.title, d.deal_price, d.shipping_fee, d.created_at FROM post_deals d JOIN posts p ON p.id = d.post_id "
                        + "WHERE d.product_url_key = ? AND d.post_id <> ? AND " + LIVE_DEAL + " AND " + PUBLIC_POST + " ORDER BY d.post_id DESC LIMIT ?",
                (rs, i) -> new SameDeal(rs.getLong(1), rs.getString(2), rs.getLong(3), (Long) rs.getObject(4), rs.getTimestamp(5).toInstant()),
                key, excludePostId == null ? 0L : excludePostId, ts(now), autoExpireDays, ts(now.minus(Duration.ofDays(autoExpireDays))), ts(now), ts(now), limit);
    }

    /** 키별 공개·진행 중 글 수(목록 줄의 {@code sameDealCount} — 자기 자신이 진행 중이면 부르는 쪽이 1 을 뺀다) */
    public Map<String, Integer> liveCountsByKey(Collection<String> keys, int autoExpireDays, Instant now) {
        Map<String, Integer> out = new HashMap<>();
        if (keys.isEmpty()) {
            return out;
        }
        List<Object> args = new ArrayList<>(keys);
        args.add(ts(now));
        args.add(autoExpireDays);
        args.add(ts(now.minus(Duration.ofDays(autoExpireDays))));
        args.add(ts(now));
        args.add(ts(now));
        jdbc.query("SELECT d.product_url_key, count(*) FROM post_deals d JOIN posts p ON p.id = d.post_id WHERE d.product_url_key IN (" + marks(keys.size()) + ") AND "
                + LIVE_DEAL + " AND " + PUBLIC_POST + " GROUP BY d.product_url_key", rs -> {
                    out.put(rs.getString(1), rs.getInt(2));
                }, args.toArray());
        return out;
    }

    /** 글별 종료 요청 집계(요청이 없는 글은 빠진다) */
    public Map<Long, EndCounts> endRequestCounts(Collection<Long> postIds) {
        Map<Long, EndCounts> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT post_id, count(*), count(*) FILTER (WHERE reason = 'SOLD_OUT'), count(*) FILTER (WHERE reason = 'PRICE_CHANGED'), "
                + "count(*) FILTER (WHERE reason = 'LINK_DEAD') FROM deal_end_requests WHERE post_id IN (" + marks(postIds.size()) + ") GROUP BY post_id", rs -> {
                    out.put(rs.getLong(1), new EndCounts(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
                }, postIds.toArray());
        return out;
    }

    /** 그 회원이 보낸 사유(글 ID → 사유) */
    public Map<Long, String> myEndRequests(Collection<Long> postIds, long memberId) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        List<Object> args = new ArrayList<>(postIds);
        args.add(memberId);
        jdbc.query("SELECT post_id, reason FROM deal_end_requests WHERE post_id IN (" + marks(postIds.size()) + ") AND member_id = ?", rs -> {
            out.put(rs.getLong(1), rs.getString(2));
        }, args.toArray());
        return out;
    }

    /**
     * 쇼핑몰 칩 — 공개 글, 말머리·종료 숨기기를 적용하고 쇼핑몰 거르기는 적용하지 않은 글 수(많은 차례, 같으면 이름 차례).
     *
     * @param hideEnded 종료·기간 종료 글을 뺀다
     */
    public List<StoreCount> storeChips(long boardId, Long prefixId, boolean hideEnded, int autoExpireDays, Instant now, int limit) {
        List<Object> args = new ArrayList<>(List.of(boardId, ts(now), ts(now)));
        StringBuilder sql = new StringBuilder("SELECT d.store_name, count(*) AS n FROM post_deals d JOIN posts p ON p.id = d.post_id WHERE p.board_id = ? AND ")
                .append(PUBLIC_POST);
        if (prefixId != null) {
            sql.append(" AND p.prefix_id = ?");
            args.add(prefixId);
        }
        if (hideEnded) {
            sql.append(" AND ").append(LIVE_DEAL);
            args.add(ts(now));
            args.add(autoExpireDays);
            args.add(ts(now.minus(Duration.ofDays(autoExpireDays))));
        }
        sql.append(" GROUP BY d.store_name ORDER BY n DESC, d.store_name LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, i) -> new StoreCount(rs.getString(1), rs.getLong(2)), args.toArray());
    }

    /**
     * 구매 링크 클릭 — 그 날(한국 날짜) 같은 방문자·같은 글은 한 번만 센다.
     *
     * @return 이번에 셌다
     */
    public boolean recordClick(long postId, LocalDate day, String visitorHash, Instant now) {
        int inserted = jdbc.update("INSERT INTO deal_click_daily (post_id, day, visitor_hash, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING", postId,
                java.sql.Date.valueOf(day), visitorHash, ts(now));
        if (inserted == 0) {
            return false;
        }
        jdbc.update("UPDATE post_deals SET click_count = click_count + 1 WHERE post_id = ?", postId);
        return true;
    }

    private static String marks(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> "?").collect(Collectors.joining(", "));
    }

    private static Timestamp ts(Instant at) {
        return Timestamp.from(at);
    }
}
