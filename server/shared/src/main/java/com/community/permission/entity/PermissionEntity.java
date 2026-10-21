package com.community.permission.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 권한 한 건 — V7__operators_roles.sql 의 {@code permissions}. 원천은 코드({@link Permission}·{@link MemberPermission})이고 이 표는 서버가 뜰 때 코드와 맞춘다
 * ({@code PermissionCatalogSynchronizer}) — {@code role_permissions} 가 참조할 수 있게 하려는 사본이다. 직접 고치지 않는다.
 */
@Entity
@Table(name = "permissions")
@DynamicUpdate
public class PermissionEntity {

    @Id
    private String code;

    /** OPERATOR · MEMBER */
    private String axis;

    /** 운영자 권한은 세부 유형 id(예: {@code operatorRole}), 회원 권한은 {@code memberSite} · {@code memberBoard} */
    private String typeId;

    /** 운영자 권한은 {@link PermissionUnit} 이름, 회원 권한은 {@link MemberPermissionGroup} 이름 */
    private String category;

    private boolean featureReady;
    private Instant syncedAt;

    protected PermissionEntity() {
    }

    public static PermissionEntity of(Permission permission, Instant now) {
        PermissionEntity entity = new PermissionEntity();
        entity.code = permission.code();
        entity.apply("OPERATOR", permission.type().id(), permission.unit().name(), permission.featureReady(), now);
        return entity;
    }

    public static PermissionEntity of(MemberPermission permission, Instant now) {
        PermissionEntity entity = new PermissionEntity();
        entity.code = permission.code();
        entity.apply("MEMBER", permission.typeId(), permission.group().name(), permission.featureReady(), now);
        return entity;
    }

    /** 코드의 분류가 바뀌었으면 맞춘다. 바뀐 것이 있으면 true */
    public boolean refresh(Permission permission, Instant now) {
        return refresh("OPERATOR", permission.type().id(), permission.unit().name(), permission.featureReady(), now);
    }

    public boolean refresh(MemberPermission permission, Instant now) {
        return refresh("MEMBER", permission.typeId(), permission.group().name(), permission.featureReady(), now);
    }

    private boolean refresh(String axis, String typeId, String category, boolean featureReady, Instant now) {
        if (axis.equals(this.axis) && typeId.equals(this.typeId) && category.equals(this.category) && featureReady == this.featureReady) {
            return false;
        }
        apply(axis, typeId, category, featureReady, now);
        return true;
    }

    private void apply(String axis, String typeId, String category, boolean featureReady, Instant now) {
        this.axis = axis;
        this.typeId = typeId;
        this.category = category;
        this.featureReady = featureReady;
        this.syncedAt = now;
    }

    public String getCode() {
        return code;
    }
}
