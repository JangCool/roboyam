package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 무효(VOIDED)가 된 제재에는 이의제기를 할 수 없다 */
public class SanctionNotAppealableException extends CommonException {

    public static final String CODE = "SANCTION_NOT_APPEALABLE";

    public SanctionNotAppealableException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.SANCTION_NOT_APPEALABLE"));
    }
}
