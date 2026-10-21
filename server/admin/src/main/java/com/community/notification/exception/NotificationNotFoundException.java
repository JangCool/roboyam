package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내 알림이 아니거나 없는 알림이다(404) — 남의 알림과 없는 알림을 구분하지 않는다 */
public class NotificationNotFoundException extends CommonException {

    public NotificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", new MessageRef("error.NOTIFICATION_NOT_FOUND"));
    }
}
