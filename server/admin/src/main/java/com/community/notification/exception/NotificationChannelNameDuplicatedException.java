package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 이름의 알림 도구가 이미 있다(409, 대소문자 무시). 이름 칸({@code name})에 문구가 붙는다 */
public class NotificationChannelNameDuplicatedException extends CommonException {

    public NotificationChannelNameDuplicatedException() {
        super(HttpStatus.CONFLICT, "NOTIFICATION_CHANNEL_NAME_DUPLICATED", new MessageRef("error.NOTIFICATION_CHANNEL_NAME_DUPLICATED"),
                Map.of("name", new MessageRef("error.NOTIFICATION_CHANNEL_NAME_DUPLICATED")), null);
    }
}
