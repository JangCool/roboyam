package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 로그인을 여러 번 틀려 잠겼다(429 {@code LOGIN_LOCKED}). 같은 이메일로 연속 5회 틀리면 10분 잠기고, 잠긴 동안은 맞는 비밀번호도 거절한다.
 * {@code retryAfterSeconds} 뒤에 다시 시도할 수 있다(응답 본문과 {@code Retry-After} 헤더).
 */
public class AuthLoginLockedException extends CommonException {

    public AuthLoginLockedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED", new MessageRef("error.LOGIN_LOCKED"),
                Map.of(), retryAfterSeconds);
    }
}
