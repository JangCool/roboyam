package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 413 — 올린 파일이 용량 한도(기본 2MB)를 넘는다 */
public class JjalTooLargeException extends CommonException {

    public JjalTooLargeException(long maxMegabytes) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "JJAL_TOO_LARGE", new MessageRef("error.JJAL_TOO_LARGE", maxMegabytes));
    }
}
