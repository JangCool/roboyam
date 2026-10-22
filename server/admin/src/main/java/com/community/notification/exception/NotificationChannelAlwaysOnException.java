package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 서랍은 항상 켜져 있어 끌 수 없다(409) */
public class NotificationChannelAlwaysOnException extends CommonException {

    public NotificationChannelAlwaysOnException() {
        super(HttpStatus.CONFLICT, "NOTIFICATION_CHANNEL_ALWAYS_ON", new MessageRef("error.NOTIFICATION_CHANNEL_ALWAYS_ON"));
    }
}
