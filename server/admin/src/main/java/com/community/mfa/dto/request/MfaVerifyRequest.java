package com.community.mfa.dto.request;

/**
 * 로그인 2단계 — OTP({@code code}, 6자리) 또는 복구 코드({@code recoveryCode}, {@code XXXXX-XXXXX}) 중 하나. 검사는 서비스가 한다(칸 경로 {@code code}·{@code recoveryCode}).
 *
 * @param rememberDevice 「이 기기에서 N일 동안 다시 묻지 않기」(없으면 false)
 */
public record MfaVerifyRequest(String code, String recoveryCode, Boolean rememberDevice) {

    @Override
    public String toString() {
        return "MfaVerifyRequest[code=(감춤), recoveryCode=(감춤), rememberDevice=" + rememberDevice + "]";
    }
}
