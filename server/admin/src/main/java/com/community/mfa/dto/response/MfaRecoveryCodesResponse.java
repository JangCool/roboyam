package com.community.mfa.dto.response;

import java.util.List;

/** 복구 코드 새로 만들기 — 새 코드 10개(옛 코드는 모두 무효) */
public record MfaRecoveryCodesResponse(List<String> recoveryCodes) {

    @Override
    public String toString() {
        return "MfaRecoveryCodesResponse[recoveryCodes=(감춤)]";
    }
}
