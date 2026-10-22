package com.community.board.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link BoardStaffEntity} 의 복합 키(게시판 + 회원) */
public class BoardStaffId implements Serializable {

    private Long boardId;
    private Long userId;

    protected BoardStaffId() {
    }

    public BoardStaffId(Long boardId, Long userId) {
        this.boardId = boardId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BoardStaffId that && Objects.equals(boardId, that.boardId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boardId, userId);
    }
}
