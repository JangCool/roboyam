package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용기 구매 인증 — {@code purchase_proofs}(V241). 사진은 비공개(다시 인코딩한 JPEG 의 {@code DataCipher.encryptBytes} 암호문)이고 확인 뒤 설정 일수가 지나면 파기한다.
 * {@code toString} 에 바이트를 싣지 않는다.
 */
@Entity
@Table(name = "purchase_proofs")
public class PurchaseProofEntity {

    @Id
    private Long id;
    private Long postId;
    private Long boardId;
    private Long userId;
    private byte[] content;
    private String contentType;
    @Enumerated(EnumType.STRING)
    private PurchaseProofStatus status;
    private String rejectReason;
    private Long reviewedBy;
    private Long reviewedByMember;
    private Instant reviewedAt;
    private Instant purgedAt;
    private Instant createdAt;

    protected PurchaseProofEntity() {
    }

    public static PurchaseProofEntity submit(long id, long postId, long boardId, long userId, byte[] encrypted, String contentType, Instant now) {
        PurchaseProofEntity e = new PurchaseProofEntity();
        e.id = id;
        e.postId = postId;
        e.boardId = boardId;
        e.userId = userId;
        e.content = encrypted;
        e.contentType = contentType;
        e.status = PurchaseProofStatus.PENDING;
        e.createdAt = now;
        return e;
    }

    /** 승인 — 운영자({@code operatorId}) 또는 게시판 매니저({@code memberId}) 중 하나 */
    public void approve(Long operatorId, Long memberId, Instant now) {
        this.status = PurchaseProofStatus.APPROVED;
        this.reviewedBy = operatorId;
        this.reviewedByMember = memberId;
        this.reviewedAt = now;
    }

    public void reject(Long operatorId, Long memberId, String reason, Instant now) {
        this.status = PurchaseProofStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedBy = operatorId;
        this.reviewedByMember = memberId;
        this.reviewedAt = now;
    }

    public void purge(Instant now) {
        this.content = null;
        this.purgedAt = now;
    }

    /** 시스템 반려(글 삭제·탈퇴 — 점검 L3·D8) — 확인자 없음, 사유는 부르는 쪽이 번들 문구로 */
    public void rejectBySystem(String reason, Instant now) {
        this.status = PurchaseProofStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedBy = null;
        this.reviewedByMember = null;
        this.reviewedAt = now;
    }

    public boolean pending() {
        return status == PurchaseProofStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getUserId() {
        return userId;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public PurchaseProofStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Long getReviewedByMember() {
        return reviewedByMember;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "PurchaseProofEntity[id=" + id + ", postId=" + postId + ", status=" + status + "]";
    }
}
