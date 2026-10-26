package com.community.restriction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 해제됐거나 기간이 끝난 기능 제한은 해제할 수 없다 */
public class RestrictionNotActiveException extends CommonException {

    public RestrictionNotActiveException() {
        super(HttpStatus.CONFLICT, "RESTRICTION_NOT_ACTIVE", new MessageRef("error.RESTRICTION_NOT_ACTIVE"));
    }
}
