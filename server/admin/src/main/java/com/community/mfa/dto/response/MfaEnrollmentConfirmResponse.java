package com.community.mfa.dto.response;

import com.community.adminauth.dto.response.AdminMeResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 등록 확정 — 복구 코드 10개({@code XXXXX-XXXXX}, 이때 한 번만 보인다). 강제 등록(2차 대기)이면 {@code me} 도 실려 로그인이 끝난다(자발 등록은 칸이 없다).
 */
public record MfaEnrollmentConfirmResponse(List<String> recoveryCodes, @JsonInclude(JsonInclude.Include.NON_NULL) AdminMeResponse me) {

    @Override
    public String toString() {
        return "MfaEnrollmentConfirmResponse[recoveryCodes=(감춤)]";
    }
}
