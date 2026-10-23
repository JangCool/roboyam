package com.community.commerce.guard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 Idempotency-Key 로 다른 본문(또는 다른 요청 종류)을 보냈다(409) */
public class IdempotencyKeyReusedException extends CommonException {

    public IdempotencyKeyReusedException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", new MessageRef("error.IDEMPOTENCY_KEY_REUSED"));
    }
}
