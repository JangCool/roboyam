package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 내 보관함에 있는 짤(담기 · 같은 파일 올리기) */
public class JjalAlreadyInBoxException extends CommonException {

    public JjalAlreadyInBoxException() {
        super(HttpStatus.CONFLICT, "JJAL_ALREADY_IN_BOX", new MessageRef("error.JJAL_ALREADY_IN_BOX"));
    }
}
