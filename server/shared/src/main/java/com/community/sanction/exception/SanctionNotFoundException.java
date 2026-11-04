package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 제재(회원 쪽은 내 것이 아닌 제재도) */
public class SanctionNotFoundException extends CommonException {

    public static final String CODE = "SANCTION_NOT_FOUND";

    public SanctionNotFoundException() {
        super(HttpStatus.NOT_FOUND, CODE, new MessageRef("error.SANCTION_NOT_FOUND"));
    }
}
