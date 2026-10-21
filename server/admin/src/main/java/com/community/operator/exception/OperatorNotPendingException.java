package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 승인 대기가 아닌 계정은 반려할 수 없다(409). 이미 승인됐거나 처리된 신청이다. */
public class OperatorNotPendingException extends CommonException {

    public OperatorNotPendingException() {
        super(HttpStatus.CONFLICT, "OPERATOR_NOT_PENDING", new MessageRef("error.OPERATOR_NOT_PENDING"));
    }
}
