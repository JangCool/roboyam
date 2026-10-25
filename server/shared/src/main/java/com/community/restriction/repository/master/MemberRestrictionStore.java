package com.community.restriction.repository.master;

import com.community.restriction.entity.RestrictionSource;
import com.community.restriction.entity.RestrictionType;
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
 * 기능 제한 표({@code member_restrictions}, shared V62) 읽기·쓰기 — {@code MemberRestrictionService} 만 부른다.
 *
 * <p>JDBC(native SQL)인 이유: 「진행 중」 판정이 막힌 기능 확인·포인트 적립(세이브포인트 안 — 영속성 컨텍스트를 거치지 않아야 한다, {@code IsolatedSideWork})에 함께 쓰이고,
 * 해제는 조건부 UPDATE(진행 중일 때만) 한 문장으로 한 번만 이기며, 자동 부여는 회원별 권고 잠금({@code pg_advisory_xact_lock})으로 줄을 세운다. 트랜잭션은 부르는 서비스가 연다.
 * 판정 시각은 DB 의 {@code now()} 가 아니라 서비스가 넘기는 지금이다(시험이 시각을 옮길 수 있게).
 */
@Repository
public class MemberRestrictionStore {

    /** 진행 중 조건(인자: 지금, 지금) */
    static final String ACTIVE = "r.lifted_at IS NULL AND r.starts_at <= ? AND (r.until IS NULL OR r.until > ?)";

    /** 권고 잠금의 이름 공간(회원별 자동 부여를 줄 세운다) */
    private static final int LOCK_NAMESPACE = 7_412;

    /** 기능 제한 한 줄(부여·해제한 운영자 이름 포함 — 운영자가 지워졌으면 이름은 null) */
    public record Row(long id, long userId, RestrictionType type, String reason, String adminMemo, Instant startsAt, Instant until, RestrictionSource source,
                      String autoRule, Long createdBy, String createdByName, Instant createdAt, Long liftedBy, String liftedByName, Instant liftedAt,
                      String liftReason) {

        /** {@code now} 에 진행 중인가 — 저장소 조건 {@link #ACTIVE} 와 같다 */
        public boolean activeAt(Instant now) {
            return liftedAt == null && !startsAt.isAfter(now) && (until == null || until.isAfter(now));
        }
    }

    private static final String COLUMNS = "r.id, r.user_id, r.type, r.reason, r.admin_memo, r.starts_at, r.until, r.source, r.auto_rule, r.created_by, c.operator_name, "
            + "r.created_at, r.lifted_by, l.operator_name, r.lifted_at, r.lift_reason";
    private static final String FROM = " FROM member_restrictions r LEFT JOIN operators c ON c.id = r.created_by LEFT JOIN operators l ON l.id = r.lifted_by";

    private final JdbcTemplate jdbc;

    public MemberRestrictionStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(long id, long userId, RestrictionType type, String reason, String adminMemo, Instant startsAt, Instant until, RestrictionSource source,
                       String autoRule, Long createdBy, Instant createdAt) {
        jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, admin_memo, starts_at, until, source, auto_rule, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, userId, type.name(), reason, adminMemo, ts(startsAt), ts(until), source.name(), autoRule, createdBy,
                ts(createdAt));
    }

    public Optional<Row> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE r.id = ?", MemberRestrictionStore::row, id).stream().findFirst();
    }

    /** 그 회원의 모든 제한 — 진행 중 먼저, 그다음 최근에 만든 것부터 */
    public List<Row> list(long userId, Instant now) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE r.user_id = ? ORDER BY (" + ACTIVE + ") DESC, r.created_at DESC, r.id DESC", MemberRestrictionStore::row,
                userId, ts(now), ts(now));
    }

    /** 그 회원의 진행 중인 제한(종류 무관) — 늦게 끝나는 것부터(무기한 먼저) */
    public List<Row> active(long userId, Instant now) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE r.user_id = ? AND " + ACTIVE + " ORDER BY r.until DESC NULLS FIRST, r.created_at DESC, r.id DESC",
                MemberRestrictionStore::row, userId, ts(now), ts(now));
    }

    /** 그 종류가 진행 중인가 */
    public boolean hasActive(long userId, RestrictionType type, Instant now) {
        return !jdbc.queryForList("SELECT 1 FROM member_restrictions r WHERE r.user_id = ? AND r.type = ? AND " + ACTIVE + " LIMIT 1", Integer.class, userId,
                type.name(), ts(now), ts(now)).isEmpty();
    }

    /** 종류 무관 진행 중인 제한이 있는가 */
    public boolean hasAnyActive(long userId, Instant now) {
        return !jdbc.queryForList("SELECT 1 FROM member_restrictions r WHERE r.user_id = ? AND " + ACTIVE + " LIMIT 1", Integer.class, userId, ts(now), ts(now))
                .isEmpty();
    }

    /**
     * 진행 중인 제한을 해제한다(조건부 — 이미 해제됐거나 기간이 끝났으면 바꾸지 않는다).
     *
     * @return 해제했으면 true
     */
    public boolean lift(long id, long userId, long operatorId, String reason, Instant now) {
        return jdbc.update("UPDATE member_restrictions r SET lifted_at = ?, lifted_by = ?, lift_reason = ? WHERE r.id = ? AND r.user_id = ? AND " + ACTIVE,
                ts(now), operatorId, reason, id, userId, ts(now), ts(now)) == 1;
    }

    /** 그 회원의 자동 부여를 줄 세운다(트랜잭션이 끝나면 풀린다) */
    public void lockMember(long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", rs -> null, LOCK_NAMESPACE, Long.toString(userId));
    }

    /**
     * 회원 상태 — 없으면 빈 값, 탈퇴(WITHDRAWN)했거나 지워졌으면 {@code true}.
     */
    public Optional<Boolean> memberGone(long userId) {
        return jdbc.query("SELECT status = 'WITHDRAWN' OR deleted_at IS NOT NULL FROM users WHERE id = ?", (rs, i) -> rs.getBoolean(1), userId).stream().findFirst();
    }

    private static Row row(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getLong(1), rs.getLong(2), RestrictionType.valueOf(rs.getString(3)), rs.getString(4), rs.getString(5), instant(rs, 6), instant(rs, 7),
                RestrictionSource.valueOf(rs.getString(8)), rs.getString(9), nullableLong(rs, 10), rs.getString(11), instant(rs, 12), nullableLong(rs, 13),
                rs.getString(14), instant(rs, 15), rs.getString(16));
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
