package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 이미 등록된 운영자 이메일이다(409). 이메일 칸에 문구가 붙는다. */
public class OperatorEmailDuplicatedException extends CommonException {

    public OperatorEmailDuplicatedException() {
        super(HttpStatus.CONFLICT, "OPERATOR_EMAIL_DUPLICATED", new MessageRef("error.OPERATOR_EMAIL_DUPLICATED"), Map.of("email", new MessageRef("operator.email.duplicated")), null);
    }
}
