package com.community.post.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link PostLikeEntity} 의 복합 키 */
public class PostLikeId implements Serializable {

    private Long postId;
    private Long userId;

    protected PostLikeId() {
    }

    public PostLikeId(Long postId, Long userId) {
        this.postId = postId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PostLikeId that && Objects.equals(postId, that.postId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, userId);
    }
}
