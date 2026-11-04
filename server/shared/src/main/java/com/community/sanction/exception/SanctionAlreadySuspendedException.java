package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 정지 중인 회원에게 경고·기간 정지를 주려 했다(영구 정지만 된다) */
public class SanctionAlreadySuspendedException extends CommonException {

    public static final String CODE = "SANCTION_ALREADY_SUSPENDED";

    public SanctionAlreadySuspendedException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.SANCTION_ALREADY_SUSPENDED"));
    }
}
