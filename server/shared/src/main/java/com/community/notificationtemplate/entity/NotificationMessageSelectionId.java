package com.community.notificationtemplate.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link NotificationMessageSelectionEntity} 의 복합 키 — (대상 종류, 대상 코드, 채널) — 값은 상수 이름 글자 */
public class NotificationMessageSelectionId implements Serializable {

    private String targetType;
    private String targetCode;
    private String channel;

    protected NotificationMessageSelectionId() {
    }

    public NotificationMessageSelectionId(String targetType, String targetCode, String channel) {
        this.targetType = targetType;
        this.targetCode = targetCode;
        this.channel = channel;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NotificationMessageSelectionId id && Objects.equals(targetType, id.targetType) && Objects.equals(targetCode, id.targetCode)
                && Objects.equals(channel, id.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetType, targetCode, channel);
    }
}
