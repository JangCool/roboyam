package com.community.sanction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 게시판 제재 반복 검토 한 건 — shared V137 의 {@code board_sanction_reviews}. 열기는 부분 유일 색인이 막는 {@code INSERT … ON CONFLICT DO NOTHING RETURNING review_no}
 * (저장소의 native 문장)라 엔티티로 넣지 않는다. 닫기는 조건부 UPDATE(열린 건만)다.
 */
@Entity
@Table(name = "board_sanction_reviews")
public class BoardSanctionReviewEntity {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    private Long id;

    /** DB 시퀀스 기본값({@code board_sanction_review_no_seq}) */
    @Column(insertable = false, updatable = false)
    private Long reviewNo;

    private Long boardId;
    private Long userId;
    /** OPEN · CLOSED */
    private String status;
    private Instant openedAt;
    private int openedCount;
    private Instant countedAfter;
    private Instant closedAt;
    private Long closedBy;
    /** KEPT · SITE_SANCTIONED · VOIDED */
    private String result;
    private String memo;
    private Long siteSanctionId;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] voidedSanctionIds;
    private Integer closedCount;
    private Integer closedBoardCount;
    private Instant createdAt;
    private Instant updatedAt;

    protected BoardSanctionReviewEntity() {
    }
}
