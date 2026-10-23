package com.community.notificationtemplate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사용자 메일 템플릿 변경 이력 — V57 의 {@code notification_mail_template_history}(바뀐 칸 {@code name|audience|showLogo|showSiteName|headerColor|footerKo|footerEn|frameKo|frameEn}) */
@Entity
@Table(name = "notification_mail_template_history")
public class NotificationMailTemplateHistoryEntity extends TemplateHistoryBase {

    private Long templateId;

    protected NotificationMailTemplateHistoryEntity() {
    }

    public static NotificationMailTemplateHistoryEntity create(long id, long templateId, String action, long operatorId, Instant changedAt, String changesJson) {
        NotificationMailTemplateHistoryEntity row = new NotificationMailTemplateHistoryEntity();
        row.fill(id, action, operatorId, changedAt, changesJson);
        row.templateId = templateId;
        return row;
    }

    public Long getTemplateId() {
        return templateId;
    }
}
