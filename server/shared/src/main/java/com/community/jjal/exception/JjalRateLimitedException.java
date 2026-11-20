package com.community.jjal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 429 — 1분 올리기 한도(기본 10장, DB 에서 센다). {@code retryAfterSeconds} */
public class JjalRateLimitedException extends CommonException {

    public JjalRateLimitedException(int perMinute, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "JJAL_RATE_LIMITED", new MessageRef("error.JJAL_RATE_LIMITED", perMinute), Map.of(), retryAfterSeconds);
    }
}
