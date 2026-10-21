package com.community.role.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 운영자가 받은 역할 한 건 — V7__operators_roles.sql 의 {@code operator_roles}. 운영자 한 명이 역할 여럿을 받고 권한은 합집합이다. 개인별 예외 권한은 없다 */
@Entity
@Table(name = "operator_roles")
public class OperatorRoleEntity {

    @Id
    private Long id;

    private Long operatorId;
    private String roleId;
    private Instant assignedAt;
    private Long assignedBy;

    protected OperatorRoleEntity() {
    }

    public static OperatorRoleEntity create(long id, long operatorId, String roleId, Long assignedBy, Instant now) {
        OperatorRoleEntity entity = new OperatorRoleEntity();
        entity.id = id;
        entity.operatorId = operatorId;
        entity.roleId = roleId;
        entity.assignedBy = assignedBy;
        entity.assignedAt = now;
        return entity;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getRoleId() {
        return roleId;
    }
}
