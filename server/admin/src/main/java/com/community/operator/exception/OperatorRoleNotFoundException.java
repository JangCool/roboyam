package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 역할이거나 운영자에게 줄 수 없는 역할(회원 역할)이다(400). */
public class OperatorRoleNotFoundException extends CommonException {

    public OperatorRoleNotFoundException() {
        super(HttpStatus.BAD_REQUEST, "OPERATOR_ROLE_NOT_FOUND", new MessageRef("error.OPERATOR_ROLE_NOT_FOUND"));
    }
}
