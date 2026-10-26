package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 끝난 일정을 고치거나 취소하려 했다(409 — 4단계) — 코드 {@code GROUP_EVENT_ENDED} */
public class GroupEventEndedException extends CommonException {

    public GroupEventEndedException() {
        super(HttpStatus.CONFLICT, "GROUP_EVENT_ENDED", new MessageRef("error.GROUP_EVENT_ENDED"));
    }
}
