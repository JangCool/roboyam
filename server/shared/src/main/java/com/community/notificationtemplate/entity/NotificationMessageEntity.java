package com.community.notificationtemplate.entity;

import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageTargetType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자 문구 — V57 의 {@code notification_messages}. 대상 · 채널은 만든 뒤 바꾸지 않는다. 기본 문구는 코드가 원천이라 여기에 없다({@code BuiltinMessages}).
 * 이메일 본문은 저장 전에 서버가 정화한 간단 서식 HTML 이다.
 */
@Entity
@Table(name = "notification_messages")
public class NotificationMessageEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private MessageTargetType targetType;

    private String targetCode;

    @Enumerated(EnumType.STRING)
    private MessageChannel channel;

    private String locale;
    private String name;
    /** 채널의 템플릿 id(기본 것이면 그 글자 id, 사용자 것이면 Snowflake 글자) — 앱 알림은 null */
    private String templateId;
    private String subject;
    private String body;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected NotificationMessageEntity() {
    }

    public static NotificationMessageEntity create(long id, MessageTargetType targetType, String targetCode, MessageChannel channel, String locale, String name,
                                                   String templateId, String subject, String body, long operatorId, Instant now) {
        NotificationMessageEntity row = new NotificationMessageEntity();
        row.id = id;
        row.targetType = targetType;
        row.targetCode = targetCode;
        row.channel = channel;
        row.createdBy = operatorId;
        row.createdAt = now;
        row.change(locale, name, templateId, subject, body, operatorId, now);
        return row;
    }

    public void change(String locale, String name, String templateId, String subject, String body, long operatorId, Instant now) {
        this.locale = locale;
        this.name = name;
        this.templateId = templateId;
        this.subject = subject;
        this.body = body;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public MessageTargetType getTargetType() {
        return targetType;
    }

    public String getTargetCode() {
        return targetCode;
    }

    public MessageChannel getChannel() {
        return channel;
    }

    public String getLocale() {
        return locale;
    }

    public String getName() {
        return name;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
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
