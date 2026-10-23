package com.community.commerce.guard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 Idempotency-Key 의 요청이 아직 처리 중이다(409) — 끝난 뒤 같은 키로 다시 보내면 처음 응답을 받는다 */
public class IdempotencyInProgressException extends CommonException {

    public IdempotencyInProgressException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", new MessageRef("error.IDEMPOTENCY_IN_PROGRESS"));
    }
}
