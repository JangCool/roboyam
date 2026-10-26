package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 멤버다(409) — 코드 {@code GROUP_ALREADY_MEMBER} */
public class GroupAlreadyMemberException extends CommonException {

    public GroupAlreadyMemberException() {
        super(HttpStatus.CONFLICT, "GROUP_ALREADY_MEMBER", new MessageRef("error.GROUP_ALREADY_MEMBER"));
    }
}
