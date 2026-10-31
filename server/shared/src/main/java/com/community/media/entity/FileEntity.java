package com.community.media.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 올린 파일의 장부 — V12__site_media.sql·V56 의 {@code files}. 파일 바이트는 저장소({@code ImageStorage})에 있고 여기에는 키·크기·상태만 둔다.
 * 올리면 {@code PENDING}, 어딘가에 쓰이게 되면({@link #markUsed}·{@link #attachToPost}) {@code IN_USE}. 이름·원본 파일명은 저장하지 않는다.
 * 운영자가 올린 파일(사이트 로고)은 {@code uploadedBy}, 회원이 올린 글 이미지는 {@code uploadedByUser} 와 쓰는 글 {@code postId} 를 갖는다.
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

    /** 올린 회원(글 이미지) — 운영자가 올린 파일은 null */
    private Long uploadedByUser;

    /** 이 이미지를 쓰는 글(글 이미지가 IN_USE 일 때) — 파티션 표라 외래 키가 없다 */
    private Long postId;

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

    /** 회원이 올린 사업자 광고 이미지(V112) — 신청 건에 쓰이면 IN_USE */
    public static FileEntity pendingBizAdImage(long id, String storageKey, String contentType, long sizeBytes, int width, int height, long userId, Instant now) {
        FileEntity file = pendingPostImage(id, storageKey, contentType, sizeBytes, width, height, userId, now);
        file.purpose = FilePurpose.BIZ_AD_IMAGE;
        return file;
    }

    /** 회원이 올린 글 이미지 — 아직 어느 글에도 쓰이지 않는다 */
    public static FileEntity pendingPostImage(long id, String storageKey, String contentType, long sizeBytes, int width, int height, long userId,
                                              Instant now) {
        FileEntity file = new FileEntity();
        file.id = id;
        file.purpose = FilePurpose.POST_IMAGE;
        file.storageKey = storageKey;
        file.contentType = contentType;
        file.sizeBytes = sizeBytes;
        file.width = width;
        file.height = height;
        file.status = FileStatus.PENDING;
        file.uploadedByUser = userId;
        file.createdAt = now;
        return file;
    }

    /** 글에 쓰인다 — 쓰는 중(IN_USE) + 글 ID. 이미 쓰는 중이면 처음 시각은 그대로 둔다 */
    public void attachToPost(long postId, Instant now) {
        this.postId = postId;
        markUsed(now);
    }

    /** 글에서 빠졌다 — 다시 올리기만 한 상태(PENDING)로 돌아가 「올리다 만 파일 정리」가 지운다 */
    public void release() {
        this.status = FileStatus.PENDING;
        this.postId = null;
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

    public Long getUploadedByUser() {
        return uploadedByUser;
    }

    public Long getPostId() {
        return postId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
