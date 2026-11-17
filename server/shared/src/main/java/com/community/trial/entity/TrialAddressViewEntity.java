package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 운영자 주소 열람 기록 — {@code trial_address_views}(V243, 점검 결정 D4). 「주소 보기」 한 번에 한 줄(사유 5~200자) */
@Entity
@Table(name = "trial_address_views")
public class TrialAddressViewEntity {

    @Id
    private Long id;
    private Long trialId;
    private Long participantId;
    private Long operatorId;
    private String reason;
    private Instant createdAt;

    protected TrialAddressViewEntity() {
    }

    public static TrialAddressViewEntity of(long id, long trialId, long participantId, long operatorId, String reason, Instant now) {
        TrialAddressViewEntity e = new TrialAddressViewEntity();
        e.id = id;
        e.trialId = trialId;
        e.participantId = participantId;
        e.operatorId = operatorId;
        e.reason = reason;
        e.createdAt = now;
        return e;
    }

    public Long getId() {
        return id;
    }

    public Long getTrialId() {
        return trialId;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
