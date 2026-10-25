package com.community.dm.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 대화·남의 대화·형식이 틀린 ID(404 {@code DM_THREAD_NOT_FOUND}) — 남의 대화가 있는지 알리지 않는다 */
public class DmThreadNotFoundException extends CommonException {

    public DmThreadNotFoundException() {
        super(HttpStatus.NOT_FOUND, "DM_THREAD_NOT_FOUND", new MessageRef("error.DM_THREAD_NOT_FOUND"));
    }
}
