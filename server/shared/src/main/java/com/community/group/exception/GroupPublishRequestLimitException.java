package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 글의 거절 한도에 닿아 더는 요청할 수 없다(409 — 모임장·매니저는 직접 공개할 수 있다) — 코드 {@code GROUP_PUBLISH_REQUEST_LIMIT} */
public class GroupPublishRequestLimitException extends CommonException {

    public GroupPublishRequestLimitException() {
        super(HttpStatus.CONFLICT, "GROUP_PUBLISH_REQUEST_LIMIT", new MessageRef("error.GROUP_PUBLISH_REQUEST_LIMIT"));
    }
}
