package com.community.role.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원이 가진 회원 역할 한 건 — V7__operators_roles.sql 의 {@code user_roles}. 한 회원은 역할 여럿을 가진다(등급 1 + 회원 유형 1 + 게시판 지정 0~N + 사용자 정의 0~N).
 * 게시판 단위 역할(매니저·스텝)은 {@code boardId} 로 게시판 하나를 가리킨다(게시판 표가 생길 때 참조를 건다). 이번 단계에는 부여하는 API 가 없다 — 저장 자리만 있다.
 */
@Entity
@Table(name = "user_roles")
public class UserRoleEntity {

    @Id
    private Long id;

    private Long userId;
    private String roleId;
    private Long boardId;
    private Instant assignedAt;
    private Long assignedBy;

    protected UserRoleEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    public String getRoleId() {
        return roleId;
    }
}
