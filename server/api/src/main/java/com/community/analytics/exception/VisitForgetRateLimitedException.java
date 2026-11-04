package com.community.analytics.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 IP 가 방문 기록 삭제를 너무 자주 요청했다(429) — {@code VisitForgetRateLimiter} */
public class VisitForgetRateLimitedException extends CommonException {

    public VisitForgetRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "VISIT_FORGET_RATE_LIMITED", new MessageRef("error.VISIT_FORGET_RATE_LIMITED"), Map.of(), retryAfterSeconds);
    }
}
