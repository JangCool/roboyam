package com.community.sociallogin.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 IP 가 소셜 로그인 시작·가입 완료를 너무 자주 했다(429, {@code retryAfterSeconds} + {@code Retry-After}) */
public class SocialRateLimitedException extends CommonException {

    public SocialRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "SOCIAL_RATE_LIMITED", new MessageRef("error.SOCIAL_RATE_LIMITED"), Map.of(), retryAfterSeconds);
    }
}
