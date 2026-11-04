package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.MemberSanctionStore.instant;
import static com.community.sanction.repository.master.MemberSanctionStore.nullableLong;
import static com.community.sanction.repository.master.MemberSanctionStore.ts;

import com.community.board.dto.BoardWriteRestriction;
import com.community.permission.entity.MemberPermission;
import com.community.role.entity.MemberRoleIds;
import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.entity.SanctionStatus;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 게시판 제재({@code board_sanctions}) · 매니저·스텝 하루 사용량({@code board_staff_usage}) · 처리 기록({@code board_staff_actions}) · 반복 검토({@code board_sanction_reviews}) ·
 * 게시판 제재 이의제기({@code sanction_appeals.board_sanction_id}) 읽기·쓰기 — shared V137. 서비스({@code BoardSanctionService})와 api·관리자 목록 저장소가 부른다.
 *
 * <p>JDBC(native SQL)인 이유는 {@link MemberSanctionStore} 와 같다 — 회원 행 잠금 아래의 조건부 UPDATE 한 문장(해제 · 무효 · 만료), 하루 한도의 원자적 증가
 * ({@code INSERT … ON CONFLICT … DO UPDATE … WHERE sanctions < 한도}), 이의제기·반복 검토의 {@code ON CONFLICT DO NOTHING}, 진행 중 쓰기 제한의 판정을 부분 색인
 * ({@code ux_board_sanctions_running} · {@code ix_board_sanctions_expire})과 같은 조건 글자로 둔다. 판정 시각은 DB 의 {@code now()} 가 아니라 서비스가 넘기는 지금이다.
 *
 * <p>⚠ {@link #endDue} 는 DB 배치 {@code R__batch_40_board_sanction_expire.sql} 규칙 1 과 같은 판정이다(이중 구현) — 한쪽을 고치면 다른 쪽도 고친다.
 */
@Repository
public class BoardSanctionStore {

    /** 진행 중인 쓰기 제한(부분 유일 색인 {@code ux_board_sanctions_running} 과 같은 조건 + 끝이 지나지 않음) — 별칭 {@code s}, 지금 자리 하나 */
    public static final String RUNNING = "s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND s.ends_at > ?";

    /** 게시판 제재 한 줄 — 게시판·회원(닉네임·등급·상태)·처리자 닉네임·해제한 매니저 닉네임/운영자 이름·무효로 한 운영자 이름·이의제기 요약을 함께 */
    public static final String SELECT = "SELECT s.id, s.sanction_no, s.board_id, b.slug, b.name, b.name_en, s.user_id, p.nickname, p.grade_key, u.status, "
            + "s.kind, s.status, s.reason_code, s.reason_text, s.issued_by, ip.nickname, s.issued_role, s.post_id, s.comment_id, s.source_title, "
            + "s.starts_at, s.ends_at, s.acknowledged_at, s.lifted_at, s.lifted_by_type, s.lifted_by_user, lp.nickname, s.lifted_by_operator, lo.operator_name, s.lift_reason, "
            + "s.voided_at, s.void_source, s.voided_by, vo.operator_name, s.void_reason, s.ended_at, s.created_at, a.id, a.appeal_no, a.status "
            + "FROM board_sanctions s JOIN boards b ON b.id = s.board_id JOIN users u ON u.id = s.user_id LEFT JOIN profiles p ON p.user_id = s.user_id "
            + "LEFT JOIN profiles ip ON ip.user_id = s.issued_by LEFT JOIN profiles lp ON lp.user_id = s.lifted_by_user "
            + "LEFT JOIN operators lo ON lo.id = s.lifted_by_operator LEFT JOIN operators vo ON vo.id = s.voided_by "
            + "LEFT JOIN sanction_appeals a ON a.board_sanction_id = s.id";

    /** 반복 검토 한 줄 — 게시판·회원·닫은 운영자 이름·닫으며 부여한 사이트 제재(번호·단계)를 함께 */
    public static final String REVIEW_SELECT = "SELECT r.id, r.review_no, r.board_id, b.slug, b.name, b.name_en, r.user_id, p.nickname, p.grade_key, u.status, "
            + "r.status, r.opened_at, r.opened_count, r.counted_after, r.closed_at, r.closed_by, o.operator_name, r.result, r.memo, r.site_sanction_id, ms.sanction_no, "
            + "ms.kind, r.voided_sanction_ids, r.closed_count, r.closed_board_count "
            + "FROM board_sanction_reviews r JOIN boards b ON b.id = r.board_id JOIN users u ON u.id = r.user_id LEFT JOIN profiles p ON p.user_id = r.user_id "
            + "LEFT JOIN operators o ON o.id = r.closed_by LEFT JOIN member_sanctions ms ON ms.id = r.site_sanction_id";

    /** 반복 검토 기준 기간 — 최근 7일(롤링, 결정 3) */
    public static final java.time.Duration REVIEW_WINDOW = java.time.Duration.ofDays(7);

    /**
     * 게시판 제재 한 줄.
     *
     * @param status        저장된 상태 — 끝이 지났지만 아직 정리되지 않은 쓰기 제한은 {@link #effectiveStatus} 가 ENDED 로 본다
     * @param issuedRole    줄 때의 자리(MANAGER · STAFF)
     * @param liftedByType  MANAGER(그 게시판 매니저 — {@code liftedByUser}) · OPERATOR(운영자 — {@code liftedByOperator})
     * @param voidSource    APPEAL · OPERATOR · REVIEW
     */
    public record BoardSanctionRow(long id, long no, long boardId, String boardSlug, String boardName, String boardNameEn, long userId, String nickname,
                                   String gradeKey, String userStatus, BoardSanctionKind kind, SanctionStatus status, String reasonCode, String reasonText,
                                   Long issuedBy, String issuedByNickname, String issuedRole, Long postId, Long commentId, String sourceTitle, Instant startsAt,
                                   Instant endsAt, Instant acknowledgedAt, Instant liftedAt, String liftedByType, Long liftedByUser, String liftedByUserNickname,
                                   Long liftedByOperator, String liftedByOperatorName, String liftReason, Instant voidedAt, String voidSource, Long voidedBy,
                                   String voidedByName, String voidReason, Instant endedAt, Instant createdAt, Long appealId, Long appealNo, AppealStatus appealStatus) {

        /** {@code now} 의 상태 — 진행 중인데 끝이 지난 쓰기 제한은 ENDED(크론이 1분 안에 그렇게 만든다) */
        public SanctionStatus effectiveStatus(Instant now) {
            return status == SanctionStatus.ACTIVE && due(now) ? SanctionStatus.ENDED : status;
        }

        /** 끝난 시각 — 아직 정리 전이면 끝 시각 */
        public Instant effectiveEndedAt(Instant now) {
            return endedAt == null && status == SanctionStatus.ACTIVE && due(now) ? endsAt : endedAt;
        }

        /** {@code now} 에 진행 중인 쓰기 제한인가 */
        public boolean runningAt(Instant now) {
            return kind.restricts() && effectiveStatus(now) == SanctionStatus.ACTIVE;
        }

        public boolean valid() {
            return status != SanctionStatus.VOIDED;
        }

        private boolean due(Instant now) {
            return endsAt != null && !endsAt.isAfter(now);
        }
    }

    /**
     * 반복 검토 한 줄.
     *
     * @param countedAfter 열 때의 마지막으로 닫힌 검토 closed_at(그 이전 제재는 세지 않는다) — 없으면 null
     */
    public record ReviewRow(long id, long no, long boardId, String boardSlug, String boardName, String boardNameEn, long userId, String nickname, String gradeKey,
                            String userStatus, String status, Instant openedAt, int openedCount, Instant countedAfter, Instant closedAt, Long closedBy,
                            String closedByName, String result, String memo, Long siteSanctionId, Long siteSanctionNo, String siteSanctionKind,
                            List<Long> voidedSanctionIds, Integer closedCount, Integer closedBoardCount) {

        public boolean open() {
            return "OPEN".equals(status);
        }

        /** 이 검토가 세는 제재의 시작 경계(이 시각 <b>뒤</b>에 받은 것) — 열 때 기준 7일 앞과 마지막으로 닫힌 검토 중 늦은 쪽 */
        public Instant windowStart() {
            Instant byWindow = openedAt.minus(REVIEW_WINDOW);
            return countedAfter != null && countedAfter.isAfter(byWindow) ? countedAfter : byWindow;
        }
    }

    /**
     * 매니저·스텝 판정 — 그 게시판에서 맡은 자리와 역할 권한, 계정 상태.
     *
     * @param role       MANAGER · STAFF — 그 게시판 매니저·스텝이 아니면 null
     * @param accountOk  계정 정상(ACTIVE · 지워지지 않음) · 이메일 인증
     * @param canIssue   역할 권한 {@code board.sanction.issue}
     * @param canRestore 역할 권한 {@code board.staff-action.restore}(매니저 전용)
     */
    public record StaffGate(String role, boolean accountOk, boolean canIssue, boolean canRestore) {

        public boolean manager() {
            return "MANAGER".equals(role);
        }

        /** 제재를 줄 수 있다 */
        public boolean issuer() {
            return role != null && accountOk && canIssue;
        }
    }

    /** 제재 대상 회원 — {@code gone} 탈퇴(진행 중 포함)·지워짐 */
    public record TargetRow(long id, String nickname, boolean gone) {
    }

    /** 계기 글 — 게시판·작성자·제목 */
    public record PostSource(long boardId, Long authorId, String title) {
    }

    /** 그 게시판 유효한 제재 수 — 경고 · 쓰기 제한(해제 포함, 무효 제외) */
    public record Counts(long warnings, long restrictions) {
        public long total() {
            return warnings + restrictions;
        }
    }

    /** 다른 게시판 요약 한 줄(반복 검토 상세) */
    public record BoardSummary(long boardId, String slug, String name, String nameEn, long recentCount, long total, Instant activeUntil) {
    }

    private final JdbcTemplate jdbc;

    public BoardSanctionStore(MemberSanctionStore sanctions) {
        this.jdbc = sanctions.jdbc();
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    // ── 매니저·스텝 · 대상 ─────────────────────────────────────────────────

    /**
     * 그 게시판에서 이 회원의 자리·역할 권한 — 한 문장. 역할 표가 아직 없으면(관리자 서버가 내장 역할을 시드하기 전) 권한을 제한하지 않는다({@code MemberRoleQueries} 와 같다).
     * 회원이 없으면 자리 없음.
     */
    public StaffGate gate(long boardId, long userId) {
        String roleOf = "CASE s.role WHEN 'MANAGER' THEN '" + MemberRoleIds.BOARD_MANAGER + "' ELSE '" + MemberRoleIds.BOARD_STAFF + "' END";
        String has = "(EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = " + roleOf + " AND rp.permission_code = ?) "
                + "OR NOT EXISTS (SELECT 1 FROM roles r WHERE r.role_id = " + roleOf + "))";
        List<StaffGate> rows = jdbc.query("SELECT s.role, (u.status = 'ACTIVE' AND u.deleted_at IS NULL AND u.email_verified_at IS NOT NULL), "
                        + "(s.role IS NOT NULL AND " + has + "), (s.role = 'MANAGER' AND " + has + ") "
                        + "FROM users u LEFT JOIN board_staff s ON s.user_id = u.id AND s.board_id = ? WHERE u.id = ?",
                (rs, i) -> new StaffGate(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)),
                MemberPermission.BOARD_SANCTION_ISSUE.code(), MemberPermission.BOARD_STAFF_ACTION_RESTORE.code(), boardId, userId);
        return rows.isEmpty() ? new StaffGate(null, false, false, false) : rows.get(0);
    }

    /** 게시판 종류(GENERAL · DEAL · PROMO · GROUP) — 없으면 빈 값 */
    public Optional<String> boardKind(long boardId) {
        return jdbc.queryForList("SELECT kind FROM boards WHERE id = ?", String.class, boardId).stream().findFirst();
    }

    /** 그 게시판의 매니저·스텝인가(대상 보호) */
    public boolean staffOf(long boardId, long userId) {
        return !jdbc.queryForList("SELECT 1 FROM board_staff WHERE board_id = ? AND user_id = ?", Integer.class, boardId, userId).isEmpty();
    }

    private static final String TARGET = "SELECT u.id, p.nickname, (u.status IN ('WITHDRAWN', 'WITHDRAWING') OR u.deleted_at IS NOT NULL) "
            + "FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE u.id = ?";

    public Optional<TargetRow> target(long userId) {
        return jdbc.query(TARGET, BoardSanctionStore::target, userId).stream().findFirst();
    }

    /** 대상 회원 행을 쓰기 잠금으로 읽는다 — 같은 회원의 부여·해제·무효·이의 인용이 줄을 선다(사이트 제재와 같은 잠금, 차례: 이의제기 행 → 회원 행 → 제재 행) */
    public Optional<TargetRow> lockTarget(long userId) {
        return jdbc.query(TARGET + " FOR UPDATE OF u", BoardSanctionStore::target, userId).stream().findFirst();
    }

    /** 계기 글(지워진 글도 — 제목 스냅숏) */
    public Optional<PostSource> post(long postId) {
        return jdbc.query("SELECT board_id, author_id, title FROM posts WHERE id = ?", (rs, i) -> new PostSource(rs.getLong(1), nullableLong(rs, 2), rs.getString(3)),
                postId).stream().findFirst();
    }

    /** 계기 댓글의 게시판·작성자 — 그 글의 댓글만(파티션 키 post_id) */
    public Optional<PostSource> comment(long postId, long commentId) {
        return jdbc.query("SELECT board_id, author_id, NULL FROM comments WHERE post_id = ? AND id = ?",
                (rs, i) -> new PostSource(rs.getLong(1), nullableLong(rs, 2), null), postId, commentId).stream().findFirst();
    }

    // ── 하루 한도 ─────────────────────────────────────────────────────────

    /** 그 게시판의 매니저·스텝 하루 제재 한도 — 게시판 값 ?? 기본값(board_defaults) */
    public int dailyLimit(long boardId) {
        Integer n = jdbc.queryForObject("SELECT coalesce((SELECT staff_sanction_daily_limit FROM boards WHERE id = ?), "
                + "(SELECT staff_sanction_daily_limit FROM board_defaults ORDER BY id LIMIT 1), 5)", Integer.class, boardId);
        return n == null ? 5 : n;
    }

    /** 그 날 쓴 제재 수 */
    public int used(long boardId, long userId, LocalDate date) {
        List<Integer> rows = jdbc.queryForList("SELECT sanctions FROM board_staff_usage WHERE board_id = ? AND user_id = ? AND usage_date = ?", Integer.class,
                boardId, userId, date);
        return rows.isEmpty() || rows.get(0) == null ? 0 : rows.get(0);
    }

    /**
     * 한도 안에서 하나 쓴다(원자적 증가 — 서버 여러 대가 동시에 불러도 한도를 넘지 않는다). 쓴 뒤의 사용량, 한도를 다 썼으면 빈 값(행을 바꾸지 않는다).
     * 트랜잭션이 롤백되면 차감도 되돌아간다.
     */
    public Optional<Integer> consume(long boardId, long userId, LocalDate date, int limit, Instant now) {
        return jdbc.queryForList("INSERT INTO board_staff_usage (board_id, user_id, usage_date, sanctions, updated_at) VALUES (?, ?, ?, 1, ?) "
                + "ON CONFLICT (board_id, user_id, usage_date) DO UPDATE SET sanctions = board_staff_usage.sanctions + 1, updated_at = EXCLUDED.updated_at "
                + "WHERE board_staff_usage.sanctions < ? RETURNING sanctions", Integer.class, boardId, userId, date, ts(now), limit).stream().findFirst();
    }

    // ── 제재 읽기 ─────────────────────────────────────────────────────────

    public Optional<BoardSanctionRow> find(long id) {
        return jdbc.query(SELECT + " WHERE s.id = ?", BoardSanctionStore::row, id).stream().findFirst();
    }

    /** 여럿(ID 로) */
    public Map<Long, BoardSanctionRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbc.query(SELECT + " WHERE s.id IN (" + marks + ")", BoardSanctionStore::row, ids.toArray()).stream()
                .collect(Collectors.toMap(BoardSanctionRow::id, r -> r));
    }

    /** 그 제재의 회원·게시판(없으면 빈 값) — {@code [userId, boardId]} */
    public Optional<long[]> ownerOf(long id) {
        return jdbc.query("SELECT user_id, board_id FROM board_sanctions WHERE id = ?", (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, id).stream().findFirst();
    }

    /** 그 회원의 게시판 제재 — 최근 것이 위, 최대 {@code limit}(무효 포함, 모든 게시판). 색인 {@code ix_board_sanctions_user} */
    public List<BoardSanctionRow> ofUser(long userId, int limit) {
        return jdbc.query(SELECT + " WHERE s.user_id = ? ORDER BY s.created_at DESC, s.id DESC LIMIT ?", BoardSanctionStore::row, userId, limit);
    }

    /** 그 회원·그 게시판의 제재 — 최근 것이 위, 최대 {@code limit} */
    public List<BoardSanctionRow> ofUserBoard(long userId, long boardId, int limit) {
        return jdbc.query(SELECT + " WHERE s.user_id = ? AND s.board_id = ? ORDER BY s.created_at DESC, s.id DESC LIMIT ?", BoardSanctionStore::row, userId, boardId,
                limit);
    }

    /** 그 회원·그 게시판에서 {@code after} 뒤에(없으면 처음부터) 받은 제재 — 받은 차례(반복 검토가 센 것) */
    public List<BoardSanctionRow> ofUserBoardBetween(long userId, long boardId, Instant after, Instant until) {
        return jdbc.query(SELECT + " WHERE s.user_id = ? AND s.board_id = ? AND s.created_at > ? AND s.created_at <= ? ORDER BY s.created_at, s.id",
                BoardSanctionStore::row, userId, boardId, ts(after), ts(until));
    }

    /** 그 게시판의 유효한 제재 수(권장 단계 · 현황) */
    public Counts counts(long userId, long boardId) {
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE kind = 'WARNING'), count(*) FILTER (WHERE kind <> 'WARNING') FROM board_sanctions "
                + "WHERE user_id = ? AND board_id = ? AND status <> 'VOIDED'", (rs, i) -> new Counts(rs.getLong(1), rs.getLong(2)), userId, boardId);
    }

    /** 그 게시판에서 진행 중인 쓰기 제한(없으면 빈 값) */
    public Optional<BoardWriteRestriction> running(long userId, long boardId, Instant now) {
        return jdbc.query("SELECT s.id, s.ends_at, s.reason_code FROM board_sanctions s WHERE s.user_id = ? AND s.board_id = ? AND " + RUNNING + " LIMIT 1",
                (rs, i) -> new BoardWriteRestriction(rs.getLong(1), instant(rs, 2), rs.getString(3)), userId, boardId, ts(now)).stream().findFirst();
    }

    /** 이 회원의 진행 중인 쓰기 제한 전부(게시판 ID → 제한) — 글·댓글 쓰기 판정, 요청당 색인 질의 하나({@code ux_board_sanctions_running}) */
    public Map<Long, BoardWriteRestriction> runningOfUser(long userId, Instant now) {
        Map<Long, BoardWriteRestriction> out = new java.util.HashMap<>();
        jdbc.query("SELECT s.board_id, s.id, s.ends_at, s.reason_code FROM board_sanctions s WHERE s.user_id = ? AND " + RUNNING, rs -> {
            out.put(rs.getLong(1), new BoardWriteRestriction(rs.getLong(2), instant(rs, 3), rs.getString(4)));
        }, userId, ts(now));
        return out;
    }

    // ── 제재 쓰기 ─────────────────────────────────────────────────────────

    /** 그 회원·그 게시판의 끝이 지난 진행 중 쓰기 제한을 끝낸다(ended_at = 끝) — R__batch_40 규칙 1 과 같은 판정. 회원 행을 잠근 뒤. 끝낸 수 */
    public int endDue(long userId, long boardId, Instant now) {
        return jdbc.update("UPDATE board_sanctions SET status = 'ENDED', ended_at = ends_at, updated_at = ? "
                + "WHERE user_id = ? AND board_id = ? AND status = 'ACTIVE' AND ends_at IS NOT NULL AND ends_at <= ?", ts(now), userId, boardId, ts(now));
    }

    /** 새 제재 — 번호는 DB 시퀀스가 매긴다(돌려준다) */
    public long insert(long id, long boardId, long userId, BoardSanctionKind kind, String reasonCode, String reasonText, long issuedBy, String issuedRole, Long postId,
                       Long commentId, String sourceTitle, Instant startsAt, Instant endsAt, Instant now) {
        Long no = jdbc.queryForObject("INSERT INTO board_sanctions (id, board_id, user_id, kind, status, reason_code, reason_text, issued_by, issued_role, post_id, "
                        + "comment_id, source_title, starts_at, ends_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "RETURNING sanction_no", Long.class, id, boardId, userId, kind.name(), reasonCode, reasonText, issuedBy, issuedRole, postId, commentId, sourceTitle,
                ts(startsAt), ts(endsAt), ts(now), ts(now));
        return no == null ? 0 : no;
    }

    /** 매니저·스텝 처리 기록 한 줄(kind SANCTION) */
    public void insertAction(long id, long boardId, long actorId, String actorRole, String action, long targetUserId, long boardSanctionId, Long postId, Long commentId,
                             String reason, Instant now) {
        jdbc.update("INSERT INTO board_staff_actions (id, board_id, actor_id, actor_role, kind, action, target_user_id, board_sanction_id, post_id, comment_id, reason, "
                + "created_at) VALUES (?, ?, ?, ?, 'SANCTION', ?, ?, ?, ?, ?, ?, ?)", id, boardId, actorId, actorRole, action, targetUserId, boardSanctionId, postId, commentId,
                reason, ts(now));
    }

    /** 진행 중 쓰기 제한 해제 — 매니저({@code byUser}) 또는 운영자({@code byOperator}). 조건부(끝이 지났거나 이미 끝났으면 바꾸지 않는다). 풀었으면 true */
    public boolean lift(long id, Long byUser, Long byOperator, String reason, Instant now) {
        return jdbc.update("UPDATE board_sanctions SET status = 'LIFTED', lifted_at = ?, lifted_by_type = ?, lifted_by_user = ?, lifted_by_operator = ?, lift_reason = ?, "
                        + "updated_at = ? WHERE id = ? AND status = 'ACTIVE' AND kind <> 'WARNING' AND ends_at > ?", ts(now), byUser != null ? "MANAGER" : "OPERATOR", byUser,
                byOperator, reason, ts(now), id, ts(now)) == 1;
    }

    /** 무효로(어느 상태에서든 — 진행 중 쓰기 제한은 그 순간 풀린다). 바꿨으면 true */
    public boolean voidSanction(long id, String source, Long operatorId, String reason, Instant now) {
        return jdbc.update("UPDATE board_sanctions SET status = 'VOIDED', voided_at = ?, void_source = ?, voided_by = ?, void_reason = ?, updated_at = ? "
                + "WHERE id = ? AND status <> 'VOIDED'", ts(now), source, operatorId, reason, ts(now), id) == 1;
    }

    /** 경고 확인(멱등) — 내 제재가 아니면 false */
    public boolean acknowledge(long id, long userId, Instant now) {
        if (jdbc.queryForList("SELECT 1 FROM board_sanctions WHERE id = ? AND user_id = ?", Integer.class, id, userId).isEmpty()) {
            return false;
        }
        jdbc.update("UPDATE board_sanctions SET acknowledged_at = ?, updated_at = ? WHERE id = ? AND user_id = ? AND kind = 'WARNING' AND acknowledged_at IS NULL",
                ts(now), ts(now), id, userId);
        return true;
    }

    // ── 반복 검토 ─────────────────────────────────────────────────────────

    /** 그 (회원, 게시판)의 열린 검토가 있나 */
    public boolean hasOpenReview(long userId, long boardId) {
        return !jdbc.queryForList("SELECT 1 FROM board_sanction_reviews WHERE user_id = ? AND board_id = ? AND status = 'OPEN'", Integer.class, userId, boardId).isEmpty();
    }

    /** 그 (회원, 게시판)의 마지막으로 닫힌 검토의 closed_at(없으면 빈 값) */
    public Optional<Instant> lastClosedAt(long userId, long boardId) {
        return jdbc.query("SELECT max(closed_at) FROM board_sanction_reviews WHERE user_id = ? AND board_id = ? AND status = 'CLOSED'", (rs, i) -> instant(rs, 1),
                userId, boardId).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** 그 (회원, 게시판)에서 {@code after} 뒤에 받은 유효한(VOIDED 아닌) 제재 수 */
    public int countValidAfter(long userId, long boardId, Instant after) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM board_sanctions WHERE user_id = ? AND board_id = ? AND status <> 'VOIDED' AND created_at > ?",
                Integer.class, userId, boardId, ts(after));
        return n == null ? 0 : n;
    }

    /** 그 회원이 {@code after} 뒤에 유효한 게시판 제재를 받은 게시판 수 */
    public int boardsValidAfter(long userId, Instant after) {
        Integer n = jdbc.queryForObject("SELECT count(DISTINCT board_id) FROM board_sanctions WHERE user_id = ? AND status <> 'VOIDED' AND created_at > ?",
                Integer.class, userId, ts(after));
        return n == null ? 0 : n;
    }

    /** 검토를 연다 — 그 (회원, 게시판)에 이미 열린 건이 있으면 넣지 않는다(부분 유일 색인). 넣은 검토 번호(없으면 빈 값) */
    public Optional<Long> openReview(long id, long boardId, long userId, int count, Instant countedAfter, Instant now) {
        return jdbc.queryForList("INSERT INTO board_sanction_reviews (id, board_id, user_id, status, opened_at, opened_count, counted_after, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'OPEN', ?, ?, ?, ?, ?) ON CONFLICT (user_id, board_id) WHERE status = 'OPEN' DO NOTHING RETURNING review_no", Long.class,
                id, boardId, userId, ts(now), count, ts(countedAfter), ts(now), ts(now)).stream().findFirst();
    }

    public Optional<ReviewRow> findReview(long id) {
        return jdbc.query(REVIEW_SELECT + " WHERE r.id = ?", BoardSanctionStore::review, id).stream().findFirst();
    }

    /** 검토 행을 쓰기 잠금으로 — 두 운영자가 동시에 닫아도 한 번만(차례: 검토 행 → 이의제기 행 → 회원 행 → 제재 행). 회원·게시판 {@code [userId, boardId, open(1/0)]} */
    public Optional<long[]> lockReview(long id) {
        return jdbc.query("SELECT user_id, board_id, CASE WHEN status = 'OPEN' THEN 1 ELSE 0 END FROM board_sanction_reviews WHERE id = ? FOR UPDATE",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, id).stream().findFirst();
    }

    /** 닫기(조건부 — 열린 건만). 닫았으면 true */
    public boolean closeReview(long id, String result, String memo, Long siteSanctionId, List<Long> voidedIds, int closedCount, int closedBoardCount, long operatorId,
                               Instant now) {
        return jdbc.update(con -> {
            var ps = con.prepareStatement("UPDATE board_sanction_reviews SET status = 'CLOSED', closed_at = ?, closed_by = ?, result = ?, memo = ?, site_sanction_id = ?, "
                    + "voided_sanction_ids = ?, closed_count = ?, closed_board_count = ?, updated_at = ? WHERE id = ? AND status = 'OPEN'");
            ps.setObject(1, ts(now));
            ps.setLong(2, operatorId);
            ps.setString(3, result);
            ps.setString(4, memo);
            ps.setObject(5, siteSanctionId);
            ps.setArray(6, con.createArrayOf("bigint", voidedIds.toArray()));
            ps.setInt(7, closedCount);
            ps.setInt(8, closedBoardCount);
            ps.setObject(9, ts(now));
            ps.setLong(10, id);
            return ps;
        }) == 1;
    }

    /** 그 회원의 열린 검토 수(회원 상세) */
    public int openReviewsOf(long userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM board_sanction_reviews WHERE user_id = ? AND status = 'OPEN'", Integer.class, userId);
        return n == null ? 0 : n;
    }

    /**
     * 그 회원의 게시판별 요약 — {@code since} 뒤 유효한 수 · 유효한 전체 수 · 진행 중 쓰기 제한의 끝. 게시판 이름 차례. 회원 범위 색인 한 번.
     */
    public List<BoardSummary> boardSummaries(long userId, Instant since, Instant now) {
        return jdbc.query("SELECT s.board_id, b.slug, b.name, b.name_en, count(*) FILTER (WHERE s.status <> 'VOIDED' AND s.created_at > ?), "
                        + "count(*) FILTER (WHERE s.status <> 'VOIDED'), max(s.ends_at) FILTER (WHERE " + RUNNING + ") "
                        + "FROM board_sanctions s JOIN boards b ON b.id = s.board_id WHERE s.user_id = ? GROUP BY s.board_id, b.slug, b.name, b.name_en ORDER BY b.name, s.board_id",
                (rs, i) -> new BoardSummary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6), instant(rs, 7)),
                ts(since), ts(now), userId);
    }

    // ── 이의제기 ──────────────────────────────────────────────────────────

    /** 게시판 제재 이의제기 접수 — 이미 있으면 넣지 않는다(유일 색인). 넣은 접수 번호(없으면 빈 값) */
    public Optional<Long> insertAppeal(long id, long boardSanctionId, long userId, String reason, Instant now) {
        return jdbc.queryForList("INSERT INTO sanction_appeals (id, board_sanction_id, user_id, status, reason, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', ?, ?, ?) "
                + "ON CONFLICT (board_sanction_id) DO NOTHING RETURNING appeal_no", Long.class, id, boardSanctionId, userId, reason, ts(now), ts(now)).stream().findFirst();
    }

    /** 그 게시판 제재의 대기 이의제기를 쓰기 잠금으로(없으면 빈 값) — 무효 처리가 같은 트랜잭션에서 인용으로 닫는다(결정 10) */
    public Optional<Long> lockPendingAppeal(long boardSanctionId) {
        return jdbc.queryForList("SELECT id FROM sanction_appeals WHERE board_sanction_id = ? AND status = 'PENDING' FOR UPDATE", Long.class, boardSanctionId).stream()
                .findFirst();
    }

    /** 회원의 게시판 제재 이의제기(내 제재 화면 — 게시판 제재 ID → 이의제기) */
    public Map<Long, AppealRow> appealsOfUser(long userId) {
        return jdbc.query(MemberSanctionStore.APPEAL_SELECT + " WHERE a.user_id = ? AND a.board_sanction_id IS NOT NULL", MemberSanctionStore::appeal, userId).stream()
                .collect(Collectors.toMap(AppealRow::boardSanctionId, a -> a, (a, b) -> a));
    }

    // ── 매니저·스텝 내역 ──────────────────────────────────────────────────

    /** 제재 내역 거르기 — {@code issuedBy} 가 있으면 그 처리자가 준 것만(스텝), {@code scope} ALL · ACTIVE(진행 중 쓰기 제한) · ENDED(그 밖) */
    public long staffTotal(long boardId, Long issuedBy, String scope, Instant now) {
        List<Object> args = new ArrayList<>();
        String where = staffWhere(boardId, issuedBy, scope, now, args);
        Long n = jdbc.queryForObject("SELECT count(*) FROM board_sanctions s" + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<BoardSanctionRow> staffPage(long boardId, Long issuedBy, String scope, Instant now, int offset, int size) {
        List<Object> args = new ArrayList<>();
        String where = staffWhere(boardId, issuedBy, scope, now, args);
        args.add(size);
        args.add((long) offset);
        return jdbc.query(SELECT + where + " ORDER BY s.created_at DESC, s.id DESC LIMIT ? OFFSET ?", BoardSanctionStore::row, args.toArray());
    }

    private static String staffWhere(long boardId, Long issuedBy, String scope, Instant now, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE s.board_id = ?");
        args.add(boardId);
        if (issuedBy != null) {
            where.append(" AND s.issued_by = ?");
            args.add(issuedBy);
        }
        if ("ACTIVE".equals(scope)) {
            where.append(" AND ").append(RUNNING);
            args.add(ts(now));
        } else if ("ENDED".equals(scope)) {
            where.append(" AND NOT (s.kind <> 'WARNING' AND s.status = 'ACTIVE' AND s.ends_at > ?) AND NOT (s.kind = 'WARNING' AND s.status = 'ACTIVE')");
            args.add(ts(now));
        }
        return where.toString();
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    public static BoardSanctionRow row(ResultSet rs, int i) throws SQLException {
        String appealStatus = rs.getString(40);
        return new BoardSanctionRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8),
                rs.getString(9), rs.getString(10), BoardSanctionKind.valueOf(rs.getString(11)), SanctionStatus.valueOf(rs.getString(12)), rs.getString(13),
                rs.getString(14), nullableLong(rs, 15), rs.getString(16), rs.getString(17), nullableLong(rs, 18), nullableLong(rs, 19), rs.getString(20), instant(rs, 21),
                instant(rs, 22), instant(rs, 23), instant(rs, 24), rs.getString(25), nullableLong(rs, 26), rs.getString(27), nullableLong(rs, 28), rs.getString(29),
                rs.getString(30), instant(rs, 31), rs.getString(32), nullableLong(rs, 33), rs.getString(34), rs.getString(35), instant(rs, 36), instant(rs, 37),
                nullableLong(rs, 38), nullableLong(rs, 39), appealStatus == null ? null : AppealStatus.valueOf(appealStatus));
    }

    public static ReviewRow review(ResultSet rs, int i) throws SQLException {
        Array array = rs.getArray(23);
        List<Long> voided = array == null ? List.of()
                : Arrays.stream((Object[]) array.getArray()).map(v -> ((Number) v).longValue()).toList();
        return new ReviewRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getString(11), instant(rs, 12), rs.getInt(13), instant(rs, 14), instant(rs, 15), nullableLong(rs, 16), rs.getString(17),
                rs.getString(18), rs.getString(19), nullableLong(rs, 20), nullableLong(rs, 21), rs.getString(22), voided, nullableInt(rs, 24), nullableInt(rs, 25));
    }

    private static TargetRow target(ResultSet rs, int i) throws SQLException {
        return new TargetRow(rs.getLong(1), rs.getString(2), rs.getBoolean(3));
    }

    private static Integer nullableInt(ResultSet rs, int column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
