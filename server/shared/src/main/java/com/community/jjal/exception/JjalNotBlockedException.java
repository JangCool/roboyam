package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 되돌릴 짤이 내려가 있지 않다(관리자 짤 되돌림) */
public class JjalNotBlockedException extends CommonException {

    public JjalNotBlockedException() {
        super(HttpStatus.CONFLICT, "JJAL_NOT_BLOCKED", new MessageRef("error.JJAL_NOT_BLOCKED"));
    }
}
