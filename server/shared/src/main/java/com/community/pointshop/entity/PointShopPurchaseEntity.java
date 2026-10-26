package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 상점 구매 — {@code point_shop_purchases}(V86). <b>읽기 전용 매핑</b>(구매·환불은 {@code PointShopPurchaseService}·{@code PointShopRefundService} 가 JDBC 로 —
 * 포인트 사용·사용 취소와 한 트랜잭션). 품목 칸은 산 때의 사본이고 고쳐지지 않는다(트리거). 예외 환불이면 {@code refundedByOperatorId}·{@code refundReason} 이 있다.
 */
@Entity
@Table(name = "point_shop_purchases")
public class PointShopPurchaseEntity {

    @Id
    private Long id;

    private Long userId;
    private Long itemId;
    private String itemCode;
    private String itemName;
    @Enumerated(EnumType.STRING)
    private PointShopItemKind itemKind;
    @Enumerated(EnumType.STRING)
    private PointShopUsageType usageType;
    private int pricePoint;
    private Integer durationDays;
    @Enumerated(EnumType.STRING)
    private PointShopPurchaseStatus status;
    private Instant refundNoticeAgreedAt;
    private Long useLedgerId;
    private Long refundLedgerId;
    private Instant refundedAt;
    private Long refundedByOperatorId;
    private String refundReason;
    @Enumerated(EnumType.STRING)
    private PointShopRefundKind refundKind;
    private Instant createdAt;

    protected PointShopPurchaseEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public PointShopItemKind getItemKind() {
        return itemKind;
    }

    public PointShopUsageType getUsageType() {
        return usageType;
    }

    public int getPricePoint() {
        return pricePoint;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public PointShopPurchaseStatus getStatus() {
        return status;
    }

    public Instant getRefundNoticeAgreedAt() {
        return refundNoticeAgreedAt;
    }

    public Long getUseLedgerId() {
        return useLedgerId;
    }

    public Long getRefundLedgerId() {
        return refundLedgerId;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public Long getRefundedByOperatorId() {
        return refundedByOperatorId;
    }

    public String getRefundReason() {
        return refundReason;
    }

    /** 환불 종류(환불한 줄만 — V87) */
    public PointShopRefundKind getRefundKind() {
        return refundKind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
