package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 IP 가 같은 글·댓글의 비밀번호를 너무 여러 번 틀림(429) — {@code GuestPasswordGuard} */
public class PasswordAttemptsExceededException extends CommonException {

    public PasswordAttemptsExceededException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "PASSWORD_ATTEMPTS_EXCEEDED", new MessageRef("error.PASSWORD_ATTEMPTS_EXCEEDED"), Map.of(), retryAfterSeconds);
    }
}
