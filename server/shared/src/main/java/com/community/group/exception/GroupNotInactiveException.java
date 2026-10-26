package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 열려 있다(409) — 코드 {@code GROUP_NOT_INACTIVE} */
public class GroupNotInactiveException extends CommonException {

    public GroupNotInactiveException() {
        super(HttpStatus.CONFLICT, "GROUP_NOT_INACTIVE", new MessageRef("error.GROUP_NOT_INACTIVE"));
    }
}
