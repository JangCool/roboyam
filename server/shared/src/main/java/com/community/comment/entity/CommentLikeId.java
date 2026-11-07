package com.community.comment.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link CommentLikeEntity} 의 복합 키 */
public class CommentLikeId implements Serializable {

    private Long commentId;
    private Long userId;

    protected CommentLikeId() {
    }

    public CommentLikeId(Long commentId, Long userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CommentLikeId that && Objects.equals(commentId, that.commentId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commentId, userId);
    }
}
