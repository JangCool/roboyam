package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그 역할이 속한 유형의 권한 후보에 없는 권한이다(400). */
public class RoleTypeMismatchException extends CommonException {

    public RoleTypeMismatchException() {
        super(HttpStatus.BAD_REQUEST, "ROLE_TYPE_MISMATCH", new MessageRef("error.ROLE_TYPE_MISMATCH"));
    }
}
