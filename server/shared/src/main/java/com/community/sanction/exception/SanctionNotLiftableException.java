package com.community.sanction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 진행 중인 정지·영구 정지가 아니다(경고 · 이미 끝남·풀림·무효) */
public class SanctionNotLiftableException extends CommonException {

    public static final String CODE = "SANCTION_NOT_LIFTABLE";

    public SanctionNotLiftableException() {
        super(HttpStatus.CONFLICT, CODE, new MessageRef("error.SANCTION_NOT_LIFTABLE"));
    }
}
