package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 영구 정지된 회원 */
public class SanctionAlreadyBannedException extends CommonException {

    public static final String CODE = "SANCTION_ALREADY_BANNED";

    public SanctionAlreadyBannedException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.SANCTION_ALREADY_BANNED"));
    }
}
