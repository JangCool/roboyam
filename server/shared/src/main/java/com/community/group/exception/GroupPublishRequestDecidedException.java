package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 승인·거절된 공개 요청이다(409) — 코드 {@code GROUP_PUBLISH_REQUEST_DECIDED} */
public class GroupPublishRequestDecidedException extends CommonException {

    public GroupPublishRequestDecidedException() {
        super(HttpStatus.CONFLICT, "GROUP_PUBLISH_REQUEST_DECIDED", new MessageRef("error.GROUP_PUBLISH_REQUEST_DECIDED"));
    }
}
