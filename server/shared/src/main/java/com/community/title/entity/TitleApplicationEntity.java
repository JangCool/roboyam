package com.community.title.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 칭호 신청 한 줄 — {@code title_applications}(V240). 회원·칭호마다 대기는 하나({@code ux_title_applications_pending}). 거절하면 사유와 다시 신청할 수 있는 날이 남는다.
 */
@Entity
@Table(name = "title_applications")
public class TitleApplicationEntity {

    @Id
    private Long id;
    private Long userId;
    private Long titleId;
    @Enumerated(EnumType.STRING)
    private TitleApplicationStatus status;
    private String reason;
    private Instant reapplyAt;
    private Long reviewedBy;
    private Long reviewedByMember;
    private Instant reviewedAt;
    private Instant createdAt;

    protected TitleApplicationEntity() {
    }

    public static TitleApplicationEntity apply(long id, long userId, long titleId, Instant now) {
        TitleApplicationEntity e = new TitleApplicationEntity();
        e.id = id;
        e.userId = userId;
        e.titleId = titleId;
        e.status = TitleApplicationStatus.PENDING;
        e.createdAt = now;
        return e;
    }

    /** 승인 — 운영자({@code operatorId}) 또는 게시판 매니저({@code memberId}) 중 하나 */
    public void approve(Long operatorId, Long memberId, String note, Instant now) {
        this.status = TitleApplicationStatus.APPROVED;
        this.reason = note;
        this.reviewedBy = operatorId;
        this.reviewedByMember = memberId;
        this.reviewedAt = now;
    }

    /** 거절 — 사유와 다시 신청할 수 있는 날 */
    public void reject(Long operatorId, Long memberId, String why, Instant reapply, Instant now) {
        this.status = TitleApplicationStatus.REJECTED;
        this.reason = why;
        this.reapplyAt = reapply;
        this.reviewedBy = operatorId;
        this.reviewedByMember = memberId;
        this.reviewedAt = now;
    }

    public boolean isPending() {
        return status == TitleApplicationStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTitleId() {
        return titleId;
    }

    public TitleApplicationStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getReapplyAt() {
        return reapplyAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Long getReviewedByMember() {
        return reviewedByMember;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
