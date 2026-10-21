package com.community.user.entity;

/** 회원 권한(계정 구분). users.role 의 CHECK 와 같은 값이다. */
public enum Role {
    USER,
    /** 운영진 */
    STAFF,
    /** 운영자 */
    ADMIN
}
