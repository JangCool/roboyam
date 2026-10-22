package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 기본 제공 도구(서랍·이메일·브라우저)는 새로 만들거나 지울 수 없다(409) */
public class NotificationChannelBuiltinException extends CommonException {

    public NotificationChannelBuiltinException() {
        super(HttpStatus.CONFLICT, "NOTIFICATION_CHANNEL_BUILTIN", new MessageRef("error.NOTIFICATION_CHANNEL_BUILTIN"));
    }
}
