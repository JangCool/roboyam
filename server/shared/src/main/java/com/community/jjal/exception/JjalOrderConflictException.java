package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 순서 저장의 ID 목록이 지금 보관함과 빠짐·겹침 없이 같지 않다(화면은 목록을 다시 받는다) */
public class JjalOrderConflictException extends CommonException {

    public JjalOrderConflictException() {
        super(HttpStatus.CONFLICT, "JJAL_ORDER_CONFLICT", new MessageRef("error.JJAL_ORDER_CONFLICT"));
    }
}
