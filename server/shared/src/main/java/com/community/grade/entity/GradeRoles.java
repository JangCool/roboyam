package com.community.grade.entity;

import java.util.Optional;

/** 등급 역할의 아이디 규칙 — 등급 설정에서 파생되는 회원 역할은 {@code ROLE_GRADE_<등급 키>} 다(역할 유형 {@code memberGrade}) */
public final class GradeRoles {

    public static final String PREFIX = "ROLE_GRADE_";

    private GradeRoles() {
    }

    public static String roleIdOf(String gradeKey) {
        return PREFIX + gradeKey;
    }

    /** 등급 역할 아이디에서 등급 키를 읽는다. 등급 역할이 아니면 비어 있다 */
    public static Optional<String> gradeKeyOf(String roleId) {
        return roleId != null && roleId.length() > PREFIX.length() && roleId.startsWith(PREFIX) ? Optional.of(roleId.substring(PREFIX.length())) : Optional.empty();
    }
}
