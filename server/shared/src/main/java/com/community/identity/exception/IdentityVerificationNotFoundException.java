package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 인증 번호(남의 인증 번호도 같은 응답 — 있는지 알리지 않는다) */
public class IdentityVerificationNotFoundException extends CommonException {

    public IdentityVerificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "IDENTITY_VERIFICATION_NOT_FOUND", new MessageRef("error.IDENTITY_VERIFICATION_NOT_FOUND"));
    }
}
