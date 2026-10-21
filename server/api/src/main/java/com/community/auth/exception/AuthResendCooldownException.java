package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 인증 메일은 잠시 뒤에 다시 보낼 수 있다(429 {@code RESEND_COOLDOWN}). {@code retryAfterSeconds} 가 남은 시간이다(본문과 {@code Retry-After} 헤더) */
public class AuthResendCooldownException extends CommonException {

    public AuthResendCooldownException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RESEND_COOLDOWN", new MessageRef("error.RESEND_COOLDOWN"), Map.of(), retryAfterSeconds);
    }
}
