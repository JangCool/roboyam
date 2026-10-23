package com.community.commerce.guard.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 돈 관련 요청을 1분 한도보다 많이 보냈다(429 {@code REQUEST_RATE_LIMITED}) — 지금 1분 창이 끝날 때까지의 초가 {@code retryAfterSeconds}·{@code Retry-After} 로 나간다 */
public class RequestRateLimitedException extends CommonException {

    public RequestRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "REQUEST_RATE_LIMITED", new MessageRef("error.REQUEST_RATE_LIMITED", retryAfterSeconds), Map.of(), retryAfterSeconds);
    }
}
