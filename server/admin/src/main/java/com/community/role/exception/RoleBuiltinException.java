package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 제품이 넣은 내장 역할은 지울 수 없다(409). 권한은 조정할 수 있다. */
public class RoleBuiltinException extends CommonException {

    public RoleBuiltinException() {
        super(HttpStatus.CONFLICT, "ROLE_BUILTIN", new MessageRef("error.ROLE_BUILTIN"));
    }
}
