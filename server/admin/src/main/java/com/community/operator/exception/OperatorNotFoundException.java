package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 운영자다(404). */
public class OperatorNotFoundException extends CommonException {

    public OperatorNotFoundException() {
        super(HttpStatus.NOT_FOUND, "OPERATOR_NOT_FOUND", new MessageRef("error.OPERATOR_NOT_FOUND"));
    }
}
