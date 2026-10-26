package com.community.pointshop.dto.response;

/** {@code POST /api/admin/point-shop/purchases/{id}/refund} — 예외 환불 결과 */
public record PointShopOperatorRefundView(String purchaseId, long refundedPoints) {
}
