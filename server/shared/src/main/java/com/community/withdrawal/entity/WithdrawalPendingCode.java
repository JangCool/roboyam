package com.community.withdrawal.entity;

/**
 * 남은 일(끝나야 탈퇴가 완료되는 것 — commerce.md 17-1) — 화면 타입 {@code WithdrawalPendingCode} 와 같은 값.
 * <ul>
 *   <li>{@link #GROUP_BUY_ORDER} 진행 중 공구 주문(PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED)</li>
 *   <li>{@link #RETURN_REQUEST} 열린 취소·반품 요청(AWAITING_FEE·REQUESTED·APPROVED·RECEIVED)</li>
 *   <li>{@link #PAYMENT_CANCEL} 끝나지 않은 결제 취소(PENDING·SENDING·GAVE_UP·MANUAL) · 송금 대기 환불(commerce_bank_transfers 미완료)</li>
 *   <li>{@link #POINT_CHARGE_REFUND} 진행 중 충전 환불 — 충전(3단계) 전이라 실제로 생기지 않는다</li>
 *   <li>{@link #POINT_SHOP_REFUND} 심사 중 포인트샵 환불 신청(PENDING)</li>
 *   <li>{@link #BIZ_APPLICATION} 「기다리기」를 고른 진행 중 광고·핫딜 신청 건</li>
 *   <li>{@link #DELIVERED_RETURN_WINDOW} 「반품 기한까지 기다리기」를 고른 배송완료 7일 안 주문</li>
 * </ul>
 */
public enum WithdrawalPendingCode {
    GROUP_BUY_ORDER,
    RETURN_REQUEST,
    PAYMENT_CANCEL,
    POINT_CHARGE_REFUND,
    POINT_SHOP_REFUND,
    BIZ_APPLICATION,
    DELIVERED_RETURN_WINDOW
}
