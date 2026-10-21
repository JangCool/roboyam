package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 카탈로그에 없는 권한 코드다(400). */
public class PermissionUnknownException extends CommonException {

    public PermissionUnknownException() {
        super(HttpStatus.BAD_REQUEST, "PERMISSION_UNKNOWN", new MessageRef("error.PERMISSION_UNKNOWN"));
    }
}
