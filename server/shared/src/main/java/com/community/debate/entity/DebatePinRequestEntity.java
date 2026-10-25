package com.community.debate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 의견 고정 요청(V78 {@code debate_pin_requests}) — 의견(입장 있는 원댓글)을 쓴 회원이 자기 의견을 토론 의견 맨 위에 고정해 달라고 한다. 확인하는 사람은 게시판 정책
 * {@code pinRequestReviewers}(글쓴이 · 게시판 매니저 · 운영자). 의견 하나에 대기 요청은 하나(부분 유일 색인). 요청한 회원이 취소하면 행을 지운다.
 */
@Entity
@Table(name = "debate_pin_requests")
public class DebatePinRequestEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String BY_MEMBER = "MEMBER";
    public static final String BY_OPERATOR = "OPERATOR";
    /** 거절 사유 최대 글자 수(V78 varchar(100)) */
    public static final int REASON_MAX = 100;

    @Id
    private Long id;

    private Long commentId;
    private Long postId;
    private Long requesterId;
    private String status;
    private String rejectReason;
    private Instant createdAt;
    private Instant decidedAt;
    private String decidedByKind;
    private Long decidedBy;

    protected DebatePinRequestEntity() {
    }

    public static DebatePinRequestEntity pending(long id, long commentId, long postId, long requesterId, Instant now) {
        DebatePinRequestEntity row = new DebatePinRequestEntity();
        row.id = id;
        row.commentId = commentId;
        row.postId = postId;
        row.requesterId = requesterId;
        row.status = PENDING;
        row.createdAt = now;
        return row;
    }

    /** 승인·거절 — 대기 중일 때만 부른다 */
    public void decide(boolean approve, String reason, String byKind, long by, Instant now) {
        this.status = approve ? APPROVED : REJECTED;
        this.rejectReason = approve ? null : reason;
        this.decidedByKind = byKind;
        this.decidedBy = by;
        this.decidedAt = now;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public Long getId() {
        return id;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public String getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecidedByKind() {
        return decidedByKind;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }
}
