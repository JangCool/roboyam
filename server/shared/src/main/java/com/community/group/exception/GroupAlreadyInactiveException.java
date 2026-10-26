package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 비활성이다(그 사이 다른 운영자가 처리했다)(409) — 코드 {@code GROUP_ALREADY_INACTIVE} */
public class GroupAlreadyInactiveException extends CommonException {

    public GroupAlreadyInactiveException() {
        super(HttpStatus.CONFLICT, "GROUP_ALREADY_INACTIVE", new MessageRef("error.GROUP_ALREADY_INACTIVE"));
    }
}
