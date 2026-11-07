package com.community.dm.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link DmBoxEntity} 의 복합 키(대화 + 회원) */
public class DmBoxId implements Serializable {

    private Long threadId;
    private Long userId;

    protected DmBoxId() {
    }

    public DmBoxId(Long threadId, Long userId) {
        this.threadId = threadId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DmBoxId that && Objects.equals(threadId, that.threadId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId, userId);
    }
}
