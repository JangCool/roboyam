package com.community.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 글 조치 기록 한 줄 — V80 {@code post_moderation_events}. 쌓기만 하고 고치거나 지우지 않는다(관리자 글 상세의 「조치 기록」).
 * 운영자 조치는 {@code OPERATOR}(운영자 ID), 신고 자동 숨김은 {@code SYSTEM}. 사유는 운영자가 적은 문구 그대로, 덧붙임은 JSON 글자.
 */
@Entity
@Table(name = "post_moderation_events")
public class PostModerationEventEntity {

    public static final String HIDDEN = "HIDDEN";
    public static final String UNHIDDEN = "UNHIDDEN";
    public static final String DELETED = "DELETED";
    public static final String RESTORED = "RESTORED";
    public static final String MOVED = "MOVED";
    public static final String NOTICE = "NOTICE";
    public static final String PINNED = "PINNED";
    public static final String UNPINNED = "UNPINNED";
    /** 신고 자동 숨김(공개 서버 ReportService) */
    public static final String REPORT_HIDDEN = "REPORT_HIDDEN";
    /** 운영자가 글의 투표를 지움(V81 — docs/features/post-poll-contract.md) */
    public static final String POLL_DELETED = "POLL_DELETED";

    public static final String BY_OPERATOR = "OPERATOR";
    public static final String BY_SYSTEM = "SYSTEM";

    /** 사유 최대 글자(V80) */
    public static final int REASON_MAX = 200;

    @Id
    private Long id;

    private Long postId;
    private String type;
    private String actorKind;
    private Long actorId;
    private String reason;
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;
    private Instant createdAt;

    protected PostModerationEventEntity() {
    }

    /**
     * @param actorId    운영자 ID — {@link #BY_SYSTEM} 이면 null
     * @param detailJson 덧붙임 JSON(없으면 null)
     */
    public static PostModerationEventEntity create(long id, long postId, String type, String actorKind, Long actorId, String reason, String detailJson,
                                                   Instant now) {
        PostModerationEventEntity row = new PostModerationEventEntity();
        row.id = id;
        row.postId = postId;
        row.type = type;
        row.actorKind = actorKind;
        row.actorId = BY_SYSTEM.equals(actorKind) ? null : actorId;
        row.reason = reason;
        row.detail = detailJson;
        row.createdAt = now;
        return row;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public String getType() {
        return type;
    }

    public String getActorKind() {
        return actorKind;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
