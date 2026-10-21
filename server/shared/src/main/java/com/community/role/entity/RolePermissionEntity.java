package com.community.role.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 역할이 가진 권한 한 건 — V7__operators_roles.sql 의 {@code role_permissions}. (역할, 권한 코드) 쌍은 유일하다. 권한은 통째로 바꾼다 */
@Entity
@Table(name = "role_permissions")
public class RolePermissionEntity {

    @Id
    private Long id;

    private String roleId;
    private String permissionCode;
    private Instant grantedAt;

    protected RolePermissionEntity() {
    }

    public static RolePermissionEntity create(long id, String roleId, String permissionCode, Instant now) {
        RolePermissionEntity entity = new RolePermissionEntity();
        entity.id = id;
        entity.roleId = roleId;
        entity.permissionCode = permissionCode;
        entity.grantedAt = now;
        return entity;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }
}
