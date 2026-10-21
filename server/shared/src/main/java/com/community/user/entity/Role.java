package com.community.user.entity;

/**
 * 회원 계정 구분. users.role 의 CHECK 와 같은 값이다.
 *
 * <p>운영자(관리자 앱 계정)는 회원이 아니라 별도 테이블 {@code operators} 다 — 회원에는 운영자 값(옛 STAFF·ADMIN)을 두지 않는다
 * (docs/domain/operator-permissions.md 확정 규칙 1, V8__operators_split_from_users.sql).
 */
public enum Role {
    USER
}
