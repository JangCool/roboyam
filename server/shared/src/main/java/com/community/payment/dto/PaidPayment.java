package com.community.payment.dto;

import java.time.Instant;

/**
 * 처리기에 넘기는 「확인된 결제」 — 포트원 조회로 금액·통화·유효 시각을 이미 맞춘 결제다(늦은 결제·금액 불일치는 처리기를 부르지 않는다).
 *
 * @param paymentOrderId 결제 주문 ID
 * @param paymentId      포트원 결제 ID
 * @param userId         회원
 * @param orderType      주문 종류
 * @param orderRefId     도메인 주문 ID
 * @param amount         결제 금액(= 청구 금액)
 * @param paidAt         포트원 결제 시각
 * @param via            확정한 길
 */
public record PaidPayment(long paymentOrderId, String paymentId, long userId, PaymentOrderType orderType, long orderRefId, long amount, Instant paidAt,
                          ConfirmedVia via) {
}
