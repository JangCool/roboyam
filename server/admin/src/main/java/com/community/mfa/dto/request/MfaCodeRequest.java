package com.community.mfa.dto.request;

/** 현재 OTP 코드 한 칸(등록 확정·복구 코드 새로 만들기). 검사는 서비스가 한다 */
public record MfaCodeRequest(String code) {

    @Override
    public String toString() {
        return "MfaCodeRequest[code=(감춤)]";
    }
}
