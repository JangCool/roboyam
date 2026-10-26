package com.community.payment.dto;

import com.community.payment.gateway.PaymentGatewayKind;
import java.time.Instant;

/**
 * 만든 결제 주문 — 화면은 이 값 그대로 결제창을 연다(금액을 다시 계산하지 않는다, commerce.md 8-2).
 *
 * @param provider   PORTONE(브라우저 SDK) · FAKE(가짜 결제창)
 * @param storeId    포트원 상점 ID(가짜면 null)
 * @param channelKey 포트원 결제 채널 키(가짜면 null)
 */
public record PaymentCheckout(long paymentOrderId, String paymentId, String orderName, long amount, String currency, Instant expiresAt, PaymentGatewayKind provider,
                              String storeId, String channelKey) {
}
