package com.community.pointshop.dto.response;

import com.community.pointshop.entity.PointShopRefundRequestStatus;
import java.time.Instant;

/**
 * 관리자 환불 신청 수락·거절 결과 — {@code POST /api/admin/point-shop/refund-requests/{id}/approve|reject}.
 *
 * @param refundedPoints 돌려준 포인트(거절은 0)
 */
public record PointShopRefundDecisionView(String requestId, String purchaseId, PointShopRefundRequestStatus status, long refundedPoints, Instant decidedAt) {
}
