package com.community.nicknameimage.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 닉네임 그림 신청 한 줄 — V72__nickname_images.sql 의 {@code nickname_image_requests}. 회원이 올리면(api) 대기({@link NicknameImageStatus#PENDING})로 생기고,
 * 운영자가(admin) 승인·반려하거나 적용 중인 그림을 해제한다. 상태는 아래 도메인 메서드로만 바꾼다(정해진 상태에서만 — 아니면 {@link IllegalStateException}, 서비스가 먼저 확인한다).
 *
 * <p>파일은 저장소({@code ImageStorage}) 키로 가리킨다 — 파일을 지운 신청은 키가 없다({@link #dropFiles}). 키에 무작위 토큰이 있어 주소를 짐작할 수 없다(검토 전 그림도 같은 공개 주소).
 */
@Entity
@Table(name = "nickname_image_requests")
@DynamicUpdate
public class NicknameImageRequestEntity {

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private NicknameImageStatus status;

    private String imageKey;
    private String stillKey;
    private String contentType;
    /** 올린 파일의 크기(바이트) */
    private int bytes;
    /** 저장한 그림의 가로·세로 */
    private int width;
    private int height;
    private int frames;
    private String rejectReason;
    private String revokeReason;
    private Long reviewedBy;
    private Instant reviewedAt;
    private Long revokedBy;
    private Instant revokedAt;
    /** 이 시각 전에는 다시 신청할 수 없다(반려·운영 해제) */
    private Instant reapplyAt;
    /** 회원이 취소·해제했거나 대체·교체된 시각 */
    private Instant endedAt;
    private Instant createdAt;

    protected NicknameImageRequestEntity() {
    }

    public static NicknameImageRequestEntity pending(long id, long userId, String imageKey, String stillKey, String contentType, int bytes, int width, int height,
                                                     int frames, Instant now) {
        NicknameImageRequestEntity e = new NicknameImageRequestEntity();
        e.id = id;
        e.userId = userId;
        e.status = NicknameImageStatus.PENDING;
        e.imageKey = imageKey;
        e.stillKey = stillKey;
        e.contentType = contentType;
        e.bytes = bytes;
        e.width = width;
        e.height = height;
        e.frames = frames;
        e.createdAt = now;
        return e;
    }

    /** 승인 — 대기 신청만. 이 그림이 회원의 지금 그림이 된다(앞 그림은 부르는 쪽이 {@link #replacedByApproval}) */
    public void approve(long operatorId, Instant now) {
        require(NicknameImageStatus.PENDING);
        this.status = NicknameImageStatus.APPROVED;
        this.reviewedBy = operatorId;
        this.reviewedAt = now;
    }

    /** 반려 — 대기 신청만. 파일은 다음 신청 때까지 둔다 */
    public void reject(long operatorId, String reason, Instant reapplyAt, Instant now) {
        require(NicknameImageStatus.PENDING);
        this.status = NicknameImageStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedBy = operatorId;
        this.reviewedAt = now;
        this.reapplyAt = reapplyAt;
    }

    /** 운영자의 해제 — 적용 중인 그림만 */
    public void revoke(long operatorId, String reason, Instant reapplyAt, Instant now) {
        require(NicknameImageStatus.APPROVED);
        this.status = NicknameImageStatus.REVOKED;
        this.revokeReason = reason;
        this.revokedBy = operatorId;
        this.revokedAt = now;
        this.reapplyAt = reapplyAt;
    }

    /** 회원이 대기 신청을 취소했거나 새 신청이 대체했다 */
    public void cancel(Instant now) {
        require(NicknameImageStatus.PENDING);
        this.status = NicknameImageStatus.CANCELED;
        this.endedAt = now;
    }

    /** 회원이 적용 중인 그림을 해제했다 */
    public void removeByOwner(Instant now) {
        require(NicknameImageStatus.APPROVED);
        this.status = NicknameImageStatus.REMOVED;
        this.endedAt = now;
    }

    /** 다른 그림이 승인돼 이 그림이 바뀌었다 */
    public void replacedByApproval(Instant now) {
        require(NicknameImageStatus.APPROVED);
        this.status = NicknameImageStatus.REMOVED;
        this.endedAt = now;
    }

    /** 파일을 지웠다(지우는 일은 커밋 뒤 부르는 쪽이 한다) — 키를 비운다 */
    public void dropFiles() {
        this.imageKey = null;
        this.stillKey = null;
    }

    private void require(NicknameImageStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("닉네임 그림 신청의 상태가 " + expected + " 가 아니다: " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public NicknameImageStatus getStatus() {
        return status;
    }

    public String getImageKey() {
        return imageKey;
    }

    public String getStillKey() {
        return stillKey;
    }

    public String getContentType() {
        return contentType;
    }

    public int getBytes() {
        return bytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrames() {
        return frames;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getReapplyAt() {
        return reapplyAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
