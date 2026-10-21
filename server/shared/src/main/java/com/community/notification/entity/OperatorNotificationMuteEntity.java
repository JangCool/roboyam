package com.community.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/** 운영자가 끈 이벤트 한 건 — V40__admin_notifications.sql 의 {@code operator_notification_mutes}. 행이 있으면 끈 것이다. 필수 이벤트는 끌 수 없어 만들어지지 않는다 */
@Entity
@Table(name = "operator_notification_mutes")
@IdClass(OperatorNotificationMuteId.class)
public class OperatorNotificationMuteEntity {

    @Id
    private Long operatorId;

    @Id
    private String eventCode;

    private Instant createdAt;

    protected OperatorNotificationMuteEntity() {
    }

    public static OperatorNotificationMuteEntity create(long operatorId, String eventCode, Instant now) {
        OperatorNotificationMuteEntity mute = new OperatorNotificationMuteEntity();
        mute.operatorId = operatorId;
        mute.eventCode = eventCode;
        mute.createdAt = now;
        return mute;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getEventCode() {
        return eventCode;
    }
}
