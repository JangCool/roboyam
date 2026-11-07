package com.community.sanction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Generated;

/**
 * 게시판 제재 한 줄 — shared V137(+V141 운영자 회원 계정 칸)의 {@code board_sanctions}. 만들기는 {@link #issue}(번호 {@code sanction_no} 는 DB 시퀀스가 매기고 넣은 뒤 읽어 온다).
 * 해제·무효·만료·확인은 저장소의 조건부 UPDATE(대상 회원 행 잠금 아래에서 한 번만 이긴다)가 한다 — 상태 칸을 엔티티로 고치지 않는다.
 * 읽기는 저장소가 게시판·회원·처리자와 함께 한 줄 모양({@code BoardSanctionStore.BoardSanctionRow})으로 꺼낸다.
 */
@Entity
@Table(name = "board_sanctions")
public class BoardSanctionEntity {

    @Id
    private Long id;

    /** DB 시퀀스 기본값({@code board_sanction_no_seq}) — 넣을 때 보내지 않고 넣은 뒤 읽어 온다 */
    @Generated
    @Column(insertable = false, updatable = false)
    private Long sanctionNo;

    private Long boardId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private BoardSanctionKind kind;
    @Enumerated(EnumType.STRING)
    private SanctionStatus status;
    private String reasonCode;
    private String reasonText;
    private Long issuedBy;
    /** MANAGER · STAFF · OPERATOR(운영자 회원 계정 — {@code issuedOperatorId} 있음) */
    private String issuedRole;
    private Long issuedOperatorId;
    private Long postId;
    private Long commentId;
    private String sourceTitle;
    private Instant startsAt;
    private Instant endsAt;
    private Instant acknowledgedAt;
    private Instant liftedAt;
    /** MANAGER · OPERATOR */
    private String liftedByType;
    private Long liftedByUser;
    private Long liftedByOperator;
    private String liftReason;
    private Instant voidedAt;
    /** APPEAL · OPERATOR · REVIEW */
    private String voidSource;
    private Long voidedBy;
    private String voidReason;
    private Instant endedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected BoardSanctionEntity() {
    }

    /** 새 제재(진행 중). 자리·한도·대상 검사는 부르는 서비스가 끝냈다 */
    public static BoardSanctionEntity issue(long id, long boardId, long userId, BoardSanctionKind kind, String reasonCode, String reasonText, long issuedBy,
                                            String issuedRole, Long issuedOperatorId, Long postId, Long commentId, String sourceTitle, Instant startsAt,
                                            Instant endsAt, Instant now) {
        BoardSanctionEntity s = new BoardSanctionEntity();
        s.id = id;
        s.boardId = boardId;
        s.userId = userId;
        s.kind = kind;
        s.status = SanctionStatus.ACTIVE;
        s.reasonCode = reasonCode;
        s.reasonText = reasonText;
        s.issuedBy = issuedBy;
        s.issuedRole = issuedRole;
        s.issuedOperatorId = issuedOperatorId;
        s.postId = postId;
        s.commentId = commentId;
        s.sourceTitle = sourceTitle;
        s.startsAt = startsAt;
        s.endsAt = endsAt;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    /** 넣고 내보낸 뒤의 번호 */
    public Long getSanctionNo() {
        return sanctionNo;
    }
}
