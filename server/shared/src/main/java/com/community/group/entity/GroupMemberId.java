package com.community.group.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link GroupMemberEntity} 의 복합 키(소모임 + 회원) */
public class GroupMemberId implements Serializable {

    private Long groupId;
    private Long userId;

    protected GroupMemberId() {
    }

    public GroupMemberId(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupMemberId that && Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}
