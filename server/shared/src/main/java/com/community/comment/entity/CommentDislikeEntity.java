package com.community.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 댓글 비추천 기록 — V78 {@code comment_dislikes}(댓글 ID 해시 16 조각 파티션 표, 부모 표에 매핑). 한 회원이 한 댓글에 한 번.
 * {@code post_id} 는 글 하나에서 내 기록을 찾고 게시판 내용을 지울 때 쓴다({@code (post_id, user_id)} 색인). 넣기는 {@code PostCounterQueries}({@code ON CONFLICT DO NOTHING}),
 * {@code created_at} 은 DB 기본값({@code now()})이 채운다.
 */
@Entity
@Table(name = "comment_dislikes")
@IdClass(CommentDislikeId.class)
public class CommentDislikeEntity {

    @Id
    private Long commentId;

    @Id
    private Long userId;

    private Long postId;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    protected CommentDislikeEntity() {
    }
}
