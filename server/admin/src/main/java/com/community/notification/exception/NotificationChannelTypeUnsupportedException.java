package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 카카오·SMS 는 업체 계약 전이라 새로 만들 수 없다(400). 종류 칸({@code type})에 문구가 붙는다 */
public class NotificationChannelTypeUnsupportedException extends CommonException {

    public NotificationChannelTypeUnsupportedException() {
        super(HttpStatus.BAD_REQUEST, "NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED", new MessageRef("error.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED"),
                Map.of("type", new MessageRef("notification.channel.type.comingSoon")), null);
    }
}
