package com.community.auth.dto.request;

/** {@code POST /api/auth/password/forgot} — 재설정 메일을 받을 이메일. {@link #toString()} 은 이메일을 싣지 않는다 */
public record PasswordForgotRequest(String email) {

    @Override
    public String toString() {
        return "PasswordForgotRequest[email=(감춤)]";
    }
}
