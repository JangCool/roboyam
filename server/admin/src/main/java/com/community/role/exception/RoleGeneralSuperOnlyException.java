package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 종합 관리 역할은 최고 관리자만 만들고 고치고 줄 수 있다(403) — 권한 상승 방지. */
public class RoleGeneralSuperOnlyException extends CommonException {

    public RoleGeneralSuperOnlyException() {
        super(HttpStatus.FORBIDDEN, "ROLE_GENERAL_SUPER_ONLY", new MessageRef("error.ROLE_GENERAL_SUPER_ONLY"));
    }
}
