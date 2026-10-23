package com.community.comment.entity;

import jakarta.persistence.Column;
import com.community.post.entity.ContentFormat;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 댓글 — V42·V46 의 {@code comments}(post_id 해시 64 파티션, PK = 글 + 댓글). 답글은 1단계({@code parentId} = 원댓글, {@code replyToId} = 실제로 답한 댓글).
 * 추천 수는 원자적 UPDATE 로만 고친다({@code updatable = false}). {@code toString} 을 두지 않는다(비밀번호 해시·IP).
 */
@Entity
@Table(name = "comments")
@IdClass(CommentId.class)
@DynamicUpdate
public class CommentEntity {

    @Id
    private Long postId;

    @Id
    private Long id;

    private Long boardId;
    private Long parentId;
    private Long replyToId;
    private Long authorId;
    private String guestName;
    private String guestPasswordHash;
    private String authorIp;
    private String content;
    @Enumerated(EnumType.STRING)
    private ContentFormat contentFormat;

    @Column(updatable = false)
    private int likeCount;

    private Instant hiddenAt;
    private String hiddenReason;
    private Instant hiddenUntil;
    private Instant deletedAt;
    private Instant purgedAt;
    private Instant editedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected CommentEntity() {
    }

    public static CommentEntity create(long id, long postId, long boardId, Long parentId, Long replyToId, Long authorId, String guestName,
                                       String guestPasswordHash, String authorIp, ContentFormat format, String content, Instant now) {
        CommentEntity comment = new CommentEntity();
        comment.id = id;
        comment.postId = postId;
        comment.boardId = boardId;
        comment.parentId = parentId;
        comment.replyToId = replyToId;
        comment.authorId = authorId;
        comment.guestName = authorId == null ? guestName : null;
        comment.guestPasswordHash = authorId == null ? guestPasswordHash : null;
        comment.authorIp = authorIp;
        comment.contentFormat = format;
        comment.content = content;
        comment.createdAt = now;
        comment.updatedAt = now;
        return comment;
    }

    public void edit(ContentFormat format, String content, Instant now) {
        this.contentFormat = format;
        this.content = content;
        this.editedAt = now;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** 신고로 숨긴다 — 이미 숨겨져 있으면 그대로(false) */
    public boolean hide(String reason, Instant until, Instant now) {
        if (isHiddenAt(now)) {
            return false;
        }
        this.hiddenAt = now;
        this.hiddenReason = reason;
        this.hiddenUntil = until;
        this.updatedAt = now;
        return true;
    }

    public boolean isGuest() {
        return authorId == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 신고로 숨겨진 상태인가(임시조치는 기간 안일 때만) */
    public boolean isHiddenAt(Instant now) {
        return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now));
    }

    public Long getPostId() {
        return postId;
    }

    public Long getId() {
        return id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getReplyToId() {
        return replyToId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getGuestPasswordHash() {
        return guestPasswordHash;
    }

    public String getAuthorIp() {
        return authorIp;
    }

    /** 본문 형식 — 칸이 비어 있던 옛 행은 TEXT */
    public ContentFormat getContentFormat() {
        return contentFormat == null ? ContentFormat.TEXT : contentFormat;
    }

    public String getContent() {
        return content;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
