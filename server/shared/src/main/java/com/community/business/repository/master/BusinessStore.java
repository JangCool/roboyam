package com.community.business.repository.master;

import com.community.business.entity.NtsBizStatus;
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

    // 기록한 사람 종류(ck_bh_actor)
    public static final String MEMBER = "MEMBER";
    public static final String OPERATOR = "OPERATOR";
    public static final String SYSTEM = "SYSTEM";

    /** 등록증 사본 한 장의 정보(내용 없음) */
    public record DocumentRow(long id, long userId, Long profileId, String fileName, String contentType, int sizeBytes, Instant createdAt, Instant attachedAt,
                              Instant erasedAt) {
    }

    /** 국세청 조회 한 줄 */
    public record NtsRow(long id, long profileId, NtsBizStatus status, String taxType, LocalDate closedOn, String message, Instant checkedAt, Long checkedBy) {
    }

    /** 처리 기록 한 줄 — 이름은 운영자 이름 또는 회원 닉네임(지워졌으면 null) */
    public record HistoryRow(Instant at, String action, String actorType, Long actorId, String actorName, String note) {
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

    /** 그 시각 뒤 회원이 올린 사본 수(하루 한도) */
    public int uploadsSince(long userId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM business_documents WHERE user_id = ? AND created_at >= ?", Integer.class, userId, Timestamp.from(since));
        return n == null ? 0 : n;
    }

    /** 암호문 내용 — 파기됐으면 비어 있다 */
    public Optional<byte[]> documentContent(long id) {
        return jdbc.query("SELECT content FROM business_documents WHERE id = ? AND erased_at IS NULL", (rs, i) -> rs.getBytes(1), id).stream().findFirst();
    }

    // ── 국세청 조회 ─────────────────────────────────────────────────────────

    public void insertNtsCheck(long id, long profileId, NtsBizStatus status, String taxType, LocalDate closedOn, String message, Instant checkedAt, Long checkedBy) {
        jdbc.update("INSERT INTO business_nts_checks (id, profile_id, status, tax_type, closed_on, message, checked_at, checked_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, profileId, status.name(), taxType, closedOn == null ? null : Date.valueOf(closedOn), message, Timestamp.from(checkedAt), checkedBy);
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
        jdbc.update("INSERT INTO business_history (id, profile_id, action, actor_type, actor_id, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id, profileId, action,
                actorType, actorId, note == null ? null : cut(note, 300), Timestamp.from(now));
    }

    public List<HistoryRow> history(long profileId) {
        return jdbc.query("SELECT h.created_at, h.action, h.actor_type, h.actor_id, CASE WHEN h.actor_type = 'OPERATOR' THEN o.operator_name "
                        + "WHEN h.actor_type = 'MEMBER' THEN p.nickname END AS actor_name, h.note FROM business_history h "
                        + "LEFT JOIN operators o ON h.actor_type = 'OPERATOR' AND o.id = h.actor_id "
                        + "LEFT JOIN profiles p ON h.actor_type = 'MEMBER' AND p.user_id = h.actor_id "
                        + "WHERE h.profile_id = ? ORDER BY h.created_at DESC, h.id DESC LIMIT 100",
                (rs, i) -> new HistoryRow(instant(rs.getTimestamp(1)), rs.getString(2), rs.getString(3), (Long) rs.getObject(4, Long.class), rs.getString(5),
                        rs.getString(6)), profileId);
    }

    /** 그 시각 뒤 신청(재신청) 수 — 반려 뒤 재신청 하루 3회 */
    public int appliedSince(long profileId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM business_history WHERE profile_id = ? AND action = 'APPLIED' AND created_at >= ?", Integer.class, profileId,
                Timestamp.from(since));
        return n == null ? 0 : n;
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private static NtsRow nts(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date closed = rs.getDate("closed_on");
        return new NtsRow(rs.getLong("id"), rs.getLong("profile_id"), NtsBizStatus.valueOf(rs.getString("status")), rs.getString("tax_type"),
                closed == null ? null : closed.toLocalDate(), rs.getString("message"), instant(rs.getTimestamp("checked_at")),
                (Long) rs.getObject("checked_by", Long.class));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
