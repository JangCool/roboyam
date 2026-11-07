package com.community.debate.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원의 글별 현재 입장(투표) — V76·V78 {@code post_stances}(글 + 회원 하나). 처음 고르는 줄은 동시 요청에도 하나만 남도록 {@code DebateQueries#insertIfAbsent}
 * ({@code ON CONFLICT DO NOTHING})가 만들고, 바꾸기는 조건부 UPDATE 다. 글 표는 파티션 표라 {@code post_id} 외래 키가 없다.
 */
@Entity
@Table(name = "post_stances")
@IdClass(PostStanceId.class)
public class PostStanceEntity {

    @Id
    private Long postId;

    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    private DebateStance stance;

    private Instant createdAt;
    private Instant updatedAt;

    protected PostStanceEntity() {
    }

    public DebateStance getStance() {
        return stance;
    }
}
