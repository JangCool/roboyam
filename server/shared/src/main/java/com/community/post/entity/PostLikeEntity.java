package com.community.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 글 추천 기록 — V46 {@code post_likes}(글 ID 해시 16 조각 파티션 표, 부모 표에 매핑). 한 회원이 한 글에 한 번. 넣기는 {@code PostCounterQueries#insertPostLike}
 * ({@code ON CONFLICT DO NOTHING} — 「처음이면 넣기」를 한 문장으로). {@code created_at} 은 DB 기본값({@code now()})이 채운다.
 */
@Entity
@Table(name = "post_likes")
@IdClass(PostLikeId.class)
public class PostLikeEntity {

    @Id
    private Long postId;

    @Id
    private Long userId;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    protected PostLikeEntity() {
    }
}
