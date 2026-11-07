package com.community.notification.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link NotificationEventChannelEntity} 의 복합 키(이벤트 + 도구) */
public class NotificationEventChannelId implements Serializable {

    private String eventCode;
    private Long channelId;

    protected NotificationEventChannelId() {
    }

    public NotificationEventChannelId(String eventCode, Long channelId) {
        this.eventCode = eventCode;
        this.channelId = channelId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NotificationEventChannelId that && Objects.equals(eventCode, that.eventCode) && Objects.equals(channelId, that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventCode, channelId);
    }
}
