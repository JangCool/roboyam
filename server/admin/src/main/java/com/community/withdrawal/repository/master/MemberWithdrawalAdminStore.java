package com.community.withdrawal.repository.master;

import com.community.group.entity.QGroupEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 탈퇴 관리 읽기(admin-web {@code /members/withdrawals}, member-withdrawal-contract 「관리자」) — 목록(거르기·정렬·쪽)·요약 4칸·사유 분포와 이름 찾기(닉네임·운영자·소모임).
 * 쓰기는 shared {@code WithdrawalRequestWriter}·{@code MemberWithdrawalStore}. 줄 모양은 shared {@link MemberWithdrawalStore.Row} 를 그대로 쓴다.
 * 이름 찾기(운영자·닉네임·소모임)는 엔티티로 읽고, 탈퇴 신청 표({@code member_withdrawals})는 엔티티가 없어 목록·요약·분포가 네이티브 SQL 이다.
 *
 * <p>인덱스(V132·V134): 상태 탭 + 신청일 정렬 {@code ix_member_withdrawals_status_requested}, 전체 신청일 {@code ix_member_withdrawals_requested}, 완료일(요약 오늘·이번 달, 완료 탭)
 * {@code ix_member_withdrawals_completed}, 취소일 {@code ix_member_withdrawals_canceled}, 진행 중·10일 넘긴 진행 중 {@code ix_member_withdrawals_scheduled}, 회원별 {@code ix_member_withdrawals_user}.
 */
@Repository
public class MemberWithdrawalAdminStore extends RoboyamRepositoryQDSL {

    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;

    /** 목록 조건 — 비어 있는(null) 조건은 거르지 않는다. 기간은 [from, to) */
    public record Criteria(WithdrawalStatus status, Long userId, Instant requestedFrom, Instant requestedTo, Instant completedFrom, Instant completedTo, String sortColumn,
                           boolean ascending, int page, int size) {
    }

    /** 목록 한 줄 — 신청 줄 + 닉네임(프로필, 익명화됐으면 null) */
    public record Listed(MemberWithdrawalStore.Row row, String nickname) {
    }

    /** 정렬할 수 있는 칸 — 화면 이름 → 칸 */
    public static final Map<String, String> SORTS = Map.of("requestedAt", "requested_at", "expectedCompleteAt", "expected_complete_at", "completedAt", "completed_at",
            "canceledAt", "canceled_at", "pendingCount", "pending_count");

    private static final String SELECT = "SELECT " + prefixed(MemberWithdrawalStore.COLUMNS) + ", p.nickname FROM member_withdrawals w LEFT JOIN profiles p ON p.user_id = w.user_id ";

    private final NamedParameterJdbcTemplate named;

    public MemberWithdrawalAdminStore(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.named = new NamedParameterJdbcTemplate(dataSource);
    }

    public List<Listed> search(Criteria c) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String order = "w." + c.sortColumn() + (c.ascending() ? " ASC NULLS LAST" : " DESC NULLS LAST") + ", w.id DESC";
        params.addValue("limit", c.size()).addValue("offset", (long) c.page() * c.size());
        return named.query(SELECT + where(c, params) + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params,
                (rs, i) -> new Listed(MemberWithdrawalStore.row(rs, i), rs.getString("nickname")));
    }

    public long count(Criteria c) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long total = named.queryForObject("SELECT count(*) FROM member_withdrawals w " + where(c, params), params, Long.class);
        return total == null ? 0 : total;
    }

    /** 신청 줄 + 닉네임 한 건(없으면 null) */
    public Listed find(long id) {
        return named.query(SELECT + "WHERE w.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> new Listed(MemberWithdrawalStore.row(rs, i), rs.getString("nickname")))
                .stream().findFirst().orElse(null);
    }

    /** 그 회원의 가장 최근 신청(없으면 null) */
    public Listed latestOf(long userId) {
        return named.query(SELECT + "WHERE w.user_id = :userId ORDER BY w.requested_at DESC, w.id DESC LIMIT 1", new MapSqlParameterSource("userId", userId),
                (rs, i) -> new Listed(MemberWithdrawalStore.row(rs, i), rs.getString("nickname"))).stream().findFirst().orElse(null);
    }

    /**
     * 요약 4칸 — 모두 인덱스로만 센다.
     *
     * @param todayStart    서비스 하루의 0시(완료 ≥ 이 시각 = 오늘 완료)
     * @param monthStart    서비스 달의 1일 0시(완료 ≥ 이 시각 = 이번 달 완료)
     * @param overdueBefore 이 시각 이전에 신청한 진행 중 건 = 10일 넘긴 진행 중
     * @return {진행 중, 오늘 완료, 이번 달 완료, 10일 넘긴 진행 중}
     */
    public long[] summary(Instant todayStart, Instant monthStart, Instant overdueBefore) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("today", ts(todayStart)).addValue("month", ts(monthStart)).addValue("overdue", ts(overdueBefore));
        return named.queryForObject("SELECT (SELECT count(*) FROM member_withdrawals WHERE status = 'SCHEDULED'), "
                + "(SELECT count(*) FROM member_withdrawals WHERE status = 'COMPLETED' AND completed_at >= :today), "
                + "(SELECT count(*) FROM member_withdrawals WHERE status = 'COMPLETED' AND completed_at >= :month), "
                + "(SELECT count(*) FROM member_withdrawals WHERE status = 'SCHEDULED' AND requested_at <= :overdue)", params,
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)});
    }

    /** 사유별 건수(신청일 [from, to), 상태 무관) — 고르지 않음은 키 null */
    public Map<String, Long> reasonCounts(Instant from, Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT reason, count(*) FROM member_withdrawals WHERE true");
        if (from != null) {
            sql.append(" AND requested_at >= :from");
            params.addValue("from", ts(from));
        }
        if (to != null) {
            sql.append(" AND requested_at < :to");
            params.addValue("to", ts(to));
        }
        sql.append(" GROUP BY reason");
        Map<String, Long> out = new HashMap<>();
        named.query(sql.toString(), params, (ResultSet rs) -> {
            out.put(rs.getString(1), rs.getLong(2));
        });
        return out;
    }

    /** 운영자 이름(지워진 운영자는 빠진다) */
    public Map<Long, String> operatorNames(Collection<Long> ids) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(OPERATOR.id, OPERATOR.operatorName).from(OPERATOR).where(OPERATOR.id.in(Set.copyOf(ids))).fetch()) {
            out.put(t.get(OPERATOR.id), t.get(OPERATOR.operatorName));
        }
        return out;
    }

    /** 회원 닉네임(익명화됐으면 null 값 — 키는 있다) */
    public Map<Long, String> nicknames(Collection<Long> userIds) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(PROFILE.userId, PROFILE.nickname).from(PROFILE).where(PROFILE.userId.in(Set.copyOf(userIds))).fetch()) {
            out.put(t.get(PROFILE.userId), t.get(PROFILE.nickname));
        }
        return out;
    }

    /** 소모임 이름(지워진 소모임도 줄은 남아 이름이 있다) */
    public Map<Long, String> groupNames(Collection<Long> groupIds) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(GROUP.id, GROUP.name).from(GROUP).where(GROUP.id.in(Set.copyOf(groupIds))).fetch()) {
            out.put(t.get(GROUP.id), t.get(GROUP.name));
        }
        return out;
    }

    private static String where(Criteria c, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("WHERE true");
        if (c.status() != null) {
            sql.append(" AND w.status = :status");
            params.addValue("status", c.status().name());
        }
        if (c.userId() != null) {
            sql.append(" AND w.user_id = :userId");
            params.addValue("userId", c.userId());
        }
        range(sql, params, "w.requested_at", "rf", c.requestedFrom(), "rt", c.requestedTo());
        range(sql, params, "w.completed_at", "cf", c.completedFrom(), "ct", c.completedTo());
        return sql.toString();
    }

    private static void range(StringBuilder sql, MapSqlParameterSource params, String column, String fromName, Instant from, String toName, Instant to) {
        if (from != null) {
            sql.append(" AND ").append(column).append(" >= :").append(fromName);
            params.addValue(fromName, ts(from));
        }
        if (to != null) {
            sql.append(" AND ").append(column).append(" < :").append(toName);
            params.addValue(toName, ts(to));
        }
    }

    /** {@code MemberWithdrawalStore.COLUMNS} 의 칸마다 {@code w.} 를 붙인다({@code ::text} 캐스트는 그대로) */
    private static String prefixed(String columns) {
        StringBuilder out = new StringBuilder();
        for (String column : columns.split(",\\s*")) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append("w.").append(column);
        }
        return out.toString();
    }

    private static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
