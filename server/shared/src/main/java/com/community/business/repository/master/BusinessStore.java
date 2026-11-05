package com.community.business.repository.master;

import com.community.business.entity.NtsBizStatus;
import com.community.docintake.entity.DocFileChannel;
import com.community.business.entity.NtsValidity;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
 * 사업자 신원 곁의 표(JDBC) — 등록증 사본(내용이 커서 엔티티로 읽지 않는다) · 국세청 조회 스냅샷 · 열람 기록 · 처리 기록(V111).
 * 줄은 고치지 않고 쌓는다(등록증의 붙이기·파기만 고친다).
 */
@Repository
public class BusinessStore {

    // 처리 기록 행위(ck_bh_action)
    public static final String APPLIED = "APPLIED";
    public static final String UPDATED = "UPDATED";
    public static final String NTS_CHECKED = "NTS_CHECKED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String UNSUSPENDED = "UNSUSPENDED";
    public static final String REVEALED = "REVEALED";
    public static final String CERT_VIEWED = "CERT_VIEWED";
    public static final String WITHDRAWN = "WITHDRAWN";
    /** 서비스만 정지·해제(V130 — 처리 기록에 서비스가 함께 남는다) */
    public static final String SERVICE_SUSPENDED = "SERVICE_SUSPENDED";
    public static final String SERVICE_RESUMED = "SERVICE_RESUMED";
    /** 등록증 종이 접수 기록(V140 — biz-doc-intake-contract): 접수 · 처리 바꾸기 · 폐기 · 잘못 기록 */
    public static final String PAPER_RECEIVED = "PAPER_RECEIVED";
    public static final String PAPER_HANDLING = "PAPER_HANDLING";
    public static final String PAPER_DISPOSED = "PAPER_DISPOSED";
    public static final String PAPER_VOIDED = "PAPER_VOIDED";
    /** 운영자가 등록증 사본을 올렸다(전자 팩스·스캔 — V140) */
    public static final String CERT_UPLOADED = "CERT_UPLOADED";

    // 기록한 사람 종류(ck_bh_actor)
    public static final String MEMBER = "MEMBER";
    public static final String OPERATOR = "OPERATOR";
    public static final String SYSTEM = "SYSTEM";

    /** 등록증 사본 한 장의 정보(내용 없음) */
    public record DocumentRow(long id, long userId, Long profileId, String fileName, String contentType, int sizeBytes, Instant createdAt, Instant attachedAt,
                              Instant erasedAt) {
    }

    /** 국세청 조회 한 줄 — 상태 + 진위(V125, 그 전 줄은 UNCHECKED) */
    public record NtsRow(long id, long profileId, NtsBizStatus status, String taxType, LocalDate closedOn, String message, Instant checkedAt, Long checkedBy,
                         NtsValidity validity) {
    }

    /** 신원에 붙은 등록증 사본 한 장(내용 없음) — 들어온 길·올린 운영자(회원이 올렸으면 null, V140) */
    public record ProfileFileRow(long id, String fileName, String contentType, int sizeBytes, Instant createdAt, DocFileChannel channel, Long uploadedBy,
                                 String uploadedByName) {
    }

    /** 처리 기록 한 줄 — 이름은 운영자 이름 또는 회원 닉네임(지워졌으면 null) */
    public record HistoryRow(Instant at, String action, String actorType, Long actorId, String actorName, String note, String service) {
    }

    /** 마지막 등록증 열람 */
    public record LastView(String operatorName, Instant at) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public BusinessStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    // ── 등록증 사본 ─────────────────────────────────────────────────────────

    public void insertDocument(long id, long userId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256, Instant now) {
        jdbc.update("INSERT INTO business_documents (id, user_id, kind, file_name, content_type, size_bytes, content, sha256, created_at) "
                + "VALUES (?, ?, 'CERTIFICATE', ?, ?, ?, ?, ?, ?)", id, userId, fileName, contentType, sizeBytes, encrypted, sha256, Timestamp.from(now));
    }

    public Optional<DocumentRow> document(long id) {
        return jdbc.query("SELECT id, user_id, profile_id, file_name, content_type, size_bytes, created_at, attached_at, erased_at FROM business_documents WHERE id = ?",
                (rs, i) -> new DocumentRow(rs.getLong("id"), rs.getLong("user_id"), (Long) rs.getObject("profile_id", Long.class), rs.getString("file_name"),
                        rs.getString("content_type"), rs.getInt("size_bytes"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("attached_at")),
                        instant(rs.getTimestamp("erased_at"))), id).stream().findFirst();
    }

    /** 신청·수정에 붙인다(한 번 붙은 파일은 다른 신원에 붙지 않는다) */
    public boolean attachDocument(long id, long profileId, Instant now) {
        return jdbc.update("UPDATE business_documents SET profile_id = ?, attached_at = coalesce(attached_at, ?) WHERE id = ? AND erased_at IS NULL "
                + "AND (profile_id IS NULL OR profile_id = ?)", profileId, Timestamp.from(now), id, profileId) == 1;
    }

    /**
     * 운영자가 올린 등록증 사본(V140 — 전자 팩스·스캔본) — 주인은 그 신원의 회원, 올리면서 바로 그 신원에 붙는다. 지금 등록증으로 바꾸는 것은 부르는 쪽(신원 엔티티)
     */
    public void insertOperatorDocument(long id, long userId, long profileId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256,
                                       DocFileChannel channel, long operatorId, Instant now) {
        jdbc.update("INSERT INTO business_documents (id, user_id, profile_id, kind, file_name, content_type, size_bytes, content, sha256, channel, uploaded_by, created_at, "
                        + "attached_at) VALUES (?, ?, ?, 'CERTIFICATE', ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, userId, profileId, fileName, contentType, sizeBytes, encrypted, sha256,
                channel.name(), operatorId, Timestamp.from(now), Timestamp.from(now));
    }

    /** 신원에 붙은 등록증 사본 중 파기되지 않은 것(올린 순) */
    public List<ProfileFileRow> profileDocuments(long profileId) {
        return jdbc.query("SELECT d.id, d.file_name, d.content_type, d.size_bytes, d.created_at, d.channel, d.uploaded_by, o.operator_name FROM business_documents d "
                        + "LEFT JOIN operators o ON o.id = d.uploaded_by WHERE d.profile_id = ? AND d.erased_at IS NULL ORDER BY d.created_at, d.id",
                (rs, i) -> new ProfileFileRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), instant(rs.getTimestamp(5)),
                        DocFileChannel.valueOf(rs.getString(6)), (Long) rs.getObject(7, Long.class), rs.getString(8)), profileId);
    }

    /** 그 시각 뒤 회원이 올린 사본 수(하루 한도 — 운영자가 올린 줄은 세지 않는다, V140) */
    public int uploadsSince(long userId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM business_documents WHERE user_id = ? AND uploaded_by IS NULL AND created_at >= ?", Integer.class, userId,
                Timestamp.from(since));
        return n == null ? 0 : n;
    }

    /** 암호문 내용 — 파기됐으면 비어 있다 */
    public Optional<byte[]> documentContent(long id) {
        return jdbc.query("SELECT content FROM business_documents WHERE id = ? AND erased_at IS NULL", (rs, i) -> rs.getBytes(1), id).stream().findFirst();
    }

    // ── 국세청 조회 ─────────────────────────────────────────────────────────

    public void insertNtsCheck(long id, long profileId, NtsBizStatus status, NtsValidity validity, String taxType, LocalDate closedOn, String message, Instant checkedAt,
                               Long checkedBy) {
        jdbc.update("INSERT INTO business_nts_checks (id, profile_id, status, validity, tax_type, closed_on, message, checked_at, checked_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, profileId, status.name(), validity.name(), taxType, closedOn == null ? null : Date.valueOf(closedOn), message,
                Timestamp.from(checkedAt), checkedBy);
    }

    public Optional<NtsRow> latestNtsCheck(long profileId) {
        return jdbc.query("SELECT * FROM business_nts_checks WHERE profile_id = ? ORDER BY checked_at DESC, id DESC LIMIT 1", (rs, i) -> nts(rs), profileId).stream()
                .findFirst();
    }

    /** 신원마다 가장 최근 조회(목록 표) */
    public Map<Long, NtsRow> latestNtsChecks(Collection<Long> profileIds) {
        Map<Long, NtsRow> out = new HashMap<>();
        if (profileIds.isEmpty()) {
            return out;
        }
        named.query("SELECT DISTINCT ON (profile_id) * FROM business_nts_checks WHERE profile_id IN (:ids) ORDER BY profile_id, checked_at DESC, id DESC",
                new MapSqlParameterSource("ids", profileIds), rs -> {
                    NtsRow row = nts(rs);
                    out.put(row.profileId(), row);
                });
        return out;
    }

    // ── 열람 기록 ───────────────────────────────────────────────────────────

    public void insertView(long id, long profileId, String kind, long operatorId, String reason, String ip, Instant now) {
        jdbc.update("INSERT INTO business_sensitive_views (id, profile_id, kind, operator_id, reason, ip, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id, profileId, kind,
                operatorId, reason, ip, Timestamp.from(now));
    }

    public Optional<LastView> lastView(long profileId, String kind) {
        return jdbc.query("SELECT o.operator_name, v.created_at FROM business_sensitive_views v LEFT JOIN operators o ON o.id = v.operator_id "
                        + "WHERE v.profile_id = ? AND v.kind = ? ORDER BY v.created_at DESC LIMIT 1",
                (rs, i) -> new LastView(rs.getString(1), instant(rs.getTimestamp(2))), profileId, kind).stream().findFirst();
    }

    // ── 처리 기록 ───────────────────────────────────────────────────────────

    public void insertHistory(long id, long profileId, String action, String actorType, Long actorId, String note, Instant now) {
        insertHistory(id, profileId, action, actorType, actorId, note, null, now);
    }

    /** 서비스 하나에 대한 처리 기록(서비스만 정지·해제) */
    public void insertHistory(long id, long profileId, String action, String actorType, Long actorId, String note, String service, Instant now) {
        jdbc.update("INSERT INTO business_history (id, profile_id, action, actor_type, actor_id, note, service, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, profileId,
                action, actorType, actorId, note == null ? null : cut(note, 300), service, Timestamp.from(now));
    }

    public List<HistoryRow> history(long profileId) {
        return jdbc.query("SELECT h.created_at, h.action, h.actor_type, h.actor_id, CASE WHEN h.actor_type = 'OPERATOR' THEN o.operator_name "
                        + "WHEN h.actor_type = 'MEMBER' THEN p.nickname END AS actor_name, h.note, h.service FROM business_history h "
                        + "LEFT JOIN operators o ON h.actor_type = 'OPERATOR' AND o.id = h.actor_id "
                        + "LEFT JOIN profiles p ON h.actor_type = 'MEMBER' AND p.user_id = h.actor_id "
                        + "WHERE h.profile_id = ? ORDER BY h.created_at DESC, h.id DESC LIMIT 100",
                (rs, i) -> new HistoryRow(instant(rs.getTimestamp(1)), rs.getString(2), rs.getString(3), (Long) rs.getObject(4, Long.class), rs.getString(5),
                        rs.getString(6), rs.getString(7)), profileId);
    }

    /** 그 시각 뒤 신청(재신청) 수 — 반려 뒤 재신청 하루 3회 */
    public int appliedSince(long profileId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM business_history WHERE profile_id = ? AND action = 'APPLIED' AND created_at >= ?", Integer.class, profileId,
                Timestamp.from(since));
        return n == null ? 0 : n;
    }

    // ── 삭제(business-contract 「사업자 삭제」) ────────────────────────────────

    /**
     * 그 신원에 광고·핫딜 신청 건이 한 줄이라도 있는가(상태 무관 — 취소·반려·완료 포함). 결제 주문(BIZ_APPLICATION)·입금·환불·처리 기록은 모두 신청 건에 매달려 있다
     * — 신청 건이 거래의 원천이다. 있으면 지우지 않는다(거래 기록 5년 보관).
     */
    public boolean hasDealings(long profileId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM biz_applications WHERE business_profile_id = ?)", Boolean.class, profileId);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 신원 줄과 딸린 사업자 표의 줄을 모두 지운다(외래키 차례) — 신원이 가리키는 등록증 끊기 → 등록증 사본(암호문) · 국세청 스냅샷 · 서비스 줄 · 처리 기록 · 열람 기록 →
     * 연결된 광고주의 사업자 연결 끊기(광고주는 계정 없는 광고주로 남는다) → 신원 줄. 회원 계정·회원 알림·회원이 아직 붙이지 않은 사본은 건드리지 않는다.
     * 호출하는 쪽이 신원 줄을 잠그고 같은 트랜잭션에서 부른다.
     *
     * @return 지운 신원 줄 수(0 이면 그 사이 없어졌다)
     */
    public int deleteProfile(long profileId) {
        jdbc.update("UPDATE business_profiles SET certificate_id = NULL WHERE id = ?", profileId);
        // 등록증 종이 접수 기록(V140) — 등록증 사본보다 먼저(스캔본 외래키)
        jdbc.update("DELETE FROM biz_doc_intakes WHERE doc = 'CERTIFICATE' AND profile_id = ?", profileId);
        jdbc.update("DELETE FROM business_documents WHERE profile_id = ?", profileId);
        jdbc.update("DELETE FROM business_nts_checks WHERE profile_id = ?", profileId);
        jdbc.update("DELETE FROM business_services WHERE profile_id = ?", profileId);
        jdbc.update("DELETE FROM business_history WHERE profile_id = ?", profileId);
        jdbc.update("DELETE FROM business_sensitive_views WHERE profile_id = ?", profileId);
        // 광고주 관리의 사업자 광고주 연락처 열람 기록(V131 — 키 = 신원)
        jdbc.update("DELETE FROM ad_advertiser_views WHERE business_profile_id = ?", profileId);
        jdbc.update("UPDATE ad_advertisers SET business_profile_id = NULL WHERE business_profile_id = ?", profileId);
        return jdbc.update("DELETE FROM business_profiles WHERE id = ?", profileId);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private static NtsRow nts(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date closed = rs.getDate("closed_on");
        return new NtsRow(rs.getLong("id"), rs.getLong("profile_id"), NtsBizStatus.valueOf(rs.getString("status")), rs.getString("tax_type"),
                closed == null ? null : closed.toLocalDate(), rs.getString("message"), instant(rs.getTimestamp("checked_at")),
                (Long) rs.getObject("checked_by", Long.class), NtsValidity.valueOf(rs.getString("validity")));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
