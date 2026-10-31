package com.community.ad.repository.master;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 계정 없는 광고주(V115 {@code ad_advertisers}) + 연락처 평문 열람 기록 — JDBC(신청 건 표와 같은 방식). 목록·검색은 가린 값만 읽고 암호문은 열람 때만 꺼낸다.
 * 사업자 검색은 승인된 사업자 신원(상호·등록번호 끝 4자리)을 같이 본다.
 */
@Repository
public class AdAdvertiserStore {

    /** 광고주 한 줄(가린 값) — 사업자와 연결됐으면 그 상호·등록번호 끝 4자리 */
    public record AdvertiserRow(long id, String name, Long businessProfileId, String companyName, String bizNoLast4, String contactNameMasked,
                                String contactPhoneMasked, String emailMasked, String memo, Instant createdAt) {
    }

    /** 새 광고주 — 암호문과 가린 값을 같이(서비스가 만든다) */
    public record NewAdvertiser(long id, String name, Long businessProfileId, String contactNameEnc, String contactPhoneEnc, String emailEnc, String contactNameMasked,
                                String contactPhoneMasked, String emailMasked, String memo, long createdBy, Instant now) {
    }

    /** 암호문(열람 때만) */
    public record Secret(String contactNameEnc, String contactPhoneEnc, String emailEnc) {
    }

    /** 광고주와 연결되지 않은 승인 사업자(검색 결과) */
    public record BusinessHit(long profileId, String companyName, String bizNoLast4) {
    }

    private static final String SELECT = "SELECT a.id, a.name, a.business_profile_id, p.company_name, p.biz_no_last4, a.contact_name_masked, a.contact_phone_masked, "
            + "a.email_masked, a.memo, a.created_at FROM ad_advertisers a LEFT JOIN business_profiles p ON p.id = a.business_profile_id ";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public AdAdvertiserStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public void insert(NewAdvertiser a) {
        Timestamp now = Timestamp.from(a.now());
        jdbc.update("INSERT INTO ad_advertisers (id, name, business_profile_id, contact_name_enc, contact_phone_enc, email_enc, contact_name_masked, contact_phone_masked, "
                        + "email_masked, memo, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                a.id(), a.name(), a.businessProfileId(), a.contactNameEnc(), a.contactPhoneEnc(), a.emailEnc(), a.contactNameMasked(), a.contactPhoneMasked(),
                a.emailMasked(), a.memo(), a.createdBy(), now, now);
    }

    public Optional<AdvertiserRow> find(long id) {
        return jdbc.query(SELECT + "WHERE a.id = ?", (rs, i) -> row(rs), id).stream().findFirst();
    }

    public Map<Long, AdvertiserRow> findAll(Collection<Long> ids) {
        Map<Long, AdvertiserRow> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        named.query(SELECT + "WHERE a.id IN (:ids)", new MapSqlParameterSource("ids", ids), rs -> {
            AdvertiserRow row = row(rs);
            out.put(row.id(), row);
        });
        return out;
    }

    /** 사업자 신원에 연결된 광고주 */
    public Optional<AdvertiserRow> byProfile(long profileId) {
        return jdbc.query(SELECT + "WHERE a.business_profile_id = ?", (rs, i) -> row(rs), profileId).stream().findFirst();
    }

    /** 이름이 같은 광고주가 있나(대소문자·앞뒤 공백 무시 — 유일 인덱스와 같은 기준) */
    public boolean nameTaken(String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM ad_advertisers WHERE lower(btrim(name)) = lower(btrim(?)))", Boolean.class, name));
    }

    /** 광고주 검색 — 이름(연결된 사업자면 상호·등록번호 끝 4자리도), 이름 차례, 최대 {@code limit} */
    public List<AdvertiserRow> search(String q, int limit) {
        if (q == null || q.isBlank()) {
            return jdbc.query(SELECT + "ORDER BY a.name, a.id LIMIT ?", (rs, i) -> row(rs), limit);
        }
        return named.query(SELECT + "WHERE a.name ILIKE :like OR p.company_name ILIKE :like OR p.biz_no_last4 = :q ORDER BY a.name, a.id LIMIT :limit",
                new MapSqlParameterSource("like", like(q)).addValue("q", q.strip()).addValue("limit", limit), (rs, i) -> row(rs));
    }

    /** 광고주와 연결되지 않은 승인 사업자 검색 — 상호·등록번호 끝 4자리 */
    public List<BusinessHit> searchBusinesses(String q, int limit) {
        String base = "SELECT p.id, p.company_name, p.biz_no_last4 FROM business_profiles p WHERE p.status = 'APPROVED' "
                + "AND NOT EXISTS (SELECT 1 FROM ad_advertisers a WHERE a.business_profile_id = p.id) ";
        if (q == null || q.isBlank()) {
            return jdbc.query(base + "ORDER BY p.company_name, p.id LIMIT ?", (rs, i) -> new BusinessHit(rs.getLong(1), rs.getString(2), rs.getString(3)), limit);
        }
        return named.query(base + "AND (p.company_name ILIKE :like OR p.biz_no_last4 = :q) ORDER BY p.company_name, p.id LIMIT :limit",
                new MapSqlParameterSource("like", like(q)).addValue("q", q.strip()).addValue("limit", limit),
                (rs, i) -> new BusinessHit(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    /** 승인 사업자 한 곳(수동 등록 — 상호·끝 4자리 스냅샷). 승인 상태가 아니면 비어 있다 */
    public Optional<BusinessHit> approvedBusiness(long profileId) {
        return jdbc.query("SELECT id, company_name, biz_no_last4 FROM business_profiles WHERE id = ? AND status = 'APPROVED'",
                (rs, i) -> new BusinessHit(rs.getLong(1), rs.getString(2), rs.getString(3)), profileId).stream().findFirst();
    }

    /** 사업자 신원의 상호·끝 4자리(상태 무관 — 연결 확인용) */
    public Optional<BusinessHit> business(long profileId) {
        return jdbc.query("SELECT id, company_name, biz_no_last4 FROM business_profiles WHERE id = ?",
                (rs, i) -> new BusinessHit(rs.getLong(1), rs.getString(2), rs.getString(3)), profileId).stream().findFirst();
    }

    public Optional<Secret> secret(long id) {
        return jdbc.query("SELECT contact_name_enc, contact_phone_enc, email_enc FROM ad_advertisers WHERE id = ?",
                (rs, i) -> new Secret(rs.getString(1), rs.getString(2), rs.getString(3)), id).stream().findFirst();
    }

    /** 사업자 신원의 이메일 암호문(긴급 내림 메일 — 지운 신원은 비어 있다) */
    public Optional<String> businessEmailEnc(long profileId) {
        return jdbc.query("SELECT email_enc FROM business_profiles WHERE id = ? AND email_enc IS NOT NULL", (rs, i) -> rs.getString(1), profileId).stream().findFirst();
    }

    /** 평문 열람 기록 */
    public void insertView(long id, long advertiserId, long operatorId, String reason, String ip, Instant now) {
        jdbc.update("INSERT INTO ad_advertiser_views (id, advertiser_id, operator_id, reason, ip, created_at) VALUES (?, ?, ?, ?, ?, ?)", id, advertiserId, operatorId, reason,
                ip, Timestamp.from(now));
    }

    public int countViews(long advertiserId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_views WHERE advertiser_id = ?", Integer.class, advertiserId);
        return n == null ? 0 : n;
    }

    private static String like(String q) {
        return "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static AdvertiserRow row(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new AdvertiserRow(rs.getLong("id"), rs.getString("name"), (Long) rs.getObject("business_profile_id", Long.class), rs.getString("company_name"),
                rs.getString("biz_no_last4"), rs.getString("contact_name_masked"), rs.getString("contact_phone_masked"), rs.getString("email_masked"), rs.getString("memo"),
                created == null ? null : created.toInstant());
    }
}
