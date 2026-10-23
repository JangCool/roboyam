package com.community.notificationtemplate.entity;

import com.community.notificationtemplate.catalog.MessageChannel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자 Slack · Mattermost 템플릿 — V57 의 {@code notification_chat_templates}. 기본 템플릿(종류마다 하나)은 코드가 원천이라 여기에 없다({@code BuiltinTemplates}).
 * 종류({@link #getTool()})는 만든 뒤 바꾸지 않는다.
 */
@Entity
@Table(name = "notification_chat_templates")
public class NotificationChatTemplateEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private MessageChannel tool;

    private String name;
    private boolean showSeverityIcon;
    private boolean boldTitle;
    private String colorInfo;
    private String colorWarning;
    private String colorCritical;
    private boolean showSiteName;
    private boolean showSentAt;
    private boolean showOpenButton;
    private String openLabelKo;
    private String openLabelEn;
    private String frameKo;
    private String frameEn;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected NotificationChatTemplateEntity() {
    }

    /** 칸 값 묶음(만들기·고치기가 같이 쓴다) */
    public record Fields(String name, boolean showSeverityIcon, boolean boldTitle, String colorInfo, String colorWarning, String colorCritical, boolean showSiteName,
                         boolean showSentAt, boolean showOpenButton, String openLabelKo, String openLabelEn, String frameKo, String frameEn) {
    }

    public static NotificationChatTemplateEntity create(long id, MessageChannel tool, Fields fields, long operatorId, Instant now) {
        NotificationChatTemplateEntity row = new NotificationChatTemplateEntity();
        row.id = id;
        row.tool = tool;
        row.createdBy = operatorId;
        row.createdAt = now;
        row.change(fields, operatorId, now);
        return row;
    }

    public void change(Fields fields, long operatorId, Instant now) {
        this.name = fields.name();
        this.showSeverityIcon = fields.showSeverityIcon();
        this.boldTitle = fields.boldTitle();
        this.colorInfo = fields.colorInfo();
        this.colorWarning = fields.colorWarning();
        this.colorCritical = fields.colorCritical();
        this.showSiteName = fields.showSiteName();
        this.showSentAt = fields.showSentAt();
        this.showOpenButton = fields.showOpenButton();
        this.openLabelKo = fields.openLabelKo();
        this.openLabelEn = fields.openLabelEn();
        this.frameKo = fields.frameKo();
        this.frameEn = fields.frameEn();
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Fields fields() {
        return new Fields(name, showSeverityIcon, boldTitle, colorInfo, colorWarning, colorCritical, showSiteName, showSentAt, showOpenButton, openLabelKo, openLabelEn,
                frameKo, frameEn);
    }

    public Long getId() {
        return id;
    }

    public MessageChannel getTool() {
        return tool;
    }

    public String getName() {
        return name;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
