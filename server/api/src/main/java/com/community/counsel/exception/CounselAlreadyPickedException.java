package com.community.counsel.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 조언을 골랐다 — 다시 고르기는 없다(409) */
public class CounselAlreadyPickedException extends CommonException {

    public CounselAlreadyPickedException() {
        super(HttpStatus.CONFLICT, "COUNSEL_ALREADY_PICKED", new MessageRef("error.COUNSEL_ALREADY_PICKED"));
    }
}
