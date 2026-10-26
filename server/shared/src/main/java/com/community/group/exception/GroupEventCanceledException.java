package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 취소된 일정을 고치거나 거기에 응답하려 했다(409 — 4단계) — 코드 {@code GROUP_EVENT_CANCELED} */
public class GroupEventCanceledException extends CommonException {

    public GroupEventCanceledException() {
        super(HttpStatus.CONFLICT, "GROUP_EVENT_CANCELED", new MessageRef("error.GROUP_EVENT_CANCELED"));
    }
}
