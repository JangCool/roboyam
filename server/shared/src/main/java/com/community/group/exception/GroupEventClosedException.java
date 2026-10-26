package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 참석 응답이 마감된(시작했거나 끝난) 일정에 응답하려 했다(409 — 4단계) — 코드 {@code GROUP_EVENT_CLOSED} */
public class GroupEventClosedException extends CommonException {

    public GroupEventClosedException() {
        super(HttpStatus.CONFLICT, "GROUP_EVENT_CLOSED", new MessageRef("error.GROUP_EVENT_CLOSED"));
    }
}
