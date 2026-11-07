package com.community.testsupport;

import com.community.operator.entity.OperatorEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.role.entity.OperatorRoleEntity;
import com.community.role.entity.RoleEntity;
import com.community.role.entity.RolePermissionEntity;
import com.community.role.repository.master.OperatorRoleRepository;
import com.community.role.repository.master.RolePermissionRepository;
import com.community.role.repository.master.RoleRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.LongFunction;
import org.springframework.context.ApplicationContext;

/**
 * 시험 운영자({@code operators})와 운영자 역할({@code roles}·{@code role_permissions}·{@code operator_roles}) — 엔티티 정적 팩터리·도메인 메서드로 만들고
 * {@code persist()} 로 넣는다(호출마다 커밋).
 *
 * <ul>
 *   <li>운영자는 승인된 계정({@link OperatorEntity#createApproved})이다 — 사용 중, 잠금·만료·기간 제한 없음.</li>
 *   <li>루트는 하나뿐이라(유일 색인) {@link #rootOrCreate} 가 있으면 쓰고 없으면 만든다.</li>
 *   <li>역할은 일반 운영자 유형({@value #OPERATOR_ROLE_TYPE}) — 사이트 범위·수동 부여, 만든 사람·부여한 사람 없음.</li>
 * </ul>
 */
public final class OperatorFixture {

    /** 시험 역할의 유형 */
    public static final String OPERATOR_ROLE_TYPE = "op_general";

    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    private final FixtureTx tx;
    private final SnowflakeIdGenerator ids;
    private final OperatorRepository operators;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final OperatorRoleRepository operatorRoles;
    private final JPAQueryFactory query;

    public OperatorFixture(ApplicationContext context) {
        this.tx = new FixtureTx(context);
        this.ids = context.getBean(SnowflakeIdGenerator.class);
        this.operators = context.getBean(OperatorRepository.class);
        this.roles = context.getBean(RoleRepository.class);
        this.rolePermissions = context.getBean(RolePermissionRepository.class);
        this.operatorRoles = context.getBean(OperatorRoleRepository.class);
        this.query = context.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
    }

    /** 승인된 활성 운영자 — 만든 시각 = 지금 */
    public void operator(long id, String email, String operatorName, String passwordHash) {
        operator(id, email, operatorName, passwordHash, FixtureTx.now());
    }

    /** 승인된 활성 운영자 — 만든 시각(고친 시각도 같다)을 고른다 */
    public void operator(long id, String email, String operatorName, String passwordHash, Instant createdAt) {
        tx.run(() -> operators.persist(OperatorEntity.createApproved(id, email, operatorName, passwordHash, createdAt)));
    }

    /** 있는 루트의 ID */
    public Optional<Long> rootId() {
        return Optional.ofNullable(tx.call(() -> query.select(OPERATOR.id).from(OPERATOR).where(OPERATOR.root.isTrue()).fetchFirst()));
    }

    /** 루트(하나뿐) — 있으면 그 ID, 없으면 새 ID 로 만든다(이메일·이름은 새 ID 로 짓는다) */
    public long rootOrCreate(LongFunction<String> email, LongFunction<String> operatorName, String passwordHash) {
        Optional<Long> found = rootId();
        if (found.isPresent()) {
            return found.get();
        }
        long id = ids.nextId();
        tx.run(() -> operators.persist(OperatorEntity.createRoot(id, email.apply(id), operatorName.apply(id), passwordHash, FixtureTx.now())));
        return id;
    }

    /** 고른 권한 코드를 가진 운영자 역할을 만들어 그 운영자에게 준다 */
    public void grantRole(long operatorId, String roleId, String roleName, Collection<String> permissionCodes) {
        tx.run(() -> {
            Instant now = FixtureTx.now();
            roles.persist(RoleEntity.createOperator(roleId, OPERATOR_ROLE_TYPE, roleName, null, null, now));
            for (String code : permissionCodes) {
                rolePermissions.persist(RolePermissionEntity.create(ids.nextId(), roleId, code, now));
            }
            operatorRoles.persist(OperatorRoleEntity.create(ids.nextId(), operatorId, roleId, null, now));
        });
    }

    /** 승인·잠금·만료·사용 표시를 바꾼다(null 은 그대로) */
    public void changeStatus(long operatorId, Boolean approved, Boolean locked, Boolean expired, Boolean enabled) {
        tx.run(() -> operators.findById(operatorId).orElseThrow().changeStatus(approved, locked, expired, enabled, FixtureTx.now()));
    }

    /** 마지막 로그인 시각(없으면 null) */
    public Instant lastLoginAt(long operatorId) {
        return tx.call(() -> query.select(OPERATOR.lastLoginAt).from(OPERATOR).where(OPERATOR.id.eq(operatorId)).fetchOne());
    }

    /** 운영자 이름 */
    public String operatorName(long operatorId) {
        return tx.call(() -> query.select(OPERATOR.operatorName).from(OPERATOR).where(OPERATOR.id.eq(operatorId)).fetchOne());
    }
}
