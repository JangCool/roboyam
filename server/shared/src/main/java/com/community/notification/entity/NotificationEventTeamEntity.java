package com.community.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 이벤트가 고른 추가 수신 팀(teams) 한 짝 — V41 {@code notification_event_teams}(이벤트·대상이 지워지면 DB 가 함께 지운다). 관리 화면이 통째로 바꾼다
 * ({@code NotificationDeliveryRepository}). {@code created_at} 은 DB 기본값({@code now()})이 채운다.
 */
@Entity
@Table(name = "notification_event_teams")
@IdClass(NotificationEventTeamId.class)
public class NotificationEventTeamEntity {

    @Id
    private String eventCode;

    @Id
    private Long teamId;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    protected NotificationEventTeamEntity() {
    }

    public static NotificationEventTeamEntity of(String eventCode, long teamId) {
        NotificationEventTeamEntity row = new NotificationEventTeamEntity();
        row.eventCode = eventCode;
        row.teamId = teamId;
        return row;
    }
}
