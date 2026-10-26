package com.community.commerce.guard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 돈 관련 요청에 Idempotency-Key 헤더가 없다(400) */
public class IdempotencyKeyRequiredException extends CommonException {

    public IdempotencyKeyRequiredException() {
        super(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", new MessageRef("error.IDEMPOTENCY_KEY_REQUIRED"));
    }
}
