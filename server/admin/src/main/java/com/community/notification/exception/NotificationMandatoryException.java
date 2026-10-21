package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 필수 알림 이벤트(보안)는 끌 수 없다(409) — 이벤트 관리에서도 개인 설정에서도 같다 */
public class NotificationMandatoryException extends CommonException {

    public NotificationMandatoryException() {
        super(HttpStatus.CONFLICT, "NOTIFICATION_MANDATORY", new MessageRef("error.NOTIFICATION_MANDATORY"));
    }
}
