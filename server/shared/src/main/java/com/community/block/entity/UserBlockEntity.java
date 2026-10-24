package com.community.block.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원 차단 한 줄 — V71__blocks_memos.sql 의 {@code user_blocks}. 내가({@code userId}) 차단한 회원({@code targetId}). 상대에게 알리지 않는다.
 * ID 는 Snowflake(만든 시각 순)라 목록 차례·커서가 ID 다. 같은 짝은 한 줄(유일 제약) — 다시 차단해도 처음 차단한 시각 그대로다(멱등).
 */
@Entity
@Table(name = "user_blocks")
public class UserBlockEntity {

    @Id
    private Long id;

    private Long userId;
    private Long targetId;
    private Instant createdAt;

    protected UserBlockEntity() {
    }

    public static UserBlockEntity create(long id, long userId, long targetId, Instant now) {
        UserBlockEntity block = new UserBlockEntity();
        block.id = id;
        block.userId = userId;
        block.targetId = targetId;
        block.createdAt = now;
        return block;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTargetId() {
        return targetId;
    }

    /** 차단한 시각 */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
