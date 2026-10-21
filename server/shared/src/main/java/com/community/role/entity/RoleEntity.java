package com.community.role.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 역할 — V7__operators_roles.sql 의 {@code roles}. 운영자 역할(관리자 앱 권한)과 회원 역할(공개 사이트 회원의 능력)이 한 표에 있고 {@code axis} 로 가른다.
 * 키는 사람이 정한 역할 아이디({@code ROLE_} + 대문자·숫자·밑줄, 30자 이하)이고 만든 뒤에는 바꿀 수 없다. 역할은 역할 유형({@link RoleTypes}) 하나에 속한다.
 * 가진 권한은 {@code role_permissions}, 받은 운영자는 {@code operator_roles}, 받은 회원은 {@code user_roles} 가 들고 있다.
 */
@Entity
@Table(name = "roles")
@DynamicUpdate
public class RoleEntity {

    @Id
    private String roleId;

    private String roleTypeId;

    @Enumerated(EnumType.STRING)
    private RoleAxis axis;

    private String roleName;
    private String description;

    @Enumerated(EnumType.STRING)
    private RoleScope scope;

    @Enumerated(EnumType.STRING)
    private RoleGrantMode grantMode;

    /** 제품이 넣은 역할 — 지울 수 없다(권한은 조정할 수 있다) */
    private boolean builtin;

    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected RoleEntity() {
    }

    /** 운영자 역할 — 사이트 범위, 수동 부여 */
    public static RoleEntity createOperator(String roleId, String roleTypeId, String roleName, String description, Long createdBy, Instant now) {
        return create(roleId, roleTypeId, RoleAxis.OPERATOR, roleName, description, RoleScope.SITE, RoleGrantMode.MANUAL, false, createdBy, now);
    }

    /** 사용자 정의 회원 역할 — 사이트 범위, 운영자가 회원에게 수동으로 준다 */
    public static RoleEntity createCustomMember(String roleId, String roleName, String description, Long createdBy, Instant now) {
        return create(roleId, RoleTypes.CUSTOM, RoleAxis.MEMBER, roleName, description, RoleScope.SITE, RoleGrantMode.MANUAL, false, createdBy, now);
    }

    /** 제품이 넣는 회원 역할(내장) */
    public static RoleEntity createBuiltinMember(String roleId, String roleTypeId, String roleName, String description, RoleScope scope,
                                                 RoleGrantMode grantMode, Instant now) {
        return create(roleId, roleTypeId, RoleAxis.MEMBER, roleName, description, scope, grantMode, true, null, now);
    }

    private static RoleEntity create(String roleId, String roleTypeId, RoleAxis axis, String roleName, String description, RoleScope scope,
                                     RoleGrantMode grantMode, boolean builtin, Long createdBy, Instant now) {
        RoleEntity role = new RoleEntity();
        role.roleId = roleId;
        role.roleTypeId = roleTypeId;
        role.axis = axis;
        role.roleName = roleName;
        role.description = description;
        role.scope = scope;
        role.grantMode = grantMode;
        role.builtin = builtin;
        role.createdBy = createdBy;
        role.createdAt = now;
        role.updatedAt = now;
        return role;
    }

    /** 이름·설명을 고친다 — 아이디·유형은 못 바꾼다 */
    public void rename(String roleName, String description, Instant now) {
        this.roleName = roleName;
        this.description = description;
        this.updatedAt = now;
    }

    /** 권한이 바뀌었다 — 수정 시각만 갱신한다 */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getRoleTypeId() {
        return roleTypeId;
    }

    public RoleAxis getAxis() {
        return axis;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }

    public RoleScope getScope() {
        return scope;
    }

    public RoleGrantMode getGrantMode() {
        return grantMode;
    }

    public boolean isBuiltin() {
        return builtin;
    }
}
