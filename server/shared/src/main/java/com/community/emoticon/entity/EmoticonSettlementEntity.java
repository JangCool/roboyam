package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 로보티콘 작가 월 정산 한 줄 — shared V145(+ V148 포기 · V149 수동 지급). 예정 줄은 금액을 저장하지 않고, 확정(굳힘) 때 금액 칸을 한꺼번에 채운다 — 굳힌 금액·지급 기록
 * 고치기·지우기·허용되지 않은 상태 전이는 트리거가 막는다. 줄 만들기는 저장소의 {@code ON CONFLICT DO NOTHING}(있으면 그대로)이고 상태 전이는 조건부 UPDATE 다 —
 * 이 엔티티는 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@Table(name = "emoticon_settlements")
@IdClass(EmoticonSettlementId.class)
public class EmoticonSettlementEntity {

    @Id
    private Long creatorId;
    @Id
    private LocalDate salesMonth;
    @Enumerated(EnumType.STRING)
    private EmoticonSettlementStatus status;
    private Integer salesCount;
    private Long salesAmount;
    private Integer refundCount;
    private Long refundAmount;
    private Long vatAmount;
    private Long baseAmount;
    private Integer feeRatePercent;
    private Long feeAmount;
    private Long shareAmount;
    private Long carriedIn;
    private Long withholdingAmount;
    private Long payoutAmount;
    private Long carriedOut;
    private String payeeType;
    private LocalDate payDate;
    private Instant closesAt;
    private Instant confirmedAt;
    private Long confirmedBy;
    private String holdReason;
    private Instant heldAt;
    private Long heldBy;
    private Instant paidAt;
    private Long paidBy;
    private LocalDate transferDate;
    private String paidMemo;
    private Instant createdAt;
    private Instant updatedAt;

    protected EmoticonSettlementEntity() {
    }
}
