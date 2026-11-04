package com.community.sanction.repository.master;

import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.entity.SanctionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사이트 제재({@code member_sanctions})·이의제기({@code sanction_appeals}) 읽기·쓰기 — shared V136. 서비스({@code MemberSanctionService})와 관리자 목록 저장소가 부른다.
 *
 * <p>JDBC(native SQL)인 이유: 회원 행 잠금({@code FOR UPDATE OF u}) 아래에서 조건부 UPDATE 한 문장으로 한 번만 이기게 하고(해제 · 인용 · 만료), 이의제기 접수는
 * {@code ON CONFLICT (sanction_id) DO NOTHING} 으로 유일 색인이 막으며, 진행 중 정지의 판정을 부분 색인({@code ux_member_sanctions_running} ·
 * {@code ix_member_sanctions_expire})과 같은 조건 글자로 둔다 — 기능 제한({@code MemberRestrictionStore})과 같은 방식. 트랜잭션은 부르는 서비스가 연다.
 * 판정 시각은 DB 의 {@code now()} 가 아니라 서비스가 넘기는 지금이다.
 *
 * <p>⚠ {@link #endDue}·{@link #releaseMember} 는 DB 배치 {@code R__batch_39_sanction_expire.sql} 과 같은 판정이다(이중 구현) — 한쪽을 고치면 다른 쪽도 고친다.
 */
@Repository
public class MemberSanctionStore {

    /** 진행 중인 정지·영구 정지(부분 유일 색인 {@code ux_member_sanctions_running} 과 같은 조건) — 별칭 {@code x}, 회원 칸 자리 하나 */
    static final String RUNNING_OF = "SELECT 1 FROM member_sanctions x WHERE x.user_id = ? AND x.status = 'ACTIVE' AND x.kind <> 'WARNING'";

    /** 제재 한 줄 — 회원(닉네임·등급·상태)·운영자 이름·신고 사건 번호·이의제기 요약을 함께 */
    public static final String SELECT = "SELECT s.id, s.sanction_no, s.user_id, s.kind, s.status, s.warning_no, s.skipped, s.reason, s.starts_at, s.ends_at, "
            + "s.issued_by, io.operator_name, s.report_case_id, rc.case_no, s.acknowledged_at, s.lifted_by, lo.operator_name, s.lifted_at, s.lift_reason, "
            + "s.voided_at, s.ended_at, s.created_at, a.id, a.appeal_no, a.status, p.nickname, p.grade_key, u.status "
            + "FROM member_sanctions s JOIN users u ON u.id = s.user_id LEFT JOIN profiles p ON p.user_id = s.user_id "
            + "LEFT JOIN operators io ON io.id = s.issued_by LEFT JOIN operators lo ON lo.id = s.lifted_by "
            + "LEFT JOIN report_cases rc ON rc.id = s.report_case_id LEFT JOIN sanction_appeals a ON a.sanction_id = s.id";

    /** 이의제기 한 줄 — 답변한 운영자 이름을 함께 */
    public static final String APPEAL_SELECT = "SELECT a.id, a.appeal_no, a.sanction_id, a.user_id, a.status, a.reason, a.answer, a.answered_by, ao.operator_name, "
            + "a.answered_at, a.created_at FROM sanction_appeals a LEFT JOIN operators ao ON ao.id = a.answered_by";

    /**
     * 제재 한 줄.
     *
     * @param status 저장된 상태 — 끝이 지났지만 아직 만료되지 않은 정지는 {@link #effectiveStatus} 가 ENDED 로 본다
     */
    public record SanctionRow(long id, long no, long userId, SanctionKind kind, SanctionStatus status, Integer warningNo, boolean skipped, String reason,
                              Instant startsAt, Instant endsAt, Long issuedBy, String issuedByName, Long reportCaseId, Long reportCaseNo, Instant acknowledgedAt,
                              Long liftedBy, String liftedByName, Instant liftedAt, String liftReason, Instant voidedAt, Instant endedAt, Instant createdAt,
                              Long appealId, Long appealNo, AppealStatus appealStatus, String nickname, String gradeKey, String userStatus) {

        /** {@code now} 의 상태 — 진행 중인데 끝이 지난 기간 정지는 ENDED(크론이 1분 안에 그렇게 만든다) */
        public SanctionStatus effectiveStatus(Instant now) {
            return status == SanctionStatus.ACTIVE && due(now) ? SanctionStatus.ENDED : status;
        }

        /** 끝난 시각 — 아직 만료 처리 전이면 끝 시각 */
        public Instant effectiveEndedAt(Instant now) {
            return endedAt == null && status == SanctionStatus.ACTIVE && due(now) ? endsAt : endedAt;
        }

        /** {@code now} 에 진행 중인 정지·영구 정지인가 */
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

    /** 이의제기 한 줄 */
    public record AppealRow(long id, long no, long sanctionId, long userId, AppealStatus status, String reason, String answer, Long answeredBy, String answeredByName,
                            Instant answeredAt, Instant createdAt) {
    }

    /**
     * 제재 대상 회원.
     *
     * @param gone 탈퇴(WITHDRAWN · 지워짐) 또는 탈퇴 진행 중(WITHDRAWING) — 제재할 수 없다
     */
    public record MemberRow(long id, String status, boolean gone, String nickname, String gradeKey) {
    }

    /** 끝난 게시판 매니저·스텝 지정 한 줄 */
    public record StaffRow(long boardId, String role) {
    }

    private final JdbcTemplate jdbc;

    public MemberSanctionStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    // ── 회원 ──────────────────────────────────────────────────────────────

    private static final String MEMBER = "SELECT u.id, u.status, (u.status IN ('WITHDRAWN', 'WITHDRAWING') OR u.deleted_at IS NOT NULL), p.nickname, p.grade_key "
            + "FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE u.id = ?";

    public Optional<MemberRow> member(long userId) {
        return jdbc.query(MEMBER, MemberSanctionStore::member, userId).stream().findFirst();
    }

    /** 회원 행을 쓰기 잠금으로 읽는다 — 부여·해제·인용·만료가 같은 회원에서 줄을 선다(차례: 회원 행 → 제재 행) */
    public Optional<MemberRow> lockMember(long userId) {
        return jdbc.query(MEMBER + " FOR UPDATE OF u", MemberSanctionStore::member, userId).stream().findFirst();
    }

    /** 계정을 막는 상태로 바꾼다(SUSPENDED · BANNED) — 회원 행을 잠근 뒤 */
    public void restrictMember(long userId, String status, Instant now) {
        jdbc.update("UPDATE users SET status = ?, updated_at = ? WHERE id = ? AND status IN ('ACTIVE', 'SUSPENDED', 'BANNED')", status, ts(now), userId);
    }

    /**
     * 진행 중인 정지·영구 정지가 없으면 정상(ACTIVE)으로 되돌린다 — 상태가 SUSPENDED·BANNED 이고 제재 기록이 있는 회원만(탈퇴 진행 중은 그대로).
     * R__batch_39 규칙 1·2 와 같은 판정. 회원 행을 잠근 뒤 부른다.
     *
     * @return 되돌렸으면 true
     */
    public boolean releaseMember(long userId, Instant now) {
        return jdbc.update("UPDATE users SET status = 'ACTIVE', updated_at = ? WHERE id = ? AND status IN ('SUSPENDED', 'BANNED') "
                + "AND EXISTS (SELECT 1 FROM member_sanctions s WHERE s.user_id = ?) AND NOT EXISTS (" + RUNNING_OF + ")", ts(now), userId, userId, userId) == 1;
    }

    /** 읽기 전 마감이 할 일이 있나 — 끝이 지난 진행 중 정지가 있거나, 막힌 상태인데 진행 중 정지·영구 정지가 없다(제재 기록이 있는 회원). 회원 범위 색인만 읽는다 */
    public boolean needsSettle(long userId, Instant now) {
        Boolean needed = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM member_sanctions s WHERE s.user_id = ? AND s.status = 'ACTIVE' AND s.ends_at IS NOT NULL "
                + "AND s.ends_at <= ?) OR EXISTS (SELECT 1 FROM users u WHERE u.id = ? AND u.status IN ('SUSPENDED', 'BANNED') "
                + "AND EXISTS (SELECT 1 FROM member_sanctions s WHERE s.user_id = u.id) AND NOT EXISTS (" + RUNNING_OF + "))", Boolean.class, userId, ts(now), userId, userId);
        return Boolean.TRUE.equals(needed);
    }

    /** 끝이 지난 진행 중 기간 정지를 끝낸다(ended_at = 끝 시각) — R__batch_39 규칙 1 과 같은 판정. 끝낸 수 */
    public int endDue(long userId, Instant now) {
        return jdbc.update("UPDATE member_sanctions SET status = 'ENDED', ended_at = ends_at, updated_at = ? "
                + "WHERE user_id = ? AND status = 'ACTIVE' AND ends_at IS NOT NULL AND ends_at <= ?", ts(now), userId, ts(now));
    }

    /** 그 회원의 게시판 매니저·스텝 지정을 모두 끝낸다(정지 시작 — operator-permissions.md 「자동 해제」). 끝낸 지정 */
    public List<StaffRow> endBoardStaff(long userId) {
        return jdbc.query("DELETE FROM board_staff WHERE user_id = ? RETURNING board_id, role", (rs, i) -> new StaffRow(rs.getLong(1), rs.getString(2)), userId);
    }

    // ── 제재 ──────────────────────────────────────────────────────────────

    public Optional<SanctionRow> find(long id) {
        return jdbc.query(SELECT + " WHERE s.id = ?", MemberSanctionStore::row, id).stream().findFirst();
    }

    /** 그 제재의 회원(없으면 빈 값) */
    public Optional<Long> userOf(long sanctionId) {
        return jdbc.queryForList("SELECT user_id FROM member_sanctions WHERE id = ?", Long.class, sanctionId).stream().findFirst();
    }

    /** 그 회원의 제재 — 최근 것이 위, 최대 {@code limit}(무효 포함). 색인 {@code ix_member_sanctions_user} */
    public List<SanctionRow> ofUser(long userId, int limit) {
        return jdbc.query(SELECT + " WHERE s.user_id = ? ORDER BY s.created_at DESC, s.id DESC LIMIT ?", MemberSanctionStore::row, userId, limit);
    }

    /** 그 회원의 모든 제재(단계 계산용 — 회원 하나의 제재 수는 적다) */
    public List<SanctionRow> allOfUser(long userId) {
        return jdbc.query(SELECT + " WHERE s.user_id = ? ORDER BY s.created_at DESC, s.id DESC", MemberSanctionStore::row, userId);
    }

    /** 새 제재 — 번호는 DB 시퀀스가 매긴다(돌려준다) */
    public long insert(long id, long userId, SanctionKind kind, Integer warningNo, boolean skipped, String reason, Instant startsAt, Instant endsAt, long issuedBy,
                       Long reportCaseId, Instant now) {
        Long no = jdbc.queryForObject("INSERT INTO member_sanctions (id, user_id, kind, status, warning_no, skipped, reason, starts_at, ends_at, issued_by, report_case_id, "
                        + "created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING sanction_no", Long.class, id, userId, kind.name(),
                warningNo, skipped, reason, ts(startsAt), ts(endsAt), issuedBy, reportCaseId, ts(now), ts(now));
        return no == null ? 0 : no;
    }

    /** 진행 중인 정지를 영구 정지로 대체해 끝낸다(ended_at = 지금). 끝낸 제재 ID(없으면 빈 값) */
    public Optional<Long> endRunning(long userId, Instant now) {
        return jdbc.queryForList("UPDATE member_sanctions SET status = 'ENDED', ended_at = ?, updated_at = ? WHERE user_id = ? AND status = 'ACTIVE' AND kind <> 'WARNING' "
                + "RETURNING id", Long.class, ts(now), ts(now), userId).stream().findFirst();
    }

    /** 진행 중인 정지·영구 정지를 푼다(조건부 — 끝이 지났거나 이미 끝났으면 바꾸지 않는다). 풀었으면 true */
    public boolean lift(long id, long operatorId, String reason, Instant now) {
        return jdbc.update("UPDATE member_sanctions SET status = 'LIFTED', lifted_by = ?, lifted_at = ?, lift_reason = ?, updated_at = ? "
                + "WHERE id = ? AND status = 'ACTIVE' AND kind <> 'WARNING' AND (ends_at IS NULL OR ends_at > ?)", operatorId, ts(now), reason, ts(now), id, ts(now)) == 1;
    }

    /** 이의제기 인용 — 무효로(어느 상태에서든). 바꿨으면 true */
    public boolean voidSanction(long id, Instant now) {
        return jdbc.update("UPDATE member_sanctions SET status = 'VOIDED', voided_at = ?, warning_no = NULL, updated_at = ? WHERE id = ? AND status <> 'VOIDED'",
                ts(now), ts(now), id) == 1;
    }

    /** 유효한 경고의 번호를 받은 차례대로 다시 매긴다(1·2 — 2 넘으면 2) — 경고가 무효가 된 뒤 */
    public void renumberWarnings(long userId, Instant now) {
        jdbc.update("UPDATE member_sanctions m SET warning_no = t.n, updated_at = ? FROM (SELECT id, least(row_number() OVER (ORDER BY created_at, id), 2) AS n "
                + "FROM member_sanctions WHERE user_id = ? AND kind = 'WARNING' AND status <> 'VOIDED') t WHERE m.id = t.id AND m.warning_no IS DISTINCT FROM t.n",
                ts(now), userId);
    }

    /** 경고 확인(멱등) — 내 제재가 아니면 false */
    public boolean acknowledge(long id, long userId, Instant now) {
        if (jdbc.queryForList("SELECT 1 FROM member_sanctions WHERE id = ? AND user_id = ?", Integer.class, id, userId).isEmpty()) {
            return false;
        }
        jdbc.update("UPDATE member_sanctions SET acknowledged_at = ?, updated_at = ? WHERE id = ? AND user_id = ? AND kind = 'WARNING' AND acknowledged_at IS NULL",
                ts(now), ts(now), id, userId);
        return true;
    }

    /** 확인하지 않은 유효한 경고 수 */
    public int pendingWarnings(long userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM member_sanctions WHERE user_id = ? AND kind = 'WARNING' AND status = 'ACTIVE' AND acknowledged_at IS NULL",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** 진행 중인 기간 정지의 끝(영구 정지·정지 없음이면 빈 값) */
    public Optional<Instant> runningUntil(long userId, Instant now) {
        return jdbc.query("SELECT ends_at FROM member_sanctions WHERE user_id = ? AND status = 'ACTIVE' AND kind <> 'WARNING' AND ends_at > ? LIMIT 1",
                (rs, i) -> instant(rs, 1), userId, ts(now)).stream().findFirst();
    }

    // ── 이의제기 ──────────────────────────────────────────────────────────

    /** 이의제기 접수 — 그 제재에 이미 있으면 넣지 않는다(유일 색인). 넣은 이의제기 번호(없으면 빈 값) */
    public Optional<Long> insertAppeal(long id, long sanctionId, long userId, String reason, Instant now) {
        return jdbc.queryForList("INSERT INTO sanction_appeals (id, sanction_id, user_id, status, reason, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', ?, ?, ?) "
                + "ON CONFLICT (sanction_id) DO NOTHING RETURNING appeal_no", Long.class, id, sanctionId, userId, reason, ts(now), ts(now)).stream().findFirst();
    }

    public Optional<AppealRow> findAppeal(long id) {
        return jdbc.query(APPEAL_SELECT + " WHERE a.id = ?", MemberSanctionStore::appeal, id).stream().findFirst();
    }

    /** 제재의 이의제기 */
    public Optional<AppealRow> appealOf(long sanctionId) {
        return jdbc.query(APPEAL_SELECT + " WHERE a.sanction_id = ?", MemberSanctionStore::appeal, sanctionId).stream().findFirst();
    }

    /** 회원의 이의제기 전부(내 제재 화면 — 제재 ID 로 잇는다) */
    public List<AppealRow> appealsOfUser(long userId) {
        return jdbc.query(APPEAL_SELECT + " WHERE a.user_id = ?", MemberSanctionStore::appeal, userId);
    }

    /** 이의제기 행을 쓰기 잠금으로 읽는다 — 두 운영자가 동시에 답해도 한 번만(차례: 이의제기 행 → 회원 행 → 제재 행) */
    public Optional<AppealRow> lockAppeal(long id) {
        return jdbc.query("SELECT a.id, a.appeal_no, a.sanction_id, a.user_id, a.status, a.reason, a.answer, a.answered_by, NULL, a.answered_at, a.created_at "
                + "FROM sanction_appeals a WHERE a.id = ? FOR UPDATE", MemberSanctionStore::appeal, id).stream().findFirst();
    }

    /** 답변(조건부 — 대기일 때만). 바꿨으면 true */
    public boolean answerAppeal(long id, AppealStatus to, String answer, long operatorId, Instant now) {
        return jdbc.update("UPDATE sanction_appeals SET status = ?, answer = ?, answered_by = ?, answered_at = ?, updated_at = ? WHERE id = ? AND status = 'PENDING'",
                to.name(), answer, operatorId, ts(now), ts(now), id) == 1;
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    public static SanctionRow row(ResultSet rs, int i) throws SQLException {
        String appealStatus = rs.getString(25);
        return new SanctionRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), SanctionKind.valueOf(rs.getString(4)), SanctionStatus.valueOf(rs.getString(5)),
                nullableInt(rs, 6), rs.getBoolean(7), rs.getString(8), instant(rs, 9), instant(rs, 10), nullableLong(rs, 11), rs.getString(12), nullableLong(rs, 13),
                nullableLong(rs, 14), instant(rs, 15), nullableLong(rs, 16), rs.getString(17), instant(rs, 18), rs.getString(19), instant(rs, 20), instant(rs, 21),
                instant(rs, 22), nullableLong(rs, 23), nullableLong(rs, 24), appealStatus == null ? null : AppealStatus.valueOf(appealStatus), rs.getString(26),
                rs.getString(27), rs.getString(28));
    }

    public static AppealRow appeal(ResultSet rs, int i) throws SQLException {
        return new AppealRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), AppealStatus.valueOf(rs.getString(5)), rs.getString(6), rs.getString(7),
                nullableLong(rs, 8), rs.getString(9), instant(rs, 10), instant(rs, 11));
    }

    private static MemberRow member(ResultSet rs, int i) throws SQLException {
        return new MemberRow(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getString(5));
    }

    public static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, int column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
