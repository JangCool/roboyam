package com.community.payment.dto;

/**
 * 취소 대기열에 넣을 취소 한 건.
 *
 * @param paymentOrderId 결제 주문
 * @param amount         취소 금액(1 이상, 남은 취소 가능 금액 이하)
 * @param kind           취소 종류
 * @param reason         내부 사유(1~200자 — 포트원에는 보내지 않는다)
 * @param requestKey     같은 취소를 두 번 넣지 않는 자연 키(1~120자, 예 {@code REFUND:<환불 건 ID>}) — 같은 키면 처음 것을 돌려준다
 * @param requestedBy    운영자 ID(시스템이면 null)
 */
public record PaymentCancelOrder(long paymentOrderId, long amount, CancelKind kind, String reason, String requestKey, Long requestedBy) {
}
