package com.community.post.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link PostTagEntity} 의 복합 키(글 ID + 비교 열쇠) */
public class PostTagId implements Serializable {

    private Long postId;
    private String tagKey;

    protected PostTagId() {
    }

    public PostTagId(Long postId, String tagKey) {
        this.postId = postId;
        this.tagKey = tagKey;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PostTagId that && Objects.equals(postId, that.postId) && Objects.equals(tagKey, that.tagKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, tagKey);
    }
}
