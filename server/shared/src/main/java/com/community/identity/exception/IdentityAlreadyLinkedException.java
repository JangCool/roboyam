package com.community.identity.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 같은 사람(DI 또는 CI)이 이미 다른 계정에 묶여 있다(1인 1계정) */
public class IdentityAlreadyLinkedException extends CommonException {

    public IdentityAlreadyLinkedException() {
        super(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED", new MessageRef("error.IDENTITY_ALREADY_LINKED"));
    }
}
