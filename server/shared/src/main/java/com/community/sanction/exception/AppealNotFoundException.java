package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 이의제기 */
public class AppealNotFoundException extends CommonException {

    public static final String CODE = "APPEAL_NOT_FOUND";

    public AppealNotFoundException() {
        super(HttpStatus.NOT_FOUND, CODE, new MessageRef("error.APPEAL_NOT_FOUND"));
    }
}
