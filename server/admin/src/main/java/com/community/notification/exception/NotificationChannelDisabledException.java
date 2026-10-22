package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 꺼진 알림 도구를 이벤트의 전달 도구로 새로 골랐다(409) — 이미 골라 둔 도구를 그대로 두는 것은 허용한다. {@code fields.channels} */
public class NotificationChannelDisabledException extends CommonException {

    public NotificationChannelDisabledException() {
        super(HttpStatus.CONFLICT, "NOTIFICATION_CHANNEL_DISABLED", new MessageRef("error.NOTIFICATION_CHANNEL_DISABLED"),
                Map.of("channels", new MessageRef("error.NOTIFICATION_CHANNEL_DISABLED")), null);
    }
}
