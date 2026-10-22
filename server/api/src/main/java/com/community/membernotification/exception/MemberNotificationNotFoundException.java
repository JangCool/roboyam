package com.community.membernotification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 알림이거나 내 알림이 아니다(404) — 남의 알림이 있는지 알리지 않는다. 형식이 틀린 ID 도 같다 */
public class MemberNotificationNotFoundException extends CommonException {

    public MemberNotificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", new MessageRef("error.NOTIFICATION_NOT_FOUND"));
    }
}
