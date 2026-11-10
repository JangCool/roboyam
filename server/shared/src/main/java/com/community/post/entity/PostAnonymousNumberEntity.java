package com.community.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 익명 글 안의 회원 번호(V184 {@code post_anonymous_numbers}) — 한 글에서 처음 댓글을 단 회원 차례대로 1, 2 …(anonymous-board-contract 정책 3).
 * 글·회원 하나에 번호 하나(PK), 글 안에서 번호는 겹치지 않는다(UNIQUE). 번호는 {@code AnonymousNumbers#assign} 이 글 단위 잠금 아래 매긴다.
 * {@code created_at} 은 DB 기본값({@code now()})이 채운다.
 */
@Entity
@Table(name = "post_anonymous_numbers")
@IdClass(PostAnonymousNumberId.class)
public class PostAnonymousNumberEntity {

    @Id
    private Long postId;

    @Id
    private Long userId;

    private int number;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    protected PostAnonymousNumberEntity() {
    }

    public static PostAnonymousNumberEntity of(long postId, long userId, int number) {
        PostAnonymousNumberEntity row = new PostAnonymousNumberEntity();
        row.postId = postId;
        row.userId = userId;
        row.number = number;
        return row;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public int getNumber() {
        return number;
    }
}
