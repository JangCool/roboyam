package com.community.pointshop.dto.response;

/** {@code POST /api/me/items/{id}/refund} — 돌려준 포인트(산 때 낸 값)와 환불 뒤 내 포인트 */
public record MyItemRefundView(long refundedPoints, long balance) {
}
