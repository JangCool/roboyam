package com.community.sanction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 제재 이의제기 한 줄 — shared V136 의 {@code sanction_appeals}(V137 부터 사이트 제재 {@code sanctionId} · 게시판 제재 {@code boardSanctionId} 중 하나).
 * 접수는 유일 색인이 막는 {@code INSERT … ON CONFLICT DO NOTHING RETURNING appeal_no}(저장소의 native 문장)라 엔티티로 넣지 않는다. 답변은 조건부 UPDATE(대기일 때만)다.
 */
@Entity
@Table(name = "sanction_appeals")
public class SanctionAppealEntity {

    @Id
    private Long id;

    /** DB 시퀀스 기본값({@code sanction_appeal_no_seq}) */
    @Column(insertable = false, updatable = false)
    private Long appealNo;

    private Long sanctionId;
    private Long boardSanctionId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private AppealStatus status;
    private String reason;
    private String answer;
    private Long answeredBy;
    private Instant answeredAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected SanctionAppealEntity() {
    }
}
