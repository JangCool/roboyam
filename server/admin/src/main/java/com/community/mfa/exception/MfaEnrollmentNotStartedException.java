package com.community.mfa.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 등록 중 비밀키가 없거나 10분이 지났는데 확정하려 했다(400 {@code MFA_ENROLLMENT_NOT_STARTED}) */
public class MfaEnrollmentNotStartedException extends CommonException {

    public MfaEnrollmentNotStartedException() {
        super(HttpStatus.BAD_REQUEST, "MFA_ENROLLMENT_NOT_STARTED", new MessageRef("error.MFA_ENROLLMENT_NOT_STARTED"));
    }
}
