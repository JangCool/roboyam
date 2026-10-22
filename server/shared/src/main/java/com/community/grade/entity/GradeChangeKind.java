package com.community.grade.entity;

/** 등급 변경 종류 — {@code member_grade_changes.kind} 의 CHECK 와 admin-web {@code MemberGradeChangeKind} 와 같은 값이다 */
public enum GradeChangeKind {
    /** 조건을 채워 시스템이 올렸다(자동 승급) */
    AUTO,
    /** 운영자가 바꿨다(수동 변경, 등급 삭제로 인한 일괄 이동) */
    ADMIN,
    /** 운영자가 자동 승급을 다시 켰다 */
    RESUME
}
