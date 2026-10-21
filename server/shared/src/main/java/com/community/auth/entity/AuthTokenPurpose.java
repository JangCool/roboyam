package com.community.auth.entity;

/** 메일 링크 토큰의 쓰임. auth_tokens.purpose 의 CHECK 와 같은 값이다. */
public enum AuthTokenPurpose {
    /** 이메일 인증 링크 */
    VERIFY_EMAIL,
    /** 비밀번호 재설정 링크 */
    RESET_PASSWORD
}
