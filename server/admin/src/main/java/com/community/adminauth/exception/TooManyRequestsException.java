package com.community.adminauth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 승인번호 요청이 너무 잦다(429 {@code TOO_MANY_REQUESTS}). {@code retryAfterSeconds} 와 {@code Retry-After} 헤더로 남은 시간을 알린다 */
public class TooManyRequestsException extends CommonException {

    public TooManyRequestsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", new MessageRef("error.TOO_MANY_REQUESTS"), Map.of(), retryAfterSeconds);
    }
}
