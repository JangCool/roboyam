package com.community.notificationtemplate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자 Slack · Mattermost 템플릿 변경 이력 — V57 의 {@code notification_chat_template_history}
 * (바뀐 칸 {@code name|showSeverityIcon|boldTitle|colorInfo|colorWarning|colorCritical|showSiteName|showSentAt|showOpenButton|openLabelKo|openLabelEn|frameKo|frameEn})
 */
@Entity
@Table(name = "notification_chat_template_history")
public class NotificationChatTemplateHistoryEntity extends TemplateHistoryBase {

    private Long templateId;

    protected NotificationChatTemplateHistoryEntity() {
    }

    public static NotificationChatTemplateHistoryEntity create(long id, long templateId, String action, long operatorId, Instant changedAt, String changesJson) {
        NotificationChatTemplateHistoryEntity row = new NotificationChatTemplateHistoryEntity();
        row.fill(id, action, operatorId, changedAt, changesJson);
        row.templateId = templateId;
        return row;
    }

    public Long getTemplateId() {
        return templateId;
    }
}
