package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 본인인증을 마친 회원이 다시 시작·확정하려 했다(다시 인증은 없다) */
public class IdentityAlreadyVerifiedException extends CommonException {

    public IdentityAlreadyVerifiedException() {
        super(HttpStatus.CONFLICT, "IDENTITY_ALREADY_VERIFIED", new MessageRef("error.IDENTITY_ALREADY_VERIFIED"));
    }
}
