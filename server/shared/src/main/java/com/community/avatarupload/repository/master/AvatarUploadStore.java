package com.community.avatarupload.repository.master;

import com.community.avatarupload.entity.AvatarUploadStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 아바타 올리기 신청({@code avatar_upload_requests}, V157) 읽기·쓰기 — 회원 화면(api)·운영자 심사(admin)·작성자 표시가 같이 쓴다.
 *
 * <p>JDBC 인 이유: 변경권 예약·사용·반환({@code UserItemStore})·프로필 아바타 바꾸기와 <b>한 트랜잭션</b>이고(포인트 상점 저장소와 같은 판단), 커머스 규칙 시험이 스프링 문맥 없이 조립한다.
 * 상태는 정해진 상태에서만 바꾼다(조건부 UPDATE — 바꾼 줄 수로 판정한다).
 */
@Repository
public class AvatarUploadStore {

    /** 신청 한 줄 */
    public record Row(long id, long userId, AvatarUploadStatus status, String imageKey, String stillKey, String contentType, int bytes, int width, int height,
                      int frames, String rejectReason, String revokeReason, Long reviewedBy, Instant reviewedAt, Long revokedBy, Instant revokedAt, Instant endedAt,
                      Instant createdAt) {

        /** 움직이는 GIF(장면이 둘 이상) — 첫 장면 정지 그림이 함께 있다 */
        public boolean animated() {
            return frames > 1;
        }

        /** 파일이 아직 있다 */
        public boolean hasFiles() {
            return imageKey != null;
        }
    }

    /** 관리자 목록 한 줄 — 신청 + 회원 지금 닉네임·등급·고른 프로필 이미지 + 심사한 운영자 이름 + 움직이는 닉네임 이미지를 지금 쓰는가 */
    public record AdminRow(Row request, String nickname, String gradeKey, String gradeLabel, String profileImage, String reviewerName, boolean nicknameMotion) {
    }

    private static final String COLUMNS = "r.id, r.user_id, r.status, r.image_key, r.still_key, r.content_type, r.bytes, r.width, r.height, r.frames, r.reject_reason, "
            + "r.revoke_reason, r.reviewed_by, r.reviewed_at, r.revoked_by, r.revoked_at, r.ended_at, r.created_at";
    private static final String ADMIN_COLUMNS = COLUMNS + ", p.nickname, p.grade_key, g.label, p.profile_image, o.operator_name, EXISTS (SELECT 1 FROM nickname_image_requests n "
            + "WHERE n.user_id = r.user_id AND n.status = 'APPROVED' AND n.still_key IS NOT NULL)";
    private static final String ADMIN_FROM = " FROM avatar_upload_requests r LEFT JOIN profiles p ON p.user_id = r.user_id LEFT JOIN grades g ON g.grade_key = p.grade_key "
            + "LEFT JOIN operators o ON o.id = r.reviewed_by";

    private final JdbcTemplate jdbc;

    public AvatarUploadStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    public Optional<Row> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM avatar_upload_requests r WHERE r.id = ?", AvatarUploadStore::rowOf, id).stream().findFirst();
    }

    /** 처리하려는 신청을 잠근다(승인·반려·해제·회원 취소가 겹쳐도 한쪽만 이긴다) */
    public Optional<Row> findForUpdate(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM avatar_upload_requests r WHERE r.id = ? FOR UPDATE", AvatarUploadStore::rowOf, id).stream().findFirst();
    }

    /** 신청한 회원(없는 신청이면 빈 값) */
    public Optional<Long> userIdOf(long id) {
        return jdbc.queryForList("SELECT user_id FROM avatar_upload_requests WHERE id = ?", Long.class, id).stream().findFirst();
    }

    /** 회원의 그 상태 신청(대기·승인은 회원당 하나 — 부분 유일 색인) */
    public Optional<Row> byStatus(long userId, AvatarUploadStatus status) {
        return jdbc.query("SELECT " + COLUMNS + " FROM avatar_upload_requests r WHERE r.user_id = ? AND r.status = ? ORDER BY r.id DESC LIMIT 1", AvatarUploadStore::rowOf,
                userId, status.name()).stream().findFirst();
    }

    /** 회원의 그 상태 신청 전부(반려 파일 정리) */
    public List<Row> allByStatus(long userId, AvatarUploadStatus status) {
        return jdbc.query("SELECT " + COLUMNS + " FROM avatar_upload_requests r WHERE r.user_id = ? AND r.status = ?", AvatarUploadStore::rowOf, userId, status.name());
    }

    /** 회원의 가장 최근 신청 */
    public Optional<Row> latest(long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM avatar_upload_requests r WHERE r.user_id = ? ORDER BY r.id DESC LIMIT 1", AvatarUploadStore::rowOf, userId).stream()
                .findFirst();
    }

    /** 여러 회원의 승인된 그림(「내가 올린 아바타」) — 작성자 표시가 목록마다 한 번 */
    public Map<Long, Row> currentOf(Collection<Long> userIds) {
        Map<Long, Row> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Row row : jdbc.query("SELECT " + COLUMNS + " FROM avatar_upload_requests r WHERE r.status = 'APPROVED' AND r.user_id IN (" + marks(userIds.size()) + ")",
                AvatarUploadStore::rowOf, new ArrayList<>(userIds).toArray())) {
            out.put(row.userId(), row);
        }
        return out;
    }

    /** 오늘(한국 시간 0시 뒤) 올린 수 — 하루 한도 */
    public long countSince(long userId, Instant since) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM avatar_upload_requests WHERE user_id = ? AND created_at >= ?", Long.class, userId, ts(since));
        return count == null ? 0 : count;
    }

    // ── 쓰기 ────────────────────────────────────────────────────────────────

    public void insertPending(long id, long userId, String imageKey, String stillKey, String contentType, int bytes, int width, int height, int frames, Instant now) {
        jdbc.update("INSERT INTO avatar_upload_requests (id, user_id, status, image_key, still_key, content_type, bytes, width, height, frames, created_at) "
                + "VALUES (?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?)", id, userId, imageKey, stillKey, contentType, bytes, width, height, frames, ts(now));
    }

    /** 대기 → CANCELED(회원 취소·새 신청이 대체) */
    public boolean cancel(long id, Instant now) {
        return jdbc.update("UPDATE avatar_upload_requests SET status = 'CANCELED', ended_at = ? WHERE id = ? AND status = 'PENDING'", ts(now), id) == 1;
    }

    /** 대기 → APPROVED */
    public boolean approve(long id, long operatorId, Instant now) {
        return jdbc.update("UPDATE avatar_upload_requests SET status = 'APPROVED', reviewed_by = ?, reviewed_at = ? WHERE id = ? AND status = 'PENDING'", operatorId, ts(now),
                id) == 1;
    }

    /** 대기 → REJECTED(사유) */
    public boolean reject(long id, long operatorId, String reason, Instant now) {
        return jdbc.update("UPDATE avatar_upload_requests SET status = 'REJECTED', reject_reason = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ? AND status = 'PENDING'",
                reason, operatorId, ts(now), id) == 1;
    }

    /** 승인 → REVOKED(운영자 해제, 사유) */
    public boolean revoke(long id, long operatorId, String reason, Instant now) {
        return jdbc.update("UPDATE avatar_upload_requests SET status = 'REVOKED', revoke_reason = ?, revoked_by = ?, revoked_at = ? WHERE id = ? AND status = 'APPROVED'",
                reason, operatorId, ts(now), id) == 1;
    }

    /** 승인 → REMOVED(회원이 지움·다른 그림이 승인돼 바뀜) */
    public boolean remove(long id, Instant now) {
        return jdbc.update("UPDATE avatar_upload_requests SET status = 'REMOVED', ended_at = ? WHERE id = ? AND status = 'APPROVED'", ts(now), id) == 1;
    }

    /** 파일을 지웠다(지우는 일은 커밋 뒤 부르는 쪽이 한다) — 키를 비운다 */
    public void dropFiles(long id) {
        jdbc.update("UPDATE avatar_upload_requests SET image_key = NULL, still_key = NULL WHERE id = ?", id);
    }

    // ── 관리자 목록 ─────────────────────────────────────────────────────────

    /**
     * 상태별 한 쪽 — 대기는 오래된 순(먼저 온 것부터), 해제는 해제 시각, 나머지는 심사 시각 최근 순. 「승인」 탭({@code APPROVED})은 승인된 뒤 바뀐·지워진 그림(REMOVED)도 보인다.
     *
     * @param keyword 닉네임 부분 일치(대소문자 무시) — null 이면 조건 없음
     */
    public List<AdminRow> adminPage(AvatarUploadStatus status, String keyword, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String where = adminWhere(status, keyword, args);
        String order = switch (status) {
            case PENDING -> " ORDER BY r.id";
            case REVOKED -> " ORDER BY r.revoked_at DESC, r.id DESC";
            default -> " ORDER BY r.reviewed_at DESC, r.id DESC";
        };
        args.add(size);
        args.add(offset);
        return jdbc.query("SELECT " + ADMIN_COLUMNS + ADMIN_FROM + where + order + " LIMIT ? OFFSET ?", AvatarUploadStore::adminRowOf, args.toArray());
    }

    public long adminCount(AvatarUploadStatus status, String keyword) {
        List<Object> args = new ArrayList<>();
        Long count = jdbc.queryForObject("SELECT count(*)" + ADMIN_FROM + adminWhere(status, keyword, args), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    /** 한 줄(승인·반려·해제 뒤 응답) */
    public Optional<AdminRow> adminRow(long id) {
        return jdbc.query("SELECT " + ADMIN_COLUMNS + ADMIN_FROM + " WHERE r.id = ?", AvatarUploadStore::adminRowOf, id).stream().findFirst();
    }

    private static String adminWhere(AvatarUploadStatus status, String keyword, List<Object> args) {
        StringBuilder where = new StringBuilder(status == AvatarUploadStatus.APPROVED ? " WHERE (r.status = 'APPROVED' OR (r.status = 'REMOVED' AND r.reviewed_at IS NOT NULL))"
                : " WHERE r.status = ?");
        if (status != AvatarUploadStatus.APPROVED) {
            args.add(status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String escaped = keyword.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            where.append(" AND lower(p.nickname) LIKE ? ESCAPE '\\'");
            args.add("%" + escaped + "%");
        }
        return where.toString();
    }

    private static AdminRow adminRowOf(ResultSet rs, int i) throws SQLException {
        return new AdminRow(rowOf(rs, i), rs.getString(19), rs.getString(20), rs.getString(21), rs.getString(22), rs.getString(23), rs.getBoolean(24));
    }

    private static Row rowOf(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getLong(1), rs.getLong(2), AvatarUploadStatus.valueOf(rs.getString(3)), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
                rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getString(11), rs.getString(12), nullableLong(rs, 13), instant(rs, 14), nullableLong(rs, 15), instant(rs, 16),
                instant(rs, 17), instant(rs, 18));
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }

    private static String marks(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }
}
