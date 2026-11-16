package com.community.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 우수 글 선정 한 줄 — {@code post_features}(V238, point-board-tier-contract). 운영자가 글을 우수 글로 뽑은 이력이고 취소해도 지우지 않는다({@link #unfeature}). 한 글에 살아 있는 선정
 * (취소되지 않은 줄)은 하나({@code ux_post_features_active}). 글쓴이는 선정 때의 글쓴이(비회원 글 null).
 *
 * <p><b>다른 기능이 읽는다</b>: 회원 칭호(member-title-contract 「이달의 작가 후보 = 그 달에 우수 글로 뽑힌 글의 글쓴이」)가 {@code featured_at} 으로 달마다 선정 글을 고른다.
 */
@Entity
@Table(name = "post_features")
public class PostFeatureEntity {

    @Id
    private Long id;

    private Long postId;
    private Long authorId;
    private Long featuredBy;
    private Instant featuredAt;
    private Long unfeaturedBy;
    private Instant unfeaturedAt;

    protected PostFeatureEntity() {
    }

    /** 선정 — 운영자가 지금 뽑았다 */
    public static PostFeatureEntity create(long id, long postId, Long authorId, long operatorId, Instant now) {
        PostFeatureEntity e = new PostFeatureEntity();
        e.id = id;
        e.postId = postId;
        e.authorId = authorId;
        e.featuredBy = operatorId;
        e.featuredAt = now;
        return e;
    }

    /** 선정 취소 — 줄은 남기고 취소한 운영자·시각을 적는다 */
    public void unfeature(long operatorId, Instant now) {
        this.unfeaturedBy = operatorId;
        this.unfeaturedAt = now;
    }

    public boolean isActive() {
        return unfeaturedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public Long getFeaturedBy() {
        return featuredBy;
    }

    public Instant getFeaturedAt() {
        return featuredAt;
    }

    public Long getUnfeaturedBy() {
        return unfeaturedBy;
    }

    public Instant getUnfeaturedAt() {
        return unfeaturedAt;
    }
}
