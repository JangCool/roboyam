package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 연결을 시험할 수 없는 도구(서랍 · 브라우저)다(400) */
public class NotificationChannelNotTestableException extends CommonException {

    public NotificationChannelNotTestableException() {
        super(HttpStatus.BAD_REQUEST, "NOTIFICATION_CHANNEL_NOT_TESTABLE", new MessageRef("error.NOTIFICATION_CHANNEL_NOT_TESTABLE"));
    }
}
