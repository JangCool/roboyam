package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 협찬 숨김 신고 확인 — {@code trial_sponsor_reviews}(V241, 글마다 한 줄). 신고가 들어오면 대기, 운영자가 확인(협찬으로 바꿈)·무시. 무시한 줄에 다시 신고가 오면 다시 대기 */
@Entity
@Table(name = "trial_sponsor_reviews")
public class TrialSponsorReviewEntity {

    @Id
    private Long postId;
    @Enumerated(EnumType.STRING)
    private SponsorReviewStatus status;
    private int reportCount;
    private Instant firstReportedAt;
    private Instant lastReportedAt;
    private Long decidedBy;
    private Instant decidedAt;
    private String note;

    protected TrialSponsorReviewEntity() {
    }

    public static TrialSponsorReviewEntity open(long postId, Instant now) {
        TrialSponsorReviewEntity e = new TrialSponsorReviewEntity();
        e.postId = postId;
        e.status = SponsorReviewStatus.PENDING;
        e.reportCount = 1;
        e.firstReportedAt = now;
        e.lastReportedAt = now;
        return e;
    }

    /** 신고 한 건 더 — 무시한 줄은 다시 대기로(확인한 줄은 이미 협찬 표시라 그대로) */
    public void reported(Instant now) {
        this.reportCount++;
        this.lastReportedAt = now;
        if (status == SponsorReviewStatus.DISMISSED) {
            this.status = SponsorReviewStatus.PENDING;
            this.decidedAt = null;
            this.decidedBy = null;
        }
    }

    public void decide(SponsorReviewStatus result, long operatorId, String note, Instant now) {
        this.status = result;
        this.decidedBy = operatorId;
        this.decidedAt = now;
        this.note = note;
    }

    public Long getPostId() {
        return postId;
    }

    public SponsorReviewStatus getStatus() {
        return status;
    }

    public int getReportCount() {
        return reportCount;
    }

    public Instant getFirstReportedAt() {
        return firstReportedAt;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getNote() {
        return note;
    }
}
