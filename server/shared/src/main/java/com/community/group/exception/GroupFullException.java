package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 정원이 찼다(409) — 코드 {@code GROUP_FULL} */
public class GroupFullException extends CommonException {

    public GroupFullException() {
        super(HttpStatus.CONFLICT, "GROUP_FULL", new MessageRef("error.GROUP_FULL"));
    }
}
