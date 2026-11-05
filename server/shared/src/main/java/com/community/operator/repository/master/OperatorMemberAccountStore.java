package com.community.operator.repository.master;

import static com.community.sanction.repository.master.MemberSanctionStore.instant;
import static com.community.sanction.repository.master.MemberSanctionStore.nullableLong;
import static com.community.sanction.repository.master.MemberSanctionStore.ts;

import com.community.role.entity.MemberRoleIds;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 운영자 회원 계정 연결({@code operator_member_accounts} — shared V141) 읽기·쓰기와 연결 후보 판정 재료.
 *
 * <p>JDBC 인 이유: 「힘이 살아 있나」(정책 6)를 운영자 상태 + 회원 상태로 <b>한 문장</b>에 판정하고(요청마다 — 캐시 없음), 목록 작성자 표시는 회원 ID 묶음 한 질의로 고른다.
 * 운영자 판정 글자({@link #OPERATOR_OK})는 {@code OperatorEntity#canLogin} 과 같은 규칙이다(이중 구현 — 한쪽을 고치면 다른 쪽도 고친다).
 */
@Repository
public class OperatorMemberAccountStore {

    /**
     * 운영자를 지금 쓸 수 있다 — 승인됨 · 잠기지 않음 · 만료 표시 없음 · 사용 중 · 기간 안({@code OperatorEntity#canLogin}). 별칭 {@code o}, 자리표시자 둘(지금, 지금)
     */
    public static final String OPERATOR_OK = "(o.approved AND NOT o.locked AND NOT o.expired AND o.enabled AND (o.account_start_at IS NULL OR o.account_start_at <= ?) "
            + "AND (o.account_end_at IS NULL OR o.account_end_at >= ?))";
    /** 회원 상태 정상 — 정지·영구 정지·탈퇴 진행·탈퇴가 아님. 별칭 {@code u} */
    public static final String MEMBER_OK = "(u.status = 'ACTIVE' AND u.deleted_at IS NULL)";

    private static final String LINK_SELECT = "SELECT m.operator_id, o.operator_name, o.is_root, m.user_id, p.nickname, p.grade_key, m.linked_at, m.linked_by, lo.operator_name, "
            + OPERATOR_OK + ", " + MEMBER_OK + " FROM operator_member_accounts m JOIN operators o ON o.id = m.operator_id JOIN users u ON u.id = m.user_id "
            + "LEFT JOIN profiles p ON p.user_id = m.user_id LEFT JOIN operators lo ON lo.id = m.linked_by";

    /**
     * 연결 한 줄 + 지금 판정.
     *
     * @param operatorOk 운영자를 지금 쓸 수 있다(정책 6 — 아니면 멈춤 {@code OPERATOR_UNAVAILABLE})
     * @param memberOk   회원 상태 정상(아니면 멈춤 {@code MEMBER_SUSPENDED})
     */
    public record Link(long operatorId, String operatorName, boolean root, long userId, String nickname, String gradeKey, Instant linkedAt, Long linkedById,
                       String linkedByName, boolean operatorOk, boolean memberOk) {

        /** 힘이 살아 있다 */
        public boolean active() {
            return operatorOk && memberOk;
        }
    }

    /**
     * 연결 후보 회원(운영자와 같은 이메일) — 자격 재료를 함께.
     *
     * @param status             회원 상태(ACTIVE · SUSPENDED · BANNED · WITHDRAWING)
     * @param business           사업자 계정(사업자 회원 역할 또는 살아 있는 사업자 신원 — 심사 중 · 승인 · 정지)
     * @param linkedOperatorId   이미 이어진 운영자(없으면 null)
     */
    public record Candidate(long userId, String nickname, String gradeKey, Instant joinedAt, String status, boolean emailVerified, boolean deleted, boolean business,
                            Long linkedOperatorId, String linkedOperatorName, int managerCount, int staffCount) {
    }

    private final JdbcTemplate jdbc;

    public OperatorMemberAccountStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    /** 그 회원의 연결(없으면 빈 값) */
    public Optional<Link> linkOfUser(long userId, Instant now) {
        return jdbc.query(LINK_SELECT + " WHERE m.user_id = ?", OperatorMemberAccountStore::link, ts(now), ts(now), userId).stream().findFirst();
    }

    /** 그 운영자의 연결(없으면 빈 값) */
    public Optional<Link> linkOfOperator(long operatorId, Instant now) {
        return jdbc.query(LINK_SELECT + " WHERE m.operator_id = ?", OperatorMemberAccountStore::link, ts(now), ts(now), operatorId).stream().findFirst();
    }

    /** 운영자 여럿의 연결(운영자 ID → 연결) — 운영자 목록 이름 옆 딱지 */
    public Map<Long, Link> linksOfOperators(Collection<Long> operatorIds, Instant now) {
        List<Long> ids = operatorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[ids.size() + 2];
        args[0] = ts(now);
        args[1] = ts(now);
        for (int i = 0; i < ids.size(); i++) {
            args[i + 2] = ids.get(i);
        }
        Map<Long, Link> out = new HashMap<>();
        jdbc.query(LINK_SELECT + " WHERE m.operator_id IN (" + marks + ")", OperatorMemberAccountStore::link, args).forEach(l -> out.put(l.operatorId(), l));
        return out;
    }

    /** 이어져 있나(힘과 상관없이 — 탈퇴 막기 · 게시판 담당 자격) */
    public boolean linked(long userId) {
        return !jdbc.queryForList("SELECT 1 FROM operator_member_accounts WHERE user_id = ?", Integer.class, userId).isEmpty();
    }

    /** 이어져 있는 회원 ID 중에서(힘과 상관없이) — 게시판 담당 후보 목록 */
    public Set<Long> linkedUserIds(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        return Set.copyOf(jdbc.queryForList("SELECT user_id FROM operator_member_accounts WHERE user_id IN (" + marks + ")", Long.class, ids.toArray()));
    }

    /** 힘이 살아 있는 연결의 회원 ID — {@code userIds} 가 null 이면 전부(연결은 운영자 수만큼이라 작다) */
    public Set<Long> activeUserIds(Collection<Long> userIds, Instant now) {
        String sql = "SELECT m.user_id FROM operator_member_accounts m JOIN operators o ON o.id = m.operator_id JOIN users u ON u.id = m.user_id WHERE " + OPERATOR_OK
                + " AND " + MEMBER_OK;
        if (userIds == null) {
            return Set.copyOf(jdbc.queryForList(sql, Long.class, ts(now), ts(now)));
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[ids.size() + 2];
        args[0] = ts(now);
        args[1] = ts(now);
        for (int i = 0; i < ids.size(); i++) {
            args[i + 2] = ids.get(i);
        }
        return Set.copyOf(jdbc.queryForList(sql + " AND m.user_id IN (" + marks + ")", Long.class, args));
    }

    /** 이 이메일(정규화한 소문자)로 가입한 회원 — 연결 후보(정책 2). 없으면 빈 값. 색인 {@code ux_users_email} */
    public Optional<Candidate> candidateByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("SELECT u.id, p.nickname, p.grade_key, u.created_at, u.status, u.email_verified_at IS NOT NULL, u.deleted_at IS NOT NULL, "
                        + "(EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.board_id IS NULL AND r.role_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM business_profiles b WHERE b.user_id = u.id AND b.status IN ('PENDING', 'APPROVED', 'SUSPENDED'))), "
                        + "m.operator_id, mo.operator_name, "
                        + "(SELECT count(*) FROM board_staff s WHERE s.user_id = u.id AND s.role = 'MANAGER'), "
                        + "(SELECT count(*) FROM board_staff s WHERE s.user_id = u.id AND s.role = 'STAFF') "
                        + "FROM users u LEFT JOIN profiles p ON p.user_id = u.id LEFT JOIN operator_member_accounts m ON m.user_id = u.id "
                        + "LEFT JOIN operators mo ON mo.id = m.operator_id WHERE u.email = ?",
                (rs, i) -> new Candidate(rs.getLong(1), rs.getString(2), rs.getString(3), instant(rs, 4), rs.getString(5), rs.getBoolean(6), rs.getBoolean(7),
                        rs.getBoolean(8), nullableLong(rs, 9), rs.getString(10), rs.getInt(11), rs.getInt(12)),
                MemberRoleIds.BUSINESS, normalizedEmail).stream().findFirst();
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    /** 회원 행을 쓰기 잠금으로 — 연결 · 탈퇴 신청 · 게시판 담당 지정이 줄을 선다. 없으면 false */
    public boolean lockUser(long userId) {
        return !jdbc.queryForList("SELECT 1 FROM users WHERE id = ? FOR UPDATE", Integer.class, userId).isEmpty();
    }

    public void insert(long operatorId, long userId, Instant linkedAt, Long linkedBy) {
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at, linked_by) VALUES (?, ?, ?, ?)", operatorId, userId, ts(linkedAt), linkedBy);
    }

    /** 그 운영자의 연결을 지운다 — 지운 회원 ID(없었으면 빈 값) */
    public Optional<Long> delete(long operatorId) {
        return jdbc.queryForList("DELETE FROM operator_member_accounts WHERE operator_id = ? RETURNING user_id", Long.class, operatorId).stream().findFirst();
    }

    /** 그 회원의 게시판 매니저·스텝 지정을 모두 끝낸다(정책 4 — 행 삭제). 끝난 게시판 ID */
    public List<Long> releaseBoardStaff(long userId) {
        return jdbc.queryForList("DELETE FROM board_staff WHERE user_id = ? RETURNING board_id", Long.class, userId).stream().sorted().toList();
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private static Link link(ResultSet rs, int i) throws SQLException {
        return new Link(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getLong(4), rs.getString(5), rs.getString(6), instant(rs, 7), nullableLong(rs, 8),
                rs.getString(9), rs.getBoolean(10), rs.getBoolean(11));
    }
}
