package com.community.sanction.repository.master;

import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 제재·이의제기 목록·건수·회원 찾기·대시보드 셈(읽기만) — 한 줄 모양은 shared {@link MemberSanctionStore}({@code SELECT}·{@code row}). native SQL 인 이유도 그쪽과 같다.
 * 색인: 목록·건수는 V136 의 부분 색인({@code ix_member_sanctions_running_list} · {@code ix_member_sanctions_closed_list} · {@code ix_sanction_appeals_pending} ·
 * {@code ix_sanction_appeals_status}), 회원 찾기는 닉네임 trigram 색인({@code ix_profiles_nickname_trgm}).
 */
@Repository
public class AdminSanctionStore {

    /** 범위 「진행 중」 — 진행 중인 정지·영구 정지(끝이 지난 것은 뺀다, 크론이 1분 안에 끝낸다). 지금 자리 하나 */
    static final String RUNNING = "s.status = 'ACTIVE' AND s.kind <> 'WARNING' AND (s.ends_at IS NULL OR s.ends_at > ?)";
    /** 범위 「끝남」 */
    static final String CLOSED = "s.status <> 'ACTIVE'";

    /** 목록 거르기 */
    public record Filter(String scope, SanctionKind kind, String nicknameLike, Long sanctionNo, Instant from, Instant to, Long userId, boolean oldest) {
    }

    /** 회원 찾기 한 줄 */
    public record TargetRow(long id, String nickname, String gradeKey, String status, int warningCount) {
    }

    /** 대시보드의 오래된 대기 이의제기 한 줄 */
    public record PendingAppealRow(long id, String nickname, SanctionKind kind, Integer warningNo, Instant appealedAt) {
    }

    private final JdbcTemplate jdbc;

    public AdminSanctionStore(MemberSanctionStore store) {
        this.jdbc = store.jdbc();
    }

    // ── 제재 목록 ─────────────────────────────────────────────────────────

    /** 전체 건수(거르기와 무관) — all · active · closed */
    public long[] counts(Instant now) {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM member_sanctions), (SELECT count(*) FROM member_sanctions s WHERE " + RUNNING + "), "
                + "(SELECT count(*) FROM member_sanctions s WHERE " + CLOSED + ")", (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, ts(now));
    }

    public long total(Filter filter, Instant now) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, now, args);
        boolean needsProfile = filter.nicknameLike() != null;
        Long n = jdbc.queryForObject("SELECT count(*) FROM member_sanctions s" + (needsProfile ? " LEFT JOIN profiles p ON p.user_id = s.user_id" : "") + where, Long.class,
                args.toArray());
        return n == null ? 0 : n;
    }

    public List<SanctionRow> page(Filter filter, Instant now, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, now, args);
        String order = filter.oldest() ? " ORDER BY s.created_at, s.id" : " ORDER BY s.created_at DESC, s.id DESC";
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(MemberSanctionStore.SELECT + where + order + " LIMIT ? OFFSET ?", MemberSanctionStore::row, args.toArray());
    }

    private static String where(Filter f, Instant now, List<Object> args) {
        List<String> parts = new ArrayList<>();
        if ("ACTIVE".equals(f.scope())) {
            parts.add(RUNNING);
            args.add(ts(now));
        } else if ("CLOSED".equals(f.scope())) {
            parts.add(CLOSED);
        }
        if (f.kind() != null) {
            parts.add("s.kind = ?");
            args.add(f.kind().name());
        }
        if (f.nicknameLike() != null) {
            parts.add("lower(p.nickname) LIKE ? ESCAPE '\\'");
            args.add(f.nicknameLike());
        }
        if (f.sanctionNo() != null) {
            parts.add("s.sanction_no = ?");
            args.add(f.sanctionNo());
        }
        if (f.from() != null) {
            parts.add("s.created_at >= ?");
            args.add(ts(f.from()));
        }
        if (f.to() != null) {
            parts.add("s.created_at <= ?");
            args.add(ts(f.to()));
        }
        if (f.userId() != null) {
            parts.add("s.user_id = ?");
            args.add(f.userId());
        }
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    /** 제재 여럿(ID 로) — 이의제기 목록의 제재 요약 */
    public Map<Long, SanctionRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbc.query(MemberSanctionStore.SELECT + " WHERE s.id IN (" + marks + ")", MemberSanctionStore::row, ids.toArray()).stream()
                .collect(Collectors.toMap(SanctionRow::id, r -> r));
    }

    // ── 회원 찾기 ─────────────────────────────────────────────────────────

    /** 닉네임에 {@code like} 가 들어간 회원(탈퇴·탈퇴 진행 중 제외) 최대 {@code limit} — 같은 닉네임 · 짧은 닉네임 먼저 */
    public List<TargetRow> targets(String like, String exact, int limit) {
        return jdbc.query("SELECT u.id, p.nickname, p.grade_key, u.status, "
                        + "(SELECT count(*) FROM member_sanctions s WHERE s.user_id = u.id AND s.kind = 'WARNING' AND s.status = 'ACTIVE') "
                        + "FROM profiles p JOIN users u ON u.id = p.user_id "
                        + "WHERE lower(p.nickname) LIKE ? ESCAPE '\\' AND u.deleted_at IS NULL AND u.status NOT IN ('WITHDRAWN', 'WITHDRAWING') "
                        + "ORDER BY (lower(p.nickname) = ?) DESC, char_length(p.nickname), p.nickname, u.id LIMIT ?",
                (rs, i) -> new TargetRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)), like, exact, limit);
    }

    // ── 이의제기 ──────────────────────────────────────────────────────────

    /** 상태별 전체 건수 — pending · accepted · rejected */
    public long[] appealCounts() {
        long[] out = new long[3];
        jdbc.query("SELECT status, count(*) FROM sanction_appeals GROUP BY status", rs -> {
            AppealStatus status = AppealStatus.valueOf(rs.getString(1));
            out[status.ordinal()] = rs.getLong(2);
        });
        return out;
    }

    public long pendingAppeals() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sanction_appeals WHERE status = 'PENDING'", Long.class);
        return n == null ? 0 : n;
    }

    public long appealTotal(AppealStatus status) {
        Long n = status == null ? jdbc.queryForObject("SELECT count(*) FROM sanction_appeals", Long.class)
                : jdbc.queryForObject("SELECT count(*) FROM sanction_appeals WHERE status = ?", Long.class, status.name());
        return n == null ? 0 : n;
    }

    public List<AppealRow> appealPage(AppealStatus status, boolean newest, int page, int size) {
        String order = newest ? " ORDER BY a.created_at DESC, a.id DESC" : " ORDER BY a.created_at, a.id";
        return status == null
                ? jdbc.query(MemberSanctionStore.APPEAL_SELECT + order + " LIMIT ? OFFSET ?", MemberSanctionStore::appeal, size, (long) page * size)
                : jdbc.query(MemberSanctionStore.APPEAL_SELECT + " WHERE a.status = ?" + order + " LIMIT ? OFFSET ?", MemberSanctionStore::appeal, status.name(), size,
                (long) page * size);
    }

    /** 대기 이의제기 오래된 차례(대시보드) — 색인 {@code ix_sanction_appeals_pending} */
    public List<PendingAppealRow> oldestPending(int limit) {
        return jdbc.query("SELECT a.id, p.nickname, s.kind, s.warning_no, a.created_at FROM sanction_appeals a JOIN member_sanctions s ON s.id = a.sanction_id "
                        + "LEFT JOIN profiles p ON p.user_id = a.user_id WHERE a.status = 'PENDING' ORDER BY a.created_at, a.id LIMIT ?",
                (rs, i) -> new PendingAppealRow(rs.getLong(1), rs.getString(2), SanctionKind.valueOf(rs.getString(3)), nullableInt(rs.getInt(4), rs.wasNull()),
                        MemberSanctionStore.instant(rs, 5)), limit);
    }

    /** 진행 중인 정지·영구 정지 수(대시보드) — 부분 색인 */
    public long running(Instant now) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM member_sanctions s WHERE " + RUNNING, Long.class, ts(now));
        return n == null ? 0 : n;
    }

    private static Integer nullableInt(int value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private static Object ts(Instant at) {
        return MemberSanctionStore.ts(at);
    }
}
