package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 인증된 이메일이다(409 {@code ALREADY_VERIFIED}) — 인증 메일을 다시 받으려는 경우 */
public class AuthAlreadyVerifiedException extends CommonException {

    public AuthAlreadyVerifiedException() {
        super(HttpStatus.CONFLICT, "ALREADY_VERIFIED", new MessageRef("error.ALREADY_VERIFIED"));
    }
}
