package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 가짜 PG 에 그 결제의 결과(결제함·실패)가 이미 있다. 가짜 결제창은 결제 하나에 한 번만 끝낸다 */
public class FakePgAlreadyDoneException extends CommonException {

    public FakePgAlreadyDoneException() {
        super(HttpStatus.CONFLICT, "FAKE_PG_ALREADY_DONE", new MessageRef("error.FAKE_PG_ALREADY_DONE"));
    }
}
