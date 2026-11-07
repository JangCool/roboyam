package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 로보티콘 유료 팩 구매 기록 — shared V144 {@code emoticon_purchases}(+ V145 정산 달 두 칸). 결제 준비 한 번이 한 줄이고, 지우기·사본 고치기·되돌리기는 트리거가 막는다.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이는 저장소의 조건부 UPDATE 다 — 이 엔티티는 넣기({@code persist})·질의 경로(Q 클래스)·행 잠금에 쓴다.
 */
@Entity
@Table(name = "emoticon_purchases")
public class EmoticonPurchaseEntity {

    @Id
    private Long id;
    private Long userId;
    private Long packId;
    private Long creatorId;
    private String packName;
    private int amount;
    @Enumerated(EnumType.STRING)
    private EmoticonPurchaseStatus status;
    private Instant refundNoticeAgreedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant closedAt;
    private Long paymentOrderId;
    private Instant paidAt;
    private Instant refundUntil;
    private Instant usedAt;
    private Instant refundedAt;
    @Enumerated(EnumType.STRING)
    private EmoticonRefundKind refundKind;
    private String refundReason;
    private Long refundedBy;
    private Long refundCancelId;
    private Instant refundCompletedAt;
    private LocalDate settlementMonth;
    private LocalDate refundSettlementMonth;

    protected EmoticonPurchaseEntity() {
    }

    /** 새 결제 대기 구매 */
    public static EmoticonPurchaseEntity pending(long id, long userId, long packId, Long creatorId, String packName, int amount, Instant agreedAt, Instant expiresAt,
                                                 Instant now) {
        EmoticonPurchaseEntity p = new EmoticonPurchaseEntity();
        p.id = id;
        p.userId = userId;
        p.packId = packId;
        p.creatorId = creatorId;
        p.packName = packName;
        p.amount = amount;
        p.status = EmoticonPurchaseStatus.PENDING;
        p.refundNoticeAgreedAt = agreedAt;
        p.expiresAt = expiresAt;
        p.createdAt = now;
        return p;
    }
}
