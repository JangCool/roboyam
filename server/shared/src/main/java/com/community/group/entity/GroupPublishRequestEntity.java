package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 비공개 소모임 글의 공개 요청 — V94 {@code group_post_publish_requests}. <b>글당 한 행</b>이다(글 표는 파티션이라 {@code post_id} 에 FK 가 없다).
 * 거절·취소된 뒤 다시 요청하면 같은 행을 {@code PENDING} 으로 되살린다(거절 횟수는 이어진다). 소모임 행을 잠근 트랜잭션 안에서만 바꾼다(community api-groups.md 11-5).
 * 글 ID 가 기본 키라 새 행은 {@code persist()}.
 */
@Entity
@Table(name = "group_post_publish_requests")
@DynamicUpdate
public class GroupPublishRequestEntity {

    /** 닫힌 이유 — {@code cancel_reason} */
    public static final String CANCEL_AUTHOR = "AUTHOR";
    public static final String CANCEL_AUTHOR_LEFT = "AUTHOR_LEFT";
    public static final String CANCEL_GROUP_PUBLIC = "GROUP_PUBLIC";
    public static final String CANCEL_UNPUBLISHED = "UNPUBLISHED";

    @Id
    private Long postId;

    private Long groupId;
    private Long requesterId;
    @Enumerated(EnumType.STRING)
    private GroupPublishRequestStatus status;
    private String note;
    private Instant requestedAt;
    private Long decidedBy;
    private Instant decidedAt;
    private String rejectReason;
    private short rejectCount;
    private String cancelReason;

    protected GroupPublishRequestEntity() {
    }

    public static GroupPublishRequestEntity create(long postId, long groupId, long requesterId, String note, Instant now) {
        GroupPublishRequestEntity r = new GroupPublishRequestEntity();
        r.postId = postId;
        r.groupId = groupId;
        r.requesterId = requesterId;
        r.status = GroupPublishRequestStatus.PENDING;
        r.note = note;
        r.requestedAt = now;
        return r;
    }

    /** 거절·취소된 요청을 다시 보낸다 — 거절 횟수는 이어진다(승인된 글을 되돌려 닫힌 경우는 {@link #cancel} 이 이미 지웠다) */
    public void reopen(String note, Instant now) {
        this.status = GroupPublishRequestStatus.PENDING;
        this.note = note;
        this.requestedAt = now;
        this.decidedBy = null;
        this.decidedAt = null;
        this.rejectReason = null;
        this.cancelReason = null;
    }

    public void approve(long byUserId, Instant now) {
        this.status = GroupPublishRequestStatus.APPROVED;
        this.decidedBy = byUserId;
        this.decidedAt = now;
        this.rejectReason = null;
        this.cancelReason = null;
    }

    public void reject(long byUserId, String reason, Instant now) {
        this.status = GroupPublishRequestStatus.REJECTED;
        this.decidedBy = byUserId;
        this.decidedAt = now;
        this.rejectReason = reason;
        this.rejectCount = (short) Math.min(3, rejectCount + 1);
    }

    /** 닫는다 — 이유는 {@code CANCEL_*}. 승인된 글을 되돌려 닫을 때는 거절 횟수를 지운다(새 요청이 바로 가능) */
    public void cancel(String reason) {
        this.status = GroupPublishRequestStatus.CANCELED;
        this.cancelReason = reason;
        if (CANCEL_UNPUBLISHED.equals(reason)) {
            this.rejectCount = 0;
        }
    }

    public boolean pending() {
        return status == GroupPublishRequestStatus.PENDING;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public GroupPublishRequestStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public int getRejectCount() {
        return rejectCount;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
