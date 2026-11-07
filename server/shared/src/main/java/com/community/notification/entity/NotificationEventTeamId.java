package com.community.notification.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link NotificationEventTeamEntity} 의 복합 키(이벤트 + 추가 수신 팀) */
public class NotificationEventTeamId implements Serializable {

    private String eventCode;
    private Long teamId;

    protected NotificationEventTeamId() {
    }

    public NotificationEventTeamId(String eventCode, Long teamId) {
        this.eventCode = eventCode;
        this.teamId = teamId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NotificationEventTeamId that && Objects.equals(eventCode, that.eventCode) && Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventCode, teamId);
    }
}
