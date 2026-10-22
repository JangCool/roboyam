package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 연결 시험을 너무 자주 했다(429) — 운영자당 분당 횟수 제한. 다시 시도할 수 있기까지의 초가 {@code retryAfterSeconds} 와 {@code Retry-After} 로 나간다 */
public class NotificationChannelTestRateLimitedException extends CommonException {

    public NotificationChannelTestRateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "NOTIFICATION_CHANNEL_TEST_RATE_LIMITED", new MessageRef("error.NOTIFICATION_CHANNEL_TEST_RATE_LIMITED"), Map.of(), retryAfterSeconds);
    }
}
