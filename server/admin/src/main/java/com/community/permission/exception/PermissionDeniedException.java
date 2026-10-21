package com.community.permission.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 일을 할 권한이 없다(403). 받은 역할의 권한 합집합에 필요한 권한이 없을 때 */
public class PermissionDeniedException extends CommonException {

    public PermissionDeniedException() {
        super(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", new MessageRef("error.PERMISSION_DENIED"));
    }
}
