package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 다음 단계보다 낮은 제재 */
public class SanctionStepTooLowException extends CommonException {

    public static final String CODE = "SANCTION_STEP_TOO_LOW";

    public SanctionStepTooLowException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.SANCTION_STEP_TOO_LOW"));
    }
}
