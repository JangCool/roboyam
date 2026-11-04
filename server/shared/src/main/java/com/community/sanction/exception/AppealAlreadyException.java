package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 그 제재에 이미 이의제기를 접수했다(기각 뒤에도) */
public class AppealAlreadyException extends CommonException {

    public static final String CODE = "APPEAL_ALREADY";

    public AppealAlreadyException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.APPEAL_ALREADY"));
    }
}
