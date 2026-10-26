package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 비공개 소모임의 일반 작성자가 직접 공개하려 했다 — 공개 요청을 보내야 한다(409, 기능 켜짐) — 코드 {@code GROUP_PUBLISH_REQUEST_REQUIRED} */
public class GroupPublishRequestRequiredException extends CommonException {

    public GroupPublishRequestRequiredException() {
        super(HttpStatus.CONFLICT, "GROUP_PUBLISH_REQUEST_REQUIRED", new MessageRef("error.GROUP_PUBLISH_REQUEST_REQUIRED"));
    }
}
