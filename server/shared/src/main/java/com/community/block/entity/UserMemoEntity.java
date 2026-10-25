package com.community.block.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 이용자 메모 — V71__blocks_memos.sql 의 {@code user_memos}. 내가({@code userId}) 상대 회원({@code targetId})에게 남긴 한 줄 메모(20자, 나에게만 보인다).
 * ID 는 Snowflake 라 「처음 남긴 것이 최근인 순서」 = ID 내림차순이다(고쳐도 자리가 바뀌지 않는다). 빈 메모로 고치면 행을 지운다(서비스).
 */
@Entity
@Table(name = "user_memos")
@DynamicUpdate
public class UserMemoEntity {

    @Id
    private Long id;

    private Long userId;
    private Long targetId;
    private String memo;
    private Instant createdAt;
    private Instant updatedAt;

    protected UserMemoEntity() {
    }

    public static UserMemoEntity create(long id, long userId, long targetId, String memo, Instant now) {
        UserMemoEntity entity = new UserMemoEntity();
        entity.id = id;
        entity.userId = userId;
        entity.targetId = targetId;
        entity.memo = memo;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /** 메모를 고친다 — 같은 글자면 그대로 둔다 */
    public void change(String memo, Instant now) {
        if (memo.equals(this.memo)) {
            return;
        }
        this.memo = memo;
        this.updatedAt = now;
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

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
