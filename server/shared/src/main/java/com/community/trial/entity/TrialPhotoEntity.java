package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 체험단 사진 — {@code trial_photos}(V241 · 비공개 V243). 먼저 올리고(붙지 않음) 처리(입고·발송·반납·파손 신고)가 붙인다. 사진은 다시 인코딩한 JPEG 의 {@code DataCipher.encryptBytes}
 * 암호문({@code content} — 용도 {@code trial.photo})이고 권한 API 로만 본다. 보관 기한이 지나면 {@code content} 를 지우고 {@code purged_at}(줄은 남긴다). {@code toString} 에 바이트를 싣지 않는다.
 */
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
    private byte[] content;
    private String contentType;
    @Enumerated(EnumType.STRING)
    private TrialActorType uploadedByType;
    private Long uploadedBy;
    private Instant attachedAt;
    private Instant purgedAt;
    private Instant createdAt;

    protected TrialPhotoEntity() {
    }

    public static TrialPhotoEntity upload(long id, long trialId, TrialPhotoKind kind, byte[] encrypted, String contentType, TrialActorType by, long actorId, Instant now) {
        TrialPhotoEntity e = new TrialPhotoEntity();
        e.id = id;
        e.trialId = trialId;
        e.kind = kind;
        e.content = encrypted;
        e.contentType = contentType;
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

    /** 보관 기한 — 바이트를 지운다(줄은 처리 기록으로 남는다) */
    public void purge(Instant now) {
        this.content = null;
        this.contentType = null;
        this.purgedAt = now;
    }

    public boolean purged() {
        return content == null;
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

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
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

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "TrialPhotoEntity[id=" + id + ", trialId=" + trialId + ", kind=" + kind + "]";
    }
}
