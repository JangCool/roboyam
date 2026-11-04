package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 답변한 이의제기(동시 처리는 행 잠금으로 한 번만) */
public class AppealClosedException extends CommonException {

    public static final String CODE = "APPEAL_CLOSED";

    public AppealClosedException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.APPEAL_CLOSED"));
    }
}
