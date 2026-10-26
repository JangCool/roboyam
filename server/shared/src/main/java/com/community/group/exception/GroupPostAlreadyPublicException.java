package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 비멤버에게 보이는 글이다(409) — 코드 {@code GROUP_POST_ALREADY_PUBLIC} */
public class GroupPostAlreadyPublicException extends CommonException {

    public GroupPostAlreadyPublicException() {
        super(HttpStatus.CONFLICT, "GROUP_POST_ALREADY_PUBLIC", new MessageRef("error.GROUP_POST_ALREADY_PUBLIC"));
    }
}
