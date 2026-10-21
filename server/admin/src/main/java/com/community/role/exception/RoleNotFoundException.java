package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 역할이다(404). */
public class RoleNotFoundException extends CommonException {

    public RoleNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", new MessageRef("error.ROLE_NOT_FOUND"));
    }
}
