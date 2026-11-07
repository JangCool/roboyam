package com.community.restriction.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.restriction.entity.MemberRestrictionEntity;
import com.community.restriction.entity.QMemberRestrictionEntity;
import com.community.restriction.entity.RestrictionSource;
import com.community.restriction.entity.RestrictionType;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 기능 제한 표({@code member_restrictions}, shared V84) 읽기·쓰기 — {@code MemberRestrictionService}·{@code WithdrawalRejoinPolicy} 가 부른다. 트랜잭션은 부르는 서비스가 연다.
 * 판정 시각은 DB 의 {@code now()} 가 아니라 서비스가 넘기는 지금이다(시험이 시각을 옮길 수 있게).
 *
 * <p>QueryDSL 이 기본이다(넣기는 {@link MemberRestrictionRepository#persist}, 해제는 조건부 대량 UPDATE — 진행 중일 때만 한 번 이긴다). Native SQL 로 남긴 것과 이유:
 * <ul>
 *   <li>{@link #hasActive} — 포인트 적립이 JDBC 세이브포인트({@code IsolatedSideWork}) 안에서 부른다. 그 안에서 JPA 질의가 실패하면 업무 트랜잭션 전체가 롤백 전용이 된다.</li>
 *   <li>{@link #lockMember} — {@code pg_advisory_xact_lock}(회원별 자동 부여를 줄 세운다).</li>
 * </ul>
 */
@Repository
public class MemberRestrictionStore extends RoboyamRepositoryQDSL {

    private static final QMemberRestrictionEntity R = QMemberRestrictionEntity.memberRestrictionEntity;
    private static final QOperatorEntity CREATED = new QOperatorEntity("createdOperator");
    private static final QOperatorEntity LIFTED = new QOperatorEntity("liftedOperator");
    private static final QUserEntity USER = QUserEntity.userEntity;

    /** 진행 중 조건 — {@link #ACTIVE_SQL} · {@link Row#activeAt} 과 같다 */
    static BooleanExpression active(Instant now) {
        return R.liftedAt.isNull().and(R.startsAt.loe(now)).and(R.until.isNull().or(R.until.gt(now)));
    }

    /** 진행 중 조건의 SQL 글자(세이브포인트 경로의 JDBC 판정, 인자: 지금, 지금) */
    static final String ACTIVE_SQL = "r.lifted_at IS NULL AND r.starts_at <= ? AND (r.until IS NULL OR r.until > ?)";

    /** 권고 잠금의 이름 공간(회원별 자동 부여를 줄 세운다) */
    private static final int LOCK_NAMESPACE = 7_412;

    /** 기능 제한 한 줄(부여·해제한 운영자 이름 포함 — 운영자가 지워졌으면 이름은 null) */
    public record Row(long id, long userId, RestrictionType type, String reason, String adminMemo, Instant startsAt, Instant until, RestrictionSource source,
                      String autoRule, Long createdBy, String createdByName, Instant createdAt, Long liftedBy, String liftedByName, Instant liftedAt,
                      String liftReason) {

        /** {@code now} 에 진행 중인가 — 저장소 조건 {@link #active} 와 같다 */
        public boolean activeAt(Instant now) {
            return liftedAt == null && !startsAt.isAfter(now) && (until == null || until.isAfter(now));
        }
    }

    private final MemberRestrictionRepository restrictions;
    private final JdbcTemplate jdbc;

    public MemberRestrictionStore(JPAQueryFactory query, MemberRestrictionRepository restrictions, DataSource dataSource) {
        super(query);
        this.restrictions = restrictions;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 넣는다 — 그 자리에서 내보낸다(뒤의 JDBC 판정 {@link #hasActive} 가 같은 트랜잭션에서 보도록, JDBC 시절과 같은 차례) */
    public void insert(long id, long userId, RestrictionType type, String reason, String adminMemo, Instant startsAt, Instant until, RestrictionSource source,
                       String autoRule, Long createdBy, Instant createdAt) {
        restrictions.persist(MemberRestrictionEntity.grant(id, userId, type, reason, adminMemo, startsAt, until, source, autoRule, createdBy, createdAt));
        restrictions.flush();
    }

    public Optional<Row> find(long id) {
        return Optional.ofNullable(rows().where(R.id.eq(id)).fetchOne()).map(MemberRestrictionStore::row);
    }

    /** 그 회원의 모든 제한 — 진행 중 먼저, 그다음 최근에 만든 것부터 */
    public List<Row> list(long userId, Instant now) {
        return rows().where(R.userId.eq(userId))
                .orderBy(new CaseBuilder().when(active(now)).then(1).otherwise(0).desc(), R.createdAt.desc(), R.id.desc())
                .fetch().stream().map(MemberRestrictionStore::row).toList();
    }

    /** 그 회원의 진행 중인 제한(종류 무관) — 늦게 끝나는 것부터(무기한 먼저) */
    public List<Row> active(long userId, Instant now) {
        return rows().where(R.userId.eq(userId), active(now)).orderBy(R.until.desc().nullsFirst(), R.createdAt.desc(), R.id.desc())
                .fetch().stream().map(MemberRestrictionStore::row).toList();
    }

    /**
     * 그 종류가 진행 중인가.
     *
     * <p>Native SQL(JDBC) 인 이유: 포인트 적립({@code PointService})이 JDBC 세이브포인트 안에서 부른다 — 그 안의 JPA 질의가 실패하면 업무 트랜잭션이 롤백 전용이 된다.
     */
    public boolean hasActive(long userId, RestrictionType type, Instant now) {
        return !jdbc.queryForList("SELECT 1 FROM member_restrictions r WHERE r.user_id = ? AND r.type = ? AND " + ACTIVE_SQL + " LIMIT 1", Integer.class, userId,
                type.name(), ts(now), ts(now)).isEmpty();
    }

    /** 종류 무관 진행 중인 제한이 있는가 */
    public boolean hasAnyActive(long userId, Instant now) {
        return query.selectOne().from(R).where(R.userId.eq(userId), active(now)).fetchFirst() != null;
    }

    /**
     * 진행 중인 제한을 해제한다(조건부 — 이미 해제됐거나 기간이 끝났으면 바꾸지 않는다).
     *
     * @return 해제했으면 true
     */
    public boolean lift(long id, long userId, long operatorId, String reason, Instant now) {
        return query.update(R).set(R.liftedAt, now).set(R.liftedBy, operatorId).set(R.liftReason, reason)
                .where(R.id.eq(id), R.userId.eq(userId), active(now)).execute() == 1;
    }

    /**
     * 그 회원의 자동 부여를 줄 세운다(트랜잭션이 끝나면 풀린다).
     *
     * <p>Native SQL 인 이유: {@code pg_advisory_xact_lock} 은 DB 함수다.
     */
    public void lockMember(long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", rs -> null, LOCK_NAMESPACE, Long.toString(userId));
    }

    /**
     * 회원 상태 — 없으면 빈 값, 탈퇴(WITHDRAWN)했거나 지워졌으면 {@code true}.
     */
    public Optional<Boolean> memberGone(long userId) {
        Tuple t = query.select(USER.status, USER.deletedAt).from(USER).where(USER.id.eq(userId)).fetchOne();
        return t == null ? Optional.empty() : Optional.of(t.get(USER.status) == UserStatus.WITHDRAWN || t.get(USER.deletedAt) != null);
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> rows() {
        return query.select(R.id, R.userId, R.type, R.reason, R.adminMemo, R.startsAt, R.until, R.source, R.autoRule, R.createdBy, CREATED.operatorName, R.createdAt,
                        R.liftedBy, LIFTED.operatorName, R.liftedAt, R.liftReason)
                .from(R).leftJoin(CREATED).on(CREATED.id.eq(R.createdBy)).leftJoin(LIFTED).on(LIFTED.id.eq(R.liftedBy));
    }

    private static Row row(Tuple t) {
        return new Row(t.get(R.id), t.get(R.userId), t.get(R.type), t.get(R.reason), t.get(R.adminMemo), t.get(R.startsAt), t.get(R.until), t.get(R.source),
                t.get(R.autoRule), t.get(R.createdBy), t.get(CREATED.operatorName), t.get(R.createdAt), t.get(R.liftedBy), t.get(LIFTED.operatorName),
                t.get(R.liftedAt), t.get(R.liftReason));
    }

    private static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
