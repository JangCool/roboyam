package com.community.sanction.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** {@link BoardStaffUsageEntity} 의 복합 키(게시판 + 회원 + 한국 날짜) */
public class BoardStaffUsageId implements Serializable {

    private Long boardId;
    private Long userId;
    private LocalDate usageDate;

    protected BoardStaffUsageId() {
    }

    public BoardStaffUsageId(Long boardId, Long userId, LocalDate usageDate) {
        this.boardId = boardId;
        this.userId = userId;
        this.usageDate = usageDate;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BoardStaffUsageId that && Objects.equals(boardId, that.boardId) && Objects.equals(userId, that.userId)
                && Objects.equals(usageDate, that.usageDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boardId, userId, usageDate);
    }
}
