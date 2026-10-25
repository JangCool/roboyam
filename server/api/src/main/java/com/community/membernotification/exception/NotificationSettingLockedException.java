package com.community.membernotification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 회원이 끌 수 없는 알림(코드 잠금 또는 운영자가 「회원이 끌 수 있음」을 끔)을 끄려 했다(409) */
public class NotificationSettingLockedException extends CommonException {

    public NotificationSettingLockedException() {
        super(HttpStatus.CONFLICT, "NOTIFICATION_SETTING_LOCKED", new MessageRef("error.NOTIFICATION_SETTING_LOCKED"));
    }
}
