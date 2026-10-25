package com.community.auth.dto.request;

/** {@code POST /api/auth/password/reset} — 재설정 링크의 토큰과 새 비밀번호. {@link #toString()} 은 값을 싣지 않는다 */
public record PasswordResetRequest(String token, String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[token=(감춤), newPassword=(감춤)]";
    }
}
