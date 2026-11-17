package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 체험단 신청 한 줄 — {@code trial_applications}(V241). 고른 배송지는 암호문 사본(용도 {@code trial.address.*} — {@code TrialPii})으로 두고, 반납 확인(제공형은 받음) 뒤 파기한다.
 * {@code toString} 에 암호문을 싣지 않는다.
 */
@Entity
@Table(name = "trial_applications")
public class TrialApplicationEntity {

    @Id
    private Long id;
    private Long trialId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private TrialApplicationStatus status;
    @Enumerated(EnumType.STRING)
    private TrialQuota quota;
    private String reviewerTier;
    private String recipientEnc;
    private String phoneEnc;
    private String postcode;
    private String address1Enc;
    private String address2Enc;
    private String memoEnc;
    private Instant addressPurgedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    private String consents;
    private Instant recommendedAt;
    private Instant createdAt;
    private Instant decidedAt;
    private Instant cancelledAt;

    protected TrialApplicationEntity() {
    }

    /** 배송지 암호문 사본 */
    public record AddressCopy(String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc) {

        @Override
        public String toString() {
            return "AddressCopy[가림]";
        }
    }

    public static TrialApplicationEntity apply(long id, long trialId, long userId, TrialQuota quota, String reviewerTier, AddressCopy address, String consentsJson,
                                               Instant now) {
        TrialApplicationEntity e = new TrialApplicationEntity();
        e.id = id;
        e.trialId = trialId;
        e.userId = userId;
        e.status = TrialApplicationStatus.APPLIED;
        e.quota = quota;
        e.reviewerTier = reviewerTier;
        e.recipientEnc = address.recipientEnc();
        e.phoneEnc = address.phoneEnc();
        e.postcode = address.postcode();
        e.address1Enc = address.address1Enc();
        e.address2Enc = address.address2Enc();
        e.memoEnc = address.memoEnc();
        e.consents = consentsJson;
        e.createdAt = now;
        return e;
    }

    public void cancel(Instant now) {
        this.status = TrialApplicationStatus.CANCELLED;
        this.cancelledAt = now;
        purgeAddress(now); // 취소한 신청의 주소는 바로 지운다(쓸 일이 없다)
    }

    public void select(Instant now) {
        this.status = TrialApplicationStatus.SELECTED;
        this.decidedAt = now;
    }

    public void notSelect(Instant now) {
        this.status = TrialApplicationStatus.NOT_SELECTED;
        this.decidedAt = now;
        purgeAddress(now); // 안 뽑힌 신청의 주소는 바로 지운다
    }

    /** 광고주(또는 운영자) 후보 추천 켜고 끄기 */
    public void recommend(boolean on, Instant now) {
        this.recommendedAt = on ? now : null;
    }

    /** 주소 사본 파기 — 이미 지웠으면 그대로 */
    public void purgeAddress(Instant now) {
        if (addressPurgedAt != null) {
            return;
        }
        this.recipientEnc = null;
        this.phoneEnc = null;
        this.postcode = null;
        this.address1Enc = null;
        this.address2Enc = null;
        this.memoEnc = null;
        this.addressPurgedAt = now;
    }

    public boolean live() {
        return status != TrialApplicationStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getTrialId() {
        return trialId;
    }

    public Long getUserId() {
        return userId;
    }

    public TrialApplicationStatus getStatus() {
        return status;
    }

    public TrialQuota getQuota() {
        return quota;
    }

    public String getReviewerTier() {
        return reviewerTier;
    }

    public String getRecipientEnc() {
        return recipientEnc;
    }

    public String getPhoneEnc() {
        return phoneEnc;
    }

    public String getPostcode() {
        return postcode;
    }

    public String getAddress1Enc() {
        return address1Enc;
    }

    public String getAddress2Enc() {
        return address2Enc;
    }

    public String getMemoEnc() {
        return memoEnc;
    }

    public Instant getAddressPurgedAt() {
        return addressPurgedAt;
    }

    public String getConsents() {
        return consents;
    }

    public Instant getRecommendedAt() {
        return recommendedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    @Override
    public String toString() {
        return "TrialApplicationEntity[id=" + id + ", trialId=" + trialId + ", status=" + status + "]";
    }
}
