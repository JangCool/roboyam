package com.community.adminauth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 로그인을 여러 번 실패해 잠시 잠겼다(429). {@code retryAfterSeconds} 와 {@code Retry-After} 헤더로 남은 시간을 알린다 */
public class AdminLoginLockedException extends CommonException {

    public AdminLoginLockedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED", new MessageRef("error.LOGIN_LOCKED"), Map.of(), retryAfterSeconds);
    }
}
