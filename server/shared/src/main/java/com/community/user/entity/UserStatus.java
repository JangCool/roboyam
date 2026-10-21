package com.community.user.entity;

/** 회원 상태. users.status 의 CHECK 와 같은 값이다. */
public enum UserStatus {
    ACTIVE,
    /** 기간 정지 */
    SUSPENDED,
    /** 영구 정지 */
    BANNED,
    /** 탈퇴 — 로그인할 수 없다 */
    WITHDRAWN
}
