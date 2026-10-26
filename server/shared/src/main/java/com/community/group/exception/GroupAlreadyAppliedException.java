package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 가입 신청을 했다(409) — 코드 {@code GROUP_ALREADY_APPLIED} */
public class GroupAlreadyAppliedException extends CommonException {

    public GroupAlreadyAppliedException() {
        super(HttpStatus.CONFLICT, "GROUP_ALREADY_APPLIED", new MessageRef("error.GROUP_ALREADY_APPLIED"));
    }
}
