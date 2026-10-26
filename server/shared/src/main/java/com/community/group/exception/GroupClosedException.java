package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 모임장이 모집을 마감했다(409) — 코드 {@code GROUP_CLOSED} */
public class GroupClosedException extends CommonException {

    public GroupClosedException() {
        super(HttpStatus.CONFLICT, "GROUP_CLOSED", new MessageRef("error.GROUP_CLOSED"));
    }
}
