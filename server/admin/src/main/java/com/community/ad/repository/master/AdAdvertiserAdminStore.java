package com.community.ad.repository.master;

import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 광고주 관리 읽기(admin-web {@code /ad-advertisers}, docs/features/ad-advertiser-contract.md) — 광고주 줄 · 목록에 나오는 사업자(규칙 2) · 신청 건 가벼운 줄(광고주 키 · 건 수 ·
 * 돈 · 최근 광고일 재료). 쓰기는 shared {@code AdAdvertiserStore}.
 */
@Repository
public class AdAdvertiserAdminStore {

    /**
     * 신청 건 가벼운 줄 — 광고주 키 재료(건의 사업자 신원 · 건 광고주 줄 · 그 광고주 줄의 사업자 신원)와 결제·환불.
     *
     * @param paid     결제액 — 신청 건 상세 {@code refundSummary.paid} 와 같은 원천({@code BizRefundSummary.paidOf}: 온라인 = 결제 완료면 승인 합계, 수동 = 입금 확인 금액), 결제 전 null
     * @param refunded 승인된 환불 합({@code BizRefundSummary.of} 의 {@code refunded} — 결제 전이면 0)
     */
    public record AppLite(long id, Long businessProfileId, Long advertiserId, Long advertiserProfileId, String status, Instant startAt, Instant endAt,
                          String paymentMethod, Long paid, long refunded) {

        /** 광고주 키(규칙 1) — 건의 사업자 신원 → 건 광고주 줄의 사업자 신원 → 직접 등록 광고주 · 없으면 null */
        public String key() {
            Long profile = businessProfileId != null ? businessProfileId : advertiserProfileId;
            if (profile != null) {
                return "biz-" + profile;
            }
            return advertiserId == null ? null : "adv-" + advertiserId;
        }
    }

    /** 목록에 나오는 사업자 신원(규칙 2) — 상호·등록번호 끝 4자리·신원 상태·광고 서비스 줄 상태(없으면 OFF)·연락처 암호문(가린 값을 만들 때만 푼다) */
    public record BusinessRow(long id, String companyName, String bizNoLast4, String status, String adService, Instant createdAt, String ownerNameEnc, String phoneEnc,
                              String emailEnc) {
    }

    /** 결제 · 환불 — {@code BizRefundSummary.paidOf} 와 같은 판정(SQL). 결제 전 건의 환불은 세지 않는다 */
    private static final String APP_SELECT = "SELECT a.id, a.business_profile_id, a.advertiser_id, adv.business_profile_id AS adv_profile, a.status, a.start_at, a.end_at, "
            + "a.payment_method, CASE WHEN a.payment_method = 'ONLINE' AND a.paid_at IS NOT NULL THEN a.approved_total "
            + "WHEN a.payment_method = 'MANUAL' THEN d.amount END AS paid, "
            + "COALESCE((SELECT sum(r.approved_amount) FROM biz_refunds r WHERE r.application_id = a.id AND r.status = 'APPROVED' AND r.approved_amount IS NOT NULL), 0) "
            + "AS refunded FROM biz_applications a LEFT JOIN ad_advertisers adv ON adv.id = a.advertiser_id LEFT JOIN biz_deposits d ON d.application_id = a.id ";

    /** 목록에 나오는 사업자 — 광고주 줄이 붙었거나 · 신청 건(종류 무관)이 있거나 · AD 서비스 줄이 있는 신원 */
    private static final String LISTED = "(EXISTS (SELECT 1 FROM ad_advertisers x WHERE x.business_profile_id = p.id) "
            + "OR EXISTS (SELECT 1 FROM biz_applications b WHERE b.business_profile_id = p.id) OR s.id IS NOT NULL)";

    private static final String BUSINESS_SELECT = "SELECT p.id, p.company_name, p.biz_no_last4, p.status, COALESCE(s.status, 'OFF') AS ad_service, p.created_at, "
            + "CASE WHEN p.erased_at IS NULL THEN p.owner_name_enc END AS owner_enc, CASE WHEN p.erased_at IS NULL THEN p.phone_enc END AS phone_enc, "
            + "CASE WHEN p.erased_at IS NULL THEN p.email_enc END AS email_enc "
            + "FROM business_profiles p LEFT JOIN business_services s ON s.profile_id = p.id AND s.service = 'AD' ";

    private static final String ADVERTISER_SELECT = "SELECT a.id, a.name, a.business_profile_id, p.company_name, p.biz_no_last4, a.contact_name_masked, "
            + "a.contact_phone_masked, a.email_masked, a.memo, a.created_at, a.archived_at, a.contact_purged_at FROM ad_advertisers a "
            + "LEFT JOIN business_profiles p ON p.id = a.business_profile_id ";

    private final NamedParameterJdbcTemplate named;

    public AdAdvertiserAdminStore(DataSource dataSource) {
        this.named = new NamedParameterJdbcTemplate(dataSource);
    }

    /** 광고주 키가 있는 모든 신청 건(목록) */
    public List<AppLite> apps() {
        return named.query(APP_SELECT + "WHERE a.business_profile_id IS NOT NULL OR a.advertiser_id IS NOT NULL", new MapSqlParameterSource(), (rs, i) -> app(rs));
    }

    /** 사업자 키의 모든 건 — 건의 신원이 그 사업자이거나 건 광고주 줄이 그 사업자에 붙었다 */
    public List<AppLite> businessApps(long profileId) {
        return named.query(APP_SELECT + "WHERE a.business_profile_id = :p OR (a.business_profile_id IS NULL AND adv.business_profile_id = :p)", new MapSqlParameterSource("p", profileId), (rs, i) -> app(rs));
    }

    /** 직접 등록 광고주 키의 모든 건 */
    public List<AppLite> advertiserApps(long advertiserId) {
        return named.query(APP_SELECT + "WHERE a.advertiser_id = :id AND a.business_profile_id IS NULL AND adv.business_profile_id IS NULL",
                new MapSqlParameterSource("id", advertiserId), (rs, i) -> app(rs));
    }

    /** 모든 광고주 줄(직접 등록 + 사업자에 붙은 것) */
    public List<AdvertiserRow> advertisers() {
        return named.query(ADVERTISER_SELECT + "ORDER BY a.id", new MapSqlParameterSource(), (rs, i) -> advertiser(rs));
    }

    /** 목록에 나오는 사업자 전부 */
    public List<BusinessRow> businesses() {
        return named.query(BUSINESS_SELECT + "WHERE " + LISTED + " ORDER BY p.id", new MapSqlParameterSource(), (rs, i) -> business(rs));
    }

    /** 목록에 나오는 사업자 한 곳 — 아니면 비어 있다(상세 404) */
    public Optional<BusinessRow> business(long profileId) {
        return named.query(BUSINESS_SELECT + "WHERE p.id = :id AND " + LISTED, new MapSqlParameterSource("id", profileId), (rs, i) -> business(rs)).stream().findFirst();
    }

    private static AppLite app(ResultSet rs) throws SQLException {
        Long paid = (Long) rs.getObject("paid", Long.class);
        return new AppLite(rs.getLong("id"), (Long) rs.getObject("business_profile_id", Long.class), (Long) rs.getObject("advertiser_id", Long.class),
                (Long) rs.getObject("adv_profile", Long.class), rs.getString("status"), instant(rs.getTimestamp("start_at")), instant(rs.getTimestamp("end_at")),
                rs.getString("payment_method"), paid, paid == null ? 0 : rs.getLong("refunded"));
    }

    private static BusinessRow business(ResultSet rs) throws SQLException {
        return new BusinessRow(rs.getLong("id"), rs.getString("company_name"), rs.getString("biz_no_last4"), rs.getString("status"), rs.getString("ad_service"),
                instant(rs.getTimestamp("created_at")), rs.getString("owner_enc"), rs.getString("phone_enc"), rs.getString("email_enc"));
    }

    private static AdvertiserRow advertiser(ResultSet rs) throws SQLException {
        return new AdvertiserRow(rs.getLong("id"), rs.getString("name"), (Long) rs.getObject("business_profile_id", Long.class), rs.getString("company_name"),
                rs.getString("biz_no_last4"), rs.getString("contact_name_masked"), rs.getString("contact_phone_masked"), rs.getString("email_masked"), rs.getString("memo"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("archived_at")), instant(rs.getTimestamp("contact_purged_at")));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
