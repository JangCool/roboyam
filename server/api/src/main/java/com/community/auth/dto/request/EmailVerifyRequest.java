package com.community.auth.dto.request;

/** {@code POST /api/auth/email/verify} — 메일 링크의 토큰(인증 링크·이메일 변경 링크). {@link #toString()} 은 토큰을 싣지 않는다 */
public record EmailVerifyRequest(String token) {

    @Override
    public String toString() {
        return "EmailVerifyRequest[token=(감춤)]";
    }
}
