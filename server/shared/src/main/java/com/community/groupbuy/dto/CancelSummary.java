package com.community.groupbuy.dto;

/**
 * 공구 중단 확인 창의 숫자(결정 29) — 「환불 N명 · 총 N원 / 이미 발송 M명은 그대로 / 결제 중 K명은 닫힘」.
 */
public record CancelSummary(int refundOrders, long refundAmount, int shippedOrders, int pendingOrders) {
}
