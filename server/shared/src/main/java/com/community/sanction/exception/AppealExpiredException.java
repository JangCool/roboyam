package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 제재 시작 후 30일이 지나 이의제기를 받지 않는다(결정 3) */
public class AppealExpiredException extends CommonException {

    public static final String CODE = "APPEAL_EXPIRED";

    public AppealExpiredException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.APPEAL_EXPIRED"));
    }
}
