package com.community.pointshop.dto.request;

/** {@code POST /api/admin/point-shop/purchases/{id}/refund} — 예외 환불 사유(5~200자, 서비스가 검사 — {@code fields.reason}) */
public record PointShopRefundRequest(String reason) {
}
