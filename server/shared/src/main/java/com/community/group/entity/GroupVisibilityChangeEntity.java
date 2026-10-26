package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 공개 범위 변경 이력 한 줄 — V94 {@code group_visibility_changes}. 값이 실제로 바뀐 변경마다 남고 고치지 않는다. ID 는 Snowflake 라 {@code persist()} */
@Entity
@Table(name = "group_visibility_changes")
public class GroupVisibilityChangeEntity {

    @Id
    private Long id;

    private Long groupId;
    @Enumerated(EnumType.STRING)
    private GroupVisibility fromValue;
    @Enumerated(EnumType.STRING)
    private GroupVisibility toValue;
    private Long changedBy;
    private Instant changedAt;
    private int postCount;
    private int commentCount;
    private int membersOnlyCount;

    protected GroupVisibilityChangeEntity() {
    }

    public static GroupVisibilityChangeEntity of(long id, long groupId, GroupVisibility from, GroupVisibility to, long changedBy, Instant changedAt, int postCount,
                                                 int commentCount, int membersOnlyCount) {
        GroupVisibilityChangeEntity row = new GroupVisibilityChangeEntity();
        row.id = id;
        row.groupId = groupId;
        row.fromValue = from;
        row.toValue = to;
        row.changedBy = changedBy;
        row.changedAt = changedAt;
        row.postCount = postCount;
        row.commentCount = commentCount;
        row.membersOnlyCount = membersOnlyCount;
        return row;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public GroupVisibility getFromValue() {
        return fromValue;
    }

    public GroupVisibility getToValue() {
        return toValue;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public int getPostCount() {
        return postCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getMembersOnlyCount() {
        return membersOnlyCount;
    }
}
