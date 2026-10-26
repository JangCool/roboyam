package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 공개 요청이 없거나 작성자가 거뒀다(404) — 코드 {@code GROUP_PUBLISH_REQUEST_NOT_FOUND} */
public class GroupPublishRequestNotFoundException extends CommonException {

    public GroupPublishRequestNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GROUP_PUBLISH_REQUEST_NOT_FOUND", new MessageRef("error.GROUP_PUBLISH_REQUEST_NOT_FOUND"));
    }
}
