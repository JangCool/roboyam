package com.community.notification.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link OperatorNotificationMuteEntity} 의 복합 키 — (운영자, 이벤트 코드) */
public class OperatorNotificationMuteId implements Serializable {

    private Long operatorId;
    private String eventCode;

    protected OperatorNotificationMuteId() {
    }

    public OperatorNotificationMuteId(Long operatorId, String eventCode) {
        this.operatorId = operatorId;
        this.eventCode = eventCode;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OperatorNotificationMuteId id && Objects.equals(operatorId, id.operatorId) && Objects.equals(eventCode, id.eventCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operatorId, eventCode);
    }
}
