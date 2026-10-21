package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 알림 이벤트다(404) — 개인 설정에서는 내가 볼 수 없는 이벤트도 같다 */
public class NotificationEventNotFoundException extends CommonException {

    public NotificationEventNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOTIFICATION_EVENT_NOT_FOUND", new MessageRef("error.NOTIFICATION_EVENT_NOT_FOUND"));
    }
}
