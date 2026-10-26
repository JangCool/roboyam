package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 지난 24시간의 공개 요청 수가 하루 한도(관리자 설정 dailyLimit)에 닿았다(429 {@code RATE_LIMITED}, {@code retryAfterSeconds} — 가장 오래된 요청이 24시간을 넘길 때까지) */
public class GroupPublishRateLimitedException extends CommonException {

    public GroupPublishRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", new MessageRef("error.GROUP_PUBLISH_RATE_LIMITED"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
