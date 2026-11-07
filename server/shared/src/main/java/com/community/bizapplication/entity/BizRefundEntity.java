package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 신청 건 환불 줄 — shared V112 {@code biz_refunds}(V127 중단 일할 · V128 운영자 추가 환불). 열린(REQUESTED) 줄은 건당 하나(부분 유일 색인 {@code ux_biz_refunds_open}).
 * 만들기는 {@link #create}, 심사·송금·취소 결과는 저장소의 조건부 UPDATE 가 한다.
 */
@Entity
@Table(name = "biz_refunds")
public class BizRefundEntity {

    @Id
    private Long id;

    private Long applicationId;
    @Enumerated(EnumType.STRING)
    private BizRefundType type;
    @Enumerated(EnumType.STRING)
    private BizRefundStatus status;
    @Enumerated(EnumType.STRING)
    private BizPaymentMethod method;
    private Long paidAmount;
    private Long proposedAmount;
    private Integer usedDays;
    private Integer totalDays;
    private Long approvedAmount;
    private String reason;
    private String decisionReason;
    private Long paymentCancelId;
    /** 수동 송금 상태 — {@code PENDING}·{@code DONE}(온라인은 null) */
    private String transferStatus;
    private Instant transferredAt;
    private Long transferredBy;
    private String transferNote;
    private Instant completedAt;
    private Instant requestedAt;
    private Instant decidedAt;
    private Long decidedBy;
    private Long proratedAmount;
    private Integer feeRate;
    private Long feeAmount;
    private Instant basisAt;

    protected BizRefundEntity() {
    }

    /** 새 환불 줄 — 중단 일할 칸({@code prorated·feeRate·feeAmount·basisAt})은 그 종류일 때만 채운다(아니면 null) */
    public static BizRefundEntity create(long id, long applicationId, BizRefundType type, BizRefundStatus status, BizPaymentMethod method, long paidAmount,
                                         long proposedAmount, int usedDays, int totalDays, Long approvedAmount, String reason, String decisionReason,
                                         String transferStatus, Instant requestedAt, Instant decidedAt, Long decidedBy, Instant completedAt, Long proratedAmount,
                                         Integer feeRate, Long feeAmount, Instant basisAt) {
        BizRefundEntity r = new BizRefundEntity();
        r.id = id;
        r.applicationId = applicationId;
        r.type = type;
        r.status = status;
        r.method = method;
        r.paidAmount = paidAmount;
        r.proposedAmount = proposedAmount;
        r.usedDays = usedDays;
        r.totalDays = totalDays;
        r.approvedAmount = approvedAmount;
        r.reason = reason;
        r.decisionReason = decisionReason;
        r.transferStatus = transferStatus;
        r.requestedAt = requestedAt;
        r.decidedAt = decidedAt;
        r.decidedBy = decidedBy;
        r.completedAt = completedAt;
        r.proratedAmount = proratedAmount;
        r.feeRate = feeRate;
        r.feeAmount = feeAmount;
        r.basisAt = basisAt;
        return r;
    }
}
