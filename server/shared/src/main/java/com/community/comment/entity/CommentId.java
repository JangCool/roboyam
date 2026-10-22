package com.community.comment.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link CommentEntity} 의 복합 키(글 + 댓글) — {@code comments} 는 post_id 해시 파티션이라 PK 에 글 ID 가 들어간다 */
public class CommentId implements Serializable {

    private Long postId;
    private Long id;

    protected CommentId() {
    }

    public CommentId(Long postId, Long id) {
        this.postId = postId;
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CommentId that && Objects.equals(postId, that.postId) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, id);
    }
}
