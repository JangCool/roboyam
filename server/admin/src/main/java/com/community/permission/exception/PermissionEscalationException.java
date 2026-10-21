package com.community.permission.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내가 가진 권한 밖의 권한은 줄 수 없다(403) — 권한 상승 방지. 권한이 든 역할을 만들거나 고치거나 운영자에게 줄 때 */
public class PermissionEscalationException extends CommonException {

    public PermissionEscalationException() {
        super(HttpStatus.FORBIDDEN, "PERMISSION_ESCALATION", new MessageRef("error.PERMISSION_ESCALATION"));
    }
}
