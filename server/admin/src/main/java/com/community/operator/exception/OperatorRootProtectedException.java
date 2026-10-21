package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 루트 계정(최고 관리자)은 역할·상태·기간·반려 어떤 변경도 할 수 없다(409). */
public class OperatorRootProtectedException extends CommonException {

    public OperatorRootProtectedException() {
        super(HttpStatus.CONFLICT, "OPERATOR_ROOT_PROTECTED", new MessageRef("error.OPERATOR_ROOT_PROTECTED"));
    }
}
