package com.community.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 알림 한 줄(수신자별) — V40__admin_notifications.sql 의 {@code admin_notifications}. <b>읽기 전용 매핑</b>이다: 만드는 일은 {@code NotificationPublisher} 가 JDBC 로 하고
 * (세이브포인트 안에서 — 알림 실패가 업무 트랜잭션을 망가뜨리지 않게), 읽음 처리·정리는 조건부 UPDATE/DELETE 다. 문구는 키 + 인자({@code args} JSON 배열)로 저장하고 읽을 때 요청 언어로 푼다.
 */
@Entity
@Table(name = "admin_notifications")
public class AdminNotificationEntity {

    @Id
    private Long id;

    private Long operatorId;
    private String eventCode;

    /** {@link NotificationSeverity} 의 이름 */
    private String severity;

    private String titleKey;
    private String bodyKey;

    /** 인자(JSON 배열 글자). 원소는 글자이거나 {@code {"key":"문구 키"}} 다 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String args;

    private String linkPath;
    private String dedupeKey;
    private Instant createdAt;
    private Instant readAt;

    protected AdminNotificationEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getEventCode() {
        return eventCode;
    }

    public NotificationSeverity getSeverity() {
        return NotificationSeverity.valueOf(severity);
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getBodyKey() {
        return bodyKey;
    }

    public String getArgs() {
        return args;
    }

    public String getLinkPath() {
        return linkPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
