package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 계정 기간의 시작이 끝보다 뒤다(400) — 그러면 아무 때도 쓸 수 없는 계정이 된다. */
public class OperatorPeriodReversedException extends CommonException {

    public OperatorPeriodReversedException() {
        super(HttpStatus.BAD_REQUEST, "OPERATOR_PERIOD_REVERSED", new MessageRef("error.OPERATOR_PERIOD_REVERSED"));
    }
}
