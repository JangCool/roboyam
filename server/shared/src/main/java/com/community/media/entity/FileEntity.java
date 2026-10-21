package com.community.media.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영자가 올린 파일의 장부 — V12__site_media.sql 의 {@code files}. 파일 바이트는 저장소({@code ImageStorage})에 있고 여기에는 키·크기·상태만 둔다.
 * 올리면 {@code PENDING}, 어딘가에 쓰이게 되면({@link #markUsed}) {@code IN_USE}. 이름·원본 파일명은 저장하지 않는다.
 */
@Entity
@Table(name = "files")
public class FileEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private FilePurpose purpose;

    private String storageKey;

    private String contentType;

    private Long sizeBytes;

    private Integer width;

    private Integer height;

    @Enumerated(EnumType.STRING)
    private FileStatus status;

    private Long uploadedBy;

    private Instant createdAt;

    private Instant usedAt;

    protected FileEntity() {
    }

    /** 올리기만 한 파일 — 아직 아무 데도 쓰이지 않는다 */
    public static FileEntity pending(long id, FilePurpose purpose, String storageKey, String contentType, long sizeBytes, int width, int height,
                                     long uploadedBy, Instant now) {
        FileEntity file = new FileEntity();
        file.id = id;
        file.purpose = purpose;
        file.storageKey = storageKey;
        file.contentType = contentType;
        file.sizeBytes = sizeBytes;
        file.width = width;
        file.height = height;
        file.status = FileStatus.PENDING;
        file.uploadedBy = uploadedBy;
        file.createdAt = now;
        return file;
    }

    /** 쓰는 중으로 바꾼다(이미 쓰는 중이면 시각을 그대로 둔다) */
    public void markUsed(Instant now) {
        if (status != FileStatus.IN_USE) {
            status = FileStatus.IN_USE;
            usedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public FilePurpose getPurpose() {
        return purpose;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public FileStatus getStatus() {
        return status;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
