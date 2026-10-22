package com.community.membernotification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원 알림 한 줄 — V50__member_notifications.sql 의 {@code member_notifications}. <b>읽기 전용 매핑</b>이다: 만드는 일은 {@code MemberNotificationPublisher} 가 JDBC 로 하고
 * (세이브포인트 안에서 — 알림 실패가 글·댓글쓰기를 깨지 않게), 읽음 처리·정리는 조건부 UPDATE/DELETE 다. 문구는 저장하지 않고 읽을 때 종류 + 칸으로 푼다.
 */
@Entity
@Table(name = "member_notifications")
public class MemberNotificationEntity {

    @Id
    private Long id;

    private Long userId;
    private String type;
    private Long actorUserId;
    private String actorName;
    private Long postId;
    private Long commentId;
    private int groupCount;

    /** 종류별 덧붙임(JSON 객체 글자) */
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant readAt;
    private Instant createdAt;

    protected MemberNotificationEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorName() {
        return actorName;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
