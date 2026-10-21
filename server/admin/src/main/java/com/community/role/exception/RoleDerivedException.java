package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 등급 역할(등급 설정에서 파생)의 이름·설명은 여기서 바꿀 수 없다(409). */
public class RoleDerivedException extends CommonException {

    public RoleDerivedException() {
        super(HttpStatus.CONFLICT, "ROLE_DERIVED", new MessageRef("error.ROLE_DERIVED"));
    }
}
