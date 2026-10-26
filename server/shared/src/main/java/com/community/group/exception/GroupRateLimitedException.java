package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 하루(24시간) 개설 신청 · 가입·신청 한도를 넘었다(429 {@code RATE_LIMITED}, {@code retryAfterSeconds} — 가장 오래된 시도가 24시간을 넘길 때까지) */
public class GroupRateLimitedException extends CommonException {

    public GroupRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", new MessageRef("error.GROUP_RATE_LIMITED"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
