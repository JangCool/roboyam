package com.community.debate.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link PostStanceEntity} 의 복합 키(글 + 회원) */
public class PostStanceId implements Serializable {

    private Long postId;
    private Long userId;

    protected PostStanceId() {
    }

    public PostStanceId(Long postId, Long userId) {
        this.postId = postId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PostStanceId that && Objects.equals(postId, that.postId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, userId);
    }
}
