package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.MemberSanctionStore.instant;
import static com.community.sanction.repository.master.MemberSanctionStore.nullableLong;
import static com.community.sanction.repository.master.MemberSanctionStore.ts;

import com.community.permission.entity.MemberPermission;
import com.community.post.entity.PostEntity;
import com.community.role.entity.MemberRoleIds;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 게시판 스텝 처리 기록({@code board_staff_actions} 의 kind POST · COMMENT — V138) · 하루 사용량({@code board_staff_usage.posts|comments}) · 처리자 판정 읽기·쓰기.
 * 서비스({@code BoardStaffActionService}) · 운영자 글 조치 몸통({@code PostModerationActions} — 정책 7) · 관리자 목록 저장소가 부른다.
 *
 * <p>JDBC 인 이유는 {@link BoardSanctionStore} 와 같다 — 하루 한도의 원자적 증가({@code INSERT … ON CONFLICT … DO UPDATE … WHERE 칸 < 한도} 한 문장), 되돌림의 조건부 UPDATE
 * ({@code … WHERE restored_at IS NULL}), 부분 색인과 같은 조건 글자. 서버 여러 대가 동시에 불러도 메모리 상태 없이 DB 가 막는다.
 */
@Repository
public class BoardStaffActionStore {

    public static final String KIND_POST = "POST";
    public static final String KIND_COMMENT = "COMMENT";
    public static final String ACTION_HIDE = "HIDE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String BY_MANAGER = "MANAGER";
    public static final String BY_OPERATOR = "OPERATOR";

    /** 제재 줄을 뺀 처리 기록(부분 색인 {@code ix_board_staff_actions_*_content} 와 같은 조건) — 별칭 {@code a} */
    public static final String CONTENT = "a.kind <> 'SANCTION'";

    /**
     * 처리 기록 한 줄 — 게시판 · 처리자 닉네임 · 대상 작성자 지금 닉네임 · 되돌린 매니저 닉네임/운영자 이름 · 대상 글·댓글의 지금 상태 칸을 함께.
     * 글은 id 범위 파티션, 댓글은 post_id 해시 파티션이라 키로만 잇는다.
     */
    public static final String SELECT = "SELECT a.id, a.board_id, b.slug, b.name, b.name_en, a.actor_id, ap.nickname, a.actor_role, a.kind, a.action, a.target_user_id, "
            + "tp.nickname, a.post_id, a.comment_id, a.reason_code, a.target_title, a.target_excerpt, a.target_author_name, a.created_at, a.restored_at, "
            + "a.restored_by_kind, a.restored_by_id, CASE a.restored_by_kind WHEN 'MANAGER' THEN rp.nickname WHEN 'OPERATOR' THEN ro.operator_name END, a.restore_reason, "
            + "p.id IS NOT NULL, p.status, p.deleted_at, p.delete_scheduled_at, p.hidden_at, p.hidden_until, p.purged_at, "
            + "c.id IS NOT NULL, c.deleted_at, c.hidden_at, c.hidden_until, c.purged_at "
            + "FROM board_staff_actions a JOIN boards b ON b.id = a.board_id LEFT JOIN profiles ap ON ap.user_id = a.actor_id "
            + "LEFT JOIN profiles tp ON tp.user_id = a.target_user_id "
            + "LEFT JOIN profiles rp ON a.restored_by_kind = 'MANAGER' AND rp.user_id = a.restored_by_id "
            + "LEFT JOIN operators ro ON a.restored_by_kind = 'OPERATOR' AND ro.id = a.restored_by_id "
            + "LEFT JOIN posts p ON p.id = a.post_id LEFT JOIN comments c ON a.comment_id IS NOT NULL AND c.post_id = a.post_id AND c.id = a.comment_id";

    /**
     * 처리 기록 한 줄.
     *
     * @param kind              POST · COMMENT
     * @param action            HIDE · DELETE
     * @param targetNickname    대상 회원의 지금 닉네임(비회원 · 지워진 회원 null — 그때는 {@code targetAuthorName} 스냅숏)
     * @param restoredByKind    MANAGER · OPERATOR(되돌리지 않았으면 null)
     * @param restoredByName    되돌린 매니저 닉네임 · 운영자 이름
     */
    public record ActionRow(long id, long boardId, String boardSlug, String boardName, String boardNameEn, Long actorId, String actorNickname, String actorRole,
                            String kind, String action, Long targetUserId, String targetNickname, long postId, Long commentId, String reasonCode, String targetTitle,
                            String targetExcerpt, String targetAuthorName, Instant createdAt, Instant restoredAt, String restoredByKind, Long restoredById,
                            String restoredByName, String restoreReason, TargetState post, TargetState comment) {

        /** 화면 종류 — POST_HIDE · POST_DELETE · COMMENT_DELETE */
        public String apiKind() {
            return kind + "_" + action;
        }

        public boolean restored() {
            return restoredAt != null;
        }

        /** 대상 작성자 이름 — 회원의 지금 닉네임, 없으면 처리 때 스냅숏 */
        public String authorName() {
            return targetNickname != null ? targetNickname : targetAuthorName;
        }

        /** 대상(댓글 삭제면 댓글, 글 처리면 글)의 지금 상태 — PUBLIC · HIDDEN · DELETED */
        public String targetState(Instant now) {
            return (KIND_COMMENT.equals(kind) ? comment : post).state(now);
        }
    }

    /** 대상 글·댓글의 지금 상태 칸 — 행이 없으면 {@code exists} 거짓(지운 것으로 본다) */
    public record TargetState(boolean exists, String status, Instant deletedAt, Instant deleteScheduledAt, Instant hiddenAt, Instant hiddenUntil, Instant purgedAt) {

        public String state(Instant now) {
            if (!exists || deletedAt != null || purgedAt != null || (status != null && !PostEntity.PUBLISHED.equals(status))
                    || (deleteScheduledAt != null && !deleteScheduledAt.isAfter(now))) {
                return "DELETED";
            }
            return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now)) ? "HIDDEN" : "PUBLIC";
        }
    }

    /**
     * 그 게시판에서 이 회원의 자리 · 계정 상태 · 역할 권한.
     *
     * @param role        MANAGER · STAFF — 그 게시판 매니저·스텝이 아니면 null
     * @param accountOk   계정 정상(ACTIVE · 지워지지 않음) · 이메일 인증
     * @param roleSeeded  역할 표에 그 역할이 있다 — 없으면(관리자 서버가 내장 역할을 시드하기 전) 권한을 제한하지 않는다({@code MemberRoleQueries} 와 같다)
     * @param permissions 그 역할의 회원 권한 코드
     */
    public record StaffAccess(String role, boolean accountOk, boolean roleSeeded, Set<String> permissions) {

        public boolean manager() {
            return "MANAGER".equals(role);
        }

        /** 자리 + 계정 정상 + 그 권한(매니저 전용 권한은 매니저만) */
        public boolean can(MemberPermission permission) {
            if (role == null || !accountOk || (permission.managerOnly() && !manager())) {
                return false;
            }
            return !roleSeeded || permissions.contains(permission.code());
        }
    }

    /** 그 게시판의 하루 한도(게시판 값 ?? 기본값) — 글(숨기기+삭제) · 댓글 삭제 · 제재 */
    public record Limits(int posts, int comments, int sanctions) {
    }

    /** 그 날 쓴 수 — 제재 · 글 · 댓글 */
    public record Usage(int sanctions, int posts, int comments) {
    }

    /** 처리 기록 거르기 — {@code actorId} 가 있으면 그 처리자 것만(스텝), {@code kind} ALL · POST · COMMENT */
    public record PageFilter(long boardId, Long actorId, String kind) {
    }

    private final JdbcTemplate jdbc;

    public BoardStaffActionStore(MemberSanctionStore sanctions) {
        this.jdbc = sanctions.jdbc();
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    // ── 판정 ──────────────────────────────────────────────────────────────

    /** 그 게시판에서 이 회원의 자리 · 계정 · 역할 권한 — 한 문장. 회원이 없으면 자리 없음 */
    public StaffAccess access(long boardId, long userId) {
        String roleOf = "CASE s.role WHEN 'MANAGER' THEN '" + MemberRoleIds.BOARD_MANAGER + "' WHEN 'STAFF' THEN '" + MemberRoleIds.BOARD_STAFF + "' END";
        List<StaffAccess> rows = jdbc.query("SELECT s.role, (u.status = 'ACTIVE' AND u.deleted_at IS NULL AND u.email_verified_at IS NOT NULL), "
                        + "EXISTS (SELECT 1 FROM roles r WHERE r.role_id = " + roleOf + "), "
                        + "ARRAY(SELECT rp.permission_code FROM role_permissions rp WHERE rp.role_id = " + roleOf + ") "
                        + "FROM users u LEFT JOIN board_staff s ON s.user_id = u.id AND s.board_id = ? WHERE u.id = ?",
                (rs, i) -> new StaffAccess(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3), codes(rs.getArray(4))), boardId, userId);
        return rows.isEmpty() ? new StaffAccess(null, false, false, Set.of()) : rows.get(0);
    }

    /** 그 게시판의 매니저·스텝인가(대상 보호) */
    public boolean staffOf(long boardId, long userId) {
        return !jdbc.queryForList("SELECT 1 FROM board_staff WHERE board_id = ? AND user_id = ?", Integer.class, boardId, userId).isEmpty();
    }

    /**
     * 처리 전 판정용 — 글(임시저장 제외)의 게시판. 없으면 빈 값. 엔티티를 미리 읽지 않는 까닭: 같은 영속성 문맥에서 먼저 읽은 글은 뒤의 잠금 조회가 새 값으로 바꾸지 않는다
     * (잠근 뒤의 상태 판정이 낡은 값을 보지 않게 — 글 엔티티는 잠금 조회로만 처음 읽는다).
     */
    public Optional<Long> publishedPostBoard(long postId) {
        return jdbc.queryForList("SELECT board_id FROM posts WHERE id = ? AND status = 'PUBLISHED'", Long.class, postId).stream().findFirst();
    }

    /** 그 글의 댓글인가(파티션 키 post_id) */
    public boolean commentExists(long postId, long commentId) {
        return !jdbc.queryForList("SELECT 1 FROM comments WHERE post_id = ? AND id = ?", Integer.class, postId, commentId).isEmpty();
    }

    /** 회원 닉네임(스냅숏) — 없으면 null */
    public String nickname(long userId) {
        return jdbc.queryForList("SELECT nickname FROM profiles WHERE user_id = ?", String.class, userId).stream().findFirst().orElse(null);
    }

    // ── 하루 한도 ─────────────────────────────────────────────────────────

    public Limits limits(long boardId) {
        return jdbc.queryForObject("SELECT coalesce(b.staff_post_daily_limit, d.staff_post_daily_limit, 10), coalesce(b.staff_comment_daily_limit, d.staff_comment_daily_limit, 20), "
                        + "coalesce(b.staff_sanction_daily_limit, d.staff_sanction_daily_limit, 5) FROM (SELECT 1) one LEFT JOIN boards b ON b.id = ? "
                        + "LEFT JOIN LATERAL (SELECT staff_post_daily_limit, staff_comment_daily_limit, staff_sanction_daily_limit FROM board_defaults ORDER BY id LIMIT 1) d ON true",
                (rs, i) -> new Limits(rs.getInt(1), rs.getInt(2), rs.getInt(3)), boardId);
    }

    public Usage usage(long boardId, long userId, LocalDate date) {
        List<Usage> rows = jdbc.query("SELECT sanctions, posts, comments FROM board_staff_usage WHERE board_id = ? AND user_id = ? AND usage_date = ?",
                (rs, i) -> new Usage(rs.getInt(1), rs.getInt(2), rs.getInt(3)), boardId, userId, date);
        return rows.isEmpty() ? new Usage(0, 0, 0) : rows.get(0);
    }

    /**
     * 한도 안에서 하나 쓴다(원자적 증가 — 서버 여러 대가 동시에 불러도 한도를 넘지 않는다). 쓴 뒤의 사용량, 한도를 다 썼으면 빈 값(행을 바꾸지 않는다). 트랜잭션이 롤백되면 차감도 돌아간다.
     *
     * @param kind {@link #KIND_POST}(칸 posts) · {@link #KIND_COMMENT}(칸 comments)
     */
    public Optional<Integer> consume(String kind, long boardId, long userId, LocalDate date, int limit, Instant now) {
        String column = KIND_POST.equals(kind) ? "posts" : "comments"; // 고정 글자 둘 중 하나
        return jdbc.queryForList("INSERT INTO board_staff_usage (board_id, user_id, usage_date, " + column + ", updated_at) VALUES (?, ?, ?, 1, ?) "
                + "ON CONFLICT (board_id, user_id, usage_date) DO UPDATE SET " + column + " = board_staff_usage." + column + " + 1, updated_at = EXCLUDED.updated_at "
                + "WHERE board_staff_usage." + column + " < ? RETURNING " + column, Integer.class, boardId, userId, date, ts(now), limit).stream().findFirst();
    }

    // ── 처리 기록 쓰기 ─────────────────────────────────────────────────────

    /** 글·댓글 처리 한 줄 — 스냅숏(제목 · 댓글 앞 40자 · 작성자 이름)과 함께 */
    public void insert(long id, long boardId, long actorId, String actorRole, String kind, String action, Long targetUserId, long postId, Long commentId,
                       String reasonCode, String title, String excerpt, String authorName, Instant now) {
        jdbc.update("INSERT INTO board_staff_actions (id, board_id, actor_id, actor_role, kind, action, target_user_id, post_id, comment_id, reason_code, target_title, "
                        + "target_excerpt, target_author_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, boardId, actorId, actorRole, kind, action,
                targetUserId, postId, commentId, reasonCode, title, excerpt, authorName, ts(now));
    }

    /** 이 처리를 되돌림으로(조건부 — 이미 되돌렸으면 바꾸지 않는다). 바꿨으면 true */
    public boolean markRestored(long id, String byKind, long byId, String reason, Instant now) {
        return jdbc.update("UPDATE board_staff_actions SET restored_at = ?, restored_by_kind = ?, restored_by_id = ?, restore_reason = ? "
                + "WHERE id = ? AND kind <> 'SANCTION' AND restored_at IS NULL", ts(now), byKind, byId, reason, id) == 1;
    }

    /**
     * 그 글의 되돌리지 않은 글 처리({@code action} HIDE · DELETE)를 되돌림으로 채운다 — 운영자 게시글 관리·신고 처리로 풀었을 때(정책 7). 부분 색인 {@code ix_board_staff_actions_open_post}.
     *
     * @return 채운 줄 수
     */
    public int restoreOpenForPost(long postId, String action, String byKind, long byId, String reason, Instant now) {
        return jdbc.update("UPDATE board_staff_actions SET restored_at = ?, restored_by_kind = ?, restored_by_id = ?, restore_reason = ? "
                + "WHERE post_id = ? AND action = ? AND restored_at IS NULL AND kind = 'POST'", ts(now), byKind, byId, reason, postId, action);
    }

    // ── 처리 기록 읽기 ─────────────────────────────────────────────────────

    /** 처리 기록 하나(제재 줄은 없다고 본다) */
    public Optional<ActionRow> find(long id) {
        return jdbc.query(SELECT + " WHERE a.id = ? AND " + CONTENT, BoardStaffActionStore::row, id).stream().findFirst();
    }

    public long total(PageFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Long n = jdbc.queryForObject("SELECT count(*) FROM board_staff_actions a" + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    /** 최신 것이 위 — 색인 {@code ix_board_staff_actions_board_content}(매니저) · {@code ix_board_staff_actions_actor_content}(스텝) */
    public List<ActionRow> page(PageFilter filter, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        args.add(size);
        args.add((long) offset);
        return jdbc.query(SELECT + where + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?", BoardStaffActionStore::row, args.toArray());
    }

    private static String where(PageFilter f, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE a.board_id = ? AND ").append(CONTENT);
        args.add(f.boardId());
        if (f.actorId() != null) {
            where.append(" AND a.actor_id = ?");
            args.add(f.actorId());
        }
        if (KIND_POST.equals(f.kind()) || KIND_COMMENT.equals(f.kind())) {
            where.append(" AND a.kind = ?");
            args.add(f.kind());
        }
        return where.toString();
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    public static ActionRow row(ResultSet rs, int i) throws SQLException {
        TargetState post = new TargetState(rs.getBoolean(25), rs.getString(26), instant(rs, 27), instant(rs, 28), instant(rs, 29), instant(rs, 30), instant(rs, 31));
        TargetState comment = new TargetState(rs.getBoolean(32), PostEntity.PUBLISHED, instant(rs, 33), null, instant(rs, 34), instant(rs, 35), instant(rs, 36));
        return new ActionRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), nullableLong(rs, 6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), nullableLong(rs, 11), rs.getString(12), rs.getLong(13), nullableLong(rs, 14), rs.getString(15), rs.getString(16),
                rs.getString(17), rs.getString(18), instant(rs, 19), instant(rs, 20), rs.getString(21), nullableLong(rs, 22), rs.getString(23), rs.getString(24), post,
                comment);
    }

    private static Set<String> codes(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
