package com.community.post.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link PostViewEntity} 의 복합 키 */
public class PostViewId implements Serializable {

    private Long postId;
    private String viewerKey;

    protected PostViewId() {
    }

    public PostViewId(Long postId, String viewerKey) {
        this.postId = postId;
        this.viewerKey = viewerKey;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PostViewId that && Objects.equals(postId, that.postId) && Objects.equals(viewerKey, that.viewerKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, viewerKey);
    }
}
