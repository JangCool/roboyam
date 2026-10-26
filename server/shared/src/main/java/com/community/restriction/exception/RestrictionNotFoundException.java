package com.community.restriction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 그 회원의 기능 제한이 아니거나 없다 */
public class RestrictionNotFoundException extends CommonException {

    public RestrictionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "RESTRICTION_NOT_FOUND", new MessageRef("error.RESTRICTION_NOT_FOUND"));
    }
}
