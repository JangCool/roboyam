package com.community.operator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 초대할 때 정한 역할 한 건 — V7__operators_roles.sql 의 {@code operator_invitation_roles}. 수락한 운영자가 받는다 */
@Entity
@Table(name = "operator_invitation_roles")
public class OperatorInvitationRoleEntity {

    @Id
    private Long id;

    private Long invitationId;
    private String roleId;

    protected OperatorInvitationRoleEntity() {
    }

    public static OperatorInvitationRoleEntity create(long id, long invitationId, String roleId) {
        OperatorInvitationRoleEntity entity = new OperatorInvitationRoleEntity();
        entity.id = id;
        entity.invitationId = invitationId;
        entity.roleId = roleId;
        return entity;
    }

    public Long getInvitationId() {
        return invitationId;
    }

    public String getRoleId() {
        return roleId;
    }
}
