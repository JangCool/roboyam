package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 글에 처리 대기 중인 공개 요청이 이미 있다(409) — 코드 {@code GROUP_PUBLISH_REQUEST_EXISTS} */
public class GroupPublishRequestExistsException extends CommonException {

    public GroupPublishRequestExistsException() {
        super(HttpStatus.CONFLICT, "GROUP_PUBLISH_REQUEST_EXISTS", new MessageRef("error.GROUP_PUBLISH_REQUEST_EXISTS"));
    }
}
