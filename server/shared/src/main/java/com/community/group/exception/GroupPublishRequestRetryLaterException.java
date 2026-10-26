package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 거절된 지 재요청 대기 시간이 지나지 않았다(429) — 코드 {@code GROUP_PUBLISH_REQUEST_RETRY_LATER}, {@code retryAfterSeconds} */
public class GroupPublishRequestRetryLaterException extends CommonException {

    public GroupPublishRequestRetryLaterException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "GROUP_PUBLISH_REQUEST_RETRY_LATER", new MessageRef("error.GROUP_PUBLISH_REQUEST_RETRY_LATER"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
