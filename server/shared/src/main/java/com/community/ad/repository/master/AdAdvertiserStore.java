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
 * 계정 없는 광고주(V115 {@code ad_advertisers}, 보관·파기 V131) + 연락처 평문 열람 기록 + 처리 기록(V131 {@code ad_advertiser_events}) — JDBC(신청 건 표와 같은 방식).
 * 목록·검색은 가린 값만 읽고 암호문은 열람 때만 꺼낸다. 사업자 검색은 승인된 사업자 신원(상호·등록번호 끝 4자리)을 같이 본다.
 */
@Repository
public class AdAdvertiserStore {

    /** 광고주 한 줄(가린 값) — 사업자와 연결됐으면 그 상호·등록번호 끝 4자리. 보관(V131)·연락처 파기 시각 */
    public record AdvertiserRow(long id, String name, Long businessProfileId, String companyName, String bizNoLast4, String contactNameMasked,
                                String contactPhoneMasked, String emailMasked, String memo, Instant createdAt, Instant archivedAt, Instant contactPurgedAt) {

        /** 광고 담당자 칸이 하나라도 있나(사업자 광고주의 연락처 원천 — ad-advertiser-contract 규칙 8) */
        public boolean hasContact() {
            return contactNameMasked != null || contactPhoneMasked != null || emailMasked != null;
        }
    }

    /** 새 광고주 — 암호문과 가린 값을 같이(서비스가 만든다) */
    public record NewAdvertiser(long id, String name, Long businessProfileId, String contactNameEnc, String contactPhoneEnc, String emailEnc, String contactNameMasked,
                                String contactPhoneMasked, String emailMasked, String memo, long createdBy, Instant now) {
    }

    /** 고치기 — 칸마다 그대로({@code keep*} true)·새 값(암호문 + 가린 값, 지우기면 둘 다 null). 이름·메모는 평문 */
    public record Change(String name, boolean keepContactName, String contactNameEnc, String contactNameMasked, boolean keepPhone, String contactPhoneEnc,
                         String contactPhoneMasked, boolean keepEmail, String emailEnc, String emailMasked, boolean keepMemo, String memo) {
    }

    /** 암호문(열람 때만) */
    public record Secret(String contactNameEnc, String contactPhoneEnc, String emailEnc) {
    }

    /** 사업자 신원 연락처 암호문(대표자·연락처·이메일) — 파기된 신원은 셋 다 null */
    public record BusinessSecret(long profileId, String ownerNameEnc, String phoneEnc, String emailEnc) {
    }

    /** 광고주와 연결되지 않은 승인 사업자(검색 결과) */
    public record BusinessHit(long profileId, String companyName, String bizNoLast4) {
    }

    /** 연락처 평문 열람 기록 한 줄(최근 차례) — 운영자 이름(없어진 운영자는 null) */
    public record ViewRow(Instant at, Long operatorId, String operatorName, String reason) {
    }

    /** 처리 기록 한 줄(최근 차례) — {@code fields} 는 바뀐 칸 이름 */
    public record EventRow(long id, Instant at, String action, Long actorId, String actorName, List<String> fields) {
    }

    private static final String SELECT = "SELECT a.id, a.name, a.business_profile_id, p.company_name, p.biz_no_last4, a.contact_name_masked, a.contact_phone_masked, "
            + "a.email_masked, a.memo, a.created_at, a.archived_at, a.contact_purged_at FROM ad_advertisers a LEFT JOIN business_profiles p ON p.id = a.business_profile_id ";

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

    /** 이름이 같은 다른 광고주가 있나(자기 자신 빼고) */
    public boolean nameTakenByOther(String name, long selfId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM ad_advertisers WHERE lower(btrim(name)) = lower(btrim(?)) AND id <> ?)", Boolean.class,
                name, selfId));
    }

    /** 광고주 검색 — 이름(연결된 사업자면 상호·등록번호 끝 4자리도), 이름 차례, 최대 {@code limit}. 보관한 광고주는 빠진다(ad-advertiser-contract 규칙 6) */
    public List<AdvertiserRow> search(String q, int limit) {
        if (q == null || q.isBlank()) {
            return jdbc.query(SELECT + "WHERE a.archived_at IS NULL ORDER BY a.name, a.id LIMIT ?", (rs, i) -> row(rs), limit);
        }
        return named.query(SELECT + "WHERE a.archived_at IS NULL AND (a.name ILIKE :like OR p.company_name ILIKE :like OR p.biz_no_last4 = :q) "
                        + "ORDER BY a.name, a.id LIMIT :limit",
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

    /** 사업자 신원 연락처 암호문 — 없는 신원은 비어 있다(파기된 신원은 칸이 null) */
    public Optional<BusinessSecret> businessSecret(long profileId) {
        return jdbc.query("SELECT id, owner_name_enc, phone_enc, email_enc, erased_at FROM business_profiles WHERE id = ?",
                (rs, i) -> rs.getTimestamp(5) != null ? new BusinessSecret(rs.getLong(1), null, null, null)
                        : new BusinessSecret(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), profileId).stream().findFirst();
    }

    /** 사업자 신원의 이메일 암호문(긴급 내림 메일 — 지운 신원은 비어 있다) */
    public Optional<String> businessEmailEnc(long profileId) {
        return jdbc.query("SELECT email_enc FROM business_profiles WHERE id = ? AND email_enc IS NOT NULL", (rs, i) -> rs.getString(1), profileId).stream().findFirst();
    }

    /** 평문 열람 기록 — 광고주 줄(직접 등록) */
    public void insertView(long id, long advertiserId, long operatorId, String reason, String ip, Instant now) {
        jdbc.update("INSERT INTO ad_advertiser_views (id, advertiser_id, operator_id, reason, ip, created_at, source) VALUES (?, ?, ?, ?, ?, ?, 'ADVERTISER')", id,
                advertiserId, operatorId, reason, ip, Timestamp.from(now));
    }

    /** 평문 열람 기록 — 사업자 광고주(키 = 사업자 신원), {@code source} ADVERTISER(광고 담당자 칸)·BUSINESS(신원 대표자·연락처·이메일) */
    public void insertBusinessView(long id, long profileId, String source, long operatorId, String reason, String ip, Instant now) {
        jdbc.update("INSERT INTO ad_advertiser_views (id, business_profile_id, operator_id, reason, ip, created_at, source) VALUES (?, ?, ?, ?, ?, ?, ?)", id, profileId,
                operatorId, reason, ip, Timestamp.from(now), source);
    }

    public int countViews(long advertiserId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_views WHERE advertiser_id = ?", Integer.class, advertiserId);
        return n == null ? 0 : n;
    }

    /** 열람 기록 최근 {@code limit} — 광고주 줄(예전 기록 포함)·사업자 신원 중 준 것(둘 다 주면 합친다) */
    public List<ViewRow> views(Long advertiserId, Long profileId, int limit) {
        MapSqlParameterSource args = new MapSqlParameterSource("adv", advertiserId == null ? -1L : advertiserId).addValue("biz", profileId == null ? -1L : profileId)
                .addValue("limit", limit);
        return named.query("SELECT v.created_at, v.operator_id, o.operator_name, v.reason FROM ad_advertiser_views v LEFT JOIN operators o ON o.id = v.operator_id "
                        + "WHERE v.advertiser_id = :adv OR v.business_profile_id = :biz ORDER BY v.created_at DESC, v.id DESC LIMIT :limit", args,
                (rs, i) -> new ViewRow(rs.getTimestamp(1).toInstant(), (Long) rs.getObject(2, Long.class), rs.getString(3), rs.getString(4)));
    }

    // ── 광고주 관리(V131 — 고치기 · 보관 · 삭제 · 처리 기록) ──

    /** 광고주 줄 잠그기(고치기·보관·삭제를 한 줄씩) — 없으면 false */
    public boolean lock(long id) {
        return !jdbc.query("SELECT id FROM ad_advertisers WHERE id = ? FOR UPDATE", (rs, i) -> rs.getLong(1), id).isEmpty();
    }

    /** 사업자 신원 줄 잠그기(사업자 광고주의 광고주 줄을 처음 만들 때 둘이 겹치지 않게) — 없으면 false */
    public boolean lockProfile(long profileId) {
        return !jdbc.query("SELECT id FROM business_profiles WHERE id = ? FOR UPDATE", (rs, i) -> rs.getLong(1), profileId).isEmpty();
    }

    public void update(long id, Change c, Instant now) {
        StringBuilder sql = new StringBuilder("UPDATE ad_advertisers SET updated_at = :now, name = :name");
        MapSqlParameterSource args = new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(now)).addValue("name", c.name());
        if (!c.keepContactName()) {
            sql.append(", contact_name_enc = :cne, contact_name_masked = :cnm");
            args.addValue("cne", c.contactNameEnc()).addValue("cnm", c.contactNameMasked());
        }
        if (!c.keepPhone()) {
            sql.append(", contact_phone_enc = :cpe, contact_phone_masked = :cpm");
            args.addValue("cpe", c.contactPhoneEnc()).addValue("cpm", c.contactPhoneMasked());
        }
        if (!c.keepEmail()) {
            sql.append(", email_enc = :ee, email_masked = :em");
            args.addValue("ee", c.emailEnc()).addValue("em", c.emailMasked());
        }
        if (!c.keepMemo()) {
            sql.append(", memo = :memo");
            args.addValue("memo", c.memo());
        }
        named.update(sql.append(" WHERE id = :id").toString(), args);
    }

    /** 보관(직접 등록만 — CHECK {@code ck_aa_archive_direct}) · {@code at} null 이면 보관 풀기 */
    public void setArchived(long id, Instant at, Long by, Instant now) {
        jdbc.update("UPDATE ad_advertisers SET archived_at = ?, archived_by = ?, updated_at = ? WHERE id = ?", at == null ? null : Timestamp.from(at), at == null ? null : by,
                Timestamp.from(now), id);
    }

    /** 광고주 줄과 딸린 열람 기록·처리 기록을 지운다(신청 건이 없을 때만 부른다 — 남아 있으면 신청 건 외래키가 막는다) */
    public int delete(long id) {
        jdbc.update("DELETE FROM ad_advertiser_views WHERE advertiser_id = ?", id);
        jdbc.update("DELETE FROM ad_advertiser_events WHERE advertiser_id = ?", id);
        return jdbc.update("DELETE FROM ad_advertisers WHERE id = ?", id);
    }

    /**
     * 그 광고주 줄을 가리키는 신청 건 — [0] 전체(상태 무관) · [1] 끝나지 않은 건(대기 {@code SUBMITTED}·{@code AWAITING_PAYMENT}·{@code AWAITING_DEPOSIT} + 진행·예약 =
     * {@code SCHEDULED} 이고 끝 &gt; 지금)
     */
    public long[] bookingCounts(long advertiserId, Instant now) {
        return jdbc.queryForObject("SELECT count(*), count(*) FILTER (WHERE status IN ('SUBMITTED', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT') "
                        + "OR (status = 'SCHEDULED' AND end_at > ?)) FROM biz_applications WHERE advertiser_id = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, Timestamp.from(now), advertiserId);
    }

    public void insertEvent(long id, long advertiserId, String action, Long actorId, List<String> fields, Instant now) {
        jdbc.update("INSERT INTO ad_advertiser_events (id, advertiser_id, action, actor_id, fields, created_at) VALUES (?, ?, ?, ?, ?, ?)", id, advertiserId, action,
                actorId, fields == null || fields.isEmpty() ? null : String.join(",", fields), Timestamp.from(now));
    }

    /** 처리 기록(최근 차례, 최대 {@code limit}) */
    public List<EventRow> events(long advertiserId, int limit) {
        return jdbc.query("SELECT e.id, e.created_at, e.action, e.actor_id, o.operator_name, e.fields FROM ad_advertiser_events e LEFT JOIN operators o ON o.id = e.actor_id "
                        + "WHERE e.advertiser_id = ? ORDER BY e.created_at DESC, e.id DESC LIMIT ?",
                (rs, i) -> new EventRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3), (Long) rs.getObject(4, Long.class), rs.getString(5),
                        rs.getString(6) == null ? List.of() : List.of(rs.getString(6).split(","))), advertiserId, limit);
    }

    private static String like(String q) {
        return "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static AdvertiserRow row(ResultSet rs) throws SQLException {
        return new AdvertiserRow(rs.getLong("id"), rs.getString("name"), (Long) rs.getObject("business_profile_id", Long.class), rs.getString("company_name"),
                rs.getString("biz_no_last4"), rs.getString("contact_name_masked"), rs.getString("contact_phone_masked"), rs.getString("email_masked"), rs.getString("memo"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("archived_at")), instant(rs.getTimestamp("contact_purged_at")));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
