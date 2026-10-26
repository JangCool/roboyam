package com.community.portone.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 칸이거나 설정돼 있지 않은 칸을 지우려 했다 */
public class PortOneFieldNotFoundException extends CommonException {

    public PortOneFieldNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PORTONE_FIELD_NOT_FOUND", new MessageRef("error.PORTONE_FIELD_NOT_FOUND"));
    }
}
