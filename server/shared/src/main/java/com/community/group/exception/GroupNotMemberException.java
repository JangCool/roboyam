package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 멤버만 볼 수 있다(403) — 코드 {@code GROUP_NOT_MEMBER} */
public class GroupNotMemberException extends CommonException {

    public GroupNotMemberException() {
        super(HttpStatus.FORBIDDEN, "GROUP_NOT_MEMBER", new MessageRef("error.GROUP_NOT_MEMBER"));
    }
}
