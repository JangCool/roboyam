package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 역할 유형이다(404). */
public class RoleTypeNotFoundException extends CommonException {

    public RoleTypeNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ROLE_TYPE_NOT_FOUND", new MessageRef("error.ROLE_TYPE_NOT_FOUND"));
    }
}
