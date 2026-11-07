package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 상점 환불 신청 — {@code point_shop_refund_requests}(V87). 정지·영구 정지 중인 회원의 셀프 환불이 남기는 신청. 만들기는 {@link #pending}(환불과 같은
 * 트랜잭션), 수락·거절은 저장소 {@code PointShopPurchaseStore} 의 조건부 UPDATE({@code status = 'PENDING'} 일 때만). 같은 구매에 대기 신청은 하나, 결정은 한 번뿐(트리거).
 */
@Entity
@Table(name = "point_shop_refund_requests")
public class PointShopRefundRequestEntity {

    @Id
    private Long id;

    private Long purchaseId;
    private Long userItemId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private PointShopRefundRequestStatus status;
    private Instant requestedAt;
    private Instant decidedAt;
    private Long decidedByOperatorId;
    private String rejectReason;

    protected PointShopRefundRequestEntity() {
    }

    /** 새 신청(대기) — 환불과 같은 트랜잭션에서 넣고 바로 내보낸다. 수락·거절은 저장소의 조건부 UPDATE({@code status = 'PENDING'} 일 때만) */
    public static PointShopRefundRequestEntity pending(long id, long purchaseId, long userItemId, long userId, Instant requestedAt) {
        PointShopRefundRequestEntity r = new PointShopRefundRequestEntity();
        r.id = id;
        r.purchaseId = purchaseId;
        r.userItemId = userItemId;
        r.userId = userId;
        r.status = PointShopRefundRequestStatus.PENDING;
        r.requestedAt = requestedAt;
        return r;
    }

    public Long getId() {
        return id;
    }

    public Long getPurchaseId() {
        return purchaseId;
    }

    public Long getUserItemId() {
        return userItemId;
    }

    public Long getUserId() {
        return userId;
    }

    public PointShopRefundRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Long getDecidedByOperatorId() {
        return decidedByOperatorId;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
