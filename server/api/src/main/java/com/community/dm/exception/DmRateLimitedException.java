package com.community.dm.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 연속으로 너무 빠르게 보냄(429 {@code RATE_LIMITED}, {@code retryAfterSeconds} + {@code Retry-After}) */
public class DmRateLimitedException extends CommonException {

    public DmRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", new MessageRef("error.RATE_LIMITED"), Map.of(), retryAfterSeconds);
    }
}
