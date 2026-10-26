package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 모임장·「글 공개 처리·되돌리기」 매니저는 요청 없이 직접 공개한다(409) — 코드 {@code GROUP_PUBLISH_REQUEST_NOT_NEEDED} */
public class GroupPublishRequestNotNeededException extends CommonException {

    public GroupPublishRequestNotNeededException() {
        super(HttpStatus.CONFLICT, "GROUP_PUBLISH_REQUEST_NOT_NEEDED", new MessageRef("error.GROUP_PUBLISH_REQUEST_NOT_NEEDED"));
    }
}
