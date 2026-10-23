package com.community.commerce.guard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** Idempotency-Key 가 형식(영문·숫자·밑줄·하이픈 8~64자)에 맞지 않는다(400) */
public class IdempotencyKeyInvalidException extends CommonException {

    public IdempotencyKeyInvalidException() {
        super(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", new MessageRef("error.IDEMPOTENCY_KEY_INVALID"));
    }
}
