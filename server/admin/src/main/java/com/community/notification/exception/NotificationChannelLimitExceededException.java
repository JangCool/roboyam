package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 알림 도구를 더 만들 수 없다(409) — 도구 수 상한. 한도는 문구 인자로 알린다 */
public class NotificationChannelLimitExceededException extends CommonException {

    public NotificationChannelLimitExceededException(int limit) {
        super(HttpStatus.CONFLICT, "NOTIFICATION_CHANNEL_LIMIT_EXCEEDED", new MessageRef("error.NOTIFICATION_CHANNEL_LIMIT_EXCEEDED", String.valueOf(limit)));
    }
}
