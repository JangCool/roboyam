package com.community.role.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 등급 역할(등급 설정에서 파생)의 이름·설명은 여기서 바꾸지 못하고 직접 지울 수도 없다(409) — 등급 설정이 만들고 지운다. */
public class RoleDerivedException extends CommonException {

    public RoleDerivedException() {
        super(HttpStatus.CONFLICT, "ROLE_DERIVED", new MessageRef("error.ROLE_DERIVED"));
    }
}
