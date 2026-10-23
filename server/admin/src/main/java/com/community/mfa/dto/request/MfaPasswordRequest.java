package com.community.mfa.dto.request;

/** OTP 다시 등록 — 현재 비밀번호 */
public record MfaPasswordRequest(String password) {

    @Override
    public String toString() {
        return "MfaPasswordRequest[password=(감춤)]";
    }
}
