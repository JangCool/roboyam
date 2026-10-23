package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 OTP 를 등록했는데 자발 등록을 시작했다(409 {@code MFA_ALREADY_ENROLLED}) — 바꾸려면 다시 등록 */
public class MfaAlreadyEnrolledException extends CommonException {

    public MfaAlreadyEnrolledException() {
        super(HttpStatus.CONFLICT, "MFA_ALREADY_ENROLLED", new MessageRef("error.MFA_ALREADY_ENROLLED"));
    }
}
