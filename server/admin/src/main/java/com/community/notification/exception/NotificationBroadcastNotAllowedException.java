package com.community.notification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 본인에게만 가는 이벤트나 필수(보안) 이벤트에 추가 수신 팀 또는 공유 채널(Slack · Mattermost)을 지정했다(400). 다른 사람이나 여럿이 보는 채널에 나가면 안 되는 알림이다.
 * 걸린 칸({@code fields.channels} · {@code fields.teamIds})에 문구가 붙는다.
 */
public class NotificationBroadcastNotAllowedException extends CommonException {

    public NotificationBroadcastNotAllowedException(boolean channels, boolean teams) {
        super(HttpStatus.BAD_REQUEST, "NOTIFICATION_BROADCAST_NOT_ALLOWED", new MessageRef("error.NOTIFICATION_BROADCAST_NOT_ALLOWED"), fields(channels, teams), null);
    }

    private static Map<String, MessageRef> fields(boolean channels, boolean teams) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (channels) {
            fields.put("channels", new MessageRef("error.NOTIFICATION_BROADCAST_NOT_ALLOWED"));
        }
        if (teams) {
            fields.put("teamIds", new MessageRef("error.NOTIFICATION_BROADCAST_NOT_ALLOWED"));
        }
        return fields;
    }
}
