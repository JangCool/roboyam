package com.community.notificationtemplate.entity;

import com.community.notificationtemplate.catalog.MailTemplateAudience;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자 메일 템플릿 — V57 의 {@code notification_mail_templates}. 기본 메일 템플릿(회원 기본 · 운영자 기본)은 코드가 원천이라 여기에 없다({@code BuiltinTemplates}).
 * 틀은 저장 전에 서버가 정화한 간단 서식 HTML 이다.
 */
@Entity
@Table(name = "notification_mail_templates")
public class NotificationMailTemplateEntity {

    @Id
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private MailTemplateAudience audience;

    private boolean showLogo;
    private boolean showSiteName;
    /** 팔레트 색 이름 — null 이면 띠 없음 */
    private String headerColor;
    private String footerKo;
    private String footerEn;
    private String frameKo;
    private String frameEn;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected NotificationMailTemplateEntity() {
    }

    public static NotificationMailTemplateEntity create(long id, String name, MailTemplateAudience audience, boolean showLogo, boolean showSiteName, String headerColor,
                                                        String footerKo, String footerEn, String frameKo, String frameEn, long operatorId, Instant now) {
        NotificationMailTemplateEntity row = new NotificationMailTemplateEntity();
        row.id = id;
        row.createdBy = operatorId;
        row.createdAt = now;
        row.change(name, audience, showLogo, showSiteName, headerColor, footerKo, footerEn, frameKo, frameEn, operatorId, now);
        return row;
    }

    public void change(String name, MailTemplateAudience audience, boolean showLogo, boolean showSiteName, String headerColor, String footerKo, String footerEn,
                       String frameKo, String frameEn, long operatorId, Instant now) {
        this.name = name;
        this.audience = audience;
        this.showLogo = showLogo;
        this.showSiteName = showSiteName;
        this.headerColor = headerColor;
        this.footerKo = footerKo;
        this.footerEn = footerEn;
        this.frameKo = frameKo;
        this.frameEn = frameEn;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MailTemplateAudience getAudience() {
        return audience;
    }

    public boolean isShowLogo() {
        return showLogo;
    }

    public boolean isShowSiteName() {
        return showSiteName;
    }

    public String getHeaderColor() {
        return headerColor;
    }

    public String getFooterKo() {
        return footerKo;
    }

    public String getFooterEn() {
        return footerEn;
    }

    public String getFrameKo() {
        return frameKo;
    }

    public String getFrameEn() {
        return frameEn;
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
