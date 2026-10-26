package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 분류에 할당한 소모임 게시판 — V88__group_categories.sql 의 {@code group_category_boards}. 게시판 하나는 분류 최대 하나(기본 키 = 게시판 ID)이고,
 * 행이 있으면 늘 분류가 있다 — 분류에서 빼는 일은 행을 지운다. 새 행은 {@code persist()} 로 넣는다.
 */
@Entity
@Table(name = "group_category_boards")
public class GroupCategoryBoardEntity {

    @Id
    private Long boardId;

    private Long categoryId;
    private int assignedOrder;

    protected GroupCategoryBoardEntity() {
    }

    public static GroupCategoryBoardEntity assign(long boardId, long categoryId, int assignedOrder) {
        GroupCategoryBoardEntity row = new GroupCategoryBoardEntity();
        row.boardId = boardId;
        row.categoryId = categoryId;
        row.assignedOrder = assignedOrder;
        return row;
    }

    /** 다른 분류로 옮기거나 같은 분류 안의 자리를 바꾼다 */
    public void moveTo(long categoryId, int assignedOrder) {
        this.categoryId = categoryId;
        this.assignedOrder = assignedOrder;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public int getAssignedOrder() {
        return assignedOrder;
    }
}
