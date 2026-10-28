package com.community.groupbuy.dto;

import com.community.payment.dto.PaymentCheckout;
import java.time.Instant;

/**
 * 만든 주문과 결제창 정보 — 결제 유효 시각 = 선점 끝(결정 41).
 */
public record JoinResult(long participationId, String orderNo, long amount, Instant reservedUntil, PaymentCheckout checkout) {
}
