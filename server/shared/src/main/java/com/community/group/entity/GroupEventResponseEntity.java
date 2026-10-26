package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/** 일정 참석 응답 한 행 — V92 {@code group_event_responses}. (일정, 회원) 한 쌍에 하나. 바꾸면 응답과 시각을 갱신하고, 지우면 행을 지운다 */
@Entity
@Table(name = "group_event_responses")
@IdClass(GroupEventResponseId.class)
public class GroupEventResponseEntity {

    @Id
    private Long eventId;
    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    private GroupEventResponseType response;
    private Instant respondedAt;

    protected GroupEventResponseEntity() {
    }

    public static GroupEventResponseEntity of(long eventId, long userId, GroupEventResponseType response, Instant now) {
        GroupEventResponseEntity row = new GroupEventResponseEntity();
        row.eventId = eventId;
        row.userId = userId;
        row.response = response;
        row.respondedAt = now;
        return row;
    }

    /** 다른 응답으로 바꾼다 — 같은 응답이면 시각도 그대로(참석자 순서가 흔들리지 않게) */
    public void change(GroupEventResponseType response, Instant now) {
        if (this.response != response) {
            this.response = response;
            this.respondedAt = now;
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public GroupEventResponseType getResponse() {
        return response;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
