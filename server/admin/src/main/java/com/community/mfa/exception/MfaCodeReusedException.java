package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 맞는 OTP 지만 이미 받아들인 시간 칸(또는 그 이전)의 코드다 — 같은 코드 재사용·옛 코드(400 {@code MFA_CODE_REUSED}). 실패 횟수에 세지 않는다 */
public class MfaCodeReusedException extends CommonException {

    public MfaCodeReusedException() {
        super(HttpStatus.BAD_REQUEST, "MFA_CODE_REUSED", new MessageRef("error.MFA_CODE_REUSED"));
    }
}
