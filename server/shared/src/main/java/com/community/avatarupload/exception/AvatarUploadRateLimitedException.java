package com.community.avatarupload.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 아바타 올리기 속도 제한(429 {@code RATE_LIMITED} — 간격 10초 · 하루 10번, {@code retryAfterSeconds} + {@code Retry-After}) */
public class AvatarUploadRateLimitedException extends CommonException {

    public AvatarUploadRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", new MessageRef("error.RATE_LIMITED"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
