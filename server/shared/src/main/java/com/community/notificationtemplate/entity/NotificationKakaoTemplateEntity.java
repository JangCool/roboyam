package com.community.notificationtemplate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 카카오톡(알림톡) 템플릿 — V57 의 {@code notification_kakao_templates}. <b>준비 중</b>이라 읽기만 한다(행을 만드는 길이 아직 없다 — 등록·검수 흐름은 계약 「미확정」 12).
 * 변수 대응표·버튼은 JSON 배열 글자다({@code [{placeholder, variable, sample}]} · {@code [{name, linkVariable}]}).
 */
@Entity
@Table(name = "notification_kakao_templates")
public class NotificationKakaoTemplateEntity {

    @Id
    private Long id;

    private String name;
    private String templateCode;
    private String status;
    private String emphasis;
    private String approvedBody;

    @JdbcTypeCode(SqlTypes.JSON)
    private String mappings;

    @JdbcTypeCode(SqlTypes.JSON)
    private String buttons;

    private String channelName;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected NotificationKakaoTemplateEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getStatus() {
        return status;
    }

    public String getEmphasis() {
        return emphasis;
    }

    public String getApprovedBody() {
        return approvedBody;
    }

    public String getMappings() {
        return mappings;
    }

    public String getButtons() {
        return buttons;
    }

    public String getChannelName() {
        return channelName;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
