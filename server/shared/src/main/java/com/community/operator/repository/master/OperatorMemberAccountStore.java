package com.community.operator.repository.master;

import com.community.board.entity.QBoardStaffEntity;
import com.community.business.entity.BusinessStatus;
import com.community.business.entity.QBusinessProfileEntity;
import com.community.operator.entity.OperatorMemberAccountEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.operator.entity.QOperatorMemberAccountEntity;
import com.community.role.entity.MemberRoleIds;
import com.community.role.entity.QUserRoleEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.jpa.RowLocks;
import com.community.zconfiguration.jpa.UniqueViolations;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 운영자 회원 계정 연결({@code operator_member_accounts} — shared V141) 읽기·쓰기와 연결 후보 판정 재료. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이 기본이다 — 「힘이 살아 있나」(정책 6)를 운영자 상태 + 회원 상태로 <b>한 문장</b>에 판정하고(요청마다 — 캐시 없음, 판정은 {@link CaseBuilder} 불리언 칸), 목록 작성자 표시는
 * 회원 ID 묶음 한 질의로 고른다. 운영자 판정({@link #operatorOk})은 {@code OperatorEntity#canLogin} 과 같은 규칙이다(이중 구현 — 한쪽을 고치면 다른 쪽도 고친다).
 * 회원 행 쓰기 잠금은 회원 표만 고르는 한 문장({@code FOR NO KEY UPDATE}).
 * Native SQL 로 남긴 것과 이유: 연결 끊기·게시판 담당 끝내기({@code DELETE … RETURNING} — 지운 줄과 돌려줄 값이 같은 문장의 결과여야 한다).
 */
@Repository
public class OperatorMemberAccountStore extends RoboyamRepositoryQDSL {

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

    private static final QOperatorMemberAccountEntity M = QOperatorMemberAccountEntity.operatorMemberAccountEntity;
    private static final QOperatorEntity O = new QOperatorEntity("linkedOperator");
    private static final QOperatorEntity LO = new QOperatorEntity("linkingOperator");
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity P = QProfileEntity.profileEntity;
    private static final QUserRoleEntity ROLE = QUserRoleEntity.userRoleEntity;
    private static final QBusinessProfileEntity BIZ = QBusinessProfileEntity.businessProfileEntity;
    private static final QBoardStaffEntity STAFF = QBoardStaffEntity.boardStaffEntity;

    private final OperatorMemberAccountRepository accounts;
    private final EntityManager entityManager;

    public OperatorMemberAccountStore(JPAQueryFactory query, OperatorMemberAccountRepository accounts, EntityManager entityManager) {
        super(query);
        this.accounts = accounts;
        this.entityManager = entityManager;
    }

    // ── 판정 ──────────────────────────────────────────────────────────────

    /** 운영자를 지금 쓸 수 있다 — 승인됨 · 잠기지 않음 · 만료 표시 없음 · 사용 중 · 기간 안({@code OperatorEntity#canLogin}) */
    public static BooleanExpression operatorOk(QOperatorEntity o, Instant now) {
        return o.approved.isTrue().and(o.locked.isFalse()).and(o.expired.isFalse()).and(o.enabled.isTrue())
                .and(o.accountStartAt.isNull().or(o.accountStartAt.loe(now))).and(o.accountEndAt.isNull().or(o.accountEndAt.goe(now)));
    }

    /** 회원 상태 정상 — 정지·영구 정지·탈퇴 진행·탈퇴가 아님 */
    public static BooleanExpression memberOk(QUserEntity u) {
        return u.status.eq(UserStatus.ACTIVE).and(u.deletedAt.isNull());
    }

    /** 조건을 참·거짓 칸으로 */
    private static BooleanExpression flag(BooleanExpression condition) {
        return Expressions.asBoolean(new CaseBuilder().when(condition).then(true).otherwise(false));
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    /** 그 회원의 연결(없으면 빈 값) */
    public Optional<Link> linkOfUser(long userId, Instant now) {
        return linkRows(now).where(M.userId.eq(userId)).fetch().stream().findFirst().map(t -> link(t, now));
    }

    /** 그 운영자의 연결(없으면 빈 값) */
    public Optional<Link> linkOfOperator(long operatorId, Instant now) {
        return linkRows(now).where(M.operatorId.eq(operatorId)).fetch().stream().findFirst().map(t -> link(t, now));
    }

    /** 운영자 여럿의 연결(운영자 ID → 연결) — 운영자 목록 이름 옆 딱지 */
    public Map<Long, Link> linksOfOperators(Collection<Long> operatorIds, Instant now) {
        List<Long> ids = operatorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Link> out = new HashMap<>();
        linkRows(now).where(M.operatorId.in(ids)).fetch().forEach(t -> {
            Link l = link(t, now);
            out.put(l.operatorId(), l);
        });
        return out;
    }

    /** 이어져 있나(힘과 상관없이 — 탈퇴 막기 · 게시판 담당 자격) */
    public boolean linked(long userId) {
        return query.selectOne().from(M).where(M.userId.eq(userId)).fetchFirst() != null;
    }

    /** 이어져 있는 회원 ID 중에서(힘과 상관없이) — 게시판 담당 후보 목록 */
    public Set<Long> linkedUserIds(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(query.select(M.userId).from(M).where(M.userId.in(ids)).fetch());
    }

    /** 힘이 살아 있는 연결의 회원 ID — {@code userIds} 가 null 이면 전부(연결은 운영자 수만큼이라 작다) */
    public Set<Long> activeUserIds(Collection<Long> userIds, Instant now) {
        JPAQuery<Long> select = query.select(M.userId).from(M).join(O).on(O.id.eq(M.operatorId)).join(U).on(U.id.eq(M.userId)).where(operatorOk(O, now), memberOk(U));
        if (userIds == null) {
            return Set.copyOf(select.fetch());
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(select.where(M.userId.in(ids)).fetch());
    }

    /** 이 이메일(정규화한 소문자)로 가입한 회원 — 연결 후보(정책 2). 없으면 빈 값. 색인 {@code ux_users_email} */
    public Optional<Candidate> candidateByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        BooleanExpression business = JPAExpressions.selectOne().from(ROLE).where(ROLE.userId.eq(U.id), ROLE.boardId.isNull(), ROLE.roleId.eq(MemberRoleIds.BUSINESS))
                .exists()
                .or(JPAExpressions.selectOne().from(BIZ)
                        .where(BIZ.userId.eq(U.id), BIZ.status.in(BusinessStatus.PENDING, BusinessStatus.APPROVED, BusinessStatus.SUSPENDED)).exists());
        Expression<Long> managers = JPAExpressions.select(STAFF.count()).from(STAFF).where(STAFF.userId.eq(U.id), STAFF.role.eq("MANAGER"));
        Expression<Long> staff = JPAExpressions.select(STAFF.count()).from(STAFF).where(STAFF.userId.eq(U.id), STAFF.role.eq("STAFF"));
        BooleanExpression businessFlag = flag(business);
        List<Tuple> rows = query.select(U.id, P.nickname, P.gradeKey, U.createdAt, U.status, U.emailVerifiedAt, U.deletedAt, businessFlag, M.operatorId, O.operatorName,
                        managers, staff)
                .from(U).leftJoin(P).on(P.userId.eq(U.id)).leftJoin(M).on(M.userId.eq(U.id)).leftJoin(O).on(O.id.eq(M.operatorId))
                .where(U.email.eq(normalizedEmail)).fetch();
        return rows.stream().findFirst().map(t -> {
            UserStatus status = t.get(U.status);
            return new Candidate(t.get(U.id), t.get(P.nickname), t.get(P.gradeKey), t.get(U.createdAt), status == null ? null : status.name(),
                    t.get(U.emailVerifiedAt) != null, t.get(U.deletedAt) != null, Boolean.TRUE.equals(t.get(businessFlag)), t.get(M.operatorId),
                    t.get(O.operatorName), count(t.get(managers)), count(t.get(staff)));
        });
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    /** 회원 행을 쓰기 잠금으로 — 연결 · 탈퇴 신청 · 게시판 담당 지정이 줄을 선다. 없으면 false. 회원 표만 고르는 한 문장({@code FOR NO KEY UPDATE}) */
    public boolean lockUser(long userId) {
        return RowLocks.forUpdate(entityManager, UserEntity.class, userId);
    }

    public void insert(long operatorId, long userId, Instant linkedAt, Long linkedBy) {
        UniqueViolations.inserting(() -> {
            accounts.persist(OperatorMemberAccountEntity.link(operatorId, userId, linkedAt, linkedBy));
            accounts.flush();
        });
    }

    /**
     * 그 운영자의 연결을 지운다 — 지운 회원 ID(없었으면 빈 값).
     *
     * <p>Native SQL 인 이유: {@code DELETE … RETURNING} — 지운 줄과 돌려줄 회원이 같은 문장의 결과여야 한다(나누면 그 사이 바뀐 연결이 어긋난다).
     */
    public Optional<Long> delete(long operatorId) {
        return entityManager.createNativeQuery("DELETE FROM operator_member_accounts WHERE operator_id = ?1 RETURNING user_id").setParameter(1, operatorId)
                .getResultList().stream().findFirst().map(v -> ((Number) v).longValue());
    }

    /**
     * 그 회원의 게시판 매니저·스텝 지정을 모두 끝낸다(정책 4 — 행 삭제). 끝난 게시판 ID.
     *
     * <p>Native SQL 인 이유: {@code DELETE … RETURNING} — 지운 지정과 감사에 남길 게시판이 같은 문장의 결과여야 한다.
     */
    public List<Long> releaseBoardStaff(long userId) {
        List<?> rows = entityManager.createNativeQuery("DELETE FROM board_staff WHERE user_id = ?1 RETURNING board_id").setParameter(1, userId).getResultList();
        return rows.stream().map(v -> ((Number) v).longValue()).sorted().toList();
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 연결 한 줄 질의 — 판정 칸 둘(운영자 · 회원)을 함께. 거르기는 부르는 쪽이 붙인다 */
    private JPAQuery<Tuple> linkRows(Instant now) {
        return query.select(M.operatorId, O.operatorName, O.root, M.userId, P.nickname, P.gradeKey, M.linkedAt, M.linkedBy, LO.operatorName, flag(operatorOk(O, now)),
                        flag(memberOk(U)))
                .from(M).join(O).on(O.id.eq(M.operatorId)).join(U).on(U.id.eq(M.userId)).leftJoin(P).on(P.userId.eq(M.userId)).leftJoin(LO).on(LO.id.eq(M.linkedBy));
    }

    private static Link link(Tuple t, Instant now) {
        return new Link(t.get(M.operatorId), t.get(O.operatorName), Boolean.TRUE.equals(t.get(O.root)), t.get(M.userId), t.get(P.nickname), t.get(P.gradeKey),
                t.get(M.linkedAt), t.get(M.linkedBy), t.get(LO.operatorName), Boolean.TRUE.equals(t.get(9, Boolean.class)), Boolean.TRUE.equals(t.get(10, Boolean.class)));
    }

    private static int count(Long value) {
        return value == null ? 0 : value.intValue();
    }
}
