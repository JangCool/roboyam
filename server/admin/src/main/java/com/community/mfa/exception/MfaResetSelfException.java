package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 루트가 화면에서 자기 OTP 를 초기화하려 했다(409 {@code MFA_RESET_SELF}) — 서버 접근자의 초기화 명령으로만 */
public class MfaResetSelfException extends CommonException {

    public MfaResetSelfException() {
        super(HttpStatus.CONFLICT, "MFA_RESET_SELF", new MessageRef("error.MFA_RESET_SELF"));
    }
}
