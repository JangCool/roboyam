package com.community.group.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link GroupEventResponseEntity} 의 복합 키(일정 + 회원) */
public class GroupEventResponseId implements Serializable {

    private Long eventId;
    private Long userId;

    protected GroupEventResponseId() {
    }

    public GroupEventResponseId(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupEventResponseId that && Objects.equals(eventId, that.eventId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, userId);
    }
}
