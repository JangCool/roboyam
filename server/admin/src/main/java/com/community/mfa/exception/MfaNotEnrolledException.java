package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** OTP 를 등록하지 않았는데 등록이 필요한 일(복구 코드 새로 만들기)을 하려 했다(409 {@code MFA_NOT_ENROLLED}) */
public class MfaNotEnrolledException extends CommonException {

    public MfaNotEnrolledException() {
        super(HttpStatus.CONFLICT, "MFA_NOT_ENROLLED", new MessageRef("error.MFA_NOT_ENROLLED"));
    }
}
