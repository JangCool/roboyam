package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 체험단 사진 — {@code trial_photos}(V241). 먼저 올리고(붙지 않음) 처리(입고·발송·반납·파손 신고)가 붙인다. 미디어 저장소 이미지 키만 둔다 */
@Entity
@Table(name = "trial_photos")
public class TrialPhotoEntity {

    @Id
    private Long id;
    private Long trialId;
    @Enumerated(EnumType.STRING)
    private TrialPhotoKind kind;
    private Long participantId;
    private Long damageReportId;
    private String imageKey;
    @Enumerated(EnumType.STRING)
    private TrialActorType uploadedByType;
    private Long uploadedBy;
    private Instant attachedAt;
    private Instant createdAt;

    protected TrialPhotoEntity() {
    }

    public static TrialPhotoEntity upload(long id, long trialId, TrialPhotoKind kind, String imageKey, TrialActorType by, long actorId, Instant now) {
        TrialPhotoEntity e = new TrialPhotoEntity();
        e.id = id;
        e.trialId = trialId;
        e.kind = kind;
        e.imageKey = imageKey;
        e.uploadedByType = by;
        e.uploadedBy = actorId;
        e.createdAt = now;
        return e;
    }

    public void attach(Long participantId, Long damageReportId, Instant now) {
        this.participantId = participantId;
        this.damageReportId = damageReportId;
        this.attachedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTrialId() {
        return trialId;
    }

    public TrialPhotoKind getKind() {
        return kind;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public Long getDamageReportId() {
        return damageReportId;
    }

    public String getImageKey() {
        return imageKey;
    }

    public TrialActorType getUploadedByType() {
        return uploadedByType;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
