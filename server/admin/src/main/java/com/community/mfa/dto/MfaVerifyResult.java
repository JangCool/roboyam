package com.community.mfa.dto;

/**
 * 로그인 2단계 통과.
 *
 * @param method            TOTP · RECOVERY_CODE
 * @param recoveryCodesLeft 복구 코드로 들어왔을 때 남은 개수. 그 밖은 null
 * @param trustToken        「이 기기에서 다시 묻지 않기」를 골랐을 때 쿠키에 실을 값(256비트, DB 에는 해시만). 아니면 null — {@link #toString()} 에 싣지 않는다
 * @param trustDays         신뢰 기기 쿠키의 기한(일)
 */
public record MfaVerifyResult(MfaLoginMethod method, Integer recoveryCodesLeft, String trustToken, int trustDays) {

    @Override
    public String toString() {
        return "MfaVerifyResult[method=" + method + ", recoveryCodesLeft=" + recoveryCodesLeft + ", remember=" + (trustToken != null) + "]";
    }
}
