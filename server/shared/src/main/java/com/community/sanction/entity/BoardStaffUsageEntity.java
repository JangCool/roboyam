package com.community.sanction.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 게시판 매니저·스텝의 하루 사용량 — shared V137 의 {@code board_staff_usage}(제재 · 글 · 댓글 수). 한도 안에서 하나 쓰기는 원자적 증가
 * {@code INSERT … ON CONFLICT … DO UPDATE … WHERE 칸 < 한도 RETURNING}(저장소의 native 문장)라 엔티티로 넣거나 고치지 않는다 — 읽기만 한다.
 * 지난 날은 DB 배치 {@code R__batch_40} 이 지운다.
 */
@Entity
@Table(name = "board_staff_usage")
@IdClass(BoardStaffUsageId.class)
public class BoardStaffUsageEntity {

    @Id
    private Long boardId;
    @Id
    private Long userId;
    @Id
    private LocalDate usageDate;

    private int sanctions;
    private int posts;
    private int comments;
    private Instant updatedAt;

    protected BoardStaffUsageEntity() {
    }
}
