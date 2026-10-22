package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 알림 이벤트의 전달 도구로 보낸 도구가 없다(400, 코드는 {@code NOTIFICATION_CHANNEL_NOT_FOUND}) — 요청 대상이 아니라 요청 안의 값이 틀린 것이라 404 가 아니다. {@code fields.channels} */
public class NotificationChannelReferenceNotFoundException extends CommonException {

    public NotificationChannelReferenceNotFoundException() {
        super(HttpStatus.BAD_REQUEST, "NOTIFICATION_CHANNEL_NOT_FOUND", new MessageRef("error.NOTIFICATION_CHANNEL_NOT_FOUND"),
                Map.of("channels", new MessageRef("error.NOTIFICATION_CHANNEL_NOT_FOUND")), null);
    }
}
