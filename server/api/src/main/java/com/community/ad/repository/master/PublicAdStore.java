package com.community.ad.repository.master;

import com.community.bizapplication.entity.BizProduct;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공개 광고 노출 읽기·집계(ad-contract 「서버 구현 결정」 「공개 노출 쿼리 후보」, V118) — 장부는 신청 건(biz_applications) 그대로다.
 *
 * <ul>
 *   <li>지금 나가는 건 = 그 상품 자리 줄이 있고 {@code status = 'SCHEDULED' AND start_at <= now < end_at} + 그 건의 <b>승인된</b> 소재 버전 중 최신
 *       ({@code approved_at IS NOT NULL}). 승인된 버전이 없으면 빠진다. 긴급 내림({@code TAKEN_DOWN})·종료·결제 대기는 상태·시각으로 빠진다.</li>
 *   <li>집계는 한 문장 — 하루 1번 판정 표({@code ad_view_marks})에 {@code ON CONFLICT DO NOTHING} 으로 넣고, 들어갔을 때만 일별 집계({@code ad_daily_stats})에 +1.</li>
 * </ul>
 */
@Repository
public class PublicAdStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    /** 지금 나가는 건 하나 — 소재는 최신 승인 버전의 내용(JSON) */
    public record Running(long applicationId, BizProduct product, int weight, String advertiserName, int version, Map<String, Object> content) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public PublicAdStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(dataSource);
    }

    /** 그 상품들에서 지금 나가는 건(신청 건 차례) */
    public List<Running> running(Collection<BizProduct> products, Instant now) {
        if (products.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("products", products.stream().map(Enum::name).toList()).addValue("now", Timestamp.from(now));
        return named.query("SELECT a.id, i.product, a.weight, COALESCE(a.company_name, a.advertiser_name) AS advertiser_name, c.version, c.content::text AS content "
                        + "FROM biz_applications a "
                        + "JOIN biz_application_items i ON i.application_id = a.id AND i.product IN (:products) "
                        + "JOIN LATERAL (SELECT v.version, v.content FROM biz_creative_versions v WHERE v.application_id = a.id AND v.approved_at IS NOT NULL "
                        + "              ORDER BY v.version DESC LIMIT 1) c ON true "
                        + "WHERE a.status = 'SCHEDULED' AND a.start_at <= :now AND a.end_at > :now ORDER BY a.id",
                params, (rs, i) -> new Running(rs.getLong("id"), BizProduct.valueOf(rs.getString("product")), rs.getInt("weight"), rs.getString("advertiser_name"),
                        rs.getInt("version"), JSON.readValue(rs.getString("content"), MAP)));
    }

    /** 핫딜 노출 자리 건의 글 — 진행 중(저장 ACTIVE · 마감 전, 의심 포함)이고 공개(지움·예약 삭제 지남·숨김 아님)인 사업자 글만 */
    public record DealPost(long applicationId, long postId, String title, long boardId, Long prefixId, String storeName, long dealPrice, Long normalPrice,
                           String imageUrl) {
    }

    /** 그 신청 건들의 글(V123 biz_deal_posts POSTED) — 진행 중·공개인 것만(biz-hotdeal-contract 규칙 6·7) */
    public Map<Long, DealPost> dealPosts(Collection<Long> applicationIds, Instant now) {
        Map<Long, DealPost> out = new java.util.HashMap<>();
        if (applicationIds.isEmpty()) {
            return out;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", applicationIds).addValue("now", Timestamp.from(now));
        named.query("SELECT b.application_id, p.id, p.title, p.board_id, p.prefix_id, d.store_name, d.deal_price, d.normal_price, d.image_url "
                + "FROM biz_deal_posts b JOIN posts p ON p.id = b.post_id JOIN post_deals d ON d.post_id = p.id "
                + "WHERE b.application_id IN (:ids) AND b.status = 'POSTED' AND d.source = 'BUSINESS' AND d.status = 'ACTIVE' AND (d.ends_at IS NULL OR d.ends_at > :now) "
                + "AND p.deleted_at IS NULL AND p.status = 'PUBLISHED' AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > :now) "
                + "AND (p.hidden_at IS NULL OR (p.hidden_until IS NOT NULL AND p.hidden_until <= :now))", params, (java.sql.ResultSet rs) -> {
                    DealPost d = new DealPost(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), (Long) rs.getObject(5, Long.class), rs.getString(6),
                            rs.getLong(7), (Long) rs.getObject(8, Long.class), rs.getString(9));
                    out.put(d.applicationId(), d);
                });
        return out;
    }

    /**
     * 하루 1번 집계 — 같은 날·방문자·종류·건·버전·자리가 처음이면 일별 집계에 +1
     *
     * @param kind {@code I} 노출 · {@code C} 클릭
     * @return 셌으면 true(이미 센 것이면 false)
     */
    public boolean count(char kind, LocalDate day, String visitorHash, long applicationId, int version, String placement) {
        String column = kind == 'C' ? "clicks" : "impressions";
        int clicks = kind == 'C' ? 1 : 0;
        int impressions = kind == 'C' ? 0 : 1;
        int n = jdbc.update("WITH mark AS (INSERT INTO ad_view_marks (day, visitor_hash, kind, application_id, version, placement) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT DO NOTHING RETURNING 1) "
                        + "INSERT INTO ad_daily_stats (application_id, version, day, placement, impressions, clicks, updated_at) "
                        + "SELECT ?, ?, ?, ?, ?, ?, now() FROM mark "
                        + "ON CONFLICT (application_id, version, day, placement) DO UPDATE SET " + column + " = ad_daily_stats." + column + " + 1, updated_at = now()",
                Date.valueOf(day), visitorHash, String.valueOf(kind), applicationId, version, placement, applicationId, version, Date.valueOf(day), placement,
                impressions, clicks);
        return n > 0;
    }
}
