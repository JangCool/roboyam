package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 403 — 본인인증이 필요한 기능인데 인증하지 않은 회원 */
public class IdentityRequiredException extends CommonException {

    public IdentityRequiredException() {
        super(HttpStatus.FORBIDDEN, "IDENTITY_REQUIRED", new MessageRef("error.IDENTITY_REQUIRED"));
    }
}
