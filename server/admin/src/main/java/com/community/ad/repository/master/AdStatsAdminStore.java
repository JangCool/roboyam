package com.community.ad.repository.master;

import com.community.ad.service.AdAdvertiserService;
import com.community.bizapplication.entity.BizProduct;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 운영자 광고 성과 읽기(ad-performance-contract 규칙 9·10) — 합계·건별. 건의 유형 = 한도가 있는 상품 줄(핫딜 게시 {@code HOTDEAL_POST} 가 아닌 첫 줄 — 상품 이름 차례,
 * {@code BizApplicationReader.capacityProduct} 와 같다). 광고주 거르기는 일정표 광고주 ID 규칙 그대로: {@code adv-<id>} = 광고주 연결 건, {@code biz-<id>} = 광고주 연결이 없는
 * 그 사업자 신원의 건.
 */
@Repository
public class AdStatsAdminStore {

    /** 건 · 날짜 · 자리 합(버전은 합친다) */
    public record SumRow(long applicationId, BizProduct product, LocalDate day, String placement, long impressions, long clicks) {
    }

    /** 건별 표 후보 — 범위 합(0 포함) */
    public record BookingSum(long applicationId, BizProduct product, long impressions, long clicks) {
    }

    private static final String TYPE_JOIN = "JOIN LATERAL (SELECT i.product FROM biz_application_items i WHERE i.application_id = a.id AND i.product <> 'HOTDEAL_POST' "
            + "ORDER BY i.product LIMIT 1) t ON true ";

    private final NamedParameterJdbcTemplate named;

    public AdStatsAdminStore(DataSource dataSource) {
        this.named = new NamedParameterJdbcTemplate(dataSource);
    }

    /** 범위 {@code [from, to]}(끝 포함)의 집계 — 유형·광고주 거르기(null = 안 거름) */
    public List<SumRow> sums(LocalDate from, LocalDate to, BizProduct type, AdAdvertiserService.Ref advertiser) {
        MapSqlParameterSource args = new MapSqlParameterSource("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        String sql = "SELECT s.application_id, t.product, s.day, s.placement, sum(s.impressions), sum(s.clicks) FROM ad_daily_stats s "
                + "JOIN biz_applications a ON a.id = s.application_id " + TYPE_JOIN + "WHERE s.day BETWEEN :from AND :to" + filters(type, advertiser, args)
                + " GROUP BY s.application_id, t.product, s.day, s.placement";
        return named.query(sql, args, (rs, i) -> new SumRow(rs.getLong(1), BizProduct.valueOf(rs.getString(2)), rs.getDate(3).toLocalDate(), rs.getString(4),
                rs.getLong(5), rs.getLong(6)));
    }

    /**
     * 건별 표 후보 — 기간이 범위와 겹치고 시작한({@code start_at <= now}) 결제 뒤 상태(예약 · 내림 · 승인 뒤 취소) 건 + 범위 합(0 포함).
     */
    public List<BookingSum> bookings(LocalDate from, LocalDate to, Instant rangeStart, Instant rangeEnd, BizProduct type, AdAdvertiserService.Ref advertiser,
                                     Instant now) {
        MapSqlParameterSource args = new MapSqlParameterSource("from", Date.valueOf(from)).addValue("to", Date.valueOf(to))
                .addValue("start", Timestamp.from(rangeStart)).addValue("end", Timestamp.from(rangeEnd)).addValue("now", Timestamp.from(now));
        String sql = "SELECT a.id, t.product, COALESCE(x.imp, 0), COALESCE(x.clk, 0) FROM biz_applications a " + TYPE_JOIN
                + "LEFT JOIN LATERAL (SELECT sum(s.impressions) AS imp, sum(s.clicks) AS clk FROM ad_daily_stats s WHERE s.application_id = a.id "
                + "AND s.day BETWEEN :from AND :to) x ON true "
                + "WHERE (a.status IN ('SCHEDULED', 'TAKEN_DOWN') OR (a.status = 'CANCELED' AND a.approved_at IS NOT NULL)) "
                + "AND a.start_at <= :now AND a.start_at < :end AND a.end_at > :start" + filters(type, advertiser, args);
        return named.query(sql, args, (rs, i) -> new BookingSum(rs.getLong(1), BizProduct.valueOf(rs.getString(2)), rs.getLong(3), rs.getLong(4)));
    }

    private static String filters(BizProduct type, AdAdvertiserService.Ref advertiser, MapSqlParameterSource args) {
        StringBuilder sql = new StringBuilder();
        if (type != null) {
            sql.append(" AND t.product = :type");
            args.addValue("type", type.name());
        }
        if (advertiser != null && advertiser.advertiserId() != null) {
            sql.append(" AND a.advertiser_id = :adv");
            args.addValue("adv", advertiser.advertiserId());
        } else if (advertiser != null && advertiser.businessProfileId() != null) {
            sql.append(" AND a.advertiser_id IS NULL AND a.business_profile_id = :biz");
            args.addValue("biz", advertiser.businessProfileId());
        }
        return sql.toString();
    }
}
