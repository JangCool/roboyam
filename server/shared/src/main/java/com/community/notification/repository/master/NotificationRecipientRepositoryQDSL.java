package com.community.notification.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.operator.entity.QOperatorPreferenceEntity;
import com.community.role.entity.QOperatorRoleEntity;
import com.community.role.entity.QRolePermissionEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 알림 수신자를 정하는 조회 — 「이 권한을 가진 운영자」 「최고 관리자」 「살아 있는 운영자」. <b>활성</b> 운영자는 승인됐고 잠기지 않았고 만료 표시가 없고 사용 중이며 계정 기간 안인 운영자다
 * (지금 로그인할 수 있는 사람 — {@code OperatorEntity#canLogin} 과 같은 조건을 DB 에서 판정한다). 읽는 도중 아직 내보내지 않은 변경이 있으면 JPA 가 먼저 내보낸다(자동 flush).
 * <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class NotificationRecipientRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;
    private static final QOperatorRoleEntity OPERATOR_ROLE = QOperatorRoleEntity.operatorRoleEntity;
    private static final QRolePermissionEntity ROLE_PERMISSION = QRolePermissionEntity.rolePermissionEntity;
    private static final QOperatorPreferenceEntity PREFERENCE = QOperatorPreferenceEntity.operatorPreferenceEntity;

    public NotificationRecipientRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 권한 코드 중 하나라도 가진 활성 운영자 + 활성 최고 관리자(최고 관리자는 모든 권한을 가진다) */
    public Set<Long> activeHolders(Collection<String> permissionCodes, Instant now) {
        BooleanExpression holds = OPERATOR.root.isTrue();
        if (!permissionCodes.isEmpty()) {
            holds = holds.or(JPAExpressions.selectOne().from(OPERATOR_ROLE).join(ROLE_PERMISSION).on(ROLE_PERMISSION.roleId.eq(OPERATOR_ROLE.roleId))
                    .where(OPERATOR_ROLE.operatorId.eq(OPERATOR.id), ROLE_PERMISSION.permissionCode.in(permissionCodes)).exists());
        }
        return new LinkedHashSet<>(query.select(OPERATOR.id).from(OPERATOR).where(isActive(now), holds).orderBy(OPERATOR.id.asc()).fetch());
    }

    /** 활성 최고 관리자 */
    public Set<Long> activeRoots(Instant now) {
        return new LinkedHashSet<>(query.select(OPERATOR.id).from(OPERATOR).where(isActive(now), OPERATOR.root.isTrue()).orderBy(OPERATOR.id.asc()).fetch());
    }

    /** 활성 운영자 전원(루트 포함) — 수신 규칙 {@code ALL_OPERATORS} */
    public Set<Long> allActive(Instant now) {
        return new LinkedHashSet<>(query.select(OPERATOR.id).from(OPERATOR).where(isActive(now)).orderBy(OPERATOR.id.asc()).fetch());
    }

    /** 그 운영자들 중 활성인 사람 — 추가 수신자를 거를 때 */
    public Set<Long> active(Collection<Long> operatorIds, Instant now) {
        if (operatorIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(query.select(OPERATOR.id).from(OPERATOR).where(OPERATOR.id.in(operatorIds), isActive(now)).orderBy(OPERATOR.id.asc()).fetch());
    }

    /** 그 운영자들 중 계정이 있는 사람(상태와 상관없이) — 「내 계정」 이벤트의 수신자 */
    public Set<Long> existing(Collection<Long> operatorIds) {
        if (operatorIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(query.select(OPERATOR.id).from(OPERATOR).where(OPERATOR.id.in(operatorIds)).orderBy(OPERATOR.id.asc()).fetch());
    }

    /** 이메일 알림을 끈({@code email_notification = false}) 운영자 — 설정 행이 없으면 처음 값(켜짐)이라 여기에 없다 */
    public Set<Long> emailOptedOut(Collection<Long> operatorIds) {
        if (operatorIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(query.select(PREFERENCE.operatorId).from(PREFERENCE)
                .where(PREFERENCE.operatorId.in(operatorIds), PREFERENCE.emailNotification.isFalse()).fetch());
    }

    /** 운영자가 받은 역할 중 이 역할 아이디를 가진 운영자들 — 역할이 바뀔 때 영향받는 사람 */
    public List<Long> operatorsWithRole(String roleId) {
        return query.select(OPERATOR_ROLE.operatorId).distinct().from(OPERATOR_ROLE).where(OPERATOR_ROLE.roleId.eq(roleId)).orderBy(OPERATOR_ROLE.operatorId.asc()).fetch();
    }

    /** 지금 로그인할 수 있는 상태 — 승인됨 · 잠기지 않음 · 만료 표시 없음 · 사용 중 · 계정 기간 안 */
    public static BooleanExpression isActive(Instant now) {
        BooleanExpression periodOver = OPERATOR.accountStartAt.isNotNull().and(OPERATOR.accountStartAt.gt(now))
                .or(OPERATOR.accountEndAt.isNotNull().and(OPERATOR.accountEndAt.lt(now)));
        return OPERATOR.approved.isTrue().and(OPERATOR.locked.isFalse()).and(OPERATOR.expired.isFalse()).and(OPERATOR.enabled.isTrue()).and(periodOver.not());
    }
}
