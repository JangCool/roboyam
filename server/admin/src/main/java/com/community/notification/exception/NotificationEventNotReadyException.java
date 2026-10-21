package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 아직 발행처가 없는(준비 중) 알림 이벤트는 설정을 바꿀 수 없다(409) */
public class NotificationEventNotReadyException extends CommonException {

    public NotificationEventNotReadyException() {
        super(HttpStatus.CONFLICT, "NOTIFICATION_EVENT_NOT_READY", new MessageRef("error.NOTIFICATION_EVENT_NOT_READY"));
    }
}
