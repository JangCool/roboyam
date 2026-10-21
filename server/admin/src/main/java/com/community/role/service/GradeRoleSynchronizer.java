package com.community.role.service;

import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeRoles;
import com.community.role.entity.RoleEntity;
import com.community.role.entity.RoleGrantMode;
import com.community.role.entity.RolePermissionEntity;
import com.community.role.entity.RoleScope;
import com.community.role.entity.RoleTypes;
import com.community.role.repository.master.RolePermissionRepository;
import com.community.role.repository.master.RoleRepository;
import com.community.role.repository.master.RoleRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 등급 목록에서 <b>등급 역할(파생)</b>을 맞춘다 — 등급 하나마다 {@code ROLE_GRADE_<키>} 역할 하나(유형 {@code memberGrade}, 사이트 범위, 자동 부여, 내장 취급).
 * 등급이 생기면 역할을 만들고, 이름이 바뀌면 역할 이름이 따라가고, 등급이 지워지면 역할도 지운다. 호출하는 쪽의 트랜잭션에 실린다.
 *
 * <ul>
 *   <li>새 역할의 기본 권한은 목록에서 <b>바로 아래 등급 역할의 권한을 복사</b>한 것이다(가정 — 운영자가 이후 권한 편집 API 로 조정한다). 첫 등급은 빈 권한으로 시작한다
 *       (가입하면 갖는 일반 회원 역할이 기본 능력을 준다).</li>
 *   <li>이미 있는 역할의 권한·설명은 건드리지 않는다(이름만 등급 이름을 따른다).</li>
 * </ul>
 */
@Component
public class GradeRoleSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(GradeRoleSynchronizer.class);

    private final RoleRepository roles;
    private final RoleRepositoryQDSL roleQueries;
    private final RolePermissionRepository rolePermissions;
    private final SnowflakeIdGenerator ids;

    public GradeRoleSynchronizer(RoleRepository roles, RoleRepositoryQDSL roleQueries, RolePermissionRepository rolePermissions, SnowflakeIdGenerator ids) {
        this.roles = roles;
        this.roleQueries = roleQueries;
        this.rolePermissions = rolePermissions;
        this.ids = ids;
    }

    /**
     * @param orderedGrades 서열 순서의 최종 등급 목록
     * @param removedKeys   이번에 지워지는 등급 키 — 그 역할(과 직접 부여 행·권한)도 지운다
     * @return 새로 만든 역할 수
     */
    public int sync(List<GradeEntity> orderedGrades, Collection<String> removedKeys, Instant now) {
        for (String key : removedKeys) {
            String roleId = GradeRoles.roleIdOf(key);
            roleQueries.deleteUserRoles(roleId);
            roles.findById(roleId).ifPresent(roles::delete); // role_permissions 는 DB 가 함께 지운다(ON DELETE CASCADE)
        }

        int created = 0;
        Map<String, Set<String>> permissionsByRole = new HashMap<>();
        String lowerRoleId = null;
        for (GradeEntity grade : orderedGrades) {
            String roleId = GradeRoles.roleIdOf(grade.getGradeKey());
            RoleEntity existing = roles.findById(roleId).orElse(null);
            if (existing == null) {
                // 만든 시각을 서열만큼 벌려 역할 목록의 차례(만든 순서)가 등급 서열을 따르게 한다
                Instant createdAt = now.plusMillis(grade.getLevel());
                roles.persistAndFlush(RoleEntity.createBuiltinMember(roleId, RoleTypes.GRADE, grade.getLabel(), null, RoleScope.SITE, RoleGrantMode.AUTO, createdAt)); // 아래 권한 행이 참조하므로 먼저 내보낸다
                Set<String> inherited = lowerRoleId == null ? Set.of() : permissionsByRole.getOrDefault(lowerRoleId, roleQueries.permissionCodesOf(lowerRoleId));
                for (String code : inherited) {
                    rolePermissions.persist(RolePermissionEntity.create(ids.nextId(), roleId, code, createdAt));
                }
                permissionsByRole.put(roleId, inherited);
                created++;
            } else {
                if (!existing.getRoleName().equals(grade.getLabel())) {
                    existing.rename(grade.getLabel(), existing.getDescription(), now);
                }
                permissionsByRole.put(roleId, roleQueries.permissionCodesOf(roleId));
            }
            lowerRoleId = roleId;
        }
        if (created > 0 || !removedKeys.isEmpty()) {
            log.info("등급 역할을 맞췄다: 새로 {}개, 지움 {}개", created, removedKeys.size());
        }
        return created;
    }
}
