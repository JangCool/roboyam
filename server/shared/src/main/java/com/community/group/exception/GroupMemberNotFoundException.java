package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그 회원은 이 소모임의 지금 멤버가 아니다(404) — 코드 {@code GROUP_MEMBER_NOT_FOUND} */
public class GroupMemberNotFoundException extends CommonException {

    public GroupMemberNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GROUP_MEMBER_NOT_FOUND", new MessageRef("error.GROUP_MEMBER_NOT_FOUND"));
    }
}
