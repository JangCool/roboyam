package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * 체험단 뽑힌 한 줄 — {@code trial_participants}(V241). 송장 → 받음(회원·자동) → 사용기(연결 글) → 대여형 반납 → 약속 지킴. 사용기·반납 기한은 받은 때부터 센다.
 */
@Entity
@Table(name = "trial_participants")
public class TrialParticipantEntity {

    public static final String RECEIVED_BY_MEMBER = "MEMBER";
    public static final String RECEIVED_BY_AUTO = "AUTO";

    @Id
    private Long id;
    private Long trialId;
    private Long applicationId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private TrialQuota quota;
    private String carrier;
    private String trackingNo;
    private Instant shippedAt;
    @Enumerated(EnumType.STRING)
    private TrialActorType shippedByType;
    private Long shippedBy;
    private Instant receivedAt;
    private String receivedBy;
    private Long reviewPostId;
    private Instant reviewPostedAt;
    private Instant reviewDueAt;
    private Instant returnDueAt;
    private Instant returnedAt;
    @Enumerated(EnumType.STRING)
    private TrialActorType returnedByType;
    private Long returnedBy;
    private Boolean kept;
    private Instant keptAt;
    private Instant reviewLateAt;
    private Instant returnRemindFirstAt;
    private Instant returnRemindSecondAt;
    private Instant unreturnedAt;
    private Instant contactProvidedAt;
    /** 운영자 취소(V242 — 발송 전에만, 점검 결정 D9) */
    private Instant cancelledAt;
    private Long cancelledBy;
    private String cancelReason;
    private Instant createdAt;

    protected TrialParticipantEntity() {
    }

    public static TrialParticipantEntity create(long id, long trialId, long applicationId, long userId, TrialQuota quota, Instant now) {
        TrialParticipantEntity e = new TrialParticipantEntity();
        e.id = id;
        e.trialId = trialId;
        e.applicationId = applicationId;
        e.userId = userId;
        e.quota = quota;
        e.createdAt = now;
        return e;
    }

    /** 송장 넣기(고치기 포함 — 받기 전까지) */
    public void ship(String carrier, String trackingNo, TrialActorType by, long actorId, Instant now) {
        this.carrier = carrier;
        this.trackingNo = trackingNo;
        if (this.shippedAt == null) {
            this.shippedAt = now;
        }
        this.shippedByType = by;
        this.shippedBy = actorId;
    }

    /** 받음 — 사용기 기한(받은 때 + 사용기 일수)·반납 기한(대여형만) */
    public void receive(String by, Instant at, int reviewDays, Integer returnDays) {
        this.receivedAt = at;
        this.receivedBy = by;
        this.reviewDueAt = at.plus(Duration.ofDays(reviewDays));
        this.returnDueAt = returnDays == null ? null : at.plus(Duration.ofDays(returnDays));
    }

    public void linkReview(long postId, Instant now) {
        this.reviewPostId = postId;
        this.reviewPostedAt = now;
    }

    /**
     * 사용기 글을 글쓴이가 지웠다(점검 L4) — 연결을 끊고, 지킴으로 판정했던 줄은 판정을 되돌린다(다시 기한 안에 쓰면 지킴, 기한이 지나면 하루 배치가 기한 넘김으로 판정).
     * 기한 넘김·미반납으로 이미 「안 지킴」인 줄은 그대로. 되돌렸으면 true.
     */
    public boolean unlinkReview() {
        this.reviewPostId = null;
        this.reviewPostedAt = null;
        if (Boolean.TRUE.equals(kept)) {
            this.kept = null;
            this.keptAt = null;
            return true;
        }
        return false;
    }

    public void confirmReturn(TrialActorType by, long actorId, Instant now) {
        this.returnedAt = now;
        this.returnedByType = by;
        this.returnedBy = actorId;
    }

    /** 약속 지킴 판정(한 번만 — 부르는 쪽이 비어 있을 때만 부른다) */
    public void judge(boolean keptPromise, Instant now) {
        this.kept = keptPromise;
        this.keptAt = now;
    }

    public void markReviewLate(Instant now) {
        this.reviewLateAt = now;
    }

    public void markRemindFirst(Instant now) {
        this.returnRemindFirstAt = now;
    }

    public void markRemindSecond(Instant now) {
        this.returnRemindSecondAt = now;
    }

    public void markUnreturned(Instant now) {
        this.unreturnedAt = now;
    }

    public void markContactProvided(Instant now) {
        if (contactProvidedAt == null) {
            this.contactProvidedAt = now;
        }
    }

    /** 운영자 취소(발송 전 — 부르는 쪽이 본다) · 모집 취소({@code by} 없음 가능). 사유는 1~200자 */
    public void cancel(Long by, String reason, Instant now) {
        this.cancelledAt = now;
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    public boolean cancelled() {
        return cancelledAt != null;
    }

    /** 이 줄로 무엇이든 시작됐나(송장·받음·사용기 연결) — 운영자 취소·결제 뒤 자동 전액 환불을 막는다 */
    public boolean started() {
        return shippedAt != null || receivedAt != null || reviewPostId != null;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public boolean shipped() {
        return shippedAt != null;
    }

    public boolean received() {
        return receivedAt != null;
    }

    public boolean returned() {
        return returnedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getTrialId() {
        return trialId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getUserId() {
        return userId;
    }

    public TrialQuota getQuota() {
        return quota;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public TrialActorType getShippedByType() {
        return shippedByType;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public Long getReviewPostId() {
        return reviewPostId;
    }

    public Instant getReviewPostedAt() {
        return reviewPostedAt;
    }

    public Instant getReviewDueAt() {
        return reviewDueAt;
    }

    public Instant getReturnDueAt() {
        return returnDueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public TrialActorType getReturnedByType() {
        return returnedByType;
    }

    public Boolean getKept() {
        return kept;
    }

    public Instant getKeptAt() {
        return keptAt;
    }

    public Instant getReviewLateAt() {
        return reviewLateAt;
    }

    public Instant getReturnRemindFirstAt() {
        return returnRemindFirstAt;
    }

    public Instant getReturnRemindSecondAt() {
        return returnRemindSecondAt;
    }

    public Instant getUnreturnedAt() {
        return unreturnedAt;
    }

    public Instant getContactProvidedAt() {
        return contactProvidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
