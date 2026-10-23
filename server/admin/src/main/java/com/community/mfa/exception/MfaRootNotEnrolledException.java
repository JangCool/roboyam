package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 루트 본인이 OTP 를 등록하지 않았는데 2차 인증을 켜려 했다(409 {@code MFA_ROOT_NOT_ENROLLED}) — 잠김 방지 */
public class MfaRootNotEnrolledException extends CommonException {

    public MfaRootNotEnrolledException() {
        super(HttpStatus.CONFLICT, "MFA_ROOT_NOT_ENROLLED", new MessageRef("error.MFA_ROOT_NOT_ENROLLED"));
    }
}
