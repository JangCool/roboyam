package com.community.role.repository.master;

import com.community.role.dto.OperatorRoleRow;
import com.community.role.entity.QOperatorRoleEntity;
import com.community.role.entity.QRoleEntity;
import com.community.role.entity.QRolePermissionEntity;
import com.community.role.entity.QUserRoleEntity;
import com.community.role.entity.RoleEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 역할 목록·쓰는 사람 수·권한 집합·운영자가 받은 역할을 읽는 조회와, 권한·할당을 지우는 조건부 DELETE. 정렬·집계는 DB 에서 한다.
 * <b>트랜잭션은 부르는 서비스가 연다.</b>
 */
@Repository
public class RoleRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QRoleEntity ROLE = QRoleEntity.roleEntity;
    private static final QRolePermissionEntity PERMISSION = QRolePermissionEntity.rolePermissionEntity;
    private static final QOperatorRoleEntity OPERATOR_ROLE = QOperatorRoleEntity.operatorRoleEntity;
    private static final QUserRoleEntity USER_ROLE = QUserRoleEntity.userRoleEntity;

    public RoleRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 모든 역할 — 제품이 넣은 역할이 앞, 그다음 만든 순서(같은 시각이면 아이디순) */
    public List<RoleEntity> findAllOrdered() {
        return query.selectFrom(ROLE).orderBy(ROLE.builtin.desc(), ROLE.createdAt.asc(), ROLE.roleId.asc()).fetch();
    }

    /** 역할 유형마다 든 역할 수 */
    public Map<String, Long> countByType() {
        Map<String, Long> counts = new HashMap<>();
        for (Tuple row : query.select(ROLE.roleTypeId, ROLE.count()).from(ROLE).groupBy(ROLE.roleTypeId).fetch()) {
            counts.put(row.get(ROLE.roleTypeId), row.get(ROLE.count()));
        }
        return counts;
    }

    /** 역할마다 가진 권한 코드(코드순) */
    public Map<String, Set<String>> permissionCodesOf(Collection<String> roleIds) {
        Map<String, Set<String>> codes = new HashMap<>();
        if (roleIds.isEmpty()) {
            return codes;
        }
        for (Tuple row : query.select(PERMISSION.roleId, PERMISSION.permissionCode).from(PERMISSION).where(PERMISSION.roleId.in(roleIds))
                .orderBy(PERMISSION.permissionCode.asc()).fetch()) {
            codes.computeIfAbsent(row.get(PERMISSION.roleId), k -> new LinkedHashSet<>()).add(row.get(PERMISSION.permissionCode));
        }
        return codes;
    }

    /** 역할 하나가 가진 권한 코드 */
    public Set<String> permissionCodesOf(String roleId) {
        return new LinkedHashSet<>(query.select(PERMISSION.permissionCode).from(PERMISSION).where(PERMISSION.roleId.eq(roleId))
                .orderBy(PERMISSION.permissionCode.asc()).fetch());
    }

    /** 역할 하나에서 권한 코드들을 뺀다 */
    public void deletePermissions(String roleId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        query.delete(PERMISSION).where(PERMISSION.roleId.eq(roleId), PERMISSION.permissionCode.in(codes)).execute();
    }

    /** 카탈로그에서 사라진 권한 코드를 모든 역할에서 뺀다(권한 맞춤). 지운 줄 수 */
    public long deletePermissionsByCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return 0;
        }
        return query.delete(PERMISSION).where(PERMISSION.permissionCode.in(codes)).execute();
    }

    /** 역할을 받은 운영자 수 — 역할마다 */
    public Map<String, Long> operatorCountByRole() {
        Map<String, Long> counts = new HashMap<>();
        for (Tuple row : query.select(OPERATOR_ROLE.roleId, OPERATOR_ROLE.count()).from(OPERATOR_ROLE).groupBy(OPERATOR_ROLE.roleId).fetch()) {
            counts.put(row.get(OPERATOR_ROLE.roleId), row.get(OPERATOR_ROLE.count()));
        }
        return counts;
    }

    /** 역할을 가진 회원 수 — 역할마다 */
    public Map<String, Long> memberCountByRole() {
        Map<String, Long> counts = new HashMap<>();
        for (Tuple row : query.select(USER_ROLE.roleId, USER_ROLE.count()).from(USER_ROLE).groupBy(USER_ROLE.roleId).fetch()) {
            counts.put(row.get(USER_ROLE.roleId), row.get(USER_ROLE.count()));
        }
        return counts;
    }

    public long operatorCountOf(String roleId) {
        Long count = query.select(OPERATOR_ROLE.count()).from(OPERATOR_ROLE).where(OPERATOR_ROLE.roleId.eq(roleId)).fetchOne();
        return count == null ? 0 : count;
    }

    public long memberCountOf(String roleId) {
        Long count = query.select(USER_ROLE.count()).from(USER_ROLE).where(USER_ROLE.roleId.eq(roleId)).fetchOne();
        return count == null ? 0 : count;
    }

    /** 운영자들이 받은 역할(이름 포함) — 운영자마다 역할 만든 순서 */
    public Map<Long, List<OperatorRoleRow>> rolesOfOperators(Collection<Long> operatorIds) {
        Map<Long, List<OperatorRoleRow>> rows = new LinkedHashMap<>();
        if (operatorIds.isEmpty()) {
            return rows;
        }
        for (Tuple row : query.select(OPERATOR_ROLE.operatorId, ROLE.roleId, ROLE.roleName).from(OPERATOR_ROLE).join(ROLE)
                .on(ROLE.roleId.eq(OPERATOR_ROLE.roleId)).where(OPERATOR_ROLE.operatorId.in(operatorIds))
                .orderBy(ROLE.createdAt.asc(), ROLE.roleId.asc()).fetch()) {
            long operatorId = row.get(OPERATOR_ROLE.operatorId);
            rows.computeIfAbsent(operatorId, k -> new java.util.ArrayList<>())
                    .add(new OperatorRoleRow(operatorId, row.get(ROLE.roleId), row.get(ROLE.roleName)));
        }
        return rows;
    }

    /** 운영자가 받은 역할 아이디 */
    public Set<String> roleIdsOfOperator(long operatorId) {
        return new LinkedHashSet<>(query.select(OPERATOR_ROLE.roleId).from(OPERATOR_ROLE).where(OPERATOR_ROLE.operatorId.eq(operatorId))
                .orderBy(OPERATOR_ROLE.roleId.asc()).fetch());
    }

    /** 운영자가 받은 운영자 역할들의 권한 합집합 — 요청마다 현재 상태를 읽는다(캐시하지 않는다) */
    public Set<String> permissionCodesOfOperator(long operatorId) {
        return new LinkedHashSet<>(query.select(PERMISSION.permissionCode).distinct().from(OPERATOR_ROLE).join(PERMISSION)
                .on(PERMISSION.roleId.eq(OPERATOR_ROLE.roleId)).where(OPERATOR_ROLE.operatorId.eq(operatorId)).fetch());
    }

    /** 운영자에게서 역할들을 뺀다 */
    public void deleteOperatorRoles(long operatorId, Collection<String> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        query.delete(OPERATOR_ROLE).where(OPERATOR_ROLE.operatorId.eq(operatorId), OPERATOR_ROLE.roleId.in(roleIds)).execute();
    }
}
