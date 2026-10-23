package com.community.media.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 글 이미지를 1분 안에 너무 많이 올렸다(429, {@code retryAfterSeconds} + {@code Retry-After}) */
public class ImageRateLimitedException extends CommonException {

    public ImageRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "IMAGE_RATE_LIMITED", new MessageRef("error.IMAGE_RATE_LIMITED"), Map.of(), retryAfterSeconds);
    }
}
