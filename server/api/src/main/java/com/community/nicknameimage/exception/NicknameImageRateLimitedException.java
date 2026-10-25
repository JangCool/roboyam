package com.community.nicknameimage.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 닉네임 그림 올리기 속도 제한(429 {@code RATE_LIMITED} — 간격 10초 · 하루 10번, {@code retryAfterSeconds} + {@code Retry-After}) */
public class NicknameImageRateLimitedException extends CommonException {

    public NicknameImageRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", new MessageRef("error.RATE_LIMITED"), Map.of(), Math.max(1, retryAfterSeconds));
    }
}
