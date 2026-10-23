package com.community.notificationtemplate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사용자 문구 변경 이력 — V57 의 {@code notification_message_history}(바뀐 칸 {@code name|locale|templateId|subject|body}) */
@Entity
@Table(name = "notification_message_history")
public class NotificationMessageHistoryEntity extends TemplateHistoryBase {

    private Long messageId;

    protected NotificationMessageHistoryEntity() {
    }

    public static NotificationMessageHistoryEntity create(long id, long messageId, String action, long operatorId, Instant changedAt, String changesJson) {
        NotificationMessageHistoryEntity row = new NotificationMessageHistoryEntity();
        row.fill(id, action, operatorId, changedAt, changesJson);
        row.messageId = messageId;
        return row;
    }

    public Long getMessageId() {
        return messageId;
    }
}
