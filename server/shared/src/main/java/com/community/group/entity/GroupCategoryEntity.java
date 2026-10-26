package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 소모임 분류 — V88__group_categories.sql 의 {@code group_categories}. ID 는 Snowflake(앱 발급)라 새 행은 {@code persist()} 로 넣는다.
 * {@code nameKey} 는 같은 이름 막기(공백 제거·소문자 — {@code GroupCategoryNames} 가 만든다). {@code updatedAt} 은 「다른 관리자가 먼저 고쳤어요」 비교 기준이라
 * 이름·게시판이 바뀔 때만 {@link #touch} 로 올린다(순서 바꾸기는 올리지 않는다).
 */
@Entity
@Table(name = "group_categories")
public class GroupCategoryEntity {

    @Id
    private Long id;

    private String name;
    private String nameKey;
    private int sortOrder;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected GroupCategoryEntity() {
    }

    public static GroupCategoryEntity create(long id, String name, String nameKey, int sortOrder, Long operatorId, Instant now) {
        GroupCategoryEntity row = new GroupCategoryEntity();
        row.id = id;
        row.name = name;
        row.nameKey = nameKey;
        row.sortOrder = sortOrder;
        row.createdAt = now;
        row.updatedBy = operatorId;
        row.updatedAt = now;
        return row;
    }

    public void rename(String name, String nameKey) {
        this.name = name;
        this.nameKey = nameKey;
    }

    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 이름·게시판이 바뀌었다 — 고친 사람·시각을 올린다 */
    public void touch(Long operatorId, Instant now) {
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
