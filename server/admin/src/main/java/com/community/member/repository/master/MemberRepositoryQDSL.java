package com.community.member.repository.master;

import com.community.grade.entity.GradeRoles;
import com.community.grade.entity.QGradeEntity;
import com.community.grade.entity.QMemberGradeChangeEntity;
import com.community.member.dto.MemberGradeChangeRow;
import com.community.member.dto.MemberRecord;
import com.community.member.dto.MemberSearchCriteria;
import com.community.member.dto.MemberSearchCriteria.AuthMethod;
import com.community.member.dto.UserRoleRow;
import com.community.operator.entity.QOperatorEntity;
import com.community.role.entity.MemberRoleIds;
import com.community.role.entity.QRoleEntity;
import com.community.role.entity.QUserRoleEntity;
import com.community.social.entity.SocialProvider;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.QUserIdentityEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 회원 목록 조회(필터·정렬·쪽 나누기)와 목록 한 쪽의 역할·소셜 연결 읽기. 정렬·집계·페이징은 DB 에서 한다. <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>조건이 파생 값을 가리킬 때의 뜻: 회원 유형은 사업자 회원 역할({@code user_roles})이 있으면 사업자·없으면 일반, 등급 역할은 프로필의 등급, 일반 회원 역할은 탈퇴하지 않았고 사업자가 아닌 회원,
 * 가입 방식 이메일은 비밀번호가 있는 계정·소셜은 그 연결이 있는 계정, 상태 변경 시각은 탈퇴 시각(정지·휴면 이력 표가 아직 없다).
 * 휴면·포인트·제재 이력은 데이터가 없어 그 조건을 걸면 해당 회원이 없는 것으로 판정한다.
 */
@Repository
public class MemberRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QGradeEntity GRADE = QGradeEntity.gradeEntity;
    private static final QMemberGradeChangeEntity GRADE_CHANGE = QMemberGradeChangeEntity.memberGradeChangeEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;
    private static final QUserRoleEntity USER_ROLE = QUserRoleEntity.userRoleEntity;
    private static final QRoleEntity ROLE = QRoleEntity.roleEntity;
    private static final QUserIdentityEntity IDENTITY = QUserIdentityEntity.userIdentityEntity;

    /** 어떤 회원도 맞지 않는 조건 — 데이터가 아직 없는 항목(휴면·제재·포인트)을 조건으로 걸었을 때 */
    private static final BooleanExpression NEVER = Expressions.booleanTemplate("1 = 0");

    public MemberRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 조건에 맞는 회원 수 */
    public long count(MemberSearchCriteria criteria, boolean canPii) {
        Long count = baseQuery().select(USER.count()).where(where(criteria, canPii)).fetchOne();
        return count == null ? 0 : count;
    }

    /** 조건에 맞는 회원 한 쪽(정렬 적용). 정렬 칸이 같으면 회원번호 순으로 가른다 */
    public List<MemberRecord> search(MemberSearchCriteria criteria, boolean canPii) {
        return baseQuery()
                .select(Projections.constructor(MemberRecord.class, USER.id, USER.email, PROFILE.nickname, PROFILE.gradeKey, USER.status,
                        new CaseBuilder().when(USER.passwordHash.isNotNull()).then(true).otherwise(false), USER.emailVerifiedAt, USER.createdAt,
                        USER.lastLoginAt, USER.deletedAt))
                .where(where(criteria, canPii))
                .orderBy(orderOf(criteria))
                .offset((long) criteria.page() * criteria.size()).limit(criteria.size())
                .fetch();
    }

    /** 한 회원의 기본 줄(상세용). 없으면 비어 있다 */
    public Optional<MemberRecord> findRecord(long userId) {
        return Optional.ofNullable(baseQuery()
                .select(Projections.constructor(MemberRecord.class, USER.id, USER.email, PROFILE.nickname, PROFILE.gradeKey, USER.status,
                        new CaseBuilder().when(USER.passwordHash.isNotNull()).then(true).otherwise(false), USER.emailVerifiedAt, USER.createdAt,
                        USER.lastLoginAt, USER.deletedAt))
                .where(USER.id.eq(userId)).fetchOne());
    }

    /** 회원들이 직접 부여받은 역할(이름·유형·부여 방식 포함) — 회원마다 역할을 만든 순서로 */
    public Map<Long, List<UserRoleRow>> userRolesOf(Collection<Long> userIds) {
        Map<Long, List<UserRoleRow>> rows = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return rows;
        }
        for (Tuple row : query.select(USER_ROLE.userId, ROLE.roleId, ROLE.roleName, ROLE.roleTypeId, ROLE.grantMode.stringValue(), USER_ROLE.boardId)
                .from(USER_ROLE).join(ROLE).on(ROLE.roleId.eq(USER_ROLE.roleId)).where(USER_ROLE.userId.in(userIds))
                .orderBy(ROLE.createdAt.asc(), ROLE.roleId.asc(), USER_ROLE.id.asc()).fetch()) {
            Long userId = row.get(USER_ROLE.userId);
            rows.computeIfAbsent(userId, k -> new ArrayList<>()).add(new UserRoleRow(userId, row.get(ROLE.roleId), row.get(ROLE.roleName), row.get(ROLE.roleTypeId),
                    row.get(ROLE.grantMode.stringValue()), row.get(USER_ROLE.boardId)));
        }
        return rows;
    }

    /** 회원들이 연결한 소셜 공급자 — 회원마다 연결한 순서로 */
    public Map<Long, List<SocialProvider>> providersOf(Collection<Long> userIds) {
        Map<Long, List<SocialProvider>> rows = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return rows;
        }
        for (Tuple row : query.select(IDENTITY.userId, IDENTITY.provider).from(IDENTITY).where(IDENTITY.userId.in(userIds)).orderBy(IDENTITY.createdAt.asc()).fetch()) {
            rows.computeIfAbsent(row.get(IDENTITY.userId), k -> new ArrayList<>()).add(row.get(IDENTITY.provider));
        }
        return rows;
    }

    /**
     * 회원의 등급 변경 이력 — 최근 것이 먼저(같은 시각이면 번호가 큰 것이 먼저), 최대 {@code limit} 줄.
     * 처리한 운영자의 이름은 운영자 표와 맞춰 보고, 운영자가 지워졌으면 이름만 null 이다. 이메일은 읽지 않는다. {@code (user_id, created_at DESC, id DESC)} 색인으로 끝난다.
     */
    public List<MemberGradeChangeRow> gradeChangesOf(long userId, int limit) {
        return query.select(Projections.constructor(MemberGradeChangeRow.class, GRADE_CHANGE.id, GRADE_CHANGE.fromGradeKey, GRADE_CHANGE.toGradeKey, GRADE_CHANGE.kind,
                        GRADE_CHANGE.reason, GRADE_CHANGE.reasonCode, GRADE_CHANGE.actorOperatorId, OPERATOR.operatorName, GRADE_CHANGE.createdAt))
                .from(GRADE_CHANGE).leftJoin(OPERATOR).on(OPERATOR.id.eq(GRADE_CHANGE.actorOperatorId))
                .where(GRADE_CHANGE.userId.eq(userId))
                .orderBy(GRADE_CHANGE.createdAt.desc(), GRADE_CHANGE.id.desc())
                .limit(limit)
                .fetch();
    }

    private JPAQuery<?> baseQuery() {
        return query.from(USER).join(PROFILE).on(PROFILE.userId.eq(USER.id)).join(GRADE).on(GRADE.gradeKey.eq(PROFILE.gradeKey));
    }

    private static Predicate where(MemberSearchCriteria c, boolean canPii) {
        BooleanBuilder where = new BooleanBuilder();

        if (c.keyword() != null) {
            where.and(keyword(c, canPii));
        }
        if (c.statuses() != null) {
            where.and(c.statuses().isEmpty() ? NEVER : USER.status.in(c.statuses()));
        }
        if (c.roleIds() != null) {
            where.and(USER.status.ne(UserStatus.WITHDRAWN)); // 탈퇴한 회원은 역할이 없다
            BooleanBuilder anyRole = new BooleanBuilder();
            for (String roleId : c.roleIds()) {
                anyRole.or(holdsRole(roleId));
            }
            where.and(anyRole);
        }
        range(where, USER.createdAt, c.joinedFrom(), c.joinedTo());
        if (c.noLoginHistory()) {
            where.and(USER.lastLoginAt.isNull());
        } else {
            range(where, USER.lastLoginAt, c.lastLoginFrom(), c.lastLoginTo());
        }
        if (c.grades() != null) {
            where.and(PROFILE.gradeKey.in(c.grades()));
        }
        if (c.memberType() != null) {
            where.and(c.memberType() == MemberSearchCriteria.MemberType.BUSINESS ? hasRole(MemberRoleIds.BUSINESS) : hasRole(MemberRoleIds.BUSINESS).not());
        }
        if (c.emailVerified() != null) {
            where.and(c.emailVerified() ? USER.emailVerifiedAt.isNotNull() : USER.emailVerifiedAt.isNull());
        }
        if (c.authMethods() != null) {
            BooleanBuilder anyMethod = new BooleanBuilder();
            for (AuthMethod method : c.authMethods()) {
                anyMethod.or(method == AuthMethod.EMAIL ? USER.passwordHash.isNotNull() : hasIdentity(SocialProvider.valueOf(method.name())));
            }
            where.and(anyMethod);
        }
        if (c.dormantRange()) {
            where.and(NEVER); // 휴면 전환 이력이 아직 없다
        }
        statusChange(where, c);
        if ((c.pointMin() != null && c.pointMin() > 0) || (c.pointMax() != null && c.pointMax() < 0)) {
            where.and(NEVER); // 포인트 기능이 아직 없어 모든 회원이 0 이다
        }
        if (c.boardRole() != null) {
            BooleanExpression manager = hasRole(MemberRoleIds.BOARD_MANAGER);
            BooleanExpression staff = hasRole(MemberRoleIds.BOARD_STAFF);
            where.and(switch (c.boardRole()) {
                case MANAGER -> manager;
                case STAFF -> staff.and(manager.not());
                case NONE -> manager.not().and(staff.not());
            });
        }
        if (Boolean.TRUE.equals(c.hasSanction())) {
            where.and(NEVER); // 제재 표가 아직 없어 이력이 있는 회원이 없다
        }
        return where;
    }

    /** 상태 변경일 조건 — 정지·휴면 이력 표가 아직 없어 날짜로 걸 수 있는 것은 탈퇴 시각뿐이다 */
    private static void statusChange(BooleanBuilder where, MemberSearchCriteria c) {
        boolean ranged = c.statusChangedFrom() != null || c.statusChangedTo() != null;
        if (c.statusChangedKind() != null) {
            switch (c.statusChangedKind()) {
                case SUSPENDED -> where.and(ranged ? NEVER : USER.status.in(UserStatus.SUSPENDED, UserStatus.BANNED));
                case WITHDRAWN -> where.and(USER.status.eq(UserStatus.WITHDRAWN));
                case DORMANT -> where.and(NEVER);
            }
        }
        if (ranged) {
            if (c.statusChangedFrom() != null) {
                where.and(USER.deletedAt.goe(c.statusChangedFrom()));
            }
            if (c.statusChangedTo() != null) {
                where.and(USER.deletedAt.loe(c.statusChangedTo()));
            }
        }
    }

    /** 기간 조건 — 양 끝을 포함한다. 없는 쪽은 거르지 않는다 */
    private static void range(BooleanBuilder where, DateTimePath<Instant> path, Instant from, Instant to) {
        if (from != null) {
            where.and(path.goe(from));
        }
        if (to != null) {
            where.and(path.loe(to));
        }
    }

    private static BooleanExpression keyword(MemberSearchCriteria c, boolean canPii) {
        String keyword = c.keyword();
        BooleanExpression byNumber = isDigits(keyword) ? memberNo(keyword) : NEVER;
        BooleanExpression byNickname = PROFILE.nickname.lower().contains(keyword);
        // 가려진 값으로 짐작해 찾을 수 없게 member.pii 가 없으면 전체 주소가 같을 때만 맞는다(이메일은 소문자로 저장한다)
        BooleanExpression byEmail = canPii ? USER.email.contains(keyword) : USER.email.eq(keyword);
        return switch (c.searchField()) {
            case EMAIL -> byEmail;
            case NICKNAME -> byNickname;
            case MEMBER_NO -> byNumber;
            case ALL -> byNumber.or(byNickname).or(byEmail);
        };
    }

    private static BooleanExpression memberNo(String digits) {
        try {
            return USER.id.eq(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return NEVER; // long 범위 밖이면 그런 회원번호가 없다
        }
    }

    private static boolean isDigits(String text) {
        return !text.isEmpty() && text.chars().allMatch(ch -> ch >= '0' && ch <= '9');
    }

    /** 이 회원 역할을 가진 회원 — 등급 역할은 프로필의 등급, 일반 회원은 사업자가 아닌 회원, 그 밖은 직접 부여한 행 */
    private static BooleanExpression holdsRole(String roleId) {
        if (MemberRoleIds.GENERAL.equals(roleId)) {
            return hasRole(MemberRoleIds.BUSINESS).not();
        }
        Optional<String> gradeKey = GradeRoles.gradeKeyOf(roleId);
        if (gradeKey.isPresent()) {
            return PROFILE.gradeKey.eq(gradeKey.get());
        }
        return hasRole(roleId);
    }

    private static BooleanExpression hasRole(String roleId) {
        return JPAExpressions.selectOne().from(USER_ROLE).where(USER_ROLE.userId.eq(USER.id), USER_ROLE.roleId.eq(roleId)).exists();
    }

    private static BooleanExpression hasIdentity(SocialProvider provider) {
        return JPAExpressions.selectOne().from(IDENTITY).where(IDENTITY.userId.eq(USER.id), IDENTITY.provider.eq(provider)).exists();
    }

    private static OrderSpecifier<?>[] orderOf(MemberSearchCriteria c) {
        boolean asc = c.sortAscending();
        List<OrderSpecifier<?>> order = new ArrayList<>();
        switch (c.sortField()) {
            case MEMBER_NO -> order.add(asc ? USER.id.asc() : USER.id.desc());
            case EMAIL -> order.add(nullsLast(USER.email, asc));
            case NICKNAME -> order.add(asc ? PROFILE.nickname.lower().asc() : PROFILE.nickname.lower().desc());
            case GRADE -> order.add(asc ? GRADE.level.asc() : GRADE.level.desc());
            case STATUS -> {
                var rank = new CaseBuilder().when(USER.status.eq(UserStatus.ACTIVE)).then(0).when(USER.status.eq(UserStatus.SUSPENDED)).then(1)
                        .when(USER.status.eq(UserStatus.BANNED)).then(2).otherwise(3);
                order.add(asc ? rank.asc() : rank.desc());
            }
            case LAST_LOGIN_AT -> order.add(nullsLast(USER.lastLoginAt, asc));
            case JOINED_AT, POINT, DORMANT_AT -> order.add(asc ? USER.createdAt.asc() : USER.createdAt.desc()); // 포인트·휴면 전환일은 값이 모두 같아(없어) 가입 시각 순으로 본다
        }
        order.add(asc ? USER.id.asc() : USER.id.desc());
        return order.toArray(new OrderSpecifier<?>[0]);
    }

    /** 값이 없는 회원은 방향과 상관없이 늘 뒤로 보낸다 */
    private static OrderSpecifier<?> nullsLast(ComparableExpressionBase<?> expression, boolean asc) {
        return (asc ? expression.asc() : expression.desc()).nullsLast();
    }
}
