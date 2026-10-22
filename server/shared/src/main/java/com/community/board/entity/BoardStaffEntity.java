package com.community.board.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 게시판 매니저·스텝 지정 하나 — V42__boards.sql 의 {@code board_staff}. 회원이 지워지면 함께 지워진다(DB 연쇄 삭제).
 * 규칙(자격·인원·한 회원 최대 5게시판)은 docs/domain/operator-permissions.md 「게시판 매니저 · 스텝」.
 */
@Entity
@Table(name = "board_staff")
@IdClass(BoardStaffId.class)
public class BoardStaffEntity {

    public static final String MANAGER = "MANAGER";
    public static final String STAFF = "STAFF";

    @Id
    private Long boardId;

    @Id
    private Long userId;

    private String role;
    private Instant appointedAt;
    private Long appointedByOperator;
    private Long appointedByUser;

    protected BoardStaffEntity() {
    }

    /** 운영자가 지정한다 */
    public static BoardStaffEntity byOperator(long boardId, long userId, String role, long operatorId, Instant now) {
        BoardStaffEntity staff = new BoardStaffEntity();
        staff.boardId = boardId;
        staff.userId = userId;
        staff.role = role;
        staff.appointedAt = now;
        staff.appointedByOperator = operatorId;
        return staff;
    }

    public Long getBoardId() {
        return boardId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public Instant getAppointedAt() {
        return appointedAt;
    }
}
