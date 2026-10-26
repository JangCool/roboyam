package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없거나 다른 소모임의 일정이다(404 — 4단계) — 코드 {@code GROUP_EVENT_NOT_FOUND} */
public class GroupEventNotFoundException extends CommonException {

    public GroupEventNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GROUP_EVENT_NOT_FOUND", new MessageRef("error.GROUP_EVENT_NOT_FOUND"));
    }
}
