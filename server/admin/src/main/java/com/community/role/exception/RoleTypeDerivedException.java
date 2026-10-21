package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 등급 유형(파생)에는 역할을 만들 수 없다(409) — 등급 설정에서 만든다. */
public class RoleTypeDerivedException extends CommonException {

    public RoleTypeDerivedException() {
        super(HttpStatus.CONFLICT, "ROLE_TYPE_DERIVED", new MessageRef("error.ROLE_TYPE_DERIVED"));
    }
}
