package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 대여형 파손 신고 — {@code trial_damage_reports}(V241). 신고 → 회원 7일 안 이의 → 운영자 판정(이의가 없으면 기한 뒤 운영자 확인할 일로) */
@Entity
@Table(name = "trial_damage_reports")
public class TrialDamageReportEntity {

    @Id
    private Long id;
    private Long trialId;
    private Long participantId;
    @Enumerated(EnumType.STRING)
    private TrialActorType reporterType;
    private Long reporterId;
    private String description;
    @Enumerated(EnumType.STRING)
    private TrialDamageStatus status;
    private Instant objectionDueAt;
    private String objection;
    private Instant objectedAt;
    @Enumerated(EnumType.STRING)
    private TrialDamageVerdict verdict;
    private String decisionNote;
    private Long decidedBy;
    private Instant decidedAt;
    private Instant contactProvidedAt;
    private Instant createdAt;

    protected TrialDamageReportEntity() {
    }

    public static TrialDamageReportEntity report(long id, long trialId, long participantId, TrialActorType by, long reporterId, String description, Instant objectionDueAt,
                                                 Instant now) {
        TrialDamageReportEntity e = new TrialDamageReportEntity();
        e.id = id;
        e.trialId = trialId;
        e.participantId = participantId;
        e.reporterType = by;
        e.reporterId = reporterId;
        e.description = description;
        e.status = TrialDamageStatus.REPORTED;
        e.objectionDueAt = objectionDueAt;
        e.createdAt = now;
        return e;
    }

    public void object(String text, Instant now) {
        this.objection = text;
        this.objectedAt = now;
        this.status = TrialDamageStatus.OBJECTED;
    }

    /** 이의 기한이 지나 운영자 확인할 일로 */
    public void await() {
        this.status = TrialDamageStatus.AWAITING;
    }

    public void decide(TrialDamageVerdict verdict, String note, long operatorId, Instant now) {
        this.verdict = verdict;
        this.decisionNote = note;
        this.decidedBy = operatorId;
        this.decidedAt = now;
        this.status = TrialDamageStatus.DECIDED;
    }

    public void markContactProvided(Instant now) {
        this.contactProvidedAt = now;
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

    public TrialActorType getReporterType() {
        return reporterType;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public String getDescription() {
        return description;
    }

    public TrialDamageStatus getStatus() {
        return status;
    }

    public Instant getObjectionDueAt() {
        return objectionDueAt;
    }

    public String getObjection() {
        return objection;
    }

    public Instant getObjectedAt() {
        return objectedAt;
    }

    public TrialDamageVerdict getVerdict() {
        return verdict;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getContactProvidedAt() {
        return contactProvidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
