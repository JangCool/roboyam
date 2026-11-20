package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 내 보관함 상한(내려간 짤 포함)이 찼다. 최상위 {@code limit} */
public class JjalLimitReachedException extends CommonException {

    public JjalLimitReachedException(int limit) {
        super(HttpStatus.CONFLICT, "JJAL_LIMIT_REACHED", new MessageRef("error.JJAL_LIMIT_REACHED", limit));
        extra("limit", limit);
    }
}
