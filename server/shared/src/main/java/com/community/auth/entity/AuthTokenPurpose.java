package com.community.auth.entity;

/** 메일 링크 토큰의 쓰임. auth_tokens.purpose 의 CHECK 와 같은 값이다. */
public enum AuthTokenPurpose {
    /** 이메일 인증 링크 */
    VERIFY_EMAIL,
    /** 비밀번호 재설정 링크 */
    RESET_PASSWORD,
    /** 이메일 변경 링크 — 새 주소({@code new_email})로 보낸다. 인증 링크와 같은 화면 주소({@code /verify-email?token=})로 온다(V64) */
    CHANGE_EMAIL
}
