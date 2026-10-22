package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 알림 도구다(404). 이벤트의 전달 도구로 보낸 값이 틀린 경우는 {@link NotificationChannelReferenceNotFoundException}(400) */
public class NotificationChannelNotFoundException extends CommonException {

    public NotificationChannelNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOTIFICATION_CHANNEL_NOT_FOUND", new MessageRef("error.NOTIFICATION_CHANNEL_NOT_FOUND"));
    }
}
